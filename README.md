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
- Test commands (op): `/hollow bloodmoon on|off|random`, `/hollow fog on|off`, `/hollow rescue now|invade|reset|status`
- **The story / เนื้อเรื่อง**: nights 1–2 are normal and your character is cheerful. On day 3 your phone gets an emergency alert (voice + subtitles); from night 3 the ghosts come and your character is scared.
  คืนที่ 1–2 ปกติ ตัวละครร่าเริง วันที่ 3 โทรศัพท์แจ้งเตือนฉุกเฉิน (มีเสียงพูด + ซับ) คืนที่ 3 ผีเริ่มออก ตัวละครเริ่มกลัว
- **The rescue / การช่วยเหลือ**: on day 7 a message comes: a helicopter will pick you up in 35–100 days, on a set day at a set time. It waits only 5 minutes — whoever is aboard is flown to **the haven**, a rich, beautiful land (cherry hills, flower woods, a fruit jungle, a coral lagoon, more ore and animals) with no ghosts and no monsters. Miss it and you wait for the next one, with ghosts 2× as cruel (blood moons 5×). In the haven, after 5–10 days, the ghosts find you: run to the helicopter (it waits) and fly back where it began, for good — ghosts 3×, blood moons 6×. Settings in `[rescue]` of `hollowhours-common.toml`.
  วันที่ 7 จะมีข้อความว่าเฮลิคอปเตอร์จะมารับในอีก 35–100 วัน (บอกวันและเวลา) เฮลิคอปเตอร์รอแค่ 5 นาที ใครขึ้นทันจะได้ไป **ดินแดนสวรรค์** ที่อุดมสมบูรณ์และสวยงาม ไม่มีผีและมอนสเตอร์ ถ้าพลาด ต้องรอลำต่อไปและผีจะโหดขึ้น 2 เท่า (พระจันทร์สีเลือด 5 เท่า) อยู่สวรรค์ได้ 5–10 วันผีจะบุก ต้องวิ่งไปขึ้นเฮลิคอปเตอร์ (รอไม่จำกัดเวลา) แล้วจะกลับโลกเดิมตลอดไป ผีโหดขึ้น 3 เท่า พระจันทร์สีเลือด 6 เท่า
