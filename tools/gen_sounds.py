#!/usr/bin/env python3
"""Synthesizes the new Java-edition sounds (screams, jumpscare hits, kill impacts, betrayal) and writes
assets/hollowhours/sounds.json (Bedrock sounds imported by import_bedrock.py + these).

Everything is generated from noise / oscillators / the add-on's own voice recordings, mono 44.1 kHz."""
import json
import os
import subprocess
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, "..", "hollow-hours", "src", "main", "resources", "assets", "hollowhours")
SND = os.path.join(A, "sounds")
SR = 44100
rng = np.random.default_rng(1234)


# ----------------------------------------------------------------------------- dsp helpers
def t_(d):
    return np.arange(int(SR * d)) / SR


def noise(d):
    return rng.uniform(-1, 1, int(SR * d))


def env_adsr(n, a=0.005, d=0.1, s=0.6, r=0.3):
    t = np.arange(n) / SR
    tot = n / SR
    e = np.ones(n) * s
    e[t < a] = t[t < a] / a
    m = (t >= a) & (t < a + d)
    e[m] = 1 - (1 - s) * (t[m] - a) / d
    m = t > tot - r
    e[m] *= np.clip((tot - t[m]) / r, 0, 1)
    return e


def env_exp(n, k):
    return np.exp(-np.arange(n) / SR * k)


def bp(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), min(0.99, hi / (SR / 2))], "band")
    return signal.lfilter(b, a, x)


def lp(x, f, order=2):
    b, a = signal.butter(order, min(0.99, f / (SR / 2)), "low")
    return signal.lfilter(b, a, x)


def hp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), "high")
    return signal.lfilter(b, a, x)


def osc(freq, d, shape="saw", phase=0.0):
    n = int(SR * d)
    f = np.broadcast_to(np.asarray(freq, float), (n,)) if np.ndim(freq) == 0 else np.asarray(freq, float)[:n]
    ph = np.cumsum(f) / SR + phase
    if shape == "sin":
        return np.sin(2 * np.pi * ph)
    if shape == "sq":
        return np.sign(np.sin(2 * np.pi * ph))
    return 2 * (ph % 1) - 1


def glide(f0, f1, d, curve=1.0):
    t = np.linspace(0, 1, int(SR * d)) ** curve
    return f0 + (f1 - f0) * t


def dist(x, k=3.0):
    return np.tanh(x * k) / np.tanh(k)


def reverb(x, decay=1.5, mix=0.35, pre=0.02):
    n = int(SR * decay)
    ir = rng.normal(0, 1, n) * np.exp(-np.arange(n) / SR * (6.9 / decay))
    ir = lp(ir, 5000)
    ir = np.concatenate([np.zeros(int(SR * pre)), ir])
    wet = signal.fftconvolve(x, ir)[: len(x) + n]
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    wet /= max(1e-9, np.abs(wet).max())
    return dry * (1 - mix) + wet * mix * np.abs(x).max()


def pad(x, d):
    return np.concatenate([x, np.zeros(int(SR * d))])


