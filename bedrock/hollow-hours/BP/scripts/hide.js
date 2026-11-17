import { flashOn } from "./flashlight.js";
import { world, system, ItemStack } from "@minecraft/server";
import {
  fl, rand, pick, clamp, valid, dist, getBlock, blockId, stateOf, passable, standCell, sound, particle, shake,
  playersNear, actionbar, isCreative, sub,
} from "./util.js";
import { SIZE, PEEK_REACH, INSPECT_REACH, typeNum } from "./body.js";

// =====================================================================
//  ระบบแอบ (v1.3)
//  1) หลบสายตา : ผีต้อง "เห็น" (ไม่มีอะไรบัง) หรือ "ได้ยิน" ถึงจะตามต่อได้
//                 คลาดกัน 3 วินาที -> เดินไปจุดสุดท้ายที่เห็น ดมหา ค้นที่ซ่อนแถวนั้น ไม่เจอก็หายไป
//  2) ตู้ซ่อนตัว : คราฟได้ เข้าไปนั่งในตู้ มองออกทางช่องบานเกล็ด
//  3) ใต้เตียง   : ย่อ + คลิกขวาที่เตียง -> มุดไปนอนใต้เตียง (กล้องต่ำติดพื้น เห็นเท้าผีเดินผ่าน)
//  4) อย่าขยับ   : ตอนแอบแล้วผีอยู่ใกล้ หันกล้อง/ขยับ = หายใจแรงขึ้น จนผีได้ยิน
// =====================================================================
let api = null;
export const HIDDEN_TAG = "ghost_hidden";
const CLOSET = "ghost:closet", SEAT = "ghost:hide_seat", CLOSET_ITEM = "ghost:closet_item";
// ขนาดตัว/ระยะเอื้อมของผีแต่ละตน -> body.js (v1.4 ผีแต่ละตัวสูงไม่เท่ากัน)
const LOSE_TICKS = 60;          // ไม่เห็น + ไม่ได้ยิน 3 วินาที = คลาดกัน
const BREATH_WARN = 55, BREATH_MAX = 100;
const P = new Map();            // player id -> สถานะผู้เล่น
const IMP = [];                 // เสียงดังชั่วขณะ (เปิดประตู ทุบบล็อก ...)
const searching = new Map();    // ghost id -> entity ที่กำลังเดินหา
let seatSeq = 0;
const N4 = [[1, 0], [-1, 0], [0, 1], [0, -1]];
const wrap = (a) => ((((a + 180) % 360) + 360) % 360) - 180;
const yawOfDir = (dx, dz) => (Math.atan2(-dx, dz) * 180) / Math.PI;
const dirOfYaw = (yaw) => ({ x: -Math.sin((yaw * Math.PI) / 180), z: Math.cos((yaw * Math.PI) / 180) });
const LOW_STATES = new Set([4, 6, 7, 9, 11]);
function trigger(g, ev) { try { g.triggerEvent(ev); } catch (_) {} }
function cmd(e, c) { try { e.runCommand(c); return true; } catch (_) { return false; } }
function stateOfGhost(g) { try { return g.getProperty("ghost:state") ?? 0; } catch (_) { return 0; } }
function face(g, loc, target, S) {
  try { g.teleport(loc, { facingLocation: target }); } catch (_) { try { g.teleport(loc); } catch (__) {} }
  if (S) S.faceYaw = yawOfDir(target.x - loc.x, target.z - loc.z);
}
function ps(p) {
  let s = P.get(p.id);
  if (!s) { s = { last: null, ground: true, vy: 0, noise: 0, breath: 0, hide: null, beatAt: 0, hudAt: 0, breathAt: 0, gaspAt: -999, interact: null }; P.set(p.id, s); }
  return s;
}
export function isHidden(p) { const s = P.get(p && p.id); return !!(s && s.hide); }
export function hideOf(p) { const s = P.get(p && p.id); return s ? s.hide : null; }

// ---------------------------------------------------------------------------------- เสียง (สิ่งที่ผีได้ยิน)
// v1.7: เสียงจากระบบอื่น (เช่น วิ่งสุดแรง) ให้ผีได้ยิน
export function makeNoise(dim, pos, r, pid) { impulse(dim, pos, r, pid); }
function impulse(dim, pos, r, pid) {
  IMP.push({ dim: dim.id, x: pos.x, y: pos.y, z: pos.z, r, pid, t: system.currentTick });
  if (IMP.length > 48) IMP.shift();
}
function playerTick(now) {
  for (const p of world.getAllPlayers()) {
    try {
      const s = ps(p);
      if (s.hide) { hiddenTick(p, s, now); continue; }
      const l = p.location;
      let sp = 0;
      if (s.last && s.last.dim === p.dimension.id) sp = Math.hypot(l.x - s.last.x, l.z - s.last.z);
      let vy = 0, onG = true, sneak = false, sprint = false;
      try { vy = p.getVelocity().y; } catch (_) {}
      try { onG = p.isOnGround; } catch (_) {}
      try { sneak = p.isSneaking; } catch (_) {}
      try { sprint = p.isSprinting; } catch (_) {}
      let r = 0;
      if (sp > 0.6) r = 0;                                  // วาร์ป/เทเลพอร์ต
      else if (sp > 0.025) r = sneak ? 2.5 : sprint || sp > 0.2 ? 16 : sp > 0.09 ? 8 : 5;
      if (s.ground && !onG && vy > 0.25) r = Math.max(r, 9);              // กระโดด
      if (!s.ground && onG && s.vy < -0.55) r = Math.max(r, 12);          // ตกกระแทกพื้น
      s.noise = r; s.ground = onG; s.vy = vy;
      s.last = { x: l.x, y: l.y, z: l.z, dim: p.dimension.id };
    } catch (_) {}
  }
}

