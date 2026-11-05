"""Gun and grenade sounds (synthesised): cracks, booms, echoes, reload clicks. Writes sounds/combat/*.ogg and sounds.json."""
import json, os, subprocess, wave
import numpy as np
from scipy.signal import butter, lfilter

A = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours')
OUT = os.path.join(A, 'sounds', 'combat')
SR = 44100
rng = np.random.default_rng(11)


def lp(x, f, o=2):
    b, a = butter(o, f / (SR / 2), 'low')
    return lfilter(b, a, x)


def hp(x, f, o=2):
    b, a = butter(o, f / (SR / 2), 'high')
    return lfilter(b, a, x)


def bp(x, lo, hi, o=2):
    b, a = butter(o, [lo / (SR / 2), hi / (SR / 2)], 'band')
    return lfilter(b, a, x)


def t_(d):
    return np.arange(int(d * SR)) / SR


def noise(d):
    return rng.standard_normal(int(d * SR))


def env(d, a, k):
    t = t_(d)
    return np.minimum(1, t / max(a, 1e-4)) * np.exp(-t / k)


def echo(x, delays=(0.09, 0.21, 0.37, 0.6), gains=(0.35, 0.22, 0.14, 0.08), tail=0.9):
    out = np.concatenate([x, np.zeros(int(tail * SR))])
    for d, g in zip(delays, gains):
        i = int(d * SR)
        out[i:i + len(x)] += lp(x, 2500) * g
    return out


def shot(crack_k, thump_f, thump_k, body_k, tail, bright=6000, gain_thump=1.0):
    d = 0.6
    crack = hp(noise(d), 900) * env(d, 0.0005, crack_k)
    body = bp(noise(d), 200, bright) * env(d, 0.001, body_k) * 0.8
    t = t_(d)
    thump = np.sin(2 * np.pi * thump_f * t * (1 - 0.4 * t)) * env(d, 0.001, thump_k) * gain_thump
    return echo(crack + body + thump, tail=tail)


def click(f=3200, k=0.004, d=0.05):
    return bp(noise(d), f * 0.6, min(f * 1.6, 18000)) * env(d, 0.0003, k)


def at(parts, d):
    out = np.zeros(int(d * SR))
    for start, x in parts:
        i = int(start * SR)
        out[i:i + len(x)] += x[: max(0, len(out) - i)]
    return out


def grenade():
    d = 2.2
    t = t_(d)
    crack = hp(noise(d), 600) * env(d, 0.0005, 0.02) * 1.2
    rumble = lp(noise(d), 180, 3) * env(d, 0.004, 0.55) * 6
    boom = np.sin(2 * np.pi * 42 * t * (1 - 0.3 * t)) * env(d, 0.002, 0.3) * 1.2
    debris = bp(noise(d), 1500, 6000) * env(d, 0.08, 0.35) * 0.25 * (rng.random(len(t)) > 0.985)
    return crack + rumble + boom + debris


def save(name, x, peak=0.92):
    os.makedirs(OUT, exist_ok=True)
    x = np.clip(x / np.abs(x).max() * peak, -1, 1)
    tmp = os.path.join(OUT, name + '.wav')
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


def main():
    sounds = {
        'pistol': shot(0.006, 110, 0.05, 0.03, 0.7),
        'rifle': shot(0.005, 95, 0.04, 0.025, 0.6, bright=7000),
        'shotgun': shot(0.012, 70, 0.09, 0.07, 0.9, bright=4000, gain_thump=1.6),
        'sniper': shot(0.009, 80, 0.08, 0.05, 1.4, bright=5000, gain_thump=1.3),
        'grenade': grenade(),
        'reload': at([(0.0, click(2200, 0.006, 0.06)), (0.05, click(1200, 0.01, 0.08) * 0.6), (0.42, click(2600, 0.005, 0.05)),
                      (0.46, click(1500, 0.012, 0.08) * 0.8)], 0.65),
        'cock': at([(0.0, click(3000, 0.004)), (0.12, click(2400, 0.006) * 1.2)], 0.3),
        'empty': click(4200, 0.003, 0.04),
    }
    for k, v in sounds.items():
        save(k, v)
    p = os.path.join(A, 'sounds.json')
    d = json.load(open(p))
    for k in sounds:
        d['gun.' + k] = {"sounds": [{"name": "hollowhours:combat/" + k}], "subtitle": "subtitles.hollowhours.gun." + k}
    json.dump(d, open(p, 'w'), indent=1, ensure_ascii=False)
    print('combat sounds written:', ', '.join(sounds))


if __name__ == '__main__':
    main()
