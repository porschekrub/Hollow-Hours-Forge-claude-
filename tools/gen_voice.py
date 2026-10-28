"""Emergency-alert voice lines (Thai + English) for the phone, run on GitHub Actions (.github/workflows/voice.yml).

Each line is spoken by Meta's MMS text-to-speech (espeak-ng as a fallback), then run through a phone-speaker /
emergency-broadcast filter and saved as alert_<lang>_<n>.ogg, with alert_<lang>.json listing each line's text and
length so the game can show matching subtitles.
"""
import json, os, subprocess, sys, wave

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
os.makedirs(OUT, exist_ok=True)

LINES = {
    "th": [
        "ประกาศเตือนภัยฉุกเฉิน",
        "ขณะนี้มีสิ่งมีชีวิตประหลาด หลุดออกมาจากที่ใดไม่ทราบ",
        "กรุณาอยู่แต่ในบ้าน",
        "ล็อกประตูและหน้าต่างให้เรียบร้อย",
        "และอย่าให้มันเห็นคุณ เด็ดขาด",
        "ขอย้ำ อย่าให้มันเห็นคุณ เด็ดขาด",
    ],
    "en": [
        "Emergency alert.",
        "Strange creatures have escaped from an unknown location.",
        "Stay inside your home.",
        "Lock all doors and windows.",
        "And do not let them see you.",
        "I repeat. Do not let them see you.",
    ],
}
MODELS = {"th": "facebook/mms-tts-tha", "en": "facebook/mms-tts-eng"}
ESPEAK = {"th": "th", "en": "en-us"}


def mms(lang):
    try:
        import torch
        from transformers import VitsModel, AutoTokenizer
        model = VitsModel.from_pretrained(MODELS[lang])
        tok = AutoTokenizer.from_pretrained(MODELS[lang])
        if getattr(tok, "is_uroman", False):
            print("model wants uroman input, using espeak for", lang)
            return None
        model.config.speaking_rate = 0.92
        model.noise_scale = 0.5

        def speak(text, path):
            torch.manual_seed(7)
            with torch.no_grad():
                wav = model(**tok(text, return_tensors="pt")).waveform[0].numpy()
            import numpy as np
            wav = (np.clip(wav / max(1e-6, abs(wav).max()) * 0.9, -1, 1) * 32767).astype("int16")
            with wave.open(path, "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(model.config.sampling_rate)
                w.writeframes(wav.tobytes())
        return speak
    except Exception as e:  # noqa
        print("MMS failed for", lang, e)
        return None


def espeak(lang):
    def speak(text, path):
        subprocess.run(["espeak-ng", "-v", ESPEAK[lang], "-s", "140", "-p", "35", "-w", path, text], check=True)
    return speak


# phone speaker / emergency broadcast: band-limited, compressed, a little grit and a hall echo
FILTER = ("highpass=f=280,lowpass=f=3600,acompressor=threshold=-18dB:ratio=6:attack=5:release=80,"
          "volume=2.2,alimiter=limit=0.9,aecho=0.8:0.55:70|140:0.25|0.12,apad=pad_dur=0.25")

for lang, lines in LINES.items():
    speak = mms(lang) or espeak(lang)
    meta = []
    for i, text in enumerate(lines):
        raw = os.path.join(OUT, f"raw_{lang}_{i}.wav")
        speak(text, raw)
        ogg = os.path.join(OUT, f"alert_{lang}_{i}.ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", raw, "-af", FILTER, "-ac", "1", "-ar", "44100",
                        "-c:a", "libvorbis", "-q:a", "5", ogg], check=True)
        dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", ogg],
                                   capture_output=True, text=True, check=True).stdout.strip())
        os.remove(raw)
        meta.append({"text": text, "seconds": round(dur, 2)})
        print(lang, i, round(dur, 2), text)
    with open(os.path.join(OUT, f"alert_{lang}.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
