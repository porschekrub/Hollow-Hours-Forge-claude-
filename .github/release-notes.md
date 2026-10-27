**Minecraft Java 1.20.1 + Forge 47.x** (not NeoForge). GeckoLib is bundled — just put the jars in your `mods` folder.
**Minecraft Java 1.20.1 + Forge 47.x** (ไม่ใช่ NeoForge) GeckoLib อยู่ในไฟล์แล้ว แค่ใส่ไฟล์ .jar ในโฟลเดอร์ `mods`

- `hollowhours-forge-1.20.1-2.1.0.jar` — Hollow Hours (ghost horror / ผีสยองขวัญ)
- `helperbuddy-forge-1.20.1-2.1.0.jar` — Helper Buddy (helper NPC / ผู้ช่วย)

**2.1.0**
- Ghosts + AI: with an API key the ghost now *understands* every sentence (questions, begging, insults, offering ANY item, asking it for any item) instead of only writing its answers. Set it up in `config/hollowhours-common.toml` (see README).
  ผี + AI: ใส่ API key แล้วผีจะ*เข้าใจ*ทุกประโยค (ถาม อ้อนวอน ด่า เสนอของอะไรก็ได้ ขอของอะไรก็ได้) ตั้งค่าในไฟล์ `config/hollowhours-common.toml` (ดู README)
- New AI providers for both mods: `openrouter`, `groq`. A base url such as `http://localhost:11434` works for ollama/custom.
  เพิ่มผู้ให้บริการ AI: openrouter, groq

**2.0.1**
- Fixed stretched "tunnels" during kills and spinning ghost heads/arms; the grab animation always plays; other mobs can't kill you while a ghost holds you; Helper Buddy's head no longer drifts.
  แก้ตัวผียืดเป็นแท่งตอนฆ่า หัว/แขนผีหมุนเพี้ยน ท่าจับตัวเล่นทุกครั้ง มอนสเตอร์อื่นตีแทรกฉากไม่ได้ หัวผู้ช่วยไม่เพี้ยน

Remove the old jars from `mods` before adding these. / ลบไฟล์เวอร์ชันเก่าออกจาก `mods` ก่อนใส่ไฟล์ใหม่

See the README for how to play / วิธีเล่นดูใน README
