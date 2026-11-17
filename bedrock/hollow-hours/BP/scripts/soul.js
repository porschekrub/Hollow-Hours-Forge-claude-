import { world, system } from "@minecraft/server";
import { actionbar, sub } from "./util.js";

// =====================================================================
//  v1.6 วิญญาณ (0-100) — คำสาปค่อย ๆ สูบไป
//  70+  ปกติ
//  <70  วิญญาณสั่นคลอน : โดนตีเจ็บขึ้น 25% · หิวง่าย
//  <40  วิญญาณแหว่ง     : โดนตีเจ็บขึ้น 60% · เดินช้า · หิวเร็วมาก · อ่อนแรง
//  <15  แทบไม่เหลือ     : โดนตีเจ็บเป็น 2 เท่า · เดินช้ามาก · ของในมือ/เกราะผุพังเรื่อย ๆ
//  ฟื้นเอง +1 ทุก 30 วิ (ของต้องสาปในตัวสูบกลับ) · แอปเปิลทอง +15 · แอปเปิลทองอาบมนตร์ +50 · นอนจนเช้า +35 · ตายแล้วเกิดใหม่ อย่างน้อย 40
// =====================================================================
const DP = "ghost:soul";
const MAX = 100;
const cache = new Map();
let api = null;

export function getSoul(p) {
  if (!p) return MAX;
  if (cache.has(p.id)) return cache.get(p.id);
  let v = MAX;
  try { const x = p.getDynamicProperty(DP); if (typeof x === "number") v = x; } catch (_) {}
  cache.set(p.id, v);
  return v;
}
function setSoul(p, v) {
  v = Math.max(0, Math.min(MAX, v));
  cache.set(p.id, v);
  try { p.setDynamicProperty(DP, Math.round(v * 10) / 10); } catch (_) {}
  return v;
}
export function soulTier(v) { return v < 15 ? 3 : v < 40 ? 2 : v < 70 ? 1 : 0; }
export const TIER_NAME = ["§7ปกติ", "§dสั่นคลอน", "§5แหว่ง", "§4แทบไม่เหลือ"];
export function soulBar(v) {
  const n = Math.round(v / 10), col = v < 15 ? "§4" : v < 40 ? "§5" : v < 70 ? "§d" : "§b";
  return col + "▮".repeat(n) + "§8" + "▮".repeat(10 - n);
}
function hud(p, v, prefix) { actionbar(p, `${prefix || ""}§5วิญญาณ ${soulBar(v)} §7${Math.round(v)}%`); }

// สูบวิญญาณ (เรียกจากคำสาป)
export function drainSoul(p, amount, why) {
  const before = getSoul(p);
  const v = setSoul(p, before - amount);
  const t0 = soulTier(before), t1 = soulTier(v);
  if (why !== "quiet") system.runTimeout(() => { try { hud(p, v, "§4วิญญาณถูกสูบ...  "); } catch (_) {} }, 50);
  if (t1 > t0) {
    const msg = ["", "§dวิญญาณของคุณเริ่มสั่นคลอน... §7(โดนตีเจ็บขึ้น · หิวง่าย)", "§5วิญญาณของคุณแหว่งหายไป... §7(เดินช้า · เจ็บขึ้นมาก · หิวเร็ว)", "§4§lวิญญาณแทบไม่เหลือ! §r§7(ของในตัวจะผุพัง)"][t1];
    try { p.sendMessage(msg); } catch (_) {}
    try { p.playSound("mob.warden.heartbeat", { volume: 1, pitch: 0.6 }); } catch (_) {}
    try { p.runCommand("camerashake add @s 0.4 0.8 positional"); } catch (_) {}
  }
  if (v <= 0 && before > 0) {
    try { p.onScreenDisplay.setTitle("§4§lวิญญาณถูกพรากไป", { subtitle: "§7มันกำลังมาเอาส่วนที่เหลือ", fadeInDuration: 5, stayDuration: 50, fadeOutDuration: 20 }); } catch (_) {}
    try { if (api && api.summonGhost) api.summonGhost(p); } catch (_) {}
  }
  return v;
}
export function healSoul(p, amount, why) {
  const v = setSoul(p, getSoul(p) + amount);
  if (why) hud(p, v, why + "  ");
  return v;
}
// ตัวคูณดาเมจที่ผู้เล่นได้รับ (ใช้ใน curse.js ตอนโดนตี)
export function damageMult(p) { return [1, 1.25, 1.6, 2.0][soulTier(getSoul(p))]; }

