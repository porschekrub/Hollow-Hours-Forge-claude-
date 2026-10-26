#!/usr/bin/env python3
"""Tiny software renderer for Bedrock/GeckoLib .geo.json models (+ .animation.json poses).

It follows GeckoLib's own conventions (BakedModelFactory / RenderUtils), so a preview made
here matches what the mod draws in game.  Used to check new models and every keyframe of
the jumpscare / kill animations without starting Minecraft.

    python3 georender.py model.geo.json texture.png out.png [--anim file.json name t] [--yaw 30]
"""
import json
import math
import re
import sys

import numpy as np
from PIL import Image


# ----------------------------------------------------------------------------- molang (subset)
def _molang(expr, env):
    if isinstance(expr, (int, float)):
        return float(expr)
    s = str(expr).strip().lower()
    if not s:
        return 0.0
    s = s.replace("math.", "m_")
    s = re.sub(r"query\.([a-z_]+)", lambda m: "q_" + m.group(1), s)
    s = re.sub(r"(variable|v)\.([a-z_0-9]+)", lambda m: "v_" + m.group(2), s)
    s = s.replace("return ", "")
    if ";" in s:
        s = s.split(";")[0]
    ns = {
        "m_sin": lambda a: math.sin(math.radians(a)), "m_cos": lambda a: math.cos(math.radians(a)),
        "m_abs": abs, "m_clamp": lambda v, a, b: max(a, min(b, v)), "m_min": min, "m_max": max,
        "m_lerp": lambda a, b, t: a + (b - a) * t, "m_sqrt": math.sqrt, "m_pow": math.pow,
        "m_floor": math.floor, "m_ceil": math.ceil, "m_round": round, "m_mod": lambda a, b: a % b,
        "m_random": lambda a, b: (a + b) / 2, "m_pi": math.pi, "m_exp": math.exp,
        "m_trunc": math.trunc, "m_atan2": lambda y, x: math.degrees(math.atan2(y, x)),
        "m_hermite_blend": lambda t: 3 * t * t - 2 * t * t * t,
    }
    for k, v in env.items():
        ns[k] = v

    try:
        return float(_Parser(s, ns).parse())
    except Exception as e:  # pragma: no cover - debugging aid
        raise ValueError(f"molang '{expr}': {e}")


class _Parser:
    """Pratt parser for the molang subset used by animations (numbers, vars, calls, ?:, && || ! < > ==)."""
    TOK = re.compile(r"\s*(?:(\d+\.?\d*|\.\d+)|([a-z_][a-z_0-9]*)|(&&|\|\||==|!=|<=|>=|[-+*/(),?:<>!]))")

    def __init__(self, s, ns):
        self.toks = []
        pos = 0
        s = s.strip()
        while pos < len(s):
            m = self.TOK.match(s, pos)
            if not m:
                raise ValueError(f"bad token at {s[pos:]}")
            pos = m.end()
            if m.group(1):
                self.toks.append(("n", float(m.group(1))))
            elif m.group(2):
                self.toks.append(("id", m.group(2)))
            else:
                self.toks.append(("op", m.group(3)))
            while pos < len(s) and s[pos].isspace():
                pos += 1
        self.i = 0
        self.ns = ns

    def peek(self):
        return self.toks[self.i] if self.i < len(self.toks) else ("end", None)

    def take(self):
        t = self.peek()
        self.i += 1
        return t

    def parse(self):
        v = self.expr(0)
        return v

    BP = {"?": 1, "||": 2, "&&": 3, "==": 4, "!=": 4, "<": 5, ">": 5, "<=": 5, ">=": 5, "+": 6, "-": 6, "*": 7, "/": 7}

    def expr(self, rbp):
        t = self.take()
        if t[0] == "n":
            left = t[1]
        elif t[0] == "id":
            name = t[1]
            if self.peek() == ("op", "("):
                self.take()
                args = []
                if self.peek() != ("op", ")"):
                    while True:
                        args.append(self.expr(0))
                        if self.peek() == ("op", ","):
                            self.take()
                            continue
                        break
                self.take()
                left = self.ns[name](*args)
            else:
                v = self.ns.get(name, 0.0)
                left = v() if callable(v) and not isinstance(v, (int, float)) and name == "m_pi" else v
                if name == "m_pi":
                    left = math.pi
        elif t == ("op", "("):
            left = self.expr(0)
            self.take()
        elif t == ("op", "-"):
            left = -self.expr(8)
        elif t == ("op", "+"):
            left = self.expr(8)
        elif t == ("op", "!"):
            left = 0.0 if self.expr(8) else 1.0
        else:
            raise ValueError(f"unexpected {t}")
        while True:
            op = self.peek()
            if op[0] != "op" or op[1] not in self.BP or self.BP[op[1]] <= rbp:
                break
            self.take()
            o = op[1]
            if o == "?":
                a = self.expr(0)
                self.take()  # ':'
                b = self.expr(0)
                left = a if left else b
                continue
            right = self.expr(self.BP[o])
            left = {
                "+": lambda: left + right, "-": lambda: left - right, "*": lambda: left * right,
                "/": lambda: left / right if right else 0.0, "<": lambda: float(left < right),
                ">": lambda: float(left > right), "<=": lambda: float(left <= right),
                ">=": lambda: float(left >= right), "==": lambda: float(left == right),
                "!=": lambda: float(left != right), "&&": lambda: float(bool(left) and bool(right)),
                "||": lambda: float(bool(left) or bool(right)),
            }[o]()
        return left


