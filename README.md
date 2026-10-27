# Hollow Hours + Helper Buddy — Forge 1.20.1

Two Java Forge mods remade from the Bedrock add-ons, built and tested automatically by GitHub Actions.
สองมอด Java Forge ที่ทำใหม่จากแอดออน Bedrock — GitHub build และทดสอบให้อัตโนมัติ

## Download / ดาวน์โหลด
Open **Releases** (right side of the repository page) and download the `.jar` files.
เปิดหน้า **Releases** (ด้านขวาของหน้า repository) แล้วดาวน์โหลดไฟล์ `.jar`

Put the jars in your `.minecraft/mods` folder. Requirements:
ใส่ไฟล์ .jar ในโฟลเดอร์ `.minecraft/mods` ต้องใช้:
- Minecraft Java **1.20.1**
- **Forge 47.x** (not NeoForge / ไม่ใช่ NeoForge)
- GeckoLib is already inside the jars, nothing else to install / GeckoLib อยู่ในไฟล์แล้ว ไม่ต้องลงเพิ่ม

---

## 👻 Hollow Hours
Hardcore ghost horror. Five ghosts stalk you, search your hiding spots, break doors and windows and talk back.
สยองขวัญผีแบบฮาร์ดคอร์ ผี 5 ตัวตามล่า ค้นที่ซ่อน พังประตูหน้าต่าง และพูดโต้ตอบได้

- **Kills / ฉากตาย**: the ghost grabs you, screams in your face and beats you to death — every ghost has 3 different kill cinematics, picked at random.
  ผีจับตัว กรีดร้องใส่หน้า แล้วทุบจนตาย ผีแต่ละตัวมีท่าฆ่า 3 แบบ สุ่มทุกครั้ง
- **Jumpscares / จัมป์สแกร์**: faces, getting dragged out from under the bed, stalkers that are only there when you don't look.
  หน้าผีโผล่ ถูกลากออกจากใต้เตียง ผีที่ยืนจ้องอยู่เมื่อคุณไม่มอง
- **Blood moon / คืนพระจันทร์สีเลือด**: 10% of nights the moon rises red. Until dawn ghosts come 3× as often, hit 3× as hard, run faster, break in easier — and a hunt can bring two ghosts at once.
  10% ของทุกคืน พระจันทร์จะเป็นสีเลือด จนถึงเช้า ผีมาถี่ขึ้น 3 เท่า ตีแรงขึ้น 3 เท่า วิ่งเร็วขึ้น พังบ้านง่ายขึ้น และอาจมาทีเดียว 2 ตัว
- **Fog / หมอก**: thick fog rolls in now and then (more at night). / บางช่วงหมอกหนาจะลง (กลางคืนบ่อยกว่า)
- **Horror shader / เชดเดอร์**: built in — cold drained colours, vignette, film grain, a heartbeat pulse when a ghost is near, red under the blood moon. Turn it off in `config/hollowhours-client.toml` (`shader = false`) if you use a shader pack.
  มีเชดเดอร์ในตัว สีหม่นเย็น ขอบจอมืด เกรนฟิล์ม จอเต้นตามหัวใจเวลาผีอยู่ใกล้ แดงทั้งจอตอนพระจันทร์สีเลือด ปิดได้ใน `config/hollowhours-client.toml` (`shader = false`)
- **Scared player / ตัวละครกลัวผี**: your character hunches, trembles, hugs itself, looks over its shoulder, flails while running away, limps when hurt and struggles when grabbed; in first person your hands shake and your breathing sways the view.
  ตัวละครเดินหลังค่อม ตัวสั่น กอดตัวเอง หันมองข้างหลัง วิ่งหนีแขนแกว่งมั่ว เดินกะเผลกตอนเลือดน้อย ดิ้นตอนโดนจับ มุมมองบุคคลที่ 1 มือสั่นและจอโยกตามลมหายใจ
- Test commands (op): `/hollow bloodmoon on|off|random`, `/hollow fog on|off`
- **Sell out a friend / ขายเพื่อน**: 25% of the time the ghost offers to spare you if you tell it where a friend is. Pick a friend or refuse — 10 seconds. Refusing or waiting = death.
  25% ผีจะถามว่า "บอกมาว่าเพื่อนอยู่ไหน แล้วฉันอาจไว้ชีวิต" เลือกเพื่อน หรือ "ไม่ขาย" ภายใน 10 วินาที ไม่ตอบ/ไม่ขาย = ตาย
- **Talking / คุยกับผี**: type in chat starting with its short name (`wail`, `widow`, `ash`, `void`, `stag`), e.g. `stag please let me go`, or use the guide book → *Talk*. Ask questions, beg, offer items, insult it, or ask it for an item (cursed).
  พิมพ์ในแชตขึ้นต้นด้วยชื่อย่อ เช่น `stag ขอร้อง ปล่อยฉันไป` หรือใช้สมุดคู่มือ → *คุยกับผี* ถามคำถาม อ้อนวอน เสนอของ ด่า หรือขอของจากผี (ของจะติดคำสาป)
