"""Thai narration for the promo short (Meta MMS text-to-speech), one wav per line + lines.json with their lengths."""
import json, os, subprocess, sys, wave
import numpy as np
import torch
from transformers import VitsModel, AutoTokenizer

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
LINES = [
    "ถ้าคืนนี้ ผีบุกบ้านคุณ",
    "วันที่สาม โทรศัพท์ทุกเครื่อง จะประกาศเตือนภัยฉุกเฉิน",
    "กฎข้อแรก อย่าให้มันเห็นคุณ เด็ดขาด",
    "ตอกไม้ปิดหน้าต่าง ยิ่งหนา ยิ่งทนนาน",
    "ถ้าได้ยินเสียงเคาะประตู ห้ามเปิด",
    "ซ่อนในตู้ หรือใต้เตียง แล้วกลั้นหายใจไว้",
    "ไฟฉายช่วยได้ แต่ถ่านมีจำกัด",
    "คืนพระจันทร์สีเลือด ผีจะมาบ่อยขึ้นสามเท่า",
    "ถ้ารอดถึงวันที่เจ็ด จะมีข่าวดี",
    "เฮลิคอปเตอร์กู้ภัยจะมารับ แต่รอแค่ห้านาที",
    "ถ้าพลาด ผีจะโหดขึ้นสองเท่า",
    "อยากรู้ว่าคุณจะรอดมั้ย",
    "ลองเล่นมอด ฮอลโลว์ อาวส์ ในไมน์คราฟต์ โหลดฟรีที่ เคิร์สฟอร์จ",
]
model = VitsModel.from_pretrained("facebook/mms-tts-tha")
tok = AutoTokenizer.from_pretrained("facebook/mms-tts-tha")
model.config.speaking_rate = 1.0
model.noise_scale = 0.55
meta = []
for i, text in enumerate(LINES):
    torch.manual_seed(11)
    with torch.no_grad():
        wav = model(**tok(text, return_tensors="pt")).waveform[0].numpy()
    wav = (np.clip(wav / max(1e-6, abs(wav).max()) * 0.9, -1, 1) * 32767).astype("int16")
    raw = os.path.join(OUT, f"raw_{i}.wav")
    with wave.open(raw, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(model.config.sampling_rate); w.writeframes(wav.tobytes())
    out = os.path.join(OUT, f"line_{i}.wav")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", raw, "-af",
                    "highpass=f=70,acompressor=threshold=-20dB:ratio=3:attack=10:release=120,volume=1.6,alimiter=limit=0.95",
                    "-ac", "1", "-ar", "44100", out], check=True)
    os.remove(raw)
    dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", out],
                               capture_output=True, text=True, check=True).stdout.strip())
    meta.append({"text": text, "seconds": round(dur, 2)})
    print(i, round(dur, 2), text)
json.dump(meta, open(os.path.join(OUT, "lines.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
