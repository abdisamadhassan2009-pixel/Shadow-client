// Ghost Client - in-game PvP data (Armor HUD and more).
//
// Reads the local player's armor, main hand and offhand items inside Minecraft and hands a small
// snapshot to the launcher's Java HUD widgets. Nothing is changed in the game: this only reads.
//
// How it finds things in the stripped game library (Minecraft 1.26.60):
//   * class vtables are located through their C++ RTTI type names (they ship in the binary);
//   * a couple of small functions are located by byte patterns and checked before use;
//   * one hook on MinecraftUIRenderContext's end-of-frame call gives us the ClientInstance.
// Research credit: the AppleSkin mod for LeviLauncher (TheRealBatu & MrPokeG) documented the
// UI render context and ClientInstance layout used here. This file is an independent implementation.

#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <jni.h>
#include <link.h>
#include <time.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <string_view>
#include <vector>

#define LOG_TAG "GhostPvP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ---- Layout facts for Minecraft 1.26.60 (all re-checked at startup where possible) ----
constexpr std::size_t kUiClient = 0x8;             // MinecraftUIRenderContext -> ClientInstance*
constexpr std::size_t kUiFinishSlot = 31;          // MinecraftUIRenderContext vtable: end of frame
constexpr std::size_t kClientLocalPlayerSlot = 31; // ClientInstance vtable: getLocalPlayer()
constexpr std::size_t kActorCarriedSlot = 78;      // Actor vtable: getCarriedItem()
constexpr std::size_t kActorEntityContext = 0x8;   // Actor -> EntityContext
constexpr std::size_t kContainerGetItemSlot = 8;   // Container vtable: getItem(int)
constexpr std::size_t kItemMaxDamageSlot = 37;     // Item vtable: getMaxDamage()

constexpr std::size_t kStackItem = 0x8;            // ItemStackBase -> WeakPtr<Item>
constexpr std::size_t kStackUserData = 0x10;       // ItemStackBase -> CompoundTag*
constexpr std::size_t kStackCount = 0x22;
constexpr std::size_t kStackValid = 0x23;
constexpr std::size_t kItemFullName = 0xb0;        // Item::mFullName (HashedString) text
constexpr std::size_t kArmorGetterDistance = 0xd0; // equipment armor getter follows hand getter

struct Segment { std::uintptr_t begin, end; unsigned flags; };
std::vector<Segment> segments;

template <class T> T rd(std::uintptr_t address) { T v; std::memcpy(&v, reinterpret_cast<const void*>(address), sizeof v); return v; }
template <class T> T rd(const void* object, std::size_t offset) { return rd<T>(reinterpret_cast<std::uintptr_t>(object) + offset); }

bool inside(std::uintptr_t address, std::size_t bytes, unsigned flags) {
    for (const auto& s : segments)
        if ((s.flags & flags) == flags && address >= s.begin && address <= s.end && bytes <= s.end - address) return true;
    return false;
}
bool code(std::uintptr_t a) { return inside(a, 4, PF_R | PF_X); }

template <class F> F virt(const void* object, std::size_t slot) {
    if (!object) return nullptr;
    auto table = rd<std::uintptr_t>(object, 0);
    if (!inside(table, (slot + 1) * 8, PF_R)) return nullptr;
    auto fn = rd<std::uintptr_t>(table + slot * 8);
    return code(fn) ? reinterpret_cast<F>(fn) : nullptr;
}

bool locateGame() {
    segments.clear();
    dl_iterate_phdr([](dl_phdr_info* info, size_t, void*) -> int {
        std::string_view name(info->dlpi_name ? info->dlpi_name : "");
        auto slash = name.find_last_of('/');
        if (slash != name.npos) name.remove_prefix(slash + 1);
        if (name != "libminecraftpe.so") return 0;
        for (int i = 0; i < info->dlpi_phnum; ++i) {
            const auto& ph = info->dlpi_phdr[i];
            if (ph.p_type == PT_LOAD && (ph.p_flags & PF_R)) {
                auto b = info->dlpi_addr + ph.p_vaddr;
                segments.push_back({b, b + ph.p_memsz, ph.p_flags});
            }
        }
        return 1;
    }, nullptr);
    return !segments.empty();
}

