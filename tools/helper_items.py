#!/usr/bin/env python3
"""Item textures (contract, command book) and the mod logo for Helper Buddy."""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "helper-buddy", "src", "main", "resources")
TEX = os.path.join(RES, "assets", "helperbuddy", "textures", "item")


def px(img, pts, c):
    for x, y in pts:
        img.putpixel((x, y), c + (255,))


def contract():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paper, edge, ink = (240, 228, 196), (196, 172, 120), (90, 70, 50)
    for y in range(2, 14):
        for x in range(3, 13):
            img.putpixel((x, y), (paper if 3 < x < 12 else edge) + (255,))
    for x in range(2, 14):
        img.putpixel((x, 1), edge + (255,))
        img.putpixel((x, 14), edge + (255,))
        img.putpixel((x, 2), (220, 204, 160, 255))
        img.putpixel((x, 13), (220, 204, 160, 255))
    for y in (4, 6, 8):
        for x in range(5, 11):
            if (x + y) % 5:
                img.putpixel((x, y), ink + (255,))
    # gold seal with a blue ribbon
    px(img, [(9, 10), (10, 10), (9, 11), (10, 11), (8, 11), (11, 11), (9, 12), (10, 12)], (250, 200, 50))
    px(img, [(9, 11)], (255, 240, 140))
    px(img, [(8, 13), (11, 13)], (52, 120, 214))
    return img


def book():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cover, dark, pages = (52, 120, 214), (30, 70, 140), (240, 236, 220)
    for y in range(2, 15):
        for x in range(3, 14):
            img.putpixel((x, y), cover + (255,))
    for y in range(2, 15):
        img.putpixel((3, y), dark + (255,))
        img.putpixel((4, y), dark + (255,))
    for y in range(3, 14):
        img.putpixel((13, y), pages + (255,))
    for x in range(5, 13):
        img.putpixel((x, 14), pages + (255,))
    # a little yellow helmet with goggles
    px(img, [(7, 5), (8, 5), (9, 5), (10, 5), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6), (6, 7), (11, 7)], (250, 196, 48))
    px(img, [(7, 7), (8, 7), (9, 7), (10, 7)], (40, 40, 46))
    px(img, [(7, 7), (10, 7)], (80, 230, 255))
    px(img, [(7, 8), (8, 8), (9, 8), (10, 8), (7, 9), (8, 9), (9, 9), (10, 9)], (246, 204, 166))
    px(img, [(8, 9), (9, 9)], (110, 60, 50))
    px(img, [(8, 4), (9, 4)], (255, 236, 120))
    for x in range(6, 12):
        img.putpixel((x, 11), (250, 210, 70, 255))
    return img


def logo():
    sys.path.insert(0, HERE)
    import georender
    model = georender.Model(os.path.join(RES, "assets", "helperbuddy", "geo", "entity", "helper.geo.json"))
    tex = Image.open(os.path.join(RES, "assets", "helperbuddy", "textures", "entity", "helper", "helper_0.png")).convert("RGBA")
    anim = georender.load_anim(os.path.join(RES, "assets", "helperbuddy", "animations", "entity", "helper.animation.json"), "animation.helper.wave")
    img = georender.render(model, tex, pose=georender.pose_from_anims([anim], 0.35), size=256, yaw=25, bg=(0, 0, 0, 0))
    return img


def main():
    os.makedirs(TEX, exist_ok=True)
    contract().save(os.path.join(TEX, "helper_contract.png"))
    book().save(os.path.join(TEX, "command_book.png"))
    logo().save(os.path.join(RES, "helperbuddy_logo.png"))
    print("items + logo written")


if __name__ == "__main__":
    main()