def mixl(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def at(x, d):
    return np.concatenate([np.zeros(int(SR * d)), x])


def norm(x, peak=0.95):
    m = np.abs(x).max()
    return x * (peak / m) if m > 0 else x


def load(name):
    """an imported add-on sound (ogg) as float mono"""
    src = os.path.join(SND, name + ".ogg")
    with tempfile.NamedTemporaryFile(suffix=".wav") as f:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-ac", "1", "-ar", str(SR), f.name], check=True)
        sr, d = wavfile.read(f.name)
    d = d.astype(float)
    return d / max(1, np.abs(d).max())


def pitch(x, factor):
    """resample: factor < 1 = lower and longer"""
    n = int(len(x) / factor)
    return signal.resample(x, n)


def save(name, x, peak=0.95):
    x = norm(x, peak)
    path = os.path.join(SND, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as f:
        wavfile.write(f.name, SR, (x * 32767).astype(np.int16))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", f.name, "-c:a", "libvorbis", "-q:a", "5", path], check=True)


# ----------------------------------------------------------------------------- screams
def scream_voice(f0, f1, d, formants, rough=0.4, vibrato=8.0, vib_depth=0.05, curve=0.6):
    f = glide(f0, f1, d, curve)
    tt = t_(d)
    f = f * (1 + vib_depth * np.sin(2 * np.pi * vibrato * tt) + rough * 0.02 * rng.normal(0, 1, len(tt)))
    src = osc(f, d, "saw") + 0.5 * osc(f * 1.01, d, "saw") + 0.3 * osc(f * 0.5, d, "sq")
    src += rough * noise(d)
    out = np.zeros(len(src))
    for (lo, hi, g) in formants:
        out += g * bp(src, lo, hi)
    return dist(out * 2.5, 2.5)


def scream(n):
    d = 2.3
    if n == 1:      # the Wailing Dweller: a high, breaking human wail
        v = scream_voice(700, 1150, d, [(700, 1300, 1.0), (2200, 3200, 0.7), (3500, 5000, 0.35)], rough=0.5, vibrato=7, vib_depth=0.07)
        v2 = scream_voice(520, 980, d, [(500, 1100, 1.0), (1800, 2600, 0.6)], rough=0.6, vibrato=5.5)
        base = mixl(v, 0.7 * v2)
    elif n == 2:    # the Sunken Widow: a gargled scream through water
        v = scream_voice(560, 900, d, [(600, 1200, 1.0), (2000, 3000, 0.6)], rough=0.4, vibrato=11, vib_depth=0.09)
        gurgle = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 17 * t_(d)) + 0.3 * np.sin(2 * np.pi * 31 * t_(d)))
        bub = bp(noise(d), 200, 900) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * t_(d)))
        base = mixl(v * (0.55 + 0.45 * gurgle), 0.5 * bub)
    elif n == 3:    # the Ashen Screamer: a roaring, crackling scream
        v = scream_voice(260, 520, d, [(250, 900, 1.0), (1200, 2400, 0.8), (2800, 4200, 0.4)], rough=1.0, vibrato=4, vib_depth=0.04)
        crack = np.zeros(int(SR * d))
        for _ in range(140):
            p = rng.integers(0, len(crack) - 400)
            crack[p:p + 400] += rng.uniform(-1, 1, 400) * np.exp(-np.arange(400) / 60)
        roar = lp(noise(d), 600) * 2
        base = mixl(v, 0.5 * hp(crack, 1500), 0.6 * roar * env_adsr(int(SR * d), 0.05, 0.4, 0.7, 0.5))
    elif n == 4:    # the Smiling Void: a laugh that turns into a screech
        tt = t_(d)
        f = np.where(tt < 0.9, 380 + 120 * (np.sin(2 * np.pi * 7 * tt) > 0), glide(500, 1500, d, 2.0)[: len(tt)])
        v = scream_voice(400, 1400, d, [(500, 1100, 1.0), (2400, 3400, 0.8), (4000, 6000, 0.4)], rough=0.3, vibrato=14, vib_depth=0.1, curve=2.0)
        ha = osc(f, d, "saw") * (np.sin(2 * np.pi * 7 * tt) > 0) * (tt < 0.9)
        base = mixl(v * np.clip((tt - 0.6) * 3, 0, 1), 0.7 * bp(ha, 400, 2500))
    else:           # the Antlered Hunger: a deep animal roar under an elk-like shriek
        v = scream_voice(180, 120, d, [(100, 500, 1.0), (700, 1500, 0.6)], rough=1.2, vibrato=3, vib_depth=0.03)
        shriek = scream_voice(1400, 2100, d, [(1500, 2600, 1.0), (3200, 4800, 0.5)], rough=0.2, vibrato=9, vib_depth=0.04)
        base = mixl(v, 0.45 * shriek * env_adsr(int(SR * d), 0.2, 0.3, 0.8, 0.6))
    e = env_adsr(len(base), 0.01, 0.25, 0.8, 0.6)
    try:
        rage = load("ghost/voice/g%d_rage1" % n)
        rage = pitch(rage, 0.85)[: len(base)]
        base = mixl(base, 0.5 * rage)
    except Exception:
        pass
    return reverb(dist(base * e, 1.6), 1.2, 0.25)