// Finds the primary vtable (address of slot 0) of a class from its mangled RTTI name, e.g. "3Mob".
std::uintptr_t findVtable(const char* typeName, std::size_t minSlots) {
    const std::string needle = std::string(typeName) + '\0';
    std::vector<std::uintptr_t> names;
    for (const auto& s : segments) {
        if (s.flags & PF_W) continue; // type names live in read-only data
        auto* p = reinterpret_cast<const char*>(s.begin);
        auto* end = reinterpret_cast<const char*>(s.end);
        while (p < end) {
            auto* hit = static_cast<const char*>(memmem(p, end - p, needle.data(), needle.size()));
            if (!hit) break;
            names.push_back(reinterpret_cast<std::uintptr_t>(hit));
            p = hit + 1;
        }
    }
    if (names.empty()) return 0;
    std::vector<std::uintptr_t> typeInfos;
    for (const auto& s : segments) {
        if (s.flags & PF_X) continue;
        for (auto a = (s.begin + 15) & ~std::uintptr_t(7); a + 8 <= s.end; a += 8)
            if (std::find(names.begin(), names.end(), rd<std::uintptr_t>(a)) != names.end()) typeInfos.push_back(a - 8);
    }
    if (typeInfos.empty()) return 0;
    std::uintptr_t found = 0;
    for (const auto& s : segments) {
        if (s.flags & PF_X) continue;
        for (auto a = (s.begin + 15) & ~std::uintptr_t(7); a + 8 * (minSlots + 1) <= s.end; a += 8) {
            if (std::find(typeInfos.begin(), typeInfos.end(), rd<std::uintptr_t>(a)) == typeInfos.end()) continue;
            if (rd<std::intptr_t>(a - 8) != 0) continue; // offset-to-top 0: primary vtable only
            bool ok = true;
            for (std::size_t i = 0; i < minSlots && ok; ++i) ok = code(rd<std::uintptr_t>(a + 8 + i * 8));
            if (!ok) continue;
            if (found && found != a + 8) return 0; // ambiguous: refuse rather than guess
            found = a + 8;
        }
    }
    return found;
}

// Pattern: hex bytes separated by spaces, '?' for wildcard. Returns the single match in code, or 0.
std::uintptr_t findPattern(const char* text) {
    std::vector<int> bytes;
    for (const char* p = text; *p;) {
        while (*p == ' ') ++p;
        if (!*p) break;
        if (*p == '?') { bytes.push_back(-1); ++p; continue; }
        auto hex = [](char c) { return c <= '9' ? c - '0' : (c & ~0x20) - 'A' + 10; };
        bytes.push_back(hex(p[0]) * 16 + hex(p[1]));
        p += 2;
    }
    // Anchor the search on the first fully known 4-byte word so memmem does most of the work.
    std::size_t first = bytes.size();
    for (std::size_t k = 0; k + 4 <= bytes.size(); ++k)
        if (bytes[k] >= 0 && bytes[k + 1] >= 0 && bytes[k + 2] >= 0 && bytes[k + 3] >= 0) { first = k; break; }
    if (first == bytes.size()) return 0;
    const std::uint8_t anchor[4] = {static_cast<std::uint8_t>(bytes[first]), static_cast<std::uint8_t>(bytes[first + 1]),
                                    static_cast<std::uint8_t>(bytes[first + 2]), static_cast<std::uint8_t>(bytes[first + 3])};
    std::uintptr_t match = 0;
    for (const auto& s : segments) {
        if (!(s.flags & PF_X)) continue;
        auto* base = reinterpret_cast<const std::uint8_t*>(s.begin);
        std::size_t size = s.end - s.begin;
        for (std::size_t i = first; i + 4 <= size;) {
            auto* hit = static_cast<const std::uint8_t*>(memmem(base + i, size - i, anchor, 4));
            if (!hit) break;
            i = (hit - base) + 1;
            if (static_cast<std::size_t>(hit - base) < first) continue;
            std::size_t start = (hit - base) - first;
            if (start % 4 || start + bytes.size() > size) continue;
            bool ok = true;
            for (std::size_t k = 0; k < bytes.size() && ok; ++k) ok = bytes[k] < 0 || base[start + k] == bytes[k];
            if (!ok) continue;
            if (match) return 0;
            match = s.begin + start;
        }
    }
    return match;
}