// ---------------------------------------------------------------------------------- การมองเห็น
const SEE_THROUGH = /(glass|pane|iron_bars|fence(?!_gate)|_wall$|chain$|lantern|candle|torch|sign|banner|button|lever|rail|carpet|flower_pot|ladder|scaffolding|cobweb|^web$|trapdoor|vine|sapling|grass|fern|flower|bush|kelp|seagrass|coral|end_rod|lightning_rod|dripstone|_head$|skull|campfire|bell|chain)/;
function blocksSight(b) {
  if (!b) return false;
  try { if (b.isAir || b.isLiquid) return false; } catch (_) { return false; }
  if (passable(b)) return false;
  return !SEE_THROUGH.test(blockId(b).replace("minecraft:", ""));
}
// ไล่ช่องบล็อกตามเส้นตรง a -> b (ไม่นับช่องเริ่มต้นและช่องปลายทาง)
export function los(dim, a, b) {
  let x = fl(a.x), y = fl(a.y), z = fl(a.z);
  const tx = fl(b.x), ty = fl(b.y), tz = fl(b.z);
  const dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
  const sx = Math.sign(dx), sy = Math.sign(dy), sz = Math.sign(dz);
  const inv = (d) => (d === 0 ? Infinity : Math.abs(1 / d));
  const dX = inv(dx), dY = inv(dy), dZ = inv(dz);
  let mX = dx === 0 ? Infinity : (sx > 0 ? x + 1 - a.x : a.x - x) * dX;
  let mY = dy === 0 ? Infinity : (sy > 0 ? y + 1 - a.y : a.y - y) * dY;
  let mZ = dz === 0 ? Infinity : (sz > 0 ? z + 1 - a.z : a.z - z) * dZ;
  for (let i = 0; i < 260; i++) {
    if (x === tx && y === ty && z === tz) return true;
    if (mX <= mY && mX <= mZ) { if (mX > 1) return true; x += sx; mX += dX; }
    else if (mY <= mZ) { if (mY > 1) return true; y += sy; mY += dY; }
    else { if (mZ > 1) return true; z += sz; mZ += dZ; }
    if (x === tx && y === ty && z === tz) return true;
    if (blocksSight(getBlock(dim, x, y, z))) return false;
  }
  return true;
}
function eyeOf(g) {
  const h = (SIZE[typeNum(g)] || [1, 4.4])[1];
  const low = LOW_STATES.has(stateOfGhost(g));
  return { x: g.location.x, y: g.location.y + (low ? 0.9 : Math.min(h * 0.8, 3.4)), z: g.location.z };
}
export function canSee(g, p, maxD = 42) {
  try {
    const E = eyeOf(g), L = p.location;
    if (Math.hypot(E.x - L.x, E.y - L.y, E.z - L.z) > maxD) return false;
    return los(g.dimension, E, { x: L.x, y: L.y + 1.5, z: L.z }) || los(g.dimension, E, { x: L.x, y: L.y + 0.6, z: L.z });
  } catch (_) { return false; }
}
function inCone(yaw, from, to, deg) {
  const f = dirOfYaw(yaw), dx = to.x - from.x, dz = to.z - from.z, d = Math.hypot(dx, dz) || 1;
  return (f.x * dx + f.z * dz) / d >= Math.cos((deg * Math.PI) / 180);
}
function hears(g, p, s, now) {
  const G = g.location, L = p.location;
  const d = dist(G, L);
  if (s.noise > 0 && d <= s.noise) {
    if (d <= s.noise * 0.6 || los(g.dimension, eyeOf(g), { x: L.x, y: L.y + 1, z: L.z })) return true;   // ผนังกั้น = เสียงเบาลง
  }
  for (const im of IMP) {
    if (im.pid !== p.id || now - im.t > 12 || im.dim !== g.dimension.id) continue;
    if (dist(G, im) <= im.r) return true;
  }
  return false;
}
// ผีตัวนี้รับรู้ผู้เล่นคนไหนอยู่ไหม (เห็น / ได้ยิน / ชิดตัว) — คนที่ซ่อนอยู่ในตู้/ใต้เตียงไม่นับ
export function perceive(g, m, now, searchingMode) {
  let best = null, bestD = 1e9, how = null;
  let list = [];
  try { list = g.dimension.getPlayers({ location: g.location, maxDistance: 64 }); } catch (_) {}
  for (const p of list) {
    const s = ps(p);
    if (s.hide) continue;
    const d = dist(g.location, p.location);
    let h = null;
    let sneak = false; try { sneak = p.isSneaking; } catch (_) {}
    if (d <= 2.3) h = "touch";
    else {
      const lit = flashOn(p);                               // แสงไฟฉาย: เห็นได้ไกลขึ้น และเห็นแสงได้รอบทิศ
      let marked = false; try { marked = p.hasTag("ghost_marked"); } catch (_) {}   // คำสาปแสงวิญญาณ: เห็นทะลุกำแพง
      const range = searchingMode ? (lit ? 34 : sneak ? 9 : 22) : (lit ? 48 : sneak ? 24 : 42);
      const coneOk = !searchingMode || d <= 4 || lit || inCone(m.search ? m.search.faceYaw : (g.getRotation ? g.getRotation().y : 0), g.location, p.location, 75);
      if (d <= range && coneOk && canSee(g, p, range)) h = "see";
      else if (marked && d <= 32) h = "see";
      else if (hears(g, p, s, now)) h = "hear";
    }
    if (h && d < bestD) { best = p; bestD = d; how = h; }
  }
  if (best) {
    m.lkp = { x: best.location.x, y: best.location.y, z: best.location.z };
    m.lkpAt = now; m.lastDetect = now; m.lastHow = how;
  }
  return best ? { p: best, d: bestD, how } : null;
}
// เรียกจาก main ตอนผีไล่ (state 2/7): false = คลาดกันแล้ว (เริ่มเดินหา)
export function chaseCheck(g, m, now) {
  if (m.lastDetect === undefined || now - m.lastDetect > 400) m.lastDetect = now;
  if (perceive(g, m, now, false)) return true;
  if (now - m.lastDetect <= LOSE_TICKS) return true;
  for (const p of playersNear(g.dimension, g.location, 40)) {
    if (!isHidden(p)) actionbar(p, pick(["§8...มันมองไม่เห็นคุณแล้ว... อย่าส่งเสียง", "§8...เงียบไว้... มันกำลังฟัง", "§8...มันคลาดกับคุณ..."]));
  }
  startSearch(g, m, now, {});
  return false;
}

// ---------------------------------------------------------------------------------- ที่ซ่อน
function closetSeat(e) {
  const yaw = e.getRotation().y, f = dirOfYaw(yaw), l = e.location;
  return { x: l.x - f.x * 0.18, y: l.y + 0.05, z: l.z - f.z * 0.18 };
}
function closetSpot(e) {
  const yaw = e.getRotation().y, f = dirOfYaw(yaw), l = e.location;
  return { kind: "closet", key: "c:" + e.id, ent: e, pos: { x: l.x, y: l.y, z: l.z }, front: f, lookAt: { x: l.x + f.x * 0.4, y: l.y + 1.45, z: l.z + f.z * 0.4 } };
}
const isBed = (b) => blockId(b) === "minecraft:bed";
function bedCells(dim, x, y, z) {
  const A = { x, y, z }, ba = getBlock(dim, x, y, z);
  const head = stateOf(ba, "head_piece_bit");
  for (const [dx, dz] of N4) {
    const b = getBlock(dim, x + dx, y, z + dz);
    if (isBed(b) && stateOf(b, "head_piece_bit") !== head) return [A, { x: x + dx, y, z: z + dz }];
  }
  for (const [dx, dz] of N4) { if (isBed(getBlock(dim, x + dx, y, z + dz))) return [A, { x: x + dx, y, z: z + dz }]; }
  return [A, A];
}
function bedSpot(dim, x, y, z) {
  const [A, B] = bedCells(dim, x, y, z);
  const k0 = A.x < B.x || (A.x === B.x && A.z <= B.z) ? A : B;
  const axis = A.x !== B.x ? "x" : "z";
  const c = { x: (A.x + B.x) / 2 + 0.5, y, z: (A.z + B.z) / 2 + 0.5 };
  return { kind: "bed", key: `b:${k0.x},${k0.y},${k0.z}`, cells: [A, B], axis, pos: c, lookAt: { x: c.x, y: y + 0.4, z: c.z } };
}
function findSpots(dim, c, R = 9) {
  const out = [], seen = new Set();
  try { for (const e of dim.getEntities({ type: CLOSET, location: c, maxDistance: R + 3 })) out.push(closetSpot(e)); } catch (_) {}
  const cx = fl(c.x), cy = fl(c.y), cz = fl(c.z);
  for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) for (let dy = -2; dy <= 2; dy++) {
    const b = getBlock(dim, cx + dx, cy + dy, cz + dz);
    if (!isBed(b)) continue;
    const sp = bedSpot(dim, cx + dx, cy + dy, cz + dz);
    if (seen.has(sp.key)) continue;
    seen.add(sp.key); out.push(sp);
  }
  out.sort((a, b) => dist(a.pos, c) - dist(b.pos, c));
  return out.slice(0, 6);
}
function occupantOf(key) {
  for (const p of world.getAllPlayers()) { const s = P.get(p.id); if (s && s.hide && s.hide.key === key) return p; }
  return null;
}
function setDoor(e, v) { try { if (valid(e)) e.setProperty("ghost:door", v); } catch (_) {} }
function doorPulse(e, v, ticks) { setDoor(e, v); system.runTimeout(() => setDoor(e, 0), ticks); }

