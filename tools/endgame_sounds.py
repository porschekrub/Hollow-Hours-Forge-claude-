"""The last chapter's sounds (synthesised): the crying under the mountain, slams, the scream, the rift, the final boom,
plus two pieces of music - the boss fight (a loop) and the end credits. Writes sounds/endgame/*.ogg and sounds.json."""
import json
import os
import subprocess
import wave

import numpy as np
from scipy.signal import butter, lfilter

A = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours')
OUT = os.path.join(A, 'sounds', 'endgame')
SR = 44100
rng = np.random.default_rng(23)


def lp(x, f, o=2):
    b, a = butter(o, min(f, SR / 2 - 100) / (SR / 2), 'low')
    return lfilter(b, a, x)


def hp(x, f, o=2):
    b, a = butter(o, f / (SR / 2), 'high')
    return lfilter(b, a, x)


def bp(x, lo, hi, o=2):
    b, a = butter(o, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], 'band')
    return lfilter(b, a, x)


def t_(d):
    return np.arange(int(d * SR)) / SR


def noise(d):
    return rng.standard_normal(int(d * SR))


def env(d, a, k):
    t = t_(d)
    return np.minimum(1, t / max(a, 1e-4)) * np.exp(-t / k)


def adsr(n, a, r):
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    if na: e[:na] = np.linspace(0, 1, na)
    if nr: e[-nr:] *= np.linspace(1, 0, nr)
    return e


def reverb(x, size=0.6, mix=0.35, tail=2.0):
    """a cheap cavern: a handful of decaying, darkening echoes"""
    out = np.concatenate([x, np.zeros(int(tail * SR))])
    wet = np.zeros_like(out)
    for i, d in enumerate((0.031, 0.047, 0.071, 0.113, 0.167, 0.241, 0.353, 0.5)):
        dd = d * size * 2.2
        g = 0.55 * (0.72 ** i)
        k = int(dd * SR)
        seg = lp(x, 5000 - i * 450)
        m = min(len(seg), len(wet) - k)
        if m > 0:
            wet[k:k + m] += seg[:m] * g
    wet = lp(wet, 3500)
    return out * (1 - mix) + wet * mix * 1.6


def glottal(freq, d):
    """voice source: a band-limited pulse train following a pitch curve"""
    f = np.broadcast_to(freq, (int(d * SR),)) if np.ndim(freq) == 0 else freq
    ph = np.cumsum(f) / SR
    s = np.zeros(len(ph))
    for h in range(1, 28):
        mask = (f * h) < SR / 2 - 500
        s += mask * np.sin(2 * np.pi * h * ph) / h ** 1.15
    return s


def formant(src, vowel):
    F = {'a': ((850, 90), (1610, 110), (2900, 160)), 'u': ((350, 70), (900, 90), (2500, 150)), 'o': ((500, 80), (1000, 100), (2700, 150)),
         'e': ((450, 70), (2100, 120), (2900, 160)), 'i': ((300, 60), (2500, 120), (3300, 170))}[vowel]
    out = 0
    for (fc, bw), g in zip(F, (1.0, 0.6, 0.3)):
        out = out + bp(src, fc - bw, fc + bw, 2) * g
    return out


def vowel_mix(src, curve):
    """curve: per-sample 0 = 'a' .. 1 = 'u'"""
    return formant(src, 'a') * (1 - curve) + formant(src, 'u') * curve


def cry(d=3.6, base=430, seed=0, sobs=True):
    r = np.random.default_rng(seed)
    t = t_(d)
    n = len(t)
    # a wail that rises, breaks and falls; with vibrato and little sobbing catches
    p = base * (1 + 0.18 * np.sin(np.pi * np.clip(t / d, 0, 1)) - 0.22 * np.clip((t - d * 0.6) / (d * 0.4), 0, 1))
    p *= 1 + 0.025 * np.sin(2 * np.pi * 5.6 * t) + 0.01 * r.standard_normal(n).cumsum() / np.sqrt(np.arange(1, n + 1))
    src = glottal(p, d)
    curve = np.clip(0.5 + 0.5 * np.sin(2 * np.pi * 0.35 * t + seed), 0, 1)
    v = vowel_mix(src, curve)
    breath = bp(noise(d), 1500, 6000) * 0.06
    amp = adsr(n, 0.15, 0.8)
    if sobs:
        for s0 in r.uniform(0.3, d - 0.6, 3):
            k = (np.abs(t - s0) < 0.07)
            amp = amp * (1 - 0.85 * k)
    return (v + breath) * amp


