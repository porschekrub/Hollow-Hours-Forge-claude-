#!/usr/bin/env python3
"""CI helper: fail the build when the dev server did not start cleanly.

The mods print "SMOKE TEST OK" after the server has fully started (only when the
smoke-test system property is set) and then stop the server.  We also fail on
any error coming from our own packages and on broken recipes / loot tables /
json resources, so data mistakes are caught before a player sees them.
"""
import re
import sys

path = sys.argv[1]
marker = sys.argv[2] if len(sys.argv) > 2 else "SMOKE TEST OK"
try:
    text = open(path, encoding="utf-8", errors="replace").read()
except OSError as e:
    print(f"::error::no server log ({e})")
    sys.exit(1)

problems = []
if marker not in text:
    problems.append(f"never reached '{marker}'")

OUR = re.compile(r"(com\.hollowhours|com\.helperbuddy|hollowhours:|helperbuddy:)")
BAD = re.compile(r"(Exception|Error|Couldn't parse|Parsing error|Failed to load|Unknown|Couldn't load)", re.I)
lines = text.splitlines()
for i, line in enumerate(lines):
    if not BAD.search(line):
        continue
    window = "\n".join(lines[i:i + 6])
    if not OUR.search(window):
        continue
    level_error = "/ERROR]" in line or "[ERROR]" in line or "/FATAL]" in line
    exception = "Exception" in line and "WARN" not in line[:80]
    if level_error or exception:
        problems.append(window)

# GeckoLib logs a broken animation without our namespace in the line: name it explicitly
for line in lines:
    if "Unable to parse animation" in line:
        problems.append(line)

# resource problems (missing sounds/textures/models) are warnings, shown in the run summary
WARN = re.compile(r"(Missing|Unable to|Invalid|Couldn't|Failed|not found)", re.I)
for line in lines:
    if "/WARN]" in line and OUR.search(line) and WARN.search(line):
        print("::warning::" + line[:600])

if problems:
    for p in problems[:40]:
        print("::error::" + p.replace("\n", " | ")[:1500])
    sys.exit(1)
print("smoke test passed")
