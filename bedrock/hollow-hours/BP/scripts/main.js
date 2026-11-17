import { world, system } from "@minecraft/server";
import { initLang } from "./lang.js";
import { initTalk } from "./talk.js";
import { initCurse, takeBuff, openAsk, askLabel, tryAsk } from "./curse.js";
import { topBlock, isSheltered, isUndergroundAt } from "./util.js";
import { initBreach, doorDebug } from "./breach.js";
import { initBoards } from "./boards.js";
import { initFlashlight } from "./flashlight.js";
import { initSprint } from "./sprint.js";
import { initIntrude, chaseHook, isScripted, startKnock, startBreakIn, nextScareSound, SCARE_TAG, rollPower, ixOf, canStand, resumeChase, release, breachToward, digToward, followTo } from "./intrude.js";
import { initHide, isHidden, chaseCheck, startSearch, afterIntrusion, searchFollowTick, searchFollowDone } from "./hide.js";
import { initBookName } from "./bookname.js";
import { PROFILE, CREEP_BY, PERSONA, rollMod, effectiveBuff, moodGap, moodEv, initVariety } from "./variety.js";
import { initStalker, stalkerIndoor, stalkerOutdoor } from "./stalker.js";
// ---- v2.0: brought over from the Java edition
import { initStory, storyActive, forceStory, sendAlert, day } from "./story.js";
import { initAtmos, bloodMoon, forceBlood, startFog, stopFog, mods as nightMods, onBloodRise, isCalm } from "./atmos.js";
import { initSurvival, shock, setThirst, setSanity, getThirst, getSanity } from "./survival.js";
import { initGraves } from "./grave.js";
import { initBetray, inDeal } from "./betray.js";
import { initPhysics } from "./physics.js";
import { initBlood } from "./blood.js";
import { initNav } from "./nav.js";
import { initPhone } from "./phone.js";
import { initRescue, rescueCommand } from "./rescue.js";
import { initSelfTest } from "./selftest.js";

// ===== ตั้งค่า (หน่วย tick: 20 tick = 1 วินาที, 1200 tick = 1 นาที) =====
const MIN = 60 * 20;
const CFG = {
  // v1.6: ผีเกิดห่างขึ้น (เดิมถี่ไป)
  FIRST_SPAWN_MIN: 3 * MIN, FIRST_SPAWN_MAX: 5 * MIN,     // ผีตัวแรก: 3-5 นาทีหลังเข้าโลก (v1.9.4 เร็วขึ้น)
  GAP_MIN: 7 * MIN, GAP_MAX: 12 * MIN,                   // รอบถัดไป: 7-12 นาที (สุ่ม)
  QUICK_CHANCE: 0.15,                                    // 8% ที่รอบถัดไปโผล่เร็วกว่าปกติ
  QUICK_MIN: 4 * MIN, QUICK_MAX: 6 * MIN,
  RESPAWN_MIN: 15 * MIN, RESPAWN_MAX: 25 * MIN,          // ผีแต่ละตัวเดิมจะกลับมาได้ใน 15-25 นาที
  OMEN_MIN: 15 * 20, OMEN_MAX: 40 * 20,                  // ลางร้าย (เสียงแปลก ๆ) 15-40 วินาทีก่อนผีโผล่
  FALSE_ALARM_CHANCE: 0.12,                              // 25% มีแต่ลางร้าย ผีไม่โผล่ (ให้หลอนเล่น ๆ)
  FALSE_ALARM_GAP_MIN: 4 * MIN, FALSE_ALARM_GAP_MAX: 8 * MIN,
  MAX_ALIVE: 1,                                          // มีผีพร้อมกันได้สูงสุดกี่ตัว
  SPAWN_DIST_MIN: 22, SPAWN_DIST_MAX: 30,
  STALK_MIN: 14 * 20, STALK_MAX: 30 * 20,                // ยืนจ้อง/ค่อย ๆ คืบเข้ามา 14-30 วินาทีก่อนวิ่งไล่
  AMBUSH_DIST: 12,                                       // ถ้าเข้าใกล้ถึงระยะนี้ ผีจะบิดคอแล้ววิ่งไล่ทันที
  CHASE_RANGE: 36, LOSE_RANGE: 48, FAR_REMOVE: 80,
  GLIMPSE_CHANCE: 0.10,                                  // 30% ของการโผล่ จะเป็นแค่ "แวบเดียว" แล้วหายเมื่อถูกมอง
  CHASE_MAX_TICKS: 90 * 20,                              // ไล่ได้สูงสุด 90 วินาทีแล้วหายตัว
  AMBIENT_FIRST_MIN: 90 * 20, AMBIENT_FIRST_MAX: 5 * MIN,   // เพลงหลอนสุ่มตอนเล่นปกติ: ครั้งแรกหลังเข้าโลก 1.5-5 นาที
  AMBIENT_GAP_MIN: 4 * MIN, AMBIENT_GAP_MAX: 10 * MIN,       // ครั้งถัดไปห่างกัน 4-10 นาที (ผีไม่ได้เกิด)
  MUSIC_RANGE: 64,                                       // ผู้เล่นที่อยู่ใกล้ผีในระยะนี้จะได้ยินเพลงผีเกิด/เพลงไล่
  MUSIC_GRACE: 60,                                       // ผีหาย/หนีรอดแล้ว เพลงค่อยหยุดหลังผ่านไป 3 วินาที (tick)
  FAKE_JOIN_MIN: 5 * MIN, FAKE_JOIN_MAX: 14 * MIN,       // ข้อความ "เข้าเซิร์ฟ" ปลอม ทุก 5-14 นาที (ทุกคนเห็น)
  HOUSE_EVENT_CHANCE: 0.8,                               // ผู้เล่นอยู่ในบ้าน: 80% ผีจะมาที่ประตู/หน้าต่างแทนการโผล่ข้างนอก
  KNOCK_SHARE: 0.35,                                     // v1.7.1: ผีตัวจริง 35% = เคาะก่อน (65% ของนั้นเคาะแล้วพังเข้ามา), ที่เหลือ = บุกพังเข้ามาเลย
  REAL_KNOCK_ESCALATE: 0.65,                             // (การมาเคาะเฉย ๆ แล้วหาย ย้ายไปเป็นเรื่องหลอนในบ้านแล้ว)
  // v1.7 เรื่องหลอนตอนอยู่ในบ้าน (มาเคาะแล้วหาย / เสียงแปลก ๆ แล้วผีมายืนจ้องที่หน้าต่าง)
  INDOOR_FIRST_MIN: 3 * MIN, INDOOR_FIRST_MAX: 6 * MIN,  // ครั้งแรกหลังเข้าบ้าน
  INDOOR_NIGHT_MIN: 8 * MIN, INDOOR_NIGHT_MAX: 14 * MIN,  // กลางคืน ห่างกัน 4-8 นาที
  INDOOR_DAY_MIN: 14 * MIN, INDOOR_DAY_MAX: 22 * MIN,     // กลางวัน ห่างกัน 8-15 นาที
  INDOOR_WATCH_SHARE: 0.5,                               // ครึ่งหนึ่ง = เสียงแปลก ๆ + ผียืนจ้อง, ที่เหลือ = มาเคาะแล้วหาย
};
const UNBREAKABLE = new Set([
  "minecraft:bedrock", "minecraft:barrier", "minecraft:end_portal_frame", "minecraft:end_portal",
  "minecraft:nether_portal", "minecraft:command_block", "minecraft:chain_command_block",
  "minecraft:repeating_command_block", "minecraft:structure_block", "minecraft:jigsaw",
  "minecraft:reinforced_deepslate", "minecraft:light_block", "minecraft:border_block",
]);
const DIMS = ["overworld", "nether", "the_end"];
const rand = (a, b) => a + Math.random() * (b - a);
const meta = new Map();
const CREEP = { ...CREEP_BY, 5: { a: 25, b: 65, s0: 4, s1: 7 } };   // Bone Stag คืบถี่และไกลกว่าตัวอื่น
const CREEP_DEF = { a: 60, b: 140, s0: 2.5, s1: 4.5 };
const NO_BUFF = { dur: 1, vis: 1, dmg: 0, spd: 1 };   // บัฟจากการขอของ (ผีที่เกิดครั้งถัดไป)
const glimpse = new Set();   // ผีที่โผล่มาให้เห็นแวบเดียวแล้วหายเมื่อถูกมอง
const WHISPERS = [
  "§4อย่าร้องไห้...", "§c เสียงร้องไห้นั่น...ไม่ใช่ของคุณ ", "§4มันกำลังยิ้มอยู่", "§8§k|||§r §c มันหยุดร้องแล้ว §8§k|||", "§4ข้างหลัง", "§c อย่ากระพริบตา ",
  "§4หันหลังสิ...", "§c มันอยู่ตรงนั้น ", "§4อย่ามองมัน", "§c มันเห็นคุณแล้ว", "§4วิ่งสิ...",
  "§8§k||||§r §4มันอยู่ข้างหลังคุณ §8§k||||", "§c ไม่มีที่ให้ซ่อน ", "§4เงียบ ๆ ไว้...", "§c อย่าหันไป ",
];
const indoorNext = new Map();   // player.id -> tick ที่จะมีเรื่องหลอนในบ้านครั้งถัดไป
const talkLock = new Map();   // player.id -> tick until which a ghost reply is on screen
const leaving = new Set();    // ghosts that agreed to leave
function whisper(p) {
  if ((talkLock.get(p.id) ?? 0) > system.currentTick) return;
  try { p.onScreenDisplay.setActionBar(WHISPERS[Math.floor(Math.random() * WHISPERS.length)]); } catch (e) {}
}
function later(t, fn) { system.runTimeout(() => { try { fn(); } catch (e) {} }, t); }
const GLITCH = [["§4§l§kIIII§r §c§lวิ่ง §4§l§kIIII", "§8มันเห็นคุณแล้ว"], ["§4§l§kIII§r §c§lอยู่ข้างหลังคุณ §4§l§kIII", "§8อย่าหันไป"], ["§4§l§kIIII§r §c§lมันเจอคุณแล้ว §4§l§kIIII", ""], ["§4§l§kIII§r §c§lหยุดร้องไห้ §4§l§kIII", "§8ได้ยินไหม"]];
// ===== v1.9.5 jumpscare สไตล์หนังผี: ชอต (กระแทกหลายชั้น+จอดำ/แดงวูบ+กล้องสั่น) → ช็อกค้าง → หัวใจเต้น/เสียงกระซิบข้างหู =====
function jumpscare(p, strong) {
  const l = p.location;
  try { shock(p, strong ? 10 : 5); } catch (e) {}
  // 1) ชอต: เสียงสายไวโอลินกรีดแหลม + คอรัสกรีด + เสียงคำรามทุ้ม ซ้อนกันพร้อมกัน
  playAt(p, "ghost.horror.sting", l.x, l.y, l.z, 3, rand(0.95, 1.05));
  playAt(p, "ghost.horror.choir", l.x, l.y, l.z, strong ? 3 : 2, rand(0.92, 1.05));
  playAt(p, "ghost.growl", l.x, l.y, l.z, strong ? 2.5 : 1.5, 0.6);
  playAt(p, "ghost.snap", l.x, l.y, l.z, 2, 0.8);
  playAt(p, "ambient.weather.thunder", l.x, l.y, l.z, 1.2, 0.5);
  playAt(p, "mob.ghast.scream", l.x, l.y, l.z, 2, 0.5);
  // 2) ภาพ: จอดำวูบ → แดงวูบ → (ถ้าแรง) ดำอีกรอบ + กล้องสั่น 2 ระลอก
  try { p.runCommand("camerashake add @s 1 2.4 rotational"); } catch (e) {}
  try { p.runCommand("camera @s fade time 0.02 0.12 0.3 color 0 0 0"); } catch (e) {}
  later(14, () => p.runCommand("camera @s fade time 0.03 0.06 0.3 color 120 0 0"));
  later(12, () => p.runCommand("camerashake add @s 0.6 1.2 positional"));
  if (strong) later(30, () => p.runCommand("camera @s fade time 0.03 0.18 0.5 color 0 0 0"));
  // 3) ข้อความกระตุกแบบกลิตช์ (ตัวอักษรเพี้ยน → ข้อความจริง)
  try {
    const g = GLITCH[Math.floor(Math.random() * GLITCH.length)];
    const o = { fadeInDuration: 0, stayDuration: 6, fadeOutDuration: 0 };
    p.onScreenDisplay.setTitle("§4§l§kIIIIIIIIII", { subtitle: "§k§8IIIIIII", ...o });
    later(4, () => p.onScreenDisplay.setTitle(g[0], { subtitle: g[1], fadeInDuration: 0, stayDuration: 24, fadeOutDuration: 10 }));
  } catch (e) {}
  // 4) ตาพร่า/ตาบอดวูบ 2 จังหวะ + มืดตามมา
  try { p.addEffect("blindness", strong ? 14 : 8, { amplifier: 0, showParticles: false }); } catch (e) {}
  if (strong) {
    later(22, () => p.addEffect("blindness", 12, { amplifier: 0, showParticles: false }));
    later(36, () => p.addEffect("darkness", 140, { amplifier: 0, showParticles: false }));
  }
  // 5) ช็อกค้าง: เงียบไปแป๊บ แล้วหัวใจเต้นรัว + เสียงกระซิบข้างหูตามมา
  if (strong) {
    later(46, () => { const q = p.location, v = p.getViewDirection(); playAt(p, "ghost.horror.whisper", q.x + v.x * 1.2, q.y + 1.6, q.z + v.z * 1.2, 1.8, rand(0.85, 1)); });
    for (let k = 0; k < 7; k++) later(52 + k * Math.round(26 - k * 2), () => { const q = p.location; playAt(p, "ghost.horror.heart", q.x, q.y, q.z, Math.max(0.5, 2.4 - k * 0.25), 0.9 + k * 0.03); });
  }
}

