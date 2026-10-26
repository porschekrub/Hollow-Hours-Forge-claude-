#!/usr/bin/env python3
"""Animation authoring kit: keyframes with easing, arm IK solved on the real model, baking to Bedrock/GeckoLib
animation JSON, and kill-cinematic tracks (victim position, camera target, roll, events) measured on the posed
model so the victim really hangs from the ghost's hand and the camera really looks into its face.

Track space: blocks, relative to the ghost's feet: fwd (+ = in front of it), up, side (+ = its left).
Model (georender) space: pixels, x = -bedrock x, y up, z = bedrock z, the model faces -z.
"""
import math

import numpy as np
from scipy.optimize import minimize

from georender import bone_matrices


# ----------------------------------------------------------------------------- easing
def ease(kind, f):
    f = min(1.0, max(0.0, f))
    if kind == "lin":
        return f
    if kind == "in":
        return f * f * f
    if kind == "out":
        return 1 - (1 - f) ** 3
    if kind == "snap":
        return 1 - (1 - f) ** 5
    if kind == "back":
        c1, c3 = 1.9, 2.9
        return 1 + c3 * (f - 1) ** 3 + c1 * (f - 1) ** 2
    if kind == "step":
        return 0.0 if f < 1 else 1.0
    if kind == "elastic":
        if f in (0, 1):
            return f
        return 2 ** (-10 * f) * math.sin((f * 10 - 0.75) * (2 * math.pi) / 3) + 1
    # "io": smooth in-out
    return f * f * (3 - 2 * f)


def g2t(p):
    """model-space point (px) -> track coords (blocks)."""
    return np.array([-p[2] / 16.0, p[1] / 16.0, -p[0] / 16.0])


def t2g(t):
    return np.array([-t[2] * 16.0, t[1] * 16.0, -t[0] * 16.0])