// ---------------------------------------------------------------------------------- เข้า/ออกที่ซ่อน
function enterHide(p, h) {
  const s = ps(p), now = system.currentTick;
  s.hide = Object.assign({ t0: now, lastRot: null }, h);
  s.breath = 0;
  try { p.addTag(HIDDEN_TAG); } catch (_) {}
  try { p.addEffect("resistance", 40, { amplifier: 4, showParticles: false }); } catch (_) {}
  const dim = p.dimension, spot = h.spotPos;
  // ผีที่ "เห็น" ตอนเรามุดเข้าไป จะจำได้ว่าเราซ่อนที่ไหน
  try {
    for (const g of dim.getEntities({ families: ["ghost"], location: p.location, maxDistance: 40 })) {
      const m = api.ensureMeta(g, now);
      const st = stateOfGhost(g);
      if (st === 5 || api.leaving.has(g.id)) continue;
      if (canSee(g, p, 40)) {
        m.sawHide = { key: h.key, t: now, pid: p.id };
        // v1.9: ผีที่เห็นตอนเราแอบ และยังอยู่ใกล้ (<= 18 บล็อก) จะได้ยินเสียงมุดเข้าที่ซ่อน -> เดินตรงมาแล้วลากออกไปทุกครั้ง
        //        (ถ้ามันไม่เห็นตอนเราแอบ = ใช้ระบบเดิมทั้งหมด)
        if (dist(g.location, spot) <= 18) {
          let heard = false;
          if (m.search) {
            m.search.alert = { key: h.key, level: "gasp", t: now, used: false };
            m.search.sawKey = h.key; m.search.sawDone = false; heard = true;
          } else if (!api.isScripted(m) && st !== 1 && !(st === 0 && !m.stalking)) {
            startSearch(g, m, now, { lkp: spot, alertKey: h.key, level: "gasp" }); heard = true;
          }
          if (heard) {
            sound(dim, "ghost.growl", g.location, 1.6, rand(0.6, 0.8), 30);
            actionbar(p, "§4§lมันได้ยินเสียงคุณ!");
          }
        }
      }
    }
  } catch (_) {}
  alertGhosts(dim, spot, h.kind === "closet" ? 6 : 4, h.key, "noise");    // เสียงประตูตู้ / มุดเข้าใต้เตียง
  actionbar(p, h.kind === "closet" ? "§7ซ่อนในตู้... §8อยู่นิ่ง ๆ (กดย่อเพื่อออก)" : "§7มุดอยู่ใต้เตียง... §8อยู่นิ่ง ๆ (กดย่อเพื่อออก)");
}
function exitHide(p, o = {}) {
  const s = P.get(p.id);
  if (!s || !s.hide) return;
  const h = s.hide;
  s.hide = null; s.breath = 0;
  try { p.removeTag(HIDDEN_TAG); } catch (_) {}
  if (o.forced) {
    cmd(p, "ride @s stop_riding");
    // ถูกลากออกมา: เลิกอมตะทันที
    let ok = false;
    try { ok = p.removeEffect("resistance"); } catch (_) {}
    if (!ok) cmd(p, "effect @s resistance 0");
  }
  if (h.kind === "bed") {
    cmd(p, "camera @s fade time 0.1 0.15 0.3 color 0 0 0");
    cmd(p, "camera @s clear");
    try { if (valid(h.ent)) h.ent.remove(); } catch (_) {}
  } else if (!o.forced) {
    doorPulse(h.ent, 1, 12);
    sound(p.dimension, "ghost.closet.open", h.spotPos, 0.9, rand(0.95, 1.1), 16);
    system.runTimeout(() => { try { sound(p.dimension, "ghost.closet.close", h.spotPos, 0.8, 1, 16); } catch (_) {} }, 12);
  }
  // ออกมายืนหน้าตู้ / ข้างเตียง
  if (h.exitPos && !o.noMove) {
    const E = h.exitPos;
    system.runTimeout(() => {
      try {
        if (!valid(p)) return;
        const l = p.location;
        if (o.forced || h.kind === "bed" || Math.hypot(l.x - h.seat.x, l.z - h.seat.z) < 0.9) p.teleport({ x: E.x, y: E.y, z: E.z }, { facingLocation: o.faceTo || { x: E.x + (E.x - h.spotPos.x), y: E.y + 1.6, z: E.z + (E.z - h.spotPos.z) } });
      } catch (_) {}
    }, o.forced ? 1 : 2);
  }
  if (!o.quiet) alertGhosts(p.dimension, h.spotPos, 5, h.key, "noise");
}
function exitPosFrom(dim, base, dir, y) {
  for (const k of [1.0, 1.3, 0.75, 1.7]) {
    const x = base.x + dir.x * k, z = base.z + dir.z * k;
    const c = standCell(dim, x, z, fl(y) + 1, 3, 2);
    if (c) return { x: fl(x) + 0.5, y: c.y, z: fl(z) + 0.5 };
  }
  return { x: base.x + dir.x * 1.0, y, z: base.z + dir.z * 1.0 };
}
// ----- ตู้: ผู้เล่นคลิกตู้ (ขึ้นนั่งแบบ rideable ของเกมเอง) -> ตรวจว่านั่งในตู้จริงไหม
function checkClosetEntry(p, e) {
  try {
    if (!valid(p) || !valid(e) || isHidden(p)) return false;
    const seat = closetSeat(e), l = p.location;
    if (Math.hypot(l.x - seat.x, l.z - seat.z) > 0.42 || Math.abs(l.y - seat.y) > 1.6) return false;
    const sp = closetSpot(e);
    enterHide(p, {
      kind: "closet", key: sp.key, ent: e, seat, spotPos: { x: e.location.x, y: e.location.y + 0.5, z: e.location.z },
      baseYaw: e.getRotation().y, front: sp.front, exitPos: exitPosFrom(e.dimension, e.location, sp.front, e.location.y),
    });
    doorPulse(e, 1, 10);
    sound(e.dimension, "ghost.closet.open", e.location, 0.8, rand(0.95, 1.1), 16);
    system.runTimeout(() => { try { sound(e.dimension, "ghost.closet.close", e.location, 0.7, 1, 16); } catch (_) {} }, 10);
    return true;
  } catch (_) { return false; }
}
// ----- เตียง: ย่อ + คลิกขวา
function hideUnderBed(p, block) {
  const s = ps(p);
  if (s.hide) return;
  const dim = block.dimension, b = block.location;
  const sp = bedSpot(dim, b.x, b.y, b.z);
  const A = { x: b.x, y: b.y, z: b.z };
  const c = { x: A.x + 0.5, z: A.z + 0.5 };
  // หันหน้าออกด้านข้างเตียง (ฝั่งที่ผู้เล่นยืนอยู่)
  let S = sp.axis === "x" ? { x: 0, z: p.location.z >= c.z ? 1 : -1 } : { x: p.location.x >= c.x ? 1 : -1, z: 0 };
  const free = (d) => passable(getBlock(dim, A.x + d.x, A.y, A.z + d.z));
  if (!free(S) && free({ x: -S.x, z: -S.z })) S = { x: -S.x, z: -S.z };
  const yaw = yawOfDir(S.x, S.z);
  const cam = { x: c.x + S.x * 0.32, y: A.y + 0.09, z: c.z + S.z * 0.32 };
  const tag = `ghs${++seatSeq}_${system.currentTick % 100000}`;
  let seat;
  try { seat = dim.spawnEntity(SEAT, { x: c.x, y: A.y + 0.6, z: c.z }); } catch (_) { return; }
  try { seat.teleport({ x: c.x, y: A.y + 0.6, z: c.z }, { rotation: { x: 0, y: yaw } }); } catch (_) {}
  try { seat.addTag(tag); } catch (_) {}
  cmd(p, "camera @s fade time 0.15 0.25 0.35 color 0 0 0");
  system.runTimeout(() => {
    try {
      if (!valid(p) || !valid(seat)) return;
      cmd(p, `ride @s start_riding @e[type=${SEAT},tag=${tag},c=1] teleport_rider`);
      system.runTimeout(() => {
        try {
          if (!valid(p) || !valid(seat)) return;
          const l = p.location, sl = seat.location;
          if (Math.hypot(l.x - sl.x, l.z - sl.z) > 0.6) { try { seat.remove(); } catch (_) {} cmd(p, "camera @s clear"); return; }
          enterHide(p, {
            kind: "bed", key: sp.key, ent: seat, seat: { x: sl.x, y: sl.y, z: sl.z }, cam, side: S, cell: A, baseYaw: yaw,
            spotPos: { x: c.x, y: A.y + 0.3, z: c.z }, exitPos: exitPosFrom(dim, { x: c.x, z: c.z }, S, A.y),
            camYaw: yaw, camPitch: 6,
          });
          cmd(p, `camera @s set minecraft:free ease 0.3 in_out_sine pos ${cam.x.toFixed(3)} ${cam.y.toFixed(3)} ${cam.z.toFixed(3)} rot 6 ${yaw.toFixed(1)}`);
          sound(dim, "dig.cloth", cam, 0.7, 0.8, 10);
        } catch (_) {}
      }, 2);
    } catch (_) {}
  }, 3);
}

