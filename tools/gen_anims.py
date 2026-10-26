#!/usr/bin/env python3
"""Ghost animations + kill-cinematic tracks.

Output:
  assets/hollowhours/animations/entity/ghost.animation.json   base Bedrock anims + every new one
  assets/hollowhours/animations/entity/stalker_N.animation.json
  data/hollowhours/kill_tracks.json                            victim / camera / events per track

Per ghost: grab_gN, scream_gN, hold_gN (fitted to that body with IK) and kill_N_1..3 (its own three deaths).
Shared: farewell, yank, lunge, laugh.
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from georender import Model, bone_matrices  # noqa: E402
from animkit import Anim, g2t  # noqa: E402

RES = os.path.join(HERE, "..", "hollow-hours", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "hollowhours")
GEO = os.path.join(ASSETS, "geo", "entity")


# ----------------------------------------------------------------------------- body measurements
def dims(n, model):
    def piv(b):
        return np.array(model.bones[b]["pivot"], float)

    head = model.bones["head"]
    pts = np.array([v for c in head["cubes"] for q in c["quads"] for v in q[0]], float)
    lo, hi = pts.min(0), pts.max(0)
    # the eyes: a little above the middle of the skull, at its front
    eye = np.array([0.0, lo[1] + (hi[1] - lo[1]) * EYE_FRAC.get(n, 0.55), lo[2] + 0.6])
    if n == 5:  # skull + antlers: eyes sit low on the long skull
        eye = np.array([0.0, piv("head")[1] + 4.5, piv("head")[2] - 5.0])
    mats, _ = bone_matrices(model, {})
    eye_rest = (mats["head"] @ np.append(eye, 1))[:3]
    S = (piv("rightArm")[1]) / 16.0
    grip_rest = piv("rightHand") + np.array([0, -4.0, -0.5])
    L = np.linalg.norm(grip_rest - piv("rightArm")) / 16.0
    top = max(v[1] for b in model.bones.values() for c in b["cubes"] for q in c["quads"] for v in q[0]) / 16.0
    E = eye_rest[1] / 16.0
    ef = -eye_rest[2] / 16.0
    return {"n": n, "eye_local": eye, "S": S, "sw": abs(piv("rightArm")[0]) / 16.0, "L": L, "E": E, "ef": ef, "H": top}


EYE_FRAC = {1: 0.58, 2: 0.55, 3: 0.55, 4: 0.6, 5: 0.5}


def face_hold(G, D=0.72, drop=0.12):
    """grip point (track coords) that holds the victim's eyes D blocks in front of the ghost's eyes."""
    vf = G["ef"] + D
    eye_h = G["E"] - drop
    feet = eye_h - 1.62
    return np.array([vf - 0.15, feet + 1.38, 0.0])


def shake(a, bone, t0, t1, amp, hz=10.0, base=(0, 0, 0), e="lin"):
    n = max(2, int((t1 - t0) * hz))
    for i in range(n + 1):
        t = t0 + (t1 - t0) * i / n
        s = 1 if i % 2 == 0 else -1
        k = 1 if i not in (0, n) else 0
        a.rot(t, bone, tuple(base[j] + s * amp[j] * k for j in range(3)), e)


def breathe(a, t0, t1, amp=3.0, period=1.2):
    t = t0
    i = 0
    while t <= t1 + 1e-6:
        a.rot(t, "chest", (amp if i % 2 else 0, 0, 0))
        t += period / 2
        i += 1


# ----------------------------------------------------------------------------- shared phases (fitted per ghost)
def grab(G):
    n = G["n"]
    a = Anim("grab_g%d" % n, 0.95)
    start = np.array([G["ef"] + 1.35, 0.0, 0.0])
    a.victim(0, *start)
    a.pose(0, {"body": (6, 0, 0), "chest": (6, 0, 0), "head": (6, 0, 0), "jaw": (10, 0, 0), "leftArm": (-10, 0, -6)})
    a.reach(0, "r", (0.5, G["S"] - 0.9, -0.15))
    # lunge: hand shoots out to the throat
    a.pose(0.2, {"body": (22, 0, 0), "chest": (18, 0, 0), "head": (16, 0, -8), "jaw": (45, 0, 0), "leftArm": (-35, 0, -20)}, "snap")
    a.pos(0, "root", (0, 0, 0)).pos(0.2, "root", (0, 0, -3), "snap")
    neck = start + np.array([-0.15, 1.38, 0])
    a.reach(0.1, "r", np.array([start[0] - 0.55, 1.05, -0.05]), "out")
    a.reach(0.2, "r", neck, "snap")
    a.hold_victim(0.2, 0.95)
    # lifts you up to its face
    a.pose(0.55, {"body": (8, 0, 0), "chest": (4, 0, 0), "head": (-6, 0, 12), "jaw": (25, 0, 0), "leftArm": (-20, 0, -14)}, "out")
    a.pos(0.55, "root", (0, 0, -1), "out")
    mid = face_hold(G, 1.0, 0.4)
    a.reach(0.55, "r", (mid + neck) / 2 + np.array([0, 0.3, 0]), "out")
    hold = face_hold(G, 0.85)
    a.pose(0.95, {"body": (4, 0, 0), "chest": (6, 0, 0), "head": (2, 0, 18), "jaw": (12, 0, 0), "leftArm": (-12, 0, -10)}, "io")
    a.reach(0.95, "r", hold, "io")
    a.pos(0.95, "root", (0, 0, 0))
    a.ev(0.17, "sound", "ghost.kill.grab", 1.2).ev(0.2, "hit", "", 0.3).ev(0.2, "fx", "static", 0.8).ev(0.2, "fx", "red", 1)
    a.roll(0, 0).roll(0.22, 9, "snap").roll(0.55, -5).roll(0.95, 3)
    return a


def scream(G):
    n = G["n"]
    a = Anim("scream_g%d" % n, 2.5)
    hold = face_hold(G, 0.85)
    close = face_hold(G, 0.55)
    a.reach(0, "r", hold)
    a.pose(0, {"body": (4, 0, 0), "chest": (6, 0, 0), "head": (2, 0, 18), "jaw": (12, 0, 0)})
    a.reach(0, "l", hold + np.array([-0.15, -0.4, 0.35]))
    # it draws back... then the face comes at you, mouth tearing open
    a.pose(0.35, {"head": (-22, 0, 4), "jaw": (6, 0, 0), "chest": (-4, 0, 0), "body": (0, 0, 0)}, "out")
    a.reach(0.35, "r", close + np.array([0.05, 0.05, 0]), "out")
    a.reach(0.35, "l", close + np.array([-0.2, -0.35, 0.32]), "out")
    a.pose(0.48, {"head": (14, 0, -6), "jaw": (78, 0, 0), "chest": (14, 0, 0), "body": (8, 0, 0)}, "snap")
    a.scl(0, "jaw", 1).scl(0.45, "jaw", 1).scl(0.5, "jaw", (1, 1.35, 1.2), "snap").scl(2.1, "jaw", (1, 1.35, 1.2)).scl(2.4, "jaw", 1)
    shake(a, "head", 0.5, 2.05, (9, 14, 16), hz=14, base=(14, 0, -6))
    a.pose(2.3, {"head": (4, 0, 14), "jaw": (22, 0, 0), "chest": (8, 0, 0), "body": (4, 0, 0)}, "io")
    a.reach(2.3, "r", face_hold(G, 0.75), "io")
    a.reach(2.3, "l", face_hold(G, 0.75) + np.array([-0.2, -0.4, 0.34]), "io")
    a.pose(2.5, {"head": (4, 0, 16), "jaw": (18, 0, 0)})
    a.hold_victim(0, 2.5)
    a.ev(0.42, "sound", "ghost.scream.g%d" % n, 2.0).ev(0.46, "fx", "face", 1).ev(0.46, "fx", "static", 1.2)
    a.ev(0.5, "fx", "red", 1).ev(1.3, "fx", "static", 0.6).ev(2.0, "sound", "ghost.growl", 1.2)
    a.roll(0, 3).roll(0.45, -4)
    for i in range(10):
        a.roll(0.5 + i * 0.15, 7 if i % 2 else -7, "lin")
    a.roll(2.3, 2)
    return a


def hold(G):
    """Looping: it holds you at its face and waits for your answer."""
    n = G["n"]
    a = Anim("hold_g%d" % n, 3.0, loop=True)
    h = face_hold(G, 0.78)
    for t, tilt, dy in ((0, 16, 0.0), (1.0, -10, 0.04), (1.6, -12, 0.02), (1.7, 30, 0.0), (2.2, 26, 0.0), (3.0, 16, 0.0)):
        a.rot(t, "head", (6 if t in (1.7, 2.2) else 0, 8 if t >= 1.7 and t < 3 else 0, tilt), "snap" if t == 1.7 else "io")
        a.reach(t, "r", h + np.array([0, dy, 0]), "io")
    a.reach(0, "l", h + np.array([-0.25, -0.5, 0.36]))
    a.reach(3.0, "l", h + np.array([-0.25, -0.5, 0.36]))
    a.pose(0, {"jaw": (14, 0, 0), "body": (4, 0, 0)})
    a.rot(1.65, "jaw", (14, 0, 0)).rot(1.75, "jaw", (40, 0, 0), "snap").rot(2.3, "jaw", (14, 0, 0))
    breathe(a, 0, 3.0, 4, 1.5)
    a.hold_victim(0, 3.0)
    a.roll(0, 2).roll(1.5, -2).roll(3.0, 2)
    return a


# ----------------------------------------------------------------------------- kills
def drop_victim(a, t0, t1, frm, land_fwd):
    """after letting go: the body falls to the floor."""
    a.victim(t0, *frm)
    a.victim(t1, land_fwd, 0.0, frm[2], "in")
    a.victim(a.length, land_fwd, 0.0, frm[2])
    a.prone(t1, a.length)


def kill_1_1(G):
    """The Wailing Dweller: holds you by the throat and stabs its claws into your chest, three times."""
    a = Anim("kill_1_1", 3.8)
    h = face_hold(G, 0.8)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (14, 0, 0), "chest": (6, 0, 0)})
    a.reach(0, "l", h + np.array([-0.4, -0.5, 0.45]))
    # wind up
    a.reach(0.6, "l", np.array([0.1, G["S"] + 0.9, 0.55]), "out")
    a.pose(0.6, {"chest": (-12, -25, 0), "head": (-8, 10, 6), "jaw": (40, 0, 0)}, "out")
    hits = [(0.85, 0.5), (1.45, 0.65), (2.05, 0.85)]
    for i, (t, s) in enumerate(hits):
        chest = h + np.array([0.3, -0.3, 0.05])
        a.reach(t, "l", chest, "snap")
        a.pose(t, {"chest": (20, 20, 0), "head": (18, -6, -10), "jaw": (70, 0, 0)}, "snap")
        a.ev(t, "sound", "ghost.kill.stab", 1.3).ev(t, "hit", "", s).ev(t, "fx", "red", s)
        if i < 2:
            back = np.array([0.2, G["S"] + 0.6, 0.6])
            a.reach(t + 0.35, "l", back, "out")
            a.pose(t + 0.35, {"chest": (-8, -18, 0), "head": (-6, 6, 10), "jaw": (30, 0, 0)}, "out")
            a.reach(t + 0.12, "r", h + np.array([0.05, 0.05, 0]), "out")
            a.reach(t + 0.35, "r", h, "io")
    # rips the claws out sideways, lets go
    a.reach(2.6, "l", h + np.array([-0.6, 0.0, 1.1]), "snap")
    a.pose(2.6, {"chest": (6, -30, 0), "head": (-14, -20, 24), "jaw": (85, 0, 0)}, "snap")
    a.ev(2.6, "sound", "ghost.kill.tear", 1.5).ev(2.6, "hit", "", 1.0).ev(2.6, "fx", "black", 2)
    a.reach(3.0, "r", h + np.array([0.3, 0.3, 0]), "out")
    a.pose(3.4, {"chest": (25, 0, 0), "head": (40, 0, 10), "jaw": (20, 0, 0)})
    a.reach(3.4, "r", h + np.array([0.0, -0.6, 0.3]))
    a.reach(3.8, "l", np.array([0.4, G["S"] - 1.8, 0.6]))
    a.hold_victim(0, 3.0)
    drop_victim(a, 3.0, 3.3, h + np.array([0.15, -1.45, 0]), G["ef"] + 1.0)
    a.ev(3.3, "sound", "ghost.kill.splat", 1.2).ev(3.35, "final")
    a.roll(0, 2).roll(0.85, -12, "snap").roll(1.45, 14, "snap").roll(2.05, -18, "snap").roll(2.6, 25, "snap").roll(3.3, 75, "in")
    return a


def kill_1_2(G):
    """Lifts you high over its head and slams you into the floor. Twice. Then it pins you and screams into your face."""
    a = Anim("kill_1_2", 4.2)
    h = face_hold(G, 0.8)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (14, 0, 0)})
    up = np.array([0.6, G["H"] + 0.6, 0.0])
    floor = np.array([G["ef"] + 1.15, 1.35, 0.0])
    for i, t in enumerate((0.0, 1.3)):
        a.reach(t + 0.45, "r", up, "out")
        a.reach(t + 0.45, "l", up + np.array([-0.2, -0.3, 0.6]), "out")
        a.pose(t + 0.45, {"chest": (-25, 0, 0), "body": (-8, 0, 0), "head": (-30, 0, 0), "jaw": (55, 0, 0)}, "out")
        a.reach(t + 0.75, "r", floor, "in")
        a.reach(t + 0.75, "l", floor + np.array([-0.3, 0.2, 0.5]), "in")
        a.pose(t + 0.75, {"chest": (40, 0, 0), "body": (25, 0, 0), "head": (20, 0, 0), "jaw": (70, 0, 0)}, "in")
        a.ev(t + 0.75, "sound", "ghost.kill.slam", 1.6).ev(t + 0.75, "hit", "", 0.8 + 0.1 * i).ev(t + 0.75, "fx", "black", 1.5)
    a.hold_victim(0, 2.05)
    # pinned flat: one hand on your chest, its face comes down over yours
    vf = G["ef"] + 1.55
    a.victim(2.05, vf, 0.0, 0.0, "in").victim(4.2, vf, 0.0, 0.0)
    a.prone(2.1, 4.2)
    a.reach(2.4, "r", np.array([vf + 0.38, 0.42, -0.08]), "io")
    a.reach(2.4, "l", np.array([vf + 0.1, 0.5, 0.5]), "io")
    a.pose(2.4, {"chest": (38, 0, 0), "body": (30, 0, 0), "neck": (20, 0, 0), "head": (45, 0, 20), "jaw": (30, 0, 0)}, "io")
    a.pose(2.75, {"chest": (42, 0, 0), "body": (32, 0, 0), "neck": (25, 0, 0), "head": (55, 0, -10), "jaw": (88, 0, 0)}, "snap")
    shake(a, "head", 2.8, 3.5, (10, 12, 14), hz=16, base=(55, 0, -10))
    a.ev(2.75, "sound", "ghost.scream.g1", 2.0).ev(2.75, "fx", "face", 1).ev(2.8, "fx", "static", 1.2)
    a.reach(3.6, "r", np.array([vf + 0.38, 0.25, -0.08]), "snap")
    a.ev(3.6, "sound", "ghost.kill.crunch", 1.5).ev(3.6, "hit", "", 1.0).ev(3.62, "final")
    a.roll(0, 2).roll(0.45, -20).roll(0.75, 60, "in").roll(1.75, -25).roll(2.05, 70, "in").roll(2.4, 10).roll(3.6, 25, "snap")
    return a


def kill_1_3(G):
    """Pulls your face to its weeping face, holds your head in both hands... and turns it."""
    a = Anim("kill_1_3", 3.4)
    h = face_hold(G, 0.8)
    near = face_hold(G, 0.5)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (14, 0, 0)})
    a.reach(0, "l", h + np.array([-0.3, -0.4, 0.4]))
    # both hands on the sides of your head
    hr = near + np.array([0.12, 0.3, -0.18])
    hl = near + np.array([0.12, 0.3, 0.18])
    a.reach(0.6, "r", hr, "io").reach(0.6, "l", hl, "io")
    a.pose(0.6, {"head": (10, 0, -14), "jaw": (30, 0, 0), "chest": (8, 0, 0)})
    a.pose(1.6, {"head": (14, 0, 14), "jaw": (45, 0, 0), "chest": (10, 0, 0)})
    a.ev(0.7, "sound", "ghost.voice.g1.l", 1.4).ev(1.6, "sound", "ghost.breath", 1.5)
    # the turn
    a.reach(2.2, "r", hr + np.array([0, 0.25, 0.1]), "io").reach(2.2, "l", hl + np.array([0, -0.25, -0.1]), "io")
    a.reach(2.45, "r", hr + np.array([0, 0.45, 0.25]), "snap").reach(2.45, "l", hl + np.array([0, -0.45, -0.25]), "snap")
    a.pose(2.45, {"head": (20, 0, -30), "jaw": (70, 0, 0)}, "snap")
    a.ev(2.45, "sound", "ghost.kill.neck", 1.8).ev(2.45, "hit", "", 1.0).ev(2.47, "fx", "black", 3).ev(2.5, "final")
    a.reach(3.4, "r", hr + np.array([0, 0.3, 0.2])).reach(3.4, "l", hl + np.array([0, -0.4, -0.2]))
    a.hold_victim(0, 0.6)
    a.hold_victim(0.6, 3.4, "rightHand", offset=(0.25, -1.45 - 0.15, 0.18))
    a.roll(0, 2).roll(0.6, -4).roll(2.2, -20).roll(2.45, 140, "snap").roll(3.4, 150)
    return a


def kill_2_1(G):
    """The Sunken Widow pushes you down to the floor and holds you under. Her hair falls over your face."""
    a = Anim("kill_2_1", 4.2)
    h = face_hold(G, 0.8)
    vf = G["ef"] + 1.35
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (14, 0, 0)})
    a.hold_victim(0, 0.55)
    a.victim(0.7, vf, 0.0, 0.0, "in").victim(4.2, vf, 0.0, 0.0)
    a.prone(0.7, 4.2)
    chest = np.array([vf + 0.38, 0.4, 0.0])
    a.reach(0.7, "r", chest, "in")
    a.reach(0.7, "l", chest + np.array([-0.15, 0.05, 0.35]), "in")
    a.pose(0.7, {"body": (35, 0, 0), "chest": (30, 0, 0), "neck": (15, 0, 0), "head": (40, 0, 0), "jaw": (30, 0, 0), "hair": (35, 0, 0)}, "in")
    a.ev(0.7, "sound", "ghost.kill.slam", 1.2).ev(0.7, "hit", "", 0.5).ev(0.75, "fx", "black", 1)
    for i in range(5):
        t = 1.1 + i * 0.5
        a.reach(t, "r", chest - np.array([0, 0.06 * (i % 2), 0]), "io")
        a.pose(t, {"body": (38, 0, 0), "chest": (34, 0, 4 if i % 2 else -4), "neck": (18, 0, 0), "head": (50, 0, 10 if i % 2 else -10),
                   "jaw": (55 if i % 2 else 25, 0, 0), "hair": (45, 0, 0)}, "io")
        a.ev(t, "sound", "ghost.kill.drown", 1.2).ev(t, "hit", "", 0.35).ev(t, "fx", "black", 1.2)
    a.ev(3.6, "sound", "ghost.kill.choke", 1.4).ev(3.7, "fx", "black", 6).ev(3.75, "final")
    a.roll(0, 2).roll(0.7, 15, "in").roll(2.0, 22).roll(3.5, 30)
    return a


def kill_2_2(G):
    """A wedding embrace: she pulls you into her chest and doesn't stop squeezing."""
    a = Anim("kill_2_2", 3.8)
    h = face_hold(G, 0.8)
    chest = face_hold(G, 0.62, 0.55)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18)})
    back_r = chest + np.array([0.35, 0.05, -0.15])
    back_l = chest + np.array([0.35, -0.1, 0.15])
    a.reach(0.6, "r", back_r, "io").reach(0.6, "l", back_l, "io")
    a.pose(0.6, {"head": (20, -55, 20), "chest": (8, 0, 0), "jaw": (10, 0, 0)}, "io")
    a.ev(0.6, "sound", "ghost.voice.g2.m", 1.3)
    for i, t in enumerate((1.3, 2.0, 2.6)):
        a.reach(t, "r", back_r + np.array([-0.08, 0, 0.06]), "snap").reach(t, "l", back_l + np.array([-0.08, 0, -0.06]), "snap")
        a.pose(t, {"chest": (12, 0, 0), "head": (24, -55, 26), "jaw": (30, 0, 0)}, "snap")
        a.reach(t + 0.3, "r", back_r, "io").reach(t + 0.3, "l", back_l, "io")
        a.ev(t, "sound", "ghost.kill.crunch", 1.2).ev(t, "hit", "", 0.5 + 0.2 * i).ev(t, "fx", "red", 0.8)
    a.pose(3.1, {"head": (20, -10, 10), "jaw": (70, 0, 0)}, "snap")
    a.ev(3.1, "sound", "ghost.kill.neck", 1.5).ev(3.1, "hit", "", 1.0).ev(3.12, "fx", "black", 3).ev(3.15, "final")
    a.hold_victim(0, 0.6)
    a.hold_victim(0.6, 3.8, "rightHand", offset=(-0.15, -1.25, 0.15))
    a.roll(0, 2).roll(0.6, -8).roll(1.3, 6, "snap").roll(2.0, -8, "snap").roll(2.6, 10, "snap").roll(3.1, 35, "snap")
    return a