def stalker_scream(n):
    d = 1.6
    f0, f1 = [(900, 1800), (1300, 2600), (500, 300)][n - 1]
    v = scream_voice(f0, f1, d, [(f0 * 0.8, f0 * 1.8, 1.0), (2500, 4200, 0.6)], rough=0.5 + 0.3 * n, vibrato=10 + 3 * n, vib_depth=0.08)
    return reverb(v * env_adsr(len(v), 0.005, 0.2, 0.8, 0.4), 1.0, 0.3)


# ----------------------------------------------------------------------------- impacts
def thud(d=0.6, f0=90, f1=40, k=7):
    tt = t_(d)
    body = osc(glide(f0, f1, d, 0.5), d, "sin") * env_exp(len(tt), k)
    click = hp(noise(0.02), 1500) * env_exp(int(SR * 0.02), 200)
    return mixl(body, 0.6 * click)


def crack(d=0.35, density=60, hi=2500):
    x = np.zeros(int(SR * d))
    for _ in range(density):
        p = rng.integers(0, max(1, int(len(x) * 0.5)))
        ln = rng.integers(40, 300)
        if p + ln < len(x):
            x[p:p + ln] += rng.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 4))
    return hp(x, hi) * env_exp(len(x), 9)


def squelch(d=0.45):
    tt = t_(d)
    f = glide(700, 180, d, 0.5)
    w = bp(noise(d), 200, 1400) * (0.5 + 0.5 * np.sin(2 * np.pi * f * tt * 0.05))
    return lp(w, 1800) * env_adsr(len(tt), 0.005, 0.1, 0.5, 0.25)


def whoosh(d=0.35):
    tt = t_(d)
    w = bp(noise(d), 300, 3000) * np.sin(np.pi * tt / d) ** 2
    return w


def bubbles(d=1.4):
    x = np.zeros(int(SR * d))
    for _ in range(60):
        p = rng.integers(0, len(x) - 3000)
        f = rng.uniform(250, 900)
        ln = rng.integers(800, 3000)
        tt = np.arange(ln) / SR
        x[p:p + ln] += np.sin(2 * np.pi * f * (1 + 6 * tt) * tt) * np.exp(-tt * 40)
    return mixl(x, 0.3 * lp(noise(d), 400))


def fire(d=1.3):
    roar = lp(noise(d), 700) * (0.7 + 0.3 * np.sin(2 * np.pi * 3 * t_(d)))
    return mixl(roar, 0.6 * crack(d, 160, 1800), 0.3 * hp(noise(d), 4000) * env_adsr(int(SR * d), 0.01, 0.1, 0.4, 0.3))


def heartbeat(d=1.6, bpm=48):
    x = np.zeros(int(SR * d))
    period = 60 / bpm
    for k in range(int(d / period) + 1):
        for off, amp in ((0.0, 1.0), (0.28, 0.7)):
            p = int(SR * (k * period + off))
            b = thud(0.25, 60, 35, 18) * amp
            if p + len(b) < len(x):
                x[p:p + len(b)] += b
    return lp(x, 300)