# ----------------------------------------------------------------------------- matrices
def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def S(x, y, z):
    return np.diag([x, y, z, 1.0])


def RX(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], float)


def RY(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], float)


def RZ(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], float)


# ----------------------------------------------------------------------------- model
class Model:
    def __init__(self, path_or_dict):
        d = json.load(open(path_or_dict)) if isinstance(path_or_dict, str) else path_or_dict
        g = d["minecraft:geometry"][0]
        desc = g["description"]
        self.tw = desc.get("texture_width", 64)
        self.th = desc.get("texture_height", 64)
        self.bones = {}
        self.order = []
        for b in g.get("bones", []):
            px, py, pz = b.get("pivot", [0, 0, 0])
            rx, ry, rz = b.get("rotation", [0, 0, 0])
            bone = {
                "name": b["name"], "parent": b.get("parent"),
                "pivot": (-px, py, pz),
                "rot": (math.radians(-rx), math.radians(-ry), math.radians(rz)),
                "cubes": [self._cube(c) for c in b.get("cubes", [])],
            }
            self.bones[b["name"]] = bone
            self.order.append(b["name"])

    def _cube(self, c):
        ox, oy, oz = c["origin"]
        sx, sy, sz = c["size"]
        inf = c.get("inflate", 0)
        x0, y0, z0 = -(ox + sx) - inf, oy - inf, oz - inf
        x1, y1, z1 = -(ox + sx) + sx + inf, oy + sy + inf, oz + sz + inf
        V = {
            "blb": (x0, y0, z0), "brb": (x0, y0, z1), "tlb": (x0, y1, z0), "trb": (x0, y1, z1),
            "tlf": (x1, y1, z0), "trf": (x1, y1, z1), "blf": (x1, y0, z0), "brf": (x1, y0, z1),
        }
        quads_v = {
            "west": ["trb", "tlb", "blb", "brb"], "east": ["tlf", "trf", "brf", "blf"],
            "north": ["tlb", "tlf", "blf", "blb"], "south": ["trf", "trb", "brb", "brf"],
            "up": ["trb", "trf", "tlf", "tlb"], "down": ["blb", "blf", "brf", "brb"],
        }
        mirror = bool(c.get("mirror", False))
        uv = c.get("uv", [0, 0])
        quads = []
        for face in ("west", "east", "north", "south", "up", "down"):
            if isinstance(uv, dict):
                f = uv.get(face)
                if not f:
                    continue
                u, v = f["uv"]
                us, vs = f.get("uv_size", [sx, sy])
            else:
                u0, v0 = uv
                fx, fy, fz = math.floor(sx), math.floor(sy), math.floor(sz)
                u, v, us, vs = {
                    "west": (u0 + fz + fx, v0 + fz, fz, fy), "east": (u0, v0 + fz, fz, fy),
                    "north": (u0 + fz, v0 + fz, fx, fy), "south": (u0 + fz + fx + fz, v0 + fz, fx, fy),
                    "up": (u0 + fz, v0, fx, fz), "down": (u0 + fz + fx, v0 + fz, fx, -fz),
                }[face]
            names = quads_v[face]
            if mirror and face in ("west", "east"):
                names = quads_v["east" if face == "west" else "west"]
            uR, uL = u + us, u
            if mirror:
                uR, uL = u, u + us
            uvs = [(uR, v), (uL, v), (uL, v + vs), (uR, v + vs)]
            quads.append(([V[n] for n in names], uvs))
        cp = c.get("pivot", [0, 0, 0])
        cr = c.get("rotation", [0, 0, 0])
        return {"quads": quads, "pivot": (-cp[0], cp[1], cp[2]),
                "rot": (math.radians(-cr[0]), math.radians(-cr[1]), math.radians(cr[2]))}