def kill_2_3(G):
    """Her hair wraps your throat; she lifts you to the ceiling, then lets you fall."""
    a = Anim("kill_2_3", 4.0)
    h = face_hold(G, 0.8)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "hair": (0, 0, 0)})
    a.pose(0.5, {"head": (35, 0, 0), "hair": (45, 0, 0), "chest": (10, 0, 0), "jaw": (20, 0, 0)}, "io")
    a.reach(0.5, "r", h + np.array([-0.1, -0.2, 0]))
    a.ev(0.5, "sound", "ghost.kill.choke", 1.4).ev(0.6, "fx", "black", 1)
    top = np.array([G["ef"] + 0.45, G["H"] + 0.7, 0.0])
    a.reach(1.6, "r", top, "io").reach(1.6, "l", top + np.array([0, -0.3, 0.3]), "io")
    a.pose(1.6, {"head": (-40, 0, 0), "hair": (-30, 0, 0), "chest": (-20, 0, 0), "body": (-8, 0, 0), "jaw": (40, 0, 0)}, "io")
    for i, t in enumerate((1.9, 2.3, 2.7)):
        a.reach(t, "r", top + np.array([0, 0.12 if i % 2 else -0.1, 0]), "lin")
        a.ev(t, "sound", "ghost.kill.choke", 1.0).ev(t, "hit", "", 0.4).ev(t, "fx", "black", 0.8)
    a.pose(2.9, {"head": (-30, 0, 20), "jaw": (80, 0, 0)}, "snap")
    a.ev(2.9, "sound", "ghost.scream.g2", 1.8).ev(2.95, "fx", "face", 1)
    a.hold_victim(0, 3.2)
    top_v = top + np.array([0.15, -1.45, 0])
    drop_victim(a, 3.2, 3.55, top_v, G["ef"] + 1.1)
    a.reach(3.6, "r", np.array([0.5, G["S"] - 1.5, -0.3]), "out")
    a.ev(3.55, "sound", "ghost.kill.splat", 1.6).ev(3.55, "hit", "", 1.0).ev(3.56, "fx", "black", 4).ev(3.6, "final")
    a.roll(0, 2).roll(1.6, -6).roll(2.9, 10).roll(3.2, 30).roll(3.55, 95, "in")
    return a


