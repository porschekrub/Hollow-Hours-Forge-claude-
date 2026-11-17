#!/usr/bin/env python3
"""Zips an add-on (bedrock/<name>/BP + RP) into <Name>-<version>.mcaddon.    python3 pack.py hollow-hours dist"""
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
NAMES = {"hollow-hours": "HollowHours", "helper-buddy": "HelperBuddy"}


def main():
    name, out = sys.argv[1], sys.argv[2]
    root = os.path.join(HERE, "..", name)
    ver = ".".join(str(x) for x in json.load(open(os.path.join(root, "BP", "manifest.json"), encoding="utf8"))["header"]["version"])
    os.makedirs(out, exist_ok=True)
    dst = os.path.join(out, f"{NAMES[name]}-Bedrock-{ver}.mcaddon")
    n = 0
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for sub in ("BP", "RP"):
            base = os.path.join(root, sub)
            for d, _, files in sorted(os.walk(base)):
                for f in sorted(files):
                    if f.startswith(".") or f.endswith((".bak", ".orig")):
                        continue
                    p = os.path.join(d, f)
                    if f.endswith(".json"):
                        json.load(open(p, encoding="utf-8-sig")) if not _has_comments(p) else None
                    z.write(p, os.path.join(f"{NAMES[name]}_{sub}", os.path.relpath(p, base)))
                    n += 1
    print(dst, n, "files", os.path.getsize(dst) // 1024, "KB")


def _has_comments(p):
    s = open(p, encoding="utf-8-sig").read()
    return "//" in s.replace("://", "") or "/*" in s


if __name__ == "__main__":
    main()