std::uintptr_t branchTarget(std::uintptr_t at) {
    if (!code(at)) return 0;
    auto ins = rd<std::uint32_t>(at);
    if ((ins & 0xfc000000u) != 0x94000000u) return 0; // BL
    auto disp = static_cast<std::int64_t>(static_cast<std::int32_t>(ins << 6) >> 4);
    auto target = at + disp;
    return code(target) ? target : 0;
}

std::uintptr_t adrpAdd(std::uintptr_t at) {
    auto adrp = rd<std::uint32_t>(at), add = rd<std::uint32_t>(at + 4);
    if ((adrp & 0x9f000000u) != 0x90000000u || (add & 0xffc00000u) != 0x91000000u) return 0;
    std::uint32_t imm = ((adrp >> 5) & 0x7ffffu) << 2 | ((adrp >> 29) & 3u);
    std::int64_t page = static_cast<std::int32_t>(imm << 11) >> 11;
    return (at & ~std::uintptr_t(0xfff)) + page * 4096 + ((add >> 10) & 0xfffu);
}

// ---- Resolved game functions ----
using Fn0 = void* (*)(void*);
using GetItemFn = const void* (*)(void*, int);
using DamageFn = int (*)(const void*, const void*);
using MaxDamageFn = short (*)(const void*);
using FinishFn = void (*)(void*);

Fn0 handContainerOf = nullptr;
Fn0 armorContainerOf = nullptr;
DamageFn damageOf = nullptr;
std::uintptr_t localPlayerVtable = 0;
FinishFn finishNext = nullptr;
std::atomic_bool ready{false};
std::atomic_bool wanted{true};
std::string lastError = "not started";

// ---- Snapshot handed to Java ----
struct Slot { bool present; int damage, max, count; char name[64]; };
constexpr int kSlots = 6; // helmet, chestplate, leggings, boots, main hand, offhand
std::mutex snapMutex;
Slot snapshot[kSlots];
long long snapshotAt = 0;

long long nowMs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000LL + ts.tv_nsec / 1000000;
}

void readName(const void* item, char* out, std::size_t cap) {
    out[0] = 0;
    auto base = reinterpret_cast<std::uintptr_t>(item) + kItemFullName;
    auto flag = rd<std::uint8_t>(base);
    const char* data;
    std::size_t size;
    if (flag & 1) { // libc++ long string
        size = rd<std::size_t>(base + 8);
        data = rd<const char*>(base + 16);
        if (!data || size > 200) return;
    } else {
        size = flag >> 1;
        data = reinterpret_cast<const char*>(base + 1);
        if (size > 22) return;
    }
    size = std::min(size, cap - 1);
    for (std::size_t i = 0; i < size; ++i) {
        char c = data[i];
        if (c < 0x20 || c > 0x7e) { out[0] = 0; return; }
        out[i] = c;
    }
    out[size] = 0;
    if (!std::strchr(out, ':')) out[0] = 0; // not a "namespace:item" id: don't trust it
}

void readStack(const void* stack, Slot& slot) {
    slot = {};
    if (!stack) return;
    if (!rd<std::uint8_t>(stack, kStackValid) || !rd<std::uint8_t>(stack, kStackCount)) return;
    auto handle = rd<const void*>(stack, kStackItem);
    const void* item = handle ? rd<const void*>(handle, 0) : nullptr;
    if (!item || !inside(rd<std::uintptr_t>(item, 0), 8, PF_R)) return;
    slot.present = true;
    slot.count = rd<std::uint8_t>(stack, kStackCount);
    if (auto maxFn = virt<MaxDamageFn>(item, kItemMaxDamageSlot)) slot.max = static_cast<unsigned short>(maxFn(item));
    if (slot.max > 0 && damageOf) slot.damage = damageOf(item, rd<const void*>(stack, kStackUserData));
    if (slot.damage < 0 || slot.damage > slot.max) slot.damage = 0;
    readName(item, slot.name, sizeof slot.name);
}

