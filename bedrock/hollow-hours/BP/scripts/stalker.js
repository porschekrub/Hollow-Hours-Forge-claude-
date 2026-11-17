// ===== stalker.js : ผี "สตอล์กเกอร์" 3 ตัว (ไม่ใช่ผีไล่ล่า 5 ตัว) =====
//  แอบมอง / ตามหลัง / พุ่งหา / วิ่งตัดหน้า / มาที่กระจกหรือประตู ... พอผู้เล่นเห็นมัน มันจะหายไป (หรือพุ่งมา jumpscare)
//  ทุกครั้งที่มาจะสุ่มตัวใหม่ (ไม่ซ้ำตัวเดิมติดกัน) และสุ่มเหตุการณ์ใหม่ (ไม่ซ้ำ 2 อันล่าสุด)
import { world, system } from "@minecraft/server";
import { fl, rand, pick, valid, dist, getBlock, standCell, topBlock, sound, particle, isUndergroundAt, isSheltered, lookDotTo, lookingAtBlock } from "./util.js";
import { los } from "./hide.js";
import { knockTargets, reachPoint, lookPoint, scareSoundAt, doorOpenable, setDoorBlock } from "./intrude.js";

const MIN = 1200;
let api = null;
const sessions = new Map();          // id -> session
const busy = new Set();              // player id ที่มี stalker อยู่
const nextAt = new Map();            // player id -> tick ถัดไปที่จะมา
const bag = new Map();               // player id -> { left, last }
const hist = new Map();              // player id -> [เหตุการณ์ล่าสุด]
let seq = 0;

// ---------------------------------------------------------------- ตัวละคร 3 ตัว
//  seen = ต้องถูกจ้องกี่ tick ถึงจะหาย, rush = ความเร็วพุ่ง (บล็อก/tick), fy = ลอยเหนือพื้น
const TYPES = {
  1: { id: "ghost:stalker_1", name: "Lanky", h: 3.1, fy: 0,   seen: 36, rush: 1.9, hop: 0,
       snd: ["whisper", "wetbreath", "creak", "wallknock"],
       w: { peek: 4, lunge: 4, tunnel: 4, lure: 2, behind: 2, follow: 2, streak: 1, window: 4, door: 3, knock: 2 } },
  2: { id: "ghost:stalker_2", name: "Pale Child", h: 1.8, fy: 0, seen: 6, rush: 1.3, hop: 3,
       snd: ["giggle", "musicbox", "sob", "wallknock"],
       w: { follow: 5, behind: 4, lure: 3, peek: 1, lunge: 1, tunnel: 2, streak: 1, window: 3, door: 2, knock: 4 } },
  3: { id: "ghost:stalker_3", name: "Veil", h: 2.9, fy: 0.9, seen: 20, rush: 1.6, hop: 0,
       snd: ["bell", "chains", "scream", "glass"],
       w: { streak: 5, peek: 3, behind: 2, lunge: 3, tunnel: 1, lure: 1, follow: 2, window: 3, door: 3, knock: 1 } },
};
const OUTDOOR = ["peek", "lunge", "behind", "streak", "follow", "lure"];
const CAVE = ["tunnel", "behind", "lunge", "lure", "follow", "peek"];
const INDOOR = ["window", "door", "knock"];

