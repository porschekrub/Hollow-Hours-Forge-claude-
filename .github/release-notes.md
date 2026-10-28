**Minecraft Java 1.20.1 + Forge 47.x** (not NeoForge). GeckoLib is bundled — just put the jars in your `mods` folder.
**Minecraft Java 1.20.1 + Forge 47.x** (ไม่ใช่ NeoForge) GeckoLib อยู่ในไฟล์แล้ว แค่ใส่ไฟล์ .jar ในโฟลเดอร์ `mods`

- `hollowhours-forge-1.20.1-2.4.0.jar` — Hollow Hours (ghost horror / ผีสยองขวัญ)
- `helperbuddy-forge-1.20.1-2.4.0.jar` — Helper Buddy (helper NPC / ผู้ช่วย)

**2.4.0 — physics and the playground**
- **Trees fall**: chop one log and the whole tree tips over away from you and crashes down; the logs, leaves, saplings and apples land where it fell. Sneak to break just one log.
  ตัดต้นไม้ทีเดียว ต้นไม้ล้มลงทั้งต้นพร้อมแอนิเมชัน ของตกอยู่ตรงที่ต้นไม้ล้ม (ย่อเพื่อตัดทีละบล็อก)
- **Vein mining**: mine one ore and every touching ore of the same kind cracks out of the rock one after another and bursts into the air (up to 64; sneak to mine one).
  ขุดแร่ทีเดียว แร่ชนิดเดียวกันที่ติดกันแตกกระจายออกมาทั้งสาย (ย่อเพื่อขุดทีละก้อน)
- **Items lie on the ground**: dropped blocks are little 3D cubes resting on the floor, flat things lie flat; they tumble while flying.
  ของที่ดรอปวางนอนบนพื้นเป็น 3D
- **Explosions throw blocks** through the air; they land as rubble.
  ระเบิดแล้วบล็อกปลิวกระเด็นไปตกเป็นซาก
- **3D flashlight and battery**, and an animation of the battery going into the flashlight.
  ไฟฉายและแบตเตอรี่เป็น 3D มีท่าใส่แบต
- **Playground and living room** (craftable, all raise sanity): swing (sit and it swings higher and higher), slide, trampoline (bounces higher each time, no fall damage), basketball hoop + basketball, football goal + football (real bouncing/rolling balls: throw, kick, dribble, score!), arcade machine (the phone games with no battery), TV (turn it on; watching it calms you).
  เครื่องเล่น (คราฟได้ เพิ่มสติทั้งหมด): ชิงช้า สไลเดอร์ แทรมโพลีน แป้นบาส + ลูกบาส ประตูฟุตบอล + ลูกฟุตบอล (ลูกเด้ง กลิ้ง เตะ ชู้ต ทำคะแนนได้จริง) ตู้เกม (เล่นเกมไม่เปลืองแบต) ทีวี
- Fixed: the emergency alert's subtitles no longer run under the phone; blood drops are bigger and easier to see.
- Settings: `[physics]` in `hollowhours-common.toml` (tree felling, vein size, explosion debris), `item_physics` in `hollowhours-client.toml`.

**2.3.0 — the story begins on night 3**
- The first two nights are completely normal (no ghosts, no fog, your character is cheerful). On day 3 an **emergency alert** comes through your phone: alert tone, a spoken warning (Thai or English, by your game language) with subtitles. From night 3 the haunting starts and your character turns scared and jumpy.
  สองคืนแรกทุกอย่างปกติ ตัวละครร่าเริง วันที่ 3 โทรศัพท์จะมีแจ้งเตือนฉุกเฉิน พร้อมเสียงพูดและซับไตเติล คืนที่ 3 ผีเริ่มออกมา ตัวละครเริ่มกลัวและระแวง
