**Helper Buddy 2.19.0 — Minecraft Java 1.20.1, Forge 47.x** (put the jar in your `mods` folder; GeckoLib is inside)
**Helper Buddy 2.19.0 — Minecraft Java 1.20.1, Forge 47.x** (ใส่ไฟล์ในโฟลเดอร์ `mods` ได้เลย GeckoLib อยู่ในไฟล์แล้ว)

Other loaders and Minecraft versions: the last full release (v2.17.0). / ตัวโหลดอื่นและเวอร์ชันอื่น: ดู release เต็มล่าสุด (v2.17.0)

**2.19.0 — its own voice, any AI voice, and a local AI you set up in the game**
- 🔊 **The helper has its own voice now, made on your computer**: free, no key, no internet, no quota, with any AI (also Ollama, Claude ...) and without one. This is the default. English voices are in the mod (KittenTTS, 8 speakers); the **Thai voice** (Meta MMS-TTS) is downloaded once by itself (108 MB) the first time a helper speaks Thai, or with the button on the *Voice* page. It can't be packed into the mod: its licence (CC-BY-NC 4.0) does not allow it everywhere.
  **ผู้ช่วยมีเสียงของตัวเองแล้ว ทำในคอมของคุณเอง**: ฟรี ไม่ต้องใช้ key ไม่ต้องต่อเน็ต ไม่มีโควตา ใช้ได้กับ AI ทุกเจ้า (รวม Ollama, Claude) และตอนไม่ใช้ AI ด้วย เป็นค่าเริ่มต้น เสียงอังกฤษอยู่ในมอด (8 เสียง) **เสียงไทย** จะโหลดเองครั้งเดียว (108 MB) ตอนผู้ช่วยพูดไทยครั้งแรก หรือกดปุ่มในหน้า *Voice* (ใส่ในมอดไม่ได้เพราะสัญญาอนุญาต CC-BY-NC 4.0)
- 🎚️ **New *Voice* page** in the helper's menu: where the voice comes from (*Mod voice*, *AI voice for answers*, *AI voice cartoon*, *Babble*, *No voice*), **this helper's voice** (14 to pick from: 8 voices plus Tiny, Kid, Deep, Giant, Fast talker, Calm) with *Listen*, and the **AI voice** with its own service, key, model and voice: Gemini, OpenAI, OpenRouter, Groq (English), Ollama (Mac) or a custom URL. So the brain can be Groq or Claude and the voice Gemini. When the AI voice can't say a line (no quota, a language it doesn't speak) the helper says it in its own voice instead of going quiet.
  **หน้า *Voice* ใหม่**: เลือกแหล่งเสียง (เสียงของมอด / เสียง AI ตอนตอบ / เสียง AI แบบการ์ตูน / ตุดๆ / ปิด) **เลือกเสียงของผู้ช่วยแต่ละตัว** (14 แบบ) พร้อมปุ่มฟัง และตั้ง **เสียง AI** แยกจากสมองได้ (เจ้า key โมเดล เสียง) เช่น สมองใช้ Groq หรือ Claude เสียงใช้ Gemini ถ้าเสียง AI พูดประโยคไหนไม่ได้ จะใช้เสียงของมอดแทน ไม่เงียบ
- 🖥️ **Local AI setup (Ollama) in the game**: pick *Ollama* on the AI page and press *Local AI setup*: **Install Ollama** downloads the official release from github.com/ollama (Windows and Mac, no installer, no admin rights), starts it, and **downloads a model** from a list with what each one needs (with progress, cancel, delete, use). It starts with the world and stops when you leave. Free and private, but small local models are less clever than Gemini (Thai especially) and need a good graphics card. On Linux install Ollama yourself; the page finds it.
  **ตั้งค่า AI ในเครื่อง (Ollama) ในเกม**: เลือก *Ollama* ในหน้า AI แล้วกด *Local AI setup*: **Install Ollama** โหลดตัวทางการจาก github.com/ollama (Windows และ Mac ไม่ต้องติดตั้ง ไม่ต้องใช้สิทธิ์แอดมิน) เปิดให้เอง แล้ว**โหลดโมเดล**จากรายการพร้อมบอกว่าแต่ละตัวต้องใช้การ์ดจอแค่ไหน (มีแถบความคืบหน้า ยกเลิก ลบ เลือกใช้) เปิดพร้อมโลกและปิดตอนออก ฟรีและเป็นส่วนตัว แต่โมเดลเล็กฉลาดน้อยกว่า Gemini (โดยเฉพาะภาษาไทย) และต้องมีการ์ดจอดีพอ บน Linux ให้ติดตั้ง Ollama เองแล้วหน้านี้จะหาเจอ
- Fixed: the AI voice sometimes read out "say this like ..." before the line.
  แก้บั๊ก: เสียง AI บางทีอ่านคำสั่ง "say this like ..." ออกมาด้วย
- Works next to Verity JE: both use the same speech library, and Forge loads one copy of it.
  ใช้คู่กับ Verity JE ได้: ใช้ไลบรารีเสียงตัวเดียวกัน Forge โหลดแค่ชุดเดียว
- The jar is bigger now (about 60 MB): the speech engine for Windows, Mac and Linux and the English voices are inside.
  ไฟล์มอดใหญ่ขึ้น (ราว 60 MB) เพราะมีตัวทำเสียงสำหรับ Windows, Mac, Linux และเสียงอังกฤษอยู่ข้างใน

**2.18.3 — fighting with you**
- **It attacks what you attack**, like a tamed wolf: hit an iron golem, a villager, a wolf, an enderman ... and your helper joins in (before, it only ever fought monsters and just stood there watching). Your own pets and horses are never attacked.
  **มันตีตัวที่คุณตี** เหมือนหมาที่เลี้ยงไว้: ตีไอร่อนโกเลม ชาวบ้าน หมาป่า เอนเดอร์แมน ... ผู้ช่วยจะเข้ามาช่วยทันที (เดิมมันสู้แค่มอนสเตอร์ เลยยืนมองเฉยๆ) แต่สัตว์เลี้ยงกับม้าของคุณเองจะไม่โดน
- **"ไปล่ามัน", "ตีมัน", "ตีตัวนั้น", "kill it", "attack that"**: it goes for the one you are looking at, or the one you just hit, or the one that hit you (before, "ล่า" sent it off hunting animals for food).
  **"ไปล่ามัน", "ตีมัน", "ตีตัวนั้น"**: มันไปตีตัวที่คุณมองอยู่ หรือตัวที่คุณเพิ่งตี หรือตัวที่ตีคุณ (เดิม "ล่า" ทำให้มันไปล่าสัตว์หาอาหารแทน)
- **"ตีไอร่อนโกเลม", "attack the iron golem", "ฆ่าชาวบ้าน 2 ตัว"**: any creature by name, not only monsters. "สู้" / "fight" also goes first for whatever you are fighting.
  **"ตีไอร่อนโกเลม", "ฆ่าชาวบ้าน 2 ตัว"**: เรียกชื่อสัตว์ตัวไหนก็ได้ ไม่ใช่แค่มอนสเตอร์ และ "สู้" จะไปช่วยตีตัวที่คุณกำลังสู้อยู่ก่อน

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
