// ===== v1.9 variety.js: นิสัยผีแต่ละตัว + ระบบสุ่มเหตุการณ์ (เล่นซ้ำไม่ซ้ำเดิม) =====
import { world, system } from "@minecraft/server";
import { flashOn } from "./flashlight.js";

const MIN = 1200;
const rand = (a, b) => a + Math.random() * (b - a);
const pick = (a) => a[Math.floor(Math.random() * a.length)];

// ---------------------------------------------------------------- โปรไฟล์ผี 5 ตัว
// spd = คูณความเร็วไล่, dur = คูณเวลาไล่/ยืนจ้อง, vis = คูณระยะรับรู้, stalk = คูณเวลาคืบ
export const PROFILE = {
  1: { name: "Pale Wailer",  spd: 1.08, dur: 1.2, vis: 1.3,  stalk: 0.7 },  // นักล่าเสียง: ฟังเก่งมาก
  2: { name: "Drowned Bride", spd: 0.92, dur: 2.2, vis: 1.15, stalk: 1.3 },  // ช้ากว่านิด แต่ไม่ยอมเลิก ไล่ได้ไกลมาก
  3: { name: "Charred One",  spd: 1.0,  dur: 0.8, vis: 1.0,  stalk: 0.6 },  // พุ่งเป็นช่วง ๆ เร็วมาก
  4: { name: "Hollow Grin",  spd: 1.05, dur: 1.2, vis: 1.1,  stalk: 1.0 },  // ขี้โกง วาร์ปหลอก
  5: { name: "Bone Stag",    spd: 1.45, dur: 1.4, vis: 1.1,  stalk: 1.0 },  // เร็วจัด หยุดนิ่งเมื่อถูกจ้องไกล ๆ เท่านั้น
};
export const CREEP_BY = {
  1: { a: 40, b: 90,  s0: 2, s1: 4 },      // คืบถี่ ๆ ก้าวสั้น
  2: { a: 90, b: 200, s0: 2, s1: 3 },      // ช้า ๆ น่าอึดอัด
  3: { a: 120, b: 220, s0: 6, s1: 10 },    // นาน ๆ ที แต่กระโดดไกล
  4: { a: 20, b: 120, s0: 1, s1: 8 },      // สุ่มมั่ว
};

const lookDotOf = (p, g) => {
  const v = p.getViewDirection();
  const dx = g.location.x - p.location.x, dz = g.location.z - p.location.z;
  const dl = Math.hypot(dx, dz) || 1, vl = Math.hypot(v.x, v.z) || 1;
  return (v.x * dx + v.z * dz) / (dl * vl);
};

