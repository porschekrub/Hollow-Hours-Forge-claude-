#!/usr/bin/env python3
"""Draws a Bedrock geometry (per-face uv cubes, bone + cube rotations) with its texture, to check a model by eye.

    python3 render_geo.py model.geo.json texture.png out.png [yaw=35] [pitch=25]
"""
import json
import math
import sys

import numpy as np
from PIL import Image


def rot(rx, ry, rz):
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Z @ Y @ X


def quads(geo):
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}

    def bone_xf(b, p):
        # points in "internal" space (x mirrored back): rotate about the bone pivot, then the parents
        while b:
            r = b.get("rotation")
            if r:
                piv = np.array([-b["pivot"][0], b["pivot"][1], b["pivot"][2]])
                p = (rot(-r[0], -r[1], r[2]) @ (p - piv).T).T + piv
            b = bones.get(b.get("parent"))
        return p

    out = []
    for b in g["bones"]:
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            x0, y0, z0 = -(o[0] + s[0]), o[1], o[2]
            x1, y1, z1 = x0 + s[0], y0 + s[1], z0 + s[2]
            F = {  # corners in order: top-left, top-right, bottom-right, bottom-left of the texture, seen from outside
                "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
                "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
                "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
                "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
                "up": [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)],
                "down": [(x1, y0, z0), (x0, y0, z0), (x0, y0, z1), (x1, y0, z1)],
            }
            for side, uv in c.get("uv", {}).items():
                p = np.array(F[side], float)
                r = c.get("rotation")
                if r:
                    piv = np.array([-c["pivot"][0], c["pivot"][1], c["pivot"][2]])
                    p = (rot(-r[0], -r[1], r[2]) @ (p - piv).T).T + piv
                p = bone_xf(b, p)
                out.append((p, uv["uv"], uv["uv_size"]))
    return out


def render(geo_path, tex_path, out_path, yaw=35, pitch=25, size=640, icon=None):
    geo = json.load(open(geo_path))
    tex = np.array(Image.open(tex_path).convert("RGBA"))
    qs = quads(geo)
    R = rot(pitch, 0, 0) @ rot(0, yaw, 0)
    pts = np.concatenate([q[0] for q in qs])
    v = (R @ pts.T).T
    mn, mx = v.min(0), v.max(0)
    sc = (size - 40) / max(mx[0] - mn[0], mx[1] - mn[1], 1e-6)
    img = np.zeros((size, size, 4), np.uint8)
    img[..., :3] = (58, 62, 70)
    img[..., 3] = 0 if icon else 255
    zb = np.full((size, size), -1e9)
    cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    for p, uv, uvs in qs:
        q = (R @ p.T).T
        sx = (q[:, 0] - cx) * sc + size / 2
        sy = size / 2 - (q[:, 1] - cy) * sc
        n = np.cross(q[1] - q[0], q[3] - q[0])
        if n[2] >= 0:
            continue  # facing away
        shade = 0.55 + 0.45 * abs(n[2]) / (np.linalg.norm(n) + 1e-9)
        x0, x1 = int(max(0, math.floor(sx.min()))), int(min(size - 1, math.ceil(sx.max())))
        y0, y1 = int(max(0, math.floor(sy.min()))), int(min(size - 1, math.ceil(sy.max())))
        if x1 < x0 or y1 < y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        # parallelogram: P = A + s*(B-A) + t*(D-A)
        ax, ay, bx, by, dx, dy = sx[0], sy[0], sx[1] - sx[0], sy[1] - sy[0], sx[3] - sx[0], sy[3] - sy[0]
        det = bx * dy - by * dx
        if abs(det) < 1e-9:
            continue
        s = ((gx - ax) * dy - (gy - ay) * dx) / det
        t = ((gy - ay) * bx - (gx - ax) * by) / det
        ok = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        z = q[0, 2] + s * (q[1, 2] - q[0, 2]) + t * (q[3, 2] - q[0, 2])
        tu = np.clip((uv[0] + s * uvs[0]).astype(int), 0, tex.shape[1] - 1)
        tv = np.clip((uv[1] + t * uvs[1]).astype(int), 0, tex.shape[0] - 1)
        col = tex[tv, tu]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        ok &= (col[..., 3] > 30) & (z > sub)
        sub[ok] = z[ok]
        dst = img[y0:y1 + 1, x0:x1 + 1]
        dst[ok, :3] = (col[ok, :3] * shade).astype(np.uint8)
        dst[ok, 3] = 255
    out = Image.fromarray(img)
    if icon:   # a small item icon: crop to what was drawn, square, scaled down
        bb = out.getbbox()
        if bb:
            out = out.crop(bb)
            w, h = out.size
            sq = Image.new("RGBA", (max(w, h), max(w, h)), (0, 0, 0, 0))
            sq.paste(out, ((max(w, h) - w) // 2, (max(w, h) - h) // 2))
            out = sq
        out = out.resize((icon, icon), Image.LANCZOS)
    out.save(out_path)


if __name__ == "__main__":
    a = sys.argv
    render(a[1], a[2], a[3], float(a[4]) if len(a) > 4 else 35, float(a[5]) if len(a) > 5 else 25)
