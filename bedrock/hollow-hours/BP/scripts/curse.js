import { world, system, ItemStack } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { GHOSTS, showRed, showForm } from "./talk.js";
import { drainSoul, healSoul, damageMult, initSoul } from "./soul.js";
import { drainFlashlight } from "./flashlight.js";
import { standCell } from "./util.js";

// =====================================================================
//  ระบบ "ขอของจากผี" + ของต้องสาป
//  - ปุ่มลับในสมุดคู่มือ -> ขอของ (ไม่มีคำอธิบายในเกม)
//  - v1.6: ขอได้ 3 ครั้งต่อคนต่อการเจอผี 1 ครั้ง, ขอแล้วผีไม่หยุดไล่ + สะสมบัฟให้ผีที่เกิดครั้งถัดไป (ครั้งเดียว)
//  - ของที่ได้ = ของต้องสาป: ดีกว่าปกติ 12.5% แต่ทุกครั้งที่ใช้มีผลเสียหนัก (แต่ละชนิดไม่เหมือนกัน)
//  - คราฟ: ใช้ของต้องสาปครบจำนวนอย่างน้อย 1 อย่างในสูตร ผลลัพธ์จะกลายเป็นของต้องสาป
// =====================================================================
const CFG = {
  ASK_COOLDOWN: 40,            // กันกดรัว: ขอซ้ำได้ทุก 2 วินาที (ต่อผู้เล่น)
  GIVE_CHANCE: 0.5,            // โอกาสได้ของ 50%
  BUFF_STEP: 0.05,             // ขอ 1 ครั้ง = บัฟ +5%
  BUFF_RARE: 0.10,             // ของดี/หายาก (เพชร ฯลฯ) = ผีโหดขึ้น +10%
  BUFF_CAP: 2.0,               // เพดานบัฟรวม +200% (กันผีเร็ว/แรงจนเกมพัง) ปรับได้
  CURSE_BONUS: 0.125,          // ของต้องสาปดีกว่าของปกติ 12.5%
  SCAN_EVERY: 10,              // สแกนช่องเก็บของเพื่อจับการคราฟ (tick)
  LEDGER_TTL: 60 * 20,         // จำของต้องสาปที่เพิ่งหายจากตัวนานกี่ tick (เผื่อวางในช่องคราฟนาน)
};

let api = null;
const rnd = (a, b) => a + Math.random() * (b - a);
const pick = (a) => a[Math.floor(Math.random() * a.length)];
const strip = (id) => String(id).replace(/^minecraft:/, "");