def kill_3_1(G):
    """The Ashen Screamer holds you with one hand and beats your face with the other."""
    a = Anim("kill_3_1", 3.8)
    h = face_hold(G, 0.85)
    a.reach(0, "l", h).pose(0, {"head": (2, 0, -12), "jaw": (30, 0, 0)})
    a.reach(0, "r", h + np.array([-0.5, -0.3, -0.5]))
    face = h + np.array([0.2, 0.35, 0])
    for i in range(5):
        t = 0.55 + i * 0.5
        a.reach(t - 0.25, "r", np.array([0.0, G["S"] + 0.4, -0.65]), "out")
        a.pose(t - 0.25, {"chest": (-6, 30, 0), "head": (-6, -14, -10), "jaw": (50, 0, 0)}, "out")
        a.reach(t, "r", face + np.array([0, -0.05 * i, 0]), "snap")
        a.pose(t, {"chest": (16, -25, 0), "head": (10, 10, 8), "jaw": (75, 0, 0)}, "snap")
        s = 0.4 + 0.12 * i
        a.ev(t, "sound", "ghost.kill.punch", 1.4).ev(t, "hit", "", s).ev(t, "fx", "red", s)
        if i >= 3:
            a.ev(t, "fx", "black", 1.5)
    a.reach(3.2, "r", face, "snap").pose(3.2, {"chest": (25, -30, 0), "head": (20, 10, 10), "jaw": (88, 0, 0)}, "snap")
    a.ev(3.2, "sound", "ghost.kill.crunch", 1.8).ev(3.2, "hit", "", 1.0).ev(3.22, "fx", "black", 4).ev(3.25, "final")
    a.reach(3.8, "r", face + np.array([0.1, -0.2, 0.0]))
    a.hold_victim(0, 3.8, "leftHand")
    for i in range(5):
        t = 0.55 + i * 0.5
        a.roll(t - 0.1, 0).roll(t, (-1) ** i * (12 + 6 * i), "snap")
    a.roll(3.2, 50, "snap")
    return a


