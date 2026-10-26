#!/usr/bin/env python3
"""Renders a posed model from an arbitrary camera (eye -> target, roll, fov): used to preview exactly what the
victim sees during a kill cinematic, and the jumpscare face rigs."""
import math

import numpy as np
from PIL import Image

from georender import bone_matrices, T, RX, RY, RZ


def look_basis(eye, target, roll_deg=0.0):
    f = np.asarray(target, float) - np.asarray(eye, float)
    f /= max(np.linalg.norm(f), 1e-9)
    up = np.array([0.0, 1.0, 0.0])
    if abs(np.dot(f, up)) > 0.999:
        up = np.array([0.0, 0.0, 1.0])
    r = np.cross(f, up)
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    a = math.radians(roll_deg)
    r, u = r * math.cos(a) + u * math.sin(a), u * math.cos(a) - r * math.sin(a)
    return r, u, f


def render_view(model, tex, pose, eye, target, roll=0.0, fov=70.0, size=(480, 270), bg=(10, 10, 12, 255), emissive=None,
                extra_tris=None, near=1.0, light=True):
    """eye/target in model space pixels (georender coords)."""
    W, H = size
    texa = np.asarray(tex.convert("RGBA"), dtype=np.float32)
    TH, TW = texa.shape[:2]
    emi = np.asarray(emissive.convert("RGBA"), dtype=np.float32) if emissive is not None else None
    mats, hidden = bone_matrices(model, pose or {})
    r, u, f = look_basis(eye, target, roll)
    eye = np.asarray(eye, float)
    F = 1.0 / math.tan(math.radians(fov) / 2)
    img = np.zeros((H, W, 4), np.float32)
    img[:] = bg
    zbuf = np.full((H, W), np.inf, np.float32)
    tris = []
    for name in model.order:
        if name in hidden:
            continue
        b = model.bones[name]
        for c in b["cubes"]:
            cm = mats[name] @ T(*c["pivot"]) @ RZ(c["rot"][2]) @ RY(c["rot"][1]) @ RX(c["rot"][0]) @ T(*[-v for v in c["pivot"]])
            for verts, uvs in c["quads"]:
                P = (np.array([list(v) + [1] for v in verts], float) @ cm.T)[:, :3]
                tris.append((P[[0, 1, 2]], [uvs[0], uvs[1], uvs[2]]))
                tris.append((P[[0, 2, 3]], [uvs[0], uvs[2], uvs[3]]))
    for Pw, uvs in tris:
        d = Pw - eye
        cx, cy, cz = d @ r, d @ u, d @ f
        if (cz <= near).any():
            continue
        sx = W / 2 + cx / cz * F * H / 2
        sy = H / 2 - cy / cz * F * H / 2
        minx, maxx = int(max(0, math.floor(sx.min()))), int(min(W - 1, math.ceil(sx.max())))
        miny, maxy = int(max(0, math.floor(sy.min()))), int(min(H - 1, math.ceil(sy.max())))
        if minx > maxx or miny > maxy:
            continue
        x0, y0, x1, y1, x2, y2 = sx[0], sy[0], sx[1], sy[1], sx[2], sy[2]
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-9:
            continue
        xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
        a = ((y1 - y2) * (xs - x2) + (x2 - x1) * (ys - y2)) / den
        bb = ((y2 - y0) * (xs - x2) + (x0 - x2) * (ys - y2)) / den
        cc = 1 - a - bb
        inside = (a >= -1e-4) & (bb >= -1e-4) & (cc >= -1e-4)
        if not inside.any():
            continue
        # perspective-correct interpolation
        iz = a / cz[0] + bb / cz[1] + cc / cz[2]
        z = 1 / np.maximum(iz, 1e-9)
        u_ = (a * uvs[0][0] / cz[0] + bb * uvs[1][0] / cz[1] + cc * uvs[2][0] / cz[2]) * z
        v_ = (a * uvs[0][1] / cz[0] + bb * uvs[1][1] / cz[1] + cc * uvs[2][1] / cz[2]) * z
        ui = np.clip((u_ * TW / model.tw).astype(int), 0, TW - 1)
        vi = np.clip((v_ * TH / model.th).astype(int), 0, TH - 1)
        col = texa[vi, ui]
        sub = zbuf[miny:maxy + 1, minx:maxx + 1]
        ok = inside & (col[..., 3] > 25) & (z < sub)
        if not ok.any():
            continue
        shade = 1.0
        if light:
            e1, e2 = Pw[1] - Pw[0], Pw[2] - Pw[0]
            n = np.cross(e1, e2)
            nl = np.linalg.norm(n)
            if nl > 0:
                n /= nl
                shade = 0.5 + 0.5 * abs(float(np.dot(n, np.array([0.3, 0.75, -0.6]) / 1.02)))
        rgb = col[..., :3] * shade
        if emi is not None:
            ec = emi[vi, ui]
            g = ec[..., 3:4] / 255.0
            rgb = rgb * (1 - g) + ec[..., :3] * g
        reg = img[miny:maxy + 1, minx:maxx + 1]
        reg[ok, :3] = rgb[ok]
        reg[ok, 3] = 255
        sub[ok] = z[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")
