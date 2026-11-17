#!/usr/bin/env python3
"""Java block model (elements + several textures) -> Bedrock geometry + one baked texture atlas.

Every face's pixels are cut out of its Java texture (flipped / rotated as the Java model asks) and packed into one
atlas, so the Bedrock model needs no uv tricks. Several Java models can go into one geometry as separate bones.

    from jmodel2geo import convert
    convert("geometry.ghost_grave", [("root", "block/grave.json", [0, 0, 0])], "out.geo.json", "out.png")
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(HERE, "..", "..", "hollow-hours", "src", "main", "resources", "assets", "hollowhours")


def _tex(ref, textures):
    seen = 0
    while ref.startswith("#") and seen < 8:
        ref = textures.get(ref[1:], ref[1:])
        seen += 1
    name = ref.split(":", 1)[-1]
    p = os.path.join(JAVA, "textures", name + ".png")
    im = Image.open(p).convert("RGBA")
    if im.height > im.width and im.height % im.width == 0:  # animated strip: first frame
        im = im.crop((0, 0, im.width, im.width))
    return im


def _load(model):
    d = json.load(open(os.path.join(JAVA, "models", model), encoding="utf8"))
    tex = dict(d.get("textures", {}))
    els = d.get("elements")
    parent = d.get("parent", "")
    while els is None and parent.startswith("hollowhours:"):
        pd = json.load(open(os.path.join(JAVA, "models", parent.split(":", 1)[1] + ".json"), encoding="utf8"))
        for k, v in pd.get("textures", {}).items():
            tex.setdefault(k, v)
        els = pd.get("elements")
        parent = pd.get("parent", "")
    return els or [], tex


class Atlas:
    def __init__(self):
        self.tiles = []  # (image, key)
        self.index = {}

    def add(self, im):
        key = (im.size, im.tobytes())
        if key in self.index:
            return self.index[key]
        self.index[key] = len(self.tiles)
        self.tiles.append(im)
        return len(self.tiles) - 1

    def pack(self):
        order = sorted(range(len(self.tiles)), key=lambda i: (-self.tiles[i].height, -self.tiles[i].width))
        area = sum((t.width + 2) * (t.height + 2) for t in self.tiles)
        w = 16
        while w * w < area * 1.25 or w < max(t.width + 2 for t in self.tiles):
            w *= 2
        while True:
            pos, x, y, rowh, ok = {}, 0, 0, 0, True
            for i in order:
                t = self.tiles[i]
                if x + t.width + 2 > w:
                    x, y, rowh = 0, y + rowh, 0
                pos[i] = (x + 1, y + 1)
                x += t.width + 2
                rowh = max(rowh, t.height + 2)
                if y + rowh > w:
                    ok = False
                    break
            if ok:
                break
            w *= 2
        h = 16
        while h < y + rowh:
            h *= 2
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        for i, (px, py) in pos.items():
            t = self.tiles[i]
            # 1px of the edge colours around every tile, so nothing bleeds in from the neighbours
            big = t.resize((t.width + 2, t.height + 2), Image.NEAREST)
            img.paste(big, (px - 1, py - 1))
            img.paste(t, (px, py))
        return img, pos


def _face_tile(face, textures):
    src = _tex(face["texture"], textures)
    k = src.width / 16.0
    u1, v1, u2, v2 = face.get("uv", [0, 0, 16, 16])
    w = max(1, round(abs(u2 - u1) * k))
    h = max(1, round(abs(v2 - v1) * k))
    box = (round(min(u1, u2) * k), round(min(v1, v2) * k))
    box = (min(box[0], src.width - 1), min(box[1], src.height - 1))
    tile = src.crop((box[0], box[1], min(src.width, box[0] + w), min(src.height, box[1] + h)))
    if tile.size != (w, h):
        tile = tile.resize((w, h), Image.NEAREST)
    if u1 > u2:
        tile = tile.transpose(Image.FLIP_LEFT_RIGHT)
    if v1 > v2:
        tile = tile.transpose(Image.FLIP_TOP_BOTTOM)
    rot = face.get("rotation", 0) % 360
    if rot:
        tile = tile.rotate(-rot, expand=True)  # Java turns the texture clockwise on the face
    return tile


def convert(identifier, parts, geo_out, png_out, visible=(2, 2.5, 0), inflate=0):
    """parts: [(bone name, java model path under models/, pivot [x,y,z] in Bedrock space, parent bone or None, offset)]"""
    atlas = Atlas()
    bones = []
    for part in parts:
        name, model, pivot = part[0], part[1], part[2]
        parent = part[3] if len(part) > 3 else None
        off = part[4] if len(part) > 4 else (0, 0, 0)   # moves the Java model (in its own pixels) before converting
        els, tex = _load(model)
        cubes = []
        for el in els:
            f = [el["from"][i] + off[i] for i in range(3)]
            t = [el["to"][i] + off[i] for i in range(3)]
            size = [t[0] - f[0], t[1] - f[1], t[2] - f[2]]
            cube = {"origin": [-((f[0] - 8) + size[0]), f[1], f[2] - 8], "size": size, "uv": {}}
            if inflate:
                cube["inflate"] = inflate
            r = el.get("rotation")
            if r and r.get("angle"):
                o = [r["origin"][i] + off[i] for i in range(3)]
                cube["pivot"] = [-(o[0] - 8), o[1], o[2] - 8]
                a = r["angle"]
                cube["rotation"] = {"x": [-a, 0, 0], "y": [0, -a, 0], "z": [0, 0, a]}[r["axis"]]
            for side, face in el.get("faces", {}).items():
                tile = _face_tile(face, tex)
                if side in ("up", "down"):
                    tile = tile.rotate(180)
                if tile.getextrema()[3][1] == 0:
                    continue  # fully transparent face
                cube["uv"][side] = atlas.add(tile)
            if cube["uv"]:
                cubes.append(cube)
        b = {"name": name, "pivot": pivot, "cubes": cubes}
        if parent:
            b["parent"] = parent
        bones.append(b)
    img, pos = atlas.pack()
    for b in bones:
        for c in b["cubes"]:
            for side, idx in list(c["uv"].items()):
                t = atlas.tiles[idx]
                c["uv"][side] = {"uv": [pos[idx][0], pos[idx][1]], "uv_size": [t.width, t.height]}
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": identifier, "texture_width": img.width, "texture_height": img.height,
                        "visible_bounds_width": visible[0], "visible_bounds_height": visible[1], "visible_bounds_offset": [0, visible[2] or visible[1] / 2, 0]},
        "bones": bones}]}
    os.makedirs(os.path.dirname(geo_out), exist_ok=True)
    os.makedirs(os.path.dirname(png_out), exist_ok=True)
    json.dump(geo, open(geo_out, "w"), separators=(",", ":"))
    img.save(png_out)
    return img.size, sum(len(b["cubes"]) for b in bones)
