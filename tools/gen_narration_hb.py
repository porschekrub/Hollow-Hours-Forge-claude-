"""Narration for the Helper Buddy promo, in the helper's own voice (KittenTTS via sherpa-onnx, the voice inside the mod).
Writes n_<i>.wav + lines.json, and the first line in every speaker (pick_<sid>.wav)."""
import json, os, subprocess, sys, wave
import numpy as np, sherpa_onnx
OUT, MODEL = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
LINES = [
    "I gave my Minecraft helper one job.",
    "Get me soul sand. And it just... goes to the Nether. By itself.",
    "Where are the diamonds? It tells me the exact spot, and walks me there.",
    "No axe? No problem. It makes one.",
    "It builds you a whole house.",
    "You hit something? It jumps in.",
    "Eighteen outfits. Yes, there's a ninja.",
    "And it talks back. Out loud. For free.",
    "Helper Buddy. Link's on CurseForge.",
]
SID = int(os.environ.get("SID", "0"))
tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    kitten=sherpa_onnx.OfflineTtsKittenModelConfig(model=f"{MODEL}/model.int8.onnx", voices=f"{MODEL}/voices.bin",
                                                    tokens=f"{MODEL}/tokens.txt", data_dir=f"{MODEL}/espeak-ng-data"),
    num_threads=2)))
print("speakers", tts.num_speakers, "rate", tts.sample_rate)

def save(path, samples, sr):
    w = np.asarray(samples, dtype=np.float32)
    w = (np.clip(w / max(1e-6, abs(w).max()) * 0.9, -1, 1) * 32767).astype("int16")
    with wave.open(path, "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(sr); f.writeframes(w.tobytes())

for sid in range(tts.num_speakers):
    a = tts.generate(LINES[0], sid=sid, speed=1.0)
    save(f"{OUT}/pick_{sid}.wav", a.samples, a.sample_rate)
meta = []
for i, t in enumerate(LINES):
    a = tts.generate(t, sid=SID, speed=1.0)
    raw = f"{OUT}/raw_{i}.wav"
    save(raw, a.samples, a.sample_rate)
    out = f"{OUT}/n_{i}.wav"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", raw, "-af",
                    "aresample=44100,highpass=f=60,acompressor=threshold=-18dB:ratio=3,volume=1.4,alimiter=limit=0.95", "-ac", "1", out], check=True)
    d = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", out]))
    meta.append({"text": t, "seconds": round(d, 2)}); print(i, d, t)
json.dump(meta, open(f"{OUT}/lines.json", "w"), indent=1)
