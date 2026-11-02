"""The rescue helicopter: textures and three block models (body, main rotor, tail rotor) drawn by HelicopterRenderer at
4x scale, so one model unit is a quarter of a block. The nose points north (-z); the ground is y = 0; the main rotor
turns around (8, 17, 4) of the body, the tail rotor around (9.1, 13.5, 28.5).
Usage: python3 helicopter_art.py   (then: python3 blockrender.py OUT block/helicopter)"""
import json, os, random
from PIL import Image

A = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours')
T = os.path.join(A, 'textures', 'block')
M = os.path.join(A, 'models', 'block')
rnd = random.Random(7)

WHITE, RED, DARK, GLASS = (236, 238, 240), (196, 34, 30), (52, 55, 60), (40, 62, 84)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def save(name, px, n=32):
    im = Image.new('RGBA', (n, n))
    p = im.load()
    for y in range(n):
        for x in range(n):
            p[x, y] = px(x, y)
    im.save(os.path.join(T, name + '.png'))


def panel(base, lines=8):
    """painted metal with panel seams and a few rivets"""
    def px(x, y):
        k = 1 + rnd.uniform(-0.025, 0.025)
        if x % 16 == 0 or y % 16 == 0:
            k *= 0.86
        if (x % 16 == 2 or x % 16 == 14) and y % 4 == 2:
            k *= 0.8
        return shade(base, k)
    return px


