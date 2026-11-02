"""The rescue helicopter's rotor (synthesised, a seamless 2-second loop): the 'whop' of the blades passing 11 times a
second, the turbine's whine and the rush of the downwash."""
import json, os, subprocess, wave
import numpy as np

A = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours')
OUT = os.path.join(A, 'sounds', 'rescue')
SR, SEC, BPS = 44100, 2.0, 11


def lowpass(x, fc):
    a = np.exp(-2 * np.pi * fc / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def rotor():
    rng = np.random.default_rng(3)
    n, k = int(SR * SEC), int(SR * 0.15)
    t = np.arange(n + k) / SR
    # the blade slap: a burst of low noise and a thump on every pass
    phase = (t * BPS) % 1.0
    env = np.exp(-phase / 0.045) + 0.25 * np.exp(-(1 - phase) / 0.02)
    noise = rng.standard_normal(n + k)
    slap = lowpass(lowpass(noise, 520), 520) * env * 3.2
    thump = np.sin(2 * np.pi * 58 * t) * np.exp(-phase / 0.06) * 0.9
    # the downwash and the turbine
    rush = lowpass(noise[::-1].copy(), 900) * 0.35
    whine = (np.sin(2 * np.pi * 1630 * t + 0.6 * np.sin(2 * np.pi * 0.5 * t)) + 0.4 * np.sin(2 * np.pi * 3260 * t)) * 0.035
    x = slap + thump + rush + whine
    # loop seam: everything repeats every 2 s except the noise, so the 0.15 s past the end fades into the start
    w = np.linspace(0, 1, k)
    y = x[:n].copy()
    y[:k] = x[:k] * w + x[n:] * (1 - w)
    return y


def save(name, x):
    os.makedirs(OUT, exist_ok=True)
    x = np.clip(x / np.abs(x).max() * 0.9, -1, 1)
    tmp = os.path.join(OUT, name + '.wav')
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


def main():
    save('rotor', rotor())
    p = os.path.join(A, 'sounds.json')
    d = json.load(open(p))
    d['rescue.rotor'] = {"sounds": [{"name": "hollowhours:rescue/rotor"}], "subtitle": "subtitles.hollowhours.rescue.rotor"}
    json.dump(d, open(p, 'w'), indent=1, ensure_ascii=False)
    print('rescue sounds written')


if __name__ == '__main__':
    main()