// ---------- ตัวอักษรมั่ว ----------
const GIB = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ#$%&*@!?+=-_<>~^¥";
function seeded(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619); }
  return () => { h += 0x6D2B79F5; let t = Math.imul(h ^ (h >>> 15), 1 | h); t ^= t + Math.imul(t ^ (t >>> 7), 61 | t); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
function gib(len, r) { r = r || Math.random; let s = ""; for (let i = 0; i < len; i++) s += GIB[Math.floor(r() * GIB.length)]; return s; }
const hashStr = (s) => { let h = 7; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0; return h; };
export function askLabel() { return `§c§k${gib(12)}`; }   // §k = ตัวอักษรสุ่มเปลี่ยนตลอดเวลาฝั่งไคลเอนต์

// =====================================================================
//  บัฟผี (สะสมจากการขอ ใช้กับผีที่เกิดครั้งถัดไปแล้วรีเซ็ต)
// =====================================================================
const BUFF_KEY = "ghost:curse_buff";
let buffMem = 0;
function getPool() { try { const v = world.getDynamicProperty(BUFF_KEY); if (typeof v === "number") return v; } catch (e) {} return buffMem; }
function setPool(x) { buffMem = x; try { world.setDynamicProperty(BUFF_KEY, x); } catch (e) {} }
const RARE = ["diamond", "netherite", "emerald", "nether_star", "elytra", "totem", "beacon", "dragon", "trident", "heart_of_the_sea", "ancient_debris", "enchanted_golden_apple", "shulker", "echo_shard", "wither", "conduit", "smithing_template", "mace", "heavy_core", "breeze_rod", "trial_key", "ominous", "end_crystal", "sponge", "spawn_egg"];
const isRare = (id) => RARE.some((k) => strip(id).includes(k));
function addBuff(id) { setPool(Math.min(CFG.BUFF_CAP, getPool() + (isRare(id) ? CFG.BUFF_RARE : CFG.BUFF_STEP))); }
// ผีตัวที่เกิดต่อไปเรียกใช้ครั้งเดียว: คืนตัวคูณ แล้วล้างบัฟ
export function takeBuff() {
  const x = getPool();
  if (!(x > 0)) return null;
  setPool(0);
  const m = Math.min(x, CFG.BUFF_CAP);
  return { dur: 1 + m, vis: 1 + m, dmg: m, spd: 1 + m };   // อยู่นานขึ้น / มองไกลขึ้น / ตีแรงขึ้น / วิ่งไวขึ้น
}

// =====================================================================
//  ของต้องสาป: ชื่อมั่ว (สีแดง §k เปลี่ยนตลอด) + lore เป็นตัวระบุ
// =====================================================================
const NAME_PRE = "§r§c§k";
const MARK = "§r§4§oของต้องสาป";
const USES_RE = /§c(\d+)§8\/(\d+)/;

export function isCursed(it) {
  try { const l = it.getLore(); return l.length > 0 && l[0] === MARK; } catch (e) { return false; }
}
function readUses(it) {
  try { const l = it.getLore(); const m = l[1] && USES_RE.exec(l[1]); return m ? { n: Number(m[1]), N: Number(m[2]) } : null; } catch (e) { return null; }
}
function writeUses(it, n, N) {
  const l = it.getLore().slice();
  l[1] = `§r§8ความทนทาน §c${n}§8/${N}`;
  it.setLore(l);
}
export function makeCursed(id, amount, ghostN) {
  const it = new ItemStack(id, amount || 1);
  const cat = catInfo(id, it);
  it.nameTag = NAME_PRE + gib(12, seeded(id));
  const lore = [MARK];
  if (cat.N > 0) lore.push(`§r§8ความทนทาน §c${cat.N}§8/${cat.N}`);
  const d = DRAW[cat.dr];
  if (d) lore.push(`§r§5คำสาป: §d${d.name}`);
  const g = ghostN && GHOSTS[ghostN - 1];
  if (g && MARK_OF[ghostN]) lore.push(`§r§8ตรา${MARK_OF[ghostN].name}: §7${g.name}`);
  lore.push("§r§4§k" + gib(16, seeded(id + "L")));
  it.setLore(lore);
  return it;
}
function ghostMarkOf(it) {
  try {
    for (const l of it.getLore()) {
      if (!l.startsWith("§r§8ตรา")) continue;
      const i = GHOSTS.findIndex((g) => l.endsWith(g.name));
      if (i >= 0) return i + 1;
    }
  } catch (e) {}
  return 0;
}

// =====================================================================
//  หมวดของ -> ผลเสีย (แต่ละหมวดไม่ซ้ำกัน) + จำนวนครั้งที่ใช้ได้ก่อนพัง (กำหนดตายตัว)
// =====================================================================
const CATS = {
  sword:    { N: 30, dr: "soul",      acc: ["hit", "break", "useon"], bonus: true },
  axe:      { N: 30, dr: "blood",     acc: ["hit", "break", "useon"], bonus: true },
  pickaxe:  { N: 40, dr: "starve",    acc: ["hit", "break", "useon"], bonus: true, haste: true },
  shovel:   { N: 35, dr: "blind",     acc: ["hit", "break", "useon"], bonus: true, haste: true },
  hoe:      { N: 35, dr: "rot",       acc: ["hit", "break", "useon"], bonus: true, haste: true },
  bucket:   { N: 0,  dr: "drown",     acc: ["use", "useon"] },
  bed:      { N: 0,  dr: "nightmare", acc: ["useon"] },
  bow:      { N: 25, dr: "burn",      acc: ["release"], bonus: true },
  crossbow: { N: 25, dr: "recoil",    acc: ["release"], bonus: true },
  trident:  { N: 20, dr: "lightning", acc: ["release", "hit"], bonus: true },
  shears:   { N: 30, dr: "xp",        acc: ["use", "useon", "break"] },
  flint:    { N: 20, dr: "wither",    acc: ["useon"] },
  rod:      { N: 25, dr: "thief",     acc: ["use"] },
  shield:   { N: 25, dr: "scramble",  acc: ["use"] },
  helmet:   { N: 25, dr: "turn",      acc: ["hurt"], armor: "Head" },
  chest:    { N: 25, dr: "freeze",    acc: ["hurt"], armor: "Chest" },
  legs:     { N: 25, dr: "stun",      acc: ["hurt"], armor: "Legs" },
  boots:    { N: 25, dr: "sky",       acc: ["hurt"], armor: "Feet" },
  elytra:   { N: 25, dr: "summon",    acc: ["hurt"], armor: "Chest" },
};
const DEFAULT_DR = ["call", "foodrot", "summon", "strip", "scramble"];   // ของอื่น ๆ ทั้งหมด: สุ่มตายตัวตามชื่อไอเทม
const USE_ITEMS = new Set(["ender_pearl", "snowball", "egg", "firework_rocket", "splash_potion", "lingering_potion", "wind_charge", "experience_bottle", "fire_charge", "spyglass", "totem_of_undying"]);
function catKey(s) {
  if (/_sword$/.test(s) || s === "mace") return "sword";
  if (/_pickaxe$/.test(s)) return "pickaxe";
  if (/_axe$/.test(s)) return "axe";
  if (/_shovel$/.test(s)) return "shovel";
  if (/_hoe$/.test(s)) return "hoe";
  if (/_helmet$/.test(s)) return "helmet";
  if (/_chestplate$/.test(s)) return "chest";
  if (/_leggings$/.test(s)) return "legs";
  if (/_boots$/.test(s)) return "boots";
  if (s === "bow") return "bow";
  if (s === "crossbow") return "crossbow";
  if (s === "trident") return "trident";
  if (s === "shears") return "shears";
  if (s === "flint_and_steel") return "flint";
  if (s === "fishing_rod" || /_on_a_stick$/.test(s)) return "rod";
  if (s === "shield") return "shield";
  if (s === "elytra") return "elytra";
  if (/bucket$/.test(s)) return "bucket";
  if (/_bed$/.test(s)) return "bed";
  return null;
}
// ---- คำสาปประจำตัวของแต่ละชิ้น (ตายตัวตามชนิดของ: วัสดุต่างกัน = คำสาปต่างกัน)
const TOOL_CURSE = {
  sword:   { wooden: "rot", stone: "stun", iron: "blood", golden: "xp", diamond: "hollow", netherite: "pact" },
  axe:     { wooden: "web", stone: "tremor", iron: "bleed", golden: "thief", diamond: "freeze", netherite: "pursuit" },
  pickaxe: { wooden: "worms", stone: "starve", iron: "rust", golden: "foodrot", diamond: "eatlight", netherite: "doom" },
  shovel:  { wooden: "blind", stone: "web", iron: "weight", golden: "scramble", diamond: "echo", netherite: "summon" },
  hoe:     { wooden: "rot", stone: "worms", iron: "coldhands", golden: "xp", diamond: "phantoms", netherite: "hollow" },
};
const ARMOR_CURSE = {
  helmet: { leather: "turn", chainmail: "echo", iron: "blind", golden: "ghostlight", diamond: "nightmare", netherite: "hollow", turtle: "drown" },
  chest:  { leather: "freeze", chainmail: "fragile", iron: "weight", golden: "pact", diamond: "bleed", netherite: "doom" },
  legs:   { leather: "stun", chainmail: "tremor", iron: "weight", golden: "scramble", diamond: "fragile", netherite: "pursuit" },
  boots:  { leather: "sky", chainmail: "web", iron: "stun", golden: "warp", diamond: "sky", netherite: "call" },
};
const SIGNATURE = {
  mace: "tremor", bow: "burn", crossbow: "recoil", trident: "lightning", shield: "scramble", fishing_rod: "thief",
  carrot_on_a_stick: "thief", warped_fungus_on_a_stick: "burn", flint_and_steel: "wither", shears: "xp", elytra: "ghostlight",
  totem_of_undying: "doom", ender_pearl: "warp", ender_eye: "echo", golden_apple: "hollow", enchanted_golden_apple: "pact",
  bucket: "drown", water_bucket: "drown", lava_bucket: "burn", milk_bucket: "hollow", powder_snow_bucket: "freeze",
  torch: "eatlight", soul_torch: "hollow", lantern: "eatlight", soul_lantern: "hollow", compass: "echo", recovery_compass: "doom", clock: "doom",
  spyglass: "nightmare", firework_rocket: "sky", snowball: "freeze", egg: "worms", experience_bottle: "xp", wind_charge: "recoil", fire_charge: "burn",
  bread: "starve", cooked_beef: "rot", beef: "rot", cooked_porkchop: "worms", cooked_chicken: "worms", chicken: "rot", apple: "worms",
  golden_carrot: "blind", carrot: "starve", baked_potato: "starve", cake: "starve", cookie: "foodrot", pumpkin_pie: "nightmare",
  melon_slice: "rot", sweet_berries: "bleed", rotten_flesh: "rot", spider_eye: "wither", cooked_salmon: "drown", cooked_cod: "drown",
  mushroom_stew: "hollow", honey_bottle: "web", dried_kelp: "drown", potion: "nightmare", splash_potion: "echo",
  tnt: "recoil", obsidian: "doom", crafting_table: "scramble", chest: "thief", furnace: "burn", anvil: "weight", jukebox: "echo",
  ladder: "sky", oak_door: "call", enchanting_table: "pact", name_tag: "pursuit", lead: "web", saddle: "warp", map: "echo",
  ghost_closet_item: "nightmare",
};
const MISC_POOL = ["call", "foodrot", "summon", "strip", "scramble", "echo", "worms", "web", "ghostlight", "tremor", "eatlight", "bleed", "phantoms", "vex", "coldhands", "warp", "weight", "fragile", "pursuit", "hollow"];
function curseFor(s, key) {
  if (SIGNATURE[s]) return SIGNATURE[s];
  let m = /^(wooden|stone|iron|golden|diamond|netherite)_(sword|axe|pickaxe|shovel|hoe)$/.exec(s);
  if (m && TOOL_CURSE[m[2]]) return TOOL_CURSE[m[2]][m[1]];
  m = /^(leather|chainmail|iron|golden|diamond|netherite|turtle)_(helmet|chestplate|leggings|boots)$/.exec(s);
  if (m) { const part = { helmet: "helmet", chestplate: "chest", leggings: "legs", boots: "boots" }[m[2]]; const c = ARMOR_CURSE[part][m[1]]; if (c) return c; }
  if (/_bed$/.test(s)) return "nightmare";
  if (key && CATS[key]) return CATS[key].dr;
  if (/(_log|_planks|_wood)$/.test(s)) return pick2(["web", "worms", "call"], s);
  if (/(ore|ingot|raw_|_block)$/.test(s) || /^raw_/.test(s)) return pick2(["rust", "weight", "doom", "thief"], s);
  return MISC_POOL[hashStr(s) % MISC_POOL.length];
}
function pick2(a, s) { return a[hashStr(s) % a.length]; }
function catInfo(id, it) {
  const s = strip(id), key = catKey(s);
  if (key) return Object.assign({ key }, CATS[key], { dr: curseFor(s, key) });
  let dur = null;
  try { dur = it && it.getComponent("minecraft:durability"); } catch (e) {}
  return {
    key: "misc", N: dur ? 30 : 0, dr: curseFor(s, null),
    acc: (dur || USE_ITEMS.has(s)) ? ["use", "useon", "hit", "break"] : ["consume", "useon"],
  };
}

// =====================================================================
//  ผลเสียทั้งหมด (ทำงานตอน "ใช้" เท่านั้น ไม่ทำงานตอนคราฟ)
//  frac = ความคืบหน้าของการใช้ (0..1) ยิ่งใกล้พังยิ่งหนัก
// =====================================================================
// ความแรงคำสาปตามความดีของของ: ธรรมดา x1 / ของดี (เพชร มรกต ฯลฯ) x2 / ของดีสุด (เนเธอร์ไรต์ ท็อทเทม ปีกเอลีทรา ฯลฯ) x3
const VERY_RARE = ["netherite", "nether_star", "elytra", "totem", "beacon", "dragon", "enchanted_golden_apple", "heart_of_the_sea", "conduit", "mace", "end_crystal", "heavy_core", "trial_key", "ominous"];
function potency(id) {
  const s = strip(id || "");
  if (VERY_RARE.some((k) => s.includes(k))) return 3;
  return isRare(s) ? 2 : 1;
}
let POT = 1;   // ตั้งค่าตอนรันคำสาปแต่ละครั้ง แล้วรีเซ็ตกลับเป็น 1
const AMP_CAP = { slowness: 4, mining_fatigue: 4, wither: 1, poison: 5, weakness: 6, hunger: 6 };   // กันตายทีเดียว/ขยับไม่ได้
const NO_SCALE = new Set(["levitation"]);   // ลอยแรงกว่านี้ = ตกตาย ไม่ขยาย
function eff(p, id, ticks, amp) {
  const base = Math.max(0, Math.floor(amp));
  let a = base, t = Math.floor(ticks);
  if (POT > 1 && !NO_SCALE.has(id)) {
    a = Math.max(base, Math.min(AMP_CAP[id] ?? 6, Math.floor(base * POT) + (POT - 1)));
    t = Math.min(Math.floor(ticks * POT), id === "wither" ? 10 * 20 : 180 * 20);
  }
  try { p.addEffect(id, t, { amplifier: a, showParticles: false }); } catch (e) {}
}
function hp(p) { try { return p.getComponent("minecraft:health").currentValue; } catch (e) { return 20; } }
function hurt(p, amount, cause) {   // ผลเสียหนักแต่ไม่ฆ่าในทีเดียว (เหลือ 1 HP เป็นอย่างน้อย)
  const a = Math.min(amount * POT, Math.max(0, hp(p) - 1));
  if (a > 0) { try { p.applyDamage(a, { cause }); } catch (e) {} }
}
const FOODS = new Set(["bread", "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_salmon", "cooked_cod", "cooked_rabbit", "apple", "baked_potato", "carrot", "golden_carrot", "beef", "porkchop", "chicken", "mutton", "rabbit", "cookie", "pumpkin_pie", "melon_slice", "sweet_berries", "golden_apple", "mushroom_stew", "beetroot_soup", "rabbit_stew", "dried_kelp"]);
const callCooldown = new Map();
function invOf(p) { try { return p.getComponent("minecraft:inventory").container; } catch (e) { return null; } }
function dropNear(p, it, r) {
  try { const l = p.location; p.dimension.spawnItem(it, { x: l.x + rnd(-r, r), y: l.y + 0.5, z: l.z + rnd(-r, r) }); } catch (e) {}
}
function sfx(p, id, pitch = 1, vol = 1, around = 0) {
  try {
    const l = p.location, a = Math.random() * Math.PI * 2;
    const at = around ? { x: l.x + Math.cos(a) * around, y: l.y + 1, z: l.z + Math.sin(a) * around } : undefined;
    p.playSound(id, at ? { location: at, volume: vol, pitch } : { volume: vol, pitch });
  } catch (e) {}
}
function warpNear(p, r0, r1) {
  const l = p.location, dim = p.dimension;
  for (let i = 0; i < 12; i++) {
    const a = Math.random() * Math.PI * 2, r = rnd(r0, r1);
    const c = standCell(dim, l.x + Math.cos(a) * r, l.z + Math.sin(a) * r, l.y + 3, 6, 2);
    if (c) { try { p.teleport({ x: c.x + 0.5, y: c.y, z: c.z + 0.5 }, { dimension: dim }); return true; } catch (e) {} }
  }
  return false;
}
function wearDurability(it, frac) {
  try {
    const d = it.getComponent("minecraft:durability");
    if (!d) return null;
    const nd = d.damage + Math.max(1, Math.ceil(d.maxDurability * frac));
    if (nd >= d.maxDurability) return "broke";
    d.damage = nd;
    return it;
  } catch (e) { return null; }
}
const DRAW = {
  soul:      { name: "ดูดพลังชีวิต", msg: "§4วิญญาณของคุณถูกสูบไปอีกนิด...", fn(p, f) {
    const a = Math.min(3, 1 + Math.floor(f * 3));
    eff(p, "weakness", 35 * 20, a); eff(p, "slowness", 30 * 20, Math.min(2, a)); eff(p, "mining_fatigue", 30 * 20, a);
    if (f >= 0.6) { eff(p, "blindness", 80, 0); eff(p, "hunger", 300, 2); }
  } },
  blood:     { name: "ดื่มเลือด", msg: "§4ของสาปดื่มเลือดของคุณ", fn(p, f) { hurt(p, 5 + Math.floor(f * 5), "magic"); eff(p, "blindness", 40, 0); } },
  starve:    { name: "หิวโหย", msg: "§4ความหิวกัดกินจากข้างใน", fn(p, f) { eff(p, "hunger", 30 * 20, 3 + f * 3); eff(p, "nausea", 8 * 20, 0); } },
  blind:     { name: "ดวงตามืดบอด", msg: "§4โลกทั้งใบมืดลง", fn(p, f) { eff(p, "darkness", 20 * 20, 0); eff(p, "blindness", (6 + f * 8) * 20, 0); } },
  rot:       { name: "เนื้อเน่า", msg: "§4เนื้อของคุณเริ่มเน่า", fn(p, f) { eff(p, "poison", 16 * 20, 1 + f * 2); eff(p, "nausea", 10 * 20, 0); eff(p, "weakness", 14 * 20, 1); } },
  drown:     { name: "จมน้ำสาป", msg: "§4น้ำสาปท่วมปอดคุณ", fn(p, f) { hurt(p, 5 + f * 5, "drowning"); eff(p, "slowness", 12 * 20, 2); eff(p, "nausea", 6 * 20, 0); } },
  nightmare: { name: "ฝันร้าย", msg: "§4มันเห็นคุณแล้ว", fn(p, f) {
    try { api.jumpscare(p, true); } catch (e) {}
    eff(p, "nausea", 14 * 20, 0); eff(p, "darkness", 30 * 20, 0); eff(p, "blindness", 6 * 20, 0); eff(p, "weakness", 25 * 20, 1);
  } },
  burn:      { name: "ไฟนรก", msg: "§4ไฟนรกลามขึ้นตัว", fn(p, f) { try { p.setOnFire(Math.min(14, (7 + Math.floor(f * 7)) * POT), true); } catch (e) {} } },
  lightning: { name: "ฟ้าลงทัณฑ์", msg: "§4ฟ้าลงโทษ", fn(p) { try { p.dimension.spawnEntity("minecraft:lightning_bolt", p.location); } catch (e) {} } },
  recoil:    { name: "แรงสะท้อน", msg: "§4แรงสาปผลักคุณกระเด็น", fn(p, f) {
    const a = Math.random() * Math.PI * 2, dx = Math.cos(a), dz = Math.sin(a);
    try { p.applyKnockback(dx, dz, 2.8 * Math.min(POT, 1.6), 0.9); } catch (e) { try { p.applyKnockback({ x: dx * 2.8, z: dz * 2.8 }, 0.9); } catch (e2) {} }
    hurt(p, 4 + f * 4, "magic");
  } },
  xp:        { name: "ลืมเลือน", msg: "§4ความทรงจำของคุณถูกดูดไป", fn(p, f) { try { p.addLevels(-(4 + Math.floor(f * 6)) * POT); } catch (e) {} } },
  wither:    { name: "สัมผัสความตาย", msg: "§4ความตายสัมผัสคุณ", fn(p, f) { eff(p, "wither", 8 * 20, f > 0.6 ? 1 : 0); eff(p, "blindness", 50, 0); } },
  thief:     { name: "มือมืด", msg: "§4บางอย่างหลุดมือคุณไป", fn(p) {   // ของในกระเป๋าปลิวไปตกไกล ๆ
    const inv = invOf(p); if (!inv) return;
    const idx = [];
    for (let i = 0; i < inv.size; i++) { const it = inv.getItem(i); if (it && !isCursed(it)) idx.push(i); }
    for (let k = 0; k < POT + 1 && idx.length; k++) {
      const j = Math.floor(Math.random() * idx.length), i = idx.splice(j, 1)[0], it = inv.getItem(i);
      if (it) { inv.setItem(i, undefined); dropNear(p, it, 9); }
    }
  } },
  turn:      { name: "บิดคอ", msg: "§4ใครบางคนหมุนคอคุณ", fn(p) {
    try {
      const r = p.getRotation(), nr = { x: r.x, y: r.y + 180 };
      try { p.setRotation(nr); } catch (e) { p.teleport(p.location, { rotation: nr, dimension: p.dimension }); }
    } catch (e) {}
    eff(p, "blindness", 30, 0); eff(p, "nausea", 6 * 20, 0);
  } },
  freeze:    { name: "หนาวจากหลุมศพ", msg: "§4ความหนาวจากหลุมศพ", fn(p, f) { hurt(p, 4 + f * 4, "freezing"); eff(p, "slowness", 10 * 20, 3); eff(p, "mining_fatigue", 10 * 20, 2); } },
  stun:      { name: "ร่างแข็งทื่อ", msg: "§4ร่างกายแข็งทื่อ", fn(p, f) { eff(p, "slowness", (3 + f * 2) * 20, 9); eff(p, "blindness", 50, 0); eff(p, "weakness", 12 * 20, 1); } },
  sky:       { name: "ดึงขึ้นฟ้า", msg: "§4แรงสาปดึงคุณขึ้นฟ้า", fn(p, f) { eff(p, "levitation", (hp(p) <= 12 ? 2 : 4) * 20, 3 + (f > 0.6 ? 1 : 0)); } },
  call:      { name: "ปลุกผี", msg: "§4การใช้มัน...ปลุกบางสิ่งขึ้นมา", fn(p) {   // ดึงผีมาหาทันที
    const now = system.currentTick;
    if (now - (callCooldown.get(p.id) ?? -1e9) < 8 * 60 * 20) { eff(p, "darkness", 10 * 20, 0); return; }   // v1.6: ปลุกได้ทุก 8 นาที (ผีไม่ถี่เกิน)
    callCooldown.set(p.id, now);
    try { api.summonGhost(p); } catch (e) {}
  } },
  foodrot:   { name: "อาหารเน่า", msg: "§4อาหารของคุณเน่าเปื่อย", fn(p) {
    const inv = invOf(p); if (!inv) return;
    const idx = [];
    for (let i = 0; i < inv.size; i++) { const it = inv.getItem(i); if (it && FOODS.has(strip(it.typeId))) idx.push(i); }
    if (!idx.length) { eff(p, "hunger", 15 * 20, 2); return; }
    for (let k = 0; k < POT + 1 && idx.length; k++) {
      const j = Math.floor(Math.random() * idx.length), i = idx.splice(j, 1)[0], it = inv.getItem(i);
      try { inv.setItem(i, new ItemStack("minecraft:rotten_flesh", it.amount)); } catch (e) {}
    }
  } },
  summon:    { name: "คนตายตื่น", msg: "§4คนตายตื่นขึ้น", fn(p) {
    const l = p.location;
    for (let k = 0; k < 3 + (POT - 1) * 2; k++) { try { p.dimension.spawnEntity("minecraft:zombie", { x: l.x + rnd(-4, 4), y: l.y, z: l.z + rnd(-4, 4) }); } catch (e) {} }
  } },
  strip:     { name: "ถอดเกราะ", msg: "§4เกราะหลุดจากตัวคุณ", fn(p) {
    const eq = p.getComponent("minecraft:equippable"), worn = [];
    for (const s of ["Head", "Chest", "Legs", "Feet"]) { try { const it = eq.getEquipment(s); if (it) worn.push([s, it]); } catch (e) {} }
    if (!worn.length) { eff(p, "weakness", 20 * 20, 1); return; }
    for (let k = 0; k < POT && worn.length; k++) {
      const [s, it] = worn.splice(Math.floor(Math.random() * worn.length), 1)[0];
      try { eq.setEquipment(s, undefined); dropNear(p, it, 4); } catch (e) {}
    }
  } },
  scramble:  { name: "มือสั่นสลับของ", msg: "§4มือของคุณสั่น ของในมือสลับที่", fn(p) {
    const inv = invOf(p); if (!inv) return;
    for (let k = 0; k < 8 * POT; k++) {
      const a = Math.floor(Math.random() * 9), b = Math.floor(Math.random() * 27) + 9;
      try { const x = inv.getItem(a), y = inv.getItem(b); inv.setItem(a, y); inv.setItem(b, x); } catch (e) {}
    }
    eff(p, "nausea", 6 * 20, 0);
  } },
  // ---- v1.6 คำสาปใหม่
  bleed:     { name: "เลือดไหลไม่หยุด", msg: "§4แผลเปิดออก... เลือดไหลไม่หยุด", fn(p) {
    const n = 5 + POT * 2;
    for (let i = 1; i <= n; i++) system.runTimeout(() => { try { if (p.isValid() && hp(p) > 2) { p.applyDamage(1, { cause: "magic" }); api.puff(p.dimension, "minecraft:redstone_wire_dust_particle", p.location, 0.5); } } catch (e) {} }, i * 30);
  } },
  weight:    { name: "แบกวิญญาณ", msg: "§4มีบางอย่างหนักอึ้งเกาะหลังคุณ", fn(p, f) { eff(p, "slowness", (12 + f * 8) * 20, 3); eff(p, "mining_fatigue", 12 * 20, 1); sfx(p, "ghost.breath", 0.6, 0.8); } },
  hollow:    { name: "ดูดวิญญาณ", msg: "§5§lวิญญาณของคุณถูกดึงออกจากร่าง...", fn(p) {
    drainSoul(p, 8 * POT, "quiet");
    eff(p, "nausea", 8 * 20, 0); eff(p, "darkness", 6 * 20, 0);
    try { api.puff(p.dimension, "minecraft:soul_particle", p.location, 0.9); } catch (e) {}
    sfx(p, "mob.ghast.moan", 0.35, 1);
  } },
  echo:      { name: "เสียงในหัว", msg: "§4เสียงกระซิบดังขึ้นรอบตัว...", fn(p) {
    eff(p, "darkness", 12 * 20, 0);
    const ids = ["ghost.creak", "ghost.sniff", "ghost.breath", "mob.ghast.moan", "ghost.growl"];
    for (let i = 0; i < 6; i++) system.runTimeout(() => sfx(p, pick(ids), rnd(0.5, 0.8), 0.9, rnd(3, 7)), 10 + i * 25 + Math.floor(Math.random() * 15));
  } },
  fragile:   { name: "เกราะผุ", msg: "§4เกราะของคุณผุกร่อน", fn(p) {
    let eq; try { eq = p.getComponent("minecraft:equippable"); } catch (e) { return; }
    for (const s of ["Head", "Chest", "Legs", "Feet"]) {
      try { const it = eq.getEquipment(s); if (!it || isCursed(it)) continue; const r = wearDurability(it, 0.08 * POT); if (r === "broke") { eq.setEquipment(s, undefined); sfx(p, "random.break", 0.6); } else if (r) eq.setEquipment(s, r); } catch (e) {}
    }
  } },
  rust:      { name: "สนิมกิน", msg: "§4สนิมคืบคลานไปทั่วของของคุณ", fn(p) {
    const inv = invOf(p); if (!inv) return;
    const idx = [];
    for (let i = 0; i < 9; i++) { const it = inv.getItem(i); if (it && !isCursed(it)) { try { if (it.getComponent("minecraft:durability")) idx.push(i); } catch (e) {} } }
    for (let k = 0; k < 1 + POT && idx.length; k++) {
      const i = idx.splice(Math.floor(Math.random() * idx.length), 1)[0], it = inv.getItem(i);
      const r = wearDurability(it, 0.12);
      if (r === "broke") { inv.setItem(i, undefined); sfx(p, "random.break", 0.7); } else if (r) inv.setItem(i, r);
    }
  } },
  ghostlight:{ name: "แสงวิญญาณ", msg: "§bร่างของคุณเรืองแสง... มันเห็นคุณทะลุกำแพง", fn(p) {
    const T = 30 * POT;                          // วินาที: ร่างเรืองแสงวิญญาณ ผีเห็นทะลุกำแพง
    try { p.addTag("ghost_marked"); } catch (e) {}
    let k = 0;
    const id = system.runInterval(() => {
      k++;
      try { if (!p.isValid()) { system.clearRun(id); return; } api.puff(p.dimension, "minecraft:soul_particle", p.location, 0.6); } catch (e) {}
      if (k >= T) { system.clearRun(id); try { p.removeTag("ghost_marked"); } catch (e) {} }
    }, 20);
  } },
  coldhands: { name: "มือชา", msg: "§4มือของคุณชาจนจับอะไรไม่ได้", fn(p) {
    try {
      const eq = p.getComponent("minecraft:equippable"), it = eq.getEquipment("Mainhand");
      if (it) { eq.setEquipment("Mainhand", undefined); dropNear(p, it, 3); }
    } catch (e) {}
    eff(p, "mining_fatigue", 15 * 20, 2); eff(p, "weakness", 15 * 20, 1);
  } },
  eatlight:  { name: "กินแสง", msg: "§8ความมืดกลืนกินแสงรอบตัว...", fn(p) { eff(p, "darkness", 20 * 20, 0); eff(p, "blindness", 3 * 20, 0); try { drainFlashlight(p, 45 * 20 * POT); } catch (e) {} } },
  doom:      { name: "นับถอยหลัง", msg: "§4§lบางอย่างกำลังนับถอยหลัง...", fn(p) {
    const T = (t, s2) => { try { p.onScreenDisplay.setTitle(t, { subtitle: s2 || "", fadeInDuration: 0, stayDuration: 18, fadeOutDuration: 4 }); } catch (e) {} };
    system.runTimeout(() => { T("§4§l3"); sfx(p, "random.click", 0.5); }, 20);
    system.runTimeout(() => { T("§4§l2"); sfx(p, "random.click", 0.5); }, 40);
    system.runTimeout(() => { T("§4§l1"); sfx(p, "random.click", 0.5); }, 60);
    system.runTimeout(() => { try { if (!p.isValid()) return; p.dimension.spawnEntity("minecraft:lightning_bolt", p.location); hurt(p, 6, "magic"); eff(p, "wither", 6 * 20, 1); } catch (e) {} }, 80);
  } },
  pact:      { name: "สัญญาเลือด", msg: "§4ร่างกายหายดี... แต่วิญญาณต้องจ่ายแทน", fn(p) {
    try { const h = p.getComponent("minecraft:health"); h.setCurrentValue(Math.min(h.effectiveMax, h.currentValue + 6)); } catch (e) {}
    drainSoul(p, 7 * POT, "quiet"); eff(p, "wither", 5 * 20, 0);
  } },
  pursuit:   { name: "ผีโกรธ", msg: "§4§lมันโกรธ!", fn(p) {
    let g = null, bd = 1e9;
    try { for (const e of p.dimension.getEntities({ families: ["ghost"], location: p.location, maxDistance: 80 })) { const d = distTo(p, e); if (d < bd) { bd = d; g = e; } } } catch (e) {}
    if (g) { try { g.triggerEvent("ghost:enrage"); } catch (e) {} sfx(p, "mob.ghast.scream", 0.45, 1); }
    else DRAW.call.fn(p);
  } },
  worms:     { name: "หนอนชอนไช", msg: "§4มีบางอย่างชอนไชออกมาจากพื้น", fn(p) {
    const l = p.location;
    for (let k = 0; k < 2 + POT; k++) { try { p.dimension.spawnEntity("minecraft:silverfish", { x: l.x + rnd(-2, 2), y: l.y, z: l.z + rnd(-2, 2) }); } catch (e) {} }
  } },
  phantoms:  { name: "ปีกดำ", msg: "§4เสียงปีกกระพือเหนือหัว...", fn(p) {
    const l = p.location;
    for (let k = 0; k < POT; k++) { try { p.dimension.spawnEntity("minecraft:phantom", { x: l.x + rnd(-6, 6), y: l.y + 12, z: l.z + rnd(-6, 6) }); } catch (e) {} }
  } },
  vex:       { name: "วิญญาณร้าย", msg: "§4วิญญาณร้ายหลุดออกมาจากของ", fn(p) {
    const l = p.location;
    for (let k = 0; k < 1 + POT; k++) { try { p.dimension.spawnEntity("minecraft:vex", { x: l.x + rnd(-3, 3), y: l.y + 2, z: l.z + rnd(-3, 3) }); } catch (e) {} }
  } },
  web:       { name: "ใยแมงมุม", msg: "§7ใยเหนียว ๆ พันขาคุณไว้", fn(p) {
    const dim = p.dimension, l = p.location, cells = [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1]].slice(0, 1 + POT * 2);
    for (const [dx, dz] of cells) {
      const pos = { x: Math.floor(l.x) + dx, y: Math.floor(l.y), z: Math.floor(l.z) + dz };
      try {
        const b = dim.getBlock(pos);
        if (b && b.isAir) {
          b.setType("minecraft:web");
          system.runTimeout(() => { try { const b2 = dim.getBlock(pos); if (b2 && b2.typeId === "minecraft:web") b2.setType("minecraft:air"); } catch (e) {} }, 18 * 20);
        }
      } catch (e) {}
    }
  } },
  warp:      { name: "บิดมิติ", msg: "§5โลกบิดเบี้ยว...", fn(p) { warpNear(p, 6, 11); eff(p, "nausea", 8 * 20, 0); eff(p, "darkness", 3 * 20, 0); } },
  tremor:    { name: "มือสั่น", msg: "§4มือสั่นจนจับอะไรไม่อยู่", fn(p) {
    try { p.runCommand("camerashake add @s 0.6 6 rotational"); } catch (e) {}
    eff(p, "mining_fatigue", 15 * 20, 2); eff(p, "weakness", 15 * 20, 1);
  } },
};
// ตราของผีแต่ละตน (ติดมากับของที่ขอจากผีตนนั้น) ทำงานเพิ่ม 35% ทุกครั้งที่คำสาปทำงาน
const MARK_OF = {
  1: { name: "เสียงร่ำไห้", msg: "§7เสียงร้องไห้ดังอยู่ในหัวคุณ", fn(p) { eff(p, "nausea", 7 * 20, 0); for (let i = 0; i < 3; i++) system.runTimeout(() => sfx(p, "mob.ghast.moan", rnd(0.7, 0.95), 0.8, 4), i * 20); drainSoul(p, 2, "quiet"); } },
  2: { name: "น้ำตาเจ้าสาว", msg: "§3น้ำเย็นเฉียบไหลออกมาจากปากคุณ", fn(p) { let w = false; try { w = p.isInWater; } catch (e) {} hurt(p, w ? 5 : 3, "drowning"); eff(p, "slowness", 6 * 20, 1); } },
  3: { name: "เถ้าถ่าน", msg: "§6เถ้าร้อน ๆ ร่วงลงบนตัวคุณ", fn(p) { try { p.setOnFire(4, true); } catch (e) {} eff(p, "hunger", 10 * 20, 1); } },
  4: { name: "รอยยิ้ม", msg: "§5ใครบางคนหัวเราะคิกคักข้างหู :)", fn(p) { warpNear(p, 3, 7); eff(p, "darkness", 4 * 20, 0); sfx(p, "mob.witch.ambient", 1.5, 1, 2); } },
  5: { name: "ความหิวกระหาย", msg: "§fความหิวที่ไม่ใช่ของคุณ...", fn(p) { eff(p, "hunger", 15 * 20, 2); drainSoul(p, 2, "quiet"); } },
};
const SOUL_COST = [0, 3, 5, 8];   // วิญญาณที่ถูกสูบทุกครั้งที่คำสาปทำงาน ตามความดีของของ
function runDrawback(p, key, frac, id, item) {
  const d = DRAW[key]; if (!d) return;
  POT = potency(id);
  try { p.onScreenDisplay.setActionBar(d.msg); } catch (e) {}
  try { p.playSound("mob.ghast.moan", { volume: 1, pitch: 0.5 }); } catch (e) {}
  try { d.fn(p, Math.min(1, frac * (1 + (POT - 1) * 0.5))); } catch (e) {}
  const gm = item ? ghostMarkOf(item) : 0;
  if (gm && MARK_OF[gm] && Math.random() < 0.35) {
    system.runTimeout(() => { try { p.onScreenDisplay.setActionBar(MARK_OF[gm].msg); MARK_OF[gm].fn(p); } catch (e) {} }, 40);
  }
  try { drainSoul(p, SOUL_COST[POT] || 3); } catch (e) {}
  POT = 1;
}
// นับของต้องสาปที่พกอยู่ (ใช้คำนวณการสูบวิญญาณแบบค่อย ๆ)
export function countCursed(p) {
  let n = 0;
  const inv = invOf(p);
  if (inv) for (let i = 0; i < inv.size; i++) { const it = inv.getItem(i); if (it && isCursed(it)) n++; }
  try { const eq = p.getComponent("minecraft:equippable"); for (const s of ["Head", "Chest", "Legs", "Feet", "Offhand"]) { const it = eq.getEquipment(s); if (it && isCursed(it)) n++; } } catch (e) {}
  return n;
}
function breakFx(p) {
  try { p.playSound("random.break", { volume: 1.2, pitch: 0.6 }); } catch (e) {}
  try { p.onScreenDisplay.setTitle("§4§l...แตกสลาย", { fadeInDuration: 0, stayDuration: 25, fadeOutDuration: 10 }); } catch (e) {}
  try { api.puff(p.dimension, "minecraft:totem_particle", p.location, 0.6); } catch (e) {}
}

