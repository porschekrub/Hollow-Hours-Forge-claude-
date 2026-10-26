#!/usr/bin/env python3
"""Glow masks (eyes, grins, embers that shine in the dark), the jumpscare face rigs and their animations,
plus the screen-effect textures (blood, vignette, TV static) and the guide book page."""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from ghostgen import Geo, paint, fbm  # noqa: E402
from animkit import Anim  # noqa: E402

A = os.path.join(HERE, "..", "hollow-hours", "src", "main", "resources", "assets", "hollowhours")
GEO = os.path.join(A, "geo", "entity")
TEX = os.path.join(A, "textures", "entity")

HEADS = {"head", "jaw", "hair"}


def luma(c):
    return 0.299 * c[:, 0] + 0.587 * c[:, 1] + 0.114 * c[:, 2]


def glow_rule(kind):
    def mat(ctx):
        o = ctx["orig"]
        r, g, b, a = o[:, 0], o[:, 1], o[:, 2], o[:, 3]
        L = luma(o)
        on = a > 0
        head = ctx["bone"] in HEADS
        k = np.zeros(len(o))
        if kind == 1:
            k = np.where(head & (L > 232), 1.0, 0.0)
            k = np.maximum(k, np.where(head & (r > 150) & (g < 50) & (b < 50), 0.5, 0))
        elif kind == 2:
            k = np.where((b > 185) & (r < 175), 1.0, 0.0)
            k = np.maximum(k, np.where(head & (L > 232), 1.0, 0))
        elif kind == 3:
            k = np.clip((r - 170) / 60.0, 0, 1) * np.clip((g - 60) / 60.0, 0, 1) * (b < 110)
            k = np.where(r > g + 40, k, 0)
        elif kind == 4:
            if head:
                med = np.median(L[on]) if on.any() else 0
                k = np.clip((L - max(med, 60) - 70) / 60.0, 0, 1) * 0.85
                k = np.maximum(k, np.where((r > 140) & (g < 60) & (b < 60), 0.7, 0))
        elif kind == 5:
            k = np.where(head & (r > 140) & (r > 2.2 * g + 30) & (r > 2.2 * b + 30), 1.0, 0.0)
        else:  # stalkers
            if head:
                k = np.where((r > 140) & (g < 80) & (b < 80), 1.0, 0.0)
                k = np.maximum(k, np.where(L > 228, 0.9, 0))
        k = k * on
        # emissive texture for the additive "eyes" pass: glow colour premultiplied, black/transparent elsewhere
        gl = np.zeros((len(o), 4))
        gl[:, :3] = np.round(o[:, :3] * k[:, None])
        gl[:, 3] = np.where(k > 0, 255, 0)
        return o, gl
    return mat


def glowmasks():
    for n in range(1, 6):
        geo = Geo.load(os.path.join(GEO, "ghost_%d.geo.json" % n))
        base = Image.open(os.path.join(TEX, "ghost_%d.png" % n))
        _, gl = paint(geo, glow_rule(n), base=base)
        gl.save(os.path.join(TEX, "ghost_%d_glow.png" % n))
        print("ghost %d glow texels: %d" % (n, (np.asarray(gl)[..., 3] > 0).sum()))
    for n in range(1, 4):
        geo = Geo.load(os.path.join(GEO, "stalker_%d.geo.json" % n))
        base = Image.open(os.path.join(TEX, "stalker_%d.png" % n))
        _, gl = paint(geo, glow_rule(10 + n), base=base)
        gl.save(os.path.join(TEX, "stalker_%d_glow.png" % n))


# ----------------------------------------------------------------------------- face rigs
FACE_BONES = {"head", "jaw", "hair", "neck", "chest", "rightArm", "rightForearm", "rightHand", "leftArm", "leftForearm", "leftHand",
              "extraArmR", "extraArmL"}
STALKER_FACE = {1: {"torso", "neck", "head", "jaw", "armR", "foreR", "handR", "armL", "foreL", "handL"},
                2: {"body", "armR", "armL", "head", "hair"},
                3: {"torso", "head", "hair", "armR", "foreR", "handR", "armL", "foreL", "handL"}}
EYE_Y = 1.62 * 16