// ---------------------------------------------------------------------------------- ระหว่างซ่อน: ลมหายใจ / หัวใจเต้น / กล้องใต้เตียง
function nearestGhost(dim, loc, R) {
  let best = null, bd = R;
  try {
    for (const g of dim.getEntities({ families: ["ghost"], location: loc, maxDistance: R })) {
      if (api.leaving.has(g.id)) continue;
      const d = dist(g.location, loc);
      if (d < bd) { bd = d; best = g; }
    }
  } catch (_) {}
  return { g: best, d: bd };
}
function alertGhosts(dim, pos, R, key, level) {
  const now = system.currentTick;
  try {
    for (const g of dim.getEntities({ families: ["ghost"], location: pos, maxDistance: R })) {
      if (api.leaving.has(g.id)) continue;
      const m = api.ensureMeta(g, now);
      const st = stateOfGhost(g);
      if (m.search) { m.search.alert = { key, level, t: now, used: false }; continue; }
      if (api.isScripted(m) || st === 1 || st === 5) continue;
      if (st === 0 && !m.stalking) continue;
      if (level === "gasp" || level === "breath" || st === 2 || st === 7 || st === 0) startSearch(g, m, now, { lkp: pos, alertKey: key, level });
    }
  } catch (_) {}
}
function hiddenTick(p, s, now) {
  const h = s.hide, dim = p.dimension;
  if (!valid(h.ent)) return exitHide(p, { quiet: true, noMove: h.kind !== "bed" });
  const l = p.location;
  if (now - h.t0 > 5) {
    const ref = h.kind === "closet" ? closetSeat(h.ent) : h.ent.location;
    if (Math.hypot(l.x - ref.x, l.z - ref.z) > 0.75 || Math.abs(l.y - ref.y) > 2.2) return exitHide(p, {});
  }
  if (now % 10 === 0) {
    try { p.addEffect("resistance", 40, { amplifier: 4, showParticles: false }); } catch (_) {}
    if (h.kind === "bed" && !isBed(getBlock(dim, h.cell.x, h.cell.y, h.cell.z))) return exitHide(p, {});   // เตียงหายไป
  }
  // ---- มุมกล้อง (ใต้เตียง: กล้องอิสระติดพื้น หมุนตามหัวผู้เล่นได้จำกัด)
  let r = { x: 0, y: 0 };
  try { r = p.getRotation(); } catch (_) {}
  if (h.kind === "bed") {
    const yaw = h.baseYaw + clamp(wrap(r.y - h.baseYaw), -50, 50), pitch = clamp(r.x, -10, 28);
    if (Math.abs(wrap(yaw - h.camYaw)) + Math.abs(pitch - h.camPitch) > 0.6 || now % 40 === 0) {
      h.camYaw = yaw; h.camPitch = pitch;
      cmd(p, `camera @s set minecraft:free ease 0.1 linear pos ${h.cam.x.toFixed(3)} ${h.cam.y.toFixed(3)} ${h.cam.z.toFixed(3)} rot ${pitch.toFixed(1)} ${yaw.toFixed(1)}`);
    }
  }
  // ---- ลมหายใจ: ขยับกล้องตอนผีอยู่ใกล้ = หายใจแรงขึ้น
  let ang = 0;
  if (h.lastRot && now - h.t0 > 12) ang = Math.abs(wrap(r.y - h.lastRot.y)) + Math.abs(r.x - h.lastRot.x);
  h.lastRot = { x: r.x, y: r.y };
  const ng = nearestGhost(dim, h.spotPos, 24), gd = ng.d;
  // ขยับช้า ๆ ทีละนิดยังพอได้ / หันเร็ว ๆ หรือหันไปมานาน ๆ = หายใจแรงจนผีได้ยิน
  if (gd < 8) { s.breath += ang > 1.5 ? Math.min(6, (ang - 1.5) * 0.9) : -1.0; }
  else s.breath -= 2.5;
  s.breath = clamp(s.breath, 0, BREATH_MAX);
  if (s.breath >= BREATH_MAX) {
    s.breath = 40; s.gaspAt = now;
    sound(dim, "ghost.gasp", h.spotPos, 1.4, rand(0.95, 1.1), 18);
    actionbar(p, "§c§l...เฮือก!");
    alertGhosts(dim, h.spotPos, 11, h.key, "gasp");
  } else if (s.breath >= BREATH_WARN && now - s.breathAt > 24) {
    s.breathAt = now;
    sound(dim, "ghost.breath_hold", h.spotPos, 0.9, rand(0.95, 1.1), 10);
    alertGhosts(dim, h.spotPos, 6, h.key, "breath");
  }
  // ---- เปิดไฟฉายทิ้งไว้ในที่ซ่อน: แสงรั่วออกไป ผีใกล้ ๆ สังเกตเห็น
  if (flashOn(p) && gd < 12 && now - (s.lightAt || -99) > 30) {
    s.lightAt = now;
    actionbar(p, "§c§lปิดไฟฉาย! §r§7แสงรั่วออกไปข้างนอก");
    alertGhosts(dim, h.spotPos, 12, h.key, "breath");
  }
  // ---- หัวใจเต้น (ได้ยินคนเดียว) + แถบลมหายใจ
  if (gd < 18 && now >= s.beatAt) {
    s.beatAt = now + Math.floor(clamp(gd * 3, 9, 40));
    cmd(p, `playsound mob.warden.heartbeat @s ~ ~ ~ ${clamp(1.6 - gd / 14, 0.35, 1.6).toFixed(2)} 1`);
  }
  if (now >= s.hudAt) {
    s.hudAt = now + 4;
    if (gd < 16) {
      const n = Math.round(s.breath / 10);
      const bar = (s.breath >= BREATH_WARN ? "§c" : "§7") + "▮".repeat(n) + "§8" + "▮".repeat(10 - n);
      actionbar(p, `${gd < 6 ? "§c§lอย่าขยับ  " : "§7เงียบไว้...  "}§r§7ลมหายใจ ${bar}`);
    } else if ((now - h.t0) % 80 < 4) actionbar(p, "§8ซ่อนอยู่... (กดย่อเพื่อออก)");
  }
}