// =====================================================================
//  ตรวจจับ "การใช้งาน" ของที่ต้องสาป
// =====================================================================
const lastUse = new Map();
function mainhand(p) { try { return p.getComponent("minecraft:equippable").getEquipment("Mainhand"); } catch (e) { return null; } }
function handleUse(p, kind, evItem) {
  try {
    if (!p || p.typeId !== "minecraft:player") return;
    const held = mainhand(p);
    const it = (evItem && isCursed(evItem)) ? evItem : (held && isCursed(held) ? held : null);
    if (!it) return;
    const cat = catInfo(it.typeId, it);
    if (cat.armor || !cat.acc.includes(kind)) return;
    const now = system.currentTick, key = p.id + "|" + it.typeId;
    if (now - (lastUse.get(key) ?? -99) < 4) return;   // อีเวนต์เดียวกันยิงซ้ำหลายตัว นับครั้งเดียว
    lastUse.set(key, now);
    let frac = 0.5, broke = false;
    const du = readUses(it);
    if (du && held && held.typeId === it.typeId && isCursed(held)) {
      const n = du.n - 1;
      frac = Math.min(1, 1 - n / du.N);
      const eq = p.getComponent("minecraft:equippable");
      if (n <= 0) { eq.setEquipment("Mainhand", undefined); broke = true; frac = 1; }
      else { writeUses(held, n, du.N); eq.setEquipment("Mainhand", held); }
    }
    runDrawback(p, cat.dr, frac, it.typeId, it);
    if (broke) breakFx(p);
  } catch (e) {}
}
// เกราะ: ทำงานตอนโดนโจมตี
const ARMOR_OK = new Set(["entityAttack", "projectile", "entityExplosion", "blockExplosion"]);
const armorLast = new Map();
function armorHurt(p, dmg, cause) {
  if (!ARMOR_OK.has(cause)) return;
  let eq; try { eq = p.getComponent("minecraft:equippable"); } catch (e) { return; }
  let pieces = 0;
  for (const slot of ["Head", "Chest", "Legs", "Feet"]) {
    try {
      const it = eq.getEquipment(slot);
      if (!it || !isCursed(it)) continue;
      const cat = catInfo(it.typeId, it);
      if (!cat.armor) continue;
      pieces++;
      const now = system.currentTick, key = p.id + "|" + slot;
      if (now - (armorLast.get(key) ?? -99) < 20) continue;
      armorLast.set(key, now);
      let frac = 0.5, broke = false;
      const du = readUses(it);
      if (du) {
        const n = du.n - 1; frac = Math.min(1, 1 - n / du.N);
        if (n <= 0) { eq.setEquipment(slot, undefined); broke = true; frac = 1; }
        else { writeUses(it, n, du.N); eq.setEquipment(slot, it); }
      }
      runDrawback(p, cat.dr, frac, it.typeId, it);
      if (broke) breakFx(p);
    } catch (e) {}
  }
  if (pieces > 0 && dmg > 0) {   // เกราะต้องสาปดีกว่าปกติ 12.5% ต่อชิ้น: คืนเลือดตามสัดส่วนความเสียหาย
    try {
      const h = p.getComponent("minecraft:health");
      if (h.currentValue > 0) h.setCurrentValue(Math.min(h.effectiveMax, h.currentValue + dmg * CFG.CURSE_BONUS * pieces));
    } catch (e) {}
  }
}
const bonusMark = new Map();   // กันบัฟดาเมจวนซ้ำ
function extraDamage(victim, attacker, amount, cause) {
  if (!(amount > 0.05)) return;
  bonusMark.set(victim.id + "|" + attacker.id, { amt: amount, t: system.currentTick });
  try { victim.applyDamage(amount, { cause, damagingEntity: attacker }); } catch (e) {}
}
function onHurt(ev) {
  try {
    const v = ev.hurtEntity, src = ev.damageSource, att = src && src.damagingEntity, dmg = ev.damage;
    if (!v) return;
    if (att) {
      const mk = bonusMark.get(v.id + "|" + att.id);
      if (mk && system.currentTick - mk.t <= 3 && Math.abs(mk.amt - dmg) < 0.02) { bonusMark.delete(v.id + "|" + att.id); return; }
    }
    // ผู้เล่นโดนตี: ผีที่โดนบัฟตีแรงขึ้น + วิญญาณยิ่งน้อยยิ่งเจ็บ (รวมเป็นดาเมจเพิ่มครั้งเดียว)
    if (att && v.typeId === "minecraft:player" && att.id !== v.id) {
      let k = 0;
      if (String(att.typeId).startsWith("ghost:ghost_")) { const m = api.meta.get(att.id); if (m && m.buff && m.buff.dmg > 0) k += m.buff.dmg; }
      const cause = (src && src.cause) || "entityAttack";
      if (cause === "entityAttack" || cause === "projectile") k += damageMult(v) - 1;
      if (k > 0.01) extraDamage(v, att, dmg * k, cause === "projectile" ? "projectile" : "entityAttack");
    }
    // ผู้เล่นตีด้วยของต้องสาป
    if (att && att.typeId === "minecraft:player") {
      const held = mainhand(att);
      if (held && isCursed(held)) {
        const cat = catInfo(held.typeId, held);
        if (cat.bonus) extraDamage(v, att, dmg * CFG.CURSE_BONUS, src.cause || "entityAttack");
        handleUse(att, "hit", null);
      }
    }
    // ผู้เล่นใส่เกราะต้องสาป
    if (v.typeId === "minecraft:player") armorHurt(v, dmg, src && src.cause);
  } catch (e) {}
}

