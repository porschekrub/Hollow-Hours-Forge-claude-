#!/usr/bin/env python3
"""Generates the Helper Buddy look: GeckoLib model, 18 outfits (6 colour suits + 12 themed ones, with glow maps) and all animations.

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
# themed outfits: the same body, other colours, details painted on top, and parts that are left out ("hide")
SKINS += [
    dict(theme="knight", suit=(172, 178, 192), suit2=(112, 118, 134), helm=(190, 196, 210), helm2=(126, 132, 148), scarf=(170, 34, 44), pants=(96, 100, 112), eye=(255, 120, 70)),
    dict(theme="ninja", suit=(36, 36, 46), suit2=(22, 22, 30), helm=(30, 30, 40), helm2=(18, 18, 26), scarf=(190, 30, 40), pants=(30, 30, 38), eye=(255, 255, 255),
         hide=("goggles", "brim", "stalk", "lamp", "roll")),
    dict(theme="chef", suit=(244, 244, 240), suit2=(204, 204, 200), helm=(250, 250, 248), helm2=(214, 214, 212), scarf=(214, 48, 48), pants=(70, 72, 80), eye=(255, 255, 255),
         hide=("goggles", "brim", "pack", "roll", "stalk", "lamp")),
    dict(theme="pirate", suit=(236, 232, 220), suit2=(92, 52, 36), helm=(34, 30, 34), helm2=(20, 18, 20), scarf=(196, 40, 40), pants=(104, 66, 40), eye=(255, 255, 255),
         hide=("goggles", "stalk", "lamp")),
    dict(theme="astronaut", suit=(236, 238, 244), suit2=(176, 182, 196), helm=(244, 246, 250), helm2=(186, 192, 206), scarf=(240, 130, 40), pants=(226, 228, 236), eye=(120, 220, 255),
         hide=("brim", "roll")),
    dict(theme="wizard", suit=(98, 58, 164), suit2=(64, 36, 116), helm=(84, 48, 150), helm2=(56, 30, 106), scarf=(240, 196, 60), pants=(84, 50, 142), eye=(255, 230, 120),
         hide=("goggles", "pack", "roll")),
    dict(theme="santa", suit=(204, 38, 38), suit2=(146, 22, 26), helm=(204, 38, 38), helm2=(150, 24, 28), scarf=(246, 246, 244), pants=(190, 34, 36), eye=(255, 255, 255),
         hide=("goggles",)),
    dict(theme="farmer", suit=(196, 60, 52), suit2=(60, 98, 170), helm=(230, 198, 112), helm2=(186, 150, 70), scarf=(204, 52, 44), pants=(62, 100, 172), eye=(255, 255, 255),
         hide=("goggles", "stalk", "lamp", "pack", "roll")),
    dict(theme="robot", suit=(152, 162, 176), suit2=(98, 108, 124), helm=(170, 180, 194), helm2=(110, 120, 136), scarf=(52, 56, 66), pants=(120, 130, 146), eye=(90, 240, 255),
         hide=("brim", "roll", "tail")),
    dict(theme="miner", suit=(96, 112, 132), suit2=(150, 96, 46), helm=(242, 198, 44), helm2=(196, 148, 22), scarf=(128, 124, 116), pants=(140, 90, 44), eye=(255, 244, 170),
         hide=("goggles", "stalk", "lamp")),
    dict(theme="doctor", suit=(240, 244, 248), suit2=(196, 204, 214), helm=(74, 172, 170), helm2=(46, 126, 126), scarf=(62, 66, 76), pants=(74, 164, 164), eye=(255, 255, 255),
         hide=("goggles", "brim", "stalk", "lamp", "pack", "roll", "tail")),
    dict(theme="police", suit=(34, 48, 98), suit2=(20, 30, 66), helm=(30, 42, 88), helm2=(16, 18, 26), scarf=(26, 36, 78), pants=(30, 40, 82), eye=(255, 255, 255),
         hide=("pack", "roll", "tail")),
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
    P0 = P
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
        sleeve = mix(P["suit"], P["suit2"], 0.45)
        P = dict(P, suit=sleeve)
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
    P = P0
    theme = P.get("theme")
    if theme:
        if name in P.get("hide", ()):
            return Image.new("RGBA", (w, h), (0, 0, 0, 0)), Image.new("RGBA", (w, h), (0, 0, 0, 0))
        THEMES[theme](Ctx(name, face, w, h, px, gp, P))
    return img, glow


# ------------------------------------------------------------------------------------------- themed outfits
BLACK = (24, 24, 28)
RED = (210, 44, 44)
STEEL = (150, 156, 170)
HAIR = (96, 62, 36)


class Ctx:
    """One face of one cube being painted (y = 0 is the top row)."""

    def __init__(self, name, face, w, h, px, gp, P):
        self.name, self.face, self.w, self.h, self.px, self.gp, self.P = name, face, w, h, px, gp, P

    def put(self, x, y, c, g=None):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = tuple(c) + (255,)
            self.gp[x, y] = (tuple(g) + (255,)) if g else (0, 0, 0, 0)

    def rect(self, x0, y0, x1, y1, c, g=None):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c, g)

    def fill(self, c, g=None):
        self.rect(0, 0, self.w - 1, self.h - 1, c, g)

    def row(self, y, c, g=None):
        self.rect(0, y, self.w - 1, y, c, g)

    def col(self, x, c, g=None):
        self.rect(x, 0, x, self.h - 1, c, g)

    def is_(self, name, face=None):
        return self.name == name and (face is None or self.face == face)

    def face_plain(self, hat_rows=4, eyes=(60, 40, 30), beard=None, mask=None):
        """The front of the head with no goggles: forehead, two eyes, cheeks, a smile (or a beard / a mask)."""
        self.rect(1, hat_rows, self.w - 2, self.h - 1, SKIN)
        self.put(3, 5, eyes)
        self.put(6, 5, eyes)
        self.put(3, 4, shade(SKIN, 0.9))
        self.put(6, 4, shade(SKIN, 0.9))
        self.put(2, 7, (240, 150, 150))
        self.put(7, 7, (240, 150, 150))
        self.put(4, 8, (110, 60, 50))
        self.put(5, 8, (110, 60, 50))
        self.rect(1, self.h - 1, self.w - 2, self.h - 1, SKIN2)
        if beard:
            self.rect(1, 7, self.w - 2, self.h - 1, beard)
            self.put(1, 6, beard)
            self.put(self.w - 2, 6, beard)
            self.put(4, 7, (150, 70, 60))
            self.put(5, 7, (150, 70, 60))
            self.rect(3, 6, 6, 6, shade(beard, 0.93))
        if mask:
            self.rect(1, 6, self.w - 2, self.h - 1, mask)
            self.rect(2, 7, self.w - 3, 7, shade(mask, 0.9))


def theme_knight(c):
    P = c.P
    if c.is_("head"):
        c.fill(P["helm"])
        for y in range(0, c.h, 3):
            c.row(y, shade(P["helm"], 1.08))
        if c.face == "north":
            c.rect(4, 6, 5, 9, P["helm2"])          # the nose guard down the middle
            for x in (2, 7):
                for y in (7, 8):
                    c.put(x, y, BLACK)                # breathing holes
        if c.face in ("east", "west"):
            c.rect(3, 4, 5, 6, P["helm2"])
        if c.face == "up":
            c.rect(4, 0, 5, c.h - 1, P["scarf"])    # the crest
    elif c.is_("goggles"):
        c.fill(P["helm2"])
        if c.face == "north":
            c.rect(1, 1, c.w - 2, 1, BLACK)          # the visor slit, with eyes glowing in it
            for x in (2, 6):
                c.put(x, 1, P["eye"], P["eye"])
    elif c.is_("brim"):
        c.fill(P["helm2"])
    elif c.is_("stalk") or c.is_("lamp"):
        c.fill(P["scarf"])                            # a red plume
        if c.is_("lamp"):
            c.put(0, 0, shade(P["scarf"], 1.3))
    elif c.is_("torso"):
        c.fill(P["suit"])
        if c.face == "north":
            c.rect(0, 0, c.w - 1, 0, shade(P["suit"], 1.15))
            c.rect(3, 1, 4, 7, GOLD)                 # a golden cross on the breastplate
            c.rect(1, 3, 6, 4, GOLD)
            c.rect(3, 3, 4, 4, shade(GOLD, 1.15))
        else:
            for y in range(2, c.h, 3):
                c.row(y, P["suit2"])
    elif c.name in ("arm_r", "arm_l"):
        c.fill(P["suit"])
        c.rect(0, 0, c.w - 1, 2, shade(P["suit"], 1.15))   # shoulder plates
        c.row(3, P["suit2"])
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, P["suit2"])
        c.row(c.h - 3, shade(P["suit2"], 1.3))
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.rect(0, 3, c.w - 1, 4, P["suit"])               # knee plates
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, P["suit2"])
        c.row(c.h - 1, shade(P["suit2"], 0.7))
    elif c.is_("pack"):
        c.fill(P["suit2"])
        if c.face == "south":                             # a round shield on the back
            c.rect(1, 1, c.w - 2, c.h - 2, P["scarf"])
            c.rect(2, 2, c.w - 3, c.h - 3, shade(P["scarf"], 1.2))
            c.rect(2, 3, 3, 3, GOLD)


def theme_ninja(c):
    P = c.P
    if c.is_("head"):
        c.fill(P["helm"])
        if c.face == "north":
            c.rect(1, 4, c.w - 2, 5, SKIN)                 # only the eyes show
            c.put(3, 5, BLACK)
            c.put(6, 5, BLACK)
            c.put(2, 4, shade(SKIN, 0.85))
            c.put(7, 4, shade(SKIN, 0.85))
            c.rect(0, 3, c.w - 1, 3, P["scarf"])           # the headband
        elif c.face in ("east", "west", "south"):
            c.row(3, P["scarf"])
        if c.face == "south":
            c.rect(4, 4, 5, 6, P["scarf"])                 # the knot
    elif c.is_("torso"):
        c.fill(P["suit"])
        if c.face == "north":
            for i in range(4):                             # the wrap-over front
                c.put(2 + i, 1 + i, shade(P["suit"], 1.5))
                c.put(5 - i + 1, 1 + i, shade(P["suit"], 1.25))
            c.rect(0, c.h - 2, c.w - 1, c.h - 2, P["scarf"])
    elif c.is_("belt"):
        c.fill(P["scarf"])
        if c.face == "north":
            c.put(c.w // 2, 0, shade(P["scarf"], 0.7))
    elif c.is_("pack"):
        c.fill(P["suit2"])
        if c.face == "south":                              # a sword across the back
            for i in range(min(c.w, c.h)):
                c.put(i, i, STEEL)
            c.put(0, 0, GOLD)
            c.put(1, 1, (70, 44, 30))
    elif c.name in ("arm_r", "arm_l"):
        c.fill(P["suit"])
        c.rect(0, c.h - 4, c.w - 1, c.h - 1, P["suit2"])
        c.row(c.h - 4, P["scarf"])
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, P["suit2"])
        c.row(c.h - 4, shade(P["pants"], 1.6))


def theme_chef(c):
    P = c.P
    if c.is_("head"):
        c.fill(HAIR)
        c.rect(0, 0, c.w - 1, 3, P["helm"])                 # the tall white hat
        c.row(3, P["helm2"])
        if c.face == "north":
            c.face_plain()
            c.rect(3, 7, 6, 7, HAIR)                        # a moustache
            c.put(4, 8, (110, 60, 50))
        if c.face == "up":
            c.fill(P["helm"])
            for x in range(1, c.w, 3):
                c.col(x, P["helm2"])
        if c.face == "down":
            c.fill(SKIN2)
    elif c.is_("stalk") or c.is_("lamp"):
        c.fill(P["helm"])                                   # the puff on top of the hat
        if c.is_("lamp"):
            c.put(1, 1, P["helm2"])
    elif c.is_("torso"):
        c.fill(P["suit"])
        if c.face == "north":
            for y in (1, 3, 5):                             # two rows of buttons
                c.put(2, y, (60, 60, 70))
                c.put(5, y, (60, 60, 70))
            c.rect(0, 6, c.w - 1, c.h - 1, shade(P["suit"], 0.93))   # the apron
        c.row(0, shade(P["suit"], 1.04))
    elif c.is_("belt"):
        c.fill(shade(P["suit"], 0.9))
    elif c.name in ("arm_r", "arm_l"):
        c.fill(P["suit"])
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, SKIN)
        c.row(c.h - 4, P["suit2"])
    elif c.name in ("leg_r", "leg_l"):
        for y in range(c.h):
            for x in range(c.w):
                c.put(x, y, P["pants"] if (x + y) % 2 else shade(P["pants"], 1.5))
        c.rect(0, c.h - 2, c.w - 1, c.h - 1, BLACK)


def theme_pirate(c):
    P = c.P
    if c.is_("head"):
        c.fill(P["scarf"])                                  # a red bandana under the hat
        c.rect(0, 0, c.w - 1, 2, P["helm"])
        if c.face == "north":
            c.face_plain()
            c.rect(5, 4, 7, 5, BLACK)                       # the eyepatch and its strap
            c.row(3, P["scarf"])
            c.rect(1, 4, 4, 4, SKIN)
            c.put(8, 4, BLACK)
            c.rect(4, 0, 5, 1, WHITE)                       # skull on the hat
            c.put(4, 2, WHITE)
            c.put(5, 2, WHITE)
            c.rect(2, 8, 7, 9, shade(HAIR, 0.8))            # stubble
            c.put(4, 8, (110, 60, 50))
            c.put(5, 8, (110, 60, 50))
        if c.face in ("east", "west"):
            c.rect(0, 6, c.w - 1, c.h - 1, SKIN)
            c.put(4, 7, GOLD)                               # an earring
        if c.face == "south":
            c.rect(0, 6, c.w - 1, c.h - 1, HAIR)
        if c.face == "up":
            c.fill(P["helm"])
        if c.face == "down":
            c.fill(SKIN2)
    elif c.is_("brim"):
        c.fill(P["helm"])
        if c.face in ("north", "south", "east", "west"):
            c.fill(GOLD)                                    # gold trim round the hat
    elif c.is_("torso"):
        for y in range(c.h):
            c.row(y, P["suit"] if y % 2 == 0 else RED)      # a striped shirt
        if c.face == "north":
            c.rect(0, 0, 1, c.h - 1, P["suit2"])            # an open vest
            c.rect(c.w - 2, 0, c.w - 1, c.h - 1, P["suit2"])
            c.put(1, 2, GOLD)
            c.put(c.w - 2, 2, GOLD)
        else:
            c.fill(P["suit2"])
    elif c.is_("belt"):
        c.fill(BLACK)
        if c.face == "north":
            c.rect(c.w // 2 - 1, 0, c.w // 2, 1, GOLD)
    elif c.name in ("arm_r", "arm_l"):
        for y in range(c.h):
            c.row(y, P["suit"] if y % 2 == 0 else RED)
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, SKIN)
        if c.name == "arm_l":
            c.rect(0, c.h - 3, c.w - 1, c.h - 1, STEEL)     # a hook hand
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.rect(0, c.h - 4, c.w - 1, c.h - 1, BLACK)
        c.row(c.h - 4, shade(LEATHER, 1.1))
    elif c.is_("pack"):
        c.fill(LEATHER)
        if c.face == "south":
            c.rect(1, 1, c.w - 2, c.h - 2, shade(LEATHER, 1.2))
            c.put(c.w // 2, 2, GOLD)


def theme_astronaut(c):
    P = c.P
    glass, glass2 = (28, 44, 84), (60, 100, 170)
    if c.is_("head"):
        c.fill(P["helm"])
        if c.face == "north":
            c.rect(1, 2, c.w - 2, 8, glass)                 # the big visor
            c.rect(2, 3, 3, 3, glass2)
            c.put(2, 4, glass2)
            c.rect(6, 7, 7, 7, shade(glass, 1.5))
            c.row(9, P["helm2"])
        if c.face in ("east", "west"):
            c.rect(3, 5, 5, 7, P["helm2"])
            c.put(4, 6, RED)
        if c.face == "south":
            c.rect(3, 6, 6, 8, P["helm2"])
    elif c.is_("goggles"):
        c.fill(glass)                                       # the visor bulges out
        if c.face == "north":
            c.rect(1, 0, 2, 0, glass2)
            c.put(6, 1, P["eye"], shade(P["eye"], 0.6))
            c.put(7, 2, shade(glass, 1.5))
        if c.face == "up":
            c.fill(P["helm2"])
    elif c.is_("stalk"):
        c.fill(STEEL)
    elif c.is_("lamp"):
        c.fill((255, 70, 60), (255, 70, 60))
    elif c.is_("torso"):
        c.fill(P["suit"])
        if c.face == "north":
            c.rect(2, 2, 5, 5, P["suit2"])                  # the control panel
            c.put(2, 2, (255, 80, 70), (255, 80, 70))
            c.put(3, 2, (90, 230, 120), (90, 230, 120))
            c.put(5, 2, (90, 170, 255), (90, 170, 255))
            c.rect(2, 4, 5, 4, BLACK)
            c.rect(6, 1, 7, 1, RED)                         # a flag patch
            c.put(6, 2, WHITE)
            c.put(7, 2, (60, 90, 190))
        c.row(c.h - 1, P["suit2"])
    elif c.is_("belt"):
        c.fill(P["suit2"])
    elif c.is_("pack"):
        c.fill(P["suit"])
        if c.face == "south":
            c.rect(1, 1, c.w - 2, c.h - 2, P["suit2"])
            c.put(1, 1, (255, 80, 70), (255, 80, 70))
            c.rect(2, 3, 3, 5, STEEL)
        if c.face == "up":
            c.fill(P["suit2"])
    elif c.name in ("arm_r", "arm_l"):
        c.fill(P["suit"])
        c.row(3, P["scarf"])
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, P["suit2"])
        c.row(c.h - 4, P["scarf"])
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.row(3, P["scarf"])
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, P["suit2"])
        c.row(c.h - 1, shade(P["suit2"], 0.7))


def stars(c, n, seed):
    r = random.Random(seed)
    for _ in range(n):
        c.put(r.randrange(c.w), r.randrange(c.h), GOLD)


def theme_wizard(c):
    P = c.P
    beard = (236, 236, 232)
    if c.is_("head"):
        c.fill((200, 200, 196))                             # long grey hair
        c.rect(0, 0, c.w - 1, 3, P["helm"])
        c.row(3, P["scarf"])                                # the hat band
        if c.face == "north":
            c.face_plain(beard=beard)
            c.rect(2, 4, 3, 4, beard)                       # bushy eyebrows
            c.rect(6, 4, 7, 4, beard)
        if c.face == "up":
            c.fill(P["helm"])
        if c.face == "down":
            c.fill(beard)
        if c.face != "north" and c.face not in ("up", "down"):
            stars(c, 2, hash(c.face) % 97)
            c.row(3, P["scarf"])
    elif c.is_("brim"):
        c.fill(P["helm2"])
        if c.face == "up":
            c.fill(P["helm"])
    elif c.is_("stalk"):
        c.fill(P["helm"])                                   # the point of the hat
    elif c.is_("lamp"):
        c.fill(GOLD, GOLD)                                  # a glowing star on its tip
    elif c.is_("torso"):
        c.fill(P["suit"])
        stars(c, 3, hash(c.face) % 89 + 5)
        if c.face == "north":
            c.rect(3, 0, 4, 4, beard)                       # the beard hangs over the robe
            c.put(3, 5, beard)
            c.rect(0, c.h - 1, c.w - 1, c.h - 1, P["scarf"])
    elif c.is_("belt"):
        c.fill(P["scarf"])
    elif c.name in ("arm_r", "arm_l"):
        c.fill(P["suit"])
        stars(c, 1, hash(c.face + c.name) % 83)
        c.rect(0, c.h - 2, c.w - 1, c.h - 1, SKIN)
        c.row(c.h - 3, P["scarf"])
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.row(c.h - 2, P["scarf"])
        c.row(c.h - 1, P["suit2"])
    elif c.is_("scarf") or c.is_("tail"):
        c.fill(P["suit2"])
        c.row(0, P["scarf"])


def theme_santa(c):
    P = c.P
    fur, beard = (248, 248, 246), (240, 240, 236)
    if c.is_("head"):
        c.fill(beard)                                       # white hair
        c.rect(0, 0, c.w - 1, 2, P["helm"])
        c.row(3, fur)                                       # the fur band of the hat
        if c.face == "north":
            c.face_plain(beard=beard)
            c.put(4, 6, (230, 120, 110))                    # a red nose
            c.put(5, 6, (230, 120, 110))
        if c.face == "up":
            c.fill(P["helm"])
        if c.face == "down":
            c.fill(beard)
    elif c.is_("brim"):
        c.fill(fur)
    elif c.is_("stalk"):
        c.fill(P["helm"])
    elif c.is_("lamp"):
        c.fill(fur)                                         # the pom-pom
    elif c.is_("torso"):
        c.fill(P["suit"])
        if c.face == "north":
            c.rect(3, 0, 4, c.h - 1, fur)
            c.put(3, 2, BLACK)
            c.put(3, 5, BLACK)
        c.row(c.h - 1, fur)
    elif c.is_("belt"):
        c.fill(BLACK)
        if c.face == "north":
            c.rect(c.w // 2 - 1, 0, c.w // 2 + 1, 1, GOLD)
            c.put(c.w // 2, 0, BLACK)
    elif c.name in ("arm_r", "arm_l"):
        c.fill(P["suit"])
        c.rect(0, c.h - 2, c.w - 1, c.h - 1, (40, 110, 60))   # green mittens
        c.row(c.h - 3, fur)
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, BLACK)
        c.row(c.h - 4, fur)
    elif c.is_("pack") or c.is_("roll"):
        c.fill((150, 100, 56))                              # the sack of presents
        if c.face == "south" and c.is_("pack"):
            c.rect(1, 2, 2, 3, (60, 150, 80))
            c.rect(3, 4, 4, 5, (70, 110, 210))
            c.put(4, 1, GOLD)
            c.row(0, shade((150, 100, 56), 0.75))


def theme_farmer(c):
    P = c.P
    denim, plaid, plaid2 = P["suit2"], P["suit"], shade(P["suit"], 0.7)

    def check(x0, y0, x1, y1):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                c.put(x, y, plaid2 if (x % 2 == 0) != (y % 2 == 0) else plaid)

    if c.is_("head"):
        c.fill(HAIR)
        c.rect(0, 0, c.w - 1, 3, P["helm"])                 # a straw hat
        c.row(3, LEATHER)
        for x in range(0, c.w, 2):
            c.put(x, 1, P["helm2"])
        if c.face == "north":
            c.face_plain()
            for p in ((2, 6), (3, 7), (7, 6), (6, 7)):      # freckles
                c.put(p[0], p[1], shade(SKIN, 0.82))
            c.put(5, 9, (120, 180, 70))                     # a straw in the mouth
        if c.face == "up":
            c.fill(P["helm"])
            for x in range(0, c.w, 2):
                c.col(x, P["helm2"])
        if c.face == "down":
            c.fill(SKIN2)
    elif c.is_("brim"):
        c.fill(P["helm2"])
        if c.face == "up":
            c.fill(P["helm"])
    elif c.is_("torso"):
        check(0, 0, c.w - 1, c.h - 1)
        if c.face == "north":
            c.rect(1, 3, c.w - 2, c.h - 1, denim)            # the bib of the overalls
            c.rect(1, 0, 1, 2, denim)
            c.rect(c.w - 2, 0, c.w - 2, 2, denim)
            c.put(1, 3, GOLD)
            c.put(c.w - 2, 3, GOLD)
            c.rect(3, 5, 4, 6, shade(denim, 1.2))
        elif c.face == "south":
            c.rect(1, 0, 1, c.h - 1, denim)
            c.rect(c.w - 2, 0, c.w - 2, c.h - 1, denim)
            c.rect(0, 5, c.w - 1, c.h - 1, denim)
        else:
            c.rect(0, 5, c.w - 1, c.h - 1, denim)
    elif c.is_("belt"):
        c.fill(denim)
    elif c.name in ("arm_r", "arm_l"):
        check(0, 0, c.w - 1, c.h - 1)
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, SKIN)
        c.rect(0, c.h - 2, c.w - 1, c.h - 1, shade(LEATHER, 1.3))   # work gloves
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, LEATHER)
        c.row(c.h - 1, LEATHER2)
        if c.face == "north":
            c.put(1, 3, shade(P["pants"], 1.25))             # a patch on the knee
            c.put(2, 3, shade(P["pants"], 1.25))


def theme_robot(c):
    P = c.P
    line = P["suit2"]
    if c.is_("head"):
        c.fill(P["helm"])
        c.row(0, shade(P["helm"], 1.1))
        c.row(c.h - 1, P["helm2"])
        if c.face == "north":
            c.rect(2, 7, 7, 8, BLACK)                        # a speaker grille for a mouth
            for x in (3, 5, 7):
                c.put(x, 7, STEEL)
                c.put(x - 1, 8, STEEL)
            for p in ((0, 0), (c.w - 1, 0), (0, c.h - 1), (c.w - 1, c.h - 1)):
                c.put(p[0], p[1], line)                      # bolts
        if c.face in ("east", "west"):
            c.rect(3, 4, 5, 6, line)                         # round "ears"
            c.put(4, 5, P["eye"], P["eye"])
        if c.face == "south":
            c.rect(2, 2, 7, 6, line)
            for y in (3, 5):
                c.rect(3, y, 6, y, BLACK)
    elif c.is_("goggles"):
        c.fill(BLACK)                                        # one wide visor, two bright eyes
        if c.face == "north":
            for x0 in (1, 5):
                c.rect(x0, 1, x0 + 2, 1, P["eye"], P["eye"])
                c.put(x0 + 1, 0, shade(P["eye"], 0.7), shade(P["eye"], 0.5))
    elif c.is_("stalk"):
        c.fill(STEEL)
    elif c.is_("lamp"):
        c.fill((255, 70, 60), (255, 70, 60))
    elif c.is_("torso"):
        c.fill(P["suit"])
        c.row(0, shade(P["suit"], 1.1))
        if c.face == "north":
            c.rect(1, 1, 6, 5, line)                         # the chest panel
            c.rect(2, 2, 5, 2, BLACK)
            c.put(2, 4, (255, 80, 70), (255, 80, 70))
            c.put(3, 4, (255, 210, 70), (255, 210, 70))
            c.put(4, 4, (90, 230, 120), (90, 230, 120))
            c.put(5, 2, P["eye"], P["eye"])
            c.rect(1, 7, 6, 7, line)
        if c.face == "south":
            c.rect(2, 1, 5, 6, line)
            for y in (2, 4):
                c.rect(3, y, 4, y, BLACK)
    elif c.is_("belt"):
        c.fill(line)
        if c.face == "north":
            c.put(c.w // 2, 0, (255, 210, 70), (255, 210, 70))
    elif c.is_("scarf"):
        c.fill(P["scarf"])                                   # the neck joint
    elif c.is_("pack"):
        c.fill(line)
        if c.face == "south":
            c.rect(1, 1, c.w - 2, c.h - 2, P["suit"])
            c.rect(2, 2, 3, 2, P["eye"], P["eye"])
            for y in (4, 5):
                c.rect(1, y, c.w - 2, y, BLACK if y == 4 else P["suit"])
    elif c.name in ("arm_r", "arm_l", "leg_r", "leg_l"):
        c.fill(P["suit"] if c.name.startswith("arm") else P["pants"])
        for y in (2, 5):
            c.row(y, line)                                   # joints
        c.rect(0, c.h - 2, c.w - 1, c.h - 1, P["helm2"])
        c.row(c.h - 1, BLACK)


def theme_miner(c):
    P = c.P
    shirt, overall = P["suit"], P["suit2"]
    soot = (70, 60, 54)
    if c.is_("head"):
        c.fill(HAIR)
        c.rect(0, 0, c.w - 1, 3, P["helm"])                 # the hard hat
        c.row(3, P["helm2"])
        if c.face == "north":
            c.face_plain()
            c.rect(4, 1, 5, 2, (255, 250, 200), (255, 244, 170))    # the headlamp, lit
            c.put(3, 1, STEEL)
            c.put(6, 1, STEEL)
            c.put(2, 6, soot)                                # soot on the cheeks
            c.put(7, 8, soot)
            c.put(1, 8, soot)
            c.rect(3, 7, 6, 7, HAIR)                         # a moustache
        else:
            c.row(2, BLACK)                                  # the lamp's strap
        if c.face == "up":
            c.fill(P["helm"])
            c.rect(4, 0, 5, c.h - 1, shade(P["helm"], 1.12))
        if c.face == "down":
            c.fill(SKIN2)
    elif c.is_("brim"):
        c.fill(P["helm2"])
        if c.face == "up":
            c.fill(P["helm"])
    elif c.is_("torso"):
        c.fill(shirt)
        if c.face == "north":
            c.rect(1, 3, c.w - 2, c.h - 1, overall)          # dungarees
            c.rect(1, 0, 1, 2, overall)
            c.rect(c.w - 2, 0, c.w - 2, 2, overall)
            c.put(1, 3, STEEL)
            c.put(c.w - 2, 3, STEEL)
            c.put(3, 6, soot)
            c.put(5, 4, soot)
        else:
            c.rect(0, 4, c.w - 1, c.h - 1, overall)
    elif c.is_("belt"):
        c.fill(LEATHER2)
        if c.face == "north":
            c.put(2, 0, STEEL)
            c.put(6, 0, GOLD)
    elif c.is_("pack"):
        c.fill(LEATHER)
        if c.face == "south":                                # a pickaxe strapped on
            c.rect(c.w // 2, 0, c.w // 2, c.h - 1, (130, 90, 50))
            c.rect(1, 1, c.w - 2, 1, STEEL)
            c.put(0, 2, STEEL)
            c.put(c.w - 1, 2, STEEL)
        if c.face == "up":
            c.fill(LEATHER2)
    elif c.is_("roll"):
        c.fill((90, 80, 70))                                 # a coil of rope
        for x in range(0, c.w, 2):
            c.col(x, (130, 112, 90))
    elif c.name in ("arm_r", "arm_l"):
        c.fill(shirt)
        c.rect(0, 2, c.w - 1, 3, shade(shirt, 1.15))         # rolled-up sleeves
        c.rect(0, 4, c.w - 1, c.h - 3, SKIN)
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, shade(LEATHER, 1.2))
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, (60, 60, 66))
        c.row(c.h - 1, BLACK)
        if c.face == "north":
            c.put(1, 2, soot)


def theme_doctor(c):
    P = c.P
    scrubs, mask = P["helm"], (150, 206, 226)
    if c.is_("head"):
        c.fill(HAIR)
        c.rect(0, 0, c.w - 1, 3, scrubs)                    # the surgeon's cap
        c.row(3, P["helm2"])
        if c.face == "north":
            c.face_plain(mask=mask)
            c.rect(2, 5, 4, 5, (60, 64, 74))                 # glasses
            c.rect(6, 5, 8, 5, (60, 64, 74))
            c.put(5, 5, (60, 64, 74))
            c.put(3, 5, (200, 230, 250))
            c.put(7, 5, (200, 230, 250))
            c.rect(4, 1, 5, 2, (220, 226, 236))              # the head mirror
            c.put(4, 1, WHITE)
        if c.face in ("east", "west"):
            c.rect(0, 6, c.w - 1, 6, mask)                   # the mask's strings
        if c.face == "up":
            c.fill(scrubs)
        if c.face == "down":
            c.fill(mask)
    elif c.is_("lamp"):
        c.fill((220, 226, 236))
    elif c.is_("torso"):
        c.fill(P["suit"])
        if c.face == "north":
            c.rect(3, 0, 4, 2, scrubs)                       # scrubs under the open coat
            c.rect(3, 3, 3, c.h - 1, P["suit2"])
            c.rect(5, 5, 6, 6, P["suit2"])                   # a pocket with a pen
            c.put(6, 4, (60, 90, 200))
            c.rect(1, 2, 1, 4, RED)                          # a red cross
            c.rect(0, 3, 2, 3, RED)
        c.row(c.h - 1, P["suit2"])
    elif c.is_("belt"):
        c.fill(P["suit"])
    elif c.is_("scarf"):
        c.fill(P["scarf"])                                   # a stethoscope round the neck
        if c.face == "north":
            c.fill(P["suit"])
            c.put(1, 0, P["scarf"])
            c.put(1, 1, P["scarf"])
            c.put(c.w - 2, 0, P["scarf"])
            c.put(c.w - 2, 1, STEEL)
        if c.face == "up":
            c.fill(P["suit"])
            c.row(0, P["scarf"])
            c.row(c.h - 1, P["scarf"])
            c.col(0, P["scarf"])
            c.col(c.w - 1, P["scarf"])
    elif c.name in ("arm_r", "arm_l"):
        c.fill(P["suit"])
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, (170, 214, 232))    # gloves
        c.row(c.h - 4, P["suit2"])
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.rect(0, 0, c.w - 1, 2, P["suit"])                  # the coat hangs over the legs
        c.rect(0, c.h - 2, c.w - 1, c.h - 1, WHITE)
        c.row(c.h - 1, (200, 204, 210))


def theme_police(c):
    P = c.P
    if c.is_("head"):
        c.fill(HAIR)
        c.rect(0, 0, c.w - 1, 3, P["helm"])                 # the cap
        c.row(3, BLACK)
        if c.face == "north":
            c.face_plain()
            c.rect(4, 1, 5, 2, GOLD)                         # the badge on the cap
            c.put(4, 1, shade(GOLD, 1.2))
        if c.face == "up":
            c.fill(P["helm"])
        if c.face == "down":
            c.fill(SKIN2)
    elif c.is_("goggles"):
        c.fill((0, 0, 0))                                    # dark glasses
        if c.face == "north":
            c.rect(0, 0, c.w - 1, 0, SKIN)
            c.rect(0, 2, c.w - 1, 2, SKIN)
            c.put(4, 1, SKIN)
            c.rect(1, 0, 3, 1, BLACK)
            c.rect(5, 0, 7, 1, BLACK)
            c.put(1, 0, (90, 96, 120))
            c.put(5, 0, (90, 96, 120))
            c.put(0, 1, BLACK)
            c.put(8, 1, BLACK)
        else:
            c.fill(BLACK)
    elif c.is_("brim"):
        c.fill(BLACK)                                        # the cap's peak
    elif c.is_("stalk"):
        c.fill(BLACK)
    elif c.is_("lamp"):
        c.fill((255, 60, 50), (255, 60, 50))                 # a little siren: red and blue
        for y in range(c.h):
            c.put(c.w - 1, y, (70, 120, 255), (70, 120, 255))
        if c.face in ("east",):
            c.fill((70, 120, 255), (70, 120, 255))
    elif c.is_("torso"):
        c.fill(P["suit"])
        if c.face == "north":
            c.rect(3, 0, 4, 0, (200, 210, 230))              # collar and tie
            c.rect(3, 1, 3, 4, BLACK)
            c.rect(5, 2, 6, 3, GOLD)                         # the badge
            c.put(5, 2, shade(GOLD, 1.2))
            c.put(1, 2, (200, 210, 230))                     # a name tag
            c.put(2, 2, (200, 210, 230))
            c.rect(1, 5, 2, 6, P["suit2"])
            c.rect(5, 5, 6, 6, P["suit2"])
        c.row(0, shade(P["suit"], 1.15))
    elif c.is_("belt"):
        c.fill(BLACK)
        if c.face == "north":
            c.put(c.w // 2, 0, GOLD)
            c.put(1, 0, STEEL)
            c.put(c.w - 2, 0, (90, 60, 40))
    elif c.is_("scarf"):
        c.fill(P["suit"])
        c.row(0, (200, 210, 230))
    elif c.name in ("arm_r", "arm_l"):
        c.fill(P["suit"])
        c.rect(0, 1, c.w - 1, 2, P["suit2"])
        if c.face in ("east", "west"):
            c.put(1, 1, GOLD)                                # the shoulder patch
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, SKIN)
        c.row(c.h - 4, P["suit2"])
    elif c.name in ("leg_r", "leg_l"):
        c.fill(P["pants"])
        c.col(0, shade(P["pants"], 1.5))
        c.rect(0, c.h - 3, c.w - 1, c.h - 1, BLACK)
        c.row(c.h - 3, (50, 50, 58))


THEMES = {"knight": theme_knight, "ninja": theme_ninja, "chef": theme_chef, "pirate": theme_pirate, "astronaut": theme_astronaut, "wizard": theme_wizard,
          "santa": theme_santa, "farmer": theme_farmer, "robot": theme_robot, "miner": theme_miner, "doctor": theme_doctor, "police": theme_police}


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
        cell = 260
        sheets = {yaw: Image.new("RGBA", (cell * 6, cell * ((len(SKINS) + 5) // 6)), (58, 62, 74, 255)) for yaw in (25, 205)}
        for i in range(len(SKINS)):
            tex = Image.open(os.path.join(RES, "textures", "entity", "helper", "helper_%d.png" % i)).convert("RGBA")
            for yaw in (25, 205):
                img = georender.render(model, tex, size=cell, yaw=yaw)
                sheets[yaw].alpha_composite(img.convert("RGBA"), ((i % 6) * cell, (i // 6) * cell))
        for yaw, sheet in sheets.items():
            sheet.convert("RGB").save(os.path.join(out, "outfits_yaw%d.png" % yaw))
        tex = Image.open(os.path.join(RES, "textures", "entity", "helper", "helper_0.png")).convert("RGBA")
        for name, t in (("walk", 0.2), ("work", 0.15), ("sit", 0.5), ("ko", 0.5), ("wave", 0.4), ("cheer", 0.3), ("swim", 0.3)):
            pose = georender.load_anim(anims_path, "animation.helper." + name)
            img = georender.render(model, tex, pose=georender.pose_from_anims([pose], t), size=320, yaw=30)
            img.save(os.path.join(out, "anim_%s.png" % name))


if __name__ == "__main__":
    main()
