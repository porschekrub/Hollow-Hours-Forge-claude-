import { world, system } from "@minecraft/server";
import {
  fl, rand, pick, valid, dist, getBlock, blockId, passable, solid, standCell, keyOf, center, sound, particle, dirVars, shake,
  playersNear, actionbar, lookDotTo, lookingAtBlock, destroyBlock, isSheltered,
} from "./util.js";
import { classify, basePos, passAxis, hitOpening, crackGeneric, clearCrack, isOpen, crackOf, overlaySpec, boardsAt, boardsHits } from "./breach.js";
import { SIZE, PROFILE, KNOCK_REACH, BASH_REACH, INSPECT_REACH, typeNum } from "./body.js";
import { plan, planning, cancelPlan, hardness, breakable, digHits } from "./path.js";
import { los } from "./hide.js";

// =====================================================================
//  ผีบุกบ้าน
//  ไล่ -> ติด -> (v1.4) วางแผนเส้นทางจริง: เดิน ขึ้นบันได กระโดด ปีนขอบ มุดหน้าต่าง
//     -> พังเฉพาะสิ่งที่ขวางทางจริง ๆ และเลือกจุดที่ "คุ้ม" ที่สุด (ประตู/หน้าต่าง ก่อนผนัง ไม่พังบันได)
//  -> ทุบจนร้าวแล้วระเบิด (หรือพังไม่ได้ถ้าพลังไม่พอ) -> มุดเข้ามา (บางทีตัวติด ค่อย ๆ ทุบกรอบออก)
//  + เหตุการณ์ "มาเคาะประตู/หน้าต่างแล้วหายไป"
// =====================================================================
let api = null;
const active = new Map();          // ghost id -> entity ที่สคริปต์ควบคุมอยู่
const SCRIPTED = new Set(["approach", "bash", "squeeze", "stuck", "knock", "dig", "search", "follow", "watch"]);
const KNOCK_WHISPERS = [
  "§8...ก๊อก... ก๊อก... ก๊อก...", "§8...เปิดประตูให้หน่อย...", "§8...ฉันรู้ว่าเธออยู่ข้างใน...", "§8...ข้างนอกนี่มันหนาว...",
  "§4...ให้ฉันเข้าไปนะ...", "§8...ทำไมไม่เปิดล่ะ...", "§4...ฉันได้ยินเสียงหายใจของเธอ...", "§8...แค่แง้มนิดเดียว...",
];
const WINDOW_WHISPERS = ["§8...ฉันเห็นเธอนะ...", "§4...มองฉันสิ...", "§8...เธอจะหลับได้เหรอคืนนี้...", "§4...กระจกบางจัง..."];

export function isScripted(m) { return !!(m && m.ix && SCRIPTED.has(m.ix.mode)); }
export function rollPower(bonus) {
  const r = Math.random();
  let p = r < 0.5 ? 1 : r < 0.85 ? 2 : 3;   // 50% ธรรมดา (พังได้แค่ไม้), 35% แข็งแรง (ทองแดง/หิน), 15% โหดมาก (เหล็กก็พัง)
  if (bonus) p++;
  return Math.min(3, p);
}
export function ixOf(m) {
  if (!m.ix) m.ix = { mode: "chase", failed: new Set(), lastProg: 0, nextPlan: 0, triedCrawl: false, crawling: false, pausedAt: 0 };
  return m.ix;
}
function power(g, m) { let p = m.power || 1; if (raged(g)) p++; return Math.min(3, p); }
function raged(g) { try { return g.getProperty("ghost:rage") === true; } catch (_) { return false; } }
function trigger(g, ev) { try { g.triggerEvent(ev); } catch (_) {} }
function setState(g, s) { try { g.setProperty("ghost:state", s); } catch (_) {} }
function face(g, loc, target) { try { g.teleport(loc, { facingLocation: target }); } catch (_) { try { g.teleport(loc); } catch (__) {} } }
function register(g, m, now) { active.set(g.id, g); const ix = ixOf(m); if (!ix.pausedAt) ix.pausedAt = now; }
export function release(g, m, now) {
  active.delete(g.id);
  const ix = ixOf(m);
  if (ix.pausedAt && m.chaseStart) m.chaseStart += now - ix.pausedAt;   // เวลาที่ใช้ทุบ/มุด ไม่นับเป็นเวลาไล่
  ix.pausedAt = 0;
}
function smoke(dim, loc) {
  for (let i = 0; i < 6; i++) particle(dim, "minecraft:basic_smoke_particle", { x: loc.x + rand(-0.6, 0.6), y: loc.y + rand(0.2, 3), z: loc.z + rand(-0.6, 0.6) });
  particle(dim, "ghost:dust", { x: loc.x, y: loc.y + 1, z: loc.z });
}
function growl(dim, loc, loud) {
  sound(dim, Math.random() < 0.5 ? "ghost.growl" : "mob.ghast.scream", loc, loud ? 2.2 : 1.4, rand(0.45, 0.7), 48);
}

// ---------------------------------------------------------------------------------- ท่าทาง ยืน/คลาน
const heightOf = (g) => (SIZE[typeNum(g)] || [1.0, 4.8])[1];
export function canStand(g) {
  const [w, h] = SIZE[typeNum(g)] || [1.0, 4.8];
  const l = g.location, dim = g.dimension;
  const x0 = fl(l.x - w / 2), x1 = fl(l.x + w / 2), z0 = fl(l.z - w / 2), z1 = fl(l.z + w / 2), y0 = fl(l.y + 0.05), y1 = fl(l.y + h);
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) for (let y = y0; y <= y1; y++) {
    if (!passable(getBlock(dim, x, y, z))) return false;
  }
  return true;
}
// ยืนเต็มตัวที่ช่องนี้ได้ไหม (เช็คแค่คอลัมน์กลาง — ใช้ตอนเดินตามเส้นทาง)
function fitsStand(g, x, y, z) {
  const dim = g.dimension, n = Math.ceil(heightOf(g) - 0.05);
  for (let k = 0; k < n; k++) if (!passable(getBlock(dim, x, y + k, z))) return false;
  return true;
}
function toCrawl(g, ix) { trigger(g, "ghost:crawl_chase"); ix.crawling = true; ix.mode = "crawlchase"; ix.crawlUntil = system.currentTick + 140; }
function toStand(g, ix, now) { trigger(g, "ghost:stand_chase"); ix.crawling = false; ix.mode = "chase"; ix.standSince = now; }
export function resumeChase(g, m, now) {
  const ix = ixOf(m);
  ix.route = null; ix.contAt = 0; ix.lowBash = false;
  cancelPlan(g.id);
  release(g, m, now);
  // ระบบแอบ: ถ้าตอนนี้ผีไม่เห็น/ไม่ได้ยินใครเลย -> เดินหาแทนการไล่
  try { if (api.afterIntrusion && api.afterIntrusion(g, m, now)) return; } catch (_) {}
  if (canStand(g)) toStand(g, ix, now); else toCrawl(g, ix);
  ix.lastProg = now; ix.bestD = undefined; ix.nextPlan = now + 40;
}

