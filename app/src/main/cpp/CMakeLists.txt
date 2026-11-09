cmake_minimum_required(VERSION 3.22)
project(levi LANGUAGES CXX)

set(CMAKE_BUILD_TYPE RelWithDebInfo CACHE STRING "Build type" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared libraries" FORCE)

if(ANDROID)
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
    add_link_options("-Wl,-z,max-page-size=16384")
endif()

add_subdirectory(preloader)
add_subdirectory(libHttpClient/Build/libHttpClient.Android)
add_subdirectory(worldtools)

# Shadow Client: empty stand-in for the removed Discord SDK so the prebuilt libgxcore.so can load.
add_library(discord_partner_sdk SHARED shadow_stubs/discord_partner_sdk_stub.cpp)
set_target_properties(discord_partner_sdk PROPERTIES OUTPUT_NAME "discord_partner_sdk")

# Ghost Client: in-game PvP data (Armor HUD). Reads equipment; changes nothing in the game.
add_library(ghostpvp SHARED ghostpvp/ghostpvp.cpp)
target_compile_features(ghostpvp PRIVATE cxx_std_20)
target_compile_options(ghostpvp PRIVATE -O2 -fvisibility=hidden -Wall)
target_link_libraries(ghostpvp PRIVATE log dl)
