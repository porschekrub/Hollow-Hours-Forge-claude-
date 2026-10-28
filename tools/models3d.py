"""3D item models (JSON elements + textures) for the flashlight (off / on) and the battery."""
from PIL import Image
import json, os

A = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours')


def save_png(img, *p):
    path = os.path.join(A, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def save_json(obj, *p):
    path = os.path.join(A, *p)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=1)


# ---------------------------------------------------------------- textures (16x16 each)
def flash_tex(on):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = im.load()
    # body (0..8, 0..8): gunmetal with grip ridges
    for y in range(8):
        for x in range(8):
            base = 52 + (8 if x in (1, 2) else 0) - (10 if x == 7 else 0)
            if y % 2 == 1 and 2 <= y <= 6:
                base -= 14
            p[x, y] = (base, base, base + 6, 255)
    # chrome ring (8..12, 0..4)
    for y in range(4):
        for x in range(8, 12):
            v = 200 - y * 25 + (x - 8) * 5
            p[x, y] = (v, v, v + 8, 255)
    # lens (12..16, 0..4)
    for y in range(4):
        for x in range(12, 16):
            d = abs(x - 13.5) + abs(y - 1.5)
            if on:
                p[x, y] = (255, 250, 200, 255) if d < 1.6 else (255, 225, 140, 255)
            else:
                p[x, y] = (70, 80, 95, 255) if d < 1.6 else (40, 46, 56, 255)
    # switch (8..10, 4..6)
    for y in range(4, 6):
        for x in range(8, 10):
            p[x, y] = (230, 120, 30, 255) if on else (170, 70, 20, 255)
    # tail cap (0..4, 8..12): rubber with ridges
    for y in range(8, 12):
        for x in range(4):
            p[x, y] = (28, 28, 30, 255) if (x + y) % 2 else (38, 38, 42, 255)
    # head shell (4..8, 8..12)
    for y in range(8, 12):
        for x in range(4, 8):
            v = 70 - (y - 8) * 6
            p[x, y] = (v, v, v + 8, 255)
    return im


save_png(flash_tex(False), 'textures', 'item', 'flashlight_3d.png')
save_png(flash_tex(True), 'textures', 'item', 'flashlight_3d_on.png')

bat = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
b = bat.load()
for y in range(12):
    for x in range(8):
        if y < 5:
            c = (205, 125, 45, 255) if x not in (0, 7) else (170, 100, 35, 255)   # copper top
        else:
            c = (30, 30, 34, 255) if x not in (0, 7) else (20, 20, 22, 255)        # black body
        if y == 5:
            c = (230, 210, 120, 255)
        b[x, y] = c
for (x, y) in [(3, 7), (4, 7), (3, 8), (4, 8), (2, 8), (5, 8)]:
    b[x, y] = (220, 220, 220, 255)
for y in range(12, 16):
    for x in range(8, 12):
        b[x, y] = (200, 200, 205, 255)       # terminals
for y in range(12, 16):
    for x in range(0, 4):
        b[x, y] = (170, 100, 35, 255)        # top cap
save_png(bat, 'textures', 'item', 'battery_3d.png')


# ---------------------------------------------------------------- models
def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def all_faces(uv_side, uv_end, tex="#t"):
    return {d: {"uv": uv_side if d in ("east", "west", "up", "down") else uv_end, "texture": tex} for d in ("north", "south", "east", "west", "up", "down")}


def flashlight_model(tex):
    elems = [
        box([6.5, 6.5, 3.5], [9.5, 9.5, 14], {**all_faces([0, 0, 8, 8], [0, 0, 3, 3]), "north": {"uv": [0, 0, 3, 3], "texture": "#t"}}),
        box([5.5, 5.5, 0.5], [10.5, 10.5, 3.5], {**all_faces([4, 8, 8, 12], [8, 0, 12, 4]),
                                                  "north": {"uv": [8, 0, 12, 4], "texture": "#t"}}),
        box([6, 6, 0.3], [10, 10, 0.5], {"north": {"uv": [12, 0, 16, 4], "texture": "#t"}}),
        box([7.5, 9.5, 6], [8.5, 10.2, 8.5], all_faces([8, 4, 10, 6], [8, 4, 10, 6])),
        box([6.25, 6.25, 14], [9.75, 9.75, 15.5], all_faces([0, 8, 4, 12], [0, 8, 4, 12])),
    ]
    return {
        "textures": {"t": tex, "particle": tex},
        "elements": elems,
        "display": {
            "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [1.0, 2.5, -2.5], "scale": [0.85, 0.85, 0.85]},
            "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [1.0, 2.5, -2.5], "scale": [0.85, 0.85, 0.85]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, -0.5], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, -0.5], "scale": [0.6, 0.6, 0.6]},
            "gui": {"rotation": [30, 135, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        },
    }


fl = flashlight_model("hollowhours:item/flashlight_3d")
fl["overrides"] = [{"predicate": {"hollowhours:on": 1}, "model": "hollowhours:item/flashlight_on"}]
save_json(fl, 'models', 'item', 'flashlight.json')
save_json(flashlight_model("hollowhours:item/flashlight_3d_on"), 'models', 'item', 'flashlight_on.json')

battery = {
    "textures": {"t": "hollowhours:item/battery_3d", "particle": "hollowhours:item/battery_3d"},
    "elements": [
        box([6.5, 3, 6.5], [9.5, 11, 9.5], {d: {"uv": [0, 0, 8, 12] if d not in ("up", "down") else [0, 12, 4, 16], "texture": "#t"}
                                            for d in ("north", "south", "east", "west", "up", "down")}),
        box([7.5, 11, 7.5], [8.5, 11.8, 8.5], {d: {"uv": [8, 12, 12, 16], "texture": "#t"} for d in ("north", "south", "east", "west", "up")}),
    ],
    "display": {
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.9, 0.9, 0.9]},
        "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.9, 0.9, 0.9]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
        "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [1.3, 1.3, 1.3]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    },
}
save_json(battery, 'models', 'item', 'battery.json')
print('ok')