def kill_3_2(G):
    """A burning embrace: it pulls you into its cracked chest and the heat comes out of it."""
    a = Anim("kill_3_2", 3.9)
    h = face_hold(G, 0.85)
    chest = face_hold(G, 0.42, 0.5)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (20, 0, 0)})
    br = chest + np.array([0.35, 0.05, -0.18])
    bl = chest + np.array([0.35, -0.1, 0.18])
    a.reach(0.5, "r", br, "io").reach(0.5, "l", bl, "io")
    a.pose(0.5, {"head": (-25, 0, 0), "chest": (6, 0, 0), "jaw": (80, 0, 0)}, "io")
    a.ev(0.5, "sound", "ghost.scream.g3", 2.0).ev(0.55, "fx", "red", 1.5)
    shake(a, "head", 0.6, 3.0, (8, 10, 12), hz=12, base=(-25, 0, 0))
    for i, t in enumerate((0.9, 1.5, 2.1, 2.7)):
        a.ev(t, "sound", "ghost.kill.burn", 1.3).ev(t, "hit", "", 0.4 + 0.1 * i).ev(t, "fx", "red", 1.2)
    a.ev(3.2, "sound", "ghost.kill.crunch", 1.6).ev(3.2, "hit", "", 1.0).ev(3.22, "fx", "black", 4).ev(3.25, "final")
    a.reach(3.2, "r", br + np.array([-0.1, 0, 0.08]), "snap").reach(3.2, "l", bl + np.array([-0.1, 0, -0.08]), "snap")
    a.pose(3.2, {"head": (-6, 0, 0), "jaw": (60, 0, 0), "chest": (4, 0, 0)}, "snap")
    a.hold_victim(0, 0.5)
    a.hold_victim(0.5, 3.9, "rightHand", offset=(-0.15, -1.25, 0.18))
    a.roll(0, 2).roll(0.5, -5)
    for i in range(8):
        a.roll(0.8 + i * 0.3, 6 if i % 2 else -6, "lin")
    a.roll(3.2, 30, "snap")
    return a


