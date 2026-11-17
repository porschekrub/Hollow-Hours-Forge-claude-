import { world, system, ItemStack, BlockPermutation } from "@minecraft/server";
import { fl, rand, valid, actionbar, getBlock, sound, isCreative, sub } from "./util.js";

// =====================================================================
//  v1.6 ไฟฉาย + แบตเตอรี่
//  - คลิกขวา = เปิด/ปิด   ·   ย่อ + คลิกขวา = เปลี่ยนแบต (ใช้แบต 1 ก้อนจากกระเป๋า)
//  - ถือแบตแล้วคลิกขวา = ใส่แบตให้ไฟฉายในมือรอง (หรือกระบอกที่แบตน้อยที่สุดในกระเป๋า)
//  - แบต 1 ก้อน = ส่องได้ 5 นาที (นับเฉพาะตอนเปิดและถืออยู่ในมือหลัก/มือรอง)
//  - แสง = บล็อกแสงล่องหน (light_block) วางตรงจุดที่ส่อง + ตามลำแสง แล้วลบทิ้งเมื่อขยับ/ปิด
//  - แบตใกล้หมดหรือผีอยู่ใกล้ -> ไฟกะพริบ  ·  ผีเห็นแสงไฟฉายได้ไกลกว่าเดิม
// =====================================================================
export const FLASH = "ghost:flashlight";
export const BATT = "ghost:battery";
const FULL = 5 * 60 * 20;                 // ticks ต่อแบต 1 ก้อน
const DP_CHARGE = "ghost:charge", DP_ON = "ghost:on", DP_FID = "ghost:fid";
const STEP = 2;                           // อัปเดตแสงทุก 2 tick
const SAVE_EVERY = 100;                   // เขียนค่าแบตลงไอเทมทุก 5 วินาที
const LOW = 0.15;
const DUR_SPAN = 950;                     // แถบความทนทานของไอเทม = แถบแบต (เว้นไว้ไม่ให้ถึง 1000 จะได้ไม่พัง)
const RANGE = 28;
const LIGHTS_DP = "ghost:flash_lights_v1";
const FACE = { Up: [0, 1, 0], Down: [0, -1, 0], North: [0, 0, -1], South: [0, 0, 1], East: [1, 0, 0], West: [-1, 0, 0] };

const mem = new Map();          // fid -> { charge, saved }  (ค่าแบตล่าสุดในหน่วยความจำ)
const pst = new Map();          // player.id -> { flickerUntil, nextFlicker, warnAt, lastSave, deadAt, ghostNear }
const placed = new Map();       // "dim|x|y|z" -> { dim, x, y, z, lv }
let lightsDirty = false, cleaned = false;
const permCache = new Map();

function lightPerm(lv) {
  if (permCache.has(lv)) return permCache.get(lv);
  let perm = null;
  try { perm = BlockPermutation.resolve(`minecraft:light_block_${lv}`); } catch (_) {
    try { perm = BlockPermutation.resolve("minecraft:light_block", { block_light_level: lv }); } catch (__) {}
  }
  permCache.set(lv, perm);
  return perm;
}
const AIR = (() => { try { return BlockPermutation.resolve("minecraft:air"); } catch (_) { return null; } })();
export const isLightId = (id) => typeof id === "string" && id.startsWith("minecraft:light_block");
function lightLevelOf(b) {
  try {
    const id = b.typeId;
    const m = /^minecraft:light_block_(\d+)$/.exec(id);
    if (m) return Number(m[1]);
    if (id === "minecraft:light_block") return Number(b.permutation.getState("block_light_level") ?? 15);
  } catch (_) {}
  return -1;
}