// ---------------------------------------------------------------------------------- ผีเดินหา
export function isSearching(m) { return !!(m && m.search && m.ix && m.ix.mode === "search"); }
function setPose(g, S, crawl) {
  S.crawl = crawl;
  trigger(g, crawl ? "ghost:crawl_search" : "ghost:search");
}
export function startSearch(g, m, now, o = {}) {
  const ix = api.ixOf(m), dim = g.dimension;
  let lkp = o.lkp || (m.lkp && now - (m.lkpAt || -1e9) < 900 ? m.lkp : null);
  if (!lkp && o.near) {
    // รู้แค่คร่าว ๆ ว่าอยู่แถวนี้: สุ่มจุดที่เดินถึงได้จากตัวผู้เล่น (ห้องเดียวกัน) ห่าง 2-5 บล็อก
    const area = reachable(dim, o.near, 6, true, 260).filter((c) => { const d = Math.hypot(c.x + 0.5 - o.near.x, c.z + 0.5 - o.near.z); return d >= 2 && d <= 5.5; });
    if (area.length) { const c = area[Math.floor(Math.random() * area.length)]; lkp = { x: c.x + 0.5, y: c.y, z: c.z + 0.5 }; }
    else lkp = { x: o.near.x, y: o.near.y, z: o.near.z };
  }
  if (!lkp) lkp = { x: g.location.x, y: g.location.y, z: g.location.z };
  const sawKey = m.sawHide && now - m.sawHide.t < 900 ? m.sawHide.key : null;
  try { api.release(g, m, now); } catch (_) {}
  m.search = {
    lkp: { x: lkp.x, y: lkp.y, z: lkp.z }, t0: now, until: now + Math.floor(rand(620, 900)), phase: "goto", goal: null, after: "look",
    stall: 0, spots: findSpots(dim, lkp), checked: new Map(), spotsDone: 0, sawKey, sawDone: false,
    alert: o.alertKey ? { key: o.alertKey, level: o.level || "noise", t: now, used: false } : null,
    faceYaw: (() => { try { return g.getRotation().y; } catch (_) { return 0; } })(), crawl: false, visitedLkp: false, nextSfx: now + 30, stepAt: 0,
  };
  ix.mode = "search";
  m.sawHide = null;
  searching.set(g.id, g);
  setPose(g, m.search, !api.canStand(g));
  sound(dim, Math.random() < 0.5 ? "ghost.sniff" : "ghost.growl", g.location, 1.2, rand(0.6, 0.85), 24);
  planNext(g, m, now, true);
}
function resumeSearch(g, m, now) {
  const S = m.search, ix = api.ixOf(m);
  ix.mode = "search"; S.stall = 0; S.breachTried = false;
  S.until = Math.max(S.until, now + 360);              // พังเข้ามาได้แล้ว: ค้นในบ้านต่ออีกพัก
  S.spots = findSpots(g.dimension, S.lkp);
  searching.set(g.id, g);
  setPose(g, S, !api.canStand(g));
  planNext(g, m, now);
}
function endSearch(g, m) {
  searching.delete(g.id);
  const S = m.search;
  if (S && S.spot && S.spot.kind === "closet") setDoor(S.spot.ent, 0);
  m.search = null;
  if (m.ix && (m.ix.mode === "search" || m.ix.mode === "follow")) { m.ix.mode = "chase"; m.ix.route = null; }
}
function spotPoint(g, S, sp, purpose) {
  const dim = g.dimension, n = typeNum(g);
  if (sp.kind === "closet") {
    const e = sp.ent; if (!valid(e)) return null;
    const f = dirOfYaw(e.getRotation().y), l = e.location;
    for (const k of [0.46 + INSPECT_REACH[n], 2.0, 1.5, 1.1]) {
      const x = l.x + f.x * k, z = l.z + f.z * k;
      const c = standCell(dim, x, z, fl(l.y) + 1, 3, 1);
      if (c) return { x, y: c.y, z, look: { x: l.x, y: l.y + 1.45, z: l.z } };
    }
    return null;
  }
  // เตียง: ยืนด้านเดียวกับที่คนซ่อนหันหน้าออกมา (คนใต้เตียงจะเห็นเท้า)
  const occ = occupantOf(sp.key), oh = occ ? P.get(occ.id).hide : null;
  let sides;
  if (oh) sides = [oh.side];
  else sides = sp.axis === "x" ? [{ x: 0, z: 1 }, { x: 0, z: -1 }] : [{ x: 1, z: 0 }, { x: -1, z: 0 }];
  const base = oh ? { x: oh.cell.x + 0.5, z: oh.cell.z + 0.5 } : { x: sp.pos.x, z: sp.pos.z };
  for (const S2 of sides) {
    const ks = purpose === "peek" ? [0.32 + 0.95 + PEEK_REACH[n]] : [0.5 + INSPECT_REACH[n] * 0.8, 1.85, 1.5];
    for (const k of ks) {
      const x = base.x + S2.x * k, z = base.z + S2.z * k;
      const c = standCell(dim, x, z, fl(sp.pos.y) + 1, 3, 1);
      if (c || purpose === "peek") return { x, y: c ? c.y : sp.pos.y, z, look: { x: base.x, y: sp.pos.y + (purpose === "peek" ? 0.1 : 0.4), z: base.z }, side: S2 };
    }
  }
  return null;
}
function goTo(g, m, S, T, after) { S.phase = "goto"; S.goal = T; S.after = after; S.spot = null; S.stall = 0; S.rush = false; S.wander = false; S.t1 = system.currentTick; S.routeTried = false; S.routeDone = false; S.routeChecked = false; }
function goSpot(g, m, S, sp, rush) {
  const T = spotPoint(g, S, sp, "inspect");
  if (!T) { S.checked.set(sp.key, 9); return planNext(g, m, system.currentTick); }
  S.phase = "goto"; S.goal = T; S.after = "inspect"; S.spot = sp; S.stall = 0; S.rush = !!rush; S.wander = false; S.t1 = system.currentTick;
  S.routeTried = false; S.routeDone = false; S.routeChecked = false;
}
function spotByKey(S, key, dim) {
  let sp = S.spots.find((x) => x.key === key);
  if (!sp) {
    const occ = occupantOf(key);
    if (occ) {
      const h = P.get(occ.id).hide;
      sp = h.kind === "closet" ? closetSpot(h.ent) : bedSpot(dim, h.cell.x, h.cell.y, h.cell.z);
      S.spots.push(sp);
    }
  }
  return sp || null;
}
// ช่องที่เดินถึงได้จากตัวผีโดยไม่ต้องพังอะไร (ประตูปิด = ผนัง)
function reachable(dim, from, R, crawl, maxN = 420) {
  const h = crawl ? 1 : 2;
  const start = standCell(dim, from.x, from.z, fl(from.y) + 1, 3, h) || standCell(dim, from.x, from.z, fl(from.y) + 1, 3, 1);
  if (!start) return [];
  const seen = new Set([`${start.x},${start.z}`]), q = [start];
  for (let i = 0; i < q.length && q.length < maxN; i++) {
    const c = q[i];
    for (const [dx, dz] of N4) {
      const nx = c.x + dx, nz = c.z + dz, k = `${nx},${nz}`;
      if (seen.has(k) || Math.hypot(nx - start.x, nz - start.z) > R) continue;
      seen.add(k);
      const n = standCell(dim, nx + 0.5, nz + 0.5, c.y + 1, 2, h);
      if (!n || Math.abs(n.y - c.y) > 1) continue;
      q.push(n);
    }
  }
  return q;
}
function randomPoint(g, S) {
  const area = reachable(g.dimension, g.location, 10, S.crawl);
  const L = g.location;
  const far = area.filter((c) => Math.hypot(c.x + 0.5 - L.x, c.z + 0.5 - L.z) >= 2.5 && Math.hypot(c.x + 0.5 - S.lkp.x, c.z + 0.5 - S.lkp.z) <= 9);
  const pool = far.length ? far : area.filter((c) => Math.hypot(c.x + 0.5 - L.x, c.z + 0.5 - L.z) >= 2);
  if (!pool.length) return null;
  const c = pool[Math.floor(Math.random() * pool.length)];
  return { x: c.x + 0.5, y: c.y, z: c.z + 0.5 };
}
function planNext(g, m, now, first) {
  const S = m.search, dim = g.dimension;
  if (S.alert && !S.alert.used) {
    S.alert.used = true;
    const sp = spotByKey(S, S.alert.key, dim);
    if (sp) return goSpot(g, m, S, sp, S.alert.level === "gasp");
  }
  if (S.sawKey && !S.sawDone) {
    S.sawDone = true;
    const sp = spotByKey(S, S.sawKey, dim);
    if (sp) return goSpot(g, m, S, sp, true);
  }
  if (first || !S.visitedLkp) { S.visitedLkp = true; return goTo(g, m, S, S.lkp, "look"); }
  if (S.spotsDone < 4) {
    const next = S.spots.filter((sp) => !S.checked.has(sp.key)).sort((a, b) => dist(a.pos, g.location) - dist(b.pos, g.location))[0];
    if (next) return goSpot(g, m, S, next, false);
  }
  const w = randomPoint(g, S);
  goTo(g, m, S, w || { x: g.location.x, y: g.location.y, z: g.location.z }, "look");
  S.wander = true;
}
function watched(g) {
  for (const p of playersNear(g.dimension, g.location, 48)) {
    if (isHidden(p)) continue;
    try {
      const v = p.getViewDirection(), dx = g.location.x - p.location.x, dz = g.location.z - p.location.z, d = Math.hypot(dx, dz) || 1;
      if ((v.x * dx + v.z * dz) / (d * (Math.hypot(v.x, v.z) || 1)) > 0.8 && canSee(g, p, 48)) return true;
    } catch (_) {}
  }
  return false;
}
function walk(g, S, T, now) {
  const L = g.location, dim = g.dimension;
  const dx = T.x - L.x, dz = T.z - L.z, d = Math.hypot(dx, dz);
  if (d < 0.35 && Math.abs(T.y - L.y) < 1.6) return "arrived";
  const sp = Math.min(S.rush ? 0.2 : S.crawl ? 0.075 : 0.105, d);
  const base = Math.atan2(dz, dx);
  for (const off of [0, 0.55, -0.55, 1.15, -1.15]) {
    const a = base + off;
    const nx = L.x + Math.cos(a) * sp, nz = L.z + Math.sin(a) * sp;
    const cell = standCell(dim, nx, nz, fl(L.y) + 1, 3, S.crawl ? 1 : 2);
    if (!cell || cell.y > L.y + 1.01) continue;
    face(g, { x: nx, y: cell.y, z: nz }, { x: nx + Math.cos(a) * 4, y: cell.y + (S.crawl ? 0.6 : 2.6), z: nz + Math.sin(a) * 4 }, S);
    if (now >= S.stepAt) { S.stepAt = now + (S.rush ? 6 : 11); sound(dim, S.crawl ? "mob.spider.step" : "mob.zombie.step", g.location, 0.7, rand(0.45, 0.6), 20); }
    return off === 0 ? "moved" : "detour";
  }
  if (!S.crawl) {
    const c = standCell(dim, L.x + Math.cos(base) * sp, L.z + Math.sin(base) * sp, fl(L.y) + 1, 3, 1);
    if (c) { setPose(g, S, true); return "detour"; }
  }
  return "blocked";
}
// v1.4: เดินตรงไปไม่ได้ (คนละชั้น / ผนังกั้น / ต้องอ้อม) -> ให้ระบบหาทางวางเส้นทางจริง
function needsRoute(g, T) {
  const L = g.location, dim = g.dimension;
  if (Math.abs(T.y - L.y) > 1.2) return true;
  const d = Math.hypot(T.x - L.x, T.z - L.z);
  if (d < 1.5) return false;
  let y = fl(L.y + 0.05);
  const n = Math.ceil(d / 0.5);
  for (let i = 1; i <= n; i++) {
    const f = i / n, x = L.x + (T.x - L.x) * f, z = L.z + (T.z - L.z) * f;
    const c = standCell(dim, x, z, y + 1, 2, 2);
    if (!c || Math.abs(c.y - y) > 1) return true;
    y = c.y;
  }
  return Math.abs(y - fl(T.y + 0.05)) > 1;
}
function routeTo(g, m, S, now) {
  S.routeTried = true;
  if (!api.followTo) return false;
  searching.delete(g.id);
  api.followTo(g, m, S.goal, now, { rush: S.rush });
  return true;
}
function tickGoto(g, m, S, now) {
  if (!S.routeChecked && !S.routeDone && !S.wander) {           // เช็คครั้งเดียวตอนเริ่มเดิน
    S.routeChecked = true;
    if (!S.routeTried && needsRoute(g, S.goal) && routeTo(g, m, S, now)) return;
  }
  const res = walk(g, S, S.goal, now);
  if (res === "arrived") return arrive(g, m, S, now);
  if (res === "blocked") S.stall += 1; else if (res === "detour") S.stall += 0.25; else S.stall = Math.max(0, S.stall - 1);
  if (now % 10 === 0) {
    const ok = api.canStand(g);
    if (S.crawl && ok && now % 20 === 0) setPose(g, S, false);          // มีที่พอ: ลุกขึ้นยืน
    else if (!S.crawl && !ok) setPose(g, S, true);                      // เพดานต่ำ: ก้มคลาน
  }
  if (S.stall >= 30 || now - S.t1 > 420) {
    S.stall = 0; S.t1 = now;
    if (S.wander) return planNext(g, m, now);           // จุดเดินสุ่มไปไม่ถึง: เลือกใหม่
    if (S.routeDone) return arrive(g, m, S, now);       // เดินตามเส้นทางมาจนใกล้แล้ว
    if (!S.routeTried && routeTo(g, m, S, now)) return;
    const dim = g.dimension, G = S.goal, gx = fl(G.x), gz = fl(G.z);
    const area = reachable(dim, g.location, 13, S.crawl, 420);
    const sameRoom = area.some((c) => Math.abs(c.x - gx) <= 1 && Math.abs(c.z - gz) <= 1 && Math.abs(c.y - G.y) <= 2);
    if (sameRoom || (outdoors(dim, G) && outdoors(dim, g.location))) {
      // ทางเดินซับซ้อนแต่ไปถึงได้: ตอนไม่มีใครมอง มันวาบไปเลย
      if (!watched(g)) {
        const c = standCell(dim, G.x, G.z, fl(G.y) + 1, 3, 1);
        if (c) {
          for (let i = 0; i < 5; i++) particle(dim, "minecraft:basic_smoke_particle", { x: g.location.x + rand(-0.5, 0.5), y: g.location.y + rand(0.3, 2.5), z: g.location.z + rand(-0.5, 0.5) });
          face(g, { x: G.x, y: c.y, z: G.z }, G.look || { x: G.x + 1, y: c.y + 1, z: G.z }, S);
          return arrive(g, m, S, now);
        }
      }
    } else {
      // ประตู/หน้าต่าง/ไม้กั้นขวาง: ใช้ระบบพังบ้าน / ไม่มีช่องเลย: ค่อย ๆ ทุบผนังเข้าไป แล้วค่อยหาต่อ
      if (!S.breachTried && api.breachToward(g, m, G, now)) { S.breachTried = true; searching.delete(g.id); return; }
    }
    if (S.spot) { S.checked.set(S.spot.key, 9); S.spotsDone++; }
    planNext(g, m, now);
  }
}
// กลางแจ้ง (ไม่มีหลังคา) = ไม่มีห้องให้พัง ผีวาบข้ามสิ่งกีดขวางได้ตอนไม่มีใครมอง
function outdoors(dim, P) {
  for (let dy = 2; dy <= 14; dy++) {
    const b = getBlock(dim, P.x, P.y + dy, P.z);
    if (b && !b.isAir && !blockId(b).includes("leaves") && !passable(b)) return false;
  }
  return true;
}
function arrive(g, m, S, now) {
  if (S.after === "inspect" && S.spot) return startInspect(g, m, S, now);
  S.phase = "look"; S.lookUntil = now + Math.floor(rand(40, 75)); S.turnAt = now + 12;
  sound(g.dimension, "ghost.sniff", eyeOf(g), 1.2, rand(0.75, 0.95), 16);
}
function tickLook(g, m, S, now) {
  if (now >= S.turnAt) {
    S.turnAt = now + Math.floor(rand(16, 26));
    const yaw = S.faceYaw + (Math.random() < 0.5 ? -1 : 1) * rand(50, 120), f = dirOfYaw(yaw), L = g.location;
    face(g, L, { x: L.x + f.x * 4, y: L.y + (S.crawl ? 0.6 : 2.6), z: L.z + f.z * 4 }, S);
  }
  if (now >= S.lookUntil) planNext(g, m, now);
}
function startInspect(g, m, S, now) {
  const sp = S.spot, T = S.goal;
  S.phase = "inspect"; S.peak = 0;
  const forced = S.sawKey === sp.key || (S.alert && S.alert.key === sp.key && S.alert.level === "gasp");
  S.inspectUntil = now + (forced ? 22 : Math.floor(rand(62, 92)));
  S.forced = forced;
  trigger(g, "ghost:inspect");
  face(g, g.location, T.look || sp.lookAt, S);
  if (sp.kind === "closet") setDoor(sp.ent, 3);
  sound(g.dimension, "ghost.sniff", sp.lookAt, 1.4, rand(0.75, 0.95), 16);
  S.sfxAt = now + 20;
}
function tickInspect(g, m, S, now) {
  const sp = S.spot, dim = g.dimension;
  if (sp.kind === "closet" && !valid(sp.ent)) { S.checked.set(sp.key, 9); setPose(g, S, S.crawl); return planNext(g, m, now); }
  const occ = occupantOf(sp.key);
  if (occ) {
    const s = P.get(occ.id);
    S.peak = Math.max(S.peak, s.breath);
    if (now - s.gaspAt < 6) return reveal(g, m, S, sp, occ, now);
  }
  if (now >= S.sfxAt) {
    S.sfxAt = now + Math.floor(rand(18, 30));
    if (sp.kind === "closet" && Math.random() < 0.55) sound(dim, "ghost.scratch", sp.lookAt, 1.2, rand(0.9, 1.15), 16);
    else sound(dim, "ghost.sniff", sp.lookAt, 1.3, rand(0.7, 0.95), 16);
  }
  if (now < S.inspectUntil) return;
  if (sp.kind === "closet") setDoor(sp.ent, 0);
  const tries = (S.checked.get(sp.key) || 0) + 1;
  S.checked.set(sp.key, tries); S.spotsDone++;
  if (occ) {
    let chance = sp.kind === "closet" ? 0.12 : 0.1;
    if (S.forced) chance = 1;
    if (S.peak >= BREATH_WARN) chance += 0.45;
    if (S.alert && S.alert.key === sp.key) chance += 0.25;
    chance += 0.15 * (tries - 1);
    if (Math.random() < chance) return reveal(g, m, S, sp, occ, now);
  }
  setPose(g, S, !api.canStand(g));
  planNext(g, m, now);
}
// ---------------------------------------------------------------------------------- เจอตัว
function reveal(g, m, S, sp, p, now) {
  const dim = g.dimension;
  S.victim = p; S.t2 = now;
  if (sp.kind === "closet") {
    S.phase = "yank";
    setDoor(sp.ent, 2);
    sound(dim, "ghost.closet.yank", sp.pos, 2.0, rand(0.9, 1.05), 40);
    shake(dim, sp.pos, 10, 0.7, 0.6);
    return;
  }
  // ใต้เตียง: หน้ามันโผล่มาที่ระดับพื้น จ้องเข้ามาใต้เตียง
  S.phase = "peek";
  const T = spotPoint(g, S, sp, "peek");
  if (T) face(g, { x: T.x, y: T.y, z: T.z }, T.look, S);
  trigger(g, "ghost:peek");
  sound(dim, "ghost.breath", sp.lookAt, 1.6, 0.7, 12);
  system.runTimeout(() => { try { sound(dim, "ghost.growl", sp.lookAt, 1.4, 0.5, 20); } catch (_) {} }, 12);
}
function dragOut(g, m, p) {
  const s = P.get(p.id), h = s && s.hide;
  if (!h) return;
  exitHide(p, { forced: true, quiet: true, faceTo: { x: g.location.x, y: g.location.y + 1.8, z: g.location.z } });
  try { api.jumpscare(p, true); } catch (_) {}
  try { p.addEffect("nausea", 140, { amplifier: 0, showParticles: false }); } catch (_) {}
  try { p.addEffect("slowness", 40, { amplifier: 1, showParticles: false }); } catch (_) {}
  actionbar(p, pick(["§4§lเจอแล้ว", "§4§lเจอตัวแล้ว...", "§4§lซ่อนไม่พ้นหรอก"]));
}
function toChase(g, m, p, now) {
  endSearch(g, m);
  m.lkp = { x: p.location.x, y: p.location.y, z: p.location.z }; m.lkpAt = now; m.lastDetect = now;
  if (!m.huntStart) m.huntStart = now;
  if (now - m.huntStart < 5 * 60 * 20) m.chaseStart = Math.max(m.chaseStart || now, now - 900);   // เจอตัวอีกครั้ง: ไล่ต่อได้อีก ~45 วินาที (ล่าตัวเดิมได้ไม่เกิน ~5 นาที)
  m._resuming = true;
  try { api.resumeChase(g, m, now); } catch (_) {}
  m._resuming = false;
}
function tickYank(g, m, S, now) {
  const p = S.victim;
  if (now - S.t2 === 5 && valid(p)) dragOut(g, m, p);
  if (now - S.t2 >= 24) {
    if (S.spot && S.spot.ent) setDoor(S.spot.ent, 1);
    system.runTimeout(() => { try { if (S.spot) setDoor(S.spot.ent, 0); } catch (_) {} }, 60);
    if (valid(p)) toChase(g, m, p, now); else { endSearch(g, m); api.vanish(g); }
  }
}
function tickPeek(g, m, S, now) {
  const p = S.victim;
  if (now - S.t2 === 30 && valid(p)) dragOut(g, m, p);
  if (now - S.t2 >= 44) { if (valid(p)) toChase(g, m, p, now); else { endSearch(g, m); api.vanish(g); } }
}
function notice(g, m, det, now) {
  const p = det.p;
  sound(g.dimension, "ghost.growl", g.location, 2, rand(0.5, 0.7), 40);
  face(g, g.location, { x: p.location.x, y: p.location.y + 1.6, z: p.location.z }, m.search);
  if (!isHidden(p)) actionbar(p, det.how === "hear" ? "§4§lมันได้ยินคุณ!" : "§4§lมันเห็นคุณแล้ว!");
  toChase(g, m, p, now);
}
function giveUp(g, m, now) {
  for (const p of playersNear(g.dimension, g.location, 48)) actionbar(p, pick(["§8...มันหาคุณไม่เจอ...", "§8...มันจากไปแล้ว... §4คราวนี้", "§8...เสียงฝีเท้าค่อย ๆ เงียบหายไป..."]));
  sound(g.dimension, "mob.ghast.moan", g.location, 1.6, 0.45, 48);
  endSearch(g, m);
  try { api.vanish(g); } catch (_) {}
}
function searchTick(g, m, now) {
  const S = m.search;
  if (!S || !m.ix || m.ix.mode !== "search") { searching.delete(g.id); return; }
  if (api.leaving.has(g.id) || (m.appeaseUntil && m.appeaseUntil > now)) { endSearch(g, m); return; }   // คุยต่อรอง/ถูกขอของ: หยุดหา
  const dim = g.dimension;
  if (!playersNear(dim, g.location, 56).length) return giveUp(g, m, now);
  const busy = S.phase === "peek" || S.phase === "yank";
  if (now >= S.until && !busy && S.phase !== "inspect") return giveUp(g, m, now);
  if (!busy && now % 3 === 0) {
    const det = perceive(g, m, now, true);
    if (det) return notice(g, m, det, now);
  }
  if (S.alert && !S.alert.used && (S.phase === "goto" || S.phase === "look") && !(S.spot && S.spot.key === S.alert.key)) planNext(g, m, now);
  if (!busy && now >= S.nextSfx) {
    S.nextSfx = now + Math.floor(rand(70, 140));
    const r = Math.random();
    if (r < 0.4) sound(dim, "ghost.sniff", eyeOf(g), 1.1, rand(0.7, 0.9), 20);
    else if (r < 0.65) sound(dim, "ghost.growl", g.location, 0.9, rand(0.45, 0.6), 24);
    for (const p of playersNear(dim, g.location, 20)) {
      try { p.addEffect("darkness", 60, { amplifier: 0, showParticles: false }); } catch (_) {}
      if (!isHidden(p) && Math.random() < 0.35) actionbar(p, pick(["§8...ฟึด... ฟึด...", "§8...ฉันได้กลิ่นเธอ...", "§8...ออกมาเถอะ...", "§8...เธออยู่แถวนี้..."]));
    }
  }
  switch (S.phase) {
    case "goto": return tickGoto(g, m, S, now);
    case "look": return tickLook(g, m, S, now);
    case "inspect": return tickInspect(g, m, S, now);
    case "peek": return tickPeek(g, m, S, now);
    case "yank": return tickYank(g, m, S, now);
  }
}
// ---------------------------------------------------------------------------------- v1.4 ระหว่างเดินตามเส้นทาง (intrude คุมการเดิน)
// เรียกทุก tick: ยังต้องฟัง/มองหาผู้เล่นอยู่ -> "stop" = ระบบค้นหารับช่วงต่อเอง (เจอตัว/ยอมแพ้/มีเสียงใหม่)
export function searchFollowTick(g, m, now) {
  const S = m.search;
  if (!S) return "stop";
  if (api.leaving.has(g.id) || (m.appeaseUntil && m.appeaseUntil > now)) { endSearch(g, m); return "stop"; }
  const dim = g.dimension;
  if (!playersNear(dim, g.location, 56).length || now >= S.until + 300) { giveUp(g, m, now); return "stop"; }
  try { S.faceYaw = g.getRotation().y; } catch (_) {}
  if (now % 3 === 0) {
    const det = perceive(g, m, now, true);
    if (det) { notice(g, m, det, now); return "stop"; }
  }
  if (S.alert && !S.alert.used && !(S.spot && S.spot.key === S.alert.key)) {
    const ix = api.ixOf(m);
    api.release(g, m, now); ix.mode = "search"; ix.route = null;
    searching.set(g.id, g);
    setPose(g, S, !api.canStand(g));
    planNext(g, m, now);
    return "stop";
  }
  if (now >= S.nextSfx) {
    S.nextSfx = now + Math.floor(rand(70, 140));
    if (Math.random() < 0.5) sound(dim, "ghost.sniff", eyeOf(g), 1.1, rand(0.7, 0.9), 20);
    for (const p of playersNear(dim, g.location, 20)) {
      try { p.addEffect("darkness", 60, { amplifier: 0, showParticles: false }); } catch (_) {}
      if (!isHidden(p) && Math.random() < 0.3) actionbar(p, pick(["§8...ฟึด... ฟึด...", "§8...ฉันได้กลิ่นเธอ...", "§8...เธออยู่แถวนี้..."]));
    }
  }
  return null;
}
// เดินตามเส้นทางจบแล้ว (ok = ถึงใกล้จุดหมาย) -> ค้นหาต่อ
export function searchFollowDone(g, m, now, ok) {
  const S = m.search;
  if (!S || !valid(g)) return;
  const ix = api.ixOf(m);
  ix.mode = "search";
  searching.set(g.id, g);
  setPose(g, S, !api.canStand(g));
  S.phase = "goto"; S.stall = 0; S.t1 = now;
  if (ok) S.routeDone = true;            // เหลือระยะสั้น ๆ เดินตรงไปจุดสุดท้าย
  else S.routeTried = true;              // หาทางไม่ได้: ใช้วิธีเดิม (เดินตรง/วาบตอนไม่มีใครมอง/พังเข้าไป)
}
// เรียกจาก intrude.resumeChase ตอนพัง/มุดเข้ามาเสร็จ: ถ้ายังไม่เห็นใคร -> หาต่อ
export function afterIntrusion(g, m, now) {
  if (m._resuming) return false;
  if (perceive(g, m, now, false)) return false;
  if (m.search) { resumeSearch(g, m, now); return true; }
  let near = null, nd = 40;
  for (const p of playersNear(g.dimension, g.location, 40)) { const d = dist(p.location, g.location); if (d < nd) { nd = d; near = p; } }
  if (!near) return false;
  startSearch(g, m, now, { near: near.location });
  return true;
}