// ---------------------------------------------------------------- พฤติกรรมเฉพาะตัว (เรียกทุก 5 tick ตอนผีกำลังทำงาน)
// c = { g, best, bestD, state, now, m, dim, api }   ตั้ง m.spdMul เพื่อปรับความเร็วไล่
export const PERSONA = {
  // 1: นักล่าเสียง — ผู้เล่นวิ่ง = มันรู้ตำแหน่งตลอดและเร่งความเร็ว, ย่องก็ยังได้ยินในระยะใกล้, ปล่อยเสียงล่อ, กรีดร้องใส่จนตาพร่า
  1(c) {
    const { g, best, bestD, state, m, now, api } = c;
    let sprint = false, sneak = false; try { sprint = best.isSprinting; sneak = best.isSneaking; } catch (e) {}
    if (state === 0 && m.stalking && bestD < 48) {
      if (sprint) { m.stalkFor = Math.max(0, m.stalkFor - 70); if (Math.random() < 0.3) api.playAt(best, "ghost.creak", g.location.x, g.location.y, g.location.z, 1.4, 0.7); }
      if (now >= (m.lureAt || 0) && bestD < 40) {
        m.lureAt = now + api.rand(200, 420);
        const a = Math.atan2(g.location.z - best.location.z, g.location.x - best.location.x) + Math.PI + api.rand(-0.8, 0.8);
        const d = api.rand(5, 11), l = best.location;
        api.playAt(best, pick(["ghost.growl", "mob.ghast.moan", "ghost.creak"]), l.x + Math.cos(a) * d, l.y, l.z + Math.sin(a) * d, 1.6, api.rand(0.5, 0.9));
      }
    }
    if (state === 2) {
      if (sprint && bestD < 45) m.lastDetect = now;                         // วิ่ง = ไม่มีทางคลาดสายตา/หู
      if (sneak && bestD < 9) m.lastDetect = now;                           // ย่องแต่อยู่ใกล้ก็ยังได้ยินลมหายใจ
      if (bestD < 12 && now >= (m.screamAt || 0)) {                         // กรีดร้องใส่ ตาพร่า
        m.screamAt = now + 320;
        api.playAt(best, "mob.ghast.scream", g.location.x, g.location.y, g.location.z, 2, 0.6);
        try { best.addEffect("blindness", 30, { amplifier: 0, showParticles: false }); best.addEffect("slowness", 20, { amplifier: 0, showParticles: false }); } catch (e) {}
      }
    }
    m.spdMul = sprint ? 1.3 : sneak ? 0.95 : 1.1;
  },
  // 2: เจ้าสาวจมน้ำ — ไม่ยอมปล่อย: ถ้าหนีไกลจะโผล่ตามมาข้างหลัง, ใกล้ ๆ โดนดึงให้อ่อนแรง+ช้าลง, น้ำทำให้มันเร็วขึ้น
  2(c) {
    const { g, best, bestD, state, m, now, api } = c;
    m.spdMul = 1;
    if (state === 2) {
      let wet = false; try { wet = best.isInWater || g.isInWater; } catch (e) {}
      if (wet) m.spdMul = 1.35;
      if (bestD < 9 && now >= (m.dragAt || 0)) {
        m.dragAt = now + 100;
        try { best.addEffect("weakness", 80, { amplifier: 0, showParticles: false }); best.addEffect("slowness", 50, { amplifier: 0, showParticles: false }); } catch (e) {}
        api.playAt(best, "liquid.water", best.location.x, best.location.y, best.location.z, 1.4, 0.6);
      }
      if (bestD > 24 && now >= (m.tideAt || 0)) {                            // หนีไกลเกินไป: น้ำท่วมแล้วมันโผล่ตามหลัง
        m.tideAt = now + 260;
        const spot = api.findSpot(best, true, 11, 15);
        if (spot) { g.teleport(spot, { facingLocation: best.location }); api.playAt(best, "bubble.up", spot.x, spot.y, spot.z, 2, 0.6); }
      }
    }
    if (state === 0 && m.stalking && bestD < 24 && Math.random() < 0.05)
      api.playAt(best, "mob.ghast.moan", g.location.x, g.location.y, g.location.z, 1.8, 0.45);
  },
  // 3: ผีไหม้ — พุ่ง 2.5 วิ (เร็วมาก 1.9 เท่า) พักแค่ 1.5 วิ แถมลมร้อนเผาไหม้เมื่อชิดตัว
  3(c) {
    const { g, best, bestD, state, m, now, api } = c;
    if (state !== 2) { m.spdMul = 1; return; }
    if (!m.burst) m.burst = { on: false, until: now + 40 };
    if (now >= m.burst.until) {
      m.burst.on = !m.burst.on;
      m.burst.until = now + (m.burst.on ? 50 : 30);
      if (m.burst.on) api.playAt(best, "mob.blaze.shoot", g.location.x, g.location.y, g.location.z, 1.8, 0.7);
    }
    m.spdMul = m.burst.on ? 1.9 : 0.85;
    if (m.burst.on) m.lastDetect = now;                                       // ตอนพุ่งมันไม่หลุดเป้า
    if (bestD < 5) { try { best.setOnFire(3, true); } catch (e) {} }
  },
  // 4: ยิ้มกลวง — ความเร็วแกว่ง 0.8-1.6, วาร์ปไปด้านหลังเราตอนไม่ได้มอง, โดนไฟฉายส่องจะถอยแล้วพุ่งกลับ
  4(c) {
    const { g, best, bestD, state, m, now, api } = c;
    if (state !== 2) { m.spdMul = 1; return; }
    if (now >= (m.wobbleAt || 0)) { m.wobbleAt = now + api.rand(25, 70); m.spdMul = api.rand(0.8, 1.6); }
    let dot = -1; try { dot = lookDotOf(best, g); } catch (e) {}
    if (bestD > 7 && bestD < 40 && dot < 0.2 && now >= (m.blinkAt || 0)) {   // ไม่ได้มองมัน = วาร์ปมาข้างหลัง
      m.blinkAt = now + api.rand(120, 220);
      const spot = api.findSpot(best, true, 4, 6);
      if (spot) { g.teleport(spot, { facingLocation: best.location }); api.playAt(best, "mob.endermen.portal", spot.x, spot.y, spot.z, 1.6, 0.6); m.wobbleAt = now + 60; m.spdMul = 1.5; }
    }
    if (bestD < 14 && now >= (m.flinchAt || 0) && dot > 0.9 && flashOn(best)) {
      m.flinchAt = now + 300;
      const spot = api.findSpot(best, false, 16, 22);
      if (spot) { g.teleport(spot, { facingLocation: best.location }); api.playAt(best, "mob.witch.ambient", spot.x, spot.y, spot.z, 2, 0.5); m.wobbleAt = now + 70; m.spdMul = 1.7; }
    }
  },
  // 5: กวางกระดูก — เร็วจัด แข็งเป็นหินเฉพาะตอนถูกจ้องและอยู่ไกลเกิน 6 บล็อก ใกล้กว่านั้นยังคืบเข้ามาแม้ถูกจ้อง
  5(c) {
    const { g, best, bestD, state, m } = c;
    if (state !== 2) { m.spdMul = 1; return; }
    let dot = -1; try { dot = lookDotOf(best, g); } catch (e) {}
    if (dot > 0.8 && bestD < 40) m.spdMul = bestD > 6 ? 0.02 : 0.4;
    else m.spdMul = 1.2;
  },
};