def face_rig(src_geo, keep, out_name, eye_frac=0.58):
    g = Geo.load(src_geo)
    f = g.subset(keep, "geometry.hollowhours." + out_name)
    head = f.bone("head")
    pts = []
    for c in head["cubes"]:
        o, s = c["origin"], c["size"]
        pts += [o, [o[0] + s[0], o[1] + s[1], o[2] + s[2]]]
    pts = np.array(pts, float)
    lo, hi = pts.min(0), pts.max(0)
    eye = np.array([0.0, lo[1] + (hi[1] - lo[1]) * eye_frac, lo[2]])
    f.translate(-eye[0], EYE_Y - eye[1], -eye[2])
    f.extra_desc = {"visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]}
    os.makedirs(os.path.join(A, "geo", "face"), exist_ok=True)
    f.save(os.path.join(A, "geo", "face", out_name + ".geo.json"))
    return f


def faces():
    os.makedirs(os.path.join(A, "textures", "face"), exist_ok=True)
    for n in range(1, 6):
        face_rig(os.path.join(GEO, "ghost_%d.geo.json" % n), FACE_BONES, "face_g%d" % n, 0.5 if n == 5 else 0.58)
        Image.open(os.path.join(TEX, "ghost_%d.png" % n)).save(os.path.join(A, "textures", "face", "face_g%d.png" % n))
        Image.open(os.path.join(TEX, "ghost_%d_glow.png" % n)).save(os.path.join(A, "textures", "face", "face_g%d_glow.png" % n))
    for n in range(1, 4):
        face_rig(os.path.join(GEO, "stalker_%d.geo.json" % n), STALKER_FACE[n], "face_s%d" % n, 0.55)
        Image.open(os.path.join(TEX, "stalker_%d.png" % n)).save(os.path.join(A, "textures", "face", "face_s%d.png" % n))
        Image.open(os.path.join(TEX, "stalker_%d_glow.png" % n)).save(os.path.join(A, "textures", "face", "face_s%d_glow.png" % n))
    # animations: the face snaps toward you, the mouth tears open, arms come up at the camera
    anims = {}
    for name, tilt, jaw in (("scream", 18, 82), ("scream2", -65, 95)):
        a = Anim(name, 2.6)
        for arm, fore, hand, side in (("rightArm", "rightForearm", "rightHand", 1), ("leftArm", "leftForearm", "leftHand", -1),
                                      ("armR", "foreR", "handR", 1), ("armL", "foreL", "handL", -1)):
            a.rot(0, arm, (-20, 0, 25 * side)).rot(0.12, arm, (-105, -25 * side, 12 * side), "snap").rot(2.6, arm, (-100, -20 * side, 14 * side))
            a.rot(0, fore, (0, 0, 0)).rot(0.12, fore, (-25, 0, 0), "snap").rot(2.6, fore, (-30, 0, 0))
            a.rot(0, hand, (0, 0, 0)).rot(0.12, hand, (-40, 0, 0), "snap").rot(2.6, hand, (-35, 0, 0))
        a.rot(0, "head", (-15, 0, tilt * 0.4)).rot(0.08, "head", (8, 0, tilt), "snap")
        for i in range(30):
            t = 0.12 + i * 0.08
            a.rot(t, "head", (8 + (5 if i % 2 else -5), (9 if i % 3 == 0 else -7), tilt + (11 if i % 2 else -11)), "lin")
        a.rot(0, "jaw", (5, 0, 0)).rot(0.07, "jaw", (jaw, 0, 0), "snap")
        for i in range(30):
            a.rot(0.12 + i * 0.08, "jaw", (jaw - (12 if i % 2 else 0), 0, 0), "lin")
        a.scl(0, "jaw", 1).scl(0.08, "jaw", (1.1, 1.5, 1.25), "snap").scl(2.6, "jaw", (1.1, 1.5, 1.25))
        a.rot(0, "neck", (0, 0, 0)).rot(0.08, "neck", (-12, 0, 0), "snap")
        a.rot(0, "hair", (0, 0, 0)).rot(0.1, "hair", (-25, 0, 0), "snap").rot(2.6, "hair", (-20, 0, 0))
        anims["animation.face." + name] = a.to_json(fps=30)
    os.makedirs(os.path.join(A, "animations", "face"), exist_ok=True)
    with open(os.path.join(A, "animations", "face", "face.animation.json"), "w") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, separators=(",", ":"))