// ---------------------------------------------------------------------------------- หาช่องบุก (ใช้กับเหตุการณ์เคาะประตู + ทางสำรอง)
function sidesOf(dim, pos, info, G, P) {
  const ax = passAxis(dim, pos, info, G || P);
  const n = ax === "x" ? { x: 1, z: 0 } : { x: 0, z: 1 };
  const A = { x: pos.x - n.x, z: pos.z - n.z }, B = { x: pos.x + n.x, z: pos.z + n.z };
  // ด้าน "ใน" = ฝั่งเดียวกับผู้เล่น, ด้าน "นอก" = อีกฝั่ง (ผีต้องไปยืนฝั่งนอกแล้วทุบเข้ามา)
  const dA = Math.hypot(A.x + 0.5 - P.x, A.z + 0.5 - P.z), dB = Math.hypot(B.x + 0.5 - P.x, B.z + 0.5 - P.z);
  let O, I;
  if (Math.abs(dA - dB) < 0.4 && G) {
    const gA = Math.hypot(A.x + 0.5 - G.x, A.z + 0.5 - G.z), gB = Math.hypot(B.x + 0.5 - G.x, B.z + 0.5 - G.z);
    [O, I] = gA <= gB ? [A, B] : [B, A];
  } else [O, I] = dA >= dB ? [A, B] : [B, A];
  const dir = { x: (I.x - O.x) / 2, z: (I.z - O.z) / 2 };
  const stand = standCell(dim, O.x, O.z, pos.y, 3);
  if (!stand || stand.y < pos.y - 2) return null;
  if (!passable(getBlock(dim, I.x, pos.y, I.z))) return null;
  const inside = standCell(dim, I.x, I.z, pos.y, 4) || { x: I.x, y: pos.y, z: I.z };
  return { stand, inside, dir, pos: { x: pos.x, y: pos.y, z: pos.z }, info, key: keyOf(dim.id, pos) };
}
function scanOpenings(dim, P, R, dyMin, dyMax, fn) {
  const px = fl(P.x), py = fl(P.y), pz = fl(P.z), seen = new Set();
  for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) for (let dy = dyMin; dy <= dyMax; dy++) {
    const b = getBlock(dim, px + dx, py + dy, pz + dz);
    if (!b || b.isAir) continue;
    const info = classify(b);
    if (!info || info.kind === "trapdoor") continue;
    if ((info.kind === "door" || info.kind === "gate") && isOpen(b)) continue;
    const pos = basePos(dim, b, info);
    const k = keyOf(dim.id, pos);
    if (seen.has(k)) continue;
    seen.add(k);
    fn(pos, info, k);
  }
}
function findBreach(g, p, m) {
  const ix = ixOf(m), dim = g.dimension, P = p.location, G = g.location, pw = power(g, m), out = [];
  scanOpenings(dim, P, 9, -3, 3, (pos, info, k) => {
    if (ix.failed.has(k)) return;
    const c = sidesOf(dim, pos, info, G, P);
    if (!c) return;
    const bh = boardsHits(boardsAt(dim, pos), pw, false);
    c.score = dist(G, c.stand) + 1.4 * dist(c.inside, P) + (info.cls > pw || bh.strong ? 14 : 0) + bh.hits * 0.6 - (info.fam === "glass" ? 2 : 0) - (crackOf(dim, pos) ? 3 : 0);
    out.push(c);
  });
  out.sort((a, b) => a.score - b.score);
  return out.slice(0, 5);
}
// ระบบแอบ (ทางสำรองเมื่อวางแผนเส้นทางไม่ได้): ผีกำลังเดินหาแล้วติดประตู/หน้าต่าง -> พังเข้าไปหาจุดเป้าหมาย T
export function breachToward(g, m, T, now) {
  const ix = ixOf(m);
  const cands = findBreach(g, { location: { x: T.x, y: T.y, z: T.z } }, m);
  if (!cands.length) return false;
  ix.crawling = !canStand(g);
  startApproach(g, m, cands, now);
  return true;
}
export function digToward(g, m, T, now) {
  const ix = ixOf(m);
  ix.crawling = !canStand(g);
  return startDig(g, m, { location: { x: T.x, y: T.y, z: T.z } }, now);
}
// ช่องยืนด้านนอก "อยู่ในห้องอื่น" ไหม (มีหลังคา + ผนังล้อม) — ระเบียงที่มีหลังคาแต่โล่งด้านข้างยังนับเป็นข้างนอก
function enclosed(dim, c) {
  let roof = false;
  for (let dy = 2; dy <= 8; dy++) { const b = getBlock(dim, c.x, c.y + dy, c.z); if (b && !b.isAir && !blockId(b).includes("leaves")) { roof = true; break; } }
  if (!roof) return false;
  let walls = 0;
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    for (let d = 1; d <= 6; d++) { if (solid(getBlock(dim, c.x + dx * d, c.y + 1, c.z + dz * d))) { walls++; break; } }
  }
  return walls >= 4;
}
function knockTargets(dim, p, relaxed) {
  const P = p.location, out = [];
  scanOpenings(dim, P, 10, -2, 3, (pos, info) => {
    if (info.kind === "gate") return;
    const c = sidesOf(dim, pos, info, null, P);
    if (!c) return;
    const dp = Math.hypot(pos.x + 0.5 - P.x, pos.z + 0.5 - P.z);
    if (dp < (relaxed ? 1.2 : 2.5)) return;
    if (!relaxed && enclosed(dim, c.stand)) return;               // ด้านนอกต้องไม่ใช่ห้องปิด
    c.score = Math.abs(dp - 6) + (info.fam === "glass" ? -1.5 : 0) + rand(0, 3);
    out.push(c);
  });
  out.sort((a, b) => a.score - b.score);
  return out;
}
function standPoint(c) {
  const s = c.stand;
  return { x: s.x + 0.5 + c.dir.x * 0.18, y: s.y, z: s.z + 0.5 + c.dir.z * 0.18 };
}
const lookPoint = (c) => center(c.pos, c.info && c.info.kind === "door" ? 1.0 : 0.5);
// ระยะจากกลางช่องที่ยืนถึง "ผิว" ของประตู/หน้าต่าง/ผนังตามทิศ dir
function surfDist(dim, c) {
  const S = { x: c.stand.x + 0.5, z: c.stand.z + 0.5 };
  const along = (p) => (p.x - S.x) * c.dir.x + (p.z - S.z) * c.dir.z;
  try {
    const k = c.info && c.info.kind;
    if (k === "door" || k === "gate" || k === "trapdoor" || k === "pane") {
      const spec = overlaySpec(dim, c.pos, c.info);
      if (spec) return Math.max(0.3, along(spec.loc) - (k === "pane" ? 0.07 : 0.1));
    }
  } catch (_) {}
  return Math.max(0.3, along({ x: c.pos.x + 0.5, z: c.pos.z + 0.5 }) - 0.5);
}
// ตัวใหญ่แขนยาว: ถอยออกมายืนให้ "มือ" ไปโดนผิวพอดี (ถอยได้เท่าที่มีที่ว่างข้างหลัง)
function reachPoint(dim, c, reach) {
  const s = c.stand, d = c.dir;
  const C0 = { x: s.x + 0.5, y: s.y, z: s.z + 0.5 };
  const want = reach - surfDist(dim, c);
  if (want <= 0) { const f = Math.min(0.3, -want); return { x: C0.x + d.x * f, y: s.y, z: C0.z + d.z * f }; }
  let room = 0.35;
  for (let k = 1; k <= Math.ceil(want); k++) {
    const x = s.x - d.x * k, z = s.z - d.z * k;
    if (!(passable(getBlock(dim, x, s.y, z)) && passable(getBlock(dim, x, s.y + 1, z)) && solid(getBlock(dim, x, s.y - 1, z)))) break;
    room = k + 0.35;
  }
  const b = Math.min(want, room);
  return { x: C0.x - d.x * b, y: s.y, z: C0.z - d.z * b };
}
function bashPoint(g, c) {
  if (ixOfEnt(g).lowBash) return standPoint(c);                     // ก้มคลานทุบในที่แคบ: ชิดเลย
  return reachPoint(g.dimension, c, BASH_REACH[typeNum(g)] || 3.5);
}
function knockPoint(dim, c, type) { return reachPoint(dim, c, KNOCK_REACH[type] || 2.4); }
function ixOfEnt(g) { const m = api.meta.get(g.id); return m ? ixOf(m) : {}; }

// ---------------------------------------------------------------------------------- hook จาก main (ทุก 5 tick ตอนไล่)
export function chaseHook(g, m, best, bestD, now, state) {
  const ix = ixOf(m);
  if (state === 7 && now >= (ix.nextStand || 0) && now >= (ix.crawlUntil || 0)) {
    ix.nextStand = now + 20;
    if (canStand(g)) { toStand(g, ix, now); return; }
  }
  if (state === 2 && ix.triedCrawl && now - (ix.standSince || 0) > 400) ix.triedCrawl = false;
  if (ix.bestD === undefined || bestD < ix.bestD - 0.8 || bestD > ix.bestD + 5) { ix.bestD = bestD; ix.lastProg = now; }
  if (bestD < 3.2 && Math.abs(best.location.y - g.location.y) < 1.8) { ix.lastProg = now; ix.bestD = bestD; return; }
  if (now - ix.lastProg < 40 || now < (ix.nextPlan || 0)) return;
  ix.nextPlan = now + 60;
  if (ix.failedAll) return;
  // 1) ตัวใหญ่ติดอยู่: ลองคลานก่อน (ตัวเล็กลง AI ของเกมพาไปต่อเองได้ถ้ามีทางโล่ง)
  if (state === 2 && !ix.triedCrawl && heightOf(g) > 2.05) { ix.triedCrawl = true; toCrawl(g, ix); ix.lastProg = now; ix.bestD = bestD; ix.nextPlan = now + 30; return; }
  // 2) v1.4: คิดเส้นทางจริง — เดิน/ขึ้นบันได/ปีน/กระโดดก่อน พังเฉพาะที่จำเป็น
  planChase(g, m, best, now);
}