def kill_3_3(G):
    """Both burning hands on your head. It lifts you and squeezes."""
    a = Anim("kill_3_3", 3.5)
    h = face_hold(G, 0.8)
    near = face_hold(G, 0.6)
    hr = near + np.array([0.12, 0.3, -0.2])
    hl = near + np.array([0.12, 0.3, 0.2])
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (20, 0, 0)})
    a.reach(0.5, "r", hr, "io").reach(0.5, "l", hl, "io")
    a.pose(0.5, {"head": (-10, 0, 0), "jaw": (40, 0, 0), "chest": (-4, 0, 0)})
    lift = np.array([0, 0.5, 0])
    a.reach(1.3, "r", hr + lift, "io").reach(1.3, "l", hl + lift, "io")
    a.pose(1.3, {"head": (-30, 0, 0), "jaw": (70, 0, 0), "chest": (-14, 0, 0)}, "io")
    shake(a, "chest", 1.4, 2.5, (4, 6, 4), hz=12, base=(-14, 0, 0))
    for i, t in enumerate((1.6, 2.0, 2.4)):
        sq = np.array([0, 0, 0.04 * (i + 1)])
        a.reach(t, "r", hr + lift + sq, "snap").reach(t, "l", hl + lift - sq, "snap")
        a.ev(t, "sound", "ghost.kill.crunch", 1.0 + 0.2 * i).ev(t, "hit", "", 0.4 + 0.2 * i).ev(t, "fx", "red", 0.9)
    a.ev(2.8, "sound", "ghost.kill.splat", 1.8).ev(2.8, "hit", "", 1.0).ev(2.82, "fx", "black", 5).ev(2.85, "final")
    a.reach(2.8, "r", hr + lift + np.array([0, 0, 0.2]), "snap").reach(2.8, "l", hl + lift - np.array([0, 0, 0.2]), "snap")
    a.pose(2.8, {"head": (-10, 0, 10), "jaw": (88, 0, 0)}, "snap")
    a.hold_victim(0, 0.5)
    a.hold_victim(0.5, 3.5, "rightHand", offset=(0.25, -1.6, 0.2))
    a.roll(0, 2).roll(1.3, -6).roll(2.8, 25, "snap")
    return a


