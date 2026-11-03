"""Thai narration for the promo short (Meta MMS text-to-speech), one wav per line + lines.json with their lengths."""
import json, os, subprocess, sys, wave
import numpy as np
import torch
from transformers import VitsModel, AutoTokenizer

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
LINES = [
    "ตอนที่สอง หนีไปดินแดนสวรรค์",
    "คุณกับฟ้า ขึ้นเฮลิคอปเตอร์ทันเวลา",
    "เฮลิคอปเตอร์บินไปไกล จนเห็นแสงอาทิตย์",
    "ที่นี่คือดินแดนสวรรค์ มีต้นซากุระ ผลไม้ และสัตว์เต็มไปหมด",
    "ที่สำคัญ ไม่มีผีเลยสักตัว",
    "ทั้งสองคนสร้างบ้านใหม่ ตกปลา และนอนหลับสบาย เป็นครั้งแรก",
    "ส่วนต้น ส่งข้อความมาว่า ผมยังรอดนะ ส่งขนมมาด้วย",
    "แต่พอถึงวันที่แปด",
    "ท้องฟ้าเริ่มมืด นกบินหนี",
    "โทรศัพท์ดังขึ้น ผีบุกสวรรค์แล้ว",
    "พวกมันตามมาถึงที่นี่",
    "เฮลิคอปเตอร์จะมารับอีกครั้ง คราวนี้รอไม่จำกัดเวลา",
    "แต่มันลงจอด อีกฝั่งของภูเขา",
    "เปิดแอปแผนที่ แล้วตามลูกศรไป",
    "ผีวิ่งเร็วกว่าคนวิ่งปกติ ต้องพุ่งตัว",
    "ฟ้าสะดุดล้ม",
    "คุณหันกลับไป ดึงเธอขึ้นมา",
    "ขึ้นเครื่องได้ ก็ปลอดภัยทันที",
    "แต่เฮลิคอปเตอร์ลำนี้ พากลับไปโลกเดิม ตลอดไป",
    "และที่นั่น ผีโหดขึ้นสามเท่า คืนพระจันทร์สีเลือด หกเท่า",
    "ลงจากเครื่อง คนแรกที่เห็นคือ",
    "ต้น ที่ยังกินขนมอยู่",
    "ถ้าเป็นคุณ จะรอดถึงตอนไหน",
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
