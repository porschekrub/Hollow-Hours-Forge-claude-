"""Phone sound effects (synthesised): notification, emergency alert tone, distant siren, click, charging, game over."""
import numpy as np, os, subprocess, sys, wave

OUT = os.path.join(os.path.dirname(__file__), '..', 'hollow-hours', 'src', 'main', 'resources', 'assets', 'hollowhours', 'sounds', 'phone')
SR = 44100
os.makedirs(OUT, exist_ok=True)


def save(name, x):
    x = np.clip(x / max(1e-6, np.abs(x).max()) * 0.9, -1, 1)
    tmp = os.path.join(OUT, name + '.wav')
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, a=0.005, r=0.1):
    e = np.ones(n)
    na, nr = int(SR * a), int(SR * r)
    e[:na] = np.linspace(0, 1, na)
    e[-nr:] *= np.linspace(1, 0, nr)
    return e


def bell(f, sec, decay=6):
    x = t(sec)
    s = np.sin(2 * np.pi * f * x) + 0.4 * np.sin(2 * np.pi * f * 2.01 * x) + 0.15 * np.sin(2 * np.pi * f * 3.02 * x)
    return s * np.exp(-decay * x) * env(len(x), 0.002, 0.02)


# notification: two bright notes
n = np.concatenate([bell(1318.5, 0.16, 14), bell(1760, 0.45, 8)])
save('notify', n)

# emergency alert tone: 853 Hz + 960 Hz together (the broadcast attention signal), 3.6 s, harsh and loud
x = t(3.6)
eas = np.sin(2 * np.pi * 853 * x) + np.sin(2 * np.pi * 960 * x)
eas = np.tanh(eas * 1.8)
gate = (np.sin(2 * np.pi * 1.25 * x) > -0.85).astype(float)
save('eas', eas * gate * env(len(x), 0.01, 0.05))

# distant siren: rising and falling, far away (low-passed, echo)
x = t(7.0)
f = 520 + 380 * (0.5 - 0.5 * np.cos(2 * np.pi * x / 3.5))
ph = 2 * np.pi * np.cumsum(f) / SR
s = np.sin(ph) + 0.3 * np.sin(2 * ph)
s *= env(len(x), 0.8, 1.5)
k = int(SR * 0.0008)
s = np.convolve(s, np.ones(k) / k, mode='same')
echo = np.zeros_like(s)
for d, g in ((0.21, 0.5), (0.47, 0.3), (0.83, 0.18)):
    i = int(SR * d)
    echo[i:] += s[:-i] * g
save('siren', (s + echo) * 0.6)

# click
x = t(0.03)
save('click', np.sin(2 * np.pi * 2400 * x) * np.exp(-180 * x))

# charging: a rising three-note chime + electric hum
c = np.concatenate([bell(880, 0.12, 12), bell(1108.7, 0.12, 12), bell(1318.5, 0.5, 6)])
x = t(len(c) / SR)
c = c + 0.08 * np.sin(2 * np.pi * 120 * x) * env(len(x), 0.05, 0.2)
save('charge', c)

# game over blip: falling tones
g = np.concatenate([bell(f, 0.09, 18) for f in (784, 659, 523, 392)])
save('game', g)
print(sorted(os.listdir(OUT)))