// ---------------------------------------------------------------- ตัวปรับแต่งผีรายครั้ง (สุ่มทุกการเกิด ทำให้ผีตัวเดิมไม่เหมือนเดิม)
const MODS = [
  { id: "plain",    w: 40, dur: 1,   vis: 1,   spd: 1,    stalk: 1 },
  { id: "frenzied", w: 14, dur: 0.8, vis: 1,   spd: 1.25, stalk: 0.7 },   // ดุ เร็ว
  { id: "lingering",w: 14, dur: 1.6, vis: 1.1, spd: 0.95, stalk: 1.5 },   // เกาะติด ยืนจ้องนาน
  { id: "blind",    w: 12, dur: 1,   vis: 0.6, spd: 1,    stalk: 1 },     // ตาไม่ดี หลบง่ายกว่า
  { id: "keen",     w: 12, dur: 1,   vis: 1.35, spd: 1,   stalk: 0.9 },   // ตาดี มองไกล
  { id: "shy",      w: 8,  dur: 0.5, vis: 1,   spd: 1,    stalk: 0.5 },   // โผล่แล้วหายเร็ว
];
export function rollMod() {
  const tot = MODS.reduce((s, x) => s + x.w, 0);
  let r = Math.random() * tot;
  for (const x of MODS) { r -= x.w; if (r <= 0) return x; }
  return MODS[0];
}
// รวมบัฟของต้องสาป + โปรไฟล์ตัวผี + ตัวปรับแต่งรอบนี้
export function effectiveBuff(m, n, base) {
  const P = PROFILE[n] || PROFILE[1], M = m.mod || MODS[0], B = m.buff || base;
  return { dur: B.dur * P.dur * M.dur, vis: B.vis * P.vis * M.vis, dmg: B.dmg, spd: B.spd * P.spd * M.spd };
}