// ===== v1.9.5 ความกดดัน: ผีอยู่ใกล้ = หัวใจเต้นเร็วขึ้น + เสียงกระซิบข้างหลัง (หนังผีสร้างอารมณ์ก่อนช็อก) =====
const dreadAt = new Map();
function dreadTick(now) {
  for (const pl of world.getAllPlayers()) {
    try {
      let dmin = 999;
      for (let i = 1; i <= 5; i++) {
        for (const g of pl.dimension.getEntities({ type: `ghost:ghost_${i}`, location: pl.location, maxDistance: 46 })) {
          if (g.hasTag(SCARE_TAG)) continue;
          const d = Math.hypot(g.location.x - pl.location.x, g.location.z - pl.location.z);
          if (d < dmin) dmin = d;
        }
      }
      if (dmin > 45) { dreadAt.delete(pl.id); continue; }
      const k = Math.min(1, Math.max(0, 1 - dmin / 45));
      let st = dreadAt.get(pl.id);
      if (!st) { st = { hb: 0, wh: now + 100, drone: false }; dreadAt.set(pl.id, st); }
      const l = pl.location;
      if (!st.drone) { st.drone = true; playAt(pl, "ghost.horror.drone", l.x, l.y, l.z, 1.4, 1); }
      if (now >= st.hb) { playAt(pl, "ghost.horror.heart", l.x, l.y, l.z, 0.5 + 1.8 * k, 0.85 + 0.45 * k); st.hb = now + Math.round(34 - 20 * k); }
      if (k > 0.35 && now >= st.wh) {
        const v = pl.getViewDirection();
        playAt(pl, "ghost.horror.whisper", l.x - v.x * 2, l.y + 1.6, l.z - v.z * 2, 1.3, rand(0.9, 1.05));
        st.wh = now + Math.round(rand(160, 320) * (1.3 - k));
      }
    } catch (e) {}
  }
}
system.runInterval(() => { try { dreadTick(system.currentTick); } catch (e) {} }, 10);

