#!/bin/bash
# Rebuilds every generated Hollow Hours asset. Order matters (each step reads the previous one's output).
set -e
cd "$(dirname "$0")"
RP="${1:-/home/claude/src_addons/hollow/Ghost_RP}"   # the unpacked Bedrock resource pack (only needed for import)
[ -d "$RP" ] && python3 import_bedrock.py "$RP"
python3 gen_data.py
python3 gen_dialogue.py
python3 gen_anims.py
python3 gen_glow_faces.py
python3 gen_sounds.py
python3 gen_lang.py