// ---------------------------------------------------------------------------------- v1.4 วางแผน + เดินตามเส้นทาง
function planOpts(g, m, purpose, extra) {
  const n = typeNum(g), ix = ixOf(m);
  return Object.assign({
    prof: PROFILE[n] || PROFILE[1], power: power(g, m), rage: raged(g), allowBreak: true, failed: ix.failed,
    goalR: purpose === "chase" ? 1.5 : 1.2, reachUp: 1, reachDown: purpose === "chase" ? 2 : 1,
    maxExpand: purpose === "chase" ? 3500 : 2200, pad: purpose === "chase" ? 14 : 10,
    walls: false,
  }, extra || {});
}
function planChase(g, m, p, now, extra) {
  const ix = ixOf(m);
  if (planning(g.id)) return true;
  const T = { x: p.location.x, y: p.location.y, z: p.location.z };
  ix.planAt = now;
  plan(g.id, g.dimension, g.location, T, planOpts(g, m, "chase", extra), (res) => onChasePlan(g, m, p, T, res, extra));
  return true;
}
function onChasePlan(g, m, p, T, res, extra) {
  const now = system.currentTick, ix = ixOf(m);
  if (!valid(g) || api.leaving.has(g.id) || (m.appeaseUntil && m.appeaseUntil > now)) return;
  const following = ix.mode === "follow" && ix.route && ix.route.purpose === "chase";
  if (!following && ix.mode !== "chase" && ix.mode !== "crawlchase") return;     // ระหว่างนี้ไปทำอย่างอื่นแล้ว
  if (res.ok) return startFollow(g, m, res.path, "chase", now, { target: p, T, topple: !!(extra && extra.topple), walls: !!(extra && extra.walls) });
  if (following) {
    if (!ix.route.hold) return;                        // วางแผนใหม่ไม่สำเร็จ: เดินเส้นเดิมต่อ
    ix.route = null;
  }
  chaseFallback(g, m, p, res, now, extra);
}
// ผีพังประตู/หน้าต่างไม่ไหว: มีโอกาสหันไปพังผนังแทน (ผีแรงเยอะมีโอกาสสูงกว่า) — สุ่มครั้งเดียวต่อการบุก
const WALL_CHANCE = { 1: 0.35, 2: 0.55, 3: 0.75 };
function wallsAllowed(g, m) {
  const ix = ixOf(m);
  if (ix.wallRoll === undefined) {
    ix.wallRoll = Math.random() < WALL_CHANCE[power(g, m)];
    if (ix.wallRoll) for (const q of playersNear(g.dimension, g.location, 28)) actionbar(q, pick(["§4มันพังประตูไม่ได้... §cมันหันไปทุบผนังแทน!", "§4ได้ยินเสียงทุบผนัง..."]));
  }
  return ix.wallRoll;
}
function chaseFallback(g, m, p, res, now, extra) {
  const ix = ixOf(m);
  const walls = !!(extra && extra.walls);
  if (ix.mode === "follow") {                          // กลับไปให้ AI ของเกมไล่ระหว่างคิดทางอื่น
    ix.route = null; release(g, m, now);
    if (canStand(g)) toStand(g, ix, now); else toCrawl(g, ix);
  }
  if (!valid(p)) return;
  const partial = res.partial && res.partial.length > 2 && res.gain >= 4;
  if (!walls) {
    // คิดไม่ทัน (ทางไกล/ซับซ้อน) แต่ยังไม่ได้แปลว่าไม่มีทาง: เดินเข้าไปใกล้ก่อน
    if (res.why === "limit" && partial) return startFollow(g, m, res.partial, "chase", now, { target: p, partial: true });
    // รอบแรกพังได้แค่ประตู/หน้าต่าง/ไม้กั้น แล้วไปไม่ได้:
    //  - ถ้าเคยลองพังแล้วไม่ไหว -> ลุ้นว่าจะไปทุบผนังแทนไหม (ไม่ได้ = ยอมแพ้ หายไป)
    //  - ถ้าไม่มีช่องให้พังเลย (กล่องปิดตาย ห้องใต้ดิน เสาสูง) -> ทุบผนัง/พื้นเข้าไป
    // v1.8.4: ถ้ามีประตู/หน้าต่างที่พังได้อยู่ใกล้เป้าหมาย ให้ไปพังตรงนั้นก่อนเสมอ (ไม่ข้ามไปขุดกำแพง)
    if ((ix.openTries || 0) < 6) {
      const oc = findBreach(g, p, m);
      if (oc.length) { ix.openTries = (ix.openTries || 0) + 1; return startApproach(g, m, oc, now); }
    }
    if (ix.failed.size > 0 && !wallsAllowed(g, m)) { ix.failedAll = true; return giveUp(g, m, now); }
    return planChase(g, m, p, now, { walls: true });
  }
  // รอบทุบผนังได้แล้วก็ยังไปไม่ถึง: เข้าใกล้ให้มากที่สุดก่อน
  if (partial) return startFollow(g, m, res.partial, "chase", now, { target: p, partial: true, walls: true });
  // ผู้เล่นต่อเสาหนีขึ้นไปสูงเกินกว่าจะปีน: ไปยืนใต้เสา แล้วทุบบล็อกใต้เท้าทีละก้อน
  if (!(extra && extra.topple) && p.location.y > g.location.y + 1.5) return planChase(g, m, p, now, { walls: true, topple: true, reachUp: 64, goalR: 1.5 });
  // ทางสำรองแบบเดิม: หาประตู/หน้าต่างใกล้ผู้เล่น
  const cands = findBreach(g, p, m);
  if (cands.length) return startApproach(g, m, cands, now);
  ix.planFails = (ix.planFails || 0) + 1;
  if (ix.planFails >= 3 || ix.failed.size > 0) { ix.failedAll = true; giveUp(g, m, now); }
}
// ระบบแอบ: ให้ผีที่กำลังเดินหา เดินตามเส้นทางจริงไปยังจุด goal (พังเข้าไปได้ถ้าจำเป็น)
export function followTo(g, m, goal, now, o = {}) {
  const ix = ixOf(m);
  cancelPlan(g.id);
  ix.mode = "follow"; ix.route = null;
  register(g, m, now);
  const G = { x: goal.x, y: goal.y, z: goal.z };
  const walls = !!o.walls || ix.wallRoll === true;
  plan(g.id, g.dimension, g.location, G, planOpts(g, m, "search", { allowBreak: o.allowBreak !== false, walls }), (res) => {
    const t = system.currentTick;
    if (!valid(g) || ix.mode !== "follow" || ix.route) return;
    if (!m.search) return resumeChase(g, m, t);
    if (res.ok) return startFollow(g, m, res.path, "search", t, { goal: G, rush: !!o.rush, walls });
    // ไปไม่ได้โดยพังแค่ประตู/หน้าต่าง: ไม่มีช่องเลย หรือพังช่องไม่ไหวแล้วสุ่มได้ว่าจะทุบผนัง -> ลองอีกรอบแบบทุบผนังได้
    if (!walls && (ix.failed.size === 0 || wallsAllowed(g, m))) return followTo(g, m, G, t, Object.assign({}, o, { walls: true }));
    release(g, m, t);
    ix.mode = "search";
    try { api.searchFollowDone(g, m, t, false); } catch (_) {}
  });
  return true;
}
function startFollow(g, m, pts, purpose, now, o = {}) {
  const ix = ixOf(m);
  if (!pts || pts.length < 2) {
    if (purpose === "search") { release(g, m, now); ix.mode = "search"; try { api.searchFollowDone(g, m, now, true); } catch (_) {} return; }
    return resumeChase(g, m, now);
  }
  // ผีอาจเดินต่อไประหว่างคิด: เริ่มจากจุดบนเส้นทางที่ใกล้ตัวที่สุด
  const L = g.location;
  let bi = 0, bd = 1e9;
  for (let i = 0; i < Math.min(pts.length - 1, 8); i++) {
    const d = Math.hypot(pts[i].x + 0.5 - L.x, (pts[i].y - L.y) * 1.5, pts[i].z + 0.5 - L.z);
    if (d < bd) { bd = d; bi = i; }
  }
  const old = ix.route;
  ix.route = {
    pts, i: bi, purpose, target: o.target || null, T: o.T || null, goal: o.goal || null, topple: !!o.topple, partial: !!o.partial, walls: !!o.walls,
    t0: now, segT: now, nextCheck: now + 30, wp: null, w: 0, pose: old && old.pose, rush: !!o.rush, fails: old ? old.fails : 0, fdx: 0, fdz: 1, stepAt: 0,
    join: bd > 0.9 ? { x: pts[bi].x + 0.5, y: pts[bi].y, z: pts[bi].z + 0.5 } : null,
  };
  ix.mode = "follow"; ix.contAt = 0; ix.squeezeAt = 0; ix.lowBash = false;
  register(g, m, now);
  if (!old) trigger(g, "ghost:hold");
  if (purpose === "chase" && !old) {
    for (const p of playersNear(g.dimension, g.location, 24)) if (Math.random() < 0.5 && !(api.isHidden && api.isHidden(p))) actionbar(p, pick(["§8...มันกำลังหาทางเข้ามา...", "§8...มันรู้ทาง...", "§4...มันไม่ยอมแพ้..."]));
  }
  nextSegment(g, m, ix, now);
}
const ctr = (c, y) => ({ x: c.x + 0.5, y: y ?? c.y, z: c.z + 0.5 });
// พื้นที่เป็นแผ่นครึ่งบล็อก (slab ล่าง): ยืนต่ำลงครึ่งบล็อก ไม่ลอย
function floorAt(dim, c) {
  const b = getBlock(dim, c.x, c.y - 1, c.z), id = blockId(b);
  if (/slab/.test(id) && !/double/.test(id)) {
    let top = false;
    try { const s = b.permutation; top = s.getState("minecraft:vertical_half") === "top" || s.getState("top_slot_bit") === true; } catch (_) {}
    if (!top) return c.y - 0.5;
  }
  return c.y;
}
function nextSegment(g, m, ix, now) {
  const R = ix.route, dim = g.dimension;
  if (R.join) {                                       // เดินกลับเข้าเส้นทางก่อน
    R.wp = [Object.assign({ k: "walk" }, R.join)]; R.join = null; R.w = 0; R.segT = now;
    return startWaypoint(g, ix, now);
  }
  if (R.i >= R.pts.length - 1) return finishFollow(g, m, now, true);
  const A = R.pts[R.i], B = R.pts[R.i + 1];
  R.fdx = Math.sign(B.x - A.x); R.fdz = Math.sign(B.z - A.z);
  // 1) ต้องพังอะไรก่อนไหม
  if (B.brk) {
    for (const c of B.brk) {
      const b = getBlock(dim, c[0], c[1], c[2]);
      if (!b || passable(b)) continue;
      return startRouteBreak(g, m, ix, A, B, c, b, now);
    }
  }
  // 2) ทางยังโล่งอยู่ไหม (ผู้เล่นอาจวางบล็อกขวางเพิ่ม)
  if (!stepClear(dim, A, B)) return replanFollow(g, m, now, "blocked");
  R.wp = waypoints(A, B); R.w = 0; R.segT = now;
  const last = R.wp[R.wp.length - 1];
  if (last.k === "walk" || last.k === "fall") last.y = floorAt(dim, B);
  startWaypoint(g, ix, now);
}
function stepClear(dim, A, B) {
  const free = (x, y, z) => passable(getBlock(dim, x, y, z));
  if (B.a === "squeeze") { const v = B.via; return free(v[0], v[1], v[2]) && free(B.x, v[1], B.z) && free(B.x, B.y, B.z); }
  if (!free(B.x, B.y, B.z) || !free(B.x, B.y + 1, B.z)) return false;
  if (B.a === "climb" || B.a === "step") { for (let y = A.y + 2; y <= B.y + 1; y++) if (!free(A.x, y, A.z)) return false; }
  return true;
}
function waypoints(A, B) {
  const dx = Math.sign(B.x - A.x), dz = Math.sign(B.z - A.z);
  switch (B.a) {
    case "drop": return [{ x: A.x + 0.5 + dx * 0.78, y: A.y, z: A.z + 0.5 + dz * 0.78, k: "walk" }, Object.assign(ctr(B), { k: "fall" })];
    case "climb": return [Object.assign(ctr(A), { k: "walk" }), Object.assign(ctr(A, B.y + 0.1), { k: "climb" }), Object.assign(ctr(B), { k: "walk" })];
    case "squeeze": {
      const v = B.via;
      return [{ x: v[0] + 0.5 - dx * 0.6, y: A.y, z: v[2] + 0.5 - dz * 0.6, k: "walk" }, { x: v[0] + 0.5, y: v[1], z: v[2] + 0.5, k: "squeeze" },
        { x: B.x + 0.5, y: v[1], z: B.z + 0.5, k: "squeeze" }, Object.assign(ctr(B), { k: "fall" })];
    }
    case "ladder": return [Object.assign(ctr(B), { k: "ladder" })];
    case "swim": return [Object.assign(ctr(B), { k: "swim" })];
    case "dig": return [Object.assign(ctr(B), { k: "fall" })];
    default: return [Object.assign(ctr(B), { k: "walk" })];     // walk, step
  }
}
// ท่าทางระหว่างเดินตามเส้นทาง: ยืน/คลาน (ตามความสูงเพดาน) / ปีน / มุด
function applyPose(g, ix, pose) {
  const R = ix.route;
  if (R.pose === pose) return;
  R.pose = pose;
  const search = R.purpose === "search";
  const ev = pose === "stand" ? (search ? "ghost:search" : "ghost:follow_run")
    : pose === "crawl" ? (search ? "ghost:crawl_search" : "ghost:follow_crawl")
    : pose === "climb" ? "ghost:climb" : "ghost:squeeze";
  trigger(g, ev);
  ix.crawling = pose !== "stand";
}
function startWaypoint(g, ix, now) {
  const R = ix.route, wp = R.wp[R.w];
  if (wp.k === "climb" || wp.k === "ladder") return applyPose(g, ix, "climb");
  if (wp.k === "squeeze") return applyPose(g, ix, "squeeze");
  const L = g.location;
  const ok = fitsStand(g, fl(L.x), fl(L.y + 0.05), fl(L.z)) && fitsStand(g, fl(wp.x), fl(wp.y + 0.05), fl(wp.z));
  if (ok && R.pose !== "stand") {
    // ลุกขึ้นยืนเมื่อข้างหน้าโล่งพอสักระยะ (กันสลับท่าไปมา)
    const nx = R.pts[Math.min(R.i + 2, R.pts.length - 1)];
    if (!nx || fitsStand(g, nx.x, nx.y, nx.z)) applyPose(g, ix, "stand"); else applyPose(g, ix, "crawl");
  } else if (!ok) applyPose(g, ix, "crawl");
  else if (!R.pose) applyPose(g, ix, "stand");
}
function followSpeed(g, ix, k) {
  const R = ix.route, P = PROFILE[typeNum(g)] || PROFILE[1];
  const search = R.purpose === "search", rush = R.rush ? 1.9 : 1;
  const rg = raged(g) ? 1.35 : 1;
  switch (k) {
    case "climb": return (search ? 0.08 : 0.11) * rg;
    case "ladder": return search ? 0.08 : 0.12;
    case "squeeze": return (search ? 0.06 : 0.08) * rg;
    case "swim": return (search ? 0.08 : 0.13) * P.spd;
    case "fall": return 0.34;
    default:
      if (R.pose === "stand") return (search ? 0.105 * rush : 0.26 * rg) * P.spd;
      return (search ? 0.075 * rush : 0.17 * rg) * P.spd;
  }
}
function tickFollow(g, m, ix, now) {
  const R = ix.route;
  if (!R) {                                           // ยังคิดเส้นทางอยู่ (ระบบแอบ)
    if (planning(g.id)) {
      if (!m.search) { cancelPlan(g.id); return resumeChase(g, m, now); }
      if (api.searchFollowTick && api.searchFollowTick(g, m, now) === "stop") { cancelPlan(g.id); if (ix.mode === "follow") resumeChase(g, m, now); else active.delete(g.id); }
      return;
    }
    return resumeChase(g, m, now);                     // ไม่มีเส้นทางและไม่ได้คิดอยู่: กลับไปไล่ตามปกติ
  }
  if (R.purpose === "search") {
    if (api.searchFollowTick && api.searchFollowTick(g, m, now) === "stop") {
      if (ix.route === R) ix.route = null;
      cancelPlan(g.id);
      if (ix.mode === "follow") resumeChase(g, m, now); else active.delete(g.id);
      return;
    }
  } else if (chaseFollowCheck(g, m, ix, now)) return;
  if (R.hold) {                                        // รอเส้นทางใหม่: ยืนจ้อง ส่งเสียงขู่
    if (now % 10 === 0 && R.target && valid(R.target)) face(g, g.location, { x: R.target.location.x, y: R.target.location.y + 1.5, z: R.target.location.z });
    if (now - R.holdAt > 200) { ix.route = null; resumeChase(g, m, now); }
    return;
  }
  if (!R.wp) return nextSegment(g, m, ix, now);
  const wp = R.wp[R.w], L = g.location;
  const sp = followSpeed(g, ix, wp.k);
  const dx = wp.x - L.x, dy = wp.y - L.y, dz = wp.z - L.z, d = Math.hypot(dx, dy, dz);
  let P, reached = false;
  if (d <= sp + 1e-3) { P = { x: wp.x, y: wp.y, z: wp.z }; reached = true; }
  else { const f = sp / d; P = { x: L.x + dx * f, y: L.y + dy * f, z: L.z + dz * f }; }
  // หันหน้า: ปีน = หันเข้าผนัง, อื่น ๆ = หันไปทางที่เดิน
  let hx, hz;
  if (wp.k === "climb" || wp.k === "ladder") { hx = R.fdx; hz = R.fdz; if (!hx && !hz) { const r = g.getRotation ? g.getRotation().y : 0; hx = -Math.sin(r * Math.PI / 180); hz = Math.cos(r * Math.PI / 180); } }
  else {
    hx = wp.x - P.x; hz = wp.z - P.z;
    if (Math.hypot(hx, hz) < 0.08) { const nw = R.wp[R.w + 1]; if (nw) { hx = nw.x - P.x; hz = nw.z - P.z; } else { hx = R.fdx; hz = R.fdz; } }
  }
  const hl = Math.hypot(hx, hz) || 1;
  const eye = R.pose === "stand" ? Math.min(heightOf(g) * 0.75, 3.2) : 0.8;
  face(g, P, { x: P.x + (hx / hl) * 4, y: P.y + eye, z: P.z + (hz / hl) * 4 });
  if (now >= R.stepAt) {
    R.stepAt = now + (R.pose === "stand" ? (R.purpose === "search" ? 11 : 7) : 9);
    const id = wp.k === "climb" || wp.k === "ladder" ? "mob.spider.step" : R.pose === "stand" ? "mob.zombie.step" : "mob.spider.step";
    sound(g.dimension, id, P, R.purpose === "search" ? 0.7 : 1.1, rand(0.45, 0.65), R.purpose === "search" ? 20 : 28);
    if (wp.k === "climb" && Math.random() < 0.5) sound(g.dimension, "ghost.scratch", P, 1.0, rand(0.7, 0.9), 20);
  }
  if (reached) {
    R.w++;
    if (R.w >= R.wp.length) { R.i++; R.wp = null; R.fails = Math.max(0, R.fails - 1); return nextSegment(g, m, ix, now); }
    return startWaypoint(g, ix, now);
  }
  if (now - R.segT > 220) replanFollow(g, m, now, "slow");
}
function chaseFollowCheck(g, m, ix, now) {
  const R = ix.route, p = R.target;
  if (!p || !valid(p) || p.dimension.id !== g.dimension.id) { finishFollow(g, m, now, false); return true; }
  const hid = api.isHidden ? api.isHidden(p) : false;
  const d = dist(g.location, p.location);
  const k = R.wp && R.wp[R.w] ? R.wp[R.w].k : "walk";
  const busy = k === "climb" || k === "squeeze" || k === "fall" || k === "ladder" || !grounded(g);   // ห้ามปล่อยกลางอากาศ/กลางช่อง
  if (!hid && !busy && d < 2.6 && Math.abs(p.location.y - g.location.y) < 1.8) { finishFollow(g, m, now, true); return true; }
  if (now >= R.nextCheck && !R.hold) {
    R.nextCheck = now + 30;
    // ผู้เล่นหนีไปไกลจากจุดหมายเดิม: คิดเส้นทางใหม่เบื้องหลัง (เดินเส้นเดิมไปก่อน)
    if (!hid && !R.topple && R.T && dist(p.location, R.T) > 3.5 && !planning(g.id)) planChase(g, m, p, now, R.walls ? { walls: true } : undefined);
  }
  return false;
}
function grounded(g) {
  const L = g.location, dim = g.dimension;
  const b = getBlock(dim, L.x, L.y - 0.05, L.z), here = getBlock(dim, L.x, L.y + 0.05, L.z);
  if (b && !passable(b)) return true;
  try { if (here && (here.isLiquid || /ladder|vine|scaffolding/.test(here.typeId))) return true; } catch (_) {}
  return false;
}
function finishFollow(g, m, now, ok) {
  const ix = ixOf(m), R = ix.route;
  ix.route = null;
  cancelPlan(g.id);
  if (R && R.purpose === "search") {
    release(g, m, now);
    ix.mode = "search";
    try { api.searchFollowDone(g, m, now, ok); } catch (_) {}
    return;
  }
  if (R && R.topple && ok && R.target && valid(R.target) && startTopple(g, m, R.target, now)) return;
  resumeChase(g, m, now);
}
function replanFollow(g, m, now, why) {
  const ix = ixOf(m), R = ix.route;
  if (!R) return resumeChase(g, m, now);
  R.fails++; R.wp = null;
  if (R.fails > 4) return finishFollow(g, m, now, false);
  if (R.purpose === "search") {
    const goal = R.goal;
    if (!goal) return finishFollow(g, m, now, false);
    ix.route = null;
    return followTo(g, m, goal, now, { rush: R.rush, walls: R.walls });
  }
  const p = R.target;
  if (!p || !valid(p)) return finishFollow(g, m, now, false);
  R.hold = true; R.holdAt = now;
  cancelPlan(g.id);
  planChase(g, m, p, now, R.walls ? { walls: true } : undefined);
}
// ทุบเสาใต้เท้าผู้เล่น (ผู้เล่นต่อเสาหนีสูงเกินปีน)
function startTopple(g, m, p, now) {
  const dim = g.dimension, P = p.location;
  const cell = { x: fl(P.x), y: fl(P.y - 0.05) - 1, z: fl(P.z) };
  if (Math.hypot(cell.x + 0.5 - g.location.x, cell.z + 0.5 - g.location.z) > 3.2) return false;
  if (cell.y - g.location.y > heightOf(g) + 1) return false;          // สูงเกินเอื้อมถึง: รออยู่ข้างล่าง
  const b = getBlock(dim, cell.x, cell.y, cell.z);
  if (!b || !breakable(b)) return false;
  const ix = ixOf(m), dx = cell.x + 0.5 - g.location.x, dz = cell.z + 0.5 - g.location.z;
  const dirv = Math.abs(dx) > Math.abs(dz) ? { x: Math.sign(dx), z: 0 } : { x: 0, z: Math.sign(dz) || 1 };
  ix.crawling = !canStand(g);
  for (const q of playersNear(dim, g.location, 20)) actionbar(q, pick(["§4มันกำลังทุบเสาที่คุณยืนอยู่!", "§8...ลงมาสิ..."]));
  startDigAt(g, m, cell, dirv, now);
  return true;
}
function startRouteBreak(g, m, ix, A, B, c, b, now) {
  const dim = g.dimension, R = ix.route;
  let dir = { x: Math.sign(B.x - A.x), z: Math.sign(B.z - A.z) };
  if (dir.x && dir.z) dir = { x: dir.x, z: 0 };
  if (!dir.x && !dir.z) dir = { x: R.fdx || 0, z: R.fdz || (R.fdx ? 0 : 1) };
  const low = !fitsStand(g, A.x, A.y, A.z);
  ix.crawling = low; ix.lowBash = low;
  const info = classify(b);
  if (info) {
    const pos = basePos(dim, b, info);
    ix.cand = { pos, info, dir, stand: { x: A.x, y: A.y, z: A.z }, inside: { x: B.x, y: B.y, z: B.z }, key: keyOf(dim.id, pos), route: true };
    ix.alt = [];
    return startBash(g, m, now);
  }
  const cell = { x: c[0], y: c[1], z: c[2] };
  ix.cand = { dig: true, pos: cell, dir, stand: { x: A.x, y: A.y, z: A.z }, info: null, key: keyOf(dim.id, cell), route: true };
  // ผนังข้างหน้า (ไม่ใช่พื้น/เพดาน): ถอยให้มือถึงพอดีถ้ายืนได้
  if (!low && cell.y >= A.y && cell.y <= A.y + 1 && (cell.x !== A.x || cell.z !== A.z)) face(g, reachPoint(dim, ix.cand, BASH_REACH[typeNum(g)] || 3.5), center(cell));
  else face(g, ctr(A), center(cell));
  return startDigAt(g, m, cell, dir, now);
}
function continueFollow(g, m, now) {
  const ix = ixOf(m);
  if (!ix.route) return resumeChase(g, m, now);
  ix.mode = "follow"; ix.contAt = 0; ix.squeezeAt = 0; ix.lowBash = false;
  ix.route.pose = null; ix.route.wp = null; ix.route.hold = false;
  register(g, m, now);
  trigger(g, "ghost:hold");
  nextSegment(g, m, ix, now);
}