// ---------------------------------------------------------------- ตัวช่วย
const head = (p) => { try { return p.getHeadLocation(); } catch (_) { return { x: p.location.x, y: p.location.y + 1.6, z: p.location.z }; } };
const baseAng = (p) => { try { const v = p.getViewDirection(); return Math.atan2(v.z, v.x); } catch (_) { return 0; } };
function floorAt(dim, x, z, py, ug) {
  const c = standCell(dim, fl(x), fl(z), py + 4, 10, 2);
  if (c) return c.y;
  if (!ug) {
    const top = topBlock(dim, x, z, py + 40, py - 40);
    if (top && !top.isLiquid && !/leaves|lava|ice/.test(top.typeId)) return top.location.y + 1;
  }
  return undefined;
}
// off = มุมเทียบทิศที่มอง (0 = ข้างหน้า, π = ข้างหลัง), "any" = รอบทิศ
function spotAround(p, off, dmin, dmax, needLos) {
  const dim = p.dimension, L = p.location, ug = isUndergroundAt(dim, L), b = baseAng(p);
  for (let t = 0; t < 18; t++) {
    const a = off === "any" ? Math.random() * 6.283 : b + off + rand(-0.4, 0.4);
    const d = rand(dmin, dmax), x = L.x + Math.cos(a) * d, z = L.z + Math.sin(a) * d;
    const y = floorAt(dim, x, z, L.y, ug);
    if (y === undefined || Math.abs(y - L.y) > (ug ? 7 : 12)) continue;
    if (needLos && !los(dim, head(p), { x, y: y + 1.5, z })) continue;
    return { x: fl(x) + 0.5, y, z: fl(z) + 0.5 };
  }
  return null;
}
function sees(p, loc, h, thr) {
  if (lookDotTo(p, loc) < thr) return false;
  try { return los(p.dimension, head(p), { x: loc.x, y: loc.y + h * 0.8, z: loc.z }); } catch (_) { return false; }
}
function ps(p, id, loc, vol, pitch) { try { p.runCommand(`playsound ${id} @s ${loc.x.toFixed(1)} ${loc.y.toFixed(1)} ${loc.z.toFixed(1)} ${vol} ${pitch}`); } catch (_) {} }
function eff(p, name, t, amp) { try { p.addEffect(name, t, { amplifier: amp || 0, showParticles: false }); } catch (_) {} }
function tpTo(S, x, y, z) {
  try { S.g.teleport({ x, y: y + S.ty.fy, z }, { facingLocation: head(S.p) }); } catch (_) {}
}
function face(S) { try { S.g.teleport(S.g.location, { facingLocation: head(S.p) }); } catch (_) {} }
function spawnS(S, loc) {
  try {
    const g = S.p.dimension.spawnEntity(S.ty.id, { x: loc.x, y: loc.y + S.ty.fy, z: loc.z });
    S.g = g; face(S); return true;
  } catch (_) { return false; }
}
function endS(S, smoke) {
  sessions.delete(S.id); busy.delete(S.p.id);
  try { if (S.g && valid(S.g)) {
    if (smoke) for (let k = 0; k < 7; k++) particle(S.g.dimension, "minecraft:basic_smoke_particle", { x: S.g.location.x + rand(-0.5, 0.5), y: S.g.location.y + rand(0.2, S.ty.h), z: S.g.location.z + rand(-0.5, 0.5) });
    S.g.remove();
  } } catch (_) {}
  if (S.door) { const c = S.cand, dim = S.dim; system.runTimeout(() => { try { setDoorBlock(dim, c.pos, false); sound(dim, "random.door_close", lookPoint(c), 1, 0.6, 24); } catch (_) {} }, 30); }
}
function fade(S, snd) {          // "หายไปหน่อย": ควันจาง ๆ + เสียงเบา ๆ
  try { if (S.g && valid(S.g)) { const l = S.g.location; sound(S.g.dimension, snd || "mob.endermen.portal", l, 0.7, S.ty.name === "Pale Child" ? 1.6 : 0.6, 24); } } catch (_) {}
  endS(S, true);
}
// jumpscare เฉพาะตัว
function scare(S, strong) {
  const p = S.p;
  try { api.jumpscare(p, strong); } catch (_) {}
  // เสียงกรีด/ตะโกนเฉพาะตัว (ซ้อนหลายชั้นให้ตกใจ): กระแทก + กรีดหลัก + เสียงทุ้มซ้อน + เสียงตามหลัง
  try {
    const l = p.location, n = S.n >= 1 && S.n <= 3 ? S.n : 1;
    ps(p, "ghost.stalker.hit", l, strong ? 3 : 2, rand(0.9, 1.1));
    ps(p, `ghost.stalker.scare${n}`, l, strong ? 3 : 2.2, rand(0.94, 1.06));
    system.runTimeout(() => { try { ps(p, `ghost.stalker.scare${n}`, p.location, strong ? 2.4 : 1.4, rand(0.68, 0.82)); } catch (_) {} }, 5);
    if (strong) {
      const o = 1 + (n % 3);                                   // เสียงของอีกตัวดังไล่หลังมา
      system.runTimeout(() => { try { const q = p.location; ps(p, `ghost.stalker.scare${o}`, { x: q.x + rand(-4, 4), y: q.y, z: q.z + rand(-4, 4) }, 2, rand(0.8, 0.95)); } catch (_) {} }, 22);
      system.runTimeout(() => { try { ps(p, "ghost.stalker.hit", p.location, 1.6, 0.7); } catch (_) {} }, 40);
    }
  } catch (_) {}
  if (S.n === 1) { eff(p, "slowness", 50, 1); eff(p, "darkness", 100, 0); }
  if (S.n === 2) { eff(p, "nausea", 80, 0); for (let k = 0; k < 3; k++) system.runTimeout(() => { try { ps(p, "ghost.scare.giggle", { x: p.location.x + rand(-3, 3), y: p.location.y, z: p.location.z + rand(-3, 3) }, 1.6, rand(0.8, 1.2)); } catch (_) {} }, 6 + k * 14); }
  if (S.n === 3) { eff(p, "darkness", 180, 0); eff(p, "blindness", 25, 0); eff(p, "weakness", 100, 0); }
}
// ---------------------------------------------------------------- เหตุการณ์ (start คืน true ถ้าเริ่มได้ / tick ทุก 2 tick)
const EV = {
  // แอบมองจากไกล: ยืนนิ่งจ้อง คืบเข้ามาตอนไม่ได้มอง พอถูกมองก็หาย ถ้าเข้ามาใกล้แล้วถูกมอง = jumpscare
  peek: {
    start(S) { const s = spotAround(S.p, rand(-0.8, 0.8), 17, 27, true); if (!s || !spawnS(S, s)) return false; S.end = S.t0 + 30 * 20; S.stepAt = S.t0 + 100; return true; },
    tick(S, now) {
      const g = S.g, d = dist(g.location, S.p.location);
      if (now % 6 === 0) face(S);
      if (sees(S.p, g.location, S.ty.h, 0.9)) {
        S.seen += 2;
        if (d < 8) { scare(S, true); return fade(S, "ghost.snap"); }
        if (S.seen >= S.ty.seen) return fade(S);
      } else {
        S.seen = Math.max(0, S.seen - 1);
        if (now >= S.stepAt && d > 6) { S.stepAt = now + rand(60, 130); const f = (S.ty.hop || 2) / d, L = g.location, P = S.p.location;
          const x = L.x + (P.x - L.x) * f, z = L.z + (P.z - L.z) * f, y = floorAt(g.dimension, x, z, L.y, isUndergroundAt(g.dimension, L));
          if (y !== undefined) tpTo(S, x, y, z); }
      }
      if (now >= S.end) fade(S);
    },
  },
  // ยืนไกล ๆ พอถูกมองก็วิ่งพุ่งเข้าใส่แล้ว jumpscare
  lunge: {
    start(S) { const s = spotAround(S.p, rand(-0.7, 0.7), 14, 22, true); if (!s || !spawnS(S, s)) return false; S.end = S.t0 + 26 * 20; S.rush = false; return true; },
    tick(S, now) {
      const g = S.g, P = S.p.location, d = dist(g.location, P);
      if (!S.rush) {
        if (now % 6 === 0) face(S);
        if (sees(S.p, g.location, S.ty.h, 0.9)) S.seen += 2; else S.seen = Math.max(0, S.seen - 1);
        if (S.seen >= Math.max(6, S.ty.seen * 0.5)) { S.rush = true; S.rushEnd = now + 70; sound(g.dimension, "ghost.growl", g.location, 1.8, 0.7, 40); }
        else if (now >= S.end) fade(S);
        return;
      }
      if (d < 2.2) { scare(S, true); return fade(S, "ghost.snap"); }
      if (now >= S.rushEnd) return fade(S);
      const f = Math.min(1, (S.ty.rush * 2) / d), L = g.location;
      const x = L.x + (P.x - L.x) * f, z = L.z + (P.z - L.z) * f, y = floorAt(g.dimension, x, z, L.y, isUndergroundAt(g.dimension, L));
      tpTo(S, x, y === undefined ? L.y - S.ty.fy : y, z);
    },
  },
  // โผล่ข้างหลัง: หันไปมอง = jumpscare เบา ๆ แล้วหาย / ไม่หันก็ค่อย ๆ ชิดมาจนแตะไหล่
  behind: {
    start(S) {
      const s = spotAround(S.p, Math.PI, 6, 9, false); if (!s || !spawnS(S, s)) return false;
      S.end = S.t0 + 14 * 20; S.stepAt = S.t0 + 40; S.turn = 0;
      ps(S.p, "ghost.scare." + pick(S.ty.snd), s, 1.2, 1); return true;
    },
    tick(S, now) {
      const g = S.g, d = dist(g.location, S.p.location);
      if (S.turn) { if (now >= S.turn) fade(S, "ghost.snap"); return; }
      if (now % 6 === 0) face(S);
      if (sees(S.p, g.location, S.ty.h * 0.6, 0.8)) { scare(S, false); S.turn = now + 6; return; }
      if (d < 2.4) { scare(S, true); return fade(S, "ghost.snap"); }
      if (now >= S.stepAt) { S.stepAt = now + 30; const f = 1 / d, L = g.location, P = S.p.location;
        const x = L.x + (P.x - L.x) * f, z = L.z + (P.z - L.z) * f, y = floorAt(g.dimension, x, z, L.y, isUndergroundAt(g.dimension, L));
        if (y !== undefined) tpTo(S, x, y, z); ps(S.p, "step.stone", g.location, 0.8, 0.8); }
      if (now >= S.end) fade(S);
    },
  },
  // วิ่งตัดหน้าผู้เล่นจากซ้ายไปขวา (หรือกลับกัน) แล้วหายไป
  streak: {
    start(S) {
      const P = S.p.location, b = baseAng(S.p), side = Math.random() < 0.5 ? 1 : -1, dim = S.p.dimension, ug = isUndergroundAt(dim, P);
      const ax = P.x + Math.cos(b) * 7, az = P.z + Math.sin(b) * 7, px = Math.cos(b + Math.PI / 2) * side, pz = Math.sin(b + Math.PI / 2) * side;
      const s = { x: ax + px * 12, z: az + pz * 12 }, y = floorAt(dim, s.x, s.z, P.y, ug);
      if (y === undefined || Math.abs(y - P.y) > 8) return false;
      if (!spawnS(S, { x: s.x, y, z: s.z })) return false;
      S.dx = -px; S.dz = -pz; S.steps = 0; S.end = S.t0 + 70; S.hit = false;
      ps(S.p, "ghost.scare." + pick(S.ty.snd), { x: s.x, y, z: s.z }, 1.5, 1); return true;
    },
    tick(S, now) {
      const g = S.g, L = g.location, nx = L.x + S.dx * 1.6, nz = L.z + S.dz * 1.6;
      const y = floorAt(g.dimension, nx, nz, L.y, isUndergroundAt(g.dimension, L));
      if (y === undefined || now >= S.end) return fade(S);
      try { g.teleport({ x: nx, y: y + S.ty.fy, z: nz }, { facingLocation: { x: nx + S.dx * 4, y: y + 1, z: nz + S.dz * 4 } }); } catch (_) {}
      if (!S.hit && dist(g.location, S.p.location) < 9 && lookDotTo(S.p, g.location) > 0.55) { S.hit = true; scare(S, false); }
      if (++S.steps >= 15) fade(S);
    },
  },
  // ตามหลังแบบ Weeping Angel: ขยับเข้ามาเฉพาะตอนไม่ถูกมอง ถูกมองไกล ๆ นานพอ = หาย, ใกล้แล้วถูกมอง = jumpscare
  follow: {
    start(S) { const s = spotAround(S.p, Math.PI + rand(-1, 1), 17, 25, false); if (!s || !spawnS(S, s)) return false; S.end = S.t0 + 60 * 20; S.stepAt = S.t0 + 40; return true; },
    tick(S, now) {
      const g = S.g, L = g.location, P = S.p.location, d = dist(L, P);
      const looked = sees(S.p, L, S.ty.h, 0.85);
      if (now % 6 === 0) face(S);
      if (looked) {
        S.seen += 2;
        if (d <= 4) { scare(S, true); return fade(S, "ghost.snap"); }
        if (d > 9 && S.seen >= S.ty.seen) return fade(S);
      } else {
        S.seen = Math.max(0, S.seen - 1);
        if (d < 1.9) { scare(S, true); return fade(S, "ghost.snap"); }
        if (now >= S.stepAt) {
          const hop = S.ty.hop || (S.n === 3 ? 1.6 : 1.1);
          S.stepAt = now + (S.ty.hop ? 26 : 8);
          const f = Math.min(hop, d - 1.6) / d, x = L.x + (P.x - L.x) * f, z = L.z + (P.z - L.z) * f, y = floorAt(g.dimension, x, z, L.y, isUndergroundAt(g.dimension, L));
          if (y !== undefined) tpTo(S, x, y, z);
          if (S.ty.hop) ps(S.p, "ghost.scare.giggle", g.location, 0.9, rand(1.1, 1.5));
        }
      }
      if (now >= S.end) fade(S);
    },
  },
  // ในถ้ำ: ยืนอยู่ปลายอุโมงค์มืด ๆ จ้องมา หายเมื่อถูกมอง
  tunnel: {
    start(S) { if (!isUndergroundAt(S.p.dimension, S.p.location)) return false; const s = spotAround(S.p, rand(-0.5, 0.5), 12, 24, true); if (!s || !spawnS(S, s)) return false; S.end = S.t0 + 25 * 20; return true; },
    tick(S, now) {
      const g = S.g, d = dist(g.location, S.p.location);
      if (now % 6 === 0) face(S);
      if (sees(S.p, g.location, S.ty.h, 0.93)) { S.seen += 2; if (S.seen >= Math.max(8, S.ty.seen * 0.6)) { if (d < 14 && S.n !== 2) { scare(S, false); } return fade(S); } }
      else S.seen = Math.max(0, S.seen - 1);
      if (now >= S.end) fade(S);
    },
  },
  // ได้ยินเสียงเรียกจากทิศหนึ่ง สักพักมันก็ยืนอยู่ตรงนั้น พอเห็นก็ jumpscare เบา ๆ แล้วหาย
  lure: {
    start(S) {
      const s = spotAround(S.p, "any", 10, 18, true); if (!s) return false;
      S.spot = s; S.phase = 0; S.at = S.t0 + 90; S.end = S.t0 + 90 + 20 * 22; S.sn = pick(S.ty.snd);
      ps(S.p, "ghost.scare." + S.sn, s, 1.8, 1); return true;
    },
    tick(S, now) {
      if (S.phase === 0) {
        if (now === S.t0 + 40) ps(S.p, "ghost.scare." + S.sn, S.spot, 2.0, 0.9);
        if (now >= S.at) { if (!spawnS(S, S.spot)) return endS(S, false); S.phase = 1; S.shownAt = now; }
        return;
      }
      const g = S.g;
      if (now % 6 === 0) face(S);
      if (sees(S.p, g.location, S.ty.h, 0.9)) { S.seen += 2; if (S.seen >= 6) { scare(S, false); return fade(S, "ghost.snap"); } }
      else S.seen = Math.max(0, S.seen - 1);
      if (now >= S.end) fade(S);
    },
  },
  // ผีที่กระจก (กระจกบานหน้าต่าง/ประตูที่เปิดได้): ยืนจ้องอยู่ข้างนอก พอเห็นก็พุ่งมาตรงหน้า + jumpscare
  window: {
    start(S) { return startWatch(S, "glass"); },
    tick(S, now) { return tickWatch(S, now); },
  },
  door: {
    start(S) { return startWatch(S, "door"); },
    tick(S, now) { return tickWatch(S, now); },
  },
  // เคาะประตู/กระจกหลายรอบ ถ้าเป็นกระจกจะมีหน้าโผล่มาจ้อง พอเห็นก็หายไปเงียบ ๆ (ไม่พุ่ง)
  knock: {
    start(S) {
      const dim = S.p.dimension, cands = knockTargets(dim, S.p);
      if (!cands.length) return false;
      const c = cands[Math.floor(Math.random() * Math.min(3, cands.length))];
      S.cand = c; S.dim = dim; S.phase = 0; S.rounds = Math.random() < 0.5 ? 2 : 3; S.k = 0; S.nextKnock = S.t0 + 10;
      return true;
    },
    tick(S, now) {
      const p = S.p, dim = S.dim, c = S.cand;
      if (S.phase === 0) {
        if (now >= S.nextKnock) {
          sound(dim, "ghost.scare.wallknock", lookPoint(c), 1.7, rand(0.95, 1.05), 40);
          S.nextKnock = now + (S.k % 3 === 2 ? 70 : 12); S.k++;
          if (S.k >= S.rounds * 3) { S.phase = 1; S.at = now + 40; }
        }
        return;
      }
      if (S.phase === 1) {
        if (now < S.at) return;
        if (c.info.fam !== "glass") { sound(dim, "ghost.scare.whisper", lookPoint(c), 1.4, 0.9, 24); return endS(S, false); }   // ประตู: แค่เสียงกระซิบแล้วเงียบ
        const T = reachPoint(dim, c, 1.1);
        if (!spawnS(S, { x: T.x, y: T.y, z: T.z })) return endS(S, false);
        S.phase = 2; S.end = now + 8 * 20; return;
      }
      if (now % 6 === 0) face(S);
      const looking = sees(p, S.g.location, S.ty.h, 0.9) || (c.info.fam === "glass" && lookingAtBlock(p, c.pos, 20));
      if (looking) S.seen += 2; else S.seen = Math.max(0, S.seen - 1);
      if (S.seen >= 8 || now >= S.end) fade(S, S.seen >= 8 ? "ghost.scare.giggle" : undefined);
    },
  },
};
// ----- ผีที่กระจก/ประตู (ใช้ร่วมกัน)
function startWatch(S, kind) {
  const dim = S.p.dimension, cands = knockTargets(dim, S.p);
  let c = kind === "glass" ? cands.find((q) => q.info.fam === "glass") : cands.find((q) => doorOpenable(dim, q));
  if (!c) return false;
  S.cand = c; S.dim = dim; S.door = kind === "door"; S.phase = 0; S.at = S.t0 + Math.floor(rand(50, 90));
  scareSoundAt(dim, S.p, c, pick(S.ty.snd));
  return true;
}
function tickWatch(S, now) {
  const p = S.p, dim = S.dim, c = S.cand;
  if (S.phase === 0) {
    if (now < S.at) return;
    const T = reachPoint(dim, c, S.n === 1 ? 1.3 : 1.0);
    if (!spawnS(S, { x: T.x, y: T.y, z: T.z })) return endS(S, false);
    if (S.door) { setDoorBlock(dim, c.pos, true); sound(dim, "random.door_open", lookPoint(c), 1.0, 0.5, 24); }
    S.phase = 1; S.end = now + 30 * 20; return;
  }
  if (S.phase === 2) { if (now >= S.lungeEnd) fade(S, "ghost.snap"); return; }
  if (now % 6 === 0) face(S);
  const d = dist(p.location, S.g.location);
  if (d < 2.2) return lunge(S, now);
  const looking = sees(p, S.g.location, S.ty.h, 0.93) || (c.info.fam === "glass" && lookingAtBlock(p, c.pos, 20));
  if (looking && d < 22) S.seen += 2; else S.seen = Math.max(0, S.seen - 1);
  if (S.seen >= 6) return lunge(S, now);
  if (now >= S.end) fade(S);
}
function lunge(S, now) {
  const p = S.p, dim = p.dimension;
  let spot = null;
  try {
    const v = p.getViewDirection(), l = p.location, hl = Math.hypot(v.x, v.z) || 1;
    for (let d = 1.5 + 0.3 * S.ty.h; d >= 1.2 && !spot; d -= 0.3) {
      const x = l.x + (v.x / hl) * d, z = l.z + (v.z / hl) * d;
      const y = floorAt(dim, x, z, l.y, true);
      if (y !== undefined && Math.abs(y - l.y) < 1.5) spot = { x, y, z };
    }
  } catch (_) {}
  if (spot) tpTo(S, spot.x, spot.y, spot.z);
  scare(S, true);
  S.phase = 2; S.lungeEnd = now + 12;
}
// ---------------------------------------------------------------- ตัวเลือกสุ่ม (ไม่ซ้ำตัวเดิม/เหตุการณ์เดิมติดกัน)
function pickType(p) {
  let b = bag.get(p.id);
  if (!b) { b = { left: [], last: 0 }; bag.set(p.id, b); }
  if (!b.left.length) b.left = [1, 2, 3].sort(() => Math.random() - 0.5);
  let i = b.left.findIndex((n) => n !== b.last);
  if (i < 0) i = 0;
  const n = b.left.splice(i, 1)[0];
  b.last = n; return n;
}
function pickEvent(p, pool, ty) {
  const h = hist.get(p.id) || [];
  let list = pool.filter((e) => !h.includes(e));
  if (!list.length) list = pool;
  const tot = list.reduce((s, e) => s + (ty.w[e] || 1), 0);
  let r = Math.random() * tot;
  for (const e of list) { r -= ty.w[e] || 1; if (r <= 0) return e; }
  return list[0];
}
function begin(p, pool, force) {
  if (!valid(p) || busy.has(p.id)) return false;
  const n = pickType(p), ty = TYPES[n];
  // ลองหลายเหตุการณ์จนกว่าจะเริ่มได้ (เช่นไม่มีกระจกก็ไปแบบอื่น)
  let tries = pool.slice();
  for (let k = 0; k < 4 && tries.length; k++) {
    const ev = pickEvent(p, tries, ty);
    tries = tries.filter((e) => e !== ev);
    const S = { id: ++seq, p, n, ty, ev, t0: system.currentTick, seen: 0, g: null, dim: p.dimension };
    let ok = false;
    try { ok = EV[ev].start(S); } catch (_) { ok = false; }
    if (!ok) { try { if (S.g && valid(S.g)) S.g.remove(); } catch (_) {} continue; }
    sessions.set(S.id, S); busy.add(p.id);
    const h = hist.get(p.id) || []; h.push(ev); while (h.length > 2) h.shift(); hist.set(p.id, h);
    return true;
  }
  return false;
}
// ใช้จากภายนอก
export function stalkerIndoor(p) { return begin(p, INDOOR); }
export function stalkerOutdoor(p) {
  const ug = (() => { try { return isUndergroundAt(p.dimension, p.location); } catch (_) { return false; } })();
  return begin(p, ug ? CAVE : OUTDOOR);
}
export function stalkerActive() { return sessions.size > 0; }