// ---------------------------------------------------------------- อารมณ์ของคืน (สุ่มใหม่ทุกวันในเกม)
const MOODS = [
  { id: "calm",     w: 30, gap: 1.35, ev: 0.7, msg: "§8คืนนี้...เงียบผิดปกติ" },
  { id: "normal",   w: 35, gap: 1.0,  ev: 1.0, msg: null },
  { id: "restless", w: 22, gap: 0.75, ev: 1.4, msg: "§8อากาศรอบตัว...หนักขึ้น" },
  { id: "haunted",  w: 13, gap: 0.55, ev: 2.0, msg: "§4§oมันตื่นแล้ว" },
];
let mood = MOODS[1], moodDay = -1;
export function moodGap() { return mood.gap; }
export function moodEv() { return mood.ev; }
function rollMood() {
  const tot = MOODS.reduce((s, x) => s + x.w, 0);
  let r = Math.random() * tot;
  for (const x of MOODS) { r -= x.w; if (r <= 0) return x; }
  return MOODS[1];
}

// ---------------------------------------------------------------- ระบบสุ่มเหตุการณ์
// กติกา: เลือกแบบถ่วงน้ำหนัก, ห้ามซ้ำ 3 อันล่าสุด, ไม่รบกวนตอนมีผีจริง/ลางร้าย/ผู้เล่นซ่อนอยู่
const recent = [];
const nextEv = new Map();   // player.id -> tick
const around = (l, d) => { const a = Math.random() * 6.283; return { x: l.x + Math.cos(a) * d, z: l.z + Math.sin(a) * d }; };