// ---------------------------------------------------------------------------------- เดินไปที่ช่อง (ทางสำรอง / หลังเคาะประตู)
function startApproach(g, m, cands, now) {
  const ix = ixOf(m);
  ix.cand = cands[0]; ix.alt = cands.slice(1); ix.mode = "approach"; ix.t0 = now; ix.walkFail = 0; ix.route = null; ix.lowBash = false;
  register(g, m, now);
  trigger(g, "ghost:hold");
  setState(g, ix.crawling ? 7 : 2);
}
function approachPoint(g, c) { return c.dig ? standPoint(c) : bashPoint(g, c); }
function tickApproach(g, m, ix, now) {
  const c = ix.cand, dim = g.dimension;
  const b = getBlock(dim, c.pos.x, c.pos.y, c.pos.z);
  if (c.dig) {
    if (passable(b)) return resumeChase(g, m, now);
  } else {
    const info = classify(b);
    if (!info || ((info.kind === "door" || info.kind === "gate") && isOpen(b))) {
      const T0 = standPoint(c);
      if (passable(b) && Math.hypot(T0.x - g.location.x, T0.z - g.location.z) < 4.5) return startSqueeze(g, m, now);
      return resumeChase(g, m, now);           // ช่องเปิดเองแล้ว (ผู้เล่นเปิดประตู) -> ปล่อยให้ตามเข้าไปเอง
    }
  }
  const T = approachPoint(g, c), L = g.location, d = Math.hypot(T.x - L.x, T.z - L.z);
  if (d < 0.3 && Math.abs(L.y - T.y) < 1.3) return c.dig ? startDigAt(g, m, c.pos, c.dir, now) : startBash(g, m, now);
  if (now - ix.t0 > 240 || ix.walkFail > 6) return phaseTo(g, m, T, now);
  const step = Math.min(ix.crawling ? 0.16 : 0.22, d);
  const nx = L.x + ((T.x - L.x) / d) * step, nz = L.z + ((T.z - L.z) / d) * step;
  const cell = standCell(dim, nx, nz, fl(L.y) + 1, 3, ix.crawling ? 1 : 2);
  if (!cell) { ix.walkFail++; return; }
  face(g, { x: nx, y: cell.y, z: nz }, { x: T.x, y: cell.y + 1.6, z: T.z });
}
function phaseTo(g, m, T, now) {
  const ix = ixOf(m), dim = g.dimension;
  const watched = playersNear(dim, g.location, 48).some((p) => lookDotTo(p, g.location) > 0.82);
  if (watched && now - ix.t0 < 320) return;
  smoke(dim, g.location);
  sound(dim, "mob.warden.nearby_close", g.location, 1, 0.5, 32);
  face(g, T, lookPoint(ix.cand));
  smoke(dim, T);
  if (ix.cand.dig) startDigAt(g, m, ix.cand.pos, ix.cand.dir, now); else startBash(g, m, now);
}

