#!/usr/bin/env python3
"""Model toolkit for the Hollow Hours assets.

* Geo: load / edit / write Bedrock-GeckoLib .geo.json models (bones, cubes, tags).
* pack(): lay out every cube face in a fresh texture atlas at a chosen texel density.
* paint(): paint the whole texture (+ glow mask) from a 3D material function. Every texel knows where it sits on
  the model (rest pose, model space, in pixels), its face normal and its cube's tag, so materials can do things
  like "blood runs down from the mouth", "tear streaks under the eyes" or "embers glow in the cracks" no matter
  how the faces are laid out in the atlas.

Coordinates inside materials use georender's space: x = -bedrock x, y up, z = bedrock z; the model faces -z.
"""
import json
import math
import copy

import numpy as np

from georender import Model, bone_matrices, T, RX, RY, RZ

FACES = ("west", "east", "north", "south", "up", "down")


# ----------------------------------------------------------------------------- noise
def _hash(ix, iy, iz, seed):
    h = (ix.astype(np.int64) * 73856093) ^ (iy.astype(np.int64) * 19349663) ^ (iz.astype(np.int64) * 83492791) ^ (seed * 2654435761)
    h &= 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = (h ^ (h >> 16)) & 0xFFFFFFFF
    return (h & 0xFFFFFF).astype(np.float64) / float(0x1000000)


def vnoise(p, scale=1.0, seed=0):
    """3D value noise in [0,1]; p: (N,3)."""
    q = np.asarray(p, float) / scale
    i = np.floor(q)
    f = q - i
    f = f * f * (3 - 2 * f)
    ix, iy, iz = i[:, 0], i[:, 1], i[:, 2]
    out = 0
    for dx in (0, 1):
        wx = f[:, 0] if dx else 1 - f[:, 0]
        for dy in (0, 1):
            wy = f[:, 1] if dy else 1 - f[:, 1]
            for dz in (0, 1):
                wz = f[:, 2] if dz else 1 - f[:, 2]
                out = out + wx * wy * wz * _hash(ix + dx, iy + dy, iz + dz, seed)
    return out


def fbm(p, scale=1.0, octaves=4, seed=0, gain=0.5):
    amp, tot, norm = 1.0, 0.0, 0.0
    s = scale
    for o in range(octaves):
        tot = tot + amp * vnoise(p, s, seed + o * 101)
        norm += amp
        amp *= gain
        s *= 0.5
    return tot / norm


def ridge(p, scale=1.0, octaves=3, seed=0):
    """Thin dark lines (cracks / veins): 1 on the line, 0 away from it."""
    n = fbm(p, scale, octaves, seed)
    return np.clip(1 - np.abs(n - 0.5) * 2 / 0.08, 0, 1)


def mix(a, b, t):
    t = np.asarray(t, float)[..., None] if np.ndim(t) == 1 else t
    return a * (1 - t) + b * t


def col(hexstr):
    h = hexstr.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], float)