def textures():
    os.makedirs(T, exist_ok=True)
    save('heli_white', panel(WHITE))
    save('heli_red', panel(RED))
    save('heli_metal', panel(DARK))

    # the body side: white over a red band, with the red cross of a rescue helicopter
    def side(x, y):
        c = WHITE if y < 22 else RED
        if 21 <= y <= 22:
            c = (250, 250, 250)
        if 4 <= y <= 16 and 13 <= x <= 18 or 7 <= y <= 13 and 10 <= x <= 21:
            c = RED
        k = 1 + rnd.uniform(-0.02, 0.02)
        if x == 0 or x == 31:
            k *= 0.85
        return shade(c, k)
    save('heli_side', side)

    # cockpit glass: dark blue with a streak of sky and a frame
    def glass(x, y):
        if x < 2 or x > 29 or y < 2 or y > 29:
            return shade(DARK, 1)
        c = GLASS
        if 0 <= (x + y) - 14 <= 4 or 0 <= (x + y) - 24 <= 1:
            c = (150, 186, 214)
        return shade(c, 1 + y / 120)
    save('heli_glass', glass)

    # a side window in white
    def window(x, y):
        if 6 <= x <= 25 and 5 <= y <= 18:
            if x in (6, 25) or y in (5, 18):
                return shade(DARK, 1)
            return shade((150, 186, 214) if (x - y) % 11 < 3 else GLASS, 1)
        return shade(WHITE if y < 22 else RED, 1 + rnd.uniform(-0.02, 0.02))
    save('heli_window', window)

    # the cabin floor: checker plate
    def floor(x, y):
        k = 1.08 if (x + 2 * y) % 6 == 0 and (x // 3 + y // 3) % 2 == 0 else 1
        return shade((92, 96, 102), k * (1 + rnd.uniform(-0.03, 0.03)))
    save('heli_floor', floor)

    # inside walls: padded grey
    def inner(x, y):
        k = 0.85 if y % 8 == 0 or x % 16 == 0 else 1
        return shade((118, 122, 126), k)
    save('heli_inner', inner)

    # rotor blade: dark with a yellow tip at both ends
    def blade(x, y):
        if x < 2 or x > 29:
            return shade((236, 196, 40), 1)
        return shade((34, 36, 40), 1 + (0.2 if y < 10 else 0))
    save('heli_blade', blade)

    # seat cushions: dark blue fabric with stitching
    def seat(x, y):
        k = 0.82 if x % 8 == 0 or y % 8 == 0 else 1 + rnd.uniform(-0.04, 0.04)
        return shade((44, 58, 92), k)
    save('heli_seat', seat)

    # skids and struts: black tubes
    save('heli_skid', lambda x, y: shade((28, 29, 32), 1.2 if 10 <= y <= 14 else 1))


FULL = [0, 0, 16, 16]


def el(frm, to, tex, faces=('north', 'south', 'east', 'west', 'up', 'down'), rot=None, per=None):
    """a box; every face shows its whole texture (per = {face: texture} for exceptions)"""
    f = {d: {"uv": FULL, "texture": "#" + (per or {}).get(d, tex)} for d in faces}
    e = {"from": frm, "to": to, "faces": f}
    if rot:
        e["rotation"] = rot
    return e


def body():
    E = []
    # skids, struts
    for x in (1, 14):
        E.append(el([x, 0, -6], [x + 1, 0.75, 16], 'skid'))
        for z in (-1, 11):
            E.append(el([x + 0.25, 0.75, z], [x + 0.75, 3.25, z + 0.5], 'skid'))
        for z in (-1, 11):
            a, b = (x + 0.25, 2.5) if x == 1 else (13.5, x + 0.75)
            E.append(el([a, 2.75, z], [b, 3.25, z + 0.5], 'skid'))
    # floor
    E.append(el([2.5, 3, -9.5], [13.5, 3.75, 14], 'red', per={'up': 'floor'}))
    # rear wall, roof
    E.append(el([2.5, 3.75, 13], [13.5, 11.5, 14], 'white', per={'north': 'inner'}))
    E.append(el([2.5, 11.5, -8], [13.5, 12.5, 14], 'white', per={'down': 'inner'}))
    # side walls: the big door gap in the middle (z 0..8), a window up front, the cross at the back
    for x0, x1, out, inn in ((2.5, 3, 'west', 'east'), (13, 13.5, 'east', 'west')):
        E.append(el([x0, 3.75, 8], [x1, 11.5, 13], 'white', per={out: 'side', inn: 'inner'}))
        E.append(el([x0, 3.75, -5], [x1, 11.5, 0], 'white', per={out: 'window', inn: 'inner'}))
        E.append(el([x0, 7, -8], [x1, 11.5, -5], 'glass'))
        # door posts
        E.append(el([x0, 3.75, -0.5], [x1, 11.5, 0], 'metal'))
        E.append(el([x0, 3.75, 8], [x1, 11.5, 8.5], 'metal'))
    # seats: a bench for three at the back, a jump seat by the door, two up front (see HelicopterEntity.SEATS)
    E.append(el([3, 3.75, 9.5], [13, 5.75, 13], 'seat'))
    E.append(el([6.5, 3.75, 4.75], [9.5, 5.75, 6.75], 'seat'))
    E.append(el([6.5, 5.75, 6.75], [9.5, 8.75, 7.25], 'seat'))
    for x0 in (3.5, 9):
        E.append(el([x0, 3.75, -4], [x0 + 3.5, 5.75, -1.5], 'seat'))
        E.append(el([x0, 5.75, -1.5], [x0 + 3.5, 9.5, -1], 'seat'))
    # the nose: the lower part, the rounded tip, the slanted windscreen
    E.append(el([2.5, 3.75, -11], [13.5, 7, -5], 'red', per={'up': 'metal'}))
    E.append(el([3.5, 4, -13], [12.5, 6.5, -11], 'red'))
    E.append(el([4.5, 4.5, -14], [11.5, 6, -13], 'metal'))
    E.append(el([3, 7, -10.5], [13, 12, -10], 'glass', rot={"angle": 22.5, "axis": "x", "origin": [8, 7, -10.5]}))
    # engine on the roof, exhaust, mast
    E.append(el([5, 12.5, -3], [11, 15, 10], 'white', per={'up': 'metal'}))
    E.append(el([6, 13, 10], [10, 14.5, 12], 'metal'))
    E.append(el([7.5, 15, 3.5], [8.5, 17, 4.5], 'metal'))
    # tail boom (thick, then thin), fins, stabiliser, the tail rotor hub
    E.append(el([6.5, 8.5, 14], [9.5, 11.5, 21], 'red'))
    E.append(el([7, 9, 21], [9, 11, 30], 'white'))
    E.append(el([7.5, 11, 27], [8.5, 16, 30], 'red'))
    E.append(el([7.5, 7, 28], [8.5, 9, 30], 'red'))
    E.append(el([5, 9.5, 25], [11, 10, 27], 'white'))
    E.append(el([8.5, 13.25, 28.25], [9.1, 13.75, 28.75], 'metal'))
    tex = {k: "hollowhours:block/heli_" + k for k in ('white', 'red', 'metal', 'side', 'glass', 'window', 'floor', 'inner', 'skid', 'seat')}
    tex["particle"] = tex["white"]
    return {"textures": tex, "elements": E}


def rotor():
    """blades through (8, 0, 8); the renderer spins it"""
    E = [el([-15, 0, 7.5], [31, 0.3, 8.5], 'blade'), el([7.5, 0, -15], [8.5, 0.3, 31], 'blade'),
         el([7, -0.5, 7], [9, 0.5, 9], 'metal')]
    return {"textures": {"blade": "hollowhours:block/heli_blade", "metal": "hollowhours:block/heli_metal", "particle": "hollowhours:block/heli_metal"},
            "elements": E}


def tail_rotor():
    """two blades in the y-z plane through (8, 8, 8); the renderer spins it around x"""
    E = [el([7.9, 5.5, 7.75], [8.1, 10.5, 8.25], 'blade'), el([7.9, 7.75, 5.5], [8.1, 8.25, 10.5], 'blade')]
    return {"textures": {"blade": "hollowhours:block/heli_blade", "particle": "hollowhours:block/heli_blade"}, "elements": E}


def main():
    textures()
    os.makedirs(M, exist_ok=True)
    for name, m in (('helicopter', body()), ('helicopter_rotor', rotor()), ('helicopter_tail_rotor', tail_rotor())):
        with open(os.path.join(M, name + '.json'), 'w') as f:
            json.dump(m, f, indent=1)
    print('helicopter written')


if __name__ == '__main__':
    main()