// ---------------------------------------------------------------------------------- ทุบประตู/หน้าต่าง/ไม้กั้น
function startBash(g, m, now) {
  const ix = ixOf(m);
  ix.mode = "bash"; ix.t0 = now; ix.blocked = 0; ix.squeezeAt = 0; ix.contAt = 0;
  register(g, m, now);
  const rg = raged(g);
  ix.period = rg ? 20 : 28;
  ix.nextHit = now + (rg ? 13 : 17);
  trigger(g, ix.lowBash ? "ghost:stuck" : "ghost:bash");          // เพดานต่ำ: ก้มคลานตะกุยแทนการเงื้อทุบ
  face(g, bashPoint(g, ix.cand), lookPoint(ix.cand));
  growl(g.dimension, g.location, true);
}
function afterOpened(g, m, now) { const ix = ixOf(m); return ix.route ? continueFollow(g, m, now) : startSqueeze(g, m, now); }
function tickBash(g, m, ix, now) {
  const c = ix.cand, dim = g.dimension;
  if (ix.contAt) { if (now >= ix.contAt) continueFollow(g, m, now); return; }
  if (ix.squeezeAt) { if (now >= ix.squeezeAt) startSqueeze(g, m, now); return; }
  const b = getBlock(dim, c.pos.x, c.pos.y, c.pos.z), info = classify(b);
  if (!info) return passable(b) ? afterOpened(g, m, now) : (ix.route ? continueFollow(g, m, now) : resumeChase(g, m, now));
  if ((info.kind === "door" || info.kind === "gate") && isOpen(b)) return afterOpened(g, m, now);
  if (now % 5 === 0) face(g, bashPoint(g, c), lookPoint(c));
  if (now >= ix.nextHit) {
    ix.nextHit = now + ix.period;
    const r = hitOpening(dim, c.pos, power(g, m), raged(g), c.dir);
    if (r.res === "broken") { if (ix.route) ix.contAt = now + 8; else ix.squeezeAt = now + 7; growl(dim, g.location, true); return; }
    if (r.res === "gone") return afterOpened(g, m, now);
    if (r.res === "blocked" && ++ix.blocked >= 2) return failTarget(g, m, now);
    if (Math.random() < 0.25) growl(dim, g.location, false);
    if (Math.random() < 0.15) for (const p of playersNear(dim, g.location, 16)) actionbar(p, pick(["§4มันกำลังพังเข้ามา", "§c...ปัง!", "§4วิ่ง!", "§8...ประตูจะไม่ไหวแล้ว..."]));
  }
  if (now - ix.t0 > 1500) failTarget(g, m, now);
}
function failTarget(g, m, now) {
  const ix = ixOf(m), dim = g.dimension;
  ix.failed.add(ix.cand.key);
  const cp = ix.cand.pos;                                   // ช่องเดียวกันที่ซ้อนบน/ล่าง (ไม้กั้น 2 ชั้น) ถือว่าล้มเหลวด้วย
  for (const dy of [-2, -1, 1, 2]) ix.failed.add(keyOf(dim.id, { x: cp.x, y: cp.y + dy, z: cp.z }));
  sound(dim, "mob.ravager.roar", g.location, 2, 0.45, 64);
  growl(dim, g.location, true);
  if (ix.route) { ix.mode = "follow"; return replanFollow(g, m, now, "failed"); }   // พังไม่ไหว: หาทางอื่น (ไม่นับจุดนี้)
  const rest = (ix.alt || []).filter((c) => !ix.failed.has(c.key));
  if (rest.length) { startApproach(g, m, rest, now); return; }
  let p = ix.player && valid(ix.player) ? ix.player : null;
  if (!p) { let bd = 40; for (const q of playersNear(dim, g.location, 40)) { const d = dist(q.location, g.location); if (d < bd) { bd = d; p = q; } } }
  if (p && wallsAllowed(g, m)) {
    release(g, m, now);
    if (canStand(g)) toStand(g, ix, now); else toCrawl(g, ix);
    planChase(g, m, p, now, { walls: true });
    return;
  }
  ix.failedAll = true;
  giveUp(g, m, now);
}
function giveUp(g, m, now) {
  const dim = g.dimension, loc = g.location;
  for (const p of playersNear(dim, loc, 32)) actionbar(p, "§8...มันพังเข้ามาไม่ได้... §4คืนนี้");
  sound(dim, "mob.ghast.scream", loc, 2, 0.4, 64);
  active.delete(g.id);
  system.runTimeout(() => { try { if (valid(g)) api.vanish(g); } catch (_) {} }, 30);
}

