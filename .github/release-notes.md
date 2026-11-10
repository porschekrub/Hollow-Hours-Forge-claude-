**Minecraft Java 1.20.1 and 1.21.1 — Forge, NeoForge and Fabric.** Take the jar for your loader and Minecraft version. GeckoLib is inside every jar (and Forge Config API Port in the Fabric ones): just put the jars in your `mods` folder.
**Minecraft Java 1.20.1 และ 1.21.1 — Forge, NeoForge และ Fabric** เลือกไฟล์ให้ตรงกับตัวโหลดมอดและเวอร์ชันเกม GeckoLib อยู่ในไฟล์แล้วทุกไฟล์ (Fabric มี Forge Config API Port ในไฟล์ด้วย) แค่ใส่ไฟล์ .jar ในโฟลเดอร์ `mods`

| File / ไฟล์ | For / ใช้กับ |
|---|---|
| `hollowhours-forge-1.20.1-2.16.0.jar` · `helperbuddy-forge-1.20.1-2.16.2.jar` | Forge 1.20.1 (47.x) **and NeoForge 1.20.1** / และ NeoForge 1.20.1 |
| `hollowhours-neoforge-1.21.1-2.16.0.jar` · `helperbuddy-neoforge-1.21.1-2.16.2.jar` | NeoForge 1.21.1 (21.1.x) |
| `hollowhours-forge-1.21.1-2.16.0.jar` · `helperbuddy-forge-1.21.1-2.16.2.jar` | Forge 1.21.1 (52.x) |
| `hollowhours-fabric-1.20.1-2.16.0.jar` · `helperbuddy-fabric-1.20.1-2.16.2.jar` | Fabric 1.20.1 + **Fabric API** |
| `hollowhours-fabric-1.21.1-2.16.0.jar` · `helperbuddy-fabric-1.21.1-2.16.2.jar` | Fabric 1.21.1 + **Fabric API** |

Hollow Hours = ghost horror / ผีสยองขวัญ · Helper Buddy = helper NPC / ผู้ช่วย. Everyone on a server needs the same jars. / ทุกคนในเซิร์ฟเวอร์ต้องใช้ไฟล์ชุดเดียวกัน

**Helper Buddy 2.16.2 — no limit on how far the helper sees** (Hollow Hours stays 2.16.0)
- The helper now sees **everything that is loaded in the world** (the whole view distance, top to bottom), not just 100 blocks; past that it walks on further by itself. `searchRadius = 0` (the new default) means no limit; a number still limits it. If you already have a config file, set `searchRadius = 0` in `config/helperbuddy-common.toml`.
  ผู้ช่วย**มองเห็นทุกอย่างที่โหลดอยู่ในโลก** (ทั้งระยะการมองเห็นของเซิร์ฟเวอร์ ตั้งแต่บนสุดถึงล่างสุด) ไม่ใช่แค่ 100 บล็อกแล้ว ไกลกว่านั้นมันจะเดินไปหาเอง `searchRadius = 0` (ค่าเริ่มต้นใหม่) = ไม่จำกัด ถ้ามีไฟล์ config อยู่แล้ว ให้แก้เป็น `searchRadius = 0` ใน `config/helperbuddy-common.toml`

**Helper Buddy 2.16.1 — a cartoon voice that speaks real words**
- With the Gemini key, the helper now says everything **in real words with a cartoon voice**: its answers, what it says while working and its chatter with other helpers (faster and higher, a little different for each helper; if the free Gemini quota runs out it babbles for a while). Set `voice` in `[voice]` of `config/helperbuddy-common.toml`: `"cartoon"` (default with a key), `"ai"` (the plain AI voice), `"babble"` (the old cartoon sounds) or `"off"`.
  เมื่อใส่คีย์ Gemini ผู้ช่วยจะพูด**เป็นคำพูดจริงด้วยเสียงการ์ตูน**ทุกประโยค ทั้งตอนตอบ ตอนทำงาน และตอนคุยกันเอง (เร็วขึ้น แหลมขึ้น แต่ละตัวเสียงต่างกันนิดหน่อย ถ้าโควตา Gemini ฟรีหมดจะกลับเป็นเสียงอ้อแอ้ชั่วคราว) ตั้งค่า `voice` ใน `[voice]` ของ `config/helperbuddy-common.toml`: `"cartoon"` (ค่าเริ่มต้นเมื่อมีคีย์) `"ai"` (เสียง AI ปกติ) `"babble"` (เสียงการ์ตูนอ้อแอ้แบบเดิม) หรือ `"off"`
