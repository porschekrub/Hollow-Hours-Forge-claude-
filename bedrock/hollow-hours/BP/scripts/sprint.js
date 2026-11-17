import { world, system } from "@minecraft/server";
import { actionbar, sound, sub, valid } from "./util.js";
import { isHidden, makeNoise } from "./hide.js";

// =====================================================================
//  v1.7 วิ่งสุดแรง (stamina)
//  - กำลังวิ่งอยู่ แล้วกดย่อ 2 ครั้งเร็ว ๆ = วิ่งสุดแรง (เร็วกว่าวิ่งปกติ) · กดย่อ 2 ครั้งอีกที = เลิก
//  - stamina เต็ม -> หมดใน 10 วินาที · หมดแล้วเต็มใหม่ใน 15 วินาที
//  - ใกล้หมด = หอบ · หมดเกลี้ยง = หมดแรงช่วงสั้น ๆ (เดินช้า หายใจหนัก)
//  - วิ่งสุดแรงเสียงดัง ผีได้ยินไกลกว่าปกติ
// =====================================================================
const DRAIN = 1 / 200;            // 10 วินาที
const REFILL = 1 / 300;           // 15 วินาที
const MIN_START = 0.2;            // ต้องมี stamina อย่างน้อย 20% ถึงจะเริ่มได้
const TAP_WINDOW = 12;            // กดย่อครั้งที่สองภายใน 0.6 วินาที
const SPRINT_MEMORY = 16;         // เพิ่งวิ่งมาไม่เกิน 0.8 วินาที (กดย่อแล้วเกมตัดการวิ่ง)
const STOP_SPEED = 0.1;           // ช้ากว่านี้ (บล็อก/tick) ติดกันครึ่งวินาที = เลิกวิ่งสุดแรง
const SEG = 12;

const S = new Map();
let api = null;
function st(p) {
  let s = S.get(p.id);
  if (!s) { s = { sta: 1, turbo: false, sneak: false, lastPress: -99, lastSprint: -99, slow: 0, last: null, hudOn: false, nextPant: 0, panting: 0, exhausted: 0, nextAnim: 0, nextFx: 0 }; S.set(p.id, s); }
  return s;
}
export function staminaOf(p) { const s = S.get(p && p.id); return s ? s.sta : 1; }
export function isDashing(p) { const s = S.get(p && p.id); return !!(s && s.turbo); }

function bar(v) {
  const n = Math.round(v * SEG), col = v > 0.5 ? "§a" : v > 0.25 ? "§e" : "§c";
  return col + "▮".repeat(n) + "§8" + "▮".repeat(SEG - n);
}
function hud(p, s, now) {
  if (api && api.talkLock && (api.talkLock.get(p.id) ?? 0) > now) return;   // ผีกำลังพูดอยู่ ไม่ทับข้อความ
  const label = s.turbo ? "§f§lวิ่งสุดแรง" : s.exhausted > now ? "§c§lหมดแรง" : "§7พักหายใจ";
  actionbar(p, `${label} §r${bar(s.sta)}`);
}
function eff(p, id, t, a) { try { p.addEffect(id, t, { amplifier: a, showParticles: false }); } catch (_) {} }
function playAnim(p) {
  try { p.playAnimation("animation.ghost.player_dash", { blendOutTime: 0.25, stopExpression: "query.any_animation_finished", controller: "ghost_dash" }); }
  catch (_) { try { p.runCommand(`playanimation @s animation.ghost.player_dash none 0.25 "query.any_animation_finished" ghost_dash`); } catch (__) {} }
}
function start(p, s, now) {
  if (s.sta < MIN_START) {
    actionbar(p, "§cเหนื่อยเกินไป... §7รอให้หายใจทันก่อน");
    sound(p.dimension, "ghost.pant", p.location, 0.8, 1.0, 10);
    return;
  }
  s.turbo = true; s.slow = 0; s.nextAnim = now; s.nextFx = now;
  sound(p.dimension, "ghost.dash", p.location, 1.0, 1.0, 10);
}
function stop(p, s, now, why) {
  s.turbo = false;                                          // Speed ที่ให้ไว้หมดเองใน < 1 วินาที (ไม่ลบยาวิ่งเร็วของผู้เล่น)
  if (why === "empty") {
    s.exhausted = now + 50;
    eff(p, "slowness", 50, 1);
    sound(p.dimension, "ghost.pant.heavy", p.location, 1.0, 1.0, 12);
    s.panting = now + 70;
    try { p.runCommand("camerashake add @s 0.15 1.2 positional"); } catch (_) {}
  }
}