// ---------------------------------------------------------------------------------- มุดเข้ามา
function startSqueeze(g, m, now) {
  const ix = ixOf(m), c = ix.cand;
  ix.mode = "squeeze"; ix.t0 = now; ix.squeezeAt = 0;
  register(g, m, now);
  trigger(g, "ghost:squeeze");
  const L = g.location, oy = c.pos.y;
  ix.path = [
    { x: L.x, y: L.y, z: L.z },
    { x: c.pos.x + 0.5, y: oy, z: c.pos.z + 0.5 },
    { x: c.pos.x + 0.5 + c.dir.x * 1.15, y: oy, z: c.pos.z + 0.5 + c.dir.z * 1.15 },
  ];
  ix.seg = 0; ix.u = 0;
  const small = !c.info || c.info.kind !== "door";
  if (ix.stuckDone === undefined) ix.stuckDone = false;
  ix.willStick = !ix.stuckDone && Math.random() < (small ? 0.7 : 0.35) && heightOf(g) > 2.05;   // ตัวเล็กไม่ติด
  sound(g.dimension, "ghost.snap", g.location, 1.6, rand(0.7, 0.9), 32);
}
function tickSqueeze(g, m, ix, now) {
  const dim = g.dimension, P = ix.path;
  const a = P[ix.seg], b = P[ix.seg + 1];
  const Ls = Math.max(0.2, Math.hypot(b.x - a.x, b.z - a.z) + Math.abs(b.y - a.y));
  // ช่วงแรก (คลานเข้าหาช่องจากจุดที่ยืนทุบ) เร็วกว่า ช่วงมุดผ่านช่องช้า ๆ
  ix.u += (ix.seg === 0 ? (raged(g) ? 0.2 : 0.15) : (raged(g) ? 0.1 : 0.07)) / Ls;
  if (ix.u >= 1) {
    ix.seg++; ix.u = 0;
    if (ix.seg >= P.length - 1) return finishSqueeze(g, m, now);
  }
  const A = P[ix.seg], B = P[ix.seg + 1];
  const loc = { x: A.x + (B.x - A.x) * ix.u, y: A.y + (B.y - A.y) * ix.u, z: A.z + (B.z - A.z) * ix.u };
  const end = P[P.length - 1];
  face(g, loc, { x: end.x + ix.cand.dir.x * 3, y: loc.y + 0.6, z: end.z + ix.cand.dir.z * 3 });
  if (now % 9 === 0) sound(dim, Math.random() < 0.5 ? "ghost.snap" : "dig.gravel", loc, 1.3, rand(0.5, 0.9), 24);
  if (now % 6 === 0) particle(dim, "ghost:dust", { x: loc.x, y: loc.y + 0.6, z: loc.z });
  if (ix.willStick && ix.seg === 1 && ix.u > 0.08) startStuck(g, m, now);
}
function finishSqueeze(g, m, now) {
  const ix = ixOf(m), dim = g.dimension, L = g.location;
  const floor = standCell(dim, L.x, L.z, fl(L.y), 4, 1);
  if (floor && floor.y < fl(L.y)) { try { g.teleport({ x: L.x, y: floor.y, z: L.z }); } catch (_) {} }
  ix.inside = true; ix.triedCrawl = true; ix.failed.clear(); ix.failedAll = false; ix.stuckDone = undefined; ix.planFails = 0; ix.wallRoll = undefined;
  growl(dim, g.location, true);
  for (const p of playersNear(dim, g.location, 20)) {
    actionbar(p, pick(["§4§lมันเข้ามาแล้ว", "§4§lมันอยู่ในบ้าน", "§c§lวิ่ง!"]));
    try { p.runCommand("camerashake add @s 0.6 0.8 positional"); } catch (_) {}
  }
  resumeChase(g, m, now);
}

// ---------------------------------------------------------------------------------- ตัวติด: ทุบกรอบออกช้า ๆ
function frameCells(dim, c) {
  if (c.info && c.info.kind === "door") return [];         // ประตู: ดิ้นแต่ไม่ทุบกรอบ (พังแค่ประตูเท่านั้น)
  const perp = c.dir.x !== 0 ? { x: 0, z: 1 } : { x: 1, z: 0 };
  const top = c.info && c.info.kind === "door" ? 2 : 1;
  const P = c.pos, cand = [{ x: P.x, y: P.y + top, z: P.z }];
  for (const s of [-1, 1]) {
    for (let h = 0; h <= top; h++) cand.push({ x: P.x + perp.x * s, y: P.y + h, z: P.z + perp.z * s });
  }
  const ok = cand.filter((q) => breakable(getBlock(dim, q.x, q.y, q.z)));
  return ok.slice(0, 2 + Math.floor(Math.random() * 2));
}
function startStuck(g, m, now) {
  const ix = ixOf(m), dim = g.dimension;
  ix.mode = "stuck"; ix.t0 = now; ix.stuckDone = true; ix.willStick = false;
  register(g, m, now);
  trigger(g, "ghost:stuck");
  ix.frame = frameCells(dim, ix.cand); ix.fi = 0; ix.fh = 0; ix.nextHit = now + 18;
  ix.hold = { x: g.location.x, y: g.location.y, z: g.location.z };
  sound(dim, "mob.ghast.scream", g.location, 2.2, 0.45, 48);
  shake(dim, g.location, 12, 0.5, 0.6);
  for (const p of playersNear(dim, g.location, 18)) actionbar(p, pick(["§8...มันตัวติด...", "§4มันกำลังดิ้นออกมา", "§8...มันทุบกรอบหน้าต่าง..."]));
}
function tickStuck(g, m, ix, now) {
  const dim = g.dimension, c = ix.cand, H = ix.hold;
  face(g, { x: H.x + rand(-0.04, 0.04), y: H.y, z: H.z + rand(-0.04, 0.04) }, { x: H.x + c.dir.x * 4, y: H.y + 0.5, z: H.z + c.dir.z * 4 });
  if (now % 23 === 0) sound(dim, Math.random() < 0.5 ? "ghost.growl" : "ghost.snap", H, 1.8, rand(0.5, 0.8), 40);
  if (now >= ix.nextHit) {
    ix.nextHit = now + (raged(g) ? 10 : 15);
    if (ix.fi >= ix.frame.length) { if (now - ix.t0 > 50) { ix.mode = "squeeze"; trigger(g, "ghost:squeeze"); } return; }
    const q = ix.frame[ix.fi], b = getBlock(dim, q.x, q.y, q.z);
    if (!breakable(b)) { ix.fi++; ix.fh = 0; return; }
    ix.fh++;
    const need = digHits(b, power(g, m), raged(g));
    const cq = center(q);
    const id = blockId(b);
    sound(dim, /(plank|log|wood|stem)/.test(id) ? "ghost.bang.wood" : "ghost.bang.stone", cq, 2, rand(0.8, 1.05), 48);
    particle(dim, "ghost:dust", cq, dirVars(c.dir.x, c.dir.z));
    particle(dim, "ghost:debris", cq, dirVars(c.dir.x, c.dir.z));
    shake(dim, cq, 12, 0.35, 0.3);
    if (ix.fh >= need) { clearCrack(dim, q); destroyBlock(dim, q); ix.fi++; ix.fh = 0; }
    else crackGeneric(dim, q, ix.fh / need);
  }
  if (now - ix.t0 > 400) { ix.mode = "squeeze"; trigger(g, "ghost:squeeze"); }
}

// ---------------------------------------------------------------------------------- ทุบบล็อกทั่วไป (ผนัง/พื้น/เพดาน ที่อยู่บนเส้นทาง หรือทางสำรอง)
function startDig(g, m, p, now) {
  const ix = ixOf(m), dim = g.dimension, L = g.location;
  const dx = p.location.x - L.x, dz = p.location.z - L.z, d = Math.hypot(dx, dz) || 1, ux = dx / d, uz = dz / d;
  const hgt = ix.crawling ? 2 : 3;
  const dirv = Math.abs(ux) > Math.abs(uz) ? { x: Math.sign(ux), z: 0 } : { x: 0, z: Math.sign(uz) };
  let cell = null, cb = null, before = null;
  for (let s = 0.6; s < Math.min(d, 24); s += 0.5) {
    const x = fl(L.x + ux * s), z = fl(L.z + uz * s);
    let hit = null;
    for (let h = 0; h < hgt; h++) {
      const b = getBlock(dim, x, fl(L.y) + h, z);
      if (b && !passable(b)) { hit = { x, y: fl(L.y) + h, z, b }; break; }
    }
    if (hit) { cell = { x: hit.x, y: hit.y, z: hit.z }; cb = hit.b; break; }
    before = { x: L.x + ux * Math.max(0, s - 0.9), z: L.z + uz * Math.max(0, s - 0.9) };
  }
  if (!cell) return false;
  const info = classify(cb);
  if (info) {
    const pos = basePos(dim, cb, info);
    const c = sidesOf(dim, pos, info, L, p.location) ||
      { stand: { x: fl(L.x), y: fl(L.y), z: fl(L.z) }, inside: { x: fl(p.location.x), y: fl(p.location.y), z: fl(p.location.z) },
        dir: dirv, pos, info, key: keyOf(dim.id, pos) };
    startApproach(g, m, [c], now);
    return true;
  }
  if (!breakable(cb)) { ix.digFails = (ix.digFails || 0) + 1; if (ix.digFails > 4) { ix.failedAll = true; giveUp(g, m, now); } return false; }
  const near = Math.hypot(cell.x + 0.5 - L.x, cell.z + 0.5 - L.z) < 2.4;
  if (near || !before) { startDigAt(g, m, cell, dirv, now); return true; }
  const sc = standCell(dim, before.x, before.z, fl(L.y) + 1, 3, 1) || { x: fl(before.x), y: fl(L.y), z: fl(before.z) };
  startApproach(g, m, [{ dig: true, stand: sc, pos: cell, dir: dirv, info: null, key: keyOf(dim.id, cell) }], now);
  return true;
}
function startDigAt(g, m, cell, dirv, now) {
  const ix = ixOf(m), dim = g.dimension;
  const b = getBlock(dim, cell.x, cell.y, cell.z);
  ix.mode = "dig"; ix.t0 = now; ix.dig = { cell, h: 0, need: digHits(b, power(g, m), raged(g)) }; ix.nextHit = now + 12;
  if (!ix.route) ix.cand = { pos: cell, dir: dirv, info: null };
  register(g, m, now);
  trigger(g, ix.crawling ? "ghost:stuck" : "ghost:bash");
}
function tickDig(g, m, ix, now) {
  const dim = g.dimension, q = ix.dig.cell, cq = center(q);
  if (now % 5 === 0) face(g, g.location, cq);
  const after = () => (ix.route ? continueFollow(g, m, now) : resumeChase(g, m, now));
  if (now >= ix.nextHit) {
    ix.nextHit = now + (raged(g) ? 11 : 16);
    const b = getBlock(dim, q.x, q.y, q.z);
    if (passable(b)) { clearCrack(dim, q); return after(); }
    if (!breakable(b)) {
      clearCrack(dim, q);
      if (ix.route) { ix.failed.add(keyOf(dim.id, q)); ix.mode = "follow"; return replanFollow(g, m, now, "unbreakable"); }
      return resumeChase(g, m, now);
    }
    ix.dig.h++;
    sound(dim, /(plank|log|wood|stem)/.test(blockId(b)) ? "ghost.bang.wood" : "ghost.bang.stone", cq, 2, rand(0.8, 1.05), 48);
    particle(dim, "ghost:dust", cq); particle(dim, "ghost:debris", cq);
    shake(dim, cq, 10, 0.3, 0.3);
    if (ix.dig.h >= ix.dig.need) { clearCrack(dim, q); destroyBlock(dim, q); return after(); }
    crackGeneric(dim, q, ix.dig.h / ix.dig.need);
  }
  if (now - ix.t0 > 400) after();
}

