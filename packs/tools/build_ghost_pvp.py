import json, os, sys, uuid
from PIL import Image
OUT, VAN, GHOST = sys.argv[1], sys.argv[2], sys.argv[3]
for d in ('textures/blocks', 'textures/misc', 'textures/gui'):
    os.makedirs(f'{OUT}/{d}', exist_ok=True)

# Low fire: shift every 16x16 animation frame down so flames sit lower on screen.
def low_fire(src, dst, shift=7):
    im = Image.open(src).convert('RGBA'); w, h = im.size
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for fy in range(0, h, w):
        frame = im.crop((0, fy, w, fy + w))
        out.paste(frame.crop((0, 0, w, w - shift)), (0, fy + shift))
    out.save(dst)
low_fire(f'{VAN}/fire_0.png', f'{OUT}/textures/blocks/fire_0.png')
low_fire(f'{VAN}/fire_1.png', f'{OUT}/textures/blocks/fire_1.png')
low_fire(f'{VAN}/flame_atlas.png', f'{OUT}/textures/flame_atlas.png')

# No pumpkin blur.
Image.new('RGBA', (256, 256), (0, 0, 0, 0)).save(f'{OUT}/textures/misc/pumpkinblur.png')

# Ghost crosshair: replace only the crosshair cell (top-left 16x16) of icons.png.
icons = Image.open(f'{VAN}/icons.png').convert('RGBA')
cell = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
W = (255, 255, 255, 255)
for i in list(range(3, 6)) + list(range(10, 13)):
    cell.putpixel((i, 7), W); cell.putpixel((i, 8), W)   # horizontal arms
    cell.putpixel((7, i), W); cell.putpixel((8, i), W)   # vertical arms
for x in (7, 8):
    for y in (7, 8):
        cell.putpixel((x, y), W)                          # centre dot
icons.paste(cell, (0, 0))
icons.save(f'{OUT}/textures/gui/icons.png')

ghost = Image.open(GHOST).convert('RGBA')
icon = Image.new('RGBA', (256, 256), (78, 99, 216, 255))
gi = ghost.resize((190, int(ghost.height * 190 / ghost.width)), Image.LANCZOS)
icon.alpha_composite(gi, ((256 - gi.width) // 2, (256 - gi.height) // 2))
icon.convert('RGB').save(f'{OUT}/pack_icon.png')
json.dump({
  "format_version": 2,
  "header": {"name": "Ghost Client PvP", "description": "Low fire, no pumpkin blur and a clean Ghost crosshair.\ndiscord.gg/shadowpvp",
             "uuid": str(uuid.uuid4()), "version": [1, 0, 0], "min_engine_version": [1, 21, 0]},
  "modules": [{"type": "resources", "uuid": str(uuid.uuid4()), "version": [1, 0, 0]}],
  "metadata": {"authors": ["Oblivexor"]}
}, open(f'{OUT}/manifest.json', 'w'), indent=2)
print('built')
