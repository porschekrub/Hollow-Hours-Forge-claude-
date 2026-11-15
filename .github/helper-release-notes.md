**Helper Buddy 2.18.2 — Minecraft Java 1.20.1, Forge 47.x** (put the jar in your `mods` folder; GeckoLib is inside)
**Helper Buddy 2.18.2 — Minecraft Java 1.20.1, Forge 47.x** (ใส่ไฟล์ในโฟลเดอร์ `mods` ได้เลย GeckoLib อยู่ในไฟล์แล้ว)

Other loaders and Minecraft versions: the last full release (v2.17.0). / ตัวโหลดอื่นและเวอร์ชันอื่น: ดู release เต็มล่าสุด (v2.17.0)

**2.18.2 — the voice**
- The real AI voice is now used for **its answers to what you say** (the lines it says by itself while working are written only), so a free Gemini key's small speech quota lasts much longer.
  เสียง AI ใช้กับ**คำตอบที่ตอบคุณ**เท่านั้น (ประโยคที่พูดเองตอนทำงานเป็นข้อความ) โควตาเสียงของ key ฟรีจึงอยู่ได้นานขึ้นมาก
- When the voice can't be made it now **says why** in grey (speech quota used up, the key may not use speech, no speech model, no connection) instead of going quiet without a word; it tries again by itself after 5 minutes.
  ถ้าทำเสียงไม่ได้ จะ**บอกสาเหตุ**เป็นข้อความสีเทา (โควตาเสียงหมด key ใช้เสียงไม่ได้ ไม่มีโมเดลเสียง ต่อไม่ติด) แทนการเงียบไปเฉยๆ และลองใหม่เองใน 5 นาที
- A speech model that is gone is skipped at once (the same line is tried with the next one), so no answer is lost while it finds one that works.
  ถ้าโมเดลเสียงตัวไหนใช้ไม่ได้ จะลองตัวถัดไปทันทีกับประโยคเดิม ไม่มีคำตอบหาย

- 🤖 **New "AI" page in the helper's menu** (right-click your helper → *AI*): turn the AI on, pick the provider (Gemini, OpenAI, Claude, OpenRouter, Groq, Ollama, or your own URL), paste the **API key**, pick the **model**, press *Save and test the AI*. The helper tries it at once and tells you in chat whether it works (and why not). After a save the arrows next to *Model* go through the models your key really has.
  **หน้า "AI" ใหม่ในเมนูผู้ช่วย** (คลิกขวาที่ผู้ช่วย → *AI*): เปิด AI เลือกเจ้า วาง **API key** เลือก**โมเดล** แล้วกด *Save and test the AI* ผู้ช่วยลองต่อทันทีแล้วบอกในแชตว่าใช้ได้ไหม (ถ้าไม่ได้บอกสาเหตุ) หลังบันทึก ลูกศรข้างช่อง *Model* ไล่ดูโมเดลที่ key ใช้ได้จริง
- 🔑 The key is typed hidden (*Show* to see it), saved in `config/helperbuddy-common.toml`, and never sent back to anyone's screen (only its last 4 characters). Only the host of the world (or a server operator) can change the AI.
  key ถูกซ่อนตอนพิมพ์ บันทึกในไฟล์ config และไม่ถูกส่งกลับไปที่หน้าจอใคร (โชว์แค่ 4 ตัวท้าย) เฉพาะเจ้าของโลกหรือแอดมินที่ตั้งค่า AI ได้
- 🌐 **The language it speaks**: *Speaks: English* (the default), Thai, Japanese ... 25 languages, or *as I type*.
  **ภาษาที่มันพูด**: ค่าเริ่มต้นอังกฤษ เลือกไทย ญี่ปุ่น ... 25 ภาษา หรือ *as I type* (ตอบตามภาษาที่พิมพ์)
- 🗣️ **The way it talks, in your own words** (*Style*): "sweet and polite", "like a pirate", "พูดหยาบๆ กูมึง" ... empty = its normal way. Needs the AI to be on.
  **ลักษณะการพูดพิมพ์เองได้** (ช่อง *Style*) เว้นว่าง = แบบปกติ ต้องเปิด AI
- 🔊 A *Voice* button on the same page (auto / ai / cartoon / babble / off).
  ปุ่ม *Voice* ในหน้าเดียวกัน

Everything from 2.17.0 is in here too (gets anything, makes its own tools, X Y Z, calmer talk, speed levels, 18 outfits).
มีทุกอย่างของ 2.17.0 ด้วย