class Anim:
    def __init__(self, name, length, loop=False):
        self.name = name
        self.length = float(length)
        self.loop = loop
        self.keys = {}        # (bone, ch) -> [(t, (x,y,z), ease)]
        self.iks = []         # (t, arm 'r'|'l', target track coords, ease, bend sign)
        self.events = []
        self.rolls = []       # (t, deg, ease)
        self.attach = []      # (t0, t1, bone, local grip px (bedrock, rel. to bone pivot), victim offset (track))
        self.vkeys = []       # explicit victim keys (t, fwd, up, side, ease)
        self.extra_bones = set()
        self.looks = []
        self.prones = []

    # --- authoring
    def _add(self, bone, ch, t, v, e):
        ks = self.keys.setdefault((bone, ch), [])
        if not ks and t > 1e-6:
            rest = (1.0, 1.0, 1.0) if ch == "scale" else (0.0, 0.0, 0.0)
            ks.append((0.0, rest, "io"))
        ks[:] = [k for k in ks if abs(k[0] - t) > 1e-6]
        ks.append((float(t), tuple(float(x) for x in v), e))
        ks.sort(key=lambda k: k[0])

    def rot(self, t, bone, v, e="io"):
        self._add(bone, "rotation", float(t), v, e)
        return self

    def pos(self, t, bone, v, e="io"):
        self._add(bone, "position", float(t), v, e)
        return self

    def scl(self, t, bone, v, e="io"):
        if isinstance(v, (int, float)):
            v = (v, v, v)
        self._add(bone, "scale", float(t), v, e)
        return self

    def prone(self, t0, t1):
        """the victim lies flat on the floor (eyes 0.4 above it) during [t0, t1]"""
        self.prones.append((float(t0), float(t1)))
        return self

    def is_prone(self, t):
        return any(a - 1e-6 <= t <= b + 1e-6 for a, b in self.prones)

    def look(self, t0, t1, bone, local=None):
        """camera target follows another bone (e.g. the foot that comes down on you)"""
        self.looks.append((float(t0), float(t1), bone, local))
        return self

    def pose(self, t, d, e="io"):
        for b, v in d.items():
            self.rot(t, b, v, e)
        return self

    def reach(self, t, arm, target, e="io", bend=1.0):
        self.iks.append((float(t), arm, np.array(target, float), e, bend))
        return self

    def ev(self, t, typ, arg="", s=1.0):
        self.events.append({"t": round(float(t), 3), "type": typ, "arg": arg, "s": round(float(s), 3)})
        return self

    def roll(self, t, deg, e="io"):
        self.rolls.append((float(t), float(deg), e))
        return self

    def hold_victim(self, t0, t1, bone="rightHand", grip=None, offset=(0.15, -1.38, 0.0)):
        self.attach.append((float(t0), float(t1), bone, grip, np.array(offset, float)))
        return self

    def victim(self, t, fwd, up, side=0.0, e="io"):
        self.vkeys.append((float(t), float(fwd), float(up), float(side), e))
        return self

    # --- sampling
    @staticmethod
    def _sample(keys, t):
        ks = sorted(keys, key=lambda k: k[0])
        if t <= ks[0][0]:
            return np.array(ks[0][1])
        for (t0, v0, _), (t1, v1, e1) in zip(ks, ks[1:]):
            if t0 <= t <= t1:
                f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                k = ease(e1, f)
                return np.array(v0) + (np.array(v1) - np.array(v0)) * k
        return np.array(ks[-1][1])

    def pose_at(self, t):
        """bone -> [rot, pos, scale] for georender."""
        out = {}
        for (bone, ch), ks in self.keys.items():
            v = self._sample(ks, t)
            cur = out.setdefault(bone, [(0, 0, 0), (0, 0, 0), (1, 1, 1)])
            idx = {"rotation": 0, "position": 1, "scale": 2}[ch]
            cur[idx] = tuple(v)
        return out

    def times(self, fps=24):
        n = max(1, int(round(self.length * fps)))
        return [self.length * i / n for i in range(n + 1)]

    # --- IK
    def solve_ik(self, model, verbose=False):
        """Turns every reach() into arm / forearm rotation keys, solved on `model` with the rest of the pose."""
        last = {}
        for (t, arm, target, e, bend) in sorted(self.iks, key=lambda k: k[0]):
            A = "rightArm" if arm == "r" else "leftArm"
            F = "rightForearm" if arm == "r" else "leftForearm"
            H = "rightHand" if arm == "r" else "leftHand"
            base = self.pose_at(t)
            tgt = t2g(target)
            hb = model.bones[H]
            wrist = np.array(hb["pivot"], float)  # rest pivot of the hand = wrist
            # grip point a bit down the hand
            grip_local = wrist + np.array([0, -4.0, -0.5])

            def f(x):
                pose = {k: list(v) for k, v in base.items()}
                pose[A] = [(x[0], x[1], x[2]), (0, 0, 0), (1, 1, 1)]
                pose[F] = [(x[3], 0, 0), (0, 0, 0), (1, 1, 1)]
                mats, _ = bone_matrices(model, pose)
                p = (mats[H] @ np.append(grip_local, 1))[:3]
                d = np.linalg.norm(p - tgt)
                # keep the elbow bending the natural way and angles modest
                reg = 0.0004 * (x[0] ** 2 * 0.1 + x[1] ** 2 + x[2] ** 2 + 0.2 * x[3] ** 2)
                pen = 0.02 * max(0.0, x[3] * bend) ** 2  # forearm x should be <= 0 (flex forward)
                return d * d + reg + pen

            best = None
            starts = [[-60, 0, 0, -30], [-110, 0, 0, -15], [-25, 0, 0, -80]]
            if arm in last:
                starts = [last[arm]] + starts[:2]
            for x0 in starts:
                r = minimize(f, np.array(x0, float), method="Powell", options={"maxiter": 2500, "xtol": 1e-2, "ftol": 1e-5})
                if best is None or r.fun < best.fun:
                    best = r
            x = best.x
            last[arm] = list(x)
            self.rot(t, A, (x[0], x[1], x[2]), e)
            self.rot(t, F, (x[3], 0, 0), e)
            if verbose:
                print("  ik %s t=%.2f %s err=%.2fpx" % (self.name, t, arm, math.sqrt(max(0, best.fun))))
        self.iks = []

    # --- output
    def to_json(self, fps=24):
        bones = {}
        ts = self.times(fps)
        for (bone, ch), ks in sorted(self.keys.items()):
            if len(ks) == 1:
                vals = {"0.0": [round(v, 3) for v in ks[0][1]]}
            else:
                vals = {}
                prev = None
                for t in ts:
                    v = [round(float(x), 2) for x in self._sample(ks, t)]
                    if prev is not None and v == prev and t != ts[-1]:
                        continue
                    vals[_tk(t)] = v
                    prev = v
                vals[_tk(ts[-1])] = [round(float(x), 2) for x in self._sample(ks, ts[-1])]
            bones.setdefault(bone, {})[ch] = vals
        out = {"animation_length": round(self.length, 3), "bones": bones}
        if self.loop:
            out["loop"] = True
        else:
            out["loop"] = "hold_on_last_frame"
        return out

    # --- track
    def track(self, model, eye_local, name=None, fps=20, phase_sounds=None):
        """Measures victim / camera keys on the posed model."""
        victim, head, rolls, clips = [], [], [], []
        ts = self.times(fps)
        hold_bone_cache = {}
        for t in ts:
            pose = self.pose_at(t)
            mats, _ = bone_matrices(model, pose)
            # camera target: the eyes (or another bone for a moment)
            e = (mats["head"] @ np.append(eye_local, 1))[:3]
            for (l0, l1, lb, ll) in self.looks:
                if l0 - 1e-6 <= t <= l1 + 1e-6:
                    lp = np.array(model.bones[lb]["pivot"], float) if ll is None else np.array([-ll[0], ll[1], ll[2]], float)
                    if ll is None and lb.endswith("Foot"):
                        lp = lp + np.array([0, -2.0, -2.0])
                    e = (mats[lb] @ np.append(lp, 1))[:3]
            head.append([round(t, 3)] + [round(float(v), 3) for v in g2t(e)])
            # victim
            v = None
            for (t0, t1, bone, grip, off) in self.attach:
                if t0 - 1e-6 <= t <= t1 + 1e-6:
                    if grip is None:
                        piv = np.array(model.bones[bone]["pivot"], float)
                        g = piv + np.array([0, -4.0, -0.5])
                    else:
                        g = np.array([-grip[0], grip[1], grip[2]], float)
                    p = (mats[bone] @ np.append(g, 1))[:3]
                    v = g2t(p) + off
                    break
            if v is None and self.vkeys:
                ks = [(k[0], (k[1], k[2], k[3]), k[4]) for k in self.vkeys]
                v = self._sample(ks, t)
            if v is None:
                v = np.array([1.2, 0.0, 0.0])
            victim.append([round(t, 3)] + [round(float(x), 3) for x in v])
            # is the victim's camera inside the ghost's body?
            camp = t2g(v + np.array([0, 0.4 if self.is_prone(t) else 1.62, 0]))
            holding = {seg[2] for seg in self.attach if seg[0] - 1e-6 <= t <= seg[1] + 1e-6}
            hit = inside_any(model, mats, camp, 0.8, skip=holding)
            if hit:
                clips.append((round(t, 2), hit))
        if self.rolls:
            ks = [(r[0], (r[1], 0, 0), r[2]) for r in self.rolls]
            for t in ts:
                rolls.append([round(t, 3), round(float(self._sample(ks, t)[0]), 2)])
        self.clips = clips
        tr = {"name": name or self.name, "anim": self.name, "length": round(self.length, 3), "victim": victim, "head": head,
              "events": sorted(self.events, key=lambda e: e["t"])}
        if rolls:
            tr["roll"] = rolls
        if self.prones:
            tr["prone"] = [[round(a, 3), round(b, 3)] for a, b in self.prones]
        if self.loop:
            tr["loop"] = True
        return tr


def _tk(t):
    s = ("%.4f" % t).rstrip("0").rstrip(".")
    return s if "." in s else s + ".0"


def inside_any(model, mats, p, pad=0.0, skip=()):
    """Name of the bone whose posed cube (+pad px) contains point p (model space), or None."""
    from georender import T as _T, RX as _RX, RY as _RY, RZ as _RZ
    for name in model.order:
        if name in skip:
            continue
        b = model.bones[name]
        for c in b["cubes"]:
            cm = mats[name] @ _T(*c["pivot"]) @ _RZ(c["rot"][2]) @ _RY(c["rot"][1]) @ _RX(c["rot"][0]) @ _T(*[-v for v in c["pivot"]])
            inv = np.linalg.inv(cm)
            q = (inv @ np.append(p, 1))[:3]
            allv = np.array([v for qd in c["quads"] for v in qd[0]], float)
            lo, hi = allv.min(0) - pad, allv.max(0) + pad
            if (q >= lo).all() and (q <= hi).all():
                return name
    return None
