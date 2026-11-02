**Minecraft Java 1.20.1 + Forge 47.x** (not NeoForge). GeckoLib is bundled — just put the jars in your `mods` folder.
**Minecraft Java 1.20.1 + Forge 47.x** (ไม่ใช่ NeoForge) GeckoLib อยู่ในไฟล์แล้ว แค่ใส่ไฟล์ .jar ในโฟลเดอร์ `mods`

- `hollowhours-forge-1.20.1-2.10.0.jar` — Hollow Hours (ghost horror / ผีสยองขวัญ)
- `helperbuddy-forge-1.20.1-2.10.0.jar` — Helper Buddy (helper NPC / ผู้ช่วย)

**2.10.0 — the phone's new apps**
- **Rescue** app: the countdown to the helicopter (days, hours, minutes; the day and time), the countdown while it waits, your part of the story.
  แอป **กู้ภัย**: นับถอยหลังว่าเฮลิคอปเตอร์จะมาในอีกกี่วัน กี่ชั่วโมง กี่นาที (บอกวันและเวลา) นับถอยหลังตอนมันรอ และสถานะเนื้อเรื่อง
- **Location** app: a map around you, your coordinates and which way you face. Tap home, the helicopter, your grave or a friend's location to **navigate** — an arrow and the distance stay on screen until you get there.
  แอป **ตำแหน่ง**: แผนที่รอบตัว พิกัด ทิศที่หัน แตะ บ้าน / เฮลิคอปเตอร์ / หลุมศพ / ตำแหน่งเพื่อน เพื่อ**นำทาง** มีลูกศรและระยะทางบนจอจนกว่าจะถึง
