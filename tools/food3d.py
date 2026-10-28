"""3D food: every vanilla food item gets a puffy 3D model built from the shape of its own 16x16 picture - thick in the
middle, thin at the edges (a round apple, a fat loaf, a thin cookie). The models only point at the game's textures
(nothing of Minecraft's is copied); in the inventory they look exactly like before (front view).
Usage: python3 food3d.py path/to/minecraft-client.jar (any jar that has assets/minecraft/textures/item/*.png)"""
import io, json, os, sys, zipfile
from collections import deque
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'minecraft', 'models', 'item')

# item -> (texture, thickest it gets in pixels)
FOODS = {
    "apple": ("apple", 5), "golden_apple": ("golden_apple", 5), "enchanted_golden_apple": ("golden_apple", 5),
    "bread": ("bread", 4), "cookie": ("cookie", 1.5), "pumpkin_pie": ("pumpkin_pie", 3),
    "beef": ("beef", 2.5), "cooked_beef": ("cooked_beef", 2.5), "porkchop": ("porkchop", 2.5), "cooked_porkchop": ("cooked_porkchop", 2.5),
    "chicken": ("chicken", 3.5), "cooked_chicken": ("cooked_chicken", 3.5), "mutton": ("mutton", 2.5), "cooked_mutton": ("cooked_mutton", 2.5),
    "rabbit": ("rabbit", 3), "cooked_rabbit": ("cooked_rabbit", 3), "rotten_flesh": ("rotten_flesh", 2),
    "cod": ("cod", 3), "cooked_cod": ("cooked_cod", 3), "salmon": ("salmon", 3), "cooked_salmon": ("cooked_salmon", 3),
    "tropical_fish": ("tropical_fish", 3), "pufferfish": ("pufferfish", 4),
    "carrot": ("carrot", 3), "golden_carrot": ("golden_carrot", 3), "potato": ("potato", 4), "baked_potato": ("baked_potato", 4),
    "poisonous_potato": ("poisonous_potato", 4), "beetroot": ("beetroot", 4), "melon_slice": ("melon_slice", 2),
    "sweet_berries": ("sweet_berries", 3), "glow_berries": ("glow_berries", 3), "chorus_fruit": ("chorus_fruit", 4),
    "dried_kelp": ("dried_kelp", 1), "spider_eye": ("spider_eye", 3),
    "mushroom_stew": ("mushroom_stew", 3), "beetroot_soup": ("beetroot_soup", 3), "rabbit_stew": ("rabbit_stew", 3),
    "suspicious_stew": ("suspicious_stew", 3),
}

# the display transforms of minecraft:item/generated (so they sit in hands, frames and on the ground the same way)
DISPLAY = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
}


def distances(mask):
    """steps from each opaque pixel to the nearest transparent one (or the edge), 8-connected"""
    INF = 99
    d = [[INF if mask[y][x] else 0 for x in range(16)] for y in range(16)]
    q = deque()
    for y in range(16):
        for x in range(16):
            if not mask[y][x]:
                q.append((x, y))
            elif x in (0, 15) or y in (0, 15):
                d[y][x] = 1
                q.append((x, y))
    while q:
        x, y = q.popleft()
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and d[ny][nx] > d[y][x] + 1:
                    d[ny][nx] = d[y][x] + 1
                    q.append((nx, ny))
    return d


def model(texture, thick):
    mask, d = texture
    elems = []
    for py in range(16):
        x = 0
        while x < 16:
            if not mask[py][x]:
                x += 1
                continue
            t = min(thick, 0.75 + 0.75 * d[py][x]) if thick > 1 else thick
            x1 = x
            while x1 + 1 < 16 and mask[py][x1 + 1] and (min(thick, 0.75 + 0.75 * d[py][x1 + 1]) if thick > 1 else thick) == t:
                x1 += 1
            y0, y1 = 15 - py, 16 - py
            z0, z1 = 8 - t / 2, 8 + t / 2
            run = [x, py, x1 + 1, py + 1]
            faces = {
                "south": {"uv": run, "texture": "#t"},
                "north": {"uv": [x1 + 1, py, x, py + 1], "texture": "#t"},
                "up": {"uv": run, "texture": "#t"},
                "down": {"uv": run, "texture": "#t"},
                "west": {"uv": [x, py, x + 1, py + 1], "texture": "#t"},
                "east": {"uv": [x1, py, x1 + 1, py + 1], "texture": "#t"},
            }
            elems.append({"from": [x, y0, round(z0, 3)], "to": [x1 + 1, y1, round(z1, 3)], "faces": faces})
            x = x1 + 1
    return elems


def main(jar):
    os.makedirs(OUT, exist_ok=True)
    z = zipfile.ZipFile(jar)
    for item, (tex, thick) in FOODS.items():
        im = Image.open(io.BytesIO(z.read('assets/minecraft/textures/item/%s.png' % tex))).convert('RGBA')
        im = im.crop((0, 0, 16, 16))
        mask = [[im.getpixel((x, y))[3] > 16 for x in range(16)] for y in range(16)]
        m = {"gui_light": "front", "textures": {"t": "minecraft:item/" + tex, "particle": "minecraft:item/" + tex},
             "elements": model((mask, distances(mask)), thick), "display": DISPLAY}
        with open(os.path.join(OUT, item + '.json'), 'w') as f:
            json.dump(m, f, separators=(',', ':'))
    print(len(FOODS), 'food models')


if __name__ == '__main__':
    main(sys.argv[1])