def kill_4_1(G):
    """The Smiling Void: all four hands take hold of you... and pull in different directions."""
    a = Anim("kill_4_1", 3.8)
    h = face_hold(G, 0.85)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (10, 0, 0)})
    a.pose(0.6, {"extraArmR": (-70, 20, 0), "extraArmL": (-70, -20, 0), "head": (6, 0, 35)}, "io")
    a.reach(0.6, "l", h + np.array([0.05, -0.75, 0.35]), "io")
    a.ev(0.6, "sound", "ghost.voice.g4.s", 1.4)
    a.pose(1.3, {"head": (6, 0, -35)}, "io").ev(1.3, "sound", "ghost.betray.laugh", 1.2)
    # pull
    a.pose(2.0, {"extraArmR": (-60, 55, 20), "extraArmL": (-60, -55, -20), "head": (0, 0, 0), "jaw": (40, 0, 0)}, "in")
    a.reach(2.0, "l", h + np.array([0.0, -0.9, 0.75]), "in")
    a.ev(2.0, "sound", "ghost.kill.tear", 1.4).ev(2.0, "hit", "", 0.6).ev(2.0, "fx", "red", 1)
    a.pose(2.6, {"extraArmR": (-55, 80, 40), "extraArmL": (-55, -80, -40), "head": (-10, 0, 0), "jaw": (70, 0, 0)}, "snap")
    a.reach(2.6, "l", h + np.array([-0.1, -1.1, 1.1]), "snap")
    a.ev(2.6, "sound", "ghost.kill.tear", 1.8).ev(2.6, "hit", "", 1.0).ev(2.62, "fx", "black", 4).ev(2.65, "final")
    a.hold_victim(0, 3.8)
    a.roll(0, 2).roll(0.6, -6).roll(1.3, 8).roll(2.0, -10, "in").roll(2.6, 30, "snap")
    return a


def kill_4_2(G):
    """Its head tips sideways, the grin keeps opening... and it bites."""
    a = Anim("kill_4_2", 3.5)
    h = face_hold(G, 1.05)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (10, 0, 0)})
    a.pose(0.9, {"head": (0, 0, 88), "jaw": (20, 0, 0)}, "io")
    a.ev(0.9, "sound", "ghost.voice.g4.m", 1.3)
    a.pose(1.8, {"head": (-10, 0, 90), "jaw": (90, 0, 0)}, "in")
    a.scl(1.2, "jaw", 1).scl(1.8, "jaw", (1.2, 1.6, 1.4), "in").scl(2.4, "jaw", (1.2, 1.6, 1.4))
    a.ev(1.8, "sound", "ghost.scream.g4", 1.6)
    a.reach(1.8, "r", h + np.array([0.0, 0.05, 0.0]))
    # bite
    a.reach(2.25, "r", face_hold(G, 0.25) + np.array([0, -0.1, 0]), "snap")
    a.pose(2.25, {"head": (25, 0, 80), "jaw": (5, 0, 0), "chest": (20, 0, 0)}, "snap")
    a.ev(1.97, "fx", "black", 8).ev(2.25, "sound", "ghost.kill.bite", 1.8).ev(2.25, "hit", "", 1.0).ev(2.26, "fx", "black", 6).ev(2.3, "final")
    a.hold_victim(0, 3.5)
    a.roll(0, 2).roll(0.9, -30).roll(1.8, -40).roll(2.25, -10, "snap")
    return a


def kill_4_3(G):
    """It turns you slowly upside down so you can see its smile the right way up."""
    a = Anim("kill_4_3", 3.6)
    h = face_hold(G, 0.8)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (10, 0, 0)})
    a.reach(0.4, "l", h + np.array([0.05, -0.75, 0.3]), "io")
    a.pose(2.4, {"head": (0, 0, -170), "jaw": (30, 0, 0)}, "io")
    a.ev(0.5, "sound", "ghost.voice.g4.l", 1.3).ev(1.8, "sound", "ghost.betray.laugh", 1.3)
    a.pose(2.8, {"head": (0, 0, -175), "jaw": (85, 0, 0)}, "snap")
    a.ev(2.8, "sound", "ghost.kill.neck", 1.8).ev(2.8, "hit", "", 1.0).ev(2.82, "fx", "black", 4).ev(2.85, "final")
    a.hold_victim(0, 3.6)
    a.roll(0, 2).roll(2.4, 178, "io").roll(2.8, 220, "snap")
    return a


def kill_5_1(G):
    """The Antlered Hunger eats. It doesn't wait for you to die first."""
    a = Anim("kill_5_1", 4.0)
    h = face_hold(G, 0.75)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (10, 0, 0)})
    a.reach(0.3, "l", h + np.array([-0.05, -0.4, 0.35]), "io")
    for i, t in enumerate((0.8, 1.5, 2.2, 2.9)):
        a.pose(t - 0.35, {"head": (-20, 0, 15 if i % 2 else -15), "jaw": (70, 0, 0), "neck": (-10, 0, 0)}, "out")
        a.pose(t, {"head": (35, 0, -10 if i % 2 else 10), "jaw": (0, 0, 0), "neck": (25, 0, 0)}, "snap")
        a.reach(t, "r", h + np.array([-0.12, -0.05, 0]), "snap")
        a.reach(t + 0.25, "r", h, "out")
        a.ev(t, "sound", "ghost.kill.bite", 1.5).ev(t, "hit", "", 0.4 + 0.15 * i).ev(t, "fx", "red", 1)
    a.ev(3.4, "sound", "ghost.kill.crunch", 1.8).ev(3.4, "hit", "", 1.0).ev(3.42, "fx", "black", 5).ev(3.45, "final")
    a.pose(3.4, {"head": (40, 0, 0), "jaw": (0, 0, 0), "neck": (30, 0, 0)}, "snap")
    a.pose(4.0, {"head": (20, 0, 10), "jaw": (30, 0, 0), "neck": (10, 0, 0)})
    a.hold_victim(0, 4.0)
    a.roll(0, 2)
    for i, t in enumerate((0.8, 1.5, 2.2, 2.9)):
        a.roll(t, 14 if i % 2 else -14, "snap").roll(t + 0.3, 0)
    a.roll(3.4, 40, "snap")
    return a