# ----------------------------------------------------------------------------- geometry
class Geo:
    def __init__(self, identifier="geometry.unknown"):
        self.identifier = identifier
        self.bones = []
        self.tw = 64
        self.th = 64
        self.extra_desc = {}

    # --- io
    @staticmethod
    def load(path, index=0):
        d = json.load(open(path))
        g = d["minecraft:geometry"][index]
        geo = Geo(g["description"]["identifier"])
        geo.tw = g["description"].get("texture_width", 64)
        geo.th = g["description"].get("texture_height", 64)
        for k, v in g["description"].items():
            if k.startswith("visible_bounds"):
                geo.extra_desc[k] = v
        for b in g.get("bones", []):
            nb = {"name": b["name"], "pivot": list(b.get("pivot", [0, 0, 0])), "cubes": []}
            if b.get("parent"):
                nb["parent"] = b["parent"]
            if b.get("rotation"):
                nb["rotation"] = list(b["rotation"])
            for c in b.get("cubes", []):
                nb["cubes"].append(copy.deepcopy(c))
            geo.bones.append(nb)
        return geo

    def to_dict(self):
        bones = []
        for b in self.bones:
            nb = {"name": b["name"], "pivot": [round(v, 4) for v in b["pivot"]]}
            if b.get("parent"):
                nb["parent"] = b["parent"]
            if b.get("rotation") and any(abs(v) > 1e-9 for v in b["rotation"]):
                nb["rotation"] = [round(v, 4) for v in b["rotation"]]
            cubes = []
            for c in b["cubes"]:
                cc = {k: v for k, v in c.items() if not k.startswith("_")}
                cc["origin"] = [round(v, 4) for v in cc["origin"]]
                cc["size"] = [round(v, 4) for v in cc["size"]]
                if "pivot" in cc:
                    cc["pivot"] = [round(v, 4) for v in cc["pivot"]]
                cubes.append(cc)
            if cubes:
                nb["cubes"] = cubes
            bones.append(nb)
        desc = {"identifier": self.identifier, "texture_width": self.tw, "texture_height": self.th}
        desc.update(self.extra_desc)
        return {"format_version": "1.12.0", "minecraft:geometry": [{"description": desc, "bones": bones}]}

    def save(self, path):
        with open(path, "w") as f:
            json.dump(self.to_dict(), f, separators=(",", ":"))

    # --- editing
    def bone(self, name):
        for b in self.bones:
            if b["name"] == name:
                return b
        return None

    def has(self, name):
        return self.bone(name) is not None

    def add_bone(self, name, parent, pivot, rotation=None):
        b = self.bone(name)
        if b is None:
            b = {"name": name, "pivot": list(pivot), "cubes": []}
            if parent:
                b["parent"] = parent
            # keep parents before children
            idx = len(self.bones)
            for i, x in enumerate(self.bones):
                if x["name"] == parent:
                    idx = i + 1
            while idx < len(self.bones) and self._is_descendant(self.bones[idx]["name"], parent):
                idx += 1
            self.bones.insert(idx, b)
        if rotation:
            b["rotation"] = list(rotation)
        return b

    def _is_descendant(self, name, ancestor):
        b = self.bone(name)
        while b is not None and b.get("parent"):
            if b["parent"] == ancestor:
                return True
            b = self.bone(b["parent"])
        return False

    def cube(self, bone, origin, size, tag="skin", rot=None, pivot=None, inflate=0.0, **extra):
        """origin/size in bedrock model space (pixels)."""
        c = {"origin": list(map(float, origin)), "size": [max(0.01, float(s)) for s in size], "_tag": tag}
        if rot is not None and any(abs(r) > 1e-9 for r in rot):
            c["rotation"] = list(rot)
            c["pivot"] = list(pivot if pivot is not None else [origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2])
        if inflate:
            c["inflate"] = inflate
        for k, v in extra.items():
            c["_" + k] = v
        self.bone(bone)["cubes"].append(c)
        return c

    def box(self, bone, center, size, tag="skin", rot=None, **kw):
        """Cube by its centre."""
        o = [center[i] - size[i] / 2 for i in range(3)]
        return self.cube(bone, o, size, tag, rot=rot, pivot=list(center) if rot else None, **kw)

    def sym(self, bone_l, bone_r, center, size, tag="skin", rot=None, **kw):
        """A cube on the left bone and its mirror image (x -> -x, rotations mirrored) on the right bone."""
        a = self.box(bone_l, center, size, tag, rot=rot, **kw)
        c2 = [-center[0], center[1], center[2]]
        r2 = [rot[0], -rot[1], -rot[2]] if rot else None
        b = self.box(bone_r, c2, size, tag, rot=r2, **kw)
        return a, b

    def clear(self, bone, pred=None):
        b = self.bone(bone)
        if b is None:
            return
        b["cubes"] = [c for c in b["cubes"] if pred is not None and not pred(c)]

    def retag(self, bone, tag, pred=None):
        b = self.bone(bone)
        for c in b["cubes"]:
            if pred is None or pred(c):
                c["_tag"] = tag

    def remove_bone(self, name):
        kids = [b["name"] for b in self.bones if b.get("parent") == name]
        for k in kids:
            self.remove_bone(k)
        self.bones = [b for b in self.bones if b["name"] != name]

    def translate(self, dx, dy, dz):
        for b in self.bones:
            b["pivot"] = [b["pivot"][0] + dx, b["pivot"][1] + dy, b["pivot"][2] + dz]
            for c in b["cubes"]:
                c["origin"] = [c["origin"][0] + dx, c["origin"][1] + dy, c["origin"][2] + dz]
                if "pivot" in c:
                    c["pivot"] = [c["pivot"][0] + dx, c["pivot"][1] + dy, c["pivot"][2] + dz]

    def subset(self, keep, identifier=None):
        g = copy.deepcopy(self)
        if identifier:
            g.identifier = identifier
        g.bones = [b for b in g.bones if b["name"] in keep]
        names = {b["name"] for b in g.bones}
        for b in g.bones:
            if b.get("parent") and b["parent"] not in names:
                del b["parent"]
        return g

    def cube_count(self):
        return sum(len(b["cubes"]) for b in self.bones)

    # --- uv layout
    def pack(self, density, width=1024, pad=0):
        rects = []
        for bi, b in enumerate(self.bones):
            for ci, c in enumerate(b["cubes"]):
                d = density * c.get("_density", 1.0)
                sx, sy, sz = c["size"]
                dims = {"west": (sz, sy), "east": (sz, sy), "north": (sx, sy), "south": (sx, sy), "up": (sx, sz), "down": (sx, sz)}
                c["uv"] = {}
                for f in FACES:
                    w, h = dims[f]
                    rects.append((max(1, int(round(w * d))), max(1, int(round(h * d))), bi, ci, f))
        rects.sort(key=lambda r: (-r[1], -r[0]))
        x = y = rowh = 0
        for w, h, bi, ci, f in rects:
            if w + pad > width:
                raise ValueError("face wider than atlas: %d" % w)
            if x + w + pad > width:
                x = 0
                y += rowh + pad
                rowh = 0
            self.bones[bi]["cubes"][ci]["uv"][f] = {"uv": [x, y], "uv_size": [w, h]}
            x += w + pad
            rowh = max(rowh, h)
        height = y + rowh
        th = 16
        while th < height:
            th *= 2
        self.tw, self.th = width, th
        return width, th