// =====================================================================
//  คราฟ: ใช้ของต้องสาปครบจำนวนอย่างน้อย 1 อย่างในสูตร -> ผลลัพธ์เป็นของต้องสาป
//  (Script API ไม่มีอีเวนต์คราฟ จึงจับจากการที่ของต้องสาปหายไป แล้วมีของคราฟได้โผล่เข้ากระเป๋า)
// =====================================================================
const COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"];
const GROUPS = {
  "#planks": ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak"].map((w) => `${w}_planks`),
  "#stone": ["cobblestone", "blackstone", "cobbled_deepslate"],
};
for (const c of COLORS) GROUPS[`#wool_${c}`] = [`${c}_wool`];
const RECIPES = new Map();   // ผลลัพธ์(id เต็ม) -> [{ n, ing:[[key,count],...] }]
function rec(out, n, ...ing) {
  const id = "minecraft:" + out;
  if (!RECIPES.has(id)) RECIPES.set(id, []);
  RECIPES.get(id).push({ n, ing });
}
(function buildRecipes() {
  for (const [m, mat] of [["wooden", "#planks"], ["stone", "#stone"], ["iron", "iron_ingot"], ["golden", "gold_ingot"], ["diamond", "diamond"]]) {
    rec(`${m}_sword`, 1, [mat, 2], ["stick", 1]);
    rec(`${m}_pickaxe`, 1, [mat, 3], ["stick", 2]);
    rec(`${m}_axe`, 1, [mat, 3], ["stick", 2]);
    rec(`${m}_shovel`, 1, [mat, 1], ["stick", 2]);
    rec(`${m}_hoe`, 1, [mat, 2], ["stick", 2]);
  }
  for (const [m, mat] of [["leather", "leather"], ["iron", "iron_ingot"], ["golden", "gold_ingot"], ["diamond", "diamond"]]) {
    rec(`${m}_helmet`, 1, [mat, 5]); rec(`${m}_chestplate`, 1, [mat, 8]);
    rec(`${m}_leggings`, 1, [mat, 7]); rec(`${m}_boots`, 1, [mat, 4]);
  }
  for (const [blk, it] of [["iron_block", "iron_ingot"], ["gold_block", "gold_ingot"], ["diamond_block", "diamond"], ["emerald_block", "emerald"], ["lapis_block", "lapis_lazuli"], ["redstone_block", "redstone"], ["coal_block", "coal"], ["netherite_block", "netherite_ingot"], ["copper_block", "copper_ingot"]]) rec(blk, 1, [it, 9]);
  rec("netherite_ingot", 1, ["netherite_scrap", 4], ["gold_ingot", 4]);
  rec("bucket", 1, ["iron_ingot", 3]);
  rec("shears", 1, ["iron_ingot", 2]);
  rec("flint_and_steel", 1, ["iron_ingot", 1], ["flint", 1]);
  rec("compass", 1, ["iron_ingot", 4], ["redstone", 1]);
  rec("clock", 1, ["gold_ingot", 4], ["redstone", 1]);
  rec("bow", 1, ["stick", 3], ["string", 3]);
  rec("arrow", 4, ["flint", 1], ["stick", 1], ["feather", 1]);
  rec("crossbow", 1, ["stick", 3], ["string", 2], ["iron_ingot", 1], ["tripwire_hook", 1]);
  rec("fishing_rod", 1, ["stick", 3], ["string", 2]);
  rec("shield", 1, ["#planks", 6], ["iron_ingot", 1]);
  rec("anvil", 1, ["iron_block", 3], ["iron_ingot", 4]);
  rec("cauldron", 1, ["iron_ingot", 7]);
  rec("hopper", 1, ["iron_ingot", 5], ["chest", 1]);
  rec("rail", 16, ["iron_ingot", 6], ["stick", 1]);
  rec("minecart", 1, ["iron_ingot", 5]);
  rec("iron_door", 3, ["iron_ingot", 6]);
  rec("iron_bars", 16, ["iron_ingot", 6]);
  rec("torch", 4, ["coal", 1], ["stick", 1]);
  rec("ladder", 3, ["stick", 7]);
  rec("chest", 1, ["#planks", 8]);
  rec("crafting_table", 1, ["#planks", 4]);
  rec("furnace", 1, ["#stone", 8]);
  rec("golden_apple", 1, ["gold_ingot", 8], ["apple", 1]);
  rec("golden_carrot", 1, ["gold_nugget", 8], ["carrot", 1]);
  rec("enchanting_table", 1, ["obsidian", 4], ["diamond", 2], ["book", 1]);
  rec("jukebox", 1, ["#planks", 8], ["diamond", 1]);
  rec("tnt", 1, ["gunpowder", 5], ["sand", 4]);
  for (const c of COLORS) rec(`${c}_bed`, 1, [`${c}_wool`, 3], ["#planks", 3]);
})();
function groupOf(key) { return GROUPS[key] || [key]; }
function ledgerHave(ledger, key) { let s = 0; for (const t of groupOf(key)) { const e = ledger.get("minecraft:" + t); if (e) s += e.n; } return s; }
function ledgerTake(ledger, key, amt) {
  for (const t of groupOf(key)) {
    const e = ledger.get("minecraft:" + t); if (!e || amt <= 0) continue;
    const k = Math.min(e.n, amt); e.n -= k; amt -= k;
  }
}
const inventories = new Map();   // player.id -> { slots, C, P, ledger }
function snapshot(p) {
  const inv = invOf(p); if (!inv) return null;
  const slots = [], C = new Map(), P = new Map();
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    if (!it) { slots.push(null); continue; }
    const c = isCursed(it);
    slots.push({ t: it.typeId, a: it.amount, c });
    const M = c ? C : P;
    M.set(it.typeId, (M.get(it.typeId) || 0) + it.amount);
  }
  return { inv, slots, C, P };
}
function convertArrival(p, snap, prev, X, need) {
  const inv = snap.inv, cand = [];
  for (let i = 0; i < snap.slots.length; i++) {
    const s = snap.slots[i];
    if (!s || s.c || s.t !== X) continue;
    const pv = prev.slots[i];
    cand.push({ i, a: s.a, inc: s.a - (pv && !pv.c && pv.t === X ? pv.a : 0) });
  }
  cand.sort((a, b) => b.inc - a.inc);
  for (const c of cand) {
    if (need <= 0) break;
    const take = Math.min(need, c.a);
    if (take >= c.a) { inv.setItem(c.i, makeCursed(X, take)); }
    else {
      const rest = inv.getItem(c.i); rest.amount = c.a - take; inv.setItem(c.i, rest);
      const left = inv.addItem(makeCursed(X, take)); if (left) dropNear(p, left, 1);
    }
    need -= take;
  }
}
function scanPlayer(p, now) {
  const snap = snapshot(p); if (!snap) return;
  let st = inventories.get(p.id);
  if (!st) { inventories.set(p.id, { slots: snap.slots, C: snap.C, P: snap.P, ledger: new Map() }); return; }
  for (const [t, e] of st.ledger) if (now - e.t > CFG.LEDGER_TTL || e.n <= 0) st.ledger.delete(t);
  const types = new Set([...snap.C.keys(), ...st.C.keys()]);
  for (const t of types) {      // ของต้องสาปที่หายไป = อาจกำลังอยู่ในช่องคราฟ
    const d = (snap.C.get(t) || 0) - (st.C.get(t) || 0);
    if (d < 0) { const e = st.ledger.get(t) || { n: 0, t: now }; e.n += -d; e.t = now; st.ledger.set(t, e); }
    else if (d > 0) { const e = st.ledger.get(t); if (e) e.n = Math.max(0, e.n - d); }
  }
  let changed = false;
  if (st.ledger.size) {
    for (const [X, cnt] of snap.P) {
      const d = cnt - (st.P.get(X) || 0);
      if (d <= 0) continue;
      const recs = RECIPES.get(X); if (!recs) continue;
      for (const r of recs) {
        let best = 0;
        for (const [key, k] of r.ing) best = Math.max(best, Math.floor(ledgerHave(st.ledger, key) / k));
        const crafts = Math.min(best, Math.floor(d / r.n));
        if (crafts <= 0) continue;
        for (const [key, k] of r.ing) { const c = Math.min(crafts, Math.floor(ledgerHave(st.ledger, key) / k)); if (c > 0) ledgerTake(st.ledger, key, c * k); }
        convertArrival(p, snap, st, X, crafts * r.n);
        changed = true;
        break;
      }
    }
  }
  const fresh = changed ? snapshot(p) : snap;
  if (fresh) { st.slots = fresh.slots; st.C = fresh.C; st.P = fresh.P; }
}