# ----------------------------------------------------------------------------- animation
def _kf_value(val, env):
    if isinstance(val, dict):
        if "post" in val:
            val = val["post"]
        elif "pre" in val:
            val = val["pre"]
        elif "vector" in val:
            val = val["vector"]
    if isinstance(val, (int, float, str)):
        x = _molang(val, env)
        return (x, x, x)
    return tuple(_molang(v, env) for v in val)


def channel_at(chan, t, env):
    if chan is None:
        return None
    if not isinstance(chan, dict) or all(not re.match(r"^[0-9.]+$", k) for k in chan.keys()):
        return _kf_value(chan, env)
    keys = sorted(((float(k), v) for k, v in chan.items()), key=lambda kv: kv[0])
    if t <= keys[0][0]:
        return _kf_value(keys[0][1], env)
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            a = _kf_value(v0, env)
            b = _kf_value(v1, env)
            k = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return tuple(a[i] + (b[i] - a[i]) * k for i in range(3))
    return _kf_value(keys[-1][1], env)


def pose_from_anims(anims, t, env=None):
    """anims: list of animation dicts (layered, later ones add on top). Returns bone -> (rot, pos, scale)."""
    env = dict(env or {})
    env["q_anim_time"] = t
    env.setdefault("q_life_time", t)
    pose = {}
    for a in anims:
        length = a.get("animation_length")
        tt = t
        if length and a.get("loop") is True:
            tt = t % length
        elif length:
            tt = min(t, length)
        env["q_anim_time"] = tt
        for bname, ch in a.get("bones", {}).items():
            r = channel_at(ch.get("rotation"), tt, env)
            p = channel_at(ch.get("position"), tt, env)
            s = channel_at(ch.get("scale"), tt, env)
            cur = pose.get(bname, [(0, 0, 0), (0, 0, 0), (1, 1, 1)])
            if r:
                cur[0] = tuple(cur[0][i] + r[i] for i in range(3))
            if p:
                cur[1] = tuple(cur[1][i] + p[i] for i in range(3))
            if s:
                cur[2] = tuple(cur[2][i] * s[i] for i in range(3))
            pose[bname] = cur
    return pose


# ----------------------------------------------------------------------------- render
def bone_matrices(model, pose):
    mats = {}
    hidden = set()
    for name in model.order:
        b = model.bones[name]
        par = mats.get(b["parent"], np.eye(4)) if b["parent"] else np.eye(4)
        r, p, s = pose.get(name, [(0, 0, 0), (0, 0, 0), (1, 1, 1)])
        rx = b["rot"][0] + math.radians(-r[0])
        ry = b["rot"][1] + math.radians(-r[1])
        rz = b["rot"][2] + math.radians(r[2])
        px, py, pz = b["pivot"]
        m = par @ T(-p[0], p[1], p[2]) @ T(px, py, pz) @ RZ(rz) @ RY(ry) @ RX(rx) @ S(*s) @ T(-px, -py, -pz)
        mats[name] = m
        if s[0] == 0 and s[1] == 0 and s[2] == 0:
            hidden.add(name)
    return mats, hidden


