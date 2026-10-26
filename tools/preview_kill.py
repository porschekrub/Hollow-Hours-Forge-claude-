#!/usr/bin/env python3
"""Contact sheet of a kill cinematic: top row = what the victim sees, bottom row = side view.

    python3 preview_kill.py <ghost n> <track name> out.png [frames]
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from georender import Model, render, pose_from_anims  # noqa: E402
from camrender import render_view  # noqa: E402
from animkit import t2g  # noqa: E402

RES = os.path.join(HERE, "..", "hollow-hours", "src", "main", "resources")
A = os.path.join(RES, "assets", "hollowhours")


def sample(keys, t):
    ks = keys
    if t <= ks[0][0]:
        return np.array(ks[0][1:])
    for a, b in zip(ks, ks[1:]):
        if a[0] <= t <= b[0]:
            f = (t - a[0]) / max(1e-9, b[0] - a[0])
            f = f * f * (3 - 2 * f)
            return np.array(a[1:]) + (np.array(b[1:]) - np.array(a[1:])) * f
    return np.array(ks[-1][1:])


def main():
    n, name, out = int(sys.argv[1]), sys.argv[2], sys.argv[3]
    frames = int(sys.argv[4]) if len(sys.argv) > 4 else 8
    model = Model(os.path.join(A, "geo", "entity", "ghost_%d.geo.json" % n))
    tex = Image.open(os.path.join(A, "textures", "entity", "ghost_%d.png" % n))
    glow_p = os.path.join(A, "textures", "entity", "ghost_%d_glow.png" % n)
    glow = Image.open(glow_p) if os.path.exists(glow_p) else None
    anims = json.load(open(os.path.join(A, "animations", "entity", "ghost.animation.json")))["animations"]
    tracks = {t["name"]: t for t in json.load(open(os.path.join(RES, "data", "hollowhours", "kill_tracks.json")))["tracks"]}
    tr = tracks[name]
    anim = anims["animation.ghost." + tr["anim"]]
    W, H = 320, 180
    sheet = Image.new("RGBA", (W * frames, H * 2 + 16), (0, 0, 0, 255))
    d = ImageDraw.Draw(sheet)
    for i in range(frames):
        t = tr["length"] * i / max(1, frames - 1)
        pose = pose_from_anims([anim], t, {"v_dist_moved": 0, "v_cph": 0})
        v = sample(tr["victim"], t)
        hd = sample(tr["head"], t)
        roll = sample(tr["roll"], t)[0] if "roll" in tr else 0
        prone = any(a <= t <= b for a, b in tr.get("prone", []))
        eye = t2g(v + np.array([0, 0.4 if prone else 1.62, 0]))
        tgt = t2g(hd)
        img = render_view(model, tex, pose, eye, tgt, roll=roll, fov=63, size=(W, H), emissive=glow, near=2.0)
        sheet.paste(img, (i * W, 0))
        side = render(model, tex, pose, size=H, yaw=90, pitch=0, scale=H / 110.0, center=(0, 45), emissive=glow)
        # draw the victim as a box in the side view (fwd -> screen left)
        sd = side.copy()
        dd = ImageDraw.Draw(sd)
        s = H / 110.0
        # georender side view at yaw 90: screen x ~ model z
        vz = -v[0] * 16
        vy = v[1] * 16
        px = H / 2 + (vz - 0) * s * -1
        dd.rectangle([px - 4 * s, H / 2 - (vy + 29 - 45) * s, px + 4 * s, H / 2 - (vy - 45) * s], outline=(0, 255, 0, 255))
        sheet.paste(sd, (i * W + (W - H) // 2, H))
        d.text((i * W + 4, 2 * H + 2), "%.2fs" % t, fill=(255, 255, 255, 255))
        evs = [e for e in tr["events"] if abs(e["t"] - t) < tr["length"] / frames / 2]
        if evs:
            d.text((i * W + 50, 2 * H + 2), ",".join(e["type"] + (":" + e["arg"] if e["arg"] else "") for e in evs)[:40], fill=(255, 80, 80, 255))
    sheet.save(out)


if __name__ == "__main__":
    main()