// ---------------------------------------------------------------- ลูปหลัก
// ความกดดันก่อนช็อก: ยิ่งใกล้ หัวใจยิ่งเต้นเร็ว + เสียงกระซิบข้างหลัง (v1.9.5)
function dread(S, now) {
  const p = S.p; if (!S.g || S.phase === 0 || S.phase === 2) return;
  const d = dist(p.location, S.g.location); if (d > 40) return;
  const k = Math.max(0, Math.min(1, 1 - d / 40)), l = p.location;
  if (!S.dr) { S.dr = true; ps(p, "ghost.horror.drone", l, 1.3, 1); }
  if (!S.hb || now >= S.hb) { ps(p, "ghost.horror.heart", l, 0.6 + 1.8 * k, 0.85 + 0.45 * k); S.hb = now + Math.round(34 - 20 * k); }
  if (k > 0.3 && (!S.wh || now >= S.wh)) {
    const v = p.getViewDirection();
    ps(p, "ghost.horror.whisper", { x: l.x - v.x * 2, y: l.y + 1.6, z: l.z - v.z * 2 }, 1.3, rand(0.9, 1.05));
    S.wh = now + Math.round(rand(150, 300) * (1.3 - k));
  }
}
function tick() {
  const now = system.currentTick;
  for (const [id, S] of sessions) {
    try {
      const p = S.p;
      if (!valid(p) || p.dimension.id !== S.dim.id) { endS(S, false); continue; }
      if (S.g && !valid(S.g) && S.phase !== 0) { sessions.delete(id); busy.delete(p.id); continue; }   // ถูกลบไป (เช่นโหลดโลกใหม่)
      if (api.isHidden(p) && S.g) { fade(S); continue; }                     // ผู้เล่นซ่อนอยู่: stalker ไม่ยุ่ง
      if (S.g && dist(S.g.location, p.location) > 70) { endS(S, false); continue; }
      try { dread(S, now); } catch (_) {}
      EV[S.ev].tick(S, now);
    } catch (_) { try { endS(S, false); } catch (__) {} }
  }
}
function schedule() {
  const now = system.currentTick;
  // กวาด stalker ที่ไม่มี session (หลังโหลดโลกใหม่ / เกิดผิดพลาด)
  if (now % 400 < 100) {
    const live = new Set(); for (const S of sessions.values()) if (S.g) live.add(S.g.id);
    for (const dn of ["overworld", "nether", "the_end"]) {
      try { for (const e of world.getDimension(dn).getEntities({ families: ["stalker"] })) if (!live.has(e.id)) e.remove(); } catch (_) {}
    }
  }
  if (api.ghostBusy()) return;
  const t = world.getTimeOfDay(), night = t >= 13000 && t <= 23000;
  for (const p of world.getAllPlayers()) {
    try {
      if (p.dimension.id !== "minecraft:overworld" || busy.has(p.id) || api.isHidden(p)) continue;
      if (!nextAt.has(p.id)) { nextAt.set(p.id, now + rand(5 * MIN, 9 * MIN)); continue; }
      if (now < nextAt.get(p.id)) continue;
      if (isSheltered(p)) { continue; }          // ในบ้านใช้ตัวจัดคิวของ main (กระจก/ประตู)
      nextAt.set(p.id, now + rand(9 * MIN, 16 * MIN) / ((api.moodEv ? api.moodEv() : 1) * (night ? 1.5 : 1)));
      if (stalkerOutdoor(p)) break;
    } catch (_) {}
  }
}
// ทดสอบ: /scriptevent ghost:stalker [1-3] [ชื่อเหตุการณ์]  เช่น  ghost:stalker 2 lunge
function stalkerTest(p, n, evName) {
  if (!valid(p)) return "ผู้เล่นไม่พร้อม";
  if (busy.has(p.id)) return "มี Stalker อยู่แล้ว รอให้หายก่อน";
  const ty = TYPES[n]; if (!ty) return "เลขตัวต้องเป็น 1-3";
  let pool = evName ? [evName] : OUTDOOR.slice().sort(() => Math.random() - 0.5);
  if (evName && !EV[evName]) return "ไม่มีเหตุการณ์ชื่อนี้: " + Object.keys(EV).join(", ");
  for (const ev of pool) {
    const S = { id: ++seq, p, n, ty, ev, t0: system.currentTick, seen: 0, g: null, dim: p.dimension };
    let ok = false;
    try { ok = EV[ev].start(S); } catch (_) { ok = false; }
    if (!ok) { try { if (S.g && valid(S.g)) S.g.remove(); } catch (_) {} continue; }
    sessions.set(S.id, S); busy.add(p.id);
    return "เริ่ม " + ty.name + " / " + ev;
  }
  return "เริ่มไม่ได้ (หาที่ยืนไม่เจอ ลองย้ายไปที่โล่ง ๆ หรืออยู่ในบ้านสำหรับ window/door/knock)";
}
export function initStalker(a) {
  api = a;
  try {
    system.afterEvents.scriptEventReceive.subscribe((ev) => {
      try {
        if (ev.id !== "ghost:stalker") return;
        const p = ev.sourceEntity; if (!p || p.typeId !== "minecraft:player") return;
        const parts = String(ev.message || "").trim().split(/\s+/).filter(Boolean);
        let n = 0, name = "";
        for (const w of parts) { if (/^[1-3]$/.test(w)) n = Number(w); else name = w; }
        if (!n) n = pickType(p);
        p.sendMessage("§7" + stalkerTest(p, n, name));
      } catch (_) {}
    });
  } catch (_) {}
  system.runInterval(tick, 2);
  system.runInterval(schedule, 100);
}