- **AI (optional) / AI (ไม่บังคับ)**: with an API key the ghost understands **any** sentence — every way of asking for or offering any item — and answers in its own words. The game still decides the outcome (chances, the item taken, rage).
  ใส่ API key แล้วผีจะเข้าใจ**ทุกประโยค** ขอของ/เสนอของอะไรก็เข้าใจ และตอบเป็นคำพูดของมันเอง (ผลลัพธ์ยังเป็นไปตามกติกาของเกม)

  Edit `config/hollowhours-common.toml` (created after the first launch; on a server it is the server's `config` folder):
  แก้ไฟล์ `config/hollowhours-common.toml` (มีหลังจากเปิดเกมครั้งแรก):
  ```toml
  [ai]
  	enabled = true
  	provider = "gemini"        # gemini / openai / anthropic / openrouter / groq / ollama / custom
  	apiKey = "YOUR-KEY"
  	model = ""                 # empty = default
  ```
  Free Gemini key: https://aistudio.google.com/apikey — Helper Buddy uses the same settings in `config/helperbuddy-common.toml`.
  key Gemini ฟรี: https://aistudio.google.com/apikey — ผู้ช่วยใช้ค่าแบบเดียวกันในไฟล์ `config/helperbuddy-common.toml`

---

## 🧰 Helper Buddy
A helper that does (almost) everything you ask in plain Thai or English.
ผู้ช่วยที่ทำได้เกือบทุกอย่าง แค่พิมพ์สั่งเป็นประโยคธรรมดา (ไทยหรืออังกฤษ)

**Getting one / การเรียกผู้ช่วย**: you get a *Helper Contract* when you first join (or craft it: paper + iron ingot + bread + feather). Use it on the ground.
ได้ *สัญญาจ้างผู้ช่วย* ตอนเข้าเกมครั้งแรก (หรือคราฟต์: กระดาษ + แท่งเหล็ก + ขนมปัง + ขนนก) แล้วคลิกขวาที่พื้น

**Talking / การสั่ง**: type in chat starting with `!` (or the helper's name), or right-click the helper → *Talk*, or `/helper ...`
พิมพ์ในแชตโดยขึ้นต้นด้วย `!` (หรือชื่อผู้ช่วย) หรือคลิกขวาที่ผู้ช่วย → แท็บ *คุย* หรือใช้ `/helper ...`

```
!ตัดไม้ 20 ท่อน แล้วขุดหิน 64 ก้อน แล้วเอามาให้
!สร้างโรงแรม 3 ชั้น            !build a big modern house with a pool
!ขุดเพชรจนกระเป๋าเต็ม          !mine diamonds until your bag is full
!เก็บไม้ไผ่ 64                 !collect 64 sugar cane
!คราฟต์คบเพลิง 16 อัน           !smelt iron
!ทำฟาร์ม  !ผสมพันธุ์วัว  !ตัดขนแกะ  !ปักคบเพลิง  !ปรับพื้นที่
!สร้างเหมือง ลึก 40 ยาว 30     !build a nether portal
```

- **Fights back** when attacked (setting, on by default), defends you, handles creepers by hitting and backing off (or with a bow).
  **สู้กลับ** เมื่อโดนตี (ตั้งค่าได้ เปิดไว้เป็นค่าเริ่มต้น) ปกป้องคุณ เจอครีปเปอร์จะตีแล้วถอย (หรือยิงธนู)
- **Builds** houses, hotels, schools, hospitals, prisons, libraries, churches, barns, markets, castles, towers, tree houses, igloos, pyramids, bunkers, statues and nether portals — **a different random design every time**. Building is free by default; turn on *Build with real blocks* in the settings to use real blocks. A nether portal always needs 10 obsidian + flint and steel.
  **สร้าง** บ้าน โรงแรม โรงเรียน โรงพยาบาล คุก ห้องสมุด โบสถ์ โรงนา ตลาด ปราสาท หอคอย บ้านต้นไม้ อิกลู ปิรามิด บังเกอร์ รูปปั้น ประตูเนเธอร์ — **สุ่มแบบใหม่ทุกครั้ง** สร้างฟรีเป็นค่าเริ่มต้น (เปิด *สร้างด้วยบล็อกจริง* ได้ในตั้งค่า) ประตูเนเธอร์ต้องให้ออบซิเดียน 10 + หินเหล็กไฟ
- **Works** within 100 blocks: mines ores (digs its way to them), chops whole trees and replants, collects any block (bamboo/sugar cane keep their roots), hunts and harvests (no need to keep animals for breeding), farms, cooks, crafts and smelts with real recipes, fetches from chests.
  **ทำงาน** ในระยะ 100 บล็อก: ขุดแร่ (ขุดทางไปเอง) ตัดไม้ทั้งต้นและปลูกคืน เก็บบล็อกทุกชนิด ล่าสัตว์/เก็บผัก (ไม่ต้องเหลือสัตว์ไว้ผสมพันธุ์) ทำฟาร์ม ทำอาหาร คราฟต์และถลุงด้วยสูตรจริง หยิบของจากหีบ
- **Menu**: right-click the helper (sneak + right-click = its bag). The *Command Book* orders your helpers from anywhere.
  **เมนู**: คลิกขวาที่ผู้ช่วย (ย่อ + คลิกขวา = กระเป๋า) *หนังสือสั่งงาน* สั่งผู้ช่วยได้จากทุกที่
- **AI (optional)**: put a key in `config/helperbuddy-common.toml` (Gemini free key, OpenAI, Claude, or a local Ollama) and the helper understands any sentence, chats, and designs anything you ask it to build ("build a red car").
  **AI (ไม่บังคับ)**: ใส่ key ในไฟล์ `config/helperbuddy-common.toml` แล้วผู้ช่วยจะเข้าใจทุกประโยค คุยได้ และออกแบบสิ่งก่อสร้างอะไรก็ได้

---

## Building from source
```
cd hollow-hours   # or helper-buddy
./gradlew build   # the jar is in build/libs/
```
Every push is built and tested on GitHub (a server and a client are started and screenshots are taken).
Pushing a tag such as `v2.0.0` (or a branch such as `release/2.0.0`) publishes a GitHub Release with the jars.
