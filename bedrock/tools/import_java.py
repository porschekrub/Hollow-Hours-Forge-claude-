#!/usr/bin/env python3
"""Brings what the Java mod has and the Bedrock add-on doesn't over to the add-on: sounds, texts (both languages),
item textures. Run again whenever the Java side changes.      python3 bedrock/tools/import_java.py
"""
import json
import os
import shutil
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(HERE, "..", "..", "hollow-hours", "src", "main", "resources", "assets", "hollowhours")
BP = os.path.join(HERE, "..", "hollow-hours", "BP")
RP = os.path.join(HERE, "..", "hollow-hours", "RP")

# text keys the scripts use (prefixes of the Java lang keys, without "hollowhours.")
TEXT_PREFIXES = ["betray.", "bloodmoon.", "alert.", "sanity.", "grave.", "restorer.", "rescue.", "phone.", "chat.", "map.", "endgame.", "credits.",
                 "gun.", "armor.", "struggle.", "weapon.", "fun.", "arcade.", "fit.", "skill.", "grenade.", "ko.", "combat.", "whisper."]
ITEM_TEXT = ["grave_compass", "restorer", "canteen", "phone", "charger"]
ITEM_TEXTURES = {"canteen": "canteen", "canteen_empty": "canteen_empty", "restorer": "restorer", "phone": "phone", "charger": "charger"}


def dur(p):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p]))


def sounds():
    jd = json.load(open(os.path.join(JAVA, "sounds.json"), encoding="utf8"))
    p = os.path.join(RP, "sounds", "sound_definitions.json")
    bd = json.load(open(p, encoding="utf8"))
    defs = bd["sound_definitions"]
    added = 0
    for key, d in jd.items():
        if key in defs:
            continue
        entries = []
        for s in d["sounds"]:
            name = (s if isinstance(s, str) else s["name"]).split(":", 1)[-1]
            src = os.path.join(JAVA, "sounds", name + ".ogg")
            dst = os.path.join(RP, "sounds", name + ".ogg")
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)
            e = {"name": "sounds/" + name, "volume": (s.get("volume", 1.0) if isinstance(s, dict) else 1.0), "load_on_low_memory": True}
            if isinstance(s, dict) and s.get("stream"):
                e["stream"] = True
            entries.append(e)
        cat = "ui" if key.startswith("phone.") else "music" if "music" in key else "hostile" if key.startswith(("ghost.", "boss.")) else "player"
        defs[key] = {"category": cat, "max_distance": 48, "sounds": entries}
        added += 1
    json.dump(bd, open(p, "w", encoding="utf8"), indent=1, ensure_ascii=False)
    print("sounds added:", added)


def texts():
    en = json.load(open(os.path.join(JAVA, "lang", "en_us.json"), encoding="utf8"))
    th = json.load(open(os.path.join(JAVA, "lang", "th_th.json"), encoding="utf8"))
    out = {}
    for k in en:
        short = None
        if k.startswith("hollowhours.") and any(k[12:].startswith(p) for p in TEXT_PREFIXES):
            short = k[12:]
        elif k.startswith("item.hollowhours.") and any(k[17:] == i or k[17:].startswith(i + ".") for i in ITEM_TEXT):
            short = "item." + k[17:]
        if short:
            out[short] = [en[k], th.get(k, en[k])]
    alert = {}
    for lang in ("en", "th"):
        alert[lang] = [round(dur(os.path.join(JAVA, "sounds", "phone", f"alert_{lang}_{i}.ogg")), 2) for i in range(6)]
    js = "// Made by bedrock/tools/import_java.py from the Java mod's lang files: [English, Thai]. Don't edit by hand.\n"
    js += "export const TEXTS = " + json.dumps(out, ensure_ascii=False, indent=0, sort_keys=True) + ";\n"
    js += "export const ALERT_SECONDS = " + json.dumps(alert) + ";\n"
    open(os.path.join(BP, "scripts", "texts_data.js"), "w", encoding="utf8").write(js)
    print("texts:", len(out))


def textures():
    p = os.path.join(RP, "textures", "item_texture.json")
    d = json.load(open(p, encoding="utf8"))
    for name, src in ITEM_TEXTURES.items():
        shutil.copyfile(os.path.join(JAVA, "textures", "item", src + ".png"), os.path.join(RP, "textures", "items", name + ".png"))
        d["texture_data"]["ghost_" + name] = {"textures": "textures/items/" + name}
    # the grave compass: the Java one is the vanilla compass tinted; frame 0 as its icon
    cp = os.path.join(JAVA, "textures", "item", "grave_compass_00.png")
    if os.path.exists(cp):
        shutil.copyfile(cp, os.path.join(RP, "textures", "items", "grave_compass.png"))
        d["texture_data"]["ghost_grave_compass"] = {"textures": "textures/items/grave_compass"}
    json.dump(d, open(p, "w", encoding="utf8"), indent=2)
    print("item textures ok")


if __name__ == "__main__":
    sounds()
    texts()
    textures()