void takeSnapshot(void* context) {
    auto client = rd<void*>(context, kUiClient);
    auto getPlayer = virt<Fn0>(client, kClientLocalPlayerSlot);
    void* player = getPlayer ? getPlayer(client) : nullptr;
    Slot fresh[kSlots] = {};
    bool inGame = player && rd<std::uintptr_t>(player, 0) == localPlayerVtable;
    if (inGame) {
        void* entity = static_cast<char*>(player) + kActorEntityContext;
        if (void* armor = armorContainerOf(entity)) {
            auto getItem = virt<GetItemFn>(armor, kContainerGetItemSlot);
            for (int i = 0; getItem && i < 4; ++i) readStack(getItem(armor, i), fresh[i]);
        }
        if (auto carried = virt<const void* (*)(void*)>(player, kActorCarriedSlot)) readStack(carried(player), fresh[4]);
        if (void* hand = handContainerOf(entity)) {
            if (auto getItem = virt<GetItemFn>(hand, kContainerGetItemSlot)) readStack(getItem(hand, 1), fresh[5]);
        }
    }
    std::lock_guard lock(snapMutex);
    std::memcpy(snapshot, fresh, sizeof fresh);
    snapshotAt = inGame ? nowMs() : 0;
}

void finishHook(void* context) {
    if (ready.load(std::memory_order_acquire) && wanted.load(std::memory_order_relaxed) && context) {
        static thread_local long long last = 0;
        long long now = nowMs();
        if (now - last >= 100) { // 10 updates a second is plenty for a HUD
            last = now;
            takeSnapshot(context);
        }
    }
    finishNext(context);
}