def kill_5_2(G):
    """It lowers its antlers and runs you through. Then it lifts its head with you on them."""
    a = Anim("kill_5_2", 3.9)
    h = face_hold(G, 1.0)
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (10, 0, 0)})
    # throws you back a step
    a.reach(0.35, "r", h + np.array([0.4, -0.3, 0]), "snap")
    a.pose(0.6, {"head": (75, 0, 0), "neck": (30, 0, 0), "chest": (35, 0, 0), "body": (25, 0, 0)}, "io")
    a.reach(0.6, "r", np.array([0.3, G["S"] - 1.2, -0.5]))
    a.ev(0.6, "sound", "ghost.growl", 1.8)
    # charge
    a.pos(0.6, "root", (0, 0, 0)).pos(0.95, "root", (0, 0, -14), "snap")
    a.ev(0.95, "sound", "ghost.kill.stab", 1.8).ev(0.95, "hit", "", 0.9).ev(0.96, "fx", "black", 1.5)
    a.pose(1.7, {"head": (-20, 0, 0), "neck": (-10, 0, 0), "chest": (-10, 0, 0), "body": (-5, 0, 0)}, "io")
    shake(a, "head", 1.8, 2.8, (10, 18, 12), hz=10, base=(-20, 0, 0))
    a.ev(2.0, "sound", "ghost.kill.tear", 1.4).ev(2.0, "hit", "", 0.6).ev(2.5, "hit", "", 0.6).ev(2.5, "fx", "red", 1)
    a.ev(2.9, "sound", "ghost.kill.splat", 1.8).ev(2.9, "hit", "", 1.0).ev(2.92, "fx", "black", 4).ev(2.95, "final")
    a.pose(3.2, {"head": (40, 30, 30)}, "snap")
    vstart = h + np.array([0.15, -1.45, 0])
    a.victim(0, *vstart)
    a.victim(0.35, *(vstart + np.array([0.4, -0.3, 0])), "snap")
    a.victim(0.6, *(np.array([vstart[0] + 0.4, 0.0, 0])), "in")
    # on the antlers: follows the head
    a.hold_victim(0.95, 2.9, "head", grip=None, offset=(0.3, -0.9, 0.0))
    a.victim(2.9, G["ef"] + 1.0, G["H"] - 1.6, 0)
    a.victim(3.25, G["ef"] + 1.6, 0.0, 0.4, "in")
    a.roll(0, 2).roll(0.95, -30, "snap").roll(1.7, 20).roll(2.9, 60, "in").roll(3.25, 95, "in")
    return a


def kill_5_3(G):
    """It throws you to the floor and stamps on you."""
    a = Anim("kill_5_3", 3.7)
    h = face_hold(G, 0.8)
    vf = 0.95
    a.reach(0, "r", h).pose(0, {"head": (2, 0, 18), "jaw": (10, 0, 0)})
    a.reach(0.45, "r", np.array([vf + 0.1, 1.2, 0.2]), "in")
    a.pose(0.45, {"body": (25, 0, 0), "chest": (25, 0, 0), "head": (40, 0, 0)}, "in")
    a.ev(0.45, "sound", "ghost.kill.slam", 1.6).ev(0.45, "hit", "", 0.6).ev(0.46, "fx", "black", 1.5)
    a.reach(0.8, "r", np.array([0.3, G["S"] - 1.4, -0.5]), "out")
    a.pose(0.9, {"body": (5, 0, 0), "chest": (10, 0, 0), "head": (55, 0, 0), "jaw": (40, 0, 0)}, "io")
    for i, t in enumerate((1.5, 2.3, 3.0)):
        a.pose(t - 0.4, {"rightLeg": (-80, 0, 0), "rightShin": (75, 0, 0), "rightFoot": (-15, 0, 0), "body": (-6, 0, 0), "head": (50, 0, 10)}, "out")
        a.pose(t, {"rightLeg": (-38, 0, 0), "rightShin": (5, 0, 0), "rightFoot": (0, 0, 0), "body": (10, 0, 0), "head": (65, 0, -10), "jaw": (70, 0, 0)}, "in")
        a.ev(t, "sound", "ghost.kill.stomp", 1.8).ev(t, "hit", "", 0.5 + 0.25 * i).ev(t, "fx", "black", 1 + i)
    a.ev(3.02, "final")
    a.hold_victim(0, 0.45)
    a.victim(0.55, vf, 0.0, 0.0, "in").victim(3.7, vf, 0.0, 0.0)
    a.prone(0.55, 3.7)
    a.look(1.0, 3.7, "rightFoot")
    a.roll(0, 2).roll(0.45, 40, "in").roll(0.9, 5)
    for i, t in enumerate((1.5, 2.3, 3.0)):
        a.roll(t, (-1) ** i * 14, "snap").roll(t + 0.3, 0)
    return a


KILLS = {1: [kill_1_1, kill_1_2, kill_1_3], 2: [kill_2_1, kill_2_2, kill_2_3], 3: [kill_3_1, kill_3_2, kill_3_3],
         4: [kill_4_1, kill_4_2, kill_4_3], 5: [kill_5_1, kill_5_2, kill_5_3]}


# ----------------------------------------------------------------------------- shared non-kill animations
def farewell():
    a = Anim("farewell", 2.6)
    a.pose(0, {"head": (0, 0, 0), "jaw": (10, 0, 0)})
    a.pose(0.6, {"head": (-40, 0, 0), "jaw": (60, 0, 0), "rightArm": (-160, 0, 20), "leftArm": (-160, 0, -20), "chest": (-15, 0, 0)}, "out")
    a.pose(2.6, {"head": (-50, 0, 15), "jaw": (70, 0, 0), "rightArm": (-175, 0, 10), "leftArm": (-175, 0, -10), "chest": (-20, 0, 0)}, "io")
    return a


def yank():
    a = Anim("yank", 1.4)
    a.pose(0, {"chest": (10, 0, 0), "head": (10, 0, 0), "rightArm": (-40, 0, 0), "leftArm": (-40, 0, 0)})
    a.pose(0.35, {"chest": (25, 0, 0), "head": (20, 0, 0), "rightArm": (-95, -10, 0), "leftArm": (-95, 10, 0), "rightForearm": (-10, 0, 0), "leftForearm": (-10, 0, 0)}, "out")
    a.pose(0.55, {"chest": (-10, 0, 0), "body": (-10, 0, 0), "head": (-10, 0, 0), "jaw": (70, 0, 0), "rightArm": (-60, -30, 20), "leftArm": (-60, 30, -20), "rightForearm": (-60, 0, 0), "leftForearm": (-60, 0, 0)}, "snap")
    a.pose(1.4, {"chest": (5, 0, 0), "body": (0, 0, 0), "head": (15, 0, 10), "jaw": (55, 0, 0), "rightArm": (-70, -20, 10), "leftArm": (-70, 20, -10), "rightForearm": (-40, 0, 0), "leftForearm": (-40, 0, 0)})
    return a