// ---------------------------------------------------------------------------------- ตู้: วาง / เก็บ
const lastPlace = new Map();
function onUseCloset(p, block, face, itemStack, cancel) {
  if (!itemStack || itemStack.typeId !== CLOSET_ITEM || !block) return false;
  cancel();
  const t = system.currentTick;
  if (t - (lastPlace.get(p.id) ?? -99) < 8) return true;
  lastPlace.set(p.id, t);
  system.run(() => { try { placeCloset(p, block, face); } catch (_) {} });
  return true;
}
function placeCloset(p, block, face) {
  const dim = block.dimension, b = block.location;
  const off = { Up: [0, 1, 0], Down: [0, -1, 0], North: [0, 0, -1], South: [0, 0, 1], East: [1, 0, 0], West: [-1, 0, 0] }[String(face)] || [0, 1, 0];
  const x = b.x + off[0], y = b.y + off[1], z = b.z + off[2];
  const cell = standCell(dim, x, z, y, 2, 2);
  if (!cell || cell.y < y - 1) { actionbar(p, "§7วางตรงนี้ไม่ได้ (ต้องมีที่ว่างสูง 2 ช่อง)"); return; }
  const C = { x: cell.x + 0.5, y: cell.y, z: cell.z + 0.5 };
  try { if (dim.getEntities({ type: CLOSET, location: C, maxDistance: 0.95 }).length) return; } catch (_) {}
  const yaw = Math.round(yawOfDir(p.location.x - C.x, p.location.z - C.z) / 90) * 90;
  let e;
  try { e = dim.spawnEntity(CLOSET, C); } catch (_) { return; }
  try { e.teleport(C, { rotation: { x: 0, y: yaw } }); } catch (_) {
    const f = dirOfYaw(yaw);
    try { e.teleport(C, { facingLocation: { x: C.x + f.x * 3, y: C.y + 1, z: C.z + f.z * 3 } }); } catch (__) {}
  }
  sound(dim, "ghost.closet.close", C, 1, 0.9, 16);
  if (!isCreative(p)) {
    try {
      const inv = p.getComponent("minecraft:inventory").container;
      const eq = p.getComponent("minecraft:equippable");
      const it = eq && eq.getEquipment("Mainhand");
      if (it && it.typeId === CLOSET_ITEM) {
        if (it.amount > 1) { it.amount -= 1; eq.setEquipment("Mainhand", it); } else eq.setEquipment("Mainhand", undefined);
      } else {
        for (let i = 0; i < inv.size; i++) { const s = inv.getItem(i); if (s && s.typeId === CLOSET_ITEM) { if (s.amount > 1) { s.amount -= 1; inv.setItem(i, s); } else inv.setItem(i, undefined); break; } }
      }
    } catch (_) {}
  }
}
function onHitCloset(p, e) {
  if (!e || e.typeId !== CLOSET || !p || p.typeId !== "minecraft:player") return;
  for (const q of world.getAllPlayers()) { const h = hideOf(q); if (h && h.ent && h.ent.id === e.id) return; }
  const dim = e.dimension, l = { x: e.location.x, y: e.location.y, z: e.location.z };
  try { e.remove(); } catch (_) { return; }
  sound(dim, "dig.wood", l, 1, 0.8, 16);
  particle(dim, "ghost:woodchunk", { x: l.x, y: l.y + 1, z: l.z });
  if (!isCreative(p)) {
    try { dim.spawnItem(new ItemStack(CLOSET_ITEM, 1), { x: l.x, y: l.y + 0.5, z: l.z }); } catch (_) {}
  }
}