// ---------------------------------------------------------------------------------- ค่าแบตบนไอเทม
function fidOf(it) { try { const v = it.getDynamicProperty(DP_FID); return typeof v === "string" ? v : null; } catch (_) { return null; } }
function newFid() { return `${Date.now().toString(36)}${Math.floor(Math.random() * 1e9).toString(36)}`; }
function chargeOf(it) {
  const f = fidOf(it);
  if (f && mem.has(f)) return mem.get(f).charge;
  try { const v = it.getDynamicProperty(DP_CHARGE); if (typeof v === "number") return Math.max(0, Math.min(FULL, v)); } catch (_) {}
  return 0;
}
function isOn(it) { try { return it.getDynamicProperty(DP_ON) === true; } catch (_) { return false; } }
function fmtTime(t) { const s = Math.ceil(t / 20); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`; }
function batteryBar(frac) {
  const n = Math.round(frac * 10), col = frac <= LOW ? "§c" : frac <= 0.4 ? "§e" : "§a";
  return col + "■".repeat(n) + "§8" + "■".repeat(10 - n);
}
// เขียนค่าแบต/สถานะลงไอเทม (ไม่ setEquipment ให้ ผู้เรียกจัดการเอง)
function stamp(it, charge, on) {
  let f = fidOf(it);
  if (!f) { f = newFid(); try { it.setDynamicProperty(DP_FID, f); } catch (_) {} }
  charge = Math.max(0, Math.min(FULL, Math.round(charge)));
  try { it.setDynamicProperty(DP_CHARGE, charge); } catch (_) {}
  if (on !== undefined) { try { it.setDynamicProperty(DP_ON, !!on); } catch (_) {} }
  const frac = charge / FULL;
  try { const d = it.getComponent("minecraft:durability"); if (d) d.damage = Math.round((1 - frac) * DUR_SPAN); } catch (_) {}
  try {
    it.setLore([
      `§r§7แบต ${batteryBar(frac)} §7${charge > 0 ? fmtTime(charge) : "§cหมด"}`,
      `§r${isOn(it) ? "§eไฟเปิดอยู่" : "§8ไฟปิดอยู่"}`,
      "§r§8คลิกขวา: เปิด/ปิด",
      "§r§8ย่อ + คลิกขวา: เปลี่ยนแบต",
    ]);
  } catch (_) {}
  mem.set(f, { charge, saved: system.currentTick });
  return it;
}
function equip(p) { try { return p.getComponent("minecraft:equippable"); } catch (_) { return null; } }
function invOf(p) { try { return p.getComponent("minecraft:inventory").container; } catch (_) { return null; } }
// ไฟฉายที่ถืออยู่ (มือหลักก่อน แล้วมือรอง)
function heldFlash(p) {
  const eq = equip(p);
  if (!eq) return null;
  for (const slot of ["Mainhand", "Offhand"]) {
    try { const it = eq.getEquipment(slot); if (it && it.typeId === FLASH) return { it, slot, eq }; } catch (_) {}
  }
  return null;
}
// ไฟฉายเปิดอยู่และมีแบต (ใช้จากระบบซ่อนตัว: แสงทำให้ผีเห็นง่ายขึ้น)
export function flashOn(p) {
  const s = pst.get(p && p.id);
  return !!(s && s.lit);
}

// สถานะสำหรับหน้าเมนูสมุด: แบตของไฟฉายที่ถือ (หรือกระบอกที่แบตเยอะสุดในกระเป๋า) + จำนวนแบตสำรอง
export function flashStatus(p) {
  const out = { has: false, charge: 0, full: FULL, batteries: 0, on: false };
  try {
    const h = heldFlash(p);
    if (h) { out.has = true; out.charge = chargeOf(h.it); out.on = isOn(h.it); }
    const inv = invOf(p);
    if (inv) for (let i = 0; i < inv.size; i++) {
      const it = inv.getItem(i);
      if (!it) continue;
      if (it.typeId === BATT) out.batteries += it.amount;
      else if (it.typeId === FLASH && !h) { const c = chargeOf(it); if (!out.has || c > out.charge) { out.charge = c; out.has = true; } }
    }
  } catch (_) {}
  return out;
}
export const fmtCharge = (t) => fmtTime(t);

// คำสาป "กินแสง": ดูดแบตไฟฉายที่ถืออยู่
export function drainFlashlight(p, ticks) {
  const eq = equip(p);
  if (!eq) return;
  for (const slot of ["Mainhand", "Offhand"]) {
    try {
      const it = eq.getEquipment(slot);
      if (!it || it.typeId !== FLASH) continue;
      stamp(it, Math.max(0, chargeOf(it) - ticks));
      eq.setEquipment(slot, it);
    } catch (_) {}
  }
}

// ---------------------------------------------------------------------------------- เปิด/ปิด และเปลี่ยนแบต
function findBattery(inv) {
  if (!inv) return -1;
  for (let i = 0; i < inv.size; i++) { const it = inv.getItem(i); if (it && it.typeId === BATT) return i; }
  return -1;
}
function takeBattery(p) {
  if (isCreative(p)) return true;
  const inv = invOf(p), i = findBattery(inv);
  if (i < 0) return false;
  const it = inv.getItem(i);
  if (it.amount > 1) { it.amount -= 1; inv.setItem(i, it); } else inv.setItem(i, undefined);
  return true;
}
function insertInto(p, it, write) {
  const c = chargeOf(it);
  if (c >= FULL * 0.9) { actionbar(p, "§7แบตยังเต็มอยู่"); return false; }
  if (!takeBattery(p)) { actionbar(p, "§cไม่มีแบตเตอรี่ §8(คราฟ: เศษเหล็ก + เรดสโตน + ทองแดงแท่ง)"); sound(p.dimension, "ghost.flashlight.click", p.location, 0.7, 0.7, 8); return false; }
  stamp(it, FULL);
  write(it);
  sound(p.dimension, "ghost.flashlight.reload", p.location, 1, rand(0.95, 1.05), 10);
  actionbar(p, "§aเปลี่ยนแบตแล้ว §7(ส่องได้ 5:00)");
  return true;
}
function onUse(p, item) {
  if (!p || !item) return;
  const s = state(p), now = system.currentTick;
  if (now - (s.useAt ?? -99) < 6) return;      // คลิกค้าง = นับครั้งเดียว
  s.useAt = now;
  const eq = equip(p);
  if (!eq) return;
  if (item.typeId === FLASH) {
    const it = eq.getEquipment("Mainhand");
    if (!it || it.typeId !== FLASH) return;
    const write = (x) => { try { eq.setEquipment("Mainhand", x); } catch (_) {} };
    let sneak = false; try { sneak = p.isSneaking; } catch (_) {}
    if (sneak) { insertInto(p, it, write); return; }
    const c = chargeOf(it);
    if (!isOn(it) && c <= 0 && !isCreative(p)) {
      sound(p.dimension, "ghost.flashlight.click", p.location, 0.8, 0.7, 8);
      actionbar(p, "§cแบตหมด! §7ย่อ + คลิกขวา เพื่อเปลี่ยนแบต");
      return;
    }
    const on = !isOn(it);
    stamp(it, c, on);
    write(it);
    sound(p.dimension, on ? "ghost.flashlight.on" : "ghost.flashlight.off", p.location, 0.9, rand(0.95, 1.05), 8);
    actionbar(p, on ? `§eเปิดไฟฉาย §7(แบตเหลือ ${fmtTime(c)})` : "§7ปิดไฟฉาย");
    return;
  }
  if (item.typeId === BATT) {
    // ถือแบต: ใส่ให้ไฟฉายในมือรองก่อน ไม่งั้นกระบอกที่แบตน้อยที่สุดในกระเป๋า
    try {
      const off = eq.getEquipment("Offhand");
      if (off && off.typeId === FLASH) { insertInto(p, off, (x) => { try { eq.setEquipment("Offhand", x); } catch (_) {} }); return; }
    } catch (_) {}
    const inv = invOf(p);
    if (!inv) return;
    let best = -1, bc = Infinity;
    for (let i = 0; i < inv.size; i++) {
      const it = inv.getItem(i);
      if (it && it.typeId === FLASH) { const c = chargeOf(it); if (c < bc) { bc = c; best = i; } }
    }
    if (best < 0) { actionbar(p, "§7ไม่มีไฟฉายให้ใส่แบต"); return; }
    const it = inv.getItem(best);
    insertInto(p, it, (x) => { try { inv.setItem(best, x); } catch (_) {} });
  }
}

// ---------------------------------------------------------------------------------- แสง
function state(p) {
  let s = pst.get(p.id);
  if (!s) { s = { flickerUntil: 0, nextFlicker: 0, warnAt: 0, lastSave: system.currentTick, lit: false, ghostNear: false, ghostAt: -999 }; pst.set(p.id, s); }
  return s;
}
function addCell(want, dimId, x, y, z, lv) {
  const k = `${dimId}|${x}|${y}|${z}`;
  const cur = want.get(k);
  if (!cur || cur.lv < lv) want.set(k, { dim: dimId, x, y, z, lv });
}
function beamCells(p, want, dimm) {
  let head, v;
  try { head = p.getHeadLocation(); v = p.getViewDirection(); } catch (_) { return; }
  let hit = null;
  try { hit = p.getBlockFromViewDirection({ maxDistance: RANGE, includePassableBlocks: false, includeLiquidBlocks: true }); } catch (_) {}
  let end = RANGE;
  const dimId = p.dimension.id;
  if (hit && hit.block) {
    const b = hit.block.location;
    let own = false; try { own = isLightId(hit.block.typeId); } catch (_) {}
    const f = own ? [0, 0, 0] : FACE[hit.face] || [0, 0, 0];         // ชนแสงของเราเอง = ใช้ช่องเดิม (ไม่คืบเข้าหาตัว)
    const fx = b.x + f[0], fy = b.y + f[1], fz = b.z + f[2];
    const dx = b.x + 0.5 - head.x, dy = b.y + 0.5 - head.y, dz = b.z + 0.5 - head.z;
    end = Math.sqrt(dx * dx + dy * dy + dz * dz);
    addCell(want, dimId, fx, fy, fz, Math.round(Math.max(9, 15 - Math.max(0, end - 10) * 0.35) * dimm));
  } else {
    addCell(want, dimId, fl(head.x + v.x * RANGE * 0.8), fl(head.y + v.y * RANGE * 0.8), fl(head.z + v.z * RANGE * 0.8), Math.round(8 * dimm));
  }
  // ตามลำแสง (ห่างกันทุก 4 บล็อก แสงอ่อนลงเรื่อย ๆ)
  for (let d = 3, i = 0; d < end - 1.2 && i < 5; d += 4, i++) {
    addCell(want, dimId, fl(head.x + v.x * d), fl(head.y + v.y * d), fl(head.z + v.z * d), Math.round(Math.max(5, 10 - i) * dimm));
  }
}
function placeAll(want) {
  const dims = {};
  const dimOf = (id) => dims[id] || (dims[id] = (() => { try { return world.getDimension(id); } catch (_) { return null; } })());
  // ลบที่ไม่ต้องการแล้ว
  for (const [k, c] of placed) {
    const w = want.get(k);
    if (w && w.lv === c.lv) continue;
    if (!w) {
      const d = dimOf(c.dim), b = d && getBlock(d, c.x, c.y, c.z);
      try { if (b && isLightId(b.typeId) && AIR) b.setPermutation(AIR); } catch (_) {}
      placed.delete(k); lightsDirty = true;
    }
  }
  // วางใหม่ / ปรับระดับ (เฉพาะช่องอากาศ หรือช่องแสงของเราเอง)
  for (const [k, w] of want) {
    const c = placed.get(k);
    if (c && c.lv === w.lv) continue;
    const d = dimOf(w.dim), b = d && getBlock(d, w.x, w.y, w.z);
    if (!b) continue;
    let ok = false;
    try { ok = b.isAir || (c && isLightId(b.typeId)); } catch (_) {}
    if (!ok) continue;
    const perm = lightPerm(Math.max(1, Math.min(15, w.lv)));
    if (!perm) continue;
    try { b.setPermutation(perm); placed.set(k, w); lightsDirty = true; } catch (_) {}
  }
}
function saveLights() {
  if (!lightsDirty) return;
  lightsDirty = false;
  try { world.setDynamicProperty(LIGHTS_DP, JSON.stringify([...placed.values()].map((c) => [c.dim, c.x, c.y, c.z]))); } catch (_) {}
}
// โหลดโลกใหม่: ลบแสงค้างจากรอบก่อน (เช่นเซิร์ฟดับตอนเปิดไฟฉาย)
function cleanupOld() {
  let raw;
  try { raw = world.getDynamicProperty(LIGHTS_DP); } catch (_) { return false; }
  if (typeof raw === "string" && raw.length) {
    try {
      for (const [dimId, x, y, z] of JSON.parse(raw)) {
        try { const d = world.getDimension(dimId), b = getBlock(d, x, y, z); if (b && isLightId(b.typeId) && AIR) b.setPermutation(AIR); } catch (_) {}
      }
    } catch (_) {}
  }
  try { world.setDynamicProperty(LIGHTS_DP, "[]"); } catch (_) {}
  return true;
}
function ghostsNear(p) {
  try {
    for (const g of p.dimension.getEntities({ families: ["ghost"], location: p.location, maxDistance: 20 })) {
      let st = 0; try { st = g.getProperty("ghost:state") ?? 0; } catch (_) {}
      if (st !== 0 || Math.hypot(g.location.x - p.location.x, g.location.z - p.location.z) < 12) return true;
    }
  } catch (_) {}
  return false;
}

function tick(now) {
  if (!cleaned) { if (now < 20) return; cleaned = cleanupOld(); if (!cleaned) return; }
  const want = new Map();
  for (const p of world.getAllPlayers()) {
    try {
      const s = state(p);
      const h = heldFlash(p);
      s.lit = false;
      if (!h || !isOn(h.it)) continue;
      const it = h.it, f = fidOf(it) || "";
      let charge = chargeOf(it);
      const creative = isCreative(p);
      if (charge <= 0 && !creative) {
        if (now - (s.deadAt ?? -999) > 40) {
          s.deadAt = now;
          stamp(it, 0, false);
          try { h.eq.setEquipment(h.slot, it); } catch (_) {}
          sound(p.dimension, "ghost.flashlight.dead", p.location, 0.9, 1, 8);
          actionbar(p, "§c§lแบตหมด! §r§7ย่อ + คลิกขวา เพื่อเปลี่ยนแบต");
        }
        continue;
      }
      if (!creative) {
        charge -= STEP;
        if (f) mem.set(f, { charge, saved: mem.get(f)?.saved ?? now });
        else { stamp(it, charge); try { h.eq.setEquipment(h.slot, it); } catch (_) {} }
      }
      if (now - s.lastSave >= SAVE_EVERY) {
        s.lastSave = now;
        stamp(it, charge);
        try { h.eq.setEquipment(h.slot, it); } catch (_) {}
      }
      const frac = charge / FULL;
      if (frac <= LOW && now - s.warnAt > 600 && !creative) { s.warnAt = now; actionbar(p, "§eแบตใกล้หมด..."); }
      // ไฟกะพริบ: แบตใกล้หมด / ผีอยู่ใกล้
      if (now % 10 === 0) s.ghostNear = ghostsNear(p);
      if (now >= s.nextFlicker) {
        const scared = s.ghostNear, low = frac <= LOW && !creative;
        if (scared || low) {
          s.flickerUntil = now + (scared ? 2 + Math.floor(Math.random() * 10) : 2 + Math.floor(Math.random() * 5));
          s.nextFlicker = now + (scared ? 20 + Math.random() * 50 : 40 + Math.random() * 120);
          sound(p.dimension, "ghost.flashlight.flicker", p.location, scared ? 0.8 : 0.5, rand(0.9, 1.15), 10);
        } else s.nextFlicker = now + 40;
      }
      s.lit = true;
      if (now < s.flickerUntil) continue;                        // ดับวูบ
      const dimm = s.ghostNear && Math.random() < 0.25 ? 0.6 : 1;
      beamCells(p, want, dimm);
    } catch (_) {}
  }
  placeAll(want);
  if (now % 20 === 0) saveLights();
}

// ไฟฉายใหม่ (เพิ่งคราฟ) ยังไม่มีข้อมูลแบต: ตั้งเป็นแบตว่างให้แถบแสดงถูก
function scanNew() {
  for (const p of world.getAllPlayers()) {
    const inv = invOf(p);
    if (!inv) continue;
    for (let i = 0; i < inv.size; i++) {
      try {
        const it = inv.getItem(i);
        if (it && it.typeId === FLASH && !fidOf(it)) inv.setItem(i, stamp(it, 0, false));
      } catch (_) {}
    }
  }
}

export function initFlashlight() {
  sub("afterEvents", "itemUse", (ev) => { try { onUse(ev.source, ev.itemStack); } catch (_) {} });
  system.runInterval(() => { try { tick(system.currentTick); } catch (_) {} }, STEP);
  system.runInterval(() => { try { scanNew(); } catch (_) {} }, 40);
}