# ----------------------------------------------------------------------------- painting
def paint(geo, material, seed=0):
    """material(ctx) -> (rgba (N,4) 0..255, glow rgba (N,4) or None). Returns (tex, glow) uint8 images."""
    from PIL import Image
    d = geo.to_dict()
    model = Model(d)
    mats, _ = bone_matrices(model, {})
    tex = np.zeros((geo.th, geo.tw, 4), np.float64)
    glow = np.zeros((geo.th, geo.tw, 4), np.float64)
    rng = np.random.default_rng(seed)
    for b in geo.bones:
        mb = model.bones[b["name"]]
        for ci, craw in enumerate(b["cubes"]):
            c = mb["cubes"][ci]
            cm = mats[b["name"]] @ T(*c["pivot"]) @ RZ(c["rot"][2]) @ RY(c["rot"][1]) @ RX(c["rot"][0]) @ T(*[-v for v in c["pivot"]])
            # cube bounds in georender local space
            allv = np.array([v for q in c["quads"] for v in q[0]], float)
            lo, hi = allv.min(0), allv.max(0)
            ext = np.maximum(hi - lo, 1e-6)
            centre = (cm @ np.append((lo + hi) / 2, 1))[:3]
            for qi, (verts, uvs) in enumerate(c["quads"]):
                face = FACES[qi]
                r = craw["uv"][face]
                u0, v0 = r["uv"]
                us, vs = r["uv_size"]
                us, vs = int(us), int(vs)
                if us <= 0 or vs <= 0:
                    continue
                V = np.array(verts, float)
                (uR, vt), (uL, _), _, (_, vb) = uvs
                ii, jj = np.meshgrid(np.arange(us) + 0.5, np.arange(vs) + 0.5)
                uu = u0 + ii
                vv = v0 + jj
                a = (uu - uR) / (uL - uR)
                bb = (vv - vt) / (vb - vt)
                P = V[0][None, None, :] + a[..., None] * (V[1] - V[0]) + bb[..., None] * (V[3] - V[0])
                P = P.reshape(-1, 3)
                lp = (P - lo) / ext
                W = (np.c_[P, np.ones(len(P))] @ cm.T)[:, :3]
                nrm = np.cross(V[1] - V[0], V[3] - V[0])
                nrm = nrm / max(np.linalg.norm(nrm), 1e-9)
                fc = (V.mean(0) - (lo + hi) / 2)
                if np.dot(nrm, fc) < 0:
                    nrm = -nrm
                nw = cm[:3, :3] @ nrm
                nw = nw / max(np.linalg.norm(nw), 1e-9)
                # distance to the face border in texels (for edge darkening)
                edge = np.minimum(np.minimum(ii, us - ii), np.minimum(jj, vs - jj)).reshape(-1)
                ctx = {
                    "bone": b["name"], "tag": craw.get("_tag", "skin"), "face": face, "p": W, "n": nw, "lp": lp,
                    "centre": centre, "size": np.array(craw["size"]), "edge": edge, "n_tex": len(W), "rng": rng,
                    "cube": craw, "uvw": (us, vs),
                }
                rgba, g = material(ctx)
                rgba = np.asarray(rgba, float).reshape(vs, us, 4)
                tex[v0:v0 + vs, u0:u0 + us] = rgba
                if g is not None:
                    glow[v0:v0 + vs, u0:u0 + us] = np.asarray(g, float).reshape(vs, us, 4)
    timg = Image.fromarray(np.clip(tex, 0, 255).astype(np.uint8), "RGBA")
    gimg = Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8), "RGBA")
    return timg, gimg


def rgba(rgb, a=255.0):
    rgb = np.asarray(rgb, float)
    if rgb.ndim == 1:
        rgb = rgb[None, :]
    alpha = np.broadcast_to(np.asarray(a, float).reshape(-1, 1) if np.ndim(a) else np.full((len(rgb), 1), a), (len(rgb), 1))
    return np.concatenate([rgb, alpha], 1)


def shade_edges(rgb, ctx, amount=0.18, width=1.0):
    k = np.clip(1 - ctx["edge"] / width, 0, 1) * amount
    return rgb * (1 - k[:, None])