# ----------------------------------------------------------------------------- screen textures
def gui():
    G = os.path.join(A, "textures", "gui")
    os.makedirs(G, exist_ok=True)
    W, H = 512, 288
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    nx, ny = (xx - W / 2) / (W / 2), (yy - H / 2) / (H / 2)
    r = np.sqrt(nx ** 2 * 0.85 + ny ** 2)
    # vignette: black edges
    v = np.clip((r - 0.45) / 0.65, 0, 1) ** 1.6
    img = np.zeros((H, W, 4))
    img[..., 3] = v * 255
    Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(G, "vignette.png"))
    # blood: dark red edges, drips from the top, splatters
    pts = np.c_[xx.ravel() / 40, yy.ravel() / 40, np.zeros(W * H)]
    n1 = fbm(pts, 2.0, 5, 7).reshape(H, W)
    n2 = fbm(pts * 3, 1.0, 4, 11).reshape(H, W)
    edge = np.clip((r - 0.35 + (n1 - 0.5) * 0.7) / 0.35, 0, 1)
    drips = np.zeros((H, W))
    rng = np.random.default_rng(5)
    for _ in range(26):
        x0 = rng.uniform(0, W)
        ln = rng.uniform(20, 170)
        wd = rng.uniform(2, 7)
        d = np.exp(-((xx - x0) / wd) ** 2) * (yy < ln) * np.clip(1 - yy / ln, 0.2, 1)
        drips = np.maximum(drips, d)
    for _ in range(14):
        cx, cy, rr = rng.uniform(0, W), rng.uniform(0, H), rng.uniform(4, 22)
        dd = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
        drips = np.maximum(drips, np.clip(1 - dd / (rr * (0.7 + 0.6 * n2)), 0, 1) * (rng.uniform() < 0.5 or r[int(cy), int(cx)] > 0.6))
    a = np.clip(np.maximum(edge, drips * 0.95), 0, 1)
    col = np.zeros((H, W, 4))
    shade = 0.55 + 0.45 * n2
    col[..., 0] = 110 * shade + 40 * (1 - a)
    col[..., 1] = 4 * shade
    col[..., 2] = 6 * shade
    col[..., 3] = a * 235
    Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(G, "blood.png"))
    # static: 4 frames of 128x128 noise with scanlines
    for i in range(4):
        rng = np.random.default_rng(100 + i)
        s = rng.uniform(0, 1, (128, 128))
        s = (s ** 1.6) * 255
        lines = (np.arange(128) % 3 == 0)[:, None] * 0.6 + 0.4
        im = np.zeros((128, 128, 4))
        im[..., 0] = im[..., 1] = im[..., 2] = s * lines
        im[..., 3] = 150 + 105 * (s / 255)
        Image.fromarray(im.astype(np.uint8), "RGBA").save(os.path.join(G, "static_%d.png" % i))
    # guide page: old stained paper
    PW, PH = 360, 230
    yy, xx = np.mgrid[0:PH, 0:PW].astype(float)
    pts = np.c_[xx.ravel() / 30, yy.ravel() / 30, np.ones(PW * PH)]
    n = fbm(pts, 1.5, 5, 3).reshape(PH, PW)
    m = fbm(pts * 2, 1.0, 4, 9).reshape(PH, PW)
    base = np.array([196, 178, 140], float)
    page = np.zeros((PH, PW, 4))
    tone = 0.78 + 0.3 * n
    for c in range(3):
        page[..., c] = base[c] * tone
    stain = np.clip((m - 0.62) / 0.12, 0, 1)
    page[..., 0] = page[..., 0] * (1 - stain) + 95 * stain
    page[..., 1] = page[..., 1] * (1 - stain) + 30 * stain
    page[..., 2] = page[..., 2] * (1 - stain) + 25 * stain
    ex = np.minimum(np.minimum(xx, PW - 1 - xx), np.minimum(yy, PH - 1 - yy))
    burn = np.clip(1 - (ex - 2 + (n - 0.5) * 10) / 14, 0, 1)
    for c in range(3):
        page[..., c] *= 1 - 0.75 * burn
    page[..., 3] = 255
    page[ex < 1] = [40, 25, 20, 255]
    # spine down the middle-left (menu | page)
    sx = 108
    page[:, sx - 1:sx + 1, :3] *= 0.6
    Image.fromarray(np.clip(page, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(G, "guide_bg.png"))


def flashlight_on():
    I = os.path.join(A, "textures", "item")
    im = Image.open(os.path.join(I, "flashlight.png")).convert("RGBA")
    a = np.asarray(im).astype(float)
    L = a[..., :3].mean(-1)
    bright = (L > 150) & (a[..., 3] > 0)
    a[bright, 0] = np.minimum(255, a[bright, 0] * 1.1 + 40)
    a[bright, 1] = np.minimum(255, a[bright, 1] * 1.1 + 35)
    a[bright, 2] = np.minimum(255, a[bright, 2] * 0.8 + 10)
    Image.fromarray(a.astype(np.uint8), "RGBA").save(os.path.join(I, "flashlight_on.png"))


if __name__ == "__main__":
    glowmasks()
    faces()
    gui()
    flashlight_on()
    print("done")
