"""Preview Hollow Hours block/item JSON models without the game: a small software renderer (perspective camera,
z-buffer, cutout alpha, Minecraft-like face shading). Usage: python3 blockrender.py OUT_DIR model [model ...]
where model is e.g. block/swing or item/basketball."""
import json, math, os, sys
import numpy as np
from PIL import Image

A = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours')
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def load_model(name):
    """elements + resolved textures, following parents inside this mod"""
    ns, path = name.split(':') if ':' in name else ('hollowhours', name)
    m = json.load(open(os.path.join(A, 'models', path + '.json')))
    tex = dict(m.get('textures', {}))
    elems = m.get('elements')
    par = m.get('parent', '')
    if par.startswith('hollowhours:'):
        pe, pt = load_model(par)
        tex = {**pt, **tex}
        elems = elems or pe
    return elems or [], tex


def texture(ref, tex, cache={}):
    seen = 0
    while ref.startswith('#') and seen < 8:
        ref = tex.get(ref[1:], '')
        seen += 1
    if ref in cache:
        return cache[ref]
    ns, p = ref.split(':') if ':' in ref else ('minecraft', ref)
    f = os.path.join(A, 'textures', p + '.png')
    im = Image.open(f).convert('RGBA') if os.path.exists(f) else Image.new('RGBA', (16, 16), (255, 0, 255, 255))
    if im.height > im.width:
        im = im.crop((0, 0, im.width, im.width))   # animated: first frame
    cache[ref] = np.asarray(im).astype(np.float32) / 255
    return cache[ref]


def rot(v, axis, ang, origin):
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v[0] - origin[0], v[1] - origin[1], v[2] - origin[2]
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        z, x = z * c - x * s, z * s + x * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return [x + origin[0], y + origin[1], z + origin[2]]


def faces(e):
    (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
    # corners (top-left, top-right, bottom-right, bottom-left as seen on the texture) and default uv
    d = {
        'north': ([(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)], [16 - x1, 16 - y1, 16 - x0, 16 - y0]),
        'south': ([(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)], [x0, 16 - y1, x1, 16 - y0]),
        'west': ([(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)], [z0, 16 - y1, z1, 16 - y0]),
        'east': ([(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], [16 - z1, 16 - y1, 16 - z0, 16 - y0]),
        'up': ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], [x0, z0, x1, z1]),
        'down': ([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], [x0, 16 - z1, x1, 16 - z0]),
    }
    for name, f in e.get('faces', {}).items():
        corners, uv = d[name]
        yield name, corners, f.get('uv', uv), f['texture']


def render(model, size=420, yrot=0, eye=(-26, 30, -34), look=(8, 12, 8), fov=40):
    elems, tex = load_model(model)
    img = np.zeros((size, size, 3), np.float32) + np.array([0.62, 0.74, 0.92])
    zb = np.full((size, size), np.inf)
    eye, look = np.array(eye, float), np.array(look, float)
    fwd = look - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    f = size / 2 / math.tan(math.radians(fov / 2))

    def project(p):
        d = np.array(p) - eye
        z = d @ fwd
        return size / 2 + (d @ right) * f / z, size / 2 - (d @ up) * f / z, z

    for e in elems:
        r = e.get('rotation')
        for name, corners, uv, tref in faces(e):
            t = texture(tref, tex)
            pts = []
            for c in corners:
                p = list(c)
                if r:
                    p = rot(p, r['axis'], r['angle'], r['origin'])
                if yrot:
                    p = rot(p, 'y', -yrot, (8, 8, 8))
                pts.append(project(p))
            u0, v0, u1, v1 = uv
            tuv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            shade = SHADE[name]
            for tri in ((0, 1, 2), (0, 2, 3)):
                P = [pts[i] for i in tri]
                T = [tuv[i] for i in tri]
                xs = [p[0] for p in P]
                ys = [p[1] for p in P]
                minx, maxx = max(0, int(min(xs))), min(size - 1, int(max(xs)) + 1)
                miny, maxy = max(0, int(min(ys))), min(size - 1, int(max(ys)) + 1)
                if minx > maxx or miny > maxy:
                    continue
                (ax, ay, az), (bx, by, bz), (cx, cy, cz) = P
                den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                if abs(den) < 1e-9:
                    continue
                X, Y = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
                w0 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
                w1 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
                w2 = 1 - w0 - w1
                inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
                if not inside.any():
                    continue
                # perspective-correct uv
                iz = w0 / az + w1 / bz + w2 / cz
                u = (w0 * T[0][0] / az + w1 * T[1][0] / bz + w2 * T[2][0] / cz) / iz
                v = (w0 * T[0][1] / az + w1 * T[1][1] / bz + w2 * T[2][1] / cz) / iz
                depth = 1 / iz
                th, tw = t.shape[:2]
                tx = np.clip((u / 16 * tw).astype(int), 0, tw - 1)
                ty = np.clip((v / 16 * th).astype(int), 0, th - 1)
                col = t[ty, tx].copy()
                # outside the texture the game reads the atlas neighbours: show it loudly
                bad = (u < -0.01) | (u > 16.01) | (v < -0.01) | (v > 16.01)
                col[bad] = [1, 0, 1, 1]
                ok = inside & (col[..., 3] > 0.1)
                sub = zb[miny:maxy + 1, minx:maxx + 1]
                ok &= depth < sub
                sub[ok] = depth[ok]
                img[miny:maxy + 1, minx:maxx + 1][ok] = col[..., :3][ok] * shade
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for m in sys.argv[2:]:
        views = [render(m), render(m, eye=(40, 22, 30)), render(m, eye=(8, 14, -40), look=(8, 12, 8))]
        sheet = Image.new('RGB', (420 * 3, 420))
        for i, v in enumerate(views):
            sheet.paste(v, (420 * i, 0))
        sheet.save(os.path.join(out, m.replace('/', '_') + '.png'))
        print(m)
