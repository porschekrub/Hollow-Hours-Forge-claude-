**Minecraft Java 1.20.1 + Forge 47.x** (not NeoForge). GeckoLib is bundled — just put the jars in your `mods` folder.
**Minecraft Java 1.20.1 + Forge 47.x** (ไม่ใช่ NeoForge) GeckoLib อยู่ในไฟล์แล้ว แค่ใส่ไฟล์ .jar ในโฟลเดอร์ `mods`

- `hollowhours-forge-1.20.1-2.2.2.jar` — Hollow Hours (ghost horror / ผีสยองขวัญ)
- `helperbuddy-forge-1.20.1-2.2.2.jar` — Helper Buddy (helper NPC / ผู้ช่วย)

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