def lunge():
    a = Anim("lunge", 0.9)
    a.pose(0, {"body": (0, 0, 0), "head": (0, 0, 0)})
    a.pose(0.15, {"body": (25, 0, 0), "chest": (20, 0, 0), "head": (-15, 0, 0), "jaw": (85, 0, 0), "rightArm": (-120, 0, 15), "leftArm": (-120, 0, -15)}, "snap")
    a.pos(0, "root", (0, 0, 0)).pos(0.15, "root", (0, 0, -10), "snap").pos(0.9, "root", (0, 0, -10))
    a.pose(0.9, {"body": (30, 0, 0), "chest": (25, 0, 0), "head": (-10, 0, 8), "jaw": (80, 0, 0), "rightArm": (-110, 0, 20), "leftArm": (-110, 0, -20)})
    return a


def laugh():
    a = Anim("laugh", 1.2, loop=True)
    for i in range(7):
        t = i * 0.2
        up = i % 2 == 0
        a.pose(t, {"head": (-35 if up else -25, 0, 6 if up else -6), "jaw": (60 if up else 20, 0, 0), "chest": (-12 if up else -6, 0, 0),
                   "rightArm": (-10, 0, 25 if up else 18), "leftArm": (-10, 0, -25 if up else -18)}, "lin")
    return a


# ----------------------------------------------------------------------------- stalkers (lunge / run per body)
def stalker_anims(n, base):
    out = {}
    idle_key = "animation.stalker_%d.idle" % n
    out["animation.stalker.idle"] = base[idle_key]
    arms = {1: ("armR", "armL", "foreR", "foreL"), 2: ("armR", "armL", None, None), 3: ("armR", "armL", "foreR", "foreL")}[n]
    legs = {1: ("legR", "legL", "shinR", "shinL"), 2: ("legR", "legL", None, None), 3: (None, None, None, None)}[n]
    torso = {1: "torso", 2: "body", 3: "torso"}[n]
    a = Anim("lunge", 0.8)
    a.rot(0, "head", (0, 0, 0)).rot(0.12, "head", (-20, 0, 15), "snap").rot(0.8, "head", (-15, 0, 20))
    if "jaw" in BONES_ST[n]:
        a.rot(0, "jaw", (0, 0, 0)).rot(0.12, "jaw", (75, 0, 0), "snap").rot(0.8, "jaw", (70, 0, 0))
    a.rot(0, arms[0], (0, 0, 0)).rot(0.12, arms[0], (-150, 0, 20), "snap").rot(0.8, arms[0], (-140, 0, 25))
    a.rot(0, arms[1], (0, 0, 0)).rot(0.12, arms[1], (-150, 0, -20), "snap").rot(0.8, arms[1], (-140, 0, -25))
    a.rot(0, torso, (0, 0, 0)).rot(0.12, torso, (25, 0, 0), "snap").rot(0.8, torso, (20, 0, 0))
    a.pos(0, "root", (0, 0, 0)).pos(0.12, "root", (0, 0, -8), "snap").pos(0.8, "root", (0, 0, -8))
    out["animation.stalker.lunge"] = a.to_json()
    r = Anim("run", 0.5, loop=True)
    for i, t in enumerate((0, 0.125, 0.25, 0.375, 0.5)):
        s = (-1) ** i if i < 4 else 1
        ph = [1, 0, -1, 0, 1][i]
        r.rot(t, arms[0], (-60 * ph - 20, 0, 15), "lin").rot(t, arms[1], (60 * ph - 20, 0, -15), "lin")
        if legs[0]:
            r.rot(t, legs[0], (50 * ph, 0, 0), "lin").rot(t, legs[1], (-50 * ph, 0, 0), "lin")
        r.rot(t, torso, (25, 0, 6 * ph), "lin")
        r.rot(t, "head", (-25, 0, 10 * ph), "lin")
    out["animation.stalker.run"] = r.to_json()
    return out


BONES_ST = {}


def report(a):
    if a.clips:
        print("   ! %s: camera inside the ghost at %s" % (a.name, a.clips[:8]))


# ----------------------------------------------------------------------------- main
def main():
    base = json.load(open(os.path.join(HERE, "data", "ghost_base.animation.json")))
    anims = dict(base["animations"])
    tracks = []
    for n in range(1, 6):
        model = Model(os.path.join(GEO, "ghost_%d.geo.json" % n))
        G = dims(n, model)
        print("ghost %d: S=%.2f E=%.2f ef=%.2f L=%.2f H=%.2f" % (n, G["S"], G["E"], G["ef"], G["L"], G["H"]))
        for fn in (grab, scream, hold):
            a = fn(G)
            a.solve_ik(model)
            anims["animation.ghost." + a.name] = a.to_json()
            tracks.append(a.track(model, G["eye_local"]))
            report(a)
        for k, fn in enumerate(KILLS[n]):
            a = fn(G)
            a.solve_ik(model)
            anims["animation.ghost." + a.name] = a.to_json()
            tracks.append(a.track(model, G["eye_local"]))
            report(a)
    for fn in (farewell, yank, lunge, laugh):
        a = fn()
        anims["animation.ghost." + a.name] = a.to_json()
    out = {"format_version": "1.8.0", "animations": anims}
    p = os.path.join(ASSETS, "animations", "entity", "ghost.animation.json")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        json.dump(out, f, separators=(",", ":"))
    tp = os.path.join(RES, "data", "hollowhours", "kill_tracks.json")
    os.makedirs(os.path.dirname(tp), exist_ok=True)
    with open(tp, "w") as f:
        json.dump({"tracks": tracks}, f, separators=(",", ":"))
    # stalkers
    sb = json.load(open(os.path.join(HERE, "data", "stalker_base.animation.json")))["animations"]
    for n in range(1, 4):
        m = json.load(open(os.path.join(GEO, "stalker_%d.geo.json" % n)))
        BONES_ST[n] = {b["name"] for b in m["minecraft:geometry"][0]["bones"]}
        sa = stalker_anims(n, sb)
        with open(os.path.join(ASSETS, "animations", "entity", "stalker_%d.animation.json" % n), "w") as f:
            json.dump({"format_version": "1.8.0", "animations": sa}, f, separators=(",", ":"))
    print("animations:", len(anims), "tracks:", len(tracks), "size %.0f KB" % (os.path.getsize(p) / 1024))


if __name__ == "__main__":
    main()