// ===== ข้อความปลอม "มีคนเข้าเซิร์ฟ" (ทุกคนในเซิร์ฟเห็นเหมือนกัน) =====
const NAME_A = ["Dark", "Shadow", "Night", "Pixel", "Ninja", "Cool", "Epic", "Blue", "Red", "Ghost", "Wolf", "Fox", "Tiger", "Dragon", "Alpha", "Neko", "Ice", "Fire", "Mega", "Lucky", "Toxic", "Crazy", "Happy", "Sleepy", "Boss", "Mini", "Super", "Silent", "Lone", "Frost"];
const NAME_B = ["Gamer", "Player", "Miner", "Builder", "Craft", "Slayer", "King", "Master", "Hunter", "Boy", "Girl", "Cat", "Panda", "Bear", "Pro", "Noob", "Steve", "Zero", "Mod", "Warrior", "Wizard", "Sniper", "Blox", "Creeper", "Knight"];
const NAME_TH = ["Nong", "Beam", "Ploy", "Tong", "Ice", "Fah", "Ning", "Pop", "Max", "Mild", "Kao", "Tum", "Bank", "Film", "Fluke", "Gun", "Jane", "Mook", "Nut", "Oat", "Pang", "Ram", "Sam", "Toey", "Mint", "Pun", "Prem", "Dew", "Net", "Boat"];
const pickOf = (a) => a[Math.floor(Math.random() * a.length)];
const digits = (n) => String(Math.floor(Math.random() * Math.pow(10, n))).padStart(n, "0");
function randomName() {
  const r = Math.random();
  let nm;
  if (r < 0.22) nm = pickOf(NAME_A) + pickOf(NAME_B) + digits(1 + Math.floor(Math.random() * 3));
  else if (r < 0.34) nm = "xX" + pickOf(NAME_A) + pickOf(NAME_B) + "Xx";
  else if (r < 0.52) nm = pickOf(NAME_TH) + digits(2 + Math.floor(Math.random() * 3));
  else if (r < 0.62) nm = pickOf(NAME_TH) + "_" + pickOf(["TH", "YT", "Pro", "Gamer", "Zaa", "Jung"]);
  else if (r < 0.74) nm = pickOf(NAME_A) + " " + pickOf(NAME_B) + " " + digits(2);
  else if (r < 0.86) nm = pickOf(NAME_A) + "_" + pickOf(NAME_B);
  else if (r < 0.94) nm = pickOf(NAME_B) + pickOf(NAME_TH) + digits(2);
  else nm = pickOf(NAME_TH) + pickOf(NAME_A) + digits(3);
  return nm.slice(0, 16);
}
let lastFakeJoin = -1e9;
function fakeJoin(now) {
  if (now !== undefined && now - lastFakeJoin < 60 * 20) return;
  const msg = { rawtext: [{ translate: "multiplayer.player.joined", with: [randomName()] }] };
  try { world.sendMessage(msg); }
  catch (e) { try { for (const p of world.getAllPlayers()) p.sendMessage(msg); } catch (e2) {} }
  lastFakeJoin = now ?? system.currentTick;
}

// ===== ระบบเพลงหลอน =====
// ambient1/2 = เพลงสุ่มตอนเล่นปกติ & ช่วงลางร้าย (ผีอาจไม่เกิด) | spawn = ตอนผีเกิด/ยืนจ้อง | chase = ตอนผีไล่
// v1.6: เพลงใหม่ทั้งหมด (tools/music.py) — เพลงตอนผีไม่เกิด (กล่องดนตรี/เปียโน) ไม่เหมือนเพลงตอนผีเกิด (เครื่องสาย/คอรัส/หัวใจเต้น)
const MUSIC = {
  ambient1: { id: "ghost.music.ambient1", len: 50 * 20, pri: 1 },
  ambient2: { id: "ghost.music.ambient2", len: 48 * 20, pri: 1 },
  spawn: { id: "ghost.music.spawn", len: 40 * 20, pri: 2 },
  chase: { id: "ghost.music.chase", len: 32 * 20, pri: 3 },
};
const musicState = new Map();   // player.id -> { key, until, wantedAt }
const ambientNext = new Map();  // player.id -> tick ที่จะเล่นเพลงสุ่มครั้งถัดไป
function musicStopSound(p, key) {
  try { p.runCommand(`stopsound @s ${MUSIC[key].id}`); } catch (e) {}
}
function musicStop(p) {
  const s = musicState.get(p.id);
  if (s) musicStopSound(p, s.key);
  musicState.delete(p.id);
}
function musicPlay(p, key, now) {
  const s = musicState.get(p.id), def = MUSIC[key];
  if (s && now < s.until && s.pri > def.pri) return false;                 // เพลงที่สำคัญกว่ากำลังเล่นอยู่
  if (s && s.key === key && now < s.until) { s.wantedAt = now; return true; }  // เพลงเดิมยังไม่จบ
  if (s && s.key !== key) musicStopSound(p, s.key);
  const l = p.location;
  playAt(p, def.id, l.x, l.y, l.z, 1, 1);
  musicState.set(p.id, { key, pri: def.pri, until: now + def.len - 5, wantedAt: now });
  return true;
}
function randomAmbient() { return Math.random() < 0.5 ? "ambient1" : "ambient2"; }

function musicTick(now) {
  for (const dim of dims()) {
    let players = [];
    try { players = dim.getPlayers(); } catch (e) { continue; }
    if (players.length === 0) continue;
    let ghosts = [];
    try { ghosts = dim.getEntities({ families: ["ghost"] }); } catch (e) {}
    for (const p of players) {
      try {
        // หาเพลงที่ควรเล่นจากผีที่อยู่ใกล้ (ไล่ > เกิด)
        let want = null;
        for (const g of ghosts) {
          const dx = p.location.x - g.location.x, dy = p.location.y - g.location.y, dz = p.location.z - g.location.z;
          if (Math.sqrt(dx * dx + dy * dy + dz * dz) > CFG.MUSIC_RANGE) continue;
          const state = g.getProperty("ghost:state") ?? 0;
          const m = meta.get(g.id);
          if (state === 5) continue;                          // กำลังเคาะประตู: เงียบ ให้ได้ยินแต่เสียงเคาะ
          if (state === 0 && m && !m.stalking) continue;      // ผีเสียเป้า/หนีรอดแล้ว ปล่อยให้เพลงจางหาย
          if ((state >= 8 && state <= 10) || (m && m.search)) { want = "spawn"; continue; }   // กำลังเดินหา: เพลงหลอนเบา ๆ
          if (state >= 1) { want = "chase"; break; }          // ไล่ / ทุบประตู / มุด / คลานไล่
          want = "spawn";
        }
        const s = musicState.get(p.id);
        // ผีเลิกไล่แล้วเปลี่ยนเป็นเดินหา: ตัดเพลงไล่ เปลี่ยนเป็นเพลงหลอนเงียบ ๆ
        if (want === "spawn" && s && s.key === "chase" && now - s.wantedAt > CFG.MUSIC_GRACE) musicStop(p);
        if (want) { musicPlay(p, want, now); continue; }
        if (s && s.pri >= 2 && now - s.wantedAt > CFG.MUSIC_GRACE) { musicStop(p); continue; }   // ผีหมดแล้ว หยุดเพลงผี
        // เพลงหลอนสุ่มตอนเล่นปกติ (ผีไม่ได้เกิด)
        if (!storyActive() || isCalm(p)) continue;
        if (!ambientNext.has(p.id)) ambientNext.set(p.id, now + rand(CFG.AMBIENT_FIRST_MIN, CFG.AMBIENT_FIRST_MAX));
        if (now >= ambientNext.get(p.id) && !(s && now < s.until) && !omen) {
          const k = randomAmbient();
          if (musicPlay(p, k, now)) ambientNext.set(p.id, now + MUSIC[k].len + rand(CFG.AMBIENT_GAP_MIN, CFG.AMBIENT_GAP_MAX));
        }
      } catch (e) {}
    }
  }
}

// ===== jumpscare ตอนโดนผีฆ่า =====
const killedByGhost = new Set();
function deathScare(p, big) {
  const l = p.location;
  playAt(p, "ghost.jumpscare", l.x, l.y, l.z, 1, 1);
  playAt(p, "ghost.growl", l.x, l.y, l.z, 2.5, 0.7);
  playAt(p, "mob.ghast.scream", l.x, l.y, l.z, 2, 0.5);
  try { p.runCommand("camerashake add @s 1 1.8 positional"); } catch (e) {}
  try { p.runCommand("camera @s fade time 0.03 0.25 0.9 color 110 0 0"); } catch (e) {}
  try {
    const T = big ? ["§4§lมันได้คุณแล้ว", "§4§lบอกแล้วว่าอย่าหันหลัง", "§4§lคุณไม่รอด"] : ["§4§lมันยังไม่หยุด", "§c§lมันรออยู่ตรงนั้น"];
    p.onScreenDisplay.setTitle(T[Math.floor(Math.random() * T.length)], { fadeInDuration: 0, stayDuration: 30, fadeOutDuration: 20 });
  } catch (e) {}
}
world.afterEvents.entityDie.subscribe((ev) => {
  try {
    const dead = ev.deadEntity;
    if (!dead || dead.typeId !== "minecraft:player") return;
    const killer = ev.damageSource && ev.damageSource.damagingEntity;
    if (!killer || !String(killer.typeId).startsWith("ghost:ghost_")) return;
    killedByGhost.add(dead.id);
    const s = musicState.get(dead.id);
    if (s) { musicStopSound(dead, s.key); musicState.delete(dead.id); }
    const l = dead.location;
    // เสียงกรี๊ดทันทีที่ตาย (คนอื่นที่อยู่ใกล้ก็ได้ยินด้วย)
    try { dead.dimension.runCommand(`playsound ghost.jumpscare @a[x=${l.x.toFixed(1)},y=${l.y.toFixed(1)},z=${l.z.toFixed(1)},r=30] ${l.x.toFixed(1)} ${l.y.toFixed(1)} ${l.z.toFixed(1)} 1 1`); } catch (e) {}
    try { dead.playSound("ghost.jumpscare", { volume: 1, pitch: 1 }); } catch (e) {}
    deathScare(dead, true);
  } catch (e) {}
});
world.afterEvents.playerSpawn.subscribe((ev) => {
  try {
    if (ev.initialSpawn || !killedByGhost.has(ev.player.id)) return;
    killedByGhost.delete(ev.player.id);
    musicState.delete(ev.player.id);
    const p = ev.player;
    system.runTimeout(() => { try { deathScare(p, false); } catch (e) {} }, 30);   // กรี๊ดซ้ำตอนเกิดใหม่
  } catch (e) {}
});