- **Phone**: everyone starts with one (random battery). Messages, two games (Snake, Ghost Tap) that calm you down but drain the battery, a health app. The more you use it the faster the battery drains. **Charger**: 3 charges (string + 3 copper + redstone).
  โทรศัพท์ แบตสุ่ม เล่นเกมเพิ่มสติแต่แบตหมดเร็ว ยิ่งใช้นานแบตยิ่งเสื่อม ที่ชาร์จใช้ได้ 3 ครั้ง
- **Thirst**: water drops above the food bar. Drink water bottles, milk, soups... or sneak + right-click water with an empty hand.
  ระบบหิวน้ำ ดื่มขวดน้ำ นม ซุป หรือย่อ + คลิกขวาที่น้ำ (มือเปล่า)
- **Sanity**: the eye bar above your armour. Darkness, night, ghosts and jumpscares drain it; light, friends, pets, music, a campfire, sleep, comfort food and phone games restore it. Low sanity = whispers, hallucinations, a warped screen.
  ค่าสติ ลดเมื่ออยู่ในที่มืด กลางคืน เจอผี โดนจัมป์สแกร์ เพิ่มเมื่ออยู่ในที่สว่าง อยู่กับเพื่อน สัตว์เลี้ยง ฟังเพลง กองไฟ นอน กินของอร่อย เล่นเกมในโทรศัพท์ สติต่ำจะเริ่มได้ยินเสียงกระซิบและเห็นภาพหลอน
- **Blood**: every hit sprays blood in the creature's own colour (zombies dark, creepers green, endermen purple, skeletons bone dust...), it splashes on the ground and stays a while; badly hurt creatures and players drip.
  ระบบเลือด โดนตีแล้วเลือดกระเด็น สีตามชนิดสิ่งมีชีวิต หยดลงพื้นค้างไว้สักพัก เลือดเหลือน้อยจะมีเลือดหยดตามตัว
- Settings: `[story]` and `[survival]` in `hollowhours-common.toml`. Test: `/hollow story start|hold|calendar|alert`, `/hollow thirst <0-20>`, `/hollow sanity <0-100>`.

**2.2.2**
- Fixed: hiding under a bed / in a closet with sneak + right-click popped you straight back out. / แก้: มุดใต้เตียงหรือเข้าตู้แล้วเด้งออกทันที
- Helper Buddy + AI: the AI now reads every sentence you type first (orders, chat, questions) and remembers the conversation; the built-in brain is only the fallback. / ผู้ช่วย + AI: AI อ่านทุกประโยคก่อน และจำบทสนทนาได้
- If the AI can't be reached, a grey line in chat says so once. / ถ้าเชื่อมต่อ AI ไม่ได้ จะมีข้อความบอกในแชต

**2.2.1**
- AI: Gemini keys that start with `AQ.` (the only kind Google AI Studio gives out now) work — both mods now talk to Gemini's own API.
  AI: key Gemini แบบใหม่ที่ขึ้นต้นด้วย `AQ.` ใช้ได้แล้ว

**2.2.0**
- Blood moon: 10% of nights. Ghosts come 3× as often, hit 3× as hard, run faster, break in easier, and 50% of hunts bring two ghosts. Red moon, red night.
  คืนพระจันทร์สีเลือด 10% ผีมาถี่ขึ้น 3 เท่า โหดขึ้น 3 เท่า วิ่งเร็วขึ้น และมีโอกาส 50% ที่จะมาทีเดียว 2 ตัว
- Fog that rolls in now and then. / หมอกลงเป็นช่วง ๆ
- Built-in horror shader (turn off with `shader = false` in `config/hollowhours-client.toml`). / เชดเดอร์สยองในตัว
- Scared player animations (third person) and shaking hands / breathing sway (first person). / แอนิเมชันตัวละครกลัวผี มือสั่น จอโยก
- Settings: `[atmosphere]` in `hollowhours-common.toml`, `[look]` in `hollowhours-client.toml`. Test: `/hollow bloodmoon on`, `/hollow fog on`.

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
