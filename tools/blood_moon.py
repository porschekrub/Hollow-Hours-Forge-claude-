"""Blood moon textures: a pixel-art red full moon (32x32) and a soft red glow (64x64)."""
from PIL import Image
import math, random, os

out = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours', 'textures', 'environment')
os.makedirs(out, exist_ok=True)
rnd = random.Random(13)

N = 32
moon = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = moon.load()
craters = [(rnd.uniform(8, 24), rnd.uniform(8, 24), rnd.uniform(1.5, 4.0)) for _ in range(9)]
for y in range(N):
    for x in range(N):
        dx, dy = x + 0.5 - N / 2, y + 0.5 - N / 2
        d = math.hypot(dx, dy) / (N / 2 - 3)
        if d > 1:
            continue
        # limb darkening + a lit edge toward the top-left
        shade = 1.0 - 0.45 * d ** 3 + 0.12 * (-(dx + dy) / N)
        for cx, cy, cr in craters:
            cd = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if cd < cr:
                shade -= 0.22 * (1 - cd / cr)
            elif cd < cr + 0.9:
                shade += 0.08
        shade += rnd.uniform(-0.05, 0.05)
        shade = max(0.25, min(1.15, shade))
        r = int(min(255, 205 * shade + 25))
        g = int(min(255, 28 * shade))
        b = int(min(255, 22 * shade))
        px[x, y] = (r, g, b, 255)
moon.save(os.path.join(out, 'blood_moon.png'))

M = 64
glow = Image.new('RGBA', (M, M), (0, 0, 0, 0))
gp = glow.load()
for y in range(M):
    for x in range(M):
        d = math.hypot(x + 0.5 - M / 2, y + 0.5 - M / 2) / (M / 2)
        a = max(0.0, 1 - d) ** 2.2
        gp[x, y] = (255, 30, 20, int(255 * a))
glow.save(os.path.join(out, 'blood_moon_glow.png'))
print('ok')