def build():
    out = {}
    for n in range(1, 6):
        save("ghost/new/scream_g%d" % n, scream(n))
        out["ghost.scream.g%d" % n] = ["ghost/new/scream_g%d" % n]
    for n in range(1, 4):
        save("ghost/new/scream_stalker%d" % n, stalker_scream(n))
        out["ghost.scream.stalker%d" % n] = ["ghost/new/scream_stalker%d" % n]
    # jumpscare layers
    hit = mixl(thud(0.9, 110, 35, 4) * 1.4, 0.8 * hp(noise(0.3), 2000) * env_exp(int(SR * 0.3), 14),
               0.7 * scream_voice(1800, 2600, 0.5, [(1800, 3500, 1.0)], 0.3) * env_exp(int(SR * 0.5), 6))
    save("ghost/new/jumpscare_hit", reverb(dist(hit, 2), 1.5, 0.3))
    st = hp(noise(1.6), 400) * (0.6 + 0.4 * (rng.uniform(0, 1, int(SR * 1.6)) > 0.97)) * env_adsr(int(SR * 1.6), 0.005, 0.2, 0.7, 0.6)
    save("ghost/new/jumpscare_static", st, 0.7)
    ring = osc(4100, 3.0, "sin") * env_adsr(int(SR * 3.0), 0.05, 0.5, 0.6, 2.0)
    save("ghost/new/jumpscare_ring", ring, 0.35)
    boom = mixl(thud(2.0, 70, 25, 2.2) * 1.5, 0.3 * lp(noise(2.0), 200) * env_exp(int(SR * 2), 2.5))
    save("ghost/new/jumpscare_boom", reverb(dist(boom, 1.5), 2.5, 0.35))
    out.update({"ghost.jumpscare.hit": ["ghost/new/jumpscare_hit"], "ghost.jumpscare.static": ["ghost/new/jumpscare_static"],
                "ghost.jumpscare.ring": ["ghost/new/jumpscare_ring"], "ghost.jumpscare.boom": ["ghost/new/jumpscare_boom"]})
    # kill sounds (2 variants each)
    K = {
        "grab": lambda: mixl(whoosh(0.3), at(thud(0.4, 140, 60, 12), 0.22), at(0.5 * squelch(0.3), 0.24)),
        "punch": lambda: mixl(thud(0.45, 120, 50, 12) * 1.3, 0.6 * crack(0.2, 20, 2000), 0.4 * squelch(0.25)),
        "crunch": lambda: mixl(crack(0.5, 140, 1200) * 1.3, 0.5 * thud(0.4, 90, 40, 10), 0.4 * squelch(0.4)),
        "slam": lambda: reverb(mixl(thud(0.9, 80, 30, 5) * 1.6, 0.6 * crack(0.4, 50, 800), 0.4 * lp(noise(0.6), 300) * env_exp(int(SR * 0.6), 8)), 1.0, 0.25),
        "tear": lambda: mixl(bp(noise(0.9), 500, 4000) * (0.4 + 0.6 * (rng.uniform(0, 1, int(SR * 0.9)) > 0.6)) * env_adsr(int(SR * 0.9), 0.01, 0.2, 0.7, 0.3), 0.7 * squelch(0.7), 0.5 * crack(0.6, 40, 1500)),
        "choke": lambda: mixl(scream_voice(160, 120, 1.0, [(200, 800, 1.0)], 1.0) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 9 * t_(1.0)))), 0.5 * bubbles(1.0)),
        "bite": lambda: mixl(crack(0.35, 90, 1500) * 1.2, squelch(0.5), thud(0.3, 150, 70, 15)),
        "burn": lambda: mixl(fire(1.2), 0.5 * scream_voice(500, 700, 1.2, [(400, 1200, 1.0)], 0.6) * env_adsr(int(SR * 1.2), 0.1, 0.3, 0.4, 0.4)),
        "drown": lambda: mixl(bubbles(1.3), 0.5 * scream_voice(220, 160, 1.3, [(200, 700, 1.0)], 0.8) * 0.4),
        "neck": lambda: mixl(crack(0.25, 30, 2500) * 1.6, 0.4 * thud(0.25, 200, 90, 25)),
        "stab": lambda: mixl(squelch(0.5) * 1.2, 0.6 * thud(0.3, 130, 60, 14), 0.4 * crack(0.2, 20, 2000)),
        "stomp": lambda: reverb(mixl(thud(1.0, 70, 28, 4.5) * 1.8, 0.7 * crack(0.5, 90, 900), 0.5 * squelch(0.4)), 1.2, 0.25),
        "splat": lambda: mixl(squelch(0.6) * 1.3, thud(0.5, 90, 40, 8), 0.4 * crack(0.3, 40, 1500)),
        "final": lambda: reverb(mixl(thud(2.2, 60, 22, 2.0) * 1.8, 0.6 * crack(0.6, 120, 900), 0.5 * scream_voice(900, 300, 1.5, [(700, 2500, 1.0)], 0.6) * env_exp(int(SR * 1.5), 2)), 3.0, 0.4),
    }
    for k, fn in K.items():
        names = []
        for v in (1, 2):
            nm = "ghost/new/kill_%s%d" % (k, v)
            save(nm, fn())
            names.append(nm)
        out["ghost.kill." + k] = names
    # betrayal
    wh = bp(noise(2.2), 1500, 6000) * (0.4 + 0.6 * np.abs(np.sin(2 * np.pi * 2.3 * t_(2.2)))) * env_adsr(int(SR * 2.2), 0.3, 0.3, 0.8, 0.8)
    save("ghost/new/betray_whisper", reverb(wh, 1.8, 0.4))
    tick = mixl(hp(noise(0.03), 2500) * env_exp(int(SR * 0.03), 250), 0.5 * osc(1800, 0.05, "sin") * env_exp(int(SR * 0.05), 120))
    save("ghost/new/betray_tick", pad(tick, 0.1), 0.6)
    tt = t_(2.0)
    laugh_f = 110 + 25 * (np.sin(2 * np.pi * 5.5 * tt) > 0)
    laugh = bp(osc(laugh_f, 2.0, "saw") + 0.4 * noise(2.0), 150, 1600) * np.clip(np.sin(2 * np.pi * 5.5 * tt), 0, 1) * env_adsr(len(tt), 0.05, 0.3, 0.8, 0.6)
    save("ghost/new/betray_laugh", reverb(dist(laugh * 2, 2), 1.6, 0.35))
    sold = mixl(osc(glide(220, 55, 2.5, 0.7), 2.5, "saw") * env_adsr(int(SR * 2.5), 0.01, 0.4, 0.6, 1.2), 0.6 * osc(glide(233, 58, 2.5, 0.7), 2.5, "saw"))
    save("ghost/new/betray_sold", reverb(lp(sold, 1200), 2.2, 0.4))
    out.update({"ghost.betray.whisper": ["ghost/new/betray_whisper"], "ghost.betray.tick": ["ghost/new/betray_tick"],
                "ghost.betray.laugh": ["ghost/new/betray_laugh"], "ghost.betray.sold": ["ghost/new/betray_sold"]})
    save("ghost/new/heart_slow", heartbeat(2.6, 46))
    fl = mixl(osc(1000, 3.5, "sin") * 0.6 * env_adsr(int(SR * 3.5), 0.01, 0.1, 1.0, 0.3),
              at(0.4 * osc(1000, 0.12, "sin"), 0.0))
    beeps = np.zeros(int(SR * 1.6))
    for i in range(3):
        p = int(SR * i * 0.5)
        b = osc(1000, 0.12, "sin") * 0.6
        beeps[p:p + len(b)] += b
    save("ghost/new/flatline", np.concatenate([beeps, fl]), 0.5)
    out.update({"ghost.heart.slow": ["ghost/new/heart_slow"], "ghost.flatline": ["ghost/new/flatline"]})
    return out


def main():
    bed = json.load(open(os.path.join(HERE, "data", "sounds_bedrock.json")))
    new = build()
    js = dict(bed)
    for k, files in new.items():
        js[k] = {"sounds": [{"name": "hollowhours:" + f} for f in files], "subtitle": "subtitles.hollowhours." + k}
    with open(os.path.join(A, "sounds.json"), "w") as f:
        json.dump(js, f, indent=1)
    print("sounds.json:", len(js), "events")


if __name__ == "__main__":
    main()
