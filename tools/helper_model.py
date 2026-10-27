#!/usr/bin/env python3
"""Generates the Helper Buddy look: GeckoLib model, 6 colour skins (+ glow maps) and all animations.

    python3 tools/helper_model.py [--preview DIR]

The helper is a small adventurer-mechanic: big helmet with goggles and a glowing antenna lamp, a scarf, a
backpack full of tools, a tool belt and boots.  Everything is written into helper-buddy/src/main/resources.
"""
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "helper-buddy", "src", "main", "resources", "assets", "helperbuddy")
TEX_W, TEX_H = 128, 128

# ------------------------------------------------------------------------------------------- palettes
#            suit          suit dark      helmet        helmet dark   scarf         pants         visor glow
SKINS = [
    dict(suit=(52, 120, 214), suit2=(33, 80, 150), helm=(250, 196, 48), helm2=(201, 140, 20), scarf=(214, 52, 52), pants=(46, 52, 70), eye=(80, 230, 255)),
    dict(suit=(206, 58, 52), suit2=(140, 34, 34), helm=(236, 236, 240), helm2=(176, 180, 190), scarf=(250, 200, 40), pants=(52, 46, 46), eye=(255, 210, 90)),
    dict(suit=(62, 160, 80), suit2=(36, 104, 52), helm=(244, 140, 40), helm2=(190, 96, 20), scarf=(60, 120, 220), pants=(50, 60, 48), eye=(150, 255, 140)),
    dict(suit=(132, 76, 196), suit2=(88, 46, 140), helm=(52, 196, 186), helm2=(30, 136, 128), scarf=(246, 120, 180), pants=(54, 44, 70), eye=(255, 140, 230)),
    dict(suit=(236, 128, 36), suit2=(170, 84, 20), helm=(150, 156, 168), helm2=(100, 106, 118), scarf=(70, 170, 70), pants=(60, 52, 44), eye=(255, 170, 60)),
    dict(suit=(48, 50, 58), suit2=(28, 30, 36), helm=(230, 180, 40), helm2=(170, 120, 20), scarf=(40, 200, 220), pants=(30, 32, 38), eye=(90, 250, 255)),
]
SKIN = (246, 204, 166)
SKIN2 = (226, 176, 140)
LEATHER = (120, 78, 44)
LEATHER2 = (84, 52, 28)
GOLD = (250, 210, 70)
GLASS = (30, 40, 56)
WHITE = (240, 240, 236)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ------------------------------------------------------------------------------------------- the model
# cubes: (bone, name, origin, size, painter, inflate)
BONES = [
    ("root", None, [0, 0, 0]),
    ("rightLeg", "root", [-2, 9, 0]),
    ("leftLeg", "root", [2, 9, 0]),
    ("body", "root", [0, 9, 0]),
    ("belt", "body", [0, 10, 0]),
    ("backpack", "body", [0, 14, 2.5]),
    ("scarf", "body", [0, 17, 0]),
    ("scarfTail", "scarf", [1.5, 17, 3]),
    ("head", "body", [0, 18, 0]),
    ("antenna", "head", [0, 28, 0]),
    ("rightArm", "body", [-5.5, 17, 0]),
    ("rightItem", "rightArm", [-5.5, 8.5, -0.5]),
    ("leftArm", "body", [5.5, 17, 0]),
]

CUBES = [
    ("rightLeg", "leg_r", [-4, 0, -2], [4, 9, 4]),
    ("leftLeg", "leg_l", [0, 0, -2], [4, 9, 4]),
    ("body", "torso", [-4, 9, -2.5], [8, 9, 5]),
    ("belt", "belt", [-4.5, 9.5, -3], [9, 1.5, 6]),
    ("backpack", "pack", [-3, 10.5, 2.5], [6, 7, 3]),
    ("backpack", "roll", [-3.5, 17, 2.5], [7, 2, 2.5]),
    ("scarf", "scarf", [-4.5, 16.5, -3], [9, 2, 6]),
    ("scarfTail", "tail", [1, 12.5, 3], [2, 4.5, 1]),
    ("head", "head", [-5, 18, -4.5], [10, 10, 9]),
    ("head", "goggles", [-4.5, 22, -5.1], [9, 3, 1]),
    ("head", "brim", [-5.5, 25.5, -5.2], [11, 1, 4]),
    ("antenna", "stalk", [-0.5, 28, -0.5], [1, 3, 1]),
    ("antenna", "lamp", [-1, 31, -1], [2, 2, 2]),
    ("rightArm", "arm_r", [-7, 8, -1.5], [3, 9, 3]),
    ("leftArm", "arm_l", [4, 8, -1.5], [3, 9, 3]),
]

