#!/usr/bin/env python3
"""The Thing That Weeps Under the Mountain (the final boss): model, texture, glow and animations.

A colossal, starved figure buried to the waist in the cavern floor under the mountain. Grey stone-like skin split by
glowing veins of gold (the vein Thongdee followed down), a small child-like face with hollow eyes and black tears
(it cries with his daughter's voice), long black hair, and two enormous hands. Each hand has five fingers, and
each finger wears a token of one of the five it made into its hands: a miner's lamp, a scrap of bridal veil, an
ember, a black smile, a stub of antler.

Output:
  geo/entity/weeping.geo.json, textures/entity/weeping.png (+ _glow.png), animations/entity/weeping.animation.json
  previews -> argv[1] (optional)
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from ghostgen import Geo, paint, fbm, ridge, col, rgba, shade_edges  # noqa: E402
from georender import Model, render, pose_from_anims  # noqa: E402
from animkit import Anim  # noqa: E402

UA, FA = 48, 54   # upper arm / forearm length (px)
RES = os.path.join(HERE, "..", "hollow-hours", "src", "main", "resources", "assets", "hollowhours")


# ----------------------------------------------------------------------------- geometry (bedrock px, faces -z)
def build():
    g = Geo("geometry.hollowhours.weeping")
    rng = np.random.default_rng(7)
    g.add_bone("root", None, [0, 0, 0])
    # the ground it broke through (stays put while the body rises / sinks)
    g.add_bone("rubble", "root", [0, 0, 0])
    g.box("rubble", [0, -0.6, 0], [84, 1.4, 84], "pit", _density=0.35)
    for i in range(22):
        a = i / 22 * 2 * math.pi + rng.uniform(-0.12, 0.12)
        r = rng.uniform(28, 40)
        w, h, d = rng.uniform(7, 14), rng.uniform(3, 11), rng.uniform(6, 12)
        g.box("rubble", [math.cos(a) * r, h / 2 - 1.5, math.sin(a) * r], [w, h, d], "rock",
              rot=[rng.uniform(-18, 18), math.degrees(-a) + rng.uniform(-20, 20), rng.uniform(-22, 22)], _density=0.6)
    for i in range(10):
        a = rng.uniform(0, 2 * math.pi)
        r = rng.uniform(42, 52)
        s = rng.uniform(3, 6)
        g.box("rubble", [math.cos(a) * r, s / 2 - 1, math.sin(a) * r], [s, s * 0.8, s], "rock", rot=[rng.uniform(-30, 30), rng.uniform(0, 90), rng.uniform(-30, 30)], _density=0.6)

    g.add_bone("body", "root", [0, 0, 0])
    g.add_bone("waist", "body", [0, 6, 2])
    g.box("waist", [0, -6, 3], [36, 26, 24], "skin")
    g.box("waist", [0, 14, 4], [30, 18, 20], "skin")           # starved belly
    g.add_bone("chest", "waist", [0, 26, 3])
    g.box("chest", [0, 44, 3], [46, 34, 28], "skin")
    for k in range(6):                                          # ribs pushing through the skin
        y = 31 + k * 5
        w = 40 - abs(k - 2.5) * 3
        g.box("chest", [0, y, -11.6], [w, 2.2, 2.2], "rib")
        g.box("chest", [21.5, y + 1, -2], [3, 2, 20], "rib", rot=[0, 0, 8])
        g.box("chest", [-21.5, y + 1, -2], [3, 2, 20], "rib", rot=[0, 0, -8])
    g.box("chest", [0, 46, -11.8], [4, 30, 2], "rib")             # breastbone
    for k in range(9):                                          # spine ridge
        g.box("chest", [0, 24 + k * 5.4, 17.6], [6, 4, 4], "rib", rot=[12, 0, 0])
    g.sym("chest", "chest", [16, 62, 4], [18, 12, 22], "skin", rot=[0, 0, -12])   # hunched shoulders
    g.box("chest", [0, 63, 8], [26, 8, 18], "skin")
    # neck and head
    g.add_bone("neck", "chest", [0, 64, -1])
    g.box("neck", [0, 71, -5], [12, 16, 12], "skin", rot=[-24, 0, 0])
    for k in range(3):
        g.box("neck", [0, 66 + k * 4.5, 1.5], [4, 3, 3], "rib", rot=[-24, 0, 0])
    g.add_bone("head", "neck", [0, 77, -9])
    g.box("head", [0, 89, -12], [26, 26, 24], "skull")
    g.box("head", [0, 88, -24.4], [22, 22, 1.2], "face", _density=3.0)
    g.sym("head", "head", [11.5, 84, -21], [3, 8, 6], "skull")  # cheekbones
    g.box("head", [0, 99.5, -21], [24, 4, 6], "skull")           # brow
    g.add_bone("jaw", "head", [0, 80, -14])
    g.box("jaw", [0, 77, -18.5], [18, 6, 13], "skull")
    g.box("jaw", [0, 77.5, -24.6], [12, 3.5, 0.6], "mouth")
    # hair: long black strands, in three swaying bunches
    for side, sx in (("hair_l", 1), ("hair_r", -1), ("hair_b", 0)):
        px = 10 * sx
        g.add_bone(side, "head", [px, 101, -6 if side != "hair_b" else 2])
        n = 7 if side != "hair_b" else 9
        for i in range(n):
            ln = rng.uniform(30, 58) if side != "hair_b" else rng.uniform(38, 66)
            if side == "hair_b":
                x = rng.uniform(-12, 12)
                z = rng.uniform(2, 10)
            else:
                x = sx * rng.uniform(9, 14)
                z = rng.uniform(-18, 8)
            g.box(side, [x, 101 - ln / 2, z], [rng.uniform(2.2, 4), ln, rng.uniform(2.2, 4)], "hair",
                  rot=[rng.uniform(-6, 10), 0, sx * rng.uniform(2, 9)], _density=0.6)
    g.box("head", [0, 101.5, -8], [28, 4, 26], "hair")

    # arms: bind pose hangs straight down (animations put the hands where they belong)
    for s, sx in (("l", 1), ("r", -1)):
        X = 26 * sx
        g.add_bone("arm_" + s, "chest", [X, 64, 3])
        g.box("arm_" + s, [X, 64 - UA / 2, 3], [12, UA + 2, 12], "skin")
        g.box("arm_" + s, [X + 3 * sx, 58, 3], [8, 10, 14], "skin")   # shoulder knob
        EL = 64 - UA
        g.add_bone("forearm_" + s, "arm_" + s, [X, EL, 3])
        g.box("forearm_" + s, [X, EL, 3], [14, 9, 14], "rib")         # elbow
        g.box("forearm_" + s, [X, EL - FA / 2, 3], [10, FA, 10], "skin")
        g.box("forearm_" + s, [X, EL - FA + 7, 3], [12, 10, 12], "skin")        # wrist swell
        W = EL - FA
        g.add_bone("hand_" + s, "forearm_" + s, [X, W, 3])
        # palm faces back while hanging (so it faces the floor when the arm reaches forward); fingers spread sideways
        g.box("hand_" + s, [X, W - 9, 3], [22, 18, 8], "skin")
        g.box("hand_" + s, [X, W - 17, 0.5], [20, 4, 4], "rib")          # knuckles (back of the hand)
        for i in range(5):
            if i == 0:   # thumb: on the inner side, angled in
                fx, fy, ln = X - sx * 13, W - 5, 18
            else:
                fx, fy, ln = X + sx * (-7.5 + (i - 1) * 5.0), W - 18, 22 + (2 if i in (2, 3) else 0)
            fb = "f%s%d" % (s, i + 1)
            g.add_bone(fb, "hand_" + s, [fx, fy, 3])
            g.add_bone(fb + "b", fb, [fx, fy - ln * 0.5, 3])
            rot = [0, 0, sx * 35] if i == 0 else None
            if i == 0:
                g.cube(fb, [fx - 1.8, fy - ln * 0.5, 1.2], [3.6, ln * 0.5, 3.6], "skin", rot=rot, pivot=[fx, fy, 3])
                g.cube(fb + "b", [fx - 1.6, fy - ln, 1.4], [3.2, ln * 0.5, 3.2], "nail", rot=rot, pivot=[fx, fy, 3])
            else:
                g.box(fb, [fx, fy - ln * 0.25, 3], [3.6, ln * 0.5, 3.6], "skin")
                g.box(fb + "b", [fx, fy - ln * 0.75, 3], [3.2, ln * 0.5, 3.2], "nail")
            # its token: lamp, veil, ember, smile, antler
            tag = "tok%d" % (i + 1)
            ty = fy - ln * 0.3
            if i == 0:
                g.cube(fb, [fx - 2.5, fy - 7, 0.5], [5, 3, 5], tag, rot=rot, pivot=[fx, fy, 3])
            elif i == 1:
                g.box(fb, [fx, ty, 3], [4.6, 2.4, 4.6], tag)
                g.box(fb, [fx, ty - 5, 5.6], [4.2, 9, 0.6], tag)       # veil scrap hanging
            elif i == 2:
                g.box(fb, [fx, ty, 3], [4.8, 2.6, 4.8], tag)
            elif i == 3:
                g.box(fb, [fx, ty, 3], [4.6, 2.6, 4.6], tag)
            else:
                g.box(fb, [fx, ty, 3], [4.6, 2.4, 4.6], tag)
                g.box(fb, [fx, ty + 1, 0], [2, 6, 2], tag, rot=[-30, 0, 0])   # antler stub
    g.extra_desc["visible_bounds_width"] = 12
    g.extra_desc["visible_bounds_height"] = 10
    g.extra_desc["visible_bounds_offset"] = [0, 3, 0]
    return g


# ----------------------------------------------------------------------------- texture
def material(ctx):
    p, tag, n = ctx["p"], ctx["tag"], ctx["n"]
    N = ctx["n_tex"]
    q = p * np.array([1, 1, 1])
    glow = np.zeros((N, 4))
    if tag in ("skin", "skull", "face"):
        base = col("8f9396") if tag == "skin" else col("a9a8a2") if tag == "skull" else col("c4c0b8")
        nz = fbm(q, 9, 4, seed=3)
        rgb = base[None, :] * (0.72 + 0.5 * nz[:, None])
        blotch = fbm(q + 40, 22, 3, seed=9)
        rgb = rgb * (0.85 + 0.25 * blotch[:, None]) + np.array([-8, -4, 6]) * (blotch[:, None] - 0.5)
        # gold veins: cracks through the stone-like skin (not on the face)
        r = ridge(q, 26 if tag == "skin" else 34, 3, seed=5)
        vein = np.clip((r - 0.62) / 0.3, 0, 1) * (fbm(q + 300, 40, 2, seed=8) > 0.42)
        if tag == "face":
            x, y = q[:, 0], q[:, 1]
            dark = np.zeros(N)
            for ex in (5.4, -5.4):
                gx = -ex                          # georender x = -bedrock x
                u = (x - gx) * np.sign(gx)        # + = towards the outer corner
                v = y - 90.5 + 0.38 * u           # outer corners droop
                sock = (u / 4.0) ** 2 + (v / 3.4) ** 2
                dark = np.maximum(dark, np.clip((1.15 - sock) * 3, 0, 1))
                # soot round the socket
                rgb = rgb * (1 - 0.35 * np.clip(1.9 - sock, 0, 1)[:, None])
                # pupils: tiny cold lights, looking up
                pup = ((x - gx) ** 2 + (y - 91.3) ** 2) < 0.75
                glow[pup, :3] = np.array([235, 235, 225])
                glow[pup, 3] = 190
                # brows: inner ends raised (grief)
                bx = np.abs(x)
                by = 97.4 - (bx - 1.8) * 0.36
                brow = (np.abs(y - by) < 0.55) * (bx > 1.8) * (bx < 9.5) * (np.sign(x) == np.sign(gx))
                dark = np.maximum(dark, brow * 0.85)
                # black tears: down from the socket to the chin, wobbling
                wob = np.sin(y * 0.7 + ex) * 0.5 + 0.9 * np.sign(gx)
                streak = np.clip(1.4 - np.abs(x - gx - wob) / 0.75, 0, 1) * (y < 88.5) * (y > 76.5)
                drop = (((x - gx - wob) ** 2) / 1.2 + ((y - 79.5 - ex * 0.3) ** 2) / 2.2) < 1
                dark = np.maximum(dark, np.maximum(streak, drop * 1.0))
            # mouth: open in a sob, corners pulled down
            mu = x / 4.4
            mv = (y - 81.0 + 1.1 * mu ** 2) / 2.6
            mouth = np.clip((1 - (mu ** 2 + mv ** 2)) * 4, 0, 1)
            rgb = rgb * (1 - mouth[:, None]) + np.array([26, 6, 6]) * mouth[:, None]
            lip = np.clip(1 - np.abs(np.sqrt(mu ** 2 + mv ** 2) - 1.05) / 0.12, 0, 1) * (1 - mouth)
            rgb = rgb * (1 - 0.4 * lip[:, None])
            rgb = rgb * (1 - dark[:, None]) + np.array([6, 6, 8]) * dark[:, None]
            vein *= 0
        rgb = rgb * (1 - 0.65 * vein[:, None]) + np.array([255, 196, 70]) * 0.65 * vein[:, None]
        glow[:, :3] = np.array([255, 190, 60])
        glow[:, 3] = 210 * vein
        rgb = shade_edges(rgb, ctx, 0.2, 1.5)
        return rgba(rgb), glow
    if tag == "rib":
        nz = fbm(q, 5, 3, seed=11)
        rgb = col("c9bfa8")[None, :] * (0.78 + 0.35 * nz[:, None])
        return rgba(shade_edges(rgb, ctx, 0.25, 1)), glow
    if tag == "nail":
        nz = fbm(q, 4, 3, seed=12)
        tip = np.clip((ctx["lp"][:, 1] < 0.45) * 1.0, 0, 1)
        rgb = col("6f7174")[None, :] * (0.8 + 0.3 * nz[:, None])
        rgb = rgb * (1 - 0.8 * tip[:, None]) + col("1a1414") * 0.8 * tip[:, None]
        return rgba(shade_edges(rgb, ctx, 0.2, 1)), glow
    if tag == "eye":
        rgb = np.tile(col("020203"), (N, 1))
        d = np.hypot(ctx["lp"][:, 0] - 0.5, ctx["lp"][:, 1] - 0.45)
        pupil = (d < 0.13) * 1.0
        rgb = rgb + pupil[:, None] * np.array([180, 180, 175])
        glow[:, :3] = np.array([235, 235, 225])
        glow[:, 3] = 160 * pupil
        return rgba(rgb), glow
    if tag == "mouth":
        rgb = np.tile(col("140606"), (N, 1)) * (0.7 + 0.5 * fbm(q, 2, 2, seed=2)[:, None])
        return rgba(rgb), glow
    if tag == "hair":
        nz = fbm(q * np.array([1, 0.15, 1]), 2.5, 3, seed=21)
        rgb = col("121316")[None, :] * (0.6 + 0.9 * nz[:, None]) + np.array([4, 6, 12]) * nz[:, None]
        return rgba(rgb), glow
    if tag == "rock":
        nz = fbm(q, 6, 4, seed=31)
        rgb = col("3d3b3e")[None, :] * (0.65 + 0.6 * nz[:, None])
        fleck = (fbm(q + 7, 1.5, 2, seed=33) > 0.78) * 1.0
        rgb = rgb * (1 - fleck[:, None]) + fleck[:, None] * np.array([214, 168, 60])
        glow[:, :3] = np.array([255, 190, 60])
        glow[:, 3] = 90 * fleck
        return rgba(shade_edges(rgb, ctx, 0.3, 1.5)), glow
    if tag == "pit":
        r = np.hypot(q[:, 0], q[:, 2])
        a = np.clip(1 - (r - 30) / 12, 0, 1)
        nz = fbm(q, 5, 3, seed=41)
        rgb = col("070708")[None, :] * (0.7 + 0.5 * nz[:, None]) + np.array([20, 16, 12]) * (1 - a)[:, None]
        alpha = np.where(r < 42, 255.0, 0.0) * (np.abs(n[1]) > 0.5) + (np.abs(n[1]) <= 0.5) * 0.0
        return rgba(rgb, alpha), glow
    if tag.startswith("tok"):
        k = int(tag[3])
        nz = fbm(q, 2, 2, seed=50 + k)
        if k == 1:   # miner's lamp: brass with a lit window
            rgb = col("8a6a2a")[None, :] * (0.8 + 0.4 * nz[:, None])
            glow[:, :3] = np.array([255, 220, 120])
            glow[:, 3] = 200 * (nz > 0.45)
        elif k == 2:  # bridal veil
            rgb = col("e8e6e0")[None, :] * (0.85 + 0.2 * nz[:, None])
        elif k == 3:  # ember
            rgb = col("2a1208")[None, :] * (0.7 + 0.5 * nz[:, None])
            glow[:, :3] = np.array([255, 110, 30])
            glow[:, 3] = 230 * np.clip((nz - 0.35) * 3, 0, 1)
        elif k == 4:  # black ring with a white smile
            rgb = np.tile(col("0b0b0d"), (N, 1))
            lp = ctx["lp"]
            smile = (np.abs((lp[:, 1] - 0.55) - 0.6 * (lp[:, 0] - 0.5) ** 2 * 4) < 0.08) * (np.abs(lp[:, 0] - 0.5) < 0.35)
            rgb = rgb + smile[:, None] * np.array([220, 220, 220])
            glow[:, :3] = np.array([235, 235, 235])
            glow[:, 3] = 120 * smile
        else:         # antler
            rgb = col("d8ccb0")[None, :] * (0.8 + 0.3 * nz[:, None])
        return rgba(shade_edges(rgb, ctx, 0.2, 1)), glow
    return rgba(np.tile(col("ff00ff"), (N, 1))), glow


# ----------------------------------------------------------------------------- animations
# Conventions (checked on the model): arm X- swings a hanging arm forward / up; Z- swings the +x (left) arm outward,
# so "outward" = -sx * deg; Y turns a forward-pointing arm sideways; finger X+ curls (towards the palm).


_MODEL = []


def _model():
    if not _MODEL:
        g = build()
        g.pack(1.0, width=512)
        _MODEL.append(Model(g.to_dict()))
    return _MODEL[0]


def solve_palm(dist, chest=(24, 0, 0), s="l", outward=4, floor=0.3):
    """(arm X, forearm X, hand X) that put the palm flat on the floor, its middle `dist` blocks in front."""
    from georender import bone_matrices
    from scipy.optimize import minimize
    m = _model()
    sx = 1 if s == "l" else -1
    hb = m.bones["hand_" + s]

    def pts(x):
        pose = {"chest": [chest, (0, 0, 0), (1, 1, 1)], "arm_" + s: [(x[0], 0, -sx * outward), (0, 0, 0), (1, 1, 1)],
                "forearm_" + s: [(x[1], 0, 0), (0, 0, 0), (1, 1, 1)], "hand_" + s: [(x[2], 0, 0), (0, 0, 0), (1, 1, 1)]}
        mats, _ = bone_matrices(m, pose)
        out = []
        for dy in (0, -9, -18):
            c = (mats["hand_" + s] @ np.array([hb["pivot"][0], hb["pivot"][1] + dy, hb["pivot"][2], 1]))[:3]
            out.append((-c[2] / 16, c[1] / 16))
        return out

    def f(x):
        w, mid, tip = pts(x)
        return (mid[0] - dist) ** 2 * 4 + (mid[1] - floor) ** 2 * 10 + (tip[1] - floor) ** 2 * 10 + (w[1] - floor - 0.15) ** 2 * 4 \
            + 1e-4 * x[1] ** 2 + 1e-3 * max(0, x[1]) ** 2 + 1e-3 * max(0, -x[2] - 90) ** 2
    best = None
    for a0 in (-20, -45, -70):
        for f0 in (-50, -20, 0):
            r = minimize(f, [a0, f0, -20], method="Nelder-Mead", options={"maxiter": 900, "xatol": 0.05, "fatol": 1e-6})
            if best is None or r.fun < best.fun:
                best = r
    return tuple(round(float(v), 1) for v in best.x)


def arms(a, t, s, arm, fore, hand=(0, 0, 0), fingers=None, e="io"):
    a.rot(t, "arm_" + s, arm, e)
    a.rot(t, "forearm_" + s, fore, e)
    a.rot(t, "hand_" + s, hand, e)
    if fingers is not None:
        for i in range(1, 6):
            a.rot(t, "f%s%d" % (s, i), (fingers[0], 0, 0), e)
            a.rot(t, "f%s%db" % (s, i), (fingers[1], 0, 0), e)


def solve_reach(target, base, s="r", hand=(0, 0, 0), x0=(-90, 0, 0, -40)):
    """(arm rot xyz, forearm X) that bring the palm centre to `target` (bedrock px, model space) with `base` pose."""
    from georender import bone_matrices
    from scipy.optimize import minimize
    m = _model()
    hb = m.bones["hand_" + s]
    tgt = np.array([-target[0], target[1], target[2]])

    def pt(x):
        pose = {k: [v, (0, 0, 0), (1, 1, 1)] for k, v in base.items()}
        pose["arm_" + s] = [(x[0], x[1], x[2]), (0, 0, 0), (1, 1, 1)]
        pose["forearm_" + s] = [(x[3], 0, 0), (0, 0, 0), (1, 1, 1)]
        pose["hand_" + s] = [hand, (0, 0, 0), (1, 1, 1)]
        mats, _ = bone_matrices(m, pose)
        return (mats["hand_" + s] @ np.array([hb["pivot"][0], hb["pivot"][1] - 9, hb["pivot"][2], 1]))[:3]

    def f(x):
        return float(np.sum((pt(x) - tgt) ** 2)) + 1e-3 * (x[1] ** 2 + x[2] ** 2) + 1e-2 * max(0, x[3]) ** 2
    best = None
    for start in (x0, (x0[0] - 30, x0[1], x0[2], x0[3] - 30), (x0[0] + 30, x0[1], x0[2], x0[3] + 20)):
        r = minimize(f, list(start), method="Nelder-Mead", options={"maxiter": 2000, "xatol": 0.05, "fatol": 1e-4})
        if best is None or r.fun < best.fun:
            best = r
    return tuple(round(float(v), 1) for v in best.x[:3]), (round(float(best.x[3]), 1), 0, 0), round(float(np.sqrt(best.fun)), 1)


_SOLVED = {}


def solve_cached(dist, chest, s, outward):
    k = (dist, tuple(chest), s, outward)
    if k not in _SOLVED:
        _SOLVED[k] = solve_palm(dist, chest, s, outward)
    return _SOLVED[k]


def rest(a, t, e="io", only=None):
    """Leaning on its hands: arms reach forward and down, palms flat on the floor, fingertips curled in."""
    for s, sx in (("l", 1), ("r", -1)):
        if only and s != only:
            continue
        ax, fx_, hx = solve_cached(3.0, (10, 0, 0), s, 10)
        arms(a, t, s, (ax, sx * 8, -sx * 10), (fx_, 0, 0), (hx, 0, 0), (14, 22), e)


def body_idle(a, t0, t1, sob=1.0):
    n = max(1, int(round((t1 - t0) / 0.5)))
    for i in range(n + 1):
        t = t0 + i * (t1 - t0) / n
        k = 1 if i % 2 else -1
        a.rot(t, "chest", (10 + 2 * k * sob, 0, 1.0 * k), "io")
        a.rot(t, "neck", (12 + 1.5 * k * sob, 0, 0), "io")
        a.rot(t, "head", (12 + 3 * k * sob, 2 * k, -2 * k), "io")
        a.rot(t, "jaw", (8 + 4 * (1 + k) * sob, 0, 0), "io")
        a.rot(t, "hair_l", (2 * k, 0, 2 * k), "io")
        a.rot(t, "hair_r", (-2 * k, 0, -2 * k), "io")


SLAM_UP = {"near": ((-168, 0, 0), (-40, 0, 0)), "far": ((-172, 0, 0), (-20, 0, 0))}
SLAM_DIST = {"near": 3.9, "far": 6.3}
SLAM_HIT = 1.06
GRAB = {}


def anims():
    out = {}

    a = Anim("animation.weeping.idle", 4.0, loop=True)
    body_idle(a, 0, 4.0)
    rest(a, 0)
    rest(a, 2.0)
    rest(a, 4.0)
    a.rot(1.0, "fl3", (2, 0, 0))
    a.rot(3.0, "fr4", (4, 0, 0))
    out[a.name] = a

    # rising out of the ground (the rubble stays)
    a = Anim("animation.weeping.rise", 4.0)
    a.pos(0, "body", (0, -112, 0))
    a.pos(2.6, "body", (0, -6, 0), "out")
    a.pos(3.2, "body", (0, 0, 0), "io")
    a.rot(0, "chest", (40, 0, 0))
    a.rot(2.6, "chest", (-12, 0, 0), "out")
    a.rot(3.6, "chest", (10, 0, 0))
    a.rot(0, "head", (40, 0, 0))
    a.rot(2.8, "head", (-34, 0, 0), "out")
    a.rot(3.6, "head", (12, 0, 0))
    a.rot(2.8, "jaw", (32, 0, 0))
    a.rot(3.8, "jaw", (8, 0, 0))
    for s, sx in (("l", 1), ("r", -1)):
        arms(a, 0, s, (-175, 0, -sx * 8), (0, 0, 0), (0, 0, 0), (0, 0))
        arms(a, 1.6, s, (-165, 0, -sx * 28), (-10, 0, 0), (0, 0, 0), (-10, 0), "out")
        arms(a, 2.4, s, (-100, sx * 10, -sx * 30), (-20, 0, 0), (-20, 0, 0), (0, 0))
    rest(a, 3.4, "back")
    out[a.name] = a

    # slams: one arm high overhead, then down flat on the floor; it stays down a moment (punish it then)
    for s, sx in (("l", 1), ("r", -1)):
        other = "r" if s == "l" else "l"
        for kind in ("near", "far"):
            a = Anim("animation.weeping.slam_%s_%s" % (kind, s), 2.4)
            body_idle(a, 0, 2.4, 0.4)
            rest(a, 0)
            rest(a, 2.4, only=other)
            up, fo = SLAM_UP[kind]
            arms(a, 0.9, s, (up[0], 0, -sx * 18), fo, (-20, 0, 0), (-12, -8), "out")
            a.rot(0.9, "chest", (-16, sx * 14, sx * 6), "out")
            a.rot(0.9, "head", (-14, 0, 0), "out")
            a.rot(0.9, "jaw", (24, 0, 0))
            dx_, df_, dh_ = solve_cached(SLAM_DIST[kind], (24, -sx * 8, -sx * 4), s, 4)
            dn, df, dh = (dx_, 0, 0), (df_, 0, 0), (dh_, 0, 0)
            arms(a, SLAM_HIT, s, (dn[0], 0, -sx * 4), df, dh, (16, 10), "in")
            a.rot(SLAM_HIT, "chest", (24, -sx * 8, -sx * 4), "in")
            a.rot(SLAM_HIT, "head", (16, 0, 0), "in")
            arms(a, 1.75, s, (dn[0], 0, -sx * 4), df, dh, (18, 12))
            a.rot(1.75, "chest", (22, -sx * 8, -sx * 4))
            rest(a, 2.4, only=s)
            a.rot(2.4, "chest", (10, 0, 0))
            a.rot(2.4, "head", (12, 0, 0))
            a.ev(SLAM_HIT, "slam")
            out[a.name] = a

    # sweep: the right arm swings across the floor in front, from its right to its left (jump over it)
    a = Anim("animation.weeping.sweep", 2.4)
    body_idle(a, 0, 2.4, 0.3)
    rest(a, 0)
    rest(a, 2.4, only="l")
    sa, sf, sh = solve_cached(5.0, (14, 0, 0), "r", 0)
    arms(a, 0.9, "r", (sa, -70, 0), (sf, 0, 0), (sh, 0, 0), (6, 6), "out")
    a.rot(0.9, "chest", (14, -24, 0), "out")
    arms(a, 1.55, "r", (sa, 70, 0), (sf, 0, 0), (sh, 0, 0), (6, 6), "lin")
    a.rot(1.55, "chest", (14, 26, 0), "out")
    rest(a, 2.4, only="r")
    a.rot(2.4, "chest", (10, 0, 0))
    out[a.name] = a

    # scream: head thrown back, jaw wide, arms flung out, shaking
    a = Anim("animation.weeping.scream", 2.6)
    rest(a, 0)
    a.rot(0, "chest", (10, 0, 0))
    a.rot(0.8, "chest", (-18, 0, 0), "out")
    a.rot(0, "head", (12, 0, 0))
    a.rot(0.8, "head", (-40, 0, 0), "out")
    a.rot(0, "jaw", (8, 0, 0))
    a.rot(0.8, "jaw", (42, 0, 0), "back")
    for s, sx in (("l", 1), ("r", -1)):
        arms(a, 0.8, s, (-30, 0, -sx * 105), (0, 0, -sx * 20), (0, 0, 0), (-14, -10), "out")
    k, t = 0, 0.85
    while t < 2.0:
        k ^= 1
        a.rot(t, "head", (-40 + (3 if k else -3), (2 if k else -2), 0), "lin")
        a.rot(t, "chest", (-18 + (1.5 if k else -1.5), 0, (1.5 if k else -1.5)), "lin")
        t += 0.08
    rest(a, 2.6)
    a.rot(2.6, "chest", (10, 0, 0))
    a.rot(2.6, "head", (12, 0, 0))
    a.rot(2.6, "jaw", (8, 0, 0))
    a.ev(0.8, "scream")
    out[a.name] = a

    # weeping into its hands (black tears rain from the ceiling meanwhile)
    a = Anim("animation.weeping.weep", 3.6)
    rest(a, 0)
    wbase = {"chest": (26, 0, 0), "neck": (16, 0, 0), "head": (30, 0, 0)}
    for s, sx in (("l", 1), ("r", -1)):
        arm, fore, err = solve_reach((sx * 7, 78, -34), wbase, s, hand=(-60, 0, 0), x0=(-60, 0, sx * 20, -100))
        arms(a, 0.7, s, arm, fore, (-60, 0, 0), (34, 30))
    a.rot(0.7, "chest", (26, 0, 0))
    a.rot(0.7, "neck", (16, 0, 0))
    a.rot(0.7, "head", (30, 0, 0))
    k, t = 0, 0.8
    while t < 3.0:
        k ^= 1
        a.rot(t, "chest", (26 + (3 if k else -1), 0, 0), "io")
        a.rot(t, "head", (30 + (4 if k else 0), 0, 0), "io")
        t += 0.32
    rest(a, 3.6)
    a.rot(3.6, "chest", (10, 0, 0))
    a.rot(3.6, "head", (12, 0, 0))
    out[a.name] = a

    # grab: reach out with the right hand, close it, lift the victim up in front of its face (hold loops), throw
    a = Anim("animation.weeping.grab", 1.2)
    rest(a, 0)
    ga, gf, err = solve_reach((-6, 26, -78), {"chest": (20, -6, 0)}, "r", hand=(-20, 0, 0), x0=(-70, 0, 0, -10))
    arms(a, 0.5, "r", ga, gf, (-20, 0, 0), (-12, -6), "out")
    a.rot(0.5, "chest", (20, -6, 0), "out")
    arms(a, 0.72, "r", ga, gf, (-20, 0, 0), (70, 80), "snap")
    hbase = {"chest": (2, -10, 0)}
    ha, hf, err2 = solve_reach((-4, 84, -54), hbase, "r", hand=(-10, 0, 0), x0=(-120, 0, 0, -40))
    arms(a, 1.2, "r", ha, hf, (-10, 0, 0), (70, 80))
    a.rot(1.2, "chest", (2, -10, 0))
    a.rot(1.2, "head", (-8, -12, 0))
    out[a.name] = a
    a = Anim("animation.weeping.hold", 1.0, loop=True)
    rest(a, 0, only="l")
    for t, k in ((0, 0), (0.5, 1), (1.0, 0)):
        arms(a, t, "r", (ha[0] - 3 * k, ha[1], ha[2]), hf, (-10, 0, 0), (70, 80))
        a.rot(t, "head", (-8 + 4 * k, -12, 0))
        a.rot(t, "jaw", (14 + 12 * k, 0, 0))
        a.rot(t, "chest", (2, -10, 0))
    rest(a, 1.0, only="l")
    out[a.name] = a
    a = Anim("animation.weeping.throw", 1.0)
    arms(a, 0, "r", ha, hf, (-10, 0, 0), (70, 80))
    ta, tf, err3 = solve_reach((-70, 70, -50), {"chest": (0, 20, 0)}, "r", hand=(0, 0, 0), x0=(-80, 30, 40, -10))
    arms(a, 0.3, "r", ta, tf, (0, 0, 0), (-20, -10), "snap")
    a.rot(0.3, "chest", (0, 20, 0), "snap")
    rest(a, 1.0)
    a.rot(1.0, "chest", (10, 0, 0))
    out[a.name] = a
    print("reach errors (px): grab %.1f hold %.1f throw %.1f" % (err, err2, err3))
    GRAB["hold"] = (-4, 84, -54)

    # stunned (after a heavy blow): slumps forward on its hands, gasping
    a = Anim("animation.weeping.stun", 2.0)
    rest(a, 0)
    for s, sx in (("l", 1), ("r", -1)):
        ax, fx_, hx = solve_cached(2.7, (34, 0, 0), s, 14)
        arms(a, 0.3, s, (ax, sx * 8, -sx * 14), (fx_, 0, 0), (hx, 0, 0), (16, 24), "out")
        arms(a, 1.6, s, (ax, sx * 8, -sx * 14), (fx_, 0, 0), (hx, 0, 0), (16, 24))
    a.rot(0.3, "chest", (34, 0, 0), "out")
    a.rot(0.3, "head", (34, 0, 0), "out")
    a.rot(0.3, "jaw", (26, 0, 0))
    a.rot(1.6, "chest", (32, 0, 0))
    a.rot(1.6, "head", (30, 0, 0))
    rest(a, 2.0)
    a.rot(2.0, "chest", (10, 0, 0))
    a.rot(2.0, "head", (12, 0, 0))
    a.rot(2.0, "jaw", (8, 0, 0))
    out[a.name] = a

    # defeat: it stops crying, reaches up to the light, and sinks back under the mountain
    a = Anim("animation.weeping.defeat", 7.0)
    rest(a, 0)
    a.rot(0, "chest", (10, 0, 0))
    a.rot(1.2, "chest", (-22, 0, 0), "out")
    a.rot(0, "head", (12, 0, 0))
    a.rot(1.2, "head", (-42, 0, 0), "out")
    a.rot(1.2, "jaw", (10, 0, 0))
    for s, sx in (("l", 1), ("r", -1)):
        arms(a, 1.8, s, (-160, 0, -sx * 12), (-8, 0, 0), (0, 0, 0), (-6, -4), "out")
        arms(a, 4.0, s, (-156, 0, -sx * 8), (-12, 0, 0), (0, 0, 0), (10, 10))
        arms(a, 6.4, s, (-176, 0, -sx * 2), (-2, 0, 0), (0, 0, 0), (20, 20))
    a.rot(4.0, "head", (-34, 0, 0))
    a.rot(4.0, "chest", (-14, 0, 0))
    a.pos(3.6, "body", (0, 0, 0))
    a.pos(6.9, "body", (0, -118, 0), "in")
    a.ev(1.2, "release")
    out[a.name] = a
    return out


def impact_points(geo_dict, js):
    """Where each slam's palm lands (blocks: forward, side (+ = its left), up) - the Java side aims the hit there."""
    from georender import bone_matrices
    m = Model(geo_dict)
    res = {}
    for s in ("l", "r"):
        for kind in ("near", "far"):
            nm = "animation.weeping.slam_%s_%s" % (kind, s)
            pose = pose_from_anims([js["animations"][nm]], SLAM_HIT + 0.05)
            mats, _ = bone_matrices(m, pose)
            b = m.bones["hand_" + s]
            c = (mats["hand_" + s] @ np.array([b["pivot"][0], b["pivot"][1] - 12, b["pivot"][2], 1]))[:3]
            # georender: x = -bedrock x, faces -z
            res[kind + "_" + s] = (round(-c[2] / 16, 2), round(-c[0] / 16, 2), round(c[1] / 16, 2))
    return res