const nextAt = {}, wasAlive = {};
let nextSpawnAt = 0, nextFakeJoinAt = 0;
let omen = null;
let started = false;

function dims() {
  const out = [];
  for (const id of DIMS) { try { out.push(world.getDimension(id)); } catch (e) {} }
  return out;
}

function vanish(g) {
  try {
    const l = g.location;
    g.dimension.runCommand(`playsound mob.warden.nearby_close @a ${Math.floor(l.x)} ${Math.floor(l.y)} ${Math.floor(l.z)} 1 0.5`);
    g.remove();
  } catch (e) {}
}

function nextGap() {
  const g = Math.random() < CFG.QUICK_CHANCE ? rand(CFG.QUICK_MIN, CFG.QUICK_MAX) : rand(CFG.GAP_MIN, CFG.GAP_MAX);
  return g * moodGap() * nightMods().gap;   // blood moon: three times as often
}

function playAt(p, id, x, y, z, vol, pitch) {
  try { p.runCommand(`playsound ${id} @s ${x.toFixed(1)} ${y.toFixed(1)} ${z.toFixed(1)} ${vol} ${pitch}`); } catch (e) {}
}

// cos ของมุมระหว่างทิศที่ผู้เล่นมอง กับทิศไปหาผี (1 = จ้องตรง ๆ, -1 = ผีอยู่ข้างหลัง)
function lookDot(p, g) {
  const v = p.getViewDirection();
  const dx = g.location.x - p.location.x, dz = g.location.z - p.location.z;
  const dl = Math.hypot(dx, dz) || 1, vl = Math.hypot(v.x, v.z) || 1;
  return (v.x * dx + v.z * dz) / (dl * vl);
}

function findSpot(player, behind, dmin, dmax) {
  const dim = player.dimension;
  const px = Math.floor(player.location.x), py = Math.floor(player.location.y), pz = Math.floor(player.location.z);
  let underground = false;
  try { underground = isUndergroundAt(dim, player.location); } catch (e) {}
  let backAng = null;
  if (behind) { try { const v = player.getViewDirection(); backAng = Math.atan2(v.z, v.x) + Math.PI; } catch (e) {} }
  for (let t = 0; t < 30; t++) {
    const ang = (backAng !== null && t < 20) ? backAng + rand(-0.9, 0.9) : Math.random() * Math.PI * 2;
    const dist = rand(dmin ?? CFG.SPAWN_DIST_MIN, dmax ?? CFG.SPAWN_DIST_MAX);
    const x = Math.floor(px + Math.cos(ang) * dist), z = Math.floor(pz + Math.sin(ang) * dist);
    try {
      if (underground) {
        for (const dy of [0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6]) {
          const y = py + dy;
          const b = dim.getBlock({ x, y, z }), up = dim.getBlock({ x, y: y + 1, z }), dn = dim.getBlock({ x, y: y - 1, z });
          const up2 = dim.getBlock({ x, y: y + 2, z });
          if (b && up && up2 && dn && b.isAir && up.isAir && up2.isAir && !dn.isAir && !dn.isLiquid) return { x: x + 0.5, y, z: z + 0.5 };
        }
      } else {
        const top = topBlock(dim, x, z, py + 40, py - 40);
        if (!top) continue;
        const id = top.typeId;
        if (id.includes("lava") || id.includes("leaves")) continue;
        if (top.isLiquid) return { x: x + 0.5, y: top.location.y, z: z + 0.5 };
        return { x: x + 0.5, y: top.location.y + 1, z: z + 0.5 };
      }
    } catch (e) {}
  }
  return null;
}

// ลางร้าย: เสียงแปลก ๆ รอบตัวผู้เล่น ก่อนผีจะโผล่ (หรือไม่โผล่เลยก็ได้)
// ===== สไตล์ Cave Dweller: เลียนเสียงที่ผู้เล่นคุ้นเคย =====
function isUnderground(p) {
  try { return isUndergroundAt(p.dimension, p.location); } catch (e) { return false; }
}

function mimicSound(p) {
  const l = p.location, a = Math.random() * Math.PI * 2, d = rand(4, 14);
  const x = l.x + Math.cos(a) * d, z = l.z + Math.sin(a) * d;
  const pool = [
    () => playAt(p, "random.door_open", x, l.y, z, 1, 1),                       // ประตูเปิดเองทั้งที่ไม่มีใคร
    () => { playAt(p, "random.door_close", x, l.y, z, 1, 1); },
    () => playAt(p, "mob.creeper.say", x, l.y, z, 1.4, 1),                      // เสียงครีปเปอร์ฟู่ข้างหลัง
    () => playAt(p, "mob.zombie.say", x, l.y, z, 1, rand(0.7, 1.1)),
    () => playAt(p, "mob.skeleton.say", x, l.y, z, 1, 1),
    () => playAt(p, "mob.villager.idle", x, l.y, z, 1, rand(0.5, 0.8)),         // เสียงชาวบ้านเพี้ยน ๆ ในถ้ำ
    () => playAt(p, "random.chestopen", x, l.y, z, 1, 1),
    () => playAt(p, "dig.wood", x, l.y, z, 1.5, 1),                              // เคาะไม้
    () => playAt(p, "game.player.hurt", x, l.y, z, 1, 1),                        // เสียงเหมือนมีคนบาดเจ็บ
    () => playAt(p, "ambient.cave", l.x, l.y, l.z, 1, 1),
    () => playAt(p, "mob.ghast.moan", x, l.y, z, 1.3, rand(0.5, 0.8)),            // เสียงร้องไห้ไกล ๆ ในความมืด (สไตล์ Don't Cry)
    () => { playAt(p, "mob.ghast.moan", x, l.y, z, 1.2, 0.6); system.runTimeout(() => playAt(p, "mob.ghast.moan", x, l.y, z, 1.4, 0.55), 40); },
    () => { try { p.sendMessage({ rawtext: [{ translate: "chat.type.text", with: [p.name, ["ช่วยด้วย", "มันอยู่ข้างหลังเธอ", "อย่าหันไป", "ฉันอยู่ตรงนี้", "ได้ยินไหม"][Math.floor(Math.random() * 5)]] }] }); } catch (e) {} },  // แชทปลอมใช้ชื่อตัวเอง
    () => {                                                                       // เสียงเดินเหมือนผู้เล่นอีกคน ตามหลังมา 4-5 ก้าว
      for (let k = 0; k < 4; k++) system.runTimeout(() => { try { playAt(p, "step.stone", x, l.y, z, 1, rand(0.9, 1.1)); } catch (e) {} }, k * 8);
    },
    () => fakeJoin(system.currentTick),                                           // ข้อความ "เข้าร่วมเกม" ปลอม (ทุกคนเห็น)
  ];
  pool[Math.floor(Math.random() * pool.length)]();
}

function omenSound(p, progress) {
  const l = p.location;
  const a = Math.random() * Math.PI * 2, d = rand(5, 12);
  const x = l.x + Math.cos(a) * d, z = l.z + Math.sin(a) * d;
  if (Math.random() < 0.45) { mimicSound(p); return; }
  const r = Math.random();
  if (r < 0.40) playAt(p, "ghost.creak", x, l.y, z, rand(0.6, 1.2), rand(0.55, 1.0));
  else if (r < 0.65) playAt(p, "ambient.cave", l.x, l.y, l.z, 1, rand(0.5, 0.9));
  else if (r < 0.85) playAt(p, "mob.warden.heartbeat", l.x, l.y, l.z, 0.5 + progress, 0.9);
  else playAt(p, "ghost.growl", x, l.y, z, rand(0.2, 0.5), rand(0.4, 0.7));
  if (progress > 0.7 && Math.random() < 0.5) { try { p.addEffect("darkness", 50, { amplifier: 0, showParticles: false }); } catch (e) {} }
}