- **Chat** app: chat with your real friends (they get a pop-up) and **send your location** (they tap it to navigate to you). Chat with **Mom, Dad (new) and your best friend** — they answer in Thai or English (with the AI if it's on).
  แอป **แชท**: แชทกับเพื่อนจริงๆ (เด้งแจ้งเตือนที่โทรศัพท์เพื่อน) และ**ส่งตำแหน่ง**ให้เพื่อน (เพื่อนแตะเพื่อนำทางมาหา) แชทกับ **แม่ พ่อ (ใหม่) และเพื่อนสนิท** ได้ ตอบเป็นภาษาไทย/อังกฤษ (ใช้ AI ถ้าเปิดไว้)
- The story comes through the chat: after the emergency alert they're worried, around day 8 it gets bad where they are, and **on night 15** each sends a last message — "we didn't make it, don't let it see you". After that nothing gets through. (`contactsLostNight` in `[story]`)
  เนื้อเรื่องผ่านแชท: หลังแจ้งเตือนฉุกเฉินพวกเขาจะเป็นห่วง วันที่ 8 เริ่มแย่ **คืนที่ 15** แต่ละคนจะส่งข้อความสุดท้าย "พวกเราไม่รอดแล้ว อย่าให้มันเห็นเด็ดขาด" หลังจากนั้นส่งข้อความไปไม่ถึงอีกเลย

**2.9.0 — the rescue**
- **Day 7: help is coming.** A message pops up on your phone: a rescue helicopter will pick you up in 35–100 days — the day and the time. When it lands near you it waits **5 minutes**; right-click it to climb aboard (up to 6 people). A countdown and an arrow show the way.
  วันที่ 7 โทรศัพท์จะแจ้งว่าเฮลิคอปเตอร์กู้ภัยจะมารับในอีก 35–100 วัน พร้อมบอกวันและเวลา พอมันลงจอดใกล้ๆ จะรอแค่ **5 นาที** คลิกขวาเพื่อขึ้น (ได้ 6 คน) มีเวลานับถอยหลังและลูกศรบอกทาง
- **The haven**: whoever is aboard is flown to a new world — cherry hills, golden meadows, flower woods, a fruit jungle, white beaches and a coral lagoon, with more ore, fruit and animals, **no ghosts and no monsters**. Portals don't work there.
  ใครขึ้นทันจะได้ไป **ดินแดนสวรรค์**: เนินซากุระ ทุ่งหญ้า ป่าดอกไม้ ป่าผลไม้ หาดทราย ทะเลปะการัง แร่ ผลไม้ สัตว์เยอะ **ไม่มีผีและมอนสเตอร์**
- **Missed it?** Another helicopter is planned (35–100 days again), but the ghosts are **2× as cruel** (hit harder, come more often) and **blood moons 5×**.
  ถ้าพลาด จะมีลำใหม่ (สุ่ม 35–100 วันอีกครั้ง) แต่ผีโหดขึ้น **2 เท่า** พระจันทร์สีเลือด **5 เท่า**
- **They find the haven** after 5–10 days: ghosts hunt you there and a helicopter lands somewhere out there — it waits as long as it takes. It flies you back where it all began, **for good**: ghosts **3×**, blood moons **6×**.
  อยู่สวรรค์ 5–10 วันผีจะบุก ต้องวิ่งไปขึ้นเฮลิคอปเตอร์ (รอไม่จำกัดเวลา) มันจะพากลับโลกเดิม **ตลอดไป** ผีโหดขึ้น **3 เท่า** พระจันทร์สีเลือด **6 เท่า**
- Settings in `[rescue]` of `hollowhours-common.toml`; test commands (op): `/hollow rescue now|invade|reset|status`.
- The haven is an extra world, which makes Minecraft show an "Experimental Settings" warning when you open the world — the mod presses Proceed for you (`skipExperimentalWarning` in `hollowhours-client.toml`).
  ดินแดนสวรรค์เป็นโลกเพิ่ม Minecraft จะเตือน "Experimental Settings" ตอนเปิดโลก มอดกด Proceed ให้อัตโนมัติ
- First person: the canteen stays in view while you twist the cap open; the power bank set sits a little higher.

**2.8.1**
- Power bank: the message after charging shows the right numbers (and where you unplugged); the bank, phone and cable sit smaller in first person.
  แก้ข้อความหลังชาร์จ (แสดงจำนวนครั้งที่เหลือถูกต้อง) และพาวเวอร์แบงก์/โทรศัพท์ในมุมมองบุคคลที่ 1 เล็กลง
- Opening the cap: the canteen / bottle is held up where you can see it.
  ตอนเปิดฝา ยกกระติก/ขวดขึ้นมาให้เห็นชัด

**2.8.0 — everything in 3D, drinking animations, power bank, faster ghosts**
- **Every item the mods add is 3D**: phone, ghost guide and command book, barricade boards, repair kits, canteen, closet, restorer, grave compass, helper contract, spawn eggs. **Buckets** (water, lava, milk, fish...) and **bottles** (water bottle, potions, honey) are 3D too.
  ของทุกชิ้นในมอดเป็น 3D: โทรศัพท์ หนังสือ ไม้กั้น ชุดซ่อม กระติกน้ำ ตู้ เข็มทิศหลุมศพ ฯลฯ ถังน้ำ/ถังลาวา/ถังนม และขวดน้ำ/ยา ก็เป็น 3D
- **Drinking animations**: unscrew the cap of the canteen or water bottle, then drink; **scoop water with both hands** from the sea or a river (first and third person).
  แอนิเมชันดื่มน้ำ: เปิดฝากระติก/ขวดก่อนแล้วค่อยดื่ม ใช้สองมือวักน้ำจากทะเล/แม่น้ำ (ทั้งบุคคลที่ 1 และ 3)
- **The charger is now a power bank** with a cable: hold right-click to plug it into your phone and watch the battery climb to 100%. 3 charges.
  ที่ชาร์จเป็นพาวเวอร์แบงก์มีสาย: กดคลิกขวาค้างเพื่อเสียบสายเข้าโทรศัพท์ เห็นแบตค่อยๆ เพิ่มจนเต็ม ใช้ได้ 3 ครั้ง
- **New players start with a flashlight and a spare battery** (plus the phone).
  เริ่มเกมได้ไฟฉาย + ถ่าน 1 ก้อน (และโทรศัพท์)
- **Ghosts run about 20% faster than your normal sprint** — only a dash (double-tap sprint) gets you away.
  ผีวิ่งเร็วกว่าผู้เล่นวิ่งปกติ ~20% ต้อง dash (กดวิ่ง 2 ครั้ง) ถึงจะหนีทัน

**2.7.1 — see yourself in first person, 3D food**
- **Your arm holds what's in your hand** in first person: you see the arm swing the pickaxe, raise the bread to your mouth, tip the canteen.
  มุมมองบุคคลที่ 1 เห็นแขนถือของ: เห็นแขนเหวี่ยงตอนขุด ยกอาหารเข้าปาก ยกกระติกดื่ม
- **Look down and see your body and legs**, with the same animations as third person (the happy skip, the scared hunch, sneaking, jumping).
  ก้มมองจะเห็นตัวและขาตัวเอง ขยับเหมือนมุมมองบุคคลที่ 3
- **3D food**: bread, apples, steak, fish, potatoes, carrots, berries... are puffy 3D in your hand and on the ground (still the same icons in the inventory).
  อาหารเป็น 3D ทั้งในมือและบนพื้น
- Settings (`hollowhours-client.toml`): `firstPersonArms`, `firstPersonBody`.

**2.6.0 — body language**
- **First person** (like the Actions & Stuff pack): your hands move with what you do — standing, walking, running, sneaking, jumping and landing.
  มุมมองบุคคลที่ 1: มือขยับตามท่าทาง ยืน เดิน วิ่ง ย่อ กระโดด ลงพื้น
- **Nights 1–2: carefree** — a bounce in every step, arms pumping, swaying to a tune, waving hello, arms thrown up when you jump; tiptoeing like a cartoon burglar when sneaking (third person).
  คืน 1–2 ร่าเริงมาก เดินเด้ง แกว่งแขน โยกตามเพลง โบกมือ กระโดดชูมือ ย่อแบบย่องเหมือนการ์ตูน
- **From night 3: scared** — hands pulled in and trembling, flinching at nothing, flailing when you run, shielding your face when you jump, and **hugging yourself** when you stand still (first and third person).
  ตั้งแต่คืนที่ 3 กลัว มือสั่น สะดุ้ง วิ่งแขนแกว่งมั่ว กระโดดเอามือบังหน้า ยืนนิ่งๆ เอามือกอดอก
- **Eating and drinking** animations (first and third person): hand to the mouth and chewing, tipping the bottle up and the head back; scooping river water with your hand.
  แอนิเมชันกินและดื่ม: ยกมือเข้าปาก เคี้ยว ยกขวดดื่ม เงยหน้า วักน้ำจากแม่น้ำ

**2.5.3**
- Thirst now works like hunger: it goes down with what you do (running, jumping, fighting, mining, healing), **10% faster than the hunger bar**, and drinks give hidden hydration (like food saturation). Standing still doesn't make you thirsty.
  หิวน้ำทำงานเหมือนหลอดอาหาร ลดตามสิ่งที่ทำ (วิ่ง กระโดด สู้ ขุด ฟื้นเลือด) เร็วกว่าหลอดอาหาร 10%
- **Canteen** (leather ×3, glass bottle, iron nugget): 3 drinks, each 15% more water than a water bottle; right-click water to refill.
  กระติกน้ำ: ดื่มได้ 3 ครั้ง ได้น้ำมากกว่าขวดน้ำ 15% คลิกขวาที่น้ำเพื่อเติม
- **Out of breath**: when dash stamina runs out you can't run at all (not even a normal sprint) until it's back to 15%.
  stamina หมด = วิ่งไม่ได้เลย (วิ่งปกติก็ไม่ได้) จนกว่า stamina จะกลับมา 15%
- Flashlight batteries are back to 5 minutes. / ถ่านไฟฉายกลับเป็น 5 นาทีเหมือนเดิม

**2.5.2**
- The phone battery lasts longer: about 30 minutes of screen time or 12 minutes of games per charge, and it wears out half as fast. / แบตโทรศัพท์อยู่ได้นานขึ้น: เปิดจอ ~30 นาที เล่นเกม ~12 นาที แบตเสื่อมช้าลงครึ่งหนึ่ง
- New settings: `thirstRate` and `phoneDrain` in `[survival]` of `hollowhours-common.toml` (1 = default, 0.5 = half as fast).

**2.5.1**
- Helper Buddy: the command book stays with you when you die (it's back in your inventory when you respawn; `keepCommandBookOnDeath` in `helperbuddy-common.toml`).
  ผู้ช่วย: หนังสือสั่งงานไม่หายตอนตาย เกิดใหม่แล้วอยู่ในกระเป๋าเหมือนเดิม

**2.5.0 — graves**
- When you die, everything you carried and **all** your experience go into a **grave** where you fell, and you get a **Grave Compass** pointing to it. Right-click the grave to get everything back. Only you can open it.
  ตายแล้วของทั้งหมดและ XP ทั้งหมดจะอยู่ในหลุมศพตรงที่ตาย ได้เข็มทิศชี้ไปหาหลุมศพ คลิกขวาเพื่อเอาของคืน คนอื่นเปิดไม่ได้
- Hit your grave 3 times and it crumbles with everything in it. After 15 minutes it crumbles by itself.
  ตีหลุมศพ 3 ที หลุมศพพังและของหายหมด ถ้าผ่านไป 15 นาทีหลุมศพจะพังเอง
- **Grave Restorer** (3 uses; soul lantern + gold + amethyst + soul sand) brings back what your last broken or crumbled grave took.
  ตะเกียงกู้ของ (ใช้ได้ 3 ครั้ง) เอาของจากหลุมศพที่พังหรือหายไปกลับคืนมา
- Hold right-click on the compass for 3 seconds, twice, to throw it away. If your inventory is full, the compass and restored items wait and come to you when there is room.
  กดคลิกขวาค้างที่เข็มทิศ 3 วิ 2 รอบเพื่อทิ้ง ถ้ากระเป๋าเต็ม ของจะรอแล้วมาเองเมื่อมีที่ว่าง
- Settings: `[graves]` in `hollowhours-common.toml`. No graves with the keepInventory game rule.

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