FACES = ["north", "south", "east", "west", "up", "down"]


def face_size(face, size):
    w, h, d = size
    w, h, d = max(1, math.ceil(w)), max(1, math.ceil(h)), max(1, math.ceil(d))
    if face in ("north", "south"):
        return w, h
    if face in ("east", "west"):
        return d, h
    return w, d


# ------------------------------------------------------------------------------------------- painting
def noise(img, rnd, amount=0.06):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            c = px[x, y]
            if c[3] == 0:
                continue
            f = 1 + (rnd.random() - 0.5) * amount * 2
            px[x, y] = shade(c[:3], f) + (c[3],)


def paint_face(name, face, w, h, P, rnd):
    """Returns (rgba image w x h, glow image w x h)."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px, gp = img.load(), glow.load()

    def fill(c):
        for y in range(h):
            for x in range(w):
                px[x, y] = c + (255,)

    def put(x, y, c, g=None):
        if 0 <= x < w and 0 <= y < h:
            px[x, y] = c + (255,)
            if g:
                gp[x, y] = g + (255,)

    # y = 0 is the top row of the face
    if name in ("leg_r", "leg_l"):
        fill(P["pants"])
        for y in range(h - 3, h):
            for x in range(w):
                put(x, y, LEATHER if y < h - 1 else LEATHER2)
        if face == "north":
            put(1, h - 3, shade(LEATHER, 1.2))
            put(2, h - 3, shade(LEATHER, 1.2))
            for x in range(w):
                put(x, 1, shade(P["pants"], 1.15))
        if face == "up":
            fill(shade(P["pants"], 0.8))
        if face == "down":
            fill(LEATHER2)
    elif name == "torso":
        fill(P["suit"])
        if face == "north":
            # zipper and a round badge with a wrench
            for y in range(h):
                put(w // 2 - 1, y, shade(P["suit"], 0.8))
            for x in range(w):
                put(x, 0, shade(P["suit"], 1.15))
            cx, cy = 5, 3
            for y in range(1, 6):
                for x in range(3, 8):
                    if (x - cx) ** 2 + (y - cy) ** 2 <= 4.5:
                        put(x, y, WHITE)
            for p in [(4, 4), (5, 3), (6, 2), (6, 4), (4, 2)]:
                put(p[0], p[1], (120, 126, 140))
            put(1, 4, P["suit2"])
            put(1, 5, P["suit2"])
        elif face == "south":
            for x in range(w):
                put(x, 0, shade(P["suit"], 1.1))
        elif face in ("east", "west"):
            for y in range(h):
                put(0, y, shade(P["suit"], 0.9))
        elif face == "up":
            fill(shade(P["suit"], 1.1))
        elif face == "down":
            fill(P["suit2"])
    elif name == "belt":
        fill(LEATHER)
        for x in range(w):
            put(x, 0, shade(LEATHER, 1.2))
        if face == "north":
            put(w // 2, 0, GOLD)
            put(w // 2, 1 if h > 1 else 0, GOLD)
            put(w // 2 - 1, 0, GOLD)
            put(1, 0, (150, 150, 160))
            put(7, 0, (90, 90, 100))
        if face in ("up", "down"):
            fill(LEATHER2)
    elif name == "pack":
        fill(shade(P["suit2"], 0.95))
        if face == "south":
            for x in range(1, w - 1):
                put(x, 2, LEATHER)
            for y in range(3, h - 1):
                for x in range(1, w - 1):
                    put(x, y, shade(P["suit2"], 1.15))
            put(w // 2, 2, GOLD)
            # a wrench sticking out on the side is painted on east
        if face in ("east", "west"):
            for y in range(h):
                put(1, y, LEATHER)
        if face == "up":
            fill(shade(P["suit2"], 1.2))
            put(1, 1, (180, 184, 196))
            put(1, 0, (180, 184, 196))
            put(w - 2, 1, LEATHER)
    elif name == "roll":
        fill((120, 92, 60))
        for x in range(0, w, 3):
            for y in range(h):
                put(x, y, (96, 70, 44))
    elif name in ("scarf", "tail"):
        fill(P["scarf"])
        for x in range(w):
            if (x // 2) % 2 == 0:
                put(x, 0, shade(P["scarf"], 1.15))
        if name == "tail":
            for x in range(w):
                put(x, h - 1, shade(P["scarf"], 0.8))
    elif name == "head":
        helm, helm2 = P["helm"], P["helm2"]
        fill(helm)
        if face == "north":
            # rows 0-3 helmet, 4-6 goggles (separate cube), 6-9 face
            for y in range(5, h):
                for x in range(1, w - 1):
                    put(x, y, SKIN)
            for y in range(5, h):
                put(0, y, helm2)
                put(w - 1, y, helm2)
            # helmet stripe
            for y in range(0, 4):
                put(w // 2 - 1, y, shade(helm, 1.12))
                put(w // 2, y, shade(helm, 1.12))
            # cheeks and smile
            put(2, 7, (240, 150, 150))
            put(7, 7, (240, 150, 150))
            put(4, 8, (110, 60, 50))
            put(5, 8, (110, 60, 50))
            put(3, 7, (110, 60, 50))
            put(6, 7, (110, 60, 50))
            for x in range(1, w - 1):
                put(x, h - 1, SKIN2)
        elif face == "south":
            for y in range(h):
                put(w // 2 - 1, y, shade(helm, 1.12))
                put(w // 2, y, shade(helm, 1.12))
            for x in range(w):
                put(x, h - 1, helm2)
                put(x, h - 2, helm2)
            # goggle strap
            for x in range(w):
                put(x, 4, (40, 40, 46))
                put(x, 5, (40, 40, 46))
        elif face in ("east", "west"):
            for x in range(w):
                put(x, 4, (40, 40, 46))
                put(x, 5, (40, 40, 46))
            # ear cap
            for y in range(6, 9):
                for x in range(w // 2 - 1, w // 2 + 2):
                    put(x, y, helm2)
            put(w // 2, 7, shade(helm, 1.2))
        elif face == "up":
            for x in range(w):
                pass
            for y in range(h):
                put(w // 2 - 1, y, shade(helm, 1.12))
                put(w // 2, y, shade(helm, 1.12))
        elif face == "down":
            fill(SKIN2)
    elif name == "goggles":
        fill((40, 40, 46))
        if face == "north":
            for lens_x in (1, 5):
                for y in range(h):
                    for x in range(lens_x, lens_x + 3):
                        put(x, y, (150, 156, 170))
                for x in range(lens_x, lens_x + 3):
                    put(x, 1, GLASS)
                eye = P["eye"]
                put(lens_x + 1, 1, eye, eye)
                put(lens_x, 1, shade(eye, 0.7), shade(eye, 0.5))
                put(lens_x + 2, 1, shade(eye, 0.7), shade(eye, 0.5))
                put(lens_x + 1, 0, (230, 240, 250))
        elif face == "up":
            fill((70, 70, 80))
    elif name == "brim":
        fill(P["helm2"])
        if face == "north":
            for x in range(w):
                put(x, 0, shade(P["helm"], 1.05))
        if face == "up":
            fill(P["helm"])
    elif name == "stalk":
        fill((90, 96, 110))
    elif name == "lamp":
        lamp = (255, 236, 120)
        fill(lamp)
        for y in range(h):
            for x in range(w):
                gp[x, y] = lamp + (255,)
        put(0, 0, (255, 255, 220), (255, 255, 220))
    elif name in ("arm_r", "arm_l"):
        fill(P["suit"])
        for y in range(h - 3, h):
            for x in range(w):
                put(x, y, (70, 70, 80) if y < h - 1 else (56, 56, 64))
        for x in range(w):
            put(x, h - 4, shade(P["suit"], 1.2))
            put(x, 0, shade(P["suit"], 1.1))
        if face == "down":
            fill((56, 56, 64))
        if face == "up":
            fill(P["suit"])
    return img, glow


# ------------------------------------------------------------------------------------------- atlas
def build(skin_index):
    P = SKINS[skin_index]
    rnd = random.Random(1234)  # same layout + same noise for every skin
    atlas = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    glow_atlas = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    uvs = {}
    x, y, row_h = 0, 0, 0
    for bone, name, origin, size in CUBES:
        uvs[name] = {}
        for face in FACES:
            fw, fh = face_size(face, size)
            if x + fw > TEX_W:
                x, y, row_h = 0, y + row_h, 0
            img, glow = paint_face(name, face, fw, fh, P, rnd)
            noise(img, rnd, 0.05)
            atlas.paste(img, (x, y))
            glow_atlas.paste(glow, (x, y))
            uvs[name][face] = (x, y, fw, fh)
            x += fw
            row_h = max(row_h, fh)
    assert y + row_h <= TEX_H, "texture atlas too small"
    return atlas, glow_atlas, uvs


def geo(uvs):
    bones = []
    for name, parent, pivot in BONES:
        b = {"name": name, "pivot": pivot}
        if parent:
            b["parent"] = parent
        cubes = []
        for bone, cname, origin, size in CUBES:
            if bone != name:
                continue
            uv = {}
            for face in FACES:
                ux, uy, fw, fh = uvs[cname][face]
                if face == "down":
                    uv[face] = {"uv": [ux, uy + fh], "uv_size": [fw, -fh]}
                else:
                    uv[face] = {"uv": [ux, uy], "uv_size": [fw, fh]}
            cubes.append({"origin": origin, "size": size, "uv": uv})
        if cubes:
            b["cubes"] = cubes
        bones.append(b)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.helper", "texture_width": TEX_W, "texture_height": TEX_H,
                            "visible_bounds_width": 2, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.5, 0]},
            "bones": bones,
        }],
    }


# ------------------------------------------------------------------------------------------- animations
def kf(pairs):
    """{time: [x, y, z]} with times as strings."""
    return {("%g" % t if t != int(t) else "%.1f" % t): v for t, v in pairs}


def loop(name, length, bones):
    return name, {"loop": True, "animation_length": length, "bones": bones}


def once(name, length, bones):
    return name, {"animation_length": length, "bones": bones}


def wave(amp, period, phase=0.0, offset=0.0, axis=0, steps=8):
    pts = []
    for i in range(steps + 1):
        t = period * i / steps
        v = offset + amp * math.sin(2 * math.pi * i / steps + phase)
        vec = [0.0, 0.0, 0.0]
        vec[axis] = round(v, 2)
        pts.append((round(t, 3), vec))
    return kf(pts)


def animations():
    A = {}
    # idle: breathing, looking around a little, antenna wobble, scarf tail sway
    A.update([loop("animation.helper.idle", 3.0, {
        "body": {"position": wave(0.25, 3.0, axis=1)},
        "head": {"rotation": {"0.0": [0, 0, 0], "1.0": [3, 8, 0], "2.0": [-2, -6, 0], "3.0": [0, 0, 0]}},
        "rightArm": {"rotation": wave(3, 3.0, axis=2, offset=2)},
        "leftArm": {"rotation": wave(3, 3.0, axis=2, offset=-2, phase=math.pi)},
        "antenna": {"rotation": wave(6, 1.5, axis=2)},
        "scarfTail": {"rotation": wave(8, 1.5, axis=0, offset=10)},
    })])
    # walk / run: legs and arms swing, body bobs
    for name, period, leg, arm, lean, bob in (("walk", 0.9, 32, 28, 2, 0.4), ("run", 0.55, 50, 55, 10, 0.8)):
        A.update([loop("animation.helper." + name, period, {
            "rightLeg": {"rotation": wave(leg, period, axis=0)},
            "leftLeg": {"rotation": wave(leg, period, axis=0, phase=math.pi)},
            "rightArm": {"rotation": wave(arm, period, axis=0, phase=math.pi)},
            "leftArm": {"rotation": wave(arm, period, axis=0)},
            "body": {"rotation": {"0.0": [lean, 0, 0]}, "position": wave(bob, period / 2, axis=1)},
            "head": {"rotation": {"0.0": [-lean * 0.6, 0, 0]}},
            "antenna": {"rotation": wave(12, period, axis=0, offset=-8)},
            "scarfTail": {"rotation": wave(14, period / 2, axis=0, offset=35 if name == "run" else 20)},
        })])
    A.update([loop("animation.helper.sit", 2.0, {
        "body": {"position": {"0.0": [0, -7, 0]}},
        "rightLeg": {"rotation": {"0.0": [-85, 8, 0]}, "position": {"0.0": [0, -7, 0]}},
        "leftLeg": {"rotation": {"0.0": [-85, -8, 0]}, "position": {"0.0": [0, -7, 0]}},
        "rightArm": {"rotation": {"0.0": [-30, 0, 8]}},
        "leftArm": {"rotation": {"0.0": [-30, 0, -8]}},
        "head": {"rotation": {"0.0": [4, 0, 0], "1.0": [6, 4, 0], "2.0": [4, 0, 0]}},
    })])
    A.update([loop("animation.helper.ko", 2.0, {
        "root": {"rotation": {"0.0": [-90, 0, 0]}, "position": {"0.0": [0, 2.5, 6]}},
        "head": {"rotation": {"0.0": [0, 20, 0], "1.0": [0, 24, 0], "2.0": [0, 20, 0]}},
        "rightArm": {"rotation": {"0.0": [0, 0, 70]}},
        "leftArm": {"rotation": {"0.0": [0, 0, -70]}},
        "antenna": {"rotation": {"0.0": [60, 0, 0]}},
    })])
    A.update([loop("animation.helper.swim", 1.2, {
        "body": {"rotation": {"0.0": [70, 0, 0]}, "position": {"0.0": [0, 2, 4]}},
        "head": {"rotation": {"0.0": [-60, 0, 0]}},
        "rightArm": {"rotation": wave(70, 1.2, axis=0, offset=-110)},
        "leftArm": {"rotation": wave(70, 1.2, axis=0, offset=-110, phase=math.pi)},
        "rightLeg": {"rotation": wave(20, 0.6, axis=0)},
        "leftLeg": {"rotation": wave(20, 0.6, axis=0, phase=math.pi)},
    })])
    # work: chopping / pick swings with the right arm (loops while it is breaking a block)
    A.update([loop("animation.helper.work", 0.5, {
        "rightArm": {"rotation": {"0.0": [-150, 0, 0], "0.2": [-40, 0, 0], "0.25": [-30, 0, 0], "0.5": [-150, 0, 0]}},
        "body": {"rotation": {"0.0": [-4, 0, 0], "0.2": [6, 0, 0], "0.5": [-4, 0, 0]}},
        "head": {"rotation": {"0.0": [-6, 0, 0], "0.2": [8, 0, 0], "0.5": [-6, 0, 0]}},
    })])
    A.update([once("animation.helper.swing", 0.3, {
        "rightArm": {"rotation": {"0.0": [-120, 0, 0], "0.15": [-20, 0, 0], "0.3": [0, 0, 0]}},
    })])
    A.update([once("animation.helper.attack", 0.35, {
        "rightArm": {"rotation": {"0.0": [-160, -20, 0], "0.15": [-30, 20, 0], "0.35": [0, 0, 0]}},
        "body": {"rotation": {"0.0": [0, 20, 0], "0.15": [6, -25, 0], "0.35": [0, 0, 0]}},
    })])
    A.update([once("animation.helper.place", 0.3, {
        "rightArm": {"rotation": {"0.0": [-70, 0, 0], "0.15": [-90, 0, 0], "0.3": [0, 0, 0]}},
    })])
    A.update([once("animation.helper.eat", 1.0, {
        "rightArm": {"rotation": {"0.0": [-110, 30, 0], "0.25": [-100, 30, 0], "0.5": [-110, 30, 0], "0.75": [-100, 30, 0], "1.0": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [6, 0, 0], "0.25": [10, 0, 0], "0.5": [6, 0, 0], "0.75": [10, 0, 0], "1.0": [0, 0, 0]}},
    })])
    A.update([once("animation.helper.wave", 1.2, {
        "leftArm": {"rotation": {"0.0": [0, 0, 0], "0.15": [0, 0, -160], "0.35": [0, 0, -130], "0.55": [0, 0, -165], "0.75": [0, 0, -130], "0.95": [0, 0, -160], "1.2": [0, 0, 0]}},
        "head": {"rotation": {"0.0": [0, 0, 0], "0.3": [0, 0, -8], "0.9": [0, 0, -8], "1.2": [0, 0, 0]}},
    })])
    A.update([once("animation.helper.cheer", 1.0, {
        "rightArm": {"rotation": {"0.0": [0, 0, 0], "0.2": [0, 0, 160], "0.5": [0, 0, 140], "0.8": [0, 0, 160], "1.0": [0, 0, 0]}},
        "leftArm": {"rotation": {"0.0": [0, 0, 0], "0.2": [0, 0, -160], "0.5": [0, 0, -140], "0.8": [0, 0, -160], "1.0": [0, 0, 0]}},
        "root": {"position": {"0.0": [0, 0, 0], "0.2": [0, 3, 0], "0.4": [0, 0, 0], "0.6": [0, 3, 0], "0.8": [0, 0, 0]}},
        "antenna": {"rotation": {"0.0": [0, 0, 0], "0.2": [-20, 0, 0], "0.4": [20, 0, 0], "0.6": [-20, 0, 0], "1.0": [0, 0, 0]}},
    })])
    A.update([once("animation.helper.shoot", 0.8, {
        "rightArm": {"rotation": {"0.0": [-90, 10, 0], "0.6": [-90, 10, 0], "0.8": [0, 0, 0]}},
        "leftArm": {"rotation": {"0.0": [-90, -30, 0], "0.5": [-90, -10, 0], "0.6": [-90, -30, 0], "0.8": [0, 0, 0]}},
        "body": {"rotation": {"0.0": [0, 20, 0], "0.8": [0, 0, 0]}},
    })])
    return {"format_version": "1.8.0", "animations": A}


def main():
    os.makedirs(os.path.join(RES, "geo", "entity"), exist_ok=True)
    os.makedirs(os.path.join(RES, "textures", "entity", "helper"), exist_ok=True)
    os.makedirs(os.path.join(RES, "animations", "entity"), exist_ok=True)
    uvs = None
    for i in range(len(SKINS)):
        atlas, glow, uvs = build(i)
        atlas.save(os.path.join(RES, "textures", "entity", "helper", "helper_%d.png" % i))
        glow.save(os.path.join(RES, "textures", "entity", "helper", "helper_%d_glow.png" % i))
    with open(os.path.join(RES, "geo", "entity", "helper.geo.json"), "w") as f:
        json.dump(geo(uvs), f, indent=1)
    with open(os.path.join(RES, "animations", "entity", "helper.animation.json"), "w") as f:
        json.dump(animations(), f, indent=1)
    print("helper model, %d skins and %d animations written" % (len(SKINS), len(animations()["animations"])))
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        sys.path.insert(0, HERE)
        import georender
        model = georender.Model(os.path.join(RES, "geo", "entity", "helper.geo.json"))
        anims_path = os.path.join(RES, "animations", "entity", "helper.animation.json")
        for i in range(len(SKINS)):
            tex = Image.open(os.path.join(RES, "textures", "entity", "helper", "helper_%d.png" % i)).convert("RGBA")
            for yaw in (0, 140):
                img = georender.render(model, tex, size=320, yaw=yaw)
                img.save(os.path.join(out, "skin%d_yaw%d.png" % (i, yaw)))
        tex = Image.open(os.path.join(RES, "textures", "entity", "helper", "helper_0.png")).convert("RGBA")
        for name, t in (("walk", 0.2), ("work", 0.15), ("sit", 0.5), ("ko", 0.5), ("wave", 0.4), ("cheer", 0.3), ("swim", 0.3)):
            pose = georender.load_anim(anims_path, "animation.helper." + name)
            img = georender.render(model, tex, pose=georender.pose_from_anims([pose], t), size=320, yaw=30)
            img.save(os.path.join(out, "anim_%s.png" % name))


if __name__ == "__main__":
    main()