function spawnTick(now) {
  // v2.0: the first nights are quiet - nothing haunts anyone until the story starts
  if (!storyActive()) { started = false; omen = null; return; }
  if (!started) {
    started = true;
    nextSpawnAt = now + rand(CFG.FIRST_SPAWN_MIN, CFG.FIRST_SPAWN_MAX);
    for (let i = 1; i <= 5; i++) { nextAt[i] = 0; wasAlive[i] = false; }
    nextFakeJoinAt = now + rand(CFG.FAKE_JOIN_MIN, CFG.FAKE_JOIN_MAX);
  }
  // ----- ข้อความปลอมว่ามีคนเข้าเซิร์ฟ (สุ่มชื่อ ทุกคนเห็น) -----
  if (now >= nextFakeJoinAt) {
    nextFakeJoinAt = now + rand(CFG.FAKE_JOIN_MIN, CFG.FAKE_JOIN_MAX);
    try { if (world.getAllPlayers().length > 0) fakeJoin(now); } catch (e) {}
  }
  const alive = {};
  let total = 0;
  for (let i = 1; i <= 5; i++) {
    alive[i] = 0;
    for (const d of dims()) { try { alive[i] += d.getEntities({ type: `ghost:ghost_${i}` }).filter((e) => !e.hasTag(SCARE_TAG)).length; } catch (e) {} }   // ผีมาหลอกตอนอยู่ในบ้านไม่นับ
    total += alive[i];
    if (wasAlive[i] && alive[i] === 0) nextAt[i] = now + rand(CFG.RESPAWN_MIN, CFG.RESPAWN_MAX);
    wasAlive[i] = alive[i] > 0;
  }

  // ----- เสียงหลอนสุ่ม ๆ ระหว่างเล่นปกติ (ในถ้ำถี่กว่า) -----
  if (!omen && total === 0) {
    try {
      for (const pl of world.getDimension("overworld").getPlayers()) {
        if (Math.random() < (isUnderground(pl) ? 0.012 : 0.003)) mimicSound(pl);
      }
    } catch (e) {}
    // ----- v1.7 อยู่ในบ้าน: มีอะไรมาเคาะประตู/หน้าต่าง (ไม่พัง) หรือมีเสียงแปลก ๆ แล้วผีมายืนจ้องที่หน้าต่าง -----
    try {
      const t = world.getTimeOfDay(), night = t >= 13000 && t <= 23000;
      for (const pl of world.getDimension("overworld").getPlayers()) {
        if (isHidden(pl) || !isSheltered(pl) || isCalm(pl)) continue;
        if (!indoorNext.has(pl.id)) { indoorNext.set(pl.id, now + rand(CFG.INDOOR_FIRST_MIN, CFG.INDOOR_FIRST_MAX)); continue; }
        if (now < indoorNext.get(pl.id)) continue;
        if (nextSpawnAt - now < 2 * MIN) { indoorNext.set(pl.id, now + rand(1 * MIN, 2 * MIN)); continue; }   // ผีตัวจริงใกล้มาแล้ว อย่าแย่งคิว
        indoorNext.set(pl.id, now + (night ? rand(CFG.INDOOR_NIGHT_MIN, CFG.INDOOR_NIGHT_MAX) : rand(CFG.INDOOR_DAY_MIN, CFG.INDOOR_DAY_MAX)));
        const ok = stalkerIndoor(pl);                       // v1.9: ผีที่มาหลอกในบ้านเป็น stalker (สุ่มตัว/เหตุการณ์ทุกครั้ง)
        if (ok) { nextSpawnAt = Math.max(nextSpawnAt, now + 90 * 20); break; }   // v1.7.1: แค่ไม่ให้ซ้อนกัน (เดิมเลื่อนผีตัวจริงออกไปเรื่อย ๆ จนไม่มาพังบ้านเลย)
      }
    } catch (e) {}
  }

  // ----- กำลังอยู่ในช่วงลางร้าย -----
  if (omen) {
    const p = omen.player;
    let ok = true;
    try { p.location.x; } catch (e) { ok = false; }
    if (!ok || total >= Math.max(1, nightMods().maxAlive)) { omen = null; nextSpawnAt = now + nextGap(); return; }
    const progress = Math.min(1, (now - omen.start) / (omen.end - omen.start));
    if (Math.random() < 0.7) { try { omenSound(p, progress); } catch (e) {} }
    if (now < omen.end) return;
    const o = omen; omen = null;
    if (o.falseAlarm) { nextSpawnAt = now + rand(CFG.FALSE_ALARM_GAP_MIN, CFG.FALSE_ALARM_GAP_MAX); return; }
    const ready = [];
    for (let i = 1; i <= 5; i++) if (alive[i] === 0 && now >= nextAt[i]) ready.push(i);
    // ผู้เล่นหลบอยู่ในบ้าน: ผีมาที่ประตู/หน้าต่าง (เคาะแล้วหาย หรือบุกพังเข้ามา)
    if (ready.length && Math.random() < CFG.HOUSE_EVENT_CHANCE && isSheltered(p)) {
      const pk = ready[Math.floor(Math.random() * ready.length)];
      const ok = Math.random() < CFG.KNOCK_SHARE ? startKnock(p, pk, Math.random() < CFG.REAL_KNOCK_ESCALATE) : startBreakIn(p, pk);
      if (ok) { nextSpawnAt = now + nextGap(); return; }
    }
    // v1.9: ลางร้ายที่เคยเป็น "ผีแวบเดียวหาย" ตอนนี้เป็น stalker แทน (ไม่ใช่ผีไล่ล่า)
    if (Math.random() < (isUnderground(p) ? 0.2 : CFG.GLIMPSE_CHANCE)) {
      nextSpawnAt = now + (stalkerOutdoor(p) ? nextGap() : rand(2 * MIN, 4 * MIN));
      return;
    }
    const spot = ready.length ? findSpot(p, true) : null;
    if (!spot) { nextSpawnAt = now + rand(4 * MIN, 8 * MIN); return; }  // ยังไม่พร้อม ลองใหม่ทีหลัง
    const pick = ready[Math.floor(Math.random() * ready.length)];
    try { p.dimension.spawnEntity(`ghost:ghost_${pick}`, spot); } catch (e) {}
    nextSpawnAt = now + nextGap();
    return;
  }

  // ----- ถึงเวลา (สุ่ม) ที่จะเริ่มลางร้ายหรือยัง -----
  if (total >= Math.max(CFG.MAX_ALIVE, nightMods().maxAlive) || now < nextSpawnAt) return;
  let players = [];
  try { players = world.getDimension("overworld").getPlayers().filter((q) => !isCalm(q)); } catch (e) {}
  if (players.length === 0) { nextSpawnAt = now + 2 * MIN; return; }
  const p = players[Math.floor(Math.random() * players.length)];
  const len = rand(CFG.OMEN_MIN, CFG.OMEN_MAX);
  omen = { player: p, start: now, end: now + len, falseAlarm: Math.random() < CFG.FALSE_ALARM_CHANCE };
  try { const k = randomAmbient(); if (musicPlay(p, k, now)) ambientNext.set(p.id, now + MUSIC[k].len + rand(CFG.AMBIENT_GAP_MIN, CFG.AMBIENT_GAP_MAX)); } catch (e) {}   // ลางร้ายมีเพลง ผีจะเกิดหรือไม่ก็ได้
}

function creepToward(g, p, step) {
  const dim = g.dimension;
  const dx = p.location.x - g.location.x, dz = p.location.z - g.location.z;
  const len = Math.hypot(dx, dz) || 1;
  const x = Math.floor(g.location.x + (dx / len) * step), z = Math.floor(g.location.z + (dz / len) * step);
  const gy = Math.floor(g.location.y);
  for (const dy of [0, 1, -1, 2, -2]) {
    const y = gy + dy;
    try {
      const b = dim.getBlock({ x, y, z }), up = dim.getBlock({ x, y: y + 1, z }), up2 = dim.getBlock({ x, y: y + 2, z }), dn = dim.getBlock({ x, y: y - 1, z });
      if (b && up && up2 && dn && b.isAir && up.isAir && up2.isAir && !dn.isAir) {
        g.teleport({ x: x + 0.5, y, z: z + 0.5 }, { facingLocation: p.location });
        return true;
      }
    } catch (e) {}
  }
  return false;
}