// ---------------------------------------------------------------------------------- loop + events
function tick() {
  const now = system.currentTick;
  try { playerTick(now); } catch (e) { if (globalThis.__simDebug) globalThis.__errors.push(e); }
  for (const [id, g] of searching) {
    try {
      if (!valid(g)) { searching.delete(id); continue; }
      const m = api.meta.get(id);
      if (!m) { searching.delete(id); continue; }
      searchTick(g, m, now);
    } catch (e) { if (globalThis.__simDebug) globalThis.__errors.push(e); }
  }
}
function cleanupAll() {
  for (const p of world.getAllPlayers()) {
    try { p.removeTag(HIDDEN_TAG); } catch (_) {}
    cmd(p, "camera @s clear");
  }
  for (const d of api.dims()) { try { for (const e of d.getEntities({ type: SEAT })) e.remove(); } catch (_) {} }
}
export function initHide(a) {
  api = a;
  system.runTimeout(() => { try { cleanupAll(); } catch (_) {} }, 2);
  system.runInterval(tick, 1);
  // ตู้: คลิกขวาที่ตู้ = เข้าไปนั่ง (rideable) -> ตรวจหลังขึ้นนั่ง
  sub("afterEvents", "playerInteractWithEntity", (ev) => {
    try {
      const e = ev.target, p = ev.player;
      if (!e || e.typeId !== CLOSET) return;
      const s = ps(p); s.interact = { id: e.id, t: system.currentTick };
      for (const d of [2, 5, 10]) system.runTimeout(() => { try { if (!isHidden(p)) checkClosetEntry(p, e); } catch (_) {} }, d);
    } catch (_) {}
  });
  // เตียง: ย่อ + คลิกขวา = มุดใต้เตียง  |  ถือตู้แล้วคลิกบล็อก = วางตู้
  const okInteract = sub("beforeEvents", "playerInteractWithBlock", (ev) => {
    try {
      const p = ev.player, b = ev.block;
      if (onUseCloset(p, b, ev.blockFace, ev.itemStack, () => { ev.cancel = true; })) return;
      if (b && isBed(b) && p.isSneaking && !isHidden(p)) {
        ev.cancel = true;
        const t = system.currentTick, s = ps(p);
        if (t - (s.bedAt || -99) < 15) return;
        s.bedAt = t;
        system.run(() => { try { hideUnderBed(p, b); } catch (_) {} });
      }
    } catch (_) {}
  });
  sub("beforeEvents", "itemUseOn", (ev) => {
    try { if (!okInteract) onUseCloset(ev.source, ev.block, ev.blockFace, ev.itemStack, () => { ev.cancel = true; }); else if (ev.itemStack && ev.itemStack.typeId === CLOSET_ITEM) ev.cancel = true; } catch (_) {}
  });
  sub("afterEvents", "entityHitEntity", (ev) => { try { onHitCloset(ev.damagingEntity, ev.hitEntity); } catch (_) {} });
  // เสียงที่ผีได้ยิน
  sub("afterEvents", "playerInteractWithBlock", (ev) => {
    try {
      const id = blockId(ev.block);
      if (/door|gate/.test(id)) impulse(ev.block.dimension, ev.block.location, 14, ev.player.id);
      else if (/chest|barrel|shulker/.test(id)) impulse(ev.block.dimension, ev.block.location, 9, ev.player.id);
    } catch (_) {}
  });
  sub("afterEvents", "playerBreakBlock", (ev) => { try { impulse(ev.player.dimension, ev.block.location, 14, ev.player.id); } catch (_) {} });
  sub("afterEvents", "playerPlaceBlock", (ev) => { try { impulse(ev.player.dimension, ev.block.location, 8, ev.player.id); } catch (_) {} });
  sub("afterEvents", "entityHitEntity", (ev) => { try { const p = ev.damagingEntity; if (p && p.typeId === "minecraft:player") impulse(p.dimension, p.location, 10, p.id); } catch (_) {} });
  // ตาย / ออกเกมระหว่างซ่อน
  sub("afterEvents", "entityDie", (ev) => { try { const p = ev.deadEntity; if (p && p.typeId === "minecraft:player" && isHidden(p)) exitHide(p, { forced: true, quiet: true, noMove: true }); } catch (_) {} });
  sub("afterEvents", "playerSpawn", (ev) => {
    try {
      const p = ev.player;
      if (isHidden(p)) exitHide(p, { forced: true, quiet: true, noMove: true });
      if (ev.initialSpawn) { try { p.removeTag(HIDDEN_TAG); } catch (_) {} cmd(p, "camera @s clear"); }
    } catch (_) {}
  });
  sub("beforeEvents", "playerLeave", (ev) => {
    try {
      const s = P.get(ev.player.id);
      if (s && s.hide && s.hide.kind === "bed") { const seat = s.hide.ent; system.run(() => { try { if (valid(seat)) seat.remove(); } catch (_) {} }); }
      P.delete(ev.player.id);
    } catch (_) {}
  });
}
