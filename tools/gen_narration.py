"""Narration for the TikTok clip: Meta MMS English TTS, a slightly deeper, spooky voice. Writes n_<i>.wav + lines.json."""
import json, os, subprocess, sys, wave
import numpy as np, torch
from transformers import VitsModel, AutoTokenizer
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
LINES = [
    "Hollow Hours. Now on Forge, NeoForge, and Fabric.",
    "Five versions. Pick the one for your game.",
    "Five ghosts hunt you.",
    "They grab you. Break free, or die.",
    "Under the blood moon, they hit three times harder.",
    "Sell out a friend, or refuse, and die.",
    "A helicopter is coming. It waits five minutes.",
    "Then face the thing under the mountain.",
    "And talk to them, in any language.",
    "Hollow Hours. Get your version in the Files tab.",
]
m = VitsModel.from_pretrained("facebook/mms-tts-eng"); tok = AutoTokenizer.from_pretrained("facebook/mms-tts-eng")
m.config.speaking_rate = 0.95; m.noise_scale = 0.55
meta = []
for i, t in enumerate(LINES):
    torch.manual_seed(11)
    with torch.no_grad(): w = m(**tok(t, return_tensors="pt")).waveform[0].numpy()
    w = (np.clip(w / max(1e-6, abs(w).max()) * 0.9, -1, 1) * 32767).astype("int16")
    raw = f"{OUT}/raw_{i}.wav"
    with wave.open(raw, "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(m.config.sampling_rate); f.writeframes(w.tobytes())
    out = f"{OUT}/n_{i}.wav"
    sr = m.config.sampling_rate
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", raw, "-af",
        f"asetrate={sr}*0.9,aresample=44100,atempo=1.08,highpass=f=70,acompressor=threshold=-20dB:ratio=4,aecho=0.8:0.4:60|120:0.18|0.08,volume=1.6,alimiter=limit=0.95",
        "-ac", "1", out], check=True)
    d = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", out]))
    meta.append({"text": t, "seconds": round(d, 2)}); print(i, d, t)
json.dump(meta, open(f"{OUT}/lines.json", "w"), indent=1)
