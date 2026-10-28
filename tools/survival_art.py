"""Textures for the phone, charger, thirst/sanity HUD icons and blood particles."""
from PIL import Image
import math, os, random

A = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours')
rnd = random.Random(5)


def save(img, *path):
    p = os.path.join(A, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


# ---- phone (16x16): black slab, glowing screen with an app grid
ph = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
p = ph.load()
for y in range(1, 16):
    for x in range(4, 12):
        p[x, y] = (24, 24, 30, 255)
for y in range(1, 16):
    p[4, y] = p[11, y] = (60, 60, 72, 255)
for x in range(4, 12):
    p[x, 1] = p[x, 15] = (60, 60, 72, 255)
for y in range(3, 13):
    for x in range(5, 11):
        k = (y - 3) / 10
        p[x, y] = (int(40 + 30 * k), int(70 + 20 * k), int(140 - 40 * k), 255)
for (x, y, c) in [(6, 5, (90, 220, 110)), (9, 5, (230, 80, 100)), (6, 8, (130, 90, 220)), (9, 8, (240, 200, 70))]:
    p[x, y] = p[x + 1, y] = p[x, y + 1] = p[x + 1, y + 1] = c + (255,)
p[7, 14] = p[8, 14] = (110, 110, 120, 255)
save(ph, 'textures', 'item', 'phone.png')

# ---- charger (16x16): white brick with a cable and a plug
ch = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
c = ch.load()
for y in range(2, 9):
    for x in range(3, 10):
        c[x, y] = (236, 236, 236, 255) if 3 < x < 9 and 2 < y < 8 else (190, 190, 196, 255)
c[5, 0] = c[5, 1] = c[7, 0] = c[7, 1] = (170, 170, 60, 255)
c[6, 5] = (90, 200, 90, 255)
cable = [(6, 9), (6, 10), (7, 11), (8, 12), (9, 12), (10, 12), (11, 13), (12, 13)]
for (x, y) in cable:
    c[x, y] = (230, 230, 230, 255)
for y in range(12, 16):
    for x in range(12, 15):
        c[x, y] = (200, 200, 205, 255)
c[13, 15] = (90, 90, 90, 255)
save(ch, 'textures', 'item', 'charger.png')

# ---- HUD icons 64x32: water drops (full, half, empty) and eyes (open, tired, bloodshot)
hud = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
h = hud.load()
DROP = ["....#....", "...###...", "...###...", "..#####..", "..#####..", ".#######.", ".#######.", "..#####..", "...###..."]
for i, kind in enumerate(['full', 'half', 'empty']):
    for y, row in enumerate(DROP):
        for x, ch_ in enumerate(row):
            if ch_ != '#':
                continue
            edge = (y == 0 or row[max(0, x - 1)] == '.' or x == 8 or row[min(8, x + 1)] == '.' or y == 8 or DROP[min(8, y + 1)][x] == '.')
            if kind == 'empty' or (kind == 'half' and x < 4):
                col = (20, 20, 30, 255) if edge else (45, 45, 60, 255)
            else:
                col = (20, 60, 140, 255) if edge else (60, 140, 235, 255)
                if x == 3 and y in (3, 4, 5):
                    col = (180, 220, 255, 255)
            h[i * 9 + x, y] = col
EYE = [".........", "..#####..", ".#.....#.", "#..###..#", "#..#@#..#", "#..###..#", ".#.....#.", "..#####..", "........."]
for i in range(3):
    for y, row in enumerate(EYE):
        for x, ch_ in enumerate(row):
            if ch_ == '.':
                if i == 2 and 1 < y < 7 and 0 < x < 8 and rnd.random() < 0.35:
                    h[i * 9 + x, 9 + y] = (180, 20, 20, 255)
                continue
            col = {'#': (230, 230, 230, 255), '@': (10, 10, 10, 255)}[ch_]
            if ch_ == '#' and y in (3, 4, 5) and 2 < x < 6:
                col = (90, 170, 210, 255) if i == 0 else (150, 120, 200, 255) if i == 1 else (200, 30, 30, 255)
            if i == 1 and y in (1, 2):
                col = (60, 50, 60, 255)
            h[i * 9 + x, 9 + y] = col
save(hud, 'textures', 'gui', 'survival.png')

# ---- blood particles: white blobs (tinted per creature in game)
for n in range(4):
    b = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    q = b.load()
    cx, cy = 3.5 + rnd.uniform(-0.5, 0.5), 3.5 + rnd.uniform(-0.5, 0.5)
    r = 2.2 + n * 0.5
    blobs = [(cx, cy, r)] + [(cx + rnd.uniform(-2.5, 2.5), cy + rnd.uniform(-2.5, 2.5), rnd.uniform(0.8, 1.5)) for _ in range(n + 1)]
    for y in range(8):
        for x in range(8):
            v = max(0, max(1 - math.hypot(x + 0.5 - bx, y + 0.5 - by) / br for bx, by, br in blobs))
            if v > 0.05:
                shade = int(255 * (0.75 + 0.25 * v))
                q[x, y] = (shade, shade, shade, min(255, int(255 * min(1, v * 2.2))))
    save(b, 'textures', 'particle', f'blood_{n}.png')
print('ok')