def render(model, tex, pose=None, size=512, yaw=0.0, pitch=0.0, scale=None, center=None,
           bg=(24, 24, 28, 255), persp=None, light=True, hide=(), emissive=None):
    pose = pose or {}
    texa = np.asarray(tex.convert("RGBA"), dtype=np.float32)
    TH, TW = texa.shape[:2]
    emi = np.asarray(emissive.convert("RGBA"), dtype=np.float32) if emissive is not None else None
    mats, hidden = bone_matrices(model, pose)
    view = RX(math.radians(pitch)) @ RY(math.radians(yaw))
    tris = []
    for name in model.order:
        if name in hidden or name in hide:
            continue
        b = model.bones[name]
        for c in b["cubes"]:
            cm = mats[name] @ T(*c["pivot"]) @ RZ(c["rot"][2]) @ RY(c["rot"][1]) @ RX(c["rot"][0]) @ T(*[-v for v in c["pivot"]])
            for verts, uvs in c["quads"]:
                P = np.array([list(v) + [1] for v in verts], float) @ (view @ cm).T
                tris.append((P[[0, 1, 2], :3], [uvs[0], uvs[1], uvs[2]]))
                tris.append((P[[0, 2, 3], :3], [uvs[0], uvs[2], uvs[3]]))
    if not tris:
        return Image.new("RGBA", (size, size), bg)
    allp = np.concatenate([t[0] for t in tris])
    if center is None:
        center = (allp[:, 0].min() + allp[:, 0].max()) / 2, (allp[:, 1].min() + allp[:, 1].max()) / 2
    if scale is None:
        ext = max(allp[:, 0].max() - allp[:, 0].min(), allp[:, 1].max() - allp[:, 1].min())
        scale = size * 0.9 / max(ext, 1e-3)
    img = np.zeros((size, size, 4), np.float32)
    img[:] = bg
    zbuf = np.full((size, size), np.inf, np.float32)
    for P, uvs in tris:
        # camera looks toward +Z from -Z: screen x = -x
        if persp:
            camz = persp
            w = (P[:, 2] + camz)
            if (w <= 0.5).any():
                continue
            sx = size / 2 + (-(P[:, 0] - center[0])) * scale * camz / w
            sy = size / 2 - (P[:, 1] - center[1]) * scale * camz / w
        else:
            sx = size / 2 + (-(P[:, 0] - center[0])) * scale
            sy = size / 2 - (P[:, 1] - center[1]) * scale
        sz = P[:, 2]
        minx, maxx = int(max(0, math.floor(sx.min()))), int(min(size - 1, math.ceil(sx.max())))
        miny, maxy = int(max(0, math.floor(sy.min()))), int(min(size - 1, math.ceil(sy.max())))
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
        z = a * sz[0] + bb * sz[1] + cc * sz[2]
        u = (a * uvs[0][0] + bb * uvs[1][0] + cc * uvs[2][0])
        v = (a * uvs[0][1] + bb * uvs[1][1] + cc * uvs[2][1])
        ui = np.clip((u * TW / model.tw).astype(int), 0, TW - 1)
        vi = np.clip((v * TH / model.th).astype(int), 0, TH - 1)
        col = texa[vi, ui]
        sub = zbuf[miny:maxy + 1, minx:maxx + 1]
        ok = inside & (col[..., 3] > 25) & (z < sub)
        if not ok.any():
            continue
        e1, e2 = P[1] - P[0], P[2] - P[0]
        nrm = np.cross(e1, e2)
        nl = np.linalg.norm(nrm)
        shade = 1.0
        if light and nl > 0:
            nrm = nrm / nl
            shade = 0.55 + 0.45 * abs(float(np.dot(nrm, np.array([0.35, 0.6, -0.72]) / 1.0)))
        rgb = col[..., :3] * shade
        if emi is not None:
            ecol = emi[vi, ui]
            glow = ecol[..., 3:4] / 255.0
            rgb = rgb * (1 - glow) + ecol[..., :3] * glow
        region = img[miny:maxy + 1, minx:maxx + 1]
        region[ok, :3] = rgb[ok]
        region[ok, 3] = 255
        sub[ok] = z[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def load_anim(path, name):
    d = json.load(open(path))
    return d["animations"][name]


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("geo")
    ap.add_argument("tex")
    ap.add_argument("out")
    ap.add_argument("--anim", nargs=3, action="append", metavar=("FILE", "NAME", "T"))
    ap.add_argument("--yaw", type=float, default=0)
    ap.add_argument("--pitch", type=float, default=0)
    ap.add_argument("--size", type=int, default=512)
    a = ap.parse_args()
    m = Model(a.geo)
    anims = []
    t = 0.0
    for f, n, tt in a.anim or []:
        anims.append(load_anim(f, n))
        t = float(tt)
    pose = pose_from_anims(anims, t) if anims else {}
    render(m, Image.open(a.tex), pose, size=a.size, yaw=a.yaw, pitch=a.pitch).save(a.out)
