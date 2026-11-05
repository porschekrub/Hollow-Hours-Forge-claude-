"""Art for fighting back (2.11): 3D gun models built from boxes, pixel-art blades, ammo, the grenade, three armour sets
(item icons + the armour textures worn on the body)."""
import json, os, random
from PIL import Image, ImageDraw

A = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours')


def save_png(img, *p):
    path = os.path.join(A, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def save_json(obj, *p):
    path = os.path.join(A, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=1)


# ----------------------------------------------------------------------------- gun palette (4x4 cells of 4x4 px)
PAL = {
    'metal_dark': (46, 48, 54), 'metal_mid': (86, 90, 98), 'metal_light': (150, 154, 160), 'black': (24, 24, 26),
    'olive': (88, 96, 58), 'olive_dark': (62, 68, 40), 'wood': (112, 72, 40), 'wood_light': (150, 102, 60),
    'tan': (176, 150, 104), 'glass': (90, 170, 220), 'red': (230, 40, 40), 'brass': (200, 160, 70),
    'grey': (110, 110, 110), 'orange': (220, 120, 40), 'white': (230, 230, 230), 'green': (60, 140, 60),
}
CELLS = {k: (i % 4, i // 4) for i, k in enumerate(PAL)}


def palette():
    im = Image.new('RGBA', (16, 16))
    p = im.load()
    rnd = random.Random(2)
    for k, (c, r) in CELLS.items():
        base = PAL[k]
        for y in range(4):
            for x in range(4):
                n = rnd.randint(-6, 6)
                p[c * 4 + x, r * 4 + y] = tuple(max(0, min(255, v + n)) for v in base) + (255,)
    return im


def uv(color):
    c, r = CELLS[color]
    return [c * 4 + 1, r * 4 + 1, c * 4 + 3, r * 4 + 3]


def box(x0, y0, z0, x1, y1, z1, color):
    u = uv(color)
    return {"from": [x0, y0, z0], "to": [x1, y1, z1],
            "faces": {f: {"uv": u, "texture": "#p"} for f in ("north", "south", "east", "west", "up", "down")}}


GUN_DISPLAY = {
    "gui": {"rotation": [15, -30, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1.5, 1.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 1.5, 1.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, 95, 0], "translation": [1.5, 3.2, 0.5], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"rotation": [0, 95, 0], "translation": [1.5, 3.2, 0.5], "scale": [0.8, 0.8, 0.8]},
}

GUNS = {
    'pistol': [
        (4, 9, 7, 13, 11, 9, 'metal_dark'), (4, 8, 7.2, 12, 9, 8.8, 'metal_mid'), (13, 9.5, 7.5, 13.8, 10.5, 8.5, 'black'),
        (4.4, 4, 7.2, 6.8, 8, 8.8, 'black'), (5.6, 4, 7.25, 7.2, 6, 8.75, 'black'), (7, 6.6, 7.6, 9.2, 7.1, 8.4, 'metal_mid'),
        (8.8, 6.6, 7.6, 9.3, 8, 8.4, 'metal_mid'), (12, 11, 7.7, 12.7, 11.5, 8.3, 'black'), (4.2, 11, 7.6, 5, 11.4, 8.4, 'black'),
        (7.6, 7.1, 7.85, 8.1, 8, 8.15, 'black'),
    ],
    'rifle': [
        (0, 7, 7, 4, 10, 9, 'olive_dark'), (0, 6.5, 7.2, 1, 10.2, 8.8, 'black'), (4, 8, 6.8, 10, 11, 9.2, 'metal_dark'),
        (10, 8.4, 7, 14, 10.6, 9, 'olive'), (14, 9, 7.6, 16, 10, 8.4, 'black'), (7, 4.6, 7.2, 8.8, 8, 8.8, 'metal_mid'),
        (7.5, 3.4, 7.2, 9.4, 4.8, 8.8, 'metal_mid'), (5, 5, 7.3, 6.5, 8, 8.7, 'black'), (5.5, 11, 7.4, 10, 11.8, 8.6, 'black'),
        (7, 11.8, 7.5, 9.4, 13, 8.5, 'black'), (9.4, 12, 7.8, 9.8, 12.8, 8.2, 'red'), (13, 10.6, 7.7, 13.6, 11.6, 8.3, 'black'),
        (6.5, 7.5, 9.2, 7.3, 8.3, 9.5, 'metal_mid'),
    ],
    'shotgun': [
        (0, 6.5, 7, 4.5, 9.5, 9, 'wood'), (0, 6, 7.2, 0.8, 9.8, 8.8, 'black'), (4.5, 8, 7, 8.5, 10.6, 9, 'metal_dark'),
        (8.5, 9.4, 7.4, 16, 10.4, 8.6, 'metal_mid'), (8.5, 8.4, 7.5, 15, 9.2, 8.5, 'metal_dark'), (10, 7.8, 7, 13, 9.5, 9, 'wood_light'),
        (4.8, 5.8, 7.3, 6.3, 8, 8.7, 'wood'), (6, 6.8, 7.7, 7.6, 7.2, 8.3, 'metal_mid'), (15.3, 10.4, 7.8, 15.8, 10.9, 8.2, 'brass'),
    ],
    'sniper': [
        (0, 6.5, 7, 4.5, 9.5, 9, 'tan'), (0, 6, 7.2, 0.7, 9.8, 8.8, 'black'), (1, 9.5, 7.3, 4, 10.3, 8.7, 'tan'),
        (4.5, 8, 7, 9, 10, 9, 'metal_dark'), (9, 8.8, 7.5, 16, 9.6, 8.5, 'black'), (15, 8.6, 7.3, 16, 9.8, 8.7, 'metal_dark'),
        (5, 10.6, 7.2, 11, 12, 8.8, 'black'), (11, 10.3, 7, 11.6, 12.3, 9, 'glass'), (4.4, 10.4, 7.1, 5, 12.2, 8.9, 'metal_dark'),
        (6, 10, 7.6, 6.6, 10.6, 8.4, 'metal_mid'), (9.4, 10, 7.6, 10, 10.6, 8.4, 'metal_mid'), (7, 9, 9, 7.6, 9.6, 9.9, 'metal_light'),
        (4.8, 6, 7.3, 6.2, 8, 8.7, 'tan'), (6.5, 6.6, 7.4, 8, 8, 8.6, 'black'), (12, 6, 7.6, 12.5, 8.8, 7.9, 'black'),
        (12, 6, 8.1, 12.5, 8.8, 8.4, 'black'),
    ],
}


def gun_models():
    save_png(palette(), 'textures', 'item', 'gun_palette.png')
    for name, boxes in GUNS.items():
        save_json({"credit": "Hollow Hours", "texture_size": [16, 16], "textures": {"p": "hollowhours:item/gun_palette", "particle": "hollowhours:item/gun_palette"},
                   "elements": [box(*b) for b in boxes], "display": GUN_DISPLAY}, 'models', 'item', name + '.json')


# ----------------------------------------------------------------------------- 16x16 sprites
def sprite(draw_fn):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw_fn(im.load(), ImageDraw.Draw(im))
    return im


def line(p, pts, col):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            p[x, y] = col + (255,) if len(col) == 3 else col


def diag(x0, y0, n):
    return [(x0 + i, y0 - i) for i in range(n)]


def knife(p, d):
    # handle bottom-left, blade up-right (like vanilla swords)
    line(p, diag(2, 13, 4), (40, 40, 40))
    line(p, diag(3, 13, 3), (60, 60, 60))
    line(p, [(5, 9), (6, 10), (4, 8), (7, 11)], (90, 90, 90))      # guard
    line(p, diag(6, 9, 6), (200, 205, 210))
    line(p, diag(7, 9, 5), (140, 145, 150))
    line(p, diag(6, 8, 6), (230, 232, 235))


def katana(p, d):
    line(p, diag(1, 14, 4), (60, 30, 30))
    line(p, [(2, 14), (3, 13), (4, 12)], (180, 40, 40))
    line(p, [(4, 10), (5, 11), (3, 9), (6, 12)], (200, 170, 60))   # tsuba
    line(p, diag(5, 10, 10), (215, 220, 228))
    line(p, diag(6, 10, 9), (150, 155, 165))
    line(p, diag(5, 9, 9), (240, 242, 245))


def battle_axe(p, d):
    line(p, diag(2, 14, 11), (100, 70, 40))
    line(p, diag(3, 14, 10), (70, 48, 28))
    for y in range(1, 9):
        for x in range(8, 15):
            if (x - 11) ** 2 / 12 + (y - 4.5) ** 2 / 14 <= 1 and x >= 10:
                p[x, y] = (150, 155, 162, 255) if x < 13 else (210, 214, 220, 255)
    line(p, [(9, 4), (9, 5), (10, 3), (10, 6)], (90, 92, 96))


def stun_baton(p, d):
    line(p, diag(2, 14, 4), (30, 30, 32))
    line(p, diag(3, 14, 3), (50, 50, 54))
    line(p, diag(5, 11, 7), (70, 72, 78))
    line(p, diag(6, 11, 6), (45, 46, 50))
    line(p, [(11, 4), (12, 3), (12, 4), (11, 3)], (90, 200, 255))
    line(p, [(13, 2), (12, 1), (14, 3)], (200, 240, 255))


def spear(p, d):
    line(p, diag(0, 15, 12), (120, 85, 50))
    line(p, diag(1, 15, 11), (90, 62, 36))
    line(p, [(11, 4), (12, 4), (11, 5)], (60, 60, 60))
    for (x, y) in [(12, 3), (13, 2), (14, 1), (12, 2), (13, 1), (13, 3), (14, 2), (15, 0)]:
        p[x, y] = (210, 214, 220, 255)


def grenade(p, d):
    d.ellipse((4, 5, 12, 14), fill=(70, 86, 52, 255))
    for y in range(6, 14, 2):
        for x in range(5, 12):
            if p[x, y][3]:
                p[x, y] = (54, 68, 40, 255)
    d.rectangle((6, 3, 9, 5), fill=(90, 90, 96, 255))
    line(p, [(10, 3), (11, 2), (11, 3), (12, 4)], (200, 200, 200))
    line(p, [(5, 4), (4, 3)], (170, 170, 175))


def bullets(col_tip, col_case, tall=6, n=3, wide=False):
    def f(p, d):
        for i in range(n):
            x = 3 + i * (4 if not wide else 5)
            w = 2 if not wide else 3
            top = 15 - tall - 3
            d.rectangle((x, top + 2, x + w, 13), fill=col_case + (255,))
            d.rectangle((x, top, x + w, top + 1), fill=col_tip + (255,))
            d.line((x + w, top + 2, x + w, 13), fill=tuple(max(0, c - 40) for c in col_case) + (255,))
    return f


# ----------------------------------------------------------------------------- armour
SETS = {
    'tactical': dict(base=(92, 104, 64), dark=(64, 74, 44), accent=(150, 136, 92), visor=(40, 40, 40), camo=[(70, 84, 48), (120, 110, 76), (52, 58, 36)]),
    'heavy': dict(base=(74, 78, 86), dark=(46, 48, 54), accent=(160, 120, 40), visor=(30, 30, 34), camo=[(84, 88, 96), (60, 64, 70)]),
    'hunter': dict(base=(30, 30, 36), dark=(16, 16, 20), accent=(90, 220, 230), visor=(60, 200, 220), camo=[(36, 36, 44), (24, 24, 30)]),
}


def pattern(w, h, s, seed):
    im = Image.new('RGBA', (w, h), s['base'] + (255,))
    p = im.load()
    r = random.Random(seed)
    for _ in range(w * h // 9):
        x, y = r.randrange(w), r.randrange(h)
        c = r.choice(s['camo'])
        for dx in range(r.randint(1, 3)):
            for dy in range(r.randint(1, 2)):
                if x + dx < w and y + dy < h:
                    p[x + dx, y + dy] = c + (255,)
    return im


def armor_layers(name, s):
    # layer 1: helmet (0,0)-(32,16), body (16,16)-(40,32), arms (40,16)-(56,32), boots/legs (0,16)-(16,32)
    l1 = pattern(64, 32, s, hash(name) % 1000)
    p = l1.load()
    d = ImageDraw.Draw(l1)
    # helmet: darker top, visor band on the front face (8..16, 8..16)
    d.rectangle((8, 0, 24, 8), fill=s['dark'] + (255,))
    d.rectangle((8, 11, 15, 13), fill=s['visor'] + (255,))
    d.rectangle((0, 11, 7, 12), fill=s['dark'] + (255,))
    d.rectangle((16, 11, 31, 12), fill=s['dark'] + (255,))
    # body: vest plates + straps + pouches (front face 20..28, 20..32)
    d.rectangle((20, 20, 27, 31), fill=s['base'] + (255,))
    d.rectangle((21, 21, 26, 26), fill=s['dark'] + (255,))
    d.rectangle((20, 28, 27, 29), fill=s['accent'] + (255,))
    d.line((21, 20, 21, 31), fill=s['dark'] + (255,))
    d.line((26, 20, 26, 31), fill=s['dark'] + (255,))
    d.rectangle((22, 29, 23, 31), fill=s['dark'] + (255,))
    d.rectangle((24, 29, 25, 31), fill=s['dark'] + (255,))
    # arms: shoulder pads
    d.rectangle((40, 16, 55, 21), fill=s['dark'] + (255,))
    d.rectangle((44, 20, 47, 21), fill=s['accent'] + (255,))
    # boots: dark soles
    d.rectangle((0, 28, 15, 31), fill=s['dark'] + (255,))
    if name == 'hunter':
        for (x, y) in [(23, 23), (24, 24), (23, 25), (24, 22), (11, 12), (12, 12), (45, 24), (46, 25)]:
            p[x, y] = s['accent'] + (255,)
    l2 = pattern(64, 32, s, hash(name) % 1000 + 7)
    d2 = ImageDraw.Draw(l2)
    d2.rectangle((16, 16, 40, 20), fill=s['dark'] + (255,))      # belt
    d2.rectangle((18, 17, 21, 19), fill=s['accent'] + (255,))     # buckle
    d2.rectangle((0, 22, 15, 26), fill=s['dark'] + (255,))        # knee pads
    d2.rectangle((4, 22, 7, 25), fill=s['accent'] + (255,) if name != 'tactical' else s['dark'] + (255,))
    save_png(l1, 'textures', 'models', 'armor', name + '_layer_1.png')
    save_png(l2, 'textures', 'models', 'armor', name + '_layer_2.png')


def armor_icon(kind, s):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    b, k, a, v = s['base'] + (255,), s['dark'] + (255,), s['accent'] + (255,), s['visor'] + (255,)
    if kind == 'helmet':
        d.rounded_rectangle((3, 3, 12, 11), 3, fill=b, outline=k)
        d.rectangle((4, 8, 11, 9), fill=v)
        d.rectangle((3, 11, 12, 12), fill=k)
    elif kind == 'chestplate':
        d.polygon([(2, 2), (6, 2), (8, 4), (10, 2), (13, 2), (13, 7), (12, 7), (12, 14), (3, 14), (3, 7), (2, 7)], fill=b, outline=k)
        d.rectangle((5, 7, 10, 10), fill=k)
        d.rectangle((4, 12, 11, 12), fill=a)
    elif kind == 'leggings':
        d.polygon([(3, 2), (12, 2), (12, 14), (9, 14), (8, 6), (7, 6), (6, 14), (3, 14)], fill=b, outline=k)
        d.rectangle((3, 2, 12, 3), fill=k)
        d.rectangle((7, 2, 8, 3), fill=a)
    else:
        d.polygon([(2, 7), (6, 7), (6, 11), (8, 12), (8, 14), (2, 14)], fill=b, outline=k)
        d.polygon([(9, 7), (13, 7), (13, 11), (15, 12), (15, 14), (9, 14)], fill=b, outline=k)
        d.rectangle((2, 13, 8, 14), fill=k)
        d.rectangle((9, 13, 15, 14), fill=k)
    return im


def item_model(name, parent='item/generated'):
    save_json({"parent": parent, "textures": {"layer0": "hollowhours:item/" + name}}, 'models', 'item', name + '.json')


def main():
    gun_models()
    for name, fn in (('combat_knife', knife), ('katana', katana), ('battle_axe', battle_axe), ('stun_baton', stun_baton), ('spear', spear)):
        save_png(sprite(fn), 'textures', 'item', name + '.png')
        item_model(name, 'item/handheld')
    save_png(sprite(grenade), 'textures', 'item', 'grenade.png')
    item_model('grenade')
    for name, fn in (('pistol_ammo', bullets((200, 160, 70), (170, 130, 50), 6, 3)), ('rifle_ammo', bullets((200, 160, 70), (150, 120, 50), 9, 3)),
                     ('shotgun_shell', bullets((200, 170, 80), (180, 40, 40), 7, 2, True)), ('sniper_ammo', bullets((210, 210, 215), (190, 150, 60), 11, 2))):
        save_png(sprite(fn), 'textures', 'item', name + '.png')
        item_model(name)
    for setname, s in SETS.items():
        armor_layers(setname, s)
        for kind in ('helmet', 'chestplate', 'leggings', 'boots'):
            n = setname + '_' + kind
            save_png(armor_icon(kind, s), 'textures', 'item', n + '.png')
            item_model(n)
    print('military art written')


if __name__ == '__main__':
    main()