// ===== ลูกเล่นเฉพาะตัว =====
function snapNow(g, best, m) {
  m.stalking = false;
  g.triggerEvent("ghost:start_snap");
  jumpscare(best, true);
  try { best.addEffect("nausea", 100, { amplifier: 0, showParticles: false }); } catch (e) {}
}
function puff(dim, name, loc, spread) {
  try { dim.spawnParticle(name, { x: loc.x + rand(-spread, spread), y: loc.y + rand(0.2, 3.5), z: loc.z + rand(-spread, spread) }); } catch (e) {}
}
const GIMMICK = {
  // 1 Pale Wailer: เลียนเสียง + กรีดร้องจนจอดับ (สไตล์ Cave Dweller คลานสี่ขาตอนไล่)
  1(c) {
    const { best, bestD, state, m, g } = c;
    if (state === 0 && m.stalking && bestD <= 40 && Math.random() < 0.05) mimicSound(best);
    if (state === 2 && Math.random() < 0.02) {
      playAt(best, "ghost.growl", g.location.x, g.location.y, g.location.z, 2.5, 1.6);
      try { best.addEffect("blindness", 25, { amplifier: 0, showParticles: false }); } catch (e) {}
      try { best.runCommand("camerashake add @s 0.5 1 positional"); } catch (e) {}
    }
  },
  // 2 Drowned Bride: น้ำหยดตามตัว เสียงฟองน้ำ ถ่วงให้ช้าเหมือนจมน้ำเมื่อเข้าใกล้
  2(c) {
    const { best, bestD, state, g, dim } = c;
    if (Math.random() < 0.7) puff(dim, "minecraft:water_splash_particle", g.location, 0.6);
    if (bestD <= 24 && Math.random() < 0.08) playAt(best, Math.random() < 0.5 ? "bubble.up" : "liquid.water", g.location.x, g.location.y, g.location.z, 1.5, rand(0.6, 1));
    if (state === 2 && bestD <= 10) {
      try { best.addEffect("slowness", 30, { amplifier: 0, showParticles: false }); } catch (e) {}
      try { best.addEffect("mining_fatigue", 60, { amplifier: 0, showParticles: false }); } catch (e) {}
    }
  },
  // 3 Charred One: ไฟลุกตามตัว ควันและเสียงปะทุ ติดไฟผู้เล่นเมื่อเข้าถึงตัว
  3(c) {
    const { best, bestD, state, g, dim } = c;
    puff(dim, "minecraft:basic_flame_particle", g.location, 0.5); if (Math.random() < 0.3) puff(dim, "minecraft:lava_particle", g.location, 0.5);
    if (Math.random() < 0.4) puff(dim, "minecraft:basic_smoke_particle", g.location, 0.0);
    if (Math.random() < 0.12) playAt(best, "fire.fire", g.location.x, g.location.y, g.location.z, 1.5, rand(0.6, 1.0));
    if (Math.random() < 0.03) playAt(best, "mob.blaze.breathe", g.location.x, g.location.y, g.location.z, 1.5, 0.5);
    if (state === 2 && bestD <= 3.5) { try { best.setOnFire(4, true); } catch (e) {} }
  },
  // 4 Hollow Grin: ภาพกระตุกวาร์ปไปมา และวาร์ปไปด้านหลังเมื่อผู้เล่นไม่ได้มอง พร้อมเสียงหัวเราะ
  4(c) {
    const { g, best, bestD, state, now, m } = c;
    if (state === 0 && m.stalking && Math.random() < 0.2) {          // กระตุกตำแหน่งเล็กน้อย
      try {
        const x = g.location.x + rand(-1.2, 1.2), z = g.location.z + rand(-1.2, 1.2), y = Math.floor(g.location.y);
        const b = g.dimension.getBlock({ x: Math.floor(x), y, z: Math.floor(z) }), u = g.dimension.getBlock({ x: Math.floor(x), y: y + 1, z: Math.floor(z) });
        if (b && u && b.isAir && u.isAir) g.teleport({ x, y, z }, { facingLocation: best.location });
      } catch (e) {}
    }
    if (state === 2 && now >= (m.nextBlink || 0) && bestD > 6 && !isSheltered(best)) {
      m.nextBlink = now + rand(100, 220);
      let dot = 1; try { dot = lookDot(best, g); } catch (e) {}
      if (dot < 0.3) {
        const spot = findSpot(best, true, 5, 8);
        if (spot) {
          g.teleport(spot, { facingLocation: best.location });
          playAt(best, "mob.witch.ambient", spot.x, spot.y, spot.z, 2, 0.6);
        }
      }
    }
  },
  // 5 Bone Stag: เงียบ คืบเร็ว ห้ามมองตานาน ถ้าจ้องกันเกิน 2 วินาทีมันจะพุ่งใส่ ดับแสงรอบตัว
  5(c) {
    const { g, best, bestD, state, m } = c;
    if (state === 0 && m.stalking) {
      let dot = -1; try { dot = lookDot(best, g); } catch (e) {}
      if (dot > 0.85 && bestD < 24) {
        m.lookTicks = (m.lookTicks || 0) + 1;
        if (m.lookTicks >= 8) { snapNow(g, best, m); return; }
      } else m.lookTicks = 0;
      if (bestD <= 14 && Math.random() < 0.1) playAt(best, "mob.skeleton.step", g.location.x, g.location.y, g.location.z, 1.5, 0.6);
    }
    if (bestD <= 14 && Math.random() < 0.05) {
      playAt(best, "random.fizz", best.location.x, best.location.y, best.location.z, 0.8, 1);
      try { best.addEffect("darkness", 80, { amplifier: 1, showParticles: false }); } catch (e) {}
    }
  },
};

// บัฟความเร็ว: ปรับค่าความเร็วตอนไล่ (ค่าพื้นฐานของผีทุกตัวคือ 0.44 / ตอนโมโห 0.88)
function setSpeed(g, mult, rage) {
  try { g.getComponent("minecraft:movement").setCurrentValue((rage ? 0.88 : 0.44) * mult); } catch (e) {}
  try { g.getComponent("minecraft:underwater_movement").setCurrentValue((rage ? 0.6 : 0.3) * mult); } catch (e) {}
}
// ใช้โดยผลเสียของต้องสาป: ดึงผีมาทันที (ถ้ายังไม่มีผีในโลก)
function summonGhost(p) {
  let total = 0;
  for (const d of dims()) { try { total += d.getEntities({ families: ["ghost"] }).length; } catch (e) {} }
  if (total > 0) return false;
  const spot = findSpot(p, true);
  if (!spot) return false;
  p.dimension.spawnEntity(`ghost:ghost_${1 + Math.floor(Math.random() * 5)}`, spot);
  return true;
}