function eff(p, id, t, a) { try { p.addEffect(id, t, { amplifier: a, showParticles: false }); } catch (_) {} }
// ของในมือ/ชุดเกราะผุพัง (วิญญาณแทบไม่เหลือ)
function rotGear(p) {
  let eq; try { eq = p.getComponent("minecraft:equippable"); } catch (_) { return; }
  const slots = ["Mainhand", "Head", "Chest", "Legs", "Feet"];
  const order = [slots[0], slots[1 + Math.floor(Math.random() * 4)]];
  let hit = false;
  for (const s of order) {
    try {
      const it = eq.getEquipment(s);
      if (!it) continue;
      const d = it.getComponent("minecraft:durability");
      if (!d) continue;
      const nd = d.damage + Math.max(1, Math.ceil(d.maxDurability * 0.06));
      if (nd >= d.maxDurability) {
        eq.setEquipment(s, undefined);
        try { p.playSound("random.break", { volume: 1, pitch: 0.6 }); } catch (_) {}
        try { p.sendMessage("§4ของของคุณผุพังไปพร้อมกับวิญญาณ..."); } catch (_) {}
      } else { d.damage = nd; eq.setEquipment(s, it); }
      hit = true;
    } catch (_) {}
  }
  if (hit) actionbar(p, "§4ของในตัวกำลังผุพัง...");
}
const sleptAt = new Map();
let lastTime = 0, k = 0;
function tick(now, countCursed) {
  k++;                                                     // เรียกทุก 1 วินาที
  let tod = 0; try { tod = world.getTimeOfDay(); } catch (_) {}
  const morning = lastTime > 20000 && tod < 2000;          // คืนผ่านไป (มีคนนอนหรือเวลาเดินจนเช้า)
  lastTime = tod;
  for (const p of world.getAllPlayers()) {
    try {
      let v = getSoul(p);
      let sleeping = false; try { sleeping = p.isSleeping; } catch (_) {}
      if (sleeping) sleptAt.set(p.id, now);
      if (morning && now - (sleptAt.get(p.id) ?? -1e9) < 400) { v = healSoul(p, 35, "§bนอนหลับพักผ่อน วิญญาณฟื้นขึ้น"); sleptAt.delete(p.id); }
      if (k % 30 === 0) {                                      // ทุก 30 วิ: ฟื้นเอง แต่ของต้องสาปในตัวสูบกลับ
        const n = countCursed ? countCursed(p) : 0;
        const delta = 1 - Math.min(4, n * 0.6);
        if (delta > 0 && v < MAX) v = setSoul(p, v + delta);
        else if (delta < 0) v = drainSoul(p, -delta, "quiet");
      }
      const tier = soulTier(v);
      if (tier >= 1 && k % 20 === 0) eff(p, "hunger", tier === 1 ? 80 : 60, tier - 1);
      if (tier >= 2) {
        eff(p, "slowness", 50, tier - 2);
        eff(p, "hunger", 50, tier - 1);
        eff(p, "weakness", 50, tier - 2);
      }
      if (tier >= 3) {
        eff(p, "mining_fatigue", 50, 0);
        if (k % 15 === 0) rotGear(p);
      }
      if (tier >= 1 && k % 60 === 0) hud(p, v, "");
    } catch (_) {}
  }
}
export function initSoul(a, countCursed) {
  api = a;
  system.runInterval(() => { try { tick(system.currentTick, countCursed); } catch (_) {} }, 20);
  sub("afterEvents", "itemCompleteUse", (ev) => {
    try {
      const id = ev.itemStack && ev.itemStack.typeId;
      if (id === "minecraft:golden_apple") healSoul(ev.source, 15, "§6แอปเปิลทองช่วยเยียวยาวิญญาณ");
      else if (id === "minecraft:enchanted_golden_apple") healSoul(ev.source, 50, "§6แสงศักดิ์สิทธิ์เยียวยาวิญญาณ");
    } catch (_) {}
  });
  sub("afterEvents", "playerSpawn", (ev) => {
    try { if (ev.initialSpawn) ev.player.removeTag("ghost_marked"); } catch (_) {}
    try { if (!ev.initialSpawn && getSoul(ev.player) < 40) setSoul(ev.player, 40); } catch (_) {}
  });
}