def main():
    prev = sys.argv[1] if len(sys.argv) > 1 else None
    g = build()
    g.pack(1.0, width=512)
    d = g.to_dict()
    tex, glow = paint(g, material, seed=3)
    # the glow layer is drawn additively ("eyes"): its colour must be black wherever nothing glows
    ga = np.asarray(glow, np.float64)
    a = ga[..., 3:4] / 255.0
    ga[..., :3] *= a
    ga[..., 3] = np.where(a[..., 0] > 0.02, 255, 0)
    glow = Image.fromarray(np.clip(ga, 0, 255).astype(np.uint8), "RGBA")
    os.makedirs(os.path.join(RES, "geo", "entity"), exist_ok=True)
    g.save(os.path.join(RES, "geo", "entity", "weeping.geo.json"))
    tex.save(os.path.join(RES, "textures", "entity", "weeping.png"))
    glow.save(os.path.join(RES, "textures", "entity", "weeping_glow.png"))
    an = anims()
    js = {"format_version": "1.8.0", "animations": {}}
    for name, a in an.items():
        js["animations"][name] = a.to_json()
    json.dump(js, open(os.path.join(RES, "animations", "entity", "weeping.animation.json"), "w"), separators=(",", ":"))
    print("slam impact points (fwd, side, up):", impact_points(d, js))
    print("weeping: %d bones, %d cubes, texture %dx%d, %d animations" % (len(g.bones), g.cube_count(), g.tw, g.th, len(an)))
    if prev:
        os.makedirs(prev, exist_ok=True)
        m = Model(d)
        tiles = []
        shots = [("idle", 1.0, 0, 6), ("idle", 1.0, 35, 8), ("idle", 1.0, 90, 0), ("slam_far_r", 0.9, 90, 0), ("slam_far_r", 1.2, 90, 0),
                 ("slam_near_l", 1.2, -90, 0), ("slam_near_l", 1.2, 30, 10), ("sweep", 0.9, 0, 20), ("sweep", 1.3, 0, 20), ("scream", 1.2, 20, 4),
                 ("weep", 1.5, 20, 6), ("weep", 1.5, 90, 0), ("grab", 1.2, 40, 4), ("hold", 0.5, 90, 0), ("rise", 2.0, 20, 4),
                 ("stun", 1.0, 20, 4), ("defeat", 2.0, 20, 4), ("defeat", 5.5, 20, 4), ("idle", 1.0, 180, 4), ("throw", 0.3, 20, 4)]
        for nm, t, yaw, pitch in shots:
            pose = pose_from_anims([js["animations"]["animation.weeping." + nm]], t)
            im = render(m, tex, pose, size=360, yaw=yaw, pitch=pitch, emissive=glow, scale=360 / 230, center=(0, 40))
            tiles.append((nm + " %.1f" % t, im))
        W = 5
        sheet = Image.new("RGBA", (360 * W, 360 * ((len(tiles) + W - 1) // W)), (20, 20, 24, 255))
        from PIL import ImageDraw
        dr = ImageDraw.Draw(sheet)
        for i, (lab, im) in enumerate(tiles):
            x, y = (i % W) * 360, (i // W) * 360
            sheet.paste(im, (x, y))
            dr.text((x + 6, y + 6), lab, fill=(255, 255, 255, 255))
        sheet.convert("RGB").save(os.path.join(prev, "weeping_sheet.jpg"), quality=88)
        tex.save(os.path.join(prev, "weeping_tex.png"))


if __name__ == "__main__":
    main()