// =====================================================================
//  ขอของ: แปลงชื่อที่พิมพ์เป็นไอเทม (อังกฤษ/ไทย) ขอได้ทุกอย่างที่เป็นไอเทมจริงในเกม
// =====================================================================
const DENY = new Set(["air", "command_block", "repeating_command_block", "chain_command_block", "command_block_minecart", "structure_block", "structure_void", "jigsaw", "barrier", "border_block", "allow", "deny", "light_block", "debug_stick"]);
const TOOLS_TH = { "ดาบ": "sword", "ขวาน": "axe", "อีเต้อ": "pickaxe", "ที่ขุด": "pickaxe", "พิคแอ็กซ์": "pickaxe", "พลั่ว": "shovel", "เสียม": "shovel", "จอบ": "hoe", "หมวก": "helmet", "เสื้อเกราะ": "chestplate", "เกราะอก": "chestplate", "กางเกง": "leggings", "เกราะขา": "leggings", "รองเท้า": "boots", "บูท": "boots", "เกราะ": "chestplate" };
const MATS_TH = { "ไม้": "wooden", "หิน": "stone", "เหล็ก": "iron", "ทองคำ": "golden", "ทอง": "golden", "เพชร": "diamond", "นีเธอไรต์": "netherite", "หนัง": "leather", "โซ่": "chainmail" };
const ITEMS_TH = {
  "เพชร": "diamond", "ทอง": "gold_ingot", "ทองคำ": "gold_ingot", "ทองคำแท่ง": "gold_ingot", "เหล็ก": "iron_ingot", "เหล็กแท่ง": "iron_ingot", "ถ่าน": "coal", "มรกต": "emerald",
  "นีเธอไรต์": "netherite_ingot", "นีเธอไรต์แท่ง": "netherite_ingot", "ไม้": "oak_planks", "ไม้แท่ง": "stick", "ท่อนไม้": "oak_log", "หิน": "cobblestone", "ก้อนหิน": "cobblestone",
  "เตียง": "red_bed", "ถัง": "bucket", "ถังน้ำ": "water_bucket", "ถังลาวา": "lava_bucket", "ธนู": "bow", "ลูกธนู": "arrow", "หน้าไม้": "crossbow", "เบ็ด": "fishing_rod", "โล่": "shield",
  "กรรไกร": "shears", "คบไฟ": "torch", "คบเพลิง": "torch", "ขนมปัง": "bread", "แอปเปิล": "apple", "แอปเปิ้ล": "apple", "แอปเปิลทอง": "golden_apple", "สเต็ก": "cooked_beef", "สเต๊ก": "cooked_beef",
  "เนื้อ": "cooked_beef", "ไข่": "egg", "เอนเดอร์เพิร์ล": "ender_pearl", "หีบ": "chest", "โต๊ะคราฟ": "crafting_table", "เตาเผา": "furnace", "ทีเอ็นที": "tnt", "ระเบิด": "tnt",
  "เรดสโตน": "redstone", "ขนสัตว์": "white_wool", "ผ้าขนสัตว์": "white_wool", "ด้าย": "string", "เชือก": "string", "กระดูก": "bone", "ผงดินปืน": "gunpowder", "ดินปืน": "gunpowder",
  "ทริเดนท์": "trident", "ตรีศูล": "trident", "ปีก": "elytra", "ปีกเอลิตรา": "elytra", "โทเท็ม": "totem_of_undying", "ลาวา": "lava_bucket", "น้ำ": "water_bucket", "หนัง": "leather",
  "แท่งไม้": "stick", "ลูกศร": "arrow", "ดวงดาว": "nether_star", "เนเธอร์สตาร์": "nether_star", "บีคอน": "beacon", "หนังสือ": "book", "ไม้กระดาน": "oak_planks",
};
Object.assign(TOOLS_TH, { "อีเตอร์": "pickaxe", "พิคแอ๊กซ์": "pickaxe", "ที่ขุดแร่": "pickaxe", "หมวกเกราะ": "helmet", "เกราะหัว": "helmet" });
Object.assign(ITEMS_TH, {
  "ทองแดง": "copper_ingot", "เหล็กดิบ": "raw_iron", "ทองดิบ": "raw_gold", "ถ่านหิน": "coal", "ดิน": "dirt", "ทราย": "sand", "กระจก": "glass",
  "แครอท": "carrot", "แครอททอง": "golden_carrot", "มันฝรั่ง": "potato", "ปลา": "cooked_cod", "ปลาแซลมอน": "cooked_salmon", "เนื้อหมู": "cooked_porkchop", "เนื้อไก่": "cooked_chicken",
  "น้ำผึ้ง": "honey_bottle", "ขวด": "glass_bottle", "ออบซิเดียน": "obsidian", "โอบซิเดียน": "obsidian", "ประตู": "oak_door", "บันได": "ladder", "รถราง": "minecart",
  "เค้ก": "cake", "ขนมเค้ก": "cake", "คุกกี้": "cookie", "แตงโม": "melon_slice", "ฟักทอง": "pumpkin", "หินเหล็กไฟ": "flint", "เข็มทิศ": "compass", "นาฬิกา": "clock", "แผนที่": "map",
  "อานม้า": "saddle", "เชือกจูง": "lead", "ป้ายชื่อ": "name_tag", "ตาเอนเดอร์": "ender_eye", "ขวดประสบการณ์": "experience_bottle", "ผงเรดสโตน": "redstone",
  "บล็อกเพชร": "diamond_block", "บล็อกเหล็ก": "iron_block", "บล็อกทอง": "gold_block", "บล็อกมรกต": "emerald_block", "ลูกสูบ": "piston", "ตะเกียง": "lantern",
});
const NUM_TH = [["ยี่สิบ", 20], ["สามสิบ", 30], ["สี่สิบ", 40], ["ห้าสิบ", 50], ["หกสิบ", 60], ["สิบ", 10], ["หนึ่ง", 1], ["สอง", 2], ["สาม", 3], ["สี่", 4], ["ห้า", 5], ["หก", 6], ["เจ็ด", 7], ["แปด", 8], ["เก้า", 9]];
const NUM_EN = { one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10, twenty: 20, thirty: 30, fifty: 50 };
const EN_FILL = new Set(["give", "me", "i", "want", "need", "please", "pls", "plz", "some", "a", "an", "the", "of", "piece", "pieces", "pcs", "pc", "x", "can", "could", "have", "get", "would", "like", "id", "to", "you", "my", "ghost", "ask", "and"]);
// อ่านจำนวนจากข้อความ: 5 / x5 / 5x / ๕ / ห้า / five (ไม่ระบุ = 1, สูงสุด 64)
function parseRequest(raw) {
  let t = String(raw).toLowerCase().trim().replace(/[๐-๙]/g, (ch) => String("๐๑๒๓๔๕๖๗๘๙".indexOf(ch)));
  let qty = 1;
  const m = /(\d{1,3})/.exec(t);
  if (m) qty = Number(m[1]);
  else {
    let found = false;
    for (const [w, n] of NUM_TH) if (t.includes(w)) { qty = n; found = true; break; }
    if (!found) for (const w of (t.match(/[a-z]+/g) || [])) if (NUM_EN[w]) { qty = NUM_EN[w]; break; }
  }
  return { text: t, qty: Math.max(1, Math.min(64, qty)) };
}
// ชื่อไทย: หาคำที่ยาวที่สุดที่อยู่ที่ไหนก็ได้ในประโยค (ไม่ต้องพิมพ์ชื่อเพียว ๆ)
function thaiLookup(t) {
  const tk = Object.keys(TOOLS_TH).sort((a, b) => b.length - a.length);
  for (const k of tk) {
    const i = t.indexOf(k);
    if (i < 0) continue;
    const rest = t.slice(i + k.length);
    const mk = Object.keys(MATS_TH).sort((a, b) => b.length - a.length).find((x) => rest.includes(x));
    return `${mk ? MATS_TH[mk] : "iron"}_${TOOLS_TH[k]}`;
  }
  const ik = Object.keys(ITEMS_TH).sort((a, b) => b.length - a.length).find((x) => t.includes(x));
  return ik ? ITEMS_TH[ik] : null;
}
function resolveItem(raw) {
  const t = String(raw).trim().toLowerCase().replace(/^minecraft:/, "");
  if (!t) return null;
  const cands = [];
  const th = thaiLookup(t); if (th) cands.push(th);
  const en = (t.match(/[a-z_]+/g) || []).filter((w) => !EN_FILL.has(w) && !(w in NUM_EN)).join("_");
  if (en) {
    cands.push(en.replace(/^gold_(?=sword|pickaxe|axe|shovel|hoe|helmet|chestplate|leggings|boots)/, "golden_").replace(/^wood_(?=sword|pickaxe|axe|shovel|hoe)/, "wooden_").replace("pick_axe", "pickaxe"));
    cands.push(en);
    if (en.length > 3 && en.endsWith("s")) cands.push(en.slice(0, -1));
    if (en === "gold") cands.push("gold_ingot");
    if (en === "iron") cands.push("iron_ingot");
    if (en === "wood") cands.push("oak_planks");
    if (en === "sticks") cands.push("stick");
    if (en === "copper") cands.push("copper_ingot");
  }
  for (const c of cands) {
    if (DENY.has(c)) return { deny: true };
    try { new ItemStack("minecraft:" + c, 1); return "minecraft:" + c; } catch (e) {}
  }
  return null;
}