- **Phone apps / แอปในโทรศัพท์**: Rescue (countdown to the helicopter), Location (map + navigation to home, the helicopter, your grave, a friend's location), Chat (real friends — send your location — and Mom, Dad, your best friend; on night 15 they send their last message).
  กู้ภัย (นับถอยหลังเฮลิคอปเตอร์) ตำแหน่ง (แผนที่ + นำทาง) แชท (เพื่อนจริง ส่งตำแหน่งได้ / แม่ พ่อ เพื่อนสนิท คืนที่ 15 จะส่งข้อความสุดท้าย)
- **Phone / โทรศัพท์**: you start with one (random battery), plus a flashlight and a spare battery. Messages, Snake, Ghost Tap, health. Games raise sanity but drain the battery; the battery wears out with use. Craft a power bank (3 charges): hold right-click to plug the cable in and charge.
  เริ่มเกมมีโทรศัพท์ (แบตสุ่ม) ไฟฉาย และถ่าน 1 ก้อน เล่นเกมเพิ่มสติแต่แบตหมดเร็ว คราฟพาวเวอร์แบงก์ได้ ใช้ได้ 3 ครั้ง (กดคลิกขวาค้างเพื่อเสียบสายชาร์จ)
- **Thirst & sanity / หิวน้ำและค่าสติ**: drink (water bottles, milk, soups, or sneak + right-click water with an empty hand). Sanity drops in the dark, at night, near ghosts; low sanity brings whispers and hallucinations. Light, friends, pets, music, a TV, sleep, games and the playground bring it back.
  ดื่มน้ำ (ขวดน้ำ นม ซุป หรือย่อ + คลิกขวาที่น้ำ) ค่าสติลดในที่มืด กลางคืน ใกล้ผี สติต่ำจะเห็นภาพหลอน เพิ่มสติด้วยแสงไฟ เพื่อน สัตว์เลี้ยง เพลง ทีวี การนอน เกม และเครื่องเล่น
- **Blood / เลือด**: hits spray blood in each creature's colour; badly hurt creatures drip. / โดนตีเลือดกระเด็นตามสีของสิ่งมีชีวิต เลือดน้อยจะหยด
- **Physics / ฟิสิกส์**: trees fall when you chop them, ore veins burst out when you mine one ore, dropped items lie on the ground in 3D, explosions throw blocks. Sneak to break a single block.
  ตัดต้นไม้ทีเดียวล้มทั้งต้น ขุดแร่ทีเดียวแตกทั้งสาย ของดรอปนอนบนพื้นแบบ 3D ระเบิดแล้วบล็อกปลิว (ย่อเพื่อทุบทีละบล็อก)
- **Playground / เครื่องเล่น**: swing, slide, trampoline, basketball hoop + ball, football goal + ball, arcade machine, TV — all craftable, all raise sanity.
  ชิงช้า สไลเดอร์ แทรมโพลีน แป้นบาส ประตูฟุตบอล ตู้เกม ทีวี คราฟได้ทั้งหมด เพิ่มสติ
- **Graves / หลุมศพ**: die and your items + all your XP wait in a grave (15 minutes, only you can open it, 3 hits break it); a compass points to it; a Grave Restorer (3 uses) brings back a lost grave.
  ตายแล้วของและ XP ทั้งหมดอยู่ในหลุมศพ (15 นาที เปิดได้แค่เจ้าของ ตี 3 ทีพัง) มีเข็มทิศนำทาง ตะเกียงกู้ของ (3 ครั้ง) เอาของจากหลุมที่พังคืนได้
- Test commands (op): `/hollow story start|hold|alert`, `/hollow thirst <0-20>`, `/hollow sanity <0-100>`
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

**Getting one / การเรียกผู้ช่วย**: you get a *Helper Contract* when you first join (or craft it, shaped: gold ingot · emerald · gold ingot / diamond · book and quill · diamond / gold ingot · iron block · gold ingot). Use it on the ground.
ได้ *สัญญาจ้างผู้ช่วย* ตอนเข้าเกมครั้งแรก (หรือคราฟต์แบบเรียงช่อง: ทอง · มรกต · ทอง / เพชร · หนังสือกับปากกาขนนก · เพชร / ทอง · บล็อกเหล็ก · ทอง) แล้วคลิกขวาที่พื้น

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
!ตกปลา 10 ตัว                  !trade with villagers for emeralds
!ร่ายมนตร์ดาบ                   !brew a potion of swiftness
!ใส่เหล็กดิบในเตาเผา            !put raw iron in the furnace
!หมู่บ้านอยู่ไหน  !พาไปหมู่บ้าน  !ไปเนเธอร์  !ไปเอนด์  !ฆ่ามังกร
!สร้างประตูอัตโนมัติ            !build an auto smelter
```

**🎤 Voice / สั่งด้วยเสียง**: hold **B**, speak, let go (needs the Gemini key). / กดปุ่ม **B** ค้าง พูด แล้วปล่อย (ต้องใส่คีย์ Gemini)
With the Gemini key it answers in real words in a cartoon voice (`voice = "cartoon"`; `"babble"`, `"ai"` or `"off"` in `[voice]`). / ใส่คีย์ Gemini แล้วผู้ช่วยตอบเป็นคำพูดด้วยเสียงการ์ตูน

- **Fights back** when attacked (setting, on by default), defends you, handles creepers by hitting and backing off (or with a bow).
  **สู้กลับ** เมื่อโดนตี (ตั้งค่าได้ เปิดไว้เป็นค่าเริ่มต้น) ปกป้องคุณ เจอครีปเปอร์จะตีแล้วถอย (หรือยิงธนู)
- **Builds** houses, hotels, schools, hospitals, prisons, libraries, churches, barns, markets, castles, towers, tree houses, igloos, pyramids, bunkers, statues and nether portals — **a different random design every time**. Building is free by default; turn on *Build with real blocks* in the settings to use real blocks. A nether portal always needs 10 obsidian + flint and steel.
  **สร้าง** บ้าน โรงแรม โรงเรียน โรงพยาบาล คุก ห้องสมุด โบสถ์ โรงนา ตลาด ปราสาท หอคอย บ้านต้นไม้ อิกลู ปิรามิด บังเกอร์ รูปปั้น ประตูเนเธอร์ — **สุ่มแบบใหม่ทุกครั้ง** สร้างฟรีเป็นค่าเริ่มต้น (เปิด *สร้างด้วยบล็อกจริง* ได้ในตั้งค่า) ประตูเนเธอร์ต้องให้ออบซิเดียน 10 + หินเหล็กไฟ
- **Works** anywhere (it looks around first, then moves on further, no distance limit): mines ores (digs its way to them), chops whole trees and replants, collects any block (bamboo/sugar cane keep their roots), hunts and harvests (no need to keep animals for breeding), farms, cooks, crafts and smelts with real recipes, fetches from chests.
  **ทำงาน** ได้ไม่จำกัดระยะ (หาแถวนี้ก่อน หมดแล้วไปไกลขึ้นเรื่อยๆ): ขุดแร่ (ขุดทางไปเอง) ตัดไม้ทั้งต้นและปลูกคืน เก็บบล็อกทุกชนิด ล่าสัตว์/เก็บผัก (ไม่ต้องเหลือสัตว์ไว้ผสมพันธุ์) ทำฟาร์ม ทำอาหาร คราฟต์และถลุงด้วยสูตรจริง หยิบของจากหีบ
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