// ---------------------------------------------------------------------------------- เคาะประตู/หน้าต่างแล้วหายไป
function knockSound(dim, c, info) {
  const loc = lookPoint(c);
  if (info.fam === "glass") sound(dim, "ghost.knock.glass", loc, 1.8, rand(0.9, 1.1), 28);
  else if (info.fam === "iron" || info.fam === "copper") sound(dim, "ghost.knock.metal", loc, 1.8, rand(0.85, 1.05), 32);
  else if (info.fam === "stone") sound(dim, "ghost.bang.stone", loc, 0.7, rand(1.3, 1.5), 24);
  else sound(dim, "ghost.knock.wood", loc, 2.0, rand(0.9, 1.08), 32);
}
function spawnAt(p, type, c, kind) {
  const dim = p.dimension, now = system.currentTick;
  const T = kind === "watch" ? reachPoint(dim, c, (INSPECT_REACH[type] || 2.4) + 0.05) : knockPoint(dim, c, type);   // ผียืนจ้อง: เอาหน้าแนบกระจก
  let g;
  try { g = dim.spawnEntity(`ghost:ghost_${type}`, T); } catch (_) { return null; }
  const m = api.ensureMeta(g, now);
  const ix = ixOf(m);
  m.stalking = false; m.born = now - 200;
  trigger(g, kind === "knock" ? "ghost:knock" : kind === "watch" ? "ghost:inspect" : "ghost:bash");
  face(g, T, lookPoint(c));
  ix.cand = c; ix.alt = []; ix.player = p; ix.knockT = T; ix.route = null; ix.lowBash = false;
  return { g, m, ix, now };
}
// เคาะ (escalate = บางครั้งเคาะแล้วเริ่มพังเข้ามาจริง)
export function startKnock(p, type, escalate) {
  try {
    const cands = knockTargets(p.dimension, p);
    if (!cands.length) return false;
    const c = cands[Math.floor(Math.random() * Math.min(3, cands.length))];
    const s = spawnAt(p, type, c, "knock");
    if (!s) return false;
    const { g, m, ix, now } = s;
    ix.mode = "knock"; ix.t0 = now; ix.loop0 = now + 6; ix.end = now + Math.floor(rand(14, 26) * 20);
    ix.escalate = escalate ?? Math.random() < 0.2; ix.watch = 0;
    register(g, m, now);
    return true;
  } catch (_) { return false; }
}
// บุกพังเข้ามาเลย (เคาะแรง ๆ 2-3 ทีแล้วทุบ)
export function startBreakIn(p, type) {
  try {
    let cands = knockTargets(p.dimension, p);
    if (!cands.length) cands = knockTargets(p.dimension, p, true);   // ทดสอบ: ผ่อนเงื่อนไข (ยืนชิดประตูได้ / ด้านนอกมีกำแพงล้อมก็ได้)
    if (!cands.length) return false;
    const c = cands[0];
    const s = spawnAt(p, type, c, "knock");
    if (!s) return false;
    const { g, m, ix, now } = s;
    ix.mode = "knock"; ix.t0 = now; ix.loop0 = now + 4; ix.end = now + 70; ix.escalate = true; ix.watch = 0;
    register(g, m, now);
    return true;
  } catch (_) { return false; }
}
function knockVanish(g, m, p, why) {
  const dim = g.dimension, loc = g.location;
  active.delete(g.id);
  if (why === "seen" || why === "close") {
    try { api.jumpscare(p, why === "close"); } catch (_) {}
    try { g.remove(); } catch (_) {}
    return;
  }
  if (why === "open") {
    try { g.remove(); } catch (_) {}
    // เปิดประตูไป... ไม่มีใคร แต่ได้ยินเสียงหายใจอยู่ข้างหลัง
    system.runTimeout(() => {
      try {
        const v = p.getViewDirection(), l = p.location;
        sound(p.dimension, "ghost.breath", { x: l.x - v.x * 1.5, y: l.y + 1.4, z: l.z - v.z * 1.5 }, 1.2, 0.8, 8);
        actionbar(p, "§8...");
      } catch (_) {}
    }, 25);
    return;
  }
  if (why === "visit") {                    // v1.7 แค่มาเคาะ: เงียบหายไปเฉย ๆ เหลือแค่เสียงหายใจแผ่ว ๆ
    try { const c = m.ix.cand; sound(dim, "ghost.breath", lookPoint(c), 0.7, 0.75, 16); particle(dim, "minecraft:basic_smoke_particle", { x: loc.x, y: loc.y + 1.5, z: loc.z }); } catch (_) {}
    try { g.remove(); } catch (_) {}
    return;
  }
  // หมดเวลา: ปังสุดท้ายแล้วเงียบ
  try {
    const c = m.ix.cand, info = c.info;
    sound(dim, info.fam === "glass" ? "ghost.knock.glass" : info.fam === "wood" ? "ghost.bang.wood" : "ghost.bang.metal", lookPoint(c), 1.6, 0.6, 40);
    shake(dim, lookPoint(c), 10, 0.4, 0.4);
  } catch (_) {}
  try { g.remove(); } catch (_) {}
}
function tickKnock(g, m, ix, now) {
  const c = ix.cand, dim = g.dimension, p = ix.player;
  if (!valid(p) || p.dimension.id !== dim.id) { active.delete(g.id); try { g.remove(); } catch (_) {} return; }
  const b = getBlock(dim, c.pos.x, c.pos.y, c.pos.z), info = classify(b);
  if (!info || ((info.kind === "door" || info.kind === "gate") && isOpen(b))) return knockVanish(g, m, p, "open");
  if (now % 10 === 0) face(g, ix.knockT || standPoint(c), { x: c.pos.x + 0.5, y: c.pos.y + (info.kind === "door" ? 1.4 : 0.6), z: c.pos.z + 0.5 });
  const t = (now - ix.loop0) % 80;
  const knocking = now >= ix.loop0 && !(ix.visit && now - ix.loop0 >= ix.rounds * 80);
  if (knocking) {
    if (t === 2 || t === 7 || t === 12) knockSound(dim, c, info);
    if (t === 34 && Math.random() < 0.55) sound(dim, Math.random() < 0.5 ? "ghost.scratch" : "ghost.breath", lookPoint(c), 1.1, rand(0.8, 1.0), 20);
    if (t === 22 && Math.random() < 0.6 && dist(p.location, c.pos) < 16) actionbar(p, pick(info.fam === "glass" ? WINDOW_WHISPERS.concat(KNOCK_WHISPERS) : KNOCK_WHISPERS));
  }
  const hid = api.isHidden ? api.isHidden(p) : false;
  if (!hid && info.fam === "glass" && dist(p.location, c.pos) < 11) {
    if (lookingAtBlock(p, c.pos, 12) || lookDotTo(p, g.location) > 0.96) ix.watch++; else ix.watch = Math.max(0, ix.watch - 1);
    if (ix.watch >= 8) {
      if (ix.escalate) {                                  // v1.7.1 ผีตัวจริง: เห็นกันแล้ว มันพังเข้ามาเลย ไม่หายไป
        sound(dim, "ghost.growl", lookPoint(c), 2.0, 0.7, 32);
        for (const q of playersNear(dim, g.location, 20)) actionbar(q, "§4§lมันเห็นคุณแล้ว!");
        return startBash(g, m, now);
      }
      return knockVanish(g, m, p, "seen");
    }
  }
  if (!hid && dist(p.location, g.location) < 1.9) {
    if (ix.escalate) return startBash(g, m, now);
    return knockVanish(g, m, p, "close");
  }
  if (now >= ix.end) {
    if (ix.escalate) {
      for (const q of playersNear(dim, g.location, 20)) actionbar(q, "§4§lมันจะพังเข้ามา!");
      return startBash(g, m, now);
    }
    return knockVanish(g, m, p, ix.visit ? "visit" : "timeout");
  }
}

