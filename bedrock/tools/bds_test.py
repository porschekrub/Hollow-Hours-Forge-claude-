#!/usr/bin/env python3
"""Runs an add-on on a real Bedrock Dedicated Server and checks what the game itself says about it.

    python3 bds_test.py <addon dir with BP/ and RP/> <steps file> [--server DIR] [--out DIR]

Steps file, one per line:  a console command to send, or
    wait <seconds>            expect <seconds> <regex>      (fails the run when the text never shows up)
Lines starting with # are comments. The log is checked for errors at the end.
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import nbtle  # noqa: E402

UA = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) addon-ci"}


def download(server):
    if os.path.exists(os.path.join(server, "bedrock_server")):
        return
    os.makedirs(server, exist_ok=True)
    url = os.environ.get("BDS_URL")
    if not url:
        try:
            req = urllib.request.Request("https://net-secondary.web.minecraft-services.net/api/v1.0/download/links", headers=UA)
            links = json.load(urllib.request.urlopen(req, timeout=60))["result"]["links"]
            url = [x["downloadUrl"] for x in links if x["downloadType"] == "serverBedrockLinux"][0]
        except Exception as e:  # noqa: BLE001
            print("download list failed:", e)
            url = "https://www.minecraft.net/bedrockdedicatedserver/bin-linux/bedrock-server-1.26.60.5.zip"
    print("server:", url)
    z = os.path.join(server, "bds.zip")
    for i in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r, open(z, "wb") as f:
                shutil.copyfileobj(r, f)
            break
        except Exception as e:  # noqa: BLE001
            print("download failed:", e)
            time.sleep(20)
    zipfile.ZipFile(z).extractall(server)
    os.remove(z)
    os.chmod(os.path.join(server, "bedrock_server"), 0o755)


def props(server, **kv):
    p = os.path.join(server, "server.properties")
    lines = open(p).read().splitlines()
    for k, v in kv.items():
        k = k.replace("_", "-")
        hit = False
        for i, ln in enumerate(lines):
            if ln.startswith(k + "="):
                lines[i] = f"{k}={v}"
                hit = True
        if not hit:
            lines.append(f"{k}={v}")
    open(p, "w").write("\n".join(lines) + "\n")


class Server:
    def __init__(self, server, logpath):
        env = dict(os.environ, LD_LIBRARY_PATH=".")
        self.p = subprocess.Popen(["./bedrock_server"], cwd=server, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        self.lines = []
        self.log = open(logpath, "a")
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        for ln in self.p.stdout:
            self.lines.append(ln.rstrip("\n"))
            self.log.write(ln)
            self.log.flush()

    def send(self, cmd):
        self.log.write("> " + cmd + "\n")
        self.p.stdin.write(cmd + "\n")
        self.p.stdin.flush()

    def expect(self, rx, timeout, start=0):
        r = re.compile(rx)
        end = time.time() + timeout
        i = start
        while time.time() < end:
            while i < len(self.lines):
                if r.search(self.lines[i]):
                    return i
                i += 1
            if self.p.poll() is not None:
                return -1
            time.sleep(0.2)
        return -1

    def stop(self):
        try:
            self.send("stop")
            self.p.wait(60)
        except Exception:  # noqa: BLE001
            self.p.kill()


def main():
    a = sys.argv[1:]
    addon, steps = a[0], a[1]
    server = a[a.index("--server") + 1] if "--server" in a else "/tmp/bds"
    out = a[a.index("--out") + 1] if "--out" in a else "/tmp/bds-out"
    os.makedirs(out, exist_ok=True)
    logpath = os.path.join(out, "server.log")
    open(logpath, "w").close()
    download(server)
    props(server, level_name="test", level_type="FLAT", gamemode="survival", difficulty="normal", online_mode="false", allow_list="false", allow_cheats="true",
          content_log_file_enabled="true", content_log_console_output_enabled="true", default_player_permission_level="operator",
          server_port="19132", server_portv6="19133", max_players="10", view_distance="8", tick_distance="4")
    world = os.path.join(server, "worlds", "test")
    shutil.rmtree(world, ignore_errors=True)
    packs = {"behavior": [], "resource": []}
    for kind, sub, src in (("behavior", "BP", addon), ("resource", "RP", addon), ("behavior", "BP", os.path.join(HERE, "..", "testpack"))):
        d = os.path.join(src, sub)
        if not os.path.isdir(d):
            continue
        man = json.load(open(os.path.join(d, "manifest.json"), encoding="utf8"))
        dst = os.path.join(server, f"development_{kind}_packs", os.path.basename(os.path.abspath(src)) + "_" + sub)
        shutil.rmtree(dst, ignore_errors=True)
        shutil.copytree(d, dst)
        packs[kind].append({"pack_id": man["header"]["uuid"], "version": man["header"]["version"]})
    # first start: makes the world
    s = Server(server, logpath)
    ok = s.expect(r"Server started", 240) >= 0
    s.stop()
    if not ok:
        print("server did not start")
        print("\n".join(s.lines[-60:]))
        sys.exit(1)
    ver, root = nbtle.load_level(os.path.join(world, "level.dat"))
    exp = root.v.get("experiments") or nbtle.Tag(nbtle.COMPOUND, {})
    for k in ("gametest", "experiments_ever_used", "saved_with_toggled_experiments"):
        exp.v[k] = nbtle.byte(1)
    root.v["experiments"] = exp
    nbtle.save_level(os.path.join(world, "level.dat"), ver, root)
    json.dump(packs["behavior"], open(os.path.join(world, "world_behavior_packs.json"), "w"))
    json.dump(packs["resource"], open(os.path.join(world, "world_resource_packs.json"), "w"))
    open(logpath, "a").write("\n===== second start: with the packs =====\n")
    s = Server(server, logpath)
    failed = []
    if s.expect(r"Server started", 240) < 0:
        failed.append("server did not start with the packs")
    else:
        for raw in open(steps, encoding="utf8").read().splitlines():
            ln = raw.strip()
            if not ln or ln.startswith("#"):
                continue
            if ln.startswith("wait "):
                time.sleep(float(ln.split()[1]))
            elif ln.startswith("expect "):
                _, t, rx = ln.split(" ", 2)
                mark = getattr(s, "mark", 0)
                i = s.expect(rx, float(t), mark)
                if i < 0:
                    failed.append("never saw: " + rx)
                    print("MISSING:", rx)
                else:
                    print("ok:", s.lines[i][:200])
            else:
                s.mark = len(s.lines)
                s.send(ln)
                time.sleep(0.3)
            if s.p.poll() is not None:
                failed.append("server stopped (crash?) at: " + ln)
                break
    s.stop()
    time.sleep(1)
    for f in glob.glob(os.path.join(server, "ContentLog*")) + glob.glob(os.path.join(server, "*.log")):
        shutil.copy(f, out)
    second = "\n".join(s.lines)
    allow = [re.compile(x) for x in open(os.path.join(os.path.dirname(steps), "allowed_errors.txt"), encoding="utf8").read().splitlines() if x.strip() and not x.startswith("#")] if os.path.exists(os.path.join(os.path.dirname(steps), "allowed_errors.txt")) else []
    errs = [ln for ln in s.lines if re.search(r"\bERROR\]|\[error\]|Unhandled promise|SyntaxError|ReferenceError|TypeError", ln) and not any(r.search(ln) for r in allow)]
    warns = [ln for ln in s.lines if re.search(r"\bWARN\]", ln) and "[hhtest]" not in ln and "[test]" not in ln]
    rep = [f"errors: {len(errs)}", *errs[:200], "", f"warnings: {len(warns)}", *warns[:200], "", "failed steps:", *failed]
    open(os.path.join(out, "report.txt"), "w").write("\n".join(rep) + "\n")
    print("\n".join(rep[:120]))
    if errs or failed or "crash" in second.lower():
        sys.exit(1)
    print("BEDROCK TEST OK")


if __name__ == "__main__":
    main()
