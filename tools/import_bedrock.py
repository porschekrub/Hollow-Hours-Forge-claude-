#!/usr/bin/env python3
"""Imports the reusable assets of the Bedrock add-on (Hollow_Hours.mcaddon, unpacked) into the Java mod:
sounds (converted to mono so they are positional in Java), base models / textures / animations (molang that
GeckoLib can't evaluate is rewritten to variables the renderers set), item textures and guide icons.

    python3 import_bedrock.py /path/to/unpacked/Ghost_RP
"""
import json
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "hollow-hours", "src", "main", "resources", "assets", "hollowhours")
RP = sys.argv[1] if len(sys.argv) > 1 else "/home/claude/src_addons/hollow/Ghost_RP"

MUSIC = ("ghost.music.",)


def ensure(p):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def to_mono(src, dst):
    ensure(dst)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", dst], check=True)


def sounds():
    d = json.load(open(os.path.join(RP, "sounds", "sound_definitions.json")))
    sd = d.get("sound_definitions", d)
    out = {}
    for name, defn in sd.items():
        entries = []
        stream = name.startswith(MUSIC)
        for s in defn.get("sounds", []):
            if isinstance(s, str):
                s = {"name": s}
            rel = s["name"]
            src = os.path.join(RP, rel + ".ogg")
            if not os.path.exists(src):
                print("  missing", src)
                continue
            jrel = rel[len("sounds/"):] if rel.startswith("sounds/") else rel
            dst = os.path.join(ASSETS, "sounds", jrel + ".ogg")
            if stream:
                ensure(dst)
                shutil.copyfile(src, dst)
            else:
                to_mono(src, dst)
            e = {"name": "hollowhours:" + jrel}
            if s.get("volume", 1.0) != 1.0:
                e["volume"] = s["volume"]
            if s.get("pitch", 1.0) != 1.0:
                e["pitch"] = s["pitch"]
            if stream:
                e["stream"] = True
            if "weight" in s:
                e["weight"] = s["weight"]
            entries.append(e)
        if entries:
            out[name] = {"sounds": entries, "subtitle": "subtitles.hollowhours." + name}
    return out


def fix_molang(text):
    text = text.replace("query.modified_distance_moved", "variable.dist_moved")
    text = text.replace("query.target_x_rotation", "0").replace("query.target_y_rotation", "0")
    text = text.replace("query.delta_time", "0.05")
    text = text.replace("variable.attack_time", "0")
    text = text.replace("query.property('ghost:kind')", "variable.bkind").replace("query.property('ghost:n')", "variable.bn")
    text = text.replace("query.property('ghost:side')", "variable.bside")
    return text


def geckolib_channels(d):
    """GeckoLib only reads vectors: a channel written as one number/expression ("scale": "q ? 0 : 1") becomes [v, v, v]."""
    def vec(v):
        if isinstance(v, (str, int, float)):
            return [v, v, v]
        if isinstance(v, dict) and "vector" not in v and not any(k in v for k in ("pre", "post")):
            return {k: vec(x) for k, x in v.items()}
        if isinstance(v, dict):
            return {k: (vec(x) if k in ("pre", "post", "vector") else x) for k, x in v.items()}
        return v
    for a in d["animations"].values():
        for b in a.get("bones", {}).values():
            for ch in ("rotation", "position", "scale"):
                if ch in b:
                    b[ch] = vec(b[ch])


def copy_json(src, dst, fix=False, rename=None):
    t = open(src).read()
    if fix:
        t = fix_molang(t)
    if rename:
        for a, b in rename.items():
            t = t.replace(a, b)
    d = json.loads(t)
    if isinstance(d, dict) and "animations" in d:
        geckolib_channels(d)
    with open(ensure(dst), "w") as f:
        json.dump(d, f, separators=(",", ":"))


def models():
    E = os.path.join(RP, "models", "entity")
    T = os.path.join(RP, "textures", "entity")
    for n in range(1, 6):
        copy_json(os.path.join(E, "ghost_%d.geo.json" % n), os.path.join(ASSETS, "geo", "entity", "ghost_%d.geo.json" % n))
        shutil.copyfile(os.path.join(T, "ghost_%d.png" % n), ensure(os.path.join(ASSETS, "textures", "entity", "ghost_%d.png" % n)))
    for n in range(1, 4):
        copy_json(os.path.join(E, "stalker_%d.geo.json" % n), os.path.join(ASSETS, "geo", "entity", "stalker_%d.geo.json" % n))
        shutil.copyfile(os.path.join(T, "stalker_%d.png" % n), ensure(os.path.join(ASSETS, "textures", "entity", "stalker_%d.png" % n)))
    copy_json(os.path.join(E, "ghost_closet.geo.json"), os.path.join(ASSETS, "geo", "entity", "closet.geo.json"))
    shutil.copyfile(os.path.join(T, "ghost_closet.png"), ensure(os.path.join(ASSETS, "textures", "entity", "closet.png")))
    copy_json(os.path.join(E, "ghost_boards.geo.json"), os.path.join(ASSETS, "geo", "entity", "boards.geo.json"))
    for i in range(4):
        shutil.copyfile(os.path.join(T, "boards", "boards_%d.png" % i), ensure(os.path.join(ASSETS, "textures", "entity", "boards", "boards_%d.png" % i)))
    A = os.path.join(RP, "animations")
    copy_json(os.path.join(A, "ghost_closet.animation.json"), os.path.join(ASSETS, "animations", "entity", "closet.animation.json"), fix=True)
    copy_json(os.path.join(A, "ghost_boards.animation.json"), os.path.join(ASSETS, "animations", "entity", "boards.animation.json"), fix=True)
    # the ghost base animations are merged with the new ones by gen_anims.py
    copy_json(os.path.join(A, "ghost.animation.json"), os.path.join(HERE, "data", "ghost_base.animation.json"), fix=True)
    copy_json(os.path.join(A, "ghost_stalker.animation.json"), os.path.join(HERE, "data", "stalker_base.animation.json"), fix=True)


def items():
    I = os.path.join(RP, "textures", "items")
    names = {"battery": "battery", "board_iron": "board_iron", "board_stone": "board_stone", "board_wood": "board_wood", "closet": "closet_item",
             "flashlight": "flashlight", "ghost_guide": "ghost_guide", "repair_glass": "repair_glass", "repair_iron": "repair_iron",
             "repair_stone": "repair_stone", "repair_wood": "repair_wood"}
    for a, b in names.items():
        shutil.copyfile(os.path.join(I, a + ".png"), ensure(os.path.join(ASSETS, "textures", "item", b + ".png")))
    U = os.path.join(RP, "textures", "ghost_ui")
    for f in os.listdir(U):
        if f.endswith(".png"):
            shutil.copyfile(os.path.join(U, f), ensure(os.path.join(ASSETS, "textures", "gui", "guide", f)))


if __name__ == "__main__":
    models()
    items()
    js = sounds()
    with open(os.path.join(HERE, "data", "sounds_bedrock.json"), "w") as f:
        json.dump(js, f, indent=1)
    print("imported", len(js), "sound events")