bool resolveAndHook() {
    if (!locateGame()) { lastError = "Minecraft library not loaded"; return false; }

    auto uiTable = findVtable("24MinecraftUIRenderContext", 34);
    auto mobTable = findVtable("3Mob", 80);
    auto itemTable = findVtable("4Item", 40);
    localPlayerVtable = findVtable("11LocalPlayer", 80);
    if (!uiTable || !mobTable || !itemTable || !localPlayerVtable) { lastError = "class tables not found"; return false; }

    // Item::getMaxDamage on the base class must read the short at 0x110 (ldrh w0,[x0,#0x110]; ret).
    auto maxDamage = rd<std::uintptr_t>(itemTable + kItemMaxDamageSlot * 8);
    if (rd<std::uint32_t>(maxDamage) != 0x79422000u || rd<std::uint32_t>(maxDamage + 4) != 0xd65f03c0u) {
        lastError = "item durability layout changed"; return false;
    }

    // Mob::getCarriedItem = { stp; mov x29,sp; add x0,x0,#8; bl handContainer; ... getItem(0) }.
    auto mobCarried = rd<std::uintptr_t>(mobTable + kActorCarriedSlot * 8);
    if (!code(mobCarried) || rd<std::uint32_t>(mobCarried + 8) != 0x91002000u) { lastError = "equipment layout changed"; return false; }
    auto hand = branchTarget(mobCarried + 12);
    auto armor = hand ? hand + kArmorGetterDistance : 0;
    // Both getters look up the same component (same type hash), the armor one returns the 2nd pointer.
    if (!hand || !code(armor) || rd<std::uint64_t>(hand) != rd<std::uint64_t>(armor) ||
        rd<std::uint32_t>(hand + 0xc) != rd<std::uint32_t>(armor + 0xc) ||
        rd<std::uint32_t>(hand + 0x10) != rd<std::uint32_t>(armor + 0x10) ||
        rd<std::uint32_t>(armor + 0xc0) != 0xf9400500u /* ldr x0,[x8,#8] */) {
        lastError = "armor container not found"; return false;
    }
    handContainerOf = reinterpret_cast<Fn0>(hand);
    armorContainerOf = reinterpret_cast<Fn0>(armor);

    // Item::getDamageValue(CompoundTag const*): reads the "Damage" tag. Optional (durability shows full without it).
    auto damage = findPattern("? ? 00 B4 FD 7B BE A9 F3 0B 00 F9 FD 03 00 91 F3 03 01 AA ? ? ? ? ? ? ? 91 E0 03 13 AA "
                              "C2 00 80 52 ? ? ? 94 ? ? ? 36 ? ? ? ? ? ? ? 91 E0 03 13 AA C2 00 80 52 ? ? ? 94 "
                              "F3 0B 40 F9 FD 7B C2 A8 C0 03 5F D6 E0 03 1F 2A C0 03 5F D6");
    if (damage) {
        auto key = adrpAdd(damage + 20);
        if (key && inside(key, 7, PF_R) && std::memcmp(reinterpret_cast<const void*>(key), "Damage", 7) == 0)
            damageOf = reinterpret_cast<DamageFn>(damage);
    }
    if (!damageOf) LOGE("Damage reader not found; durability will show as full");

    // Hook the UI render context's end-of-frame call through the LeviLauncher preloader.
    void* preloader = dlopen("libpreloader.so", RTLD_NOW | RTLD_NOLOAD);
    using HookFn = int (*)(void*, void*, void**, int);
    auto hook = preloader ? reinterpret_cast<HookFn>(dlsym(preloader, "_ZN2pl6memory4hookEPvS1_PS1_NS0_12HookPriorityE")) : nullptr;
    if (!hook) { lastError = "game loader hook API missing"; return false; }
    auto finish = rd<std::uintptr_t>(uiTable + kUiFinishSlot * 8);
    if (!code(finish) || hook(reinterpret_cast<void*>(finish), reinterpret_cast<void*>(finishHook),
                              reinterpret_cast<void**>(&finishNext), 200) != 0 || !finishNext) {
        lastError = "could not hook frame end"; return false;
    }
    lastError.clear();
    LOGI("Ghost PvP ready (durability %s)", damageOf ? "on" : "off");
    return true;
}

std::mutex initMutex;
bool initDone = false;

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_shadow_GhostPvpNative_nativeInit(JNIEnv*, jclass) {
    std::lock_guard lock(initMutex);
    if (initDone) return ready.load() ? JNI_TRUE : JNI_FALSE;
    initDone = true;
    bool ok = false;
    try { ok = resolveAndHook(); } catch (...) { lastError = "unexpected error"; }
    if (!ok) LOGE("Ghost PvP unavailable: %s", lastError.c_str());
    ready.store(ok, std::memory_order_release);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_shadow_GhostPvpNative_nativeStatus(JNIEnv* env, jclass) {
    return env->NewStringUTF(ready.load() ? "ready" : lastError.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_shadow_GhostPvpNative_nativeSetEnabled(JNIEnv*, jclass, jboolean on) {
    wanted.store(on == JNI_TRUE);
}

// Returns "" when not in a world, else 6 lines "name|damage|max|count" (empty name line = empty slot).
extern "C" JNIEXPORT jstring JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_shadow_GhostPvpNative_nativeEquipment(JNIEnv* env, jclass) {
    Slot copy[kSlots];
    long long at;
    {
        std::lock_guard lock(snapMutex);
        std::memcpy(copy, snapshot, sizeof copy);
        at = snapshotAt;
    }
    if (!at || nowMs() - at > 1500) return env->NewStringUTF("");
    std::string out;
    for (int i = 0; i < kSlots; ++i) {
        if (copy[i].present) {
            out += copy[i].name[0] ? copy[i].name : "?";
            out += '|' + std::to_string(copy[i].damage) + '|' + std::to_string(copy[i].max) + '|' + std::to_string(copy[i].count);
        }
        out += '\n';
    }
    return env->NewStringUTF(out.c_str());
}