function tick(now) {
  for (const p of world.getAllPlayers()) {
    try {
      const s = st(p);
      const l = p.location;
      let sneak = false, sprint = false;
      try { sneak = p.isSneaking; } catch (_) {}
      try { sprint = p.isSprinting; } catch (_) {}
      const sp = s.last && s.last.dim === p.dimension.id ? Math.hypot(l.x - s.last.x, l.z - s.last.z) : 0;
      s.last = { x: l.x, z: l.z, dim: p.dimension.id };
      if (sprint) s.lastSprint = now;
      const hidden = isHidden(p);
      // ---- กดย่อ 2 ครั้ง
      if (sneak && !s.sneak) {
        if (now - s.lastPress <= TAP_WINDOW) {
          s.lastPress = -99;
          if (s.turbo) stop(p, s, now, "toggle");
          else if (!hidden && (now - s.lastSprint <= SPRINT_MEMORY || sp > 0.2)) start(p, s, now);
        } else s.lastPress = now;
      }
      s.sneak = sneak;
      // ---- ระหว่างวิ่งสุดแรง
      if (s.turbo) {
        if (hidden) { stop(p, s, now, "hide"); continue; }
        s.sta = Math.max(0, s.sta - DRAIN);
        if (now % 10 === 0) eff(p, "speed", 16, 2);                        // Speed III (เดินก็ยังเร็วกว่าวิ่งปกติ)
        if (now >= s.nextAnim) { s.nextAnim = now + 10; playAnim(p); }
        if (now >= s.nextFx) { s.nextFx = now + 10; try { makeNoise(p.dimension, p.location, 26, p.id); } catch (_) {} }
        if (s.sta < 0.3 && now >= s.nextPant) { s.nextPant = now + 30; sound(p.dimension, "ghost.pant", p.location, 0.6 + (0.3 - s.sta) * 2, 1.05, 10); }
        s.slow = sp < STOP_SPEED && !sneak ? s.slow + 1 : 0;
        if (s.sta <= 0) stop(p, s, now, "empty");
        else if (s.slow >= 10) stop(p, s, now, "stopped");
      } else if (s.sta < 1) {
        s.sta = Math.min(1, s.sta + REFILL);
        if (now < s.panting && now >= s.nextPant) { s.nextPant = now + 44; sound(p.dimension, "ghost.pant.heavy", p.location, 0.7, 1.0, 12); }
      }
      // ---- แถบ stamina (แสดงตอนวิ่งสุดแรง และตอนกำลังเต็มใหม่)
      if (s.turbo || s.sta < 1) { s.hudOn = true; if (now % 4 === 0 && !hidden) hud(p, s, now); }
      else if (s.hudOn) { s.hudOn = false; if (!hidden) actionbar(p, "§7พร้อมวิ่งสุดแรง §8(วิ่งแล้วกดย่อ 2 ครั้ง)"); }
    } catch (_) {}
  }
}

export function initSprint(a) {
  api = a;
  system.runInterval(() => { try { tick(system.currentTick); } catch (_) {} }, 1);
  sub("afterEvents", "playerSpawn", (ev) => { try { const s = S.get(ev.player.id); if (s) { s.turbo = false; s.sta = 1; } } catch (_) {} });
  sub("beforeEvents", "playerLeave", (ev) => { try { S.delete(ev.player.id); } catch (_) {} });
}