// =====================================================================
//  ผีตอบ
// =====================================================================
const ASK_LINES = {
  1: { give: ["เอาไปสิ... ฉันไม่ต้องการมันแล้ว", "ของนี้ไม่ใช่ของฉันตั้งแต่แรก... รับไป", "ได้... แต่มันจะจำเธอไว้"],
       refuse: ["ไม่มี... ฉันไม่มีอะไรเหลือให้เธอ", "ขอดังกว่านี้... ฉันยังไม่ได้ยิน", "ครั้งนี้ ฉันขอเก็บไว้"],
       unknown: ["สิ่งนั้น... ฉันไม่รู้จัก", "พูดใหม่อีกครั้ง... ฉันได้ยินแต่เสียงร้องไห้"] },
  2: { give: ["ของขวัญแต่งงาน... เอาไปเถอะ", "จากก้นทะเลสาบ... เป็นของเธอแล้ว", "รับไป... แล้วอย่าลืมฉัน"],
       refuse: ["ทะเลสาบไม่คายของนั้นออกมา", "คืนนี้ ฉันไม่ให้อะไรใครทั้งนั้น", "เขาก็ไม่เคยให้ฉันเหมือนกัน"],
       unknown: ["ใต้น้ำ... ฉันไม่ได้ยินชื่อนั้น", "คำนั้นจมหายไปแล้ว"] },
  3: { give: ["เอาไป! มันยังร้อนอยู่", "จากกองเถ้า... รับไปซะ", "ของนี้ไหม้ฉันมานานแล้ว! เอาไป"],
       refuse: ["ไม่! ฉันไม่ให้", "ขอไปก็กลายเป็นควัน", "ไม่มีอะไรเหลือรอดจากไฟ"],
       unknown: ["ฉันไม่รู้จักสิ่งนั้น! ไฟกินมันไปแล้ว", "พูดให้ชัดกว่านี้!"] },
  4: { give: ["ได้สิ :) ฉันใจดีเสมอ", "นี่ไง :) ราคาเดี๋ยวฉันเก็บทีหลัง", "รับไปเลย :) อย่าลืมยิ้มนะ"],
       refuse: ["ไม่ให้หรอก :) ขำดีใช่ไหม", "ครั้งนี้ไม่ :) ลองใหม่สิ", "ฉันยิ้ม แต่ไม่ให้ :)"],
       unknown: ["หืม? สิ่งนั้นมีอยู่จริงเหรอ :)", "ไม่เข้าใจเลย :) น่ารักจัง"] },
  5: { give: ["...เอาไป...", "ของ... เอาไป... แล้วหยุดมอง", "ได้... แต่ฉันหิว"],
       refuse: ["...ไม่", "ฉันหิว... ไม่ใช่ใจดี", "ไม่มีให้... ยืนนิ่ง ๆ"],
       unknown: ["...ไม่รู้จัก...", "ฉันไม่เข้าใจคำนั้น..."] },
};
const lastAsk = new Map();
function quiet(p, msg) { try { p.onScreenDisplay.setActionBar("§8..."); if (msg) p.sendMessage("§7" + msg); } catch (e) {} }
function distTo(a, b) { const x = a.location.x - b.location.x, y = a.location.y - b.location.y, z = a.location.z - b.location.z; return Math.sqrt(x * x + y * y + z * z); }
function nearestGhost(p) {
  let best = null, bd = Infinity;
  try {
    for (const g of p.dimension.getEntities({ families: ["ghost"] })) {
      if (api.leaving.has(g.id)) continue;
      const d = distTo(p, g); if (d < bd) { bd = d; best = g; }
    }
  } catch (e) {}
  return best;
}
// v1.6: ขอของได้ 3 ครั้งต่อคน ต่อการเจอผี 1 ครั้ง (ผีตัวใหม่โผล่มา = นับใหม่) และขอแล้วผีไม่หยุดไล่
const ASK_LIMIT = 3;
function asksUsed(m, p) { return (m.asks && m.asks[p.id]) || 0; }
function useAsk(m, p) { m.asks = m.asks || {}; m.asks[p.id] = asksUsed(m, p) + 1; }
const LIMIT_LINES = {
  1: ["พอแล้ว... ฉันให้เธอมากพอแล้ว", "ไม่มีอีกแล้ว... ไม่มีวันมีอีก"],
  2: ["ทะเลสาบไม่ให้อะไรเธออีกแล้ว", "เธอเอาไปจากฉันมากพอแล้ว..."],
  3: ["ไม่! ไม่มีอีกแล้ว! ไหม้ไปให้หมด!", "พอแล้ว! ต่อไปเธอต้องจ่ายเอง!"],
  4: ["ครบสามแล้วนะ :) ทีนี้ถึงตาฉันเอาบ้าง", "ไม่มีให้แล้ว :) แต่ฉันยังอยากได้ของจากเธอ"],
  5: ["...หมดแล้ว.", "...ไม่ให้. ...กิน."],
};
const pretty = (id) => strip(id).replace(/_/g, " ");
function sealFx(p, id, got) {
  try { p.addEffect("darkness", 50, { amplifier: 0, showParticles: false }); } catch (e) {}
  try { p.onScreenDisplay.setTitle("§4§l✝ ของต้องสาป ✝", { subtitle: `§c${pretty(id)} §4×${got}`, fadeInDuration: 4, stayDuration: 40, fadeOutDuration: 16 }); } catch (e) {}
  try { p.playSound("mob.ghast.moan", { volume: 0.8, pitch: 0.4 }); } catch (e) {}
  try { api.puff(p.dimension, "minecraft:soul_particle", p.location, 0.8); } catch (e) {}
}
function give(p, id, qty, ghostN) {
  const inv = invOf(p);
  let maxA = 1; try { maxA = new ItemStack(id, 1).maxAmount; } catch (e) {}
  const n = Math.max(1, Math.min(qty || 1, maxA > 1 ? maxA : 8));   // ของซ้อนได้: ไม่เกิน 1 สแตก, ของซ้อนไม่ได้: ไม่เกิน 8 ชิ้น
  const parts = maxA > 1 ? [n] : new Array(n).fill(1);
  for (const a of parts) {
    const it = makeCursed(id, a, ghostN);
    const left = inv ? inv.addItem(it) : it;   // เด้งเข้าตัวทันที เต็มก็ตกที่เท้า
    if (left) { try { p.dimension.spawnItem(left, p.location); } catch (e) {} }
  }
  return n;
}
function askGhost(p, text) {
  const g = nearestGhost(p);
  if (!g) { quiet(p, "ไม่มีผีอยู่ในมิตินี้"); return; }
  const now = system.currentTick;
  if (now - (lastAsk.get(p.id) ?? -1e9) < CFG.ASK_COOLDOWN) { quiet(p, "(รอ 2 วินาทีก่อนขอใหม่)"); return; }
  lastAsk.set(p.id, now);
  const n = Number(String(g.typeId).slice(-1)), info = GHOSTS[n - 1], L = ASK_LINES[n];
  const m = api.meta.get(g.id);
  if (!info || !L || !m) { quiet(p, "(ผียังไม่พร้อม ลองขออีกครั้งใน 2-3 วินาที)"); return; }
  const id = resolveItem(text);
  const qty = parseRequest(text).qty;
  if (!id || id.deny) { showRed(p, info, pick(L.unknown), g, "talk"); return; }       // ไม่รู้จักของ = ไม่เสียสิทธิ์
  if (asksUsed(m, p) >= ASK_LIMIT) { showRed(p, info, pick(LIMIT_LINES[n]), g, "rage"); return; }
  useAsk(m, p);
  addBuff(id);
  const left = ASK_LIMIT - asksUsed(m, p);
  if (Math.random() < CFG.GIVE_CHANCE) {
    const got = give(p, id, qty, n);
    showRed(p, info, pick(L.give), g, "talk");
    sealFx(p, id, got);
    try { p.sendMessage(`§4[ได้รับของต้องสาป: ${pretty(id)} ×${got}] §8(ขอได้อีก ${left} ครั้ง)`); } catch (e) {}
  } else {
    showRed(p, info, pick(L.refuse), g, "talk");
    try { p.sendMessage(`§8(ผีไม่ให้... ขอได้อีก ${left} ครั้ง)`); } catch (e) {}
  }
}
const REQ_RE = /(ขอ(?!ร้อง|โทษ|บคุณ|บใจ)|อยากได้|ต้องการ|เอา.+มา|give me|gimme|i want|i need|can i have|could i have|i'd like)/;
const OFFER_RE = /(จะให้|ขอให้|แลก|ข้อเสนอ|give you|i give|i'll give|ill give|offer|trade|deal)/;
// ใช้จากเมนูคุย: ถ้าประโยคเป็นการ "ขอของ" และรู้จักไอเทม -> เข้าระบบขอของ (คืน true)
export function tryAsk(p, text) {
  const t = String(text).toLowerCase();
  if (!REQ_RE.test(t) || OFFER_RE.test(t)) return false;
  const id = resolveItem(t);
  if (!id || id.deny) return false;
  if (!nearestGhost(p)) return false;
  askGhost(p, t);
  return true;
}
export async function openAsk(p) {
  const g = nearestGhost(p);
  if (!g) { quiet(p, "ไม่มีผีอยู่ในมิตินี้ ขอของไม่ได้"); return; }     // ไม่มีผีอยู่ ไม่มีอะไรเกิดขึ้น
  const n = Number(String(g.typeId).slice(-1)), info = GHOSTS[n - 1];
  const m = api.meta.get(g.id);
  const left = m ? Math.max(0, ASK_LIMIT - asksUsed(m, p)) : ASK_LIMIT;
  const marks = "§4" + "✝".repeat(left) + "§8" + "✝".repeat(ASK_LIMIT - left);
  const form = new ModalFormData().title(`§4§l§k||§r §4§lสัญญากับผี §r§4§l§k||`);
  const label = [
    `${info ? info.color + "§l" + info.name : "§4§k" + gib(8)}§r §7กำลังฟังอยู่...`,
    "",
    "§7บอกสิ่งที่ต้องการ เช่น §fเพชร 5§7, §fดาบเพชร§7, §fdiamond sword",
    "§8ทุกอย่างที่ได้ไป... เป็นของต้องสาป และผีจะไม่หยุดไล่",
    "",
    `§7คำขอที่เหลือ ${marks} §8(${left}/${ASK_LIMIT})`,
  ].join("\n");
  form.textField(label, "§8...ท่านต้องการสิ่งใด...", "");
  try { form.submitButton("§4§lยื่นคำขอ"); } catch (e) {}
  if (left <= 0 && info) { showRed(p, info, pick(LIMIT_LINES[n]), g, "rage"); return; }
  const r = await showForm(p, form);
  if (r.canceled || !r.formValues) return;
  askGhost(p, String(r.formValues[0] ?? ""));
}

// =====================================================================
//  เริ่มระบบ
// =====================================================================
function sub(kind, name, fn) {
  try { world[kind][name].subscribe(fn); } catch (e) { try { console.warn(`[curse] event ${kind}.${name} unavailable`); } catch (_) {} }
}
export function initCurse(a) {
  api = a;
  initSoul(a, countCursed);
  sub("afterEvents", "entityHurt", onHurt);
  sub("afterEvents", "playerBreakBlock", (ev) => handleUse(ev.player, "break", null));
  sub("afterEvents", "itemUse", (ev) => handleUse(ev.source, "use", ev.itemStack));
  sub("afterEvents", "itemUseOn", (ev) => handleUse(ev.source, "useon", ev.itemStack));
  sub("afterEvents", "itemReleaseUse", (ev) => handleUse(ev.source, "release", ev.itemStack));
  sub("afterEvents", "itemCompleteUse", (ev) => handleUse(ev.source, "consume", ev.itemStack));
  system.runInterval(() => {
    const now = system.currentTick;
    try { for (const p of world.getAllPlayers()) { try { scanPlayer(p, now); } catch (e) {} } } catch (e) {}
    for (const [k, v] of bonusMark) if (now - v.t > 10) bonusMark.delete(k);
  }, CFG.SCAN_EVERY);
  system.runInterval(() => {   // เครื่องมือขุดต้องสาป: เร็วขึ้น (Haste I)
    try {
      for (const p of world.getAllPlayers()) {
        const h = mainhand(p);
        if (h && isCursed(h) && catInfo(h.typeId, h).haste) eff(p, "haste", 60, 0);
      }
    } catch (e) {}
  }, 20);
}