- The helper's menus and books are **always in English** now, whatever the game language.
  เมนูและหนังสือของผู้ช่วย**เป็นภาษาอังกฤษเสมอ** ไม่เปลี่ยนตามภาษาเกม
- **No work distance limit**: the helper looks around first, and when nothing is left it moves on further and further (sooner than before), as far as it takes. `searchRadius` (how far it looks at once) can now go up to 512.
  **ไม่จำกัดระยะทำงาน**: ผู้ช่วยหาแถวนี้ก่อน ถ้าหมดจะไปหาไกลขึ้นเรื่อยๆ (เร็วกว่าเดิม) ไกลแค่ไหนก็ได้ ตั้ง `searchRadius` (ระยะที่มองเห็นทีเดียว) ได้สูงสุด 512

**2.16.0 — English first**
- Everything is in **English unless your game is set to Thai** (Options → Language → ภาษาไทย).
  ทุกอย่างเป็น**ภาษาอังกฤษเป็นหลัก** จะเป็นภาษาไทยเมื่อตั้งภาษาเกมเป็นภาษาไทย
- **The AI answers in the language you ask in, any language**: Thai, English, Japanese, Chinese, Spanish... (English when it can't tell). This goes for the helper, the ghosts and the people on the phone. Voice orders work in any language too.
  **AI ตอบตามภาษาที่ถาม ได้ทุกภาษา**: ไทย อังกฤษ ญี่ปุ่น จีน สเปน... (ดูไม่ออกตอบอังกฤษ) ทั้งผู้ช่วย ผี และคนในโทรศัพท์ สั่งด้วยเสียงก็พูดได้ทุกภาษา
- Tell the helper **"speak Thai"** / **"พูดภาษาไทย"** and it talks Thai from then on (all its messages too); **"speak Japanese"** / **"พูดภาษาญี่ปุ่น"** (or any language) makes the AI always answer in it (the built-in messages exist in English and Thai only); **"speak my game's language"** / **"พูดตามภาษาเกม"** goes back. It remembers this in every world.
  สั่งผู้ช่วยว่า **"พูดภาษาไทย"** แล้วมันจะพูดไทยตลอด (ข้อความทั้งหมดด้วย) **"พูดภาษาญี่ปุ่น"** (หรือภาษาอื่น) ให้ AI ตอบภาษานั้นตลอด (ข้อความในตัวมอดมีแค่ไทยกับอังกฤษ) **"พูดตามภาษาเกม"** เพื่อกลับไปตามภาษาเกม มันจำไว้ทุกโลก
- Without the AI, orders are understood in Thai and English. / ถ้าไม่ได้เปิด AI จะเข้าใจคำสั่งแค่ไทยกับอังกฤษ
- Hollow Hours: the yellow lines on the main menu are English only.
  Hollow Hours: ข้อความสีเหลืองหน้าเมนูหลักเป็นภาษาอังกฤษอย่างเดียว

**2.15.0 — talk to your helper**
- 🎤 **Voice orders**: hold **B** (change it in Controls → Helper Buddy), say what you want ("ไปตัดไม้ 20 ท่อน", "take me to the village", "ตกปลา 10 ตัว"...) and let go. The helper hears it, shows what it understood in chat, and does it, answering in its own voice. Works in Thai and English, for everyone on the server (each player talks to their own helpers).
  🎤 **สั่งด้วยเสียง**: กดปุ่ม **B** ค้างไว้ (เปลี่ยนได้ใน การตั้งค่าปุ่ม → Helper Buddy) พูดสิ่งที่อยากให้ทำ แล้วปล่อยปุ่ม ผู้ช่วยจะฟัง ขึ้นข้อความในแชทว่าได้ยินอะไร แล้วทำตาม พร้อมตอบด้วยเสียงของมัน ใช้ได้ทั้งไทยและอังกฤษ ทุกคนในเซิร์ฟเวอร์ใช้ได้ (สั่งผู้ช่วยของตัวเอง)
- It needs the Gemini AI key in `config/helperbuddy-common.toml` (`[ai]`) on the computer that runs the world; the recording is sent to Google to be written down. Turn it off with `listen = false` in `[voice]`.
  ต้องใส่คีย์ Gemini ใน `config/helperbuddy-common.toml` (`[ai]`) ในเครื่องที่เปิดโลก เสียงที่อัดจะถูกส่งไปให้ Google แปลงเป็นข้อความ ปิดได้ที่ `listen = false` ใน `[voice]`
- On a Mac, allow the game (Java / the launcher) to use the microphone the first time.
  ถ้าใช้ Mac ครั้งแรกต้องกดอนุญาตให้เกมใช้ไมโครโฟน

**2.14.0 — a helper that can do (almost) everything**
- 🎣 **Fishing**: "go fishing" / "catch 10 fish" / "ตกปลา 10 ตัว". It finds water nearby and fishes like a player (fish, junk, the odd treasure, XP), with its own rod (or makes one).
  🎣 **ตกปลา**: "ตกปลา 10 ตัว" หาน้ำใกล้ๆ เอง ได้ของแบบตกปลาจริง (ปลา ของเก่า บางทีได้สมบัติ และ XP) ใช้เบ็ดของมันเอง (ไม่มีก็ทำเอง)
- 💰 **Trading with villagers**: "trade with villagers for emeralds", "sell wheat to the villagers", "buy a book from villagers" / "แลกมรกตกับชาวบ้าน", "ขายข้าวสาลีให้ชาวบ้าน", "ซื้อหนังสือจากชาวบ้าน". It pays with what's in its bag and says what it's missing.
  💰 **แลกของกับชาวบ้าน**: จ่ายด้วยของในกระเป๋ามัน ถ้าไม่พอจะบอกว่าต้องใช้อะไรเท่าไหร่
- ✨ **Enchanting**: "enchant my sword", "enchant this" / "ร่ายมนตร์ดาบ", "ร่ายมนตร์ของในมือ". It goes to an enchanting table (or places / crafts one), uses its lapis and your levels; more bookshelves, better enchantments.
  ✨ **ร่ายมนตร์**: ไปที่โต๊ะร่ายมนตร์เอง (ไม่มีก็วาง/คราฟให้) ใช้ลาพิสของมัน กับเลเวลของคุณ ชั้นหนังสือเยอะ ได้เวทดีขึ้น
- 🧪 **Brewing**: "brew a potion of swiftness", "a long fire resistance potion", "a splash healing potion" / "ต้มยาวิ่งเร็ว", "ยากันไฟแบบนาน", "ยาเพิ่มเลือดแบบปา". It works out the recipe, fills the bottles at the water and brews on a real brewing stand; it tells you what's missing.
  🧪 **ต้มยา**: คิดสูตรเอง ไปเติมน้ำใส่ขวด แล้วต้มที่แท่นต้มยาจริง ขาดอะไรจะบอก
- ⚙️ **Machines**: "put raw iron in the furnace", "put coal in this machine", "collect from the furnace" / "ใส่เหล็กดิบในเตาเผา", "ใส่ถ่านในเครื่องนี้". Works with other mods' machines too (anything hoppers can use): it loads it, adds fuel, waits and collects what comes out.
  ⚙️ **ใช้เครื่องจักร**: ใช้กับเครื่องจักรของมอดอื่นได้ด้วย ใส่ของ เติมเชื้อเพลิง รอแล้วเก็บของที่ออกมาให้
- 🌋 **The Nether and the End**: "take me to the nether" / "ไปเนเธอร์" leads you to the nearest nether portal (or tells you to have one built), "go back to the overworld", "take me to the end" / "ไปเอนด์" leads you to the stronghold.
  🌋 **เนเธอร์และเอนด์**: "ไปเนเธอร์" พาไปประตูเนเธอร์ที่ใกล้สุด "กลับโลกปกติ" "ไปเอนด์" พาไปสตรองโฮลด์
- 🐉 **Bosses**: "kill the dragon", "fight the wither" / "ฆ่ามังกร", "สู้วิเธอร์". It shoots the end crystals first (with a bow and arrows), then the boss, from far away. In the End it does this by itself.
  🐉 **สู้บอส**: ยิงคริสตัลบนเสาก่อน (ต้องมีธนูกับลูกธนู) แล้วยิง/ฟันบอสจากระยะไกล อยู่ในเอนด์จะทำเองอัตโนมัติ
- 🔴 **Redstone builds**: "build an automatic door" (iron door + pressure plates), "build an auto smelter" (furnace + hoppers + chests: things to smelt on top, fuel at the side, results in the floor), "build a light switch" (redstone lamps in the floor + a lever) / "สร้างประตูอัตโนมัติ", "สร้างเตาเผาอัตโนมัติ", "สร้างไฟพื้นเปิดปิด".
  🔴 **งานเรดสโตน**: ประตูอัตโนมัติ เตาเผาอัตโนมัติ (ใส่ของบนสุด เชื้อเพลิงด้านข้าง ของที่เผาแล้วลงหีบที่พื้น) ไฟพื้นเปิดปิดด้วยคันโยก
- The AI (when a key is set) knows all the new orders too. / ถ้าเปิด AI ไว้ AI ก็รู้จักคำสั่งใหม่ทั้งหมด

**2.13.0 — every loader, and a helper with a big mouth**
- Both mods now run on **Forge 1.20.1, NeoForge 1.20.1, NeoForge 1.21.1, Forge 1.21.1, Fabric 1.20.1 and Fabric 1.21.1** (Fabric needs Fabric API). Same features everywhere.
  มอดทั้งสองตัวเล่นได้บน **Forge 1.20.1, NeoForge 1.20.1, NeoForge 1.21.1, Forge 1.21.1, Fabric 1.20.1 และ Fabric 1.21.1** แล้ว (Fabric ต้องลง Fabric API) ฟีเจอร์เหมือนกันทุกเวอร์ชัน
- Helper Buddy has a **personality**: *sassy* by default (teases you, calls you names), or *rude* (swears for real), or *polite*. Tell a helper "be polite" / "be rude" / "be sassy", or set `style` in `[personality]`. Call it stupid and it answers back.
  Helper Buddy มี**นิสัย**แล้ว: เริ่มต้นเป็นแบบ*กวนตีน* (แซว แกล้ง ด่าเล่น) หรือ*หยาบ* (กู/มึง ด่าแรง) หรือ*สุภาพ* สั่งได้ว่า "พูดสุภาพหน่อย" / "ด่าได้เลย" / "กวนๆหน่อย" หรือตั้ง `style` ใน `[personality]` ด่ามันว่าโง่ มันด่ากลับ
- **"Where's the village?"** The helper finds villages, temples, strongholds, ancient cities, nether fortresses, shipwrecks... biomes (desert, jungle, cherry grove, ocean...), other mods' structures and biomes by name, where you died, the world spawn, your bed and your friends, and tells you the direction (north, south-east...), distance and coordinates. **"Take me to the village"** / "ไปหมู่บ้านกัน" and it walks ahead of you all the way there, waiting when you fall behind, however far it is.
  **"หมู่บ้านอยู่ไหน"** ผู้ช่วยหาได้ทั้งหมู่บ้าน วิหาร สตรองโฮลด์ เมืองโบราณ ป้อมเนเธอร์ ซากเรือ... ไบโอม (ทะเลทราย ป่าดิบ ป่าซากุระ ทะเล...) สิ่งก่อสร้างและไบโอมของมอดอื่น (บอกชื่อได้เลย) ที่ที่เราตาย จุดเกิด เตียง และเพื่อน แล้วบอกทิศ (เหนือ ตะวันออกเฉียงใต้...) ระยะทาง และพิกัด สั่ง **"พาไปหมู่บ้าน"** / "ไปหมู่บ้านกัน" มันจะเดินนำไปจนถึง รอเราถ้าตามไม่ทัน ไกลแค่ไหนก็ไป
- **Understands amounts**: "get 64 meat" / "หาเนื้อมา 64 ชิ้น" brings 64 pieces of meat (it hunts as many animals as it takes), "หมู 20 ชิ้น", "2 แถว" (2 stacks); "kill 3 cows" still means 3 cows.
  **เข้าใจจำนวน**: "หาเนื้อมา 64 ชิ้น" ได้เนื้อ 64 ชิ้นจริงๆ (ล่าสัตว์กี่ตัวก็ได้จนครบ) "หมู 20 ชิ้น" "2 แถว" (128) ส่วน "ฆ่าวัว 3 ตัว" ก็ยังเป็น 3 ตัว
- **Knows things**: "what is this?" (what you hold or look at, which mod it's from, how to make it), "what's around?", "which mods are installed?". The AI (when on) knows all this too: where you are, the time, your inventory, nearby mobs, your mods.
  **รู้เรื่อง**: "นี่คืออะไร" (ของในมือ/ที่มองอยู่ มาจากมอดไหน ทำยังไง) "แถวนี้มีอะไร" "มีมอดอะไรบ้าง" ถ้าเปิด AI มันก็รู้ทั้งหมดนี้ด้วย (อยู่ไหน กี่โมง ของในตัว มอนแถวนั้น มอดที่ลง)
- **Remembers you in every world**: your deaths, the worlds you played, what it did for you and anything you tell it ("remember that the base is at 100 64 200" / "จำไว้ว่า..."). "What do you remember?" / "จำอะไรได้บ้าง", "forget ..." / "ลืม...". Kept in `config/helperbuddy-memory.json` on the computer that runs the world.
  **ความจำข้ามโลก**: จำได้ว่าเราตายกี่ครั้ง เคยเล่นโลกไหน ช่วยทำอะไรไปบ้าง และเรื่องที่สั่งให้จำ ("จำไว้ว่าฐานอยู่ที่ 100 64 200") ถาม "จำอะไรได้บ้าง" สั่ง "ลืม..." ได้ เก็บไว้ที่ `config/helperbuddy-memory.json` ในเครื่องที่เปิดโลก
- **Helpers talk to each other** when two or more are near you (and gossip about you).
  **ผู้ช่วยคุยกันเอง** ถ้ามีตั้งแต่ 2 ตัวอยู่ใกล้ๆ (แล้วนินทาเราด้วย)
- **Trolling** (with Hollow Hours): when a ghost comes, a cheeky helper may dig a hole and hide instead of helping, then climb out when it's gone ("I was helping... from a distance"). `trolling` / `trollChance` in `[personality]`.
  **กวนตีน** (เล่นกับ Hollow Hours): ผีมาแทนที่จะช่วย มันอาจขุดหลุมลงไปหลบ แล้วค่อยโผล่ออกมาตอนผีไปแล้ว ("ข้าก็ช่วยอยู่ห่างๆ นะ") ปิด/ปรับได้ที่ `trolling` / `trollChance`
- **A voice**: every helper babbles in a voice of its own when it talks (sassy ones fast and bouncy, rude ones low). With a Gemini AI key it can talk with a real AI voice. `[voice]`: `voice = auto | babble | ai | off`, `volume`.
  **มีเสียงพูด**: ผู้ช่วยแต่ละตัวพูดเป็นเสียงของตัวเอง (แบบการ์ตูน ตัวกวนพูดเร็ว ตัวหยาบเสียงต่ำ) ถ้าใส่คีย์ Gemini จะพูดเป็นเสียง AI จริงได้ ตั้งค่าใน `[voice]`
- **No distance limit**: the search radius goes up to 256 blocks, a helper with nothing left nearby walks on further to look (`exploreFurther`), and the area around a working helper stays loaded while you're online, so it keeps working far away from you (`keepChunksLoaded`).
  **ไม่จำกัดระยะ**: ระยะหาของได้ถึง 256 บล็อก ถ้าแถวนั้นหมดแล้วมันจะเดินไปหาไกลขึ้นเอง (`exploreFurther`) และพื้นที่รอบผู้ช่วยที่กำลังทำงานจะไม่ถูกปิดโหลด ทำงานต่อได้แม้อยู่ไกลจากเรา (`keepChunksLoaded`)
- The **Helper Contract** is harder to craft: gold ingot, emerald, gold ingot / diamond, book and quill, diamond / gold ingot, iron block, gold ingot. (You still get one free when you first join.)
  **สัญญาจ้างผู้ช่วย**คราฟยากขึ้น: ทอง มรกต ทอง / เพชร หนังสือกับปากกาขนนก เพชร / ทอง บล็อกเหล็ก ทอง (เข้าเกมครั้งแรกยังได้ฟรี 1 ใบ)

**2.12.2**
- Hollow Hours: **drinking water** (sneak + right-click water with an empty hand) works everywhere now: flowing water (rivers, streams, shores, waterfalls), water under lily pads (swamps), past grass, flowers and reeds, waterlogged blocks, water cauldrons (clean: no nausea), and the water you stand or swim in. It no longer fails now and then when the server saw your aim a little differently.
  Hollow Hours: **กินน้ำ** (ย่อ + คลิกขวาที่น้ำ มือเปล่า) ใช้ได้ทุกที่แล้ว: น้ำไหล (แม่น้ำ ลำธาร ริมฝั่ง น้ำตก) น้ำใต้ใบบัว (หนองน้ำ) มองผ่านหญ้า ดอกไม้ ต้นอ้อได้ บล็อกที่มีน้ำขัง หม้อน้ำ (สะอาด ไม่เวียนหัว) และน้ำที่ยืน/ว่ายอยู่ แก้บั๊กกดกินน้ำแล้วบางทีไม่ได้กิน

**2.12.1 — a haunted main menu**
- Hollow Hours: the **main menu** is haunted now. Behind the buttons a dead forest under a blood moon slowly turns, and the five ghosts stand round you in the dark by their cold lanterns (one reaching for you, one crawling up to you, one grinning behind you...). "HOLLOW HOURS" flickers in red under the Minecraft logo, and the yellow splash lines are spooky ones (English and Thai).
  Hollow Hours: **หน้าเมนูหลัก**หลอนแล้ว ฉากหลังเป็นป่าไม้ตายใต้พระจันทร์สีเลือดที่หมุนช้าๆ ผีทั้ง 5 ยืนล้อมเราในความมืดข้างตะเกียงวิญญาณ (ตนหนึ่งยื่นมือมาคว้า ตนหนึ่งคลานขึ้นมาหา ตนหนึ่งยืนยิ้มอยู่ข้างหลัง...) ใต้โลโก้ Minecraft มีคำว่า "HOLLOW HOURS" สีแดงกะพริบ และข้อความสีเหลืองข้างโลโก้เป็นประโยคหลอนๆ (ไทย/อังกฤษ)
- Guns: no muzzle flash in your own view while aiming through the scope.
  ปืน: ตอนเล็ง/ส่องกล้อง จะไม่เห็นไฟปากกระบอกบังจอแล้ว

**2.12.0 — the last chapter**
- Hollow Hours now has an **ending**. 20 days after you come home from the haven (`daysAfterReturn`), the crying under the mountain starts... and on that night **all five ghosts come at once**.
  Hollow Hours มี**ตอนจบ**แล้ว! กลับบ้านจากดินแดนปลอดภัยได้ 20 วัน (`daysAfterReturn`) จะเริ่มได้ยินเสียงร้องไห้ใต้ภูเขา... แล้วคืนนั้น **ผีทั้ง 5 ตนจะมาพร้อมกัน**
- **Knock out all five** - this time once they're down, they stay down. With the last one down they remember who they were, point the way and fade, and everyone gets an **old map of Mist Hollow** (hold it to see how far and which way).
  **ทำให้ผีสลบให้ครบทั้ง 5 ตน** คราวนี้สลบแล้วจะไม่ฟื้น พอตนสุดท้ายล้ม พวกเขาจะจำได้ว่าตัวเองเคยเป็นใคร บอกทาง แล้วจางหายไป ทุกคนได้ **แผนที่เก่าของหุบหมอก** (ถือไว้จะบอกระยะและทิศทาง)
- Follow it to **the mountain** and the old gold mine under it (a chest of supplies waits at the mine mouth). In the cavern below sits **The Thing That Weeps Under the Mountain**: it slams the floor (get out of the red ring, then hit the hand while it's down), sweeps the floor (jump over it), grabs you (mash to break free, or friends hit it hard), rains black tears and screams. It gets angrier below 2/3 and 1/3 of its health; more players = more health.
  ตามแผนที่ไปที่**ภูเขา** และเหมืองทองเก่าข้างใต้ (มีหีบเสบียงรออยู่ที่ปากเหมือง) ในถ้ำข้างล่างคือ **สิ่งที่ร้องไห้ใต้ภูเขา** มันฟาดมือลงพื้น (ออกจากวงสีแดง แล้วตีมือมันตอนวางอยู่บนพื้น) กวาดมือไปตามพื้น (กระโดดข้าม) จับตัวเรา (กดรัวให้หลุด หรือให้เพื่อนตีมันแรงๆ) ทำน้ำตาสีดำตกจากเพดาน และกรีดร้อง เลือดเหลือ 2/3 และ 1/3 มันจะดุขึ้น ยิ่งผู้เล่นเยอะ มันยิ่งเลือดเยอะ
- Beat it and a **rift** of light opens where it sank. Step in: a flash, a boom - and the **end credits**. Afterwards you're home and the world is **at peace**: no more ghosts. Keep playing as long as you like.
  ชนะแล้วจะมี**รอยแยก**แสงสีขาวเปิดขึ้น เดินเข้าไป: แสงวาบ เสียงบึ้ม แล้ว**เครดิตตอนจบ**ขึ้น หลังจากนั้นกลับมาที่บ้าน และโลก**สงบ**แล้ว ไม่มีผีอีก เล่นต่อได้ตามสบาย
- Settings in `[endgame]` (on/off, days after coming home, how far the mountain is, the boss's health). Commands for admins: `/hollow endgame status | countdown | hunt | map | boss | win | credits | reset`.
  ตั้งค่าได้ใน `[endgame]` (เปิด/ปิด, จำนวนวันหลังกลับบ้าน, ระยะทางไปภูเขา, เลือดบอส) คำสั่งแอดมิน: `/hollow endgame status | countdown | hunt | map | boss | win | credits | reset`

**2.11.0 — fighting back**
- Hollow Hours: **weapons**. Guns: pistol, assault rifle (hold right-click), shotgun, sniper rifle; ammo for each, and hand grenades. Melee: combat knife, warrior's blade, battle axe, stun baton (slows what it hits), spear (longer reach). All craftable - press **K** for the recipes.
  Hollow Hours: **อาวุธ** ปืน: ปืนพก ไรเฟิลจู่โจม (คลิกขวาค้าง) ลูกซอง สไนเปอร์ มีกระสุนแต่ละแบบ และระเบิดมือ อาวุธระยะประชิด: มีดทหาร ดาบนักรบ ขวานรบ กระบองช็อตไฟฟ้า (ตีแล้วช้าลง) หอก (ตีได้ไกลกว่า) คราฟได้ทั้งหมด กด **K** ดูสูตร
- Controls: right-click = shoot, **R** = reload, sneak = aim (zoom). Melee: hold right-click = guard (right as a ghost swings = **parry**, it staggers), sprint + a full swing = heavy blow. **V** = dodge roll (a moment where nothing hits you).
  ปุ่ม: คลิกขวา = ยิง, **R** = รีโหลด, ย่อ = เล็ง (ซูม) อาวุธประชิด: คลิกขวาค้าง = ตั้งการ์ด (กดตอนผีจะตีพอดี = **ปัดป้อง** ผีเซ) วิ่ง + ฟันเต็มแรง = ฟันหนัก **V** = กลิ้งหลบ (ช่วงสั้นๆ ที่ไม่โดนอะไรเลย)
- **Military armour**: Tactical, Heavy Soldier and Ghost Hunter sets. A full set makes ghost hits weaker and breaking free easier.
  **ชุดเกราะทหาร** 3 ชุด: ยุทธวิธี / ทหารหนัก / นักล่าผี ใส่ครบชุด ผีตีเบาลง และดิ้นหลุดง่ายขึ้น
- **Knock ghosts out** (they can't die): hitting a ghost wears it down until it collapses for about 70 s, grumbling, then gets up angry. A knocked-out ghost drops whoever it was holding.
  **ทำผีสลบได้** (ผีไม่ตาย) ตีจนมันล้มสลบประมาณ 70 วินาที นอนบ่นไปเรื่อย แล้วลุกขึ้นมาแบบโกรธ ถ้าผีกำลังจับเพื่อนอยู่แล้วโดนตีจนสลบ เพื่อนจะหลุดทันที
- **Break free**: when a ghost grabs you, mash **Space** / left click to fill the bar before the time runs out. Stronger and better armoured = easier.
  **ดิ้นให้หลุด**: ตอนผีจับ กด **Space** / คลิกซ้ายรัวๆ ให้หลอดเต็มก่อนหมดเวลา ยิ่งแข็งแรง ยิ่งใส่เกราะ ยิ่งหลุดง่าย
- **Getting stronger**: playing sports on the playground (basketball hoop, football goal, kicking the ball...) raises **Strength** (0-100): more health, harder hits, easier struggles. Melee and gun **skill levels** go up as you use them. All shown on the phone's health page.
  **ออกกำลังกาย**: เล่นกีฬาที่สนามเด็กเล่น (ชู้ตบาส ยิงประตู เตะบอล...) เพิ่ม **ความแข็งแรง** (0-100): เลือดเยอะขึ้น ตีแรงขึ้น ดิ้นหลุดง่ายขึ้น และมี **เลเวลทักษะ** ของอาวุธประชิดกับปืน เพิ่มเมื่อใช้ ดูได้ในหน้าสุขภาพของโทรศัพท์
- Settings in `[combat]` of `hollowhours-common.toml` (knock-out time, ghost toughness, struggle on/off and time, fitness on/off).
  ตั้งค่าได้ใน `[combat]` ของ `hollowhours-common.toml` (เวลาสลบ, ความอึดของผี, เปิด/ปิดมินิเกมดิ้น และเวลา, เปิด/ปิดระบบความแข็งแรง)

**2.10.6**
- Hollow Hours: **recipe book** (like JEI). Press **K**: every item of the game - other mods too - on the right (search by name, `@mod` for one mod), click an item to see how it's made (crafting table, furnace, blast furnace, smoker, campfire, stonecutter, smithing table and other mods' own recipe types), right click to see what it's used for. In any inventory hover an item and press **R** (recipe) or **U** (uses). Click items inside a recipe to follow them, Backspace goes back, hold Shift to stop the cycling. (With JEI installed, R/U stay JEI's; K still opens this book.)
  Hollow Hours: **สมุดสูตรคราฟ** (แบบ JEI) กด **K** ด้านขวามีของทุกชิ้นในเกม รวมของมอดอื่น (ค้นหาด้วยชื่อ หรือ `@ชื่อมอด`) คลิกซ้าย = ดูวิธีทำ คลิกขวา = ดูว่าใช้ทำอะไร ในกระเป๋า/หีบ ชี้ของแล้วกด **R** (สูตร) หรือ **U** (ใช้ทำ) คลิกของในสูตรเพื่อดูต่อ Backspace = ย้อนกลับ กด Shift ค้าง = หยุดสลับของ

**2.10.5**
- Helper Buddy: the world-wide "friendly fire" switch from 2.10.4 is gone (players hit each other like normal Minecraft again). Instead every helper has its own switch in Settings: **"May hit other players"** - OFF by default, so your helper never attacks your friends (even when they hit you or it).
  Helper Buddy: เอาระบบ "ตีเพื่อน" ของ 2.10.4 ออกแล้ว (ผู้เล่นตีกันได้ตามปกติ) แทนด้วยปุ่มในตั้งค่าของผู้ช่วยแต่ละตัว **"ตีผู้เล่นคนอื่นได้"** เริ่มต้นปิด ผู้ช่วยจะไม่ตีเพื่อนเราเลย (ถึงเพื่อนจะตีเราหรือตีผู้ช่วย)
- Hollow Hours: jumping no longer moves your hands/item up to your face, and the body's jump pose (arms over the head) is gone.
  Hollow Hours: กระโดดแล้วมือ/ของในมือไม่ตบหน้าแล้ว และเอาท่ากระโดดยกแขนของตัวละครออก
- Hollow Hours: dropped tools, buckets, bottles and other 3D items lie flat on the ground instead of standing upright.
  Hollow Hours: ของที่ดรอป (เครื่องมือ ถัง ขวด ไอเท็ม 3D) นอนราบกับพื้น ไม่ตั้งตรงแล้ว
- Hollow Hours: a tree falls in one go wherever you chop it - the trunk below the cut comes down too, no stump left to chop again.
  Hollow Hours: ตัดต้นไม้ตรงไหนก็ล้มทั้งต้นในครั้งเดียว ลำต้นด้านล่างล้มไปด้วย ไม่ต้องตีตอซ้ำ

**2.10.3**
- AI (both mods): the default Gemini model is now `gemini-flash-lite-latest` (works with free API keys). Leave `model = ""` in the config to use it.
  AI (ทั้ง 2 มอด): โมเดล Gemini เริ่มต้นเป็น `gemini-flash-lite-latest` (ใช้กับคีย์ฟรีได้) ปล่อย `model = ""` ไว้ได้เลย

**2.10.2**
- First person is plain again by default: no arms holding things, no body when looking down (Minecraft's normal view). Existing settings are switched once; turn them back on with `firstPersonArms` / `firstPersonBody = true` in `hollowhours-client.toml`.
  มุมมองบุคคลที่ 1 กลับเป็นแบบปกติ: ไม่เห็นแขน ไม่เห็นตัว (เหมือน Minecraft ปกติ) ปรับให้อัตโนมัติ ถ้าอยากเปิดกลับ ตั้ง `firstPersonArms` / `firstPersonBody = true`

**2.10.1**
- Chat: while a friend is dead and hasn't respawned yet, your messages don't get through ("No signal"), and they show as "No signal..." in the contact list — until they respawn.
  แชท: ตอนเพื่อนตายแล้วยังไม่กดเกิดใหม่ ส่งข้อความไปจะขึ้น "ไม่มีสัญญาณ" และในรายชื่อจะขึ้น "ไม่มีสัญญาณ..." จนกว่าเพื่อนจะเกิด

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
