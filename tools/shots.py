#!/usr/bin/env python3
"""CI helper: shrink the client smoke-test screenshots to small JPEGs (keeps the ci-logs branch light)."""
import os
import sys

src, dst = sys.argv[1], sys.argv[2]
os.makedirs(dst, exist_ok=True)
try:
    from PIL import Image
except ImportError:
    Image = None
for name in sorted(os.listdir(src)) if os.path.isdir(src) else []:
    if not name.endswith(".png"):
        continue
    p = os.path.join(src, name)
    if Image is None:
        os.system(f"cp '{p}' '{dst}/'")
        continue
    im = Image.open(p).convert("RGB")
    w = 640
    im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    im.save(os.path.join(dst, name[:-4] + ".jpg"), quality=78)
print("screenshots:", len(os.listdir(dst)))