// ---------------------------------------------------------------------------------- v1.7 เรื่องหลอนตอนอยู่ในบ้าน
//  1) มาเคาะประตู/หน้าต่าง (ไม่พัง) แล้วหายไปหลังเคาะ 7 วินาที
//  2) มีเสียงแปลก ๆ (สุ่มไม่ซ้ำ) แล้วมีผีมายืนจ้องอยู่ที่หน้าต่าง/ประตู พอมองเห็น = jumpscare แล้วหายไป
export const SCARE_TAG = "ghost_scare";
export const SCARE_SOUNDS = ["giggle", "musicbox", "scream", "glass", "roof", "whisper", "wetbreath", "wallknock", "bell", "sob", "chains", "creak"];
const bags = new Map();                       // player id -> { left, last }  (ถุงสุ่ม: ครบทุกเสียงก่อนถึงจะวนซ้ำ)
export function nextScareSound(p) {
  let b = bags.get(p.id);
  if (!b) { b = { left: [], last: null }; bags.set(p.id, b); }
  if (!b.left.length) b.left = SCARE_SOUNDS.slice();
  let pool = b.left.filter((k) => k !== b.last);
  if (!pool.length) pool = b.left;
  const k = pool[Math.floor(Math.random() * pool.length)];
  b.left.splice(b.left.indexOf(k), 1);
  b.last = k;
  return k;
}
export function startKnockVisit(p, type) {
  try {
    const cands = knockTargets(p.dimension, p);
    if (!cands.length) return false;
    const c = cands[Math.floor(Math.random() * Math.min(3, cands.length))];
    const s = spawnAt(p, type, c, "knock");
    if (!s) return false;
    const { g, m, ix, now } = s;
    try { g.addTag(SCARE_TAG); } catch (_) {}
    ix.mode = "knock"; ix.t0 = now; ix.loop0 = now + 6; ix.visit = true; ix.rounds = Math.random() < 0.5 ? 1 : 2;
    ix.end = ix.loop0 + (ix.rounds - 1) * 80 + 12 + 140;       // หายไป 7 วินาทีหลังเคาะครั้งสุดท้าย
    ix.escalate = false; ix.watch = 0;
    register(g, m, now);
    return true;
  } catch (_) { return false; }
}
const pendingWatch = [];
function scareSoundAt(dim, p, c, key) {
  const L = lookPoint(c), d = c.dir;
  let loc = L, vol = 1.3;
  if (key === "roof") { loc = { x: p.location.x, y: p.location.y + 3.6, z: p.location.z }; vol = 1.4; }
  else if (key === "bell" || key === "scream") { loc = { x: L.x - d.x * 18, y: L.y + 2, z: L.z - d.z * 18 }; vol = 3.0; }
  else if (key === "wallknock") { loc = { x: (L.x + p.location.x) / 2, y: p.location.y + 1, z: (L.z + p.location.z) / 2 }; vol = 1.2; }
  sound(dim, `ghost.scare.${key}`, loc, vol, rand(0.95, 1.03), 40);
}
function doorOpenable(dim, c) {
  if (!c.info || c.info.kind !== "door" || c.info.fam !== "wood") return false;
  try { const r = boardsAt(dim, c.pos); if (r && r.n > 0) return false; } catch (_) {}
  return !isOpen(getBlock(dim, c.pos.x, c.pos.y, c.pos.z));
}
function setDoor(dim, pos, open) {
  for (const dy of [0, 1]) {
    try {
      const b = getBlock(dim, pos.x, pos.y + dy, pos.z);
      if (b && /_door$/.test(blockId(b))) b.setPermutation(b.permutation.withState("open_bit", open));
    } catch (_) {}
  }
}
export function startWatcher(p, type, key) {
  try {
    const dim = p.dimension;
    const cands = knockTargets(dim, p);
    let c = cands.find((q) => q.info.fam === "glass"), door = false;
    if (!c) { c = cands.find((q) => doorOpenable(dim, q)); door = !!c; }
    if (!c) return false;
    scareSoundAt(dim, p, c, key || nextScareSound(p));
    pendingWatch.push({ p, type, c, door, at: system.currentTick + Math.floor(rand(50, 90)) });
    return true;
  } catch (_) { return false; }
}
function pendingTick(now) {
  for (let i = pendingWatch.length - 1; i >= 0; i--) {
    const w = pendingWatch[i];
    if (now < w.at) continue;
    pendingWatch.splice(i, 1);
    try {
      if (!valid(w.p)) continue;
      const dim = w.p.dimension;
      const s = spawnAt(w.p, w.type, w.c, "watch");
      if (!s) continue;
      const { g, m, ix } = s;
      try { g.addTag(SCARE_TAG); } catch (_) {}
      ix.mode = "watch"; ix.t0 = now; ix.end = now + 30 * 20; ix.seen = 0; ix.door = w.door; ix.type = w.type;
      if (w.door) {
        setDoor(dim, w.c.pos, true);
        sound(dim, "random.door_open", lookPoint(w.c), 1.0, 0.5, 24);
      }
      face(g, ix.knockT, w.p.location);
      register(g, m, now);
    } catch (_) {}
  }
}
function endWatch(g, ix, smoke) {
  active.delete(g.id);
  const dim = g.dimension;
  try { if (smoke) for (let k = 0; k < 6; k++) particle(dim, "minecraft:basic_smoke_particle", { x: g.location.x + rand(-0.5, 0.5), y: g.location.y + rand(0.5, 2.5), z: g.location.z + rand(-0.5, 0.5) }); } catch (_) {}
  try { g.remove(); } catch (_) {}
  if (ix.door) {
    const c = ix.cand;
    system.runTimeout(() => {
      try { if (isOpen(getBlock(dim, c.pos.x, c.pos.y, c.pos.z))) { setDoor(dim, c.pos, false); sound(dim, "random.door_close", lookPoint(c), 1.0, 0.6, 24); } } catch (_) {}
    }, 30);
  }
}
// มองเห็นมันแล้ว: มันพุ่งมาตรงหน้า (ก้มตัวระดับหน้าเรา ดิ้นพล่าน) + jumpscare แล้วหายไป
function lunge(g, m, ix, p) {
  active.delete(g.id);
  const dim = p.dimension;
  let spot = null;
  try {
    const v = p.getViewDirection(), l = p.location, hl = Math.hypot(v.x, v.z) || 1;
    const h = (SIZE[ix.type] || [1, 3])[1];
    for (let d = 1.3 + 0.35 * h; d >= 1.2 && !spot; d -= 0.4) {
      const x = l.x + (v.x / hl) * d, z = l.z + (v.z / hl) * d;
      if (passable(getBlock(dim, x, l.y, z)) && passable(getBlock(dim, x, l.y + 1, z))) spot = { x, y: l.y, z };
    }
  } catch (_) {}
  if (spot) { face(g, spot, p.getHeadLocation()); trigger(g, "ghost:stuck"); }
  try { api.jumpscare(p, true); } catch (_) {}
  system.runTimeout(() => endWatch(g, ix, true), spot ? 14 : 1);
}
function tickWatch(g, m, ix, now) {
  const p = ix.player, dim = g.dimension;
  if (!valid(p) || p.dimension.id !== dim.id) return endWatch(g, ix, false);
  if (now % 10 === 0) face(g, ix.knockT, p.location);                         // จ้องตามตัวเรา
  const hid = api.isHidden ? api.isHidden(p) : false;
  const d = dist(p.location, g.location);
  if (!hid && d < 2.2) return lunge(g, m, ix, p);
  if (!hid && d < 20) {
    const h = (SIZE[ix.type] || [1, 3])[1];
    const head = { x: g.location.x, y: g.location.y + h * 0.82, z: g.location.z };
    let eye = p.location; try { eye = p.getHeadLocation(); } catch (_) {}
    const looking = lookDotTo(p, g.location) > 0.93 || (ix.cand.info.fam === "glass" && lookingAtBlock(p, ix.cand.pos, 20));
    if (looking && (los(dim, eye, head) || los(dim, eye, { x: head.x, y: g.location.y + 1.2, z: head.z }))) ix.seen++;
    else ix.seen = Math.max(0, ix.seen - 1);
    if (ix.seen >= 3) return lunge(g, m, ix, p);
  }
  if (now >= ix.end) return endWatch(g, ix, true);
}

// ---------------------------------------------------------------------------------- loop
function tick() {
  const now = system.currentTick;
  if (pendingWatch.length) pendingTick(now);
  for (const [id, g] of active) {
    try {
      if (!valid(g)) { active.delete(id); cancelPlan(id); continue; }
      const m = api.meta.get(id);
      if (!m || !m.ix || api.leaving.has(id)) { active.delete(id); cancelPlan(id); continue; }
      if (m.appeaseUntil && m.appeaseUntil > now) { release(g, m, now); m.ix.mode = "chase"; m.ix.route = null; cancelPlan(id); continue; }
      const ix = m.ix;
      switch (ix.mode) {
        case "approach": tickApproach(g, m, ix, now); break;
        case "bash": tickBash(g, m, ix, now); break;
        case "squeeze": tickSqueeze(g, m, ix, now); break;
        case "stuck": tickStuck(g, m, ix, now); break;
        case "dig": tickDig(g, m, ix, now); break;
        case "knock": tickKnock(g, m, ix, now); break;
        case "watch": tickWatch(g, m, ix, now); break;
        case "follow": tickFollow(g, m, ix, now); break;
        default: active.delete(id);
      }
    } catch (e) {
      if (globalThis.__simDebug) globalThis.__errors.push(e);
      // กันผีค้าง: ถ้าพังซ้ำ ๆ ให้กลับไปไล่แบบปกติ
      try { const m = api.meta.get(id); if (m && m.ix) { m.ix.err = (m.ix.err || 0) + 1; if (m.ix.err > 40) { m.ix.err = 0; resumeChase(g, m, now); } } } catch (_) {}
    }
  }
}
export function initIntrude(a) {
  api = a;
  system.runInterval(tick, 1);
}
export { isSheltered };
// ส่งออกให้ระบบ stalker ใช้หาประตู/หน้าต่างรอบตัวผู้เล่น
export { knockTargets, reachPoint, lookPoint, scareSoundAt, doorOpenable, setDoor as setDoorBlock };