def save(name, x, peak=0.9, stereo=False):
    os.makedirs(OUT, exist_ok=True)
    if stereo:
        x = x / np.abs(x).max() * peak
        data = (np.clip(x, -1, 1) * 32767).astype('<i2')
    else:
        x = np.clip(x / np.abs(x).max() * peak, -1, 1)
        data = (x * 32767).astype('<i2')
    tmp = os.path.join(OUT, name + '.wav')
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(2 if stereo else 1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    q = '4' if stereo else '5'
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', q, os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


# ----------------------------------------------------------------------------- effects
def s_cry():
    a = cry(3.8, 440, 1)
    b = cry(3.8, 446, 2) * 0.5
    return reverb(a + b, 0.9, 0.5, 2.5)


def s_scream():
    d = 2.8
    t = t_(d)
    layers = 0
    for i, f in enumerate((210, 318, 425, 640)):
        p = f * (1 + 0.35 * np.clip(t / 0.4, 0, 1) - 0.25 * np.clip((t - 1.4) / 1.4, 0, 1)) * (1 + 0.04 * np.sin(2 * np.pi * (7 + i) * t))
        layers = layers + formant(glottal(p, d), 'a') * (1 - 0.15 * i)
    x = np.tanh(layers * 2.2) * adsr(len(t), 0.05, 1.0)
    x += bp(noise(d), 800, 7000) * env(d, 0.02, 0.6) * 0.6
    return reverb(x, 1.0, 0.45, 2.5)


def s_slam():
    d = 1.6
    t = t_(d)
    thump = np.sin(2 * np.pi * 46 * t * (1 - 0.35 * t)) * env(d, 0.002, 0.28) * 1.6
    crack = hp(noise(d), 500) * env(d, 0.001, 0.03)
    body = lp(noise(d), 300, 3) * env(d, 0.003, 0.35) * 5
    debris = bp(noise(d), 1500, 5000) * env(d, 0.08, 0.4) * (rng.random(len(t)) > 0.97) * 0.8
    return reverb(thump + crack + body + debris, 1.0, 0.4, 1.8)


def s_rise():
    d = 5.0
    t = t_(d)
    rumble = lp(noise(d), 120, 3) * 8 * adsr(len(t), 1.5, 1.2)
    groan = formant(glottal(70 + 20 * np.sin(2 * np.pi * 0.2 * t), d), 'o') * adsr(len(t), 1.2, 1.5) * 0.8
    debris = bp(noise(d), 1200, 5000) * (rng.random(len(t)) > 0.985) * 0.6 * adsr(len(t), 0.5, 1)
    return reverb(rumble + groan + debris + 0.4 * cry(d, 300, 5, False), 1.0, 0.4, 2.5)


def s_defeat():
    d = 7.0
    t = t_(d)
    c = cry(3.0, 460, 9, True)
    c = np.concatenate([c, np.zeros(len(t) - len(c))])
    pad = 0
    for f in (220, 277.2, 329.6, 440, 554.4):  # A major
        pad = pad + np.sin(2 * np.pi * f * t + rng.random() * 6) * (1 + 0.3 * np.sin(2 * np.pi * 0.3 * t))
        pad = pad + 0.5 * np.sin(2 * np.pi * f * 1.003 * t)
    pad = pad * np.clip((t - 2.0) / 2.5, 0, 1) * np.clip((d - t) / 1.5, 0, 1) * 0.12
    return reverb(c * 0.8 + pad, 1.0, 0.5, 3)


def bell(f, d, k=1.6):
    t = t_(d)
    x = 0
    for m, g in ((1, 1), (2.76, 0.5), (5.4, 0.25), (8.93, 0.12)):
        x = x + np.sin(2 * np.pi * f * m * t) * g * np.exp(-t * m / k)
    return x * np.minimum(1, t / 0.002)


def s_release():
    d = 3.0
    x = np.zeros(int(d * SR))
    for i, f in enumerate((880, 1108.7, 1318.5, 1760)):
        b = bell(f, d - i * 0.12, 1.8)
        k = int(i * 0.12 * SR)
        x[k:k + len(b)] += b * 0.5
    return reverb(x, 0.8, 0.4, 2.0)


def s_windup():
    d = 1.3
    t = t_(d)
    inhale = bp(noise(d), 300, 2500) * np.clip(t / d, 0, 1) ** 2 * 0.8
    groan = formant(glottal(60 + 25 * t, d), 'o') * adsr(len(t), 0.3, 0.3) * 0.6
    creak = bp(noise(d), 600, 1200) * (np.sin(2 * np.pi * 18 * t) > 0.9) * 0.4
    return reverb(inhale + groan + creak, 0.8, 0.35, 1.2)


def s_sweep():
    d = 1.2
    t = t_(d)
    sweep = np.zeros(len(t))
    nz = noise(d)
    for lo, hi, w in ((200, 600, 1.0), (500, 1500, 0.7), (1200, 3000, 0.4)):
        sweep += bp(nz, lo, hi) * w
    sweep *= np.exp(-((t - 0.45) / 0.22) ** 2) * 2
    scrape = bp(noise(d), 2000, 6000) * (rng.random(len(t)) > 0.9) * np.exp(-((t - 0.5) / 0.3) ** 2) * 0.5
    return reverb(sweep + scrape, 0.7, 0.3, 1.0)


def s_grab():
    d = 0.9
    t = t_(d)
    thud = np.sin(2 * np.pi * 80 * t) * env(d, 0.002, 0.12)
    squeeze = bp(noise(d), 300, 900) * env(d, 0.05, 0.3) * 0.6
    creak = formant(glottal(90 - 30 * t, d), 'u') * env(d, 0.03, 0.4) * 0.4
    return reverb(thud + squeeze + creak, 0.6, 0.3, 0.8)


def s_tear():
    d = 0.6
    t = t_(d)
    plop = np.sin(2 * np.pi * (900 - 600 * np.clip(t / 0.05, 0, 1)) * t) * env(d, 0.001, 0.05)
    splash = bp(noise(d), 1500, 7000) * env(d, 0.002, 0.08) * 0.5
    return reverb(plop + splash, 0.7, 0.4, 0.8)


def s_hurt():
    d = 0.9
    t = t_(d)
    p = 150 * (1 + 0.5 * np.exp(-t / 0.1)) * (1 - 0.2 * t)
    return reverb(np.tanh(formant(glottal(p, d), 'o') * 1.6) * adsr(len(t), 0.02, 0.4), 0.8, 0.35, 1.0)


def s_rift():
    d = 3.0
    t = t_(d)
    x = 0
    for f, g in ((110, 1), (165, 0.6), (220, 0.5), (330, 0.3), (440, 0.25), (660, 0.12), (880, 0.1)):
        x = x + np.sin(2 * np.pi * f * t + np.sin(2 * np.pi * 0.5 * t) * 0.6) * g
    sparkle = bp(noise(d), 5000, 12000) * 0.05 * (1 + np.sin(2 * np.pi * 3 * t))
    x = x + sparkle
    # loop-friendly: fade the edges
    n = len(x)
    f = int(0.25 * SR)
    x[:f] *= np.linspace(0, 1, f)
    x[-f:] *= np.linspace(1, 0, f)
    return x


def s_boom():
    d = 6.0
    t = t_(d)
    sub = np.sin(2 * np.pi * 32 * t * (1 - 0.15 * t)) * env(d, 0.004, 0.9) * 2
    blast = lp(noise(d), 900, 2) * env(d, 0.002, 0.5) * 3
    crack = hp(noise(d), 1500) * env(d, 0.001, 0.05) * 1.2
    ring = np.sin(2 * np.pi * 3950 * t) * np.clip((t - 0.3) / 0.4, 0, 1) * np.exp(-(t - 0.3).clip(0) / 1.8) * 0.06
    return reverb(sub + blast + crack, 1.2, 0.45, 3)[:int(d * SR)] + np.concatenate([ring, np.zeros(0)])


# ----------------------------------------------------------------------------- music
def note_hz(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def taiko(d=0.9, f=55):
    t = t_(d)
    return (np.sin(2 * np.pi * f * t * (1 - 0.3 * t)) * env(d, 0.002, 0.25) * 1.4 + lp(noise(d), 400) * env(d, 0.001, 0.06) * 1.5)


def string_note(freq, d, bright=1800):
    t = t_(d)
    saw = 0
    for k in range(1, 18):
        saw = saw + np.sin(2 * np.pi * freq * k * t * (1 + 0.0015 * np.sin(2 * np.pi * 5 * t))) / k
    return lp(saw, bright) * adsr(len(t), 0.02, min(0.08, d * 0.3))


def choir_chord(notes, d):
    t = t_(d)
    x = 0
    for n in notes:
        for det in (-0.004, 0, 0.005):
            p = note_hz(n) * (1 + det) * (1 + 0.006 * np.sin(2 * np.pi * 5.2 * t + n))
            x = x + formant(glottal(p, d), 'a') * 0.4
    return x * adsr(len(t), 0.6, 0.6)


def m_boss():
    bpm = 92
    beat = 60 / bpm
    bars = 16
    d = bars * 4 * beat
    n = int(d * SR)
    L = np.zeros(n)
    Rr = np.zeros(n)

    def add(buf, x, at, g=1.0):
        i = int(at * SR)
        x = x[: max(0, n - i)]
        buf[i:i + len(x)] += x * g

    # D minor: Dm - Bb - Gm - A (x4)
    chords = [[50, 53, 57], [46, 50, 53], [43, 46, 50], [45, 49, 52]]
    ost = [38, 38, 41, 38, 40, 38, 36, 33]
    for bar in range(bars):
        t0 = bar * 4 * beat
        ch = chords[bar % 4]
        c = choir_chord([x + 12 for x in ch], 4 * beat)
        g = 0.0 if bar < 2 else 0.35
        add(L, c, t0, g)
        add(Rr, c, t0, g * 0.9)
        # strings ostinato (8ths), from bar 1
        for i, nn in enumerate(ost):
            root_shift = ch[0] - 50 if bar % 4 else 0
            s = string_note(note_hz(nn + root_shift), beat / 2 * 0.95, 2200)
            add(L, s, t0 + i * beat / 2, 0.22)
            add(Rr, s, t0 + i * beat / 2, 0.22)
        # drums: big hits on 1 and the "and" of 2, a roll at the end of every 4th bar
        if bar >= 1:
            for b in (0, 1.5, 2.5):
                add(L, taiko(0.9, 52), t0 + b * beat, 0.8)
                add(Rr, taiko(0.9, 52), t0 + b * beat, 0.8)
            if bar % 4 == 3:
                for k in range(8):
                    add(L, taiko(0.4, 70), t0 + 3 * beat + k * beat / 8, 0.25 + 0.05 * k)
                    add(Rr, taiko(0.4, 70), t0 + 3 * beat + k * beat / 8, 0.25 + 0.05 * k)
        # drone
        dr = string_note(note_hz(26), 4 * beat, 400) * 0.35
        add(L, dr, t0, 1)
        add(Rr, dr, t0, 1)
    # the child crying over it, now and then
    for at, sd in ((4 * 4 * beat + beat, 11), (10 * 4 * beat, 12)):
        c = reverb(cry(3.2, 520, sd), 1.0, 0.6, 2)[: int(4 * beat * SR)] * 0.18
        add(L, c, at, 1)
        add(Rr, c, at + 0.02, 1)
    # loop: carry the tail of the last bar onto the start
    out = np.stack([L, Rr], 1)
    return out


def piano(freq, d, vel=1.0):
    t = t_(d)
    x = 0
    for k, g in ((1, 1), (2, 0.45), (3, 0.22), (4, 0.12), (5, 0.06)):
        x = x + np.sin(2 * np.pi * freq * k * t * (1 + 0.0004 * k * k)) * g * np.exp(-t * (0.9 + 0.5 * k) / max(0.6, d * 0.5))
    hammer = bp(noise(min(d, 0.05)), 1000, 4000) * 0.03
    x[: len(hammer)] += hammer
    return x * vel * np.minimum(1, t / 0.004)


def m_credits():
    bpm = 72
    beat = 60 / bpm
    # A minor, turning to C major and F - G - C at the end
    prog = [("Am", [45, 52, 57, 60]), ("F", [41, 48, 53, 57]), ("C", [36, 43, 48, 52]), ("G", [43, 50, 55, 59]),
            ("Am", [45, 52, 57, 60]), ("F", [41, 48, 53, 57]), ("Dm", [38, 45, 50, 53]), ("E", [40, 47, 52, 56]),
            ("F", [41, 48, 53, 57]), ("G", [43, 50, 55, 59]), ("Em", [40, 47, 52, 55]), ("Am", [45, 52, 57, 60]),
            ("F", [41, 48, 53, 57]), ("G", [43, 50, 55, 59]), ("C", [36, 43, 48, 52]), ("C", [36, 43, 48, 52])]
    melody = [[76, 74, 72, 71], [72, 69, 69, 0], [67, 69, 72, 76], [74, 0, 71, 0],
              [76, 74, 72, 71], [72, 69, 69, 0], [65, 69, 74, 72], [71, 0, 68, 0],
              [69, 72, 77, 76], [74, 72, 74, 0], [71, 72, 74, 76], [72, 0, 69, 0],
              [69, 72, 77, 76], [74, 76, 77, 79], [76, 0, 72, 0], [72, 0, 0, 0]]
    reps = 2
    total_bars = len(prog) * reps
    d = total_bars * 4 * beat + 6
    n = int(d * SR)
    L = np.zeros(n)
    Rr = np.zeros(n)

    def add(x, at, g=1.0, pan=0.0):
        i = int(at * SR)
        x = x[: max(0, n - i)]
        L[i:i + len(x)] += x * g * (1 - pan) / 1.4
        Rr[i:i + len(x)] += x * g * (1 + pan) / 1.4

    for r in range(reps):
        for bi, ((_, ch), mel) in enumerate(zip(prog, melody)):
            bar = r * len(prog) + bi
            t0 = bar * 4 * beat
            # broken chord, left hand
            for k, nn in enumerate([ch[0], ch[1], ch[2], ch[1], ch[3], ch[1], ch[2], ch[1]]):
                add(piano(note_hz(nn), 2.2, 0.32), t0 + k * beat / 2, 1, -0.3)
            # melody (second time an octave up and softer at first)
            for k, nn in enumerate(mel):
                if nn:
                    add(piano(note_hz(nn + (12 if r == 1 and bi < 8 else 0)), 2.6, 0.55), t0 + k * beat, 1, 0.25)
            # a soft pad the second time round
            if r == 1:
                t = t_(4 * beat)
                pad = 0
                for nn in ch:
                    pad = pad + np.sin(2 * np.pi * note_hz(nn + 12) * t) + 0.5 * np.sin(2 * np.pi * note_hz(nn + 12) * 1.004 * t)
                add(pad * adsr(len(t), 0.8, 0.8) * 0.035, t0)
    # final chord rings
    t0 = total_bars * 4 * beat
    for nn in (36, 43, 48, 52, 55, 60, 64):
        add(piano(note_hz(nn), 6, 0.4), t0, 1)
    out = np.stack([L, Rr], 1)
    rv_l = reverb(out[:, 0], 1.0, 0.3, 0)[:n]
    rv_r = reverb(out[:, 1], 1.1, 0.3, 0)[:n]
    return np.stack([rv_l, rv_r], 1)


def main():
    fx = {
        'boss.cry': s_cry(), 'boss.scream': s_scream(), 'boss.slam': s_slam(), 'boss.rise': s_rise(), 'boss.defeat': s_defeat(),
        'boss.release': s_release(), 'boss.windup': s_windup(), 'boss.sweep': s_sweep(), 'boss.grab': s_grab(), 'boss.tear': s_tear(),
        'boss.hurt': s_hurt(), 'rift.hum': s_rift(), 'ending.boom': s_boom(),
    }
    for k, v in fx.items():
        save(k.replace('.', '_'), v)
    save('music_boss', m_boss(), 0.85, stereo=True)
    save('music_credits', m_credits(), 0.8, stereo=True)
    p = os.path.join(A, 'sounds.json')
    d = json.load(open(p))
    subs = {'boss.cry': 'Crying under the mountain', 'boss.scream': 'Something screams', 'boss.slam': 'A huge hand slams down',
            'boss.rise': 'The ground breaks open', 'boss.defeat': 'The crying stops', 'boss.release': 'Something is set free',
            'boss.windup': 'It draws back', 'boss.sweep': 'Something sweeps the floor', 'boss.grab': 'Grabbed', 'boss.tear': 'A black tear falls',
            'boss.hurt': 'It groans', 'rift.hum': 'The rift hums', 'ending.boom': 'BOOM'}
    for k in fx:
        d[k] = {"sounds": [{"name": "hollowhours:endgame/" + k.replace('.', '_')}], "subtitle": "subtitles.hollowhours." + k}
    d['ghost.music.boss'] = {"sounds": [{"name": "hollowhours:endgame/music_boss", "stream": True}]}
    d['ghost.music.credits'] = {"sounds": [{"name": "hollowhours:endgame/music_credits", "stream": True}]}
    json.dump(d, open(p, 'w'), indent=1, ensure_ascii=False)
    print('endgame sounds written:', len(fx) + 2)
    return subs


if __name__ == '__main__':
    main()