function mkEvents(api) {
  const P = (p, id, x, y, z, v, pt) => api.playAt(p, id, x, y, z, v, pt);
  return [
    { id: "footsteps", w: 14, run(p) {                       // ฝีเท้าเดินตามมาแล้วหยุดทันที
      const l = p.location, a = around(l, 9).x - l.x, b = around(l, 9).z - l.z; const L = Math.hypot(a, b) || 1;
      for (let k = 0; k < 7; k++) system.runTimeout(() => {
        const f = 1 - k / 8;
        P(p, pick(["step.stone", "step.gravel", "step.grass"]), l.x + (a / L) * 9 * f, l.y, l.z + (b / L) * 9 * f, 1.3, api.rand(0.7, 0.9));
      }, k * 9);
    } },
    { id: "blackout", w: 9, run(p) {                         // ไฟดับชั่วขณะ
      try { p.addEffect("darkness", 160, { amplifier: 0, showParticles: false }); } catch (e) {}
      P(p, "mob.warden.heartbeat", p.location.x, p.location.y, p.location.z, 1.4, 0.9);
      system.runTimeout(() => { try { p.onScreenDisplay.setActionBar("§8§k||§r §7อย่าส่งเสียง §8§k||"); } catch (e) {} }, 40);
    } },
    { id: "coldspot", w: 10, run(p) {                        // จุดเย็นเยือก
      try { p.addEffect("slowness", 100, { amplifier: 0, showParticles: false }); } catch (e) {}
      try { p.onScreenDisplay.setActionBar("§b...หนาวขึ้นมาทันที..."); } catch (e) {}
      P(p, "ambient.cave", p.location.x, p.location.y, p.location.z, 1, 0.5);
    } },
    { id: "cry", w: 12, run(p) {                              // เสียงร้องไห้เข้ามาใกล้ขึ้นเรื่อย ๆ
      const l = p.location, d0 = around(l, 1);
      for (let k = 0; k < 4; k++) system.runTimeout(() => {
        const r = 22 - k * 5; const ox = (d0.x - l.x) * r, oz = (d0.z - l.z) * r;
        P(p, "mob.ghast.moan", l.x + ox, l.y, l.z + oz, 0.8 + k * 0.3, 0.55);
      }, k * 50);
    } },
    { id: "knockwood", w: 9, run(p) {                         // เคาะ 3 ครั้ง
      const l = p.location, a = around(l, 6);
      for (let k = 0; k < 3; k++) system.runTimeout(() => P(p, "dig.wood", a.x, l.y, a.z, 1.8, 0.8), k * 14);
    } },
    { id: "storm", w: 6, run(p) {                             // พายุฟ้าร้องโผล่มาเฉย ๆ
      try { p.dimension.setWeather("Thunder", 20 * 75); } catch (e) { try { p.runCommand("weather thunder 75"); } catch (e2) {} }
      P(p, "ambient.weather.thunder", p.location.x, p.location.y, p.location.z, 1.5, 0.7);
    } },
    { id: "ownvoice", w: 7, run(p) {                           // แชทปลอมใช้ชื่อตัวเอง
      try { p.sendMessage({ rawtext: [{ translate: "chat.type.text", with: [p.name, pick(["เธอไม่ได้อยู่คนเดียว", "ฉันเห็นเธอนะ", "หันมาสิ", "ตรงนี้...ใกล้ ๆ เธอ"])] }] }); } catch (e) {}
    } },
    { id: "heartstop", w: 8, run(p) {                           // เสียงทุกอย่างเงียบ แล้วหัวใจเต้นรัว
      try { p.runCommand("stopsound @s"); } catch (e) {}
      for (let k = 0; k < 5; k++) system.runTimeout(() => P(p, "mob.warden.heartbeat", p.location.x, p.location.y, p.location.z, 1.8, 1.2), 20 + k * 12);
    } },
    { id: "shadow", w: 8, run(p) {                              // เงาผียืนไกล ๆ แวบเดียว (ผีจริงแต่แค่โผล่ให้เห็น)
      if (api.glimpseGhost) api.glimpseGhost(p);
    } },
    { id: "falseflee", w: 6, run(p) {                           // กรีดร้องไกล ๆ เหมือนมีผู้เล่นอื่นถูกฆ่า
      const a = around(p.location, 30);
      P(p, "game.player.hurt", a.x, p.location.y, a.z, 2, 0.8);
      system.runTimeout(() => { try { api.fakeLeave?.(); } catch (e) {} }, 60);
    } },
  ];
}

function pickEvent(list, boost) {
  const pool = list.filter((e) => !recent.includes(e.id));
  const tot = pool.reduce((s, e) => s + e.w, 0);
  let r = Math.random() * tot;
  for (const e of pool) { r -= e.w; if (r <= 0) return e; }
  return pool[0];
}

export function initVariety(api) {
  const events = mkEvents(api);
  system.runInterval(() => {
    try {
      const now = system.currentTick;
      // อารมณ์ของคืนเปลี่ยนทุกวันในเกม
      const day = Math.floor(world.getAbsoluteTime() / 24000);
      if (day !== moodDay) {
        moodDay = day; mood = rollMood();
        if (mood.msg) system.runTimeout(() => { try { for (const p of world.getAllPlayers()) p.onScreenDisplay.setActionBar(mood.msg); } catch (e) {} }, 100);
      }
      if (api.ghostBusy()) return;
      for (const p of world.getAllPlayers()) {
        if (!nextEv.has(p.id)) { nextEv.set(p.id, now + rand(2 * MIN, 5 * MIN)); continue; }
        if (now < nextEv.get(p.id) || api.isHidden(p)) continue;
        const night = (() => { const t = world.getTimeOfDay(); return t >= 13000 && t <= 23000; })();
        nextEv.set(p.id, now + rand(3 * MIN, 7 * MIN) / (mood.ev * (night ? 1.4 : 1)));
        const ev = pickEvent(events);
        recent.push(ev.id); while (recent.length > 3) recent.shift();
        try { ev.run(p); } catch (e) {}
        break;
      }
    } catch (e) {}
  }, 100);
}