function ghostTick(now) {
  for (const dim of dims()) {
    let ghosts;
    try { ghosts = dim.getEntities({ families: ["ghost"] }); } catch (e) { continue; }
    if (ghosts.length === 0) continue;
    const players = dim.getPlayers();
    for (const g of ghosts) {
      try {
        if (leaving.has(g.id)) continue;
        const m = ensureMeta(g, now);
        const n = Number(g.typeId.slice(-1));
        const bf = effectiveBuff(m, n, NO_BUFF);
        // เป้าหมาย = ผู้เล่นที่ใกล้สุดที่ "ไม่ได้ซ่อน" (คนในตู้/ใต้เตียง ผีต้องหาเอง)
        let best = null, bestD = Infinity, hid = null, hidD = Infinity;
        for (const p of players) {
          if (isCalm(p)) continue;   // aboard the helicopter, or in the haven before they find it
          const dx = p.location.x - g.location.x, dy = p.location.y - g.location.y, dz = p.location.z - g.location.z;
          const d = Math.sqrt(dx * dx + dy * dy + dz * dz);
          if (isHidden(p)) { if (d < hidD) { hidD = d; hid = p; } continue; }
          if (d < bestD) { bestD = d; best = p; }
        }
        if ((!best && !hid) || Math.min(bestD, hidD) > CFG.FAR_REMOVE * bf.vis) { vanish(g); continue; }
        if (isScripted(m)) continue;   // กำลังทุบประตู / มุด / เคาะ / เดินหา — สคริปต์ควบคุมอยู่
        const age = now - m.born;
        if (g.getProperty("ghost:rage") === true && Math.random() < 0.5) puff(dim, "minecraft:villager_angry", g.location, 0.8);
        const CP = CREEP[n] ?? CREEP_DEF;
        if (age < 70) continue;
        // ===== ทุกคนแถวนี้ซ่อนอยู่: ผีต้องเดินหาเอง =====
        if (!best) {
          if (glimpse.has(g.id)) { glimpse.delete(g.id); vanish(g); continue; }
          if (m.appeaseUntil && now < m.appeaseUntil) continue;
          if ((g.getProperty("ghost:state") ?? 0) === 1) continue;
          startSearch(g, m, now, { near: hid.location });
          continue;
        }
        // ===== ถูกขอของ: ยืนนิ่งจ้องผู้เล่น ไม่ไล่ (หมดเวลา 30 วิ ที่รีเซ็ตทุกครั้งที่ขอ -> กลับมาไล่ต่อ) =====
        if (m.appeaseUntil) {
          if (now < m.appeaseUntil) { g.teleport(g.location, { facingLocation: best.location }); continue; }
          m.appeaseUntil = 0; m.stalking = false; m.chaseStart = 0; m.lostAt = 0;
          try { g.triggerEvent("ghost:resume"); } catch (e) {}
          jumpscare(best, true);
          continue;
        }
        if (glimpse.has(g.id)) {   // โหมดแวบเดียว: ยืนนิ่งจ้อง พอถูกมองเต็ม ๆ จะหายพร้อมเสียงตกใจ
          g.teleport(g.location, { facingLocation: best.location });
          let dot = -1; try { dot = lookDot(best, g); } catch (e) {}
          if (dot > 0.75 && bestD < 45) m.seen = (m.seen || 0) + 1;
          if (m.seen >= 2) { jumpscare(best, false); glimpse.delete(g.id); vanish(g); }
          else if (age > 70 + 25 * 20 * bf.dur) { glimpse.delete(g.id); vanish(g); }
          continue;
        }
        const state = g.getProperty("ghost:state") ?? 0;
        // กู้สถานะหลังโหลดโลกใหม่ (สคริปต์ลืมว่ากำลังทุบ/มุด/เคาะอยู่)
        if (state >= 3 && state <= 6) {
          if (state === 5) { try { g.remove(); } catch (e) {} continue; }
          try { g.triggerEvent("ghost:crawl_chase"); } catch (e) {}
          continue;
        }
        if (state >= 8) { startSearch(g, m, now, { near: best.location }); continue; }
        if (state !== 2 && state !== 7) m.wasChasing = false;

        if (state === 0) {
          if (m.stalking) {
            g.teleport(g.location, { facingLocation: best.location });
            const done = age > 70 + m.stalkFor;
            // หัวใจเต้นดังขึ้นและถี่ขึ้นเมื่อผีเข้าใกล้
            if (now >= m.nextBeat) {
              playAt(best, "mob.warden.heartbeat", best.location.x, best.location.y, best.location.z, Math.max(0.4, 1.6 - bestD / 25), 1);
              m.nextBeat = now + Math.max(18, Math.min(70, bestD * 2.2));
            }
            if (bestD <= 26) { try { best.addEffect("darkness", 50, { amplifier: 0, showParticles: false }); } catch (e) {} }
            if (bestD <= 28 && Math.random() < 0.12) {   // เสียงฝีเท้าลากอยู่ข้างหลัง
              try {
                const v = best.getViewDirection(), bl = best.location, dd = rand(3, 6);
                const vl = Math.hypot(v.x, v.z) || 1;
                playAt(best, Math.random() < 0.5 ? "step.stone" : "step.gravel", bl.x - (v.x / vl) * dd, bl.y, bl.z - (v.z / vl) * dd, 1.2, rand(0.5, 0.8));
              } catch (e) {}
            }
            if (bestD <= 24 && Math.random() < 0.03) whisper(best);
            if (bestD <= 34 && Math.random() < 0.05) playAt(best, "mob.ghast.moan", g.location.x, g.location.y, g.location.z, 1.6, rand(0.45, 0.7));   // ร้องไห้อยู่ไม่ไกล
            // คืบเข้ามาเฉพาะตอนผู้เล่นไม่ได้มอง (หันหลังให้/มองทางอื่น) เหมือนมันขยับตอนเราไม่เห็น
            if (now >= m.nextCreep && bestD > CFG.AMBUSH_DIST + 3) {
              m.nextCreep = now + rand(CP.a, CP.b);
              let dot = 1; try { dot = lookDot(best, g); } catch (e) {}
              if (dot < 0.25) creepToward(g, best, rand(CP.s0, CP.s1));
            }
            if (bestD <= CFG.AMBUSH_DIST * bf.vis || (done && bestD <= CFG.CHASE_RANGE * bf.vis)) {
              m.stalking = false;
              g.triggerEvent("ghost:start_snap");
              jumpscare(best, true);
              try { best.addEffect("nausea", 100, { amplifier: 0, showParticles: false }); } catch (e) {}
            } else if (done) { vanish(g); }
          } else {
            if (!m.lostAt) m.lostAt = now;
            if (now - m.lostAt > 400 * bf.dur) vanish(g);
          }
        } else if (state === 1) {
          g.teleport(g.location, { facingLocation: best.location });
        } else if (state === 2 || state === 7) {
          // ระบบแอบ: ผีต้องเห็นหรือได้ยินเราถึงจะไล่ต่อได้ คลาดกัน 3 วินาที -> เดินหาจุดสุดท้ายที่เห็น
          if (!m.wasChasing) { m.wasChasing = true; m.lastDetect = now; m.lkp = { x: best.location.x, y: best.location.y, z: best.location.z }; m.lkpAt = now; if (!m.huntStart) m.huntStart = now; }
          if (!chaseCheck(g, m, now)) continue;
          if (!m.chaseStart) m.chaseStart = now;
          if (bestD > CFG.LOSE_RANGE * bf.vis) {
            if (state === 7) { vanish(g); continue; }        // คลานอยู่แล้วเสียเป้า: หายไปในความมืด
            g.triggerEvent("ghost:chase_off"); m.lostAt = now; continue;
          }
          if (now - m.chaseStart > CFG.CHASE_MAX_TICKS * bf.dur) { vanish(g); continue; }
          if (state === 2) { try { (PERSONA[n] || (() => {}))({ g, best, bestD, state, now, m, dim, api: PAPI }); } catch (e) {} }
          const sp = bf.spd * (m.spdMul ?? 1) * nightMods().speed;
          if (state === 2 && (sp !== 1 || m.spApplied)) { setSpeed(g, sp, g.getProperty("ghost:rage") === true); m.spApplied = sp !== 1; }
          // ไม่ทุบทุกอย่างที่ขวางแล้ว: ติดอยู่ -> คลาน / ทุบประตู-หน้าต่าง-ไม้กั้น / มุดเข้ามา / ค่อย ๆ ทุบออก
          try { chaseHook(g, m, best, bestD, now, state); } catch (e) {}
          if (isScripted(m)) continue;
        }
        const chasing = state === 2 || state === 7;
        if (chasing) {
          if (Math.random() < 0.35) playAt(best, "mob.warden.heartbeat", best.location.x, best.location.y, best.location.z, 1.8, 1.3);
          if (Math.random() < 0.06) playAt(best, "ghost.growl", g.location.x, g.location.y, g.location.z, 1.5, rand(0.6, 1.1));
          if (Math.random() < 0.05) playAt(best, "mob.ghast.scream", g.location.x, g.location.y, g.location.z, 1.6, rand(0.4, 0.7));
          if (Math.random() < 0.03) whisper(best);
          if (bestD <= 6) { try { best.addEffect("nausea", 80, { amplifier: 0, showParticles: false }); } catch (e) {} }
        }
        if (chasing && bestD <= 18 && Math.random() < 0.05) {
          try { best.addEffect("blindness", 12, { amplifier: 0, showParticles: false }); } catch (e) {}
        }
        if (chasing && bestD <= 8 && Math.random() < 0.25) {
          try { best.runCommand("camerashake add @s 0.25 0.5 positional"); } catch (e) {}
        }
        if ((state === 1 || chasing) && bestD <= 30) {
          try { best.addEffect("darkness", 60, { amplifier: 0, showParticles: false }); } catch (e) {}
        }
        if (state === 0 && m.stalking) { try { (PERSONA[n] || (() => {}))({ g, best, bestD, state, now, m, dim, api: PAPI }); } catch (e) {} }
        const gm = GIMMICK[n];
        if (gm) gm({ g, best, bestD, state: chasing ? 2 : state, now, m, dim });
      } catch (e) { /* entity unloaded */ }
    }
  }
  if (meta.size > 120) {
    const live = new Set();
    for (const d of dims()) { try { for (const e of d.getEntities({ families: ["ghost"] })) live.add(e.id); } catch (e) {} }
    for (const id of [...meta.keys()]) if (!live.has(id)) meta.delete(id);
  }
}

// ข้อมูลประจำตัวผี (สร้างครั้งแรกที่เจอ) + พลังทุบประตูที่สุ่มต่อการเกิดแต่ละครั้ง
let forcedPower = 0;   // ตั้งด้วย /scriptevent ghost:power 1|2|3 (0 = สุ่ม)
function ensureMeta(g, now) {
  let m = meta.get(g.id);
  if (!m) {
    now = now ?? system.currentTick;
    m = { born: now, stalkFor: rand(CFG.STALK_MIN, CFG.STALK_MAX), stalking: true, chaseStart: 0, lostAt: 0, nextBeat: 0, nextCreep: now + rand(80, 160) };
    m.mod = rollMod(); m.stalkFor *= m.mod.stalk * (PROFILE[Number(g.typeId.slice(-1))]?.stalk ?? 1);
    const B = takeBuff();
    if (B) { m.buff = B; m.stalkFor *= B.dur; }
    m.power = forcedPower || rollPower(!!B && Math.random() < 0.5);
    meta.set(g.id, m);
  }
  return m;
}

