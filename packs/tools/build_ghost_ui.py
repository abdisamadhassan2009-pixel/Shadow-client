import json, os, random, uuid, math, sys, zipfile
from PIL import Image, ImageDraw, ImageFilter
OUT, LOGO, GHOST = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(f'{OUT}/textures/ui', exist_ok=True)

# --- buttons: 4x4 nine-slice bevels (same layout and nineslice as vanilla) ---
def bevel(name, top, center, bottom):
    im = Image.new('RGBA', (4, 4), center + (255,))
    for i in range(4):
        im.putpixel((i, 0), top + (255,)); im.putpixel((0, i), top + (255,))
        im.putpixel((i, 3), bottom + (255,)); im.putpixel((3, i), bottom + (255,))
    im.save(f'{OUT}/textures/ui/{name}.png')
    json.dump({"nineslice_size": 1, "base_size": [4, 4]}, open(f'{OUT}/textures/ui/{name}.json', 'w'), indent=2)
L_TOP, L_MID, L_BOT = (230, 235, 255), (185, 195, 230), (74, 83, 120)      # ghost grey (dark text stays readable)
B_TOP, B_MID, B_BOT = (157, 178, 255), (78, 99, 216), (38, 48, 122)        # Ghost blue (replaces vanilla green)
D_TOP, D_MID, D_BOT = (58, 63, 85), (35, 38, 58), (17, 19, 28)             # dark navy
bevel('button_borderless_light', L_TOP, L_MID, L_BOT)
bevel('button_borderless_lighthover', B_TOP, B_MID, B_BOT)
bevel('button_borderless_lightpressed', B_BOT, B_MID, B_TOP)
bevel('button_borderless_lightpressednohover', L_BOT, (142, 152, 190), L_TOP)
bevel('button_borderless_dark', D_TOP, D_MID, D_BOT)
bevel('button_borderless_darkhover', (127, 147, 232), (47, 60, 158), (26, 34, 96))
bevel('button_borderless_darkpressed', (26, 34, 96), (47, 60, 158), (127, 147, 232))
bevel('button_borderless_darkpressednohover', D_BOT, D_MID, D_TOP)

# --- title: ghost + GHOST CLIENT side by side on vanilla's 1937x333 canvas ---
logo = Image.open(LOGO).convert('RGBA')
a = logo.getchannel('A')
text_part = logo.crop((0, 505, logo.width, logo.height))
text_part = text_part.crop(text_part.getchannel('A').point(lambda v: 255 if v > 20 else 0).getbbox())
ghost = Image.open(GHOST).convert('RGBA')
ghost = ghost.crop(ghost.getchannel('A').point(lambda v: 255 if v > 20 else 0).getbbox())
W, H = 1937, 333
title = Image.new('RGBA', (W, H), (0, 0, 0, 0))
gh = 320; g = ghost.resize((int(ghost.width * gh / ghost.height), gh), Image.LANCZOS)
th = 190; tx = text_part.resize((int(text_part.width * th / text_part.height), th), Image.LANCZOS)
gap = 40; total = g.width + gap + tx.width
if total > W - 20:
    s = (W - 20) / total; g = g.resize((int(g.width * s), int(g.height * s)), Image.LANCZOS); tx = tx.resize((int(tx.width * s), int(tx.height * s)), Image.LANCZOS); gap = int(gap * s); total = g.width + gap + tx.width
x0 = (W - total) // 2
title.alpha_composite(g, (x0, (H - g.height) // 2))
title.alpha_composite(tx, (x0 + g.width + gap, (H - tx.height) // 2 + 10))
title.save(f'{OUT}/textures/ui/title.png', optimize=True)

# --- panorama: dark ghostly night, stars, blocky hills, a faint ghost in the sky ---
S = 1024
random.seed(13)
def sky_color(y):  # y 0..1 from top of side face
    top, hor = (8, 10, 22), (40, 44, 92)
    t = min(1, max(0, y / 0.62)) ** 1.6
    return tuple(int(top[i] + (hor[i] - top[i]) * t) for i in range(3))
def hill(xg):  # continuous across the 4 side faces, xg in 0..4
    return 0.66 + 0.035 * math.sin(xg * 2 * math.pi * 1.5) + 0.02 * math.sin(xg * 2 * math.pi * 4.3 + 1)
B = 16  # block size for the pixel look
for face in range(4):
    im = Image.new('RGB', (S, S)); d = ImageDraw.Draw(im)
    for by in range(0, S, B):
        c = sky_color(by / S)
        for bx in range(0, S, B):
            j = random.choice([0, 0, 0, 3, -3])
            d.rectangle([bx, by, bx + B - 1, by + B - 1], fill=tuple(max(0, min(255, v + j)) for v in c))
    for _ in range(140):
        x, y = random.randrange(0, S, 4), random.randrange(0, int(S * 0.55), 4)
        r = random.choice([2, 2, 3, 4]); v = random.randint(170, 255)
        d.rectangle([x, y, x + r, y + r], fill=(v, v, min(255, v + 20)))
    for bx in range(0, S, B):
        top = int(hill(face + bx / S) * S) // B * B
        d.rectangle([bx, top, bx + B - 1, S], fill=(12, 13, 24))
        d.rectangle([bx, top, bx + B - 1, top + B - 1], fill=(26, 32, 58))
    if face == 0:
        gg = ghost.resize((260, int(ghost.height * 260 / ghost.width)), Image.LANCZOS)
        alpha = gg.getchannel('A').point(lambda v: int(v * 0.18)); gg.putalpha(alpha)
        base = im.convert('RGBA'); base.alpha_composite(gg, (S // 2 - 130, int(S * 0.22))); im = base.convert('RGB')
    im.save(f'{OUT}/textures/ui/panorama_{face}.png', optimize=True)
top_im = Image.new('RGB', (S, S), (8, 10, 22)); d = ImageDraw.Draw(top_im)
for _ in range(220):
    x, y = random.randrange(0, S, 4), random.randrange(0, S, 4); r = random.choice([2, 2, 3]); v = random.randint(150, 255)
    d.rectangle([x, y, x + r, y + r], fill=(v, v, min(255, v + 20)))
top_im.save(f'{OUT}/textures/ui/panorama_4.png', optimize=True)
Image.new('RGB', (S, S), (12, 13, 24)).save(f'{OUT}/textures/ui/panorama_5.png', optimize=True)

# --- pack icon and manifest ---
icon = Image.new('RGBA', (256, 256), (7, 8, 11, 255))
gi = ghost.resize((200, int(ghost.height * 200 / ghost.width)), Image.LANCZOS)
icon.alpha_composite(gi, ((256 - gi.width) // 2, (256 - gi.height) // 2))
icon.convert('RGB').save(f'{OUT}/pack_icon.png', optimize=True)
manifest = {
  "format_version": 2,
  "header": {"name": "Ghost Client UI", "description": "Ghost Client main menu: ghost logo, dark night background and Ghost-blue buttons.\ndiscord.gg/shadowpvp",
             "uuid": str(uuid.uuid4()), "version": [1, 0, 0], "min_engine_version": [1, 21, 0]},
  "modules": [{"type": "resources", "uuid": str(uuid.uuid4()), "version": [1, 0, 0]}],
  "metadata": {"authors": ["Oblivexor"]}
}
json.dump(manifest, open(f'{OUT}/manifest.json', 'w'), indent=2)
print('built', OUT)