const PAPI = { playAt, rand, findSpot };
function ghostBusy() {
  if (omen || !storyActive()) return true;
  try { const all = world.getAllPlayers(); if (all.length && all.every((q) => isCalm(q))) return true; } catch (e) {}
  for (const d of dims()) { try { if (d.getEntities({ families: ["ghost"] }).length > 0) return true; } catch (e) {} }
  return false;
}
function glimpseGhost(p) { return stalkerOutdoor(p); }
system.runInterval(() => ghostTick(system.currentTick), 5);
system.runInterval(() => { try { musicTick(system.currentTick); } catch (e) {} }, 5);
system.runInterval(() => { try { spawnTick(system.currentTick); } catch (e) {} }, 40);

initLang();
initStory();
initAtmos();
initSurvival({ whisper, jumpscare });
initGraves();
initBetray({ ensureMeta, findSpot, jumpscare, isHidden });
initPhysics();
initBlood();
initNav();
initPhone();
initRescue({ huntNow });
onBloodRise(() => { nextSpawnAt = Math.min(nextSpawnAt, system.currentTick + rand(40 * 20, 90 * 20)); });
// blood moon / the rescue you missed: ghosts hit harder (the extra is dealt on top of the hit that landed)
let extraHit = false;
world.afterEvents.entityHurt.subscribe((ev) => {
  try {
    if (extraHit) return;
    const p = ev.hurtEntity, g = ev.damageSource && ev.damageSource.damagingEntity;
    if (!p || p.typeId !== "minecraft:player" || !g || !String(g.typeId).startsWith("ghost:ghost_")) return;
    const k = nightMods(p).damage;
    if (k <= 1 || inDeal(p)) return;
    extraHit = true;
    try { p.applyDamage(ev.damage * (k - 1), { cause: "entityAttack", damagingEntity: g }); } catch (e) {}
    extraHit = false;
  } catch (e) { extraHit = false; }
});
initTalk({ meta, leaving, talkLock, dims, playAt, puff, rand, openAsk, askLabel, tryAsk });
initCurse({ meta, glimpse, leaving, dims, playAt, puff, jumpscare, findSpot, summonGhost });
initBreach();
initBoards();
initFlashlight();
initSprint({ talkLock });
initIntrude({ meta, leaving, dims, jumpscare, vanish, ensureMeta, isHidden, afterIntrusion, searchFollowTick, searchFollowDone });
initHide({ meta, leaving, dims, jumpscare, vanish, ensureMeta, isScripted, ixOf, canStand, resumeChase, release, breachToward, digToward, followTo });
initBookName();
initStalker({ jumpscare, ghostBusy, isHidden, moodEv });
initVariety({ playAt, rand, findSpot, ghostBusy, glimpseGhost, isHidden, fakeLeave: () => { try { world.sendMessage({ rawtext: [{ translate: "multiplayer.player.left", with: ["Player" + Math.floor(Math.random() * 900 + 100)] }] }); } catch (e) {} } });

// ===== คำสั่งทดสอบ (ใช้ในโลกที่เปิดสูตรโกง) =====
//   /scriptevent ghost:knock [1-5]    ผีมาเคาะประตู/หน้าต่างใกล้ตัว (ต้องอยู่ในบ้าน)
//   /scriptevent ghost:breakin [1-5]  ผีบุกพังเข้ามา
//   /scriptevent ghost:power 1|2|3|0  พลังทุบของผีตัวถัดไป (0 = สุ่มตามปกติ)
//   /scriptevent ghost:hunt [1-5]     ผีโผล่ข้างหลังแล้วไล่ทันที (ลองหลบ/ซ่อน)
try {
  system.afterEvents.scriptEventReceive.subscribe((ev) => {
    try {
      if (ev.id.startsWith("hollow:")) { hollowCommand(ev); return; }
      const p = ev.sourceEntity;
      if (!p || p.typeId !== "minecraft:player") return;
      if (ev.id === "ghost:doordebug") { p.sendMessage("§7" + doorDebug(p)); return; }
      const n = Math.floor(Number(ev.message));
      const type = n >= 1 && n <= 5 ? n : 1 + Math.floor(Math.random() * 5);
      if (ev.id === "ghost:knock" || ev.id === "ghost:breakin") {
        const ok = ev.id === "ghost:knock" ? startKnock(p, type, false) : startBreakIn(p, type);
        if (!ok) p.sendMessage("§7ไม่เจอประตูหรือหน้าต่างที่ด้านนอกเป็นที่โล่ง ในระยะ 10 บล็อก");
      } else if (ev.id === "ghost:hunt") {
        // ผีโผล่ด้านหลังแล้วไล่ทันที (ไว้ลองระบบแอบ)
        const spot = findSpot(p, true, 10, 14);
        if (!spot) { p.sendMessage("§7หาที่ให้ผีโผล่ไม่ได้"); return; }
        const g = p.dimension.spawnEntity(`ghost:ghost_${type}`, spot);
        const m = ensureMeta(g, system.currentTick);
        m.stalking = false; m.born = system.currentTick - 200;
        system.runTimeout(() => { try { g.triggerEvent("ghost:ready"); g.triggerEvent("ghost:start_snap"); jumpscare(p, false); } catch (e) {} }, 4);
      } else if (ev.id === "ghost:power") {
        forcedPower = n >= 1 && n <= 3 ? n : 0;
        p.sendMessage(`§7พลังทุบของผีตัวถัดไป: §e${forcedPower || "สุ่ม"}`);
      }
    } catch (e) {}
  });
} catch (e) {}

// ===== v2.0 test commands (cheats on), the same as the Java edition's /hollow ... =====
//   /scriptevent hollow:story start|hold|auto|alert      /scriptevent hollow:bloodmoon on|off|random
//   /scriptevent hollow:fog on|off                       /scriptevent hollow:thirst <0-20>      /scriptevent hollow:sanity <0-100>
//   /scriptevent hollow:rescue now|invade|reset|status
//   /scriptevent hollow:status
function hollowCommand(ev) {
  const p = ev.sourceEntity && ev.sourceEntity.typeId === "minecraft:player" ? ev.sourceEntity : null;
  const a = String(ev.message || "").trim().toLowerCase(), what = ev.id.slice(7);
  const tell = (t) => { try { if (p) p.sendMessage("§7" + t); else console.warn("[hollow] " + t); } catch (e) {} };
  const who = p ? [p] : world.getAllPlayers();
  if (what === "story") {
    if (a === "alert") { for (const q of who) sendAlert(q); return; }
    forceStory(a === "start" ? 1 : a === "hold" ? -1 : 0);
    tell("story: " + (a || "auto") + " (day " + day() + ", haunting " + (storyActive() ? "on" : "off") + ")");
  } else if (what === "bloodmoon") {
    forceBlood(a === "on" ? 1 : a === "off" ? -1 : 0); tell("blood moon: " + (a || "random"));
  } else if (what === "fog") {
    if (a === "off") stopFog(); else startFog(240 * 20, 0.9);
    tell("fog: " + (a === "off" ? "off" : "on"));
  } else if (what === "thirst") { for (const q of who) setThirst(q, Number(a)); tell("thirst " + a); }
  else if (what === "sanity") { for (const q of who) setSanity(q, Number(a)); tell("sanity " + a); }
  else if (what === "rescue") rescueCommand(a, p, tell);
  else if (what === "status") {
    tell(`day ${day()} · haunting ${storyActive() ? "on" : "off"} · blood moon ${bloodMoon() ? "yes" : "no"}` + (p ? ` · thirst ${getThirst(p).toFixed(1)} · sanity ${getSanity(p).toFixed(0)}` : ""));
  }
}
/** A ghost comes out of the dark behind this player, already running (the haven, once it is found). */
function huntNow(p, type) {
  const spot = findSpot(p, false, 18, 30);
  if (!spot) return null;
  const g = p.dimension.spawnEntity(`ghost:ghost_${type || 1 + Math.floor(Math.random() * 5)}`, spot);
  const m = ensureMeta(g, system.currentTick);
  m.stalking = false; m.born = system.currentTick - 200;
  system.runTimeout(() => { try { g.triggerEvent("ghost:ready"); g.triggerEvent("ghost:start_snap"); } catch (e) {} }, 4);
  return g;
}
initSelfTest({ ensureMeta, findSpot, jumpscare, hollowCommand, huntNow });
