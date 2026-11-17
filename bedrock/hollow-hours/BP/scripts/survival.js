// Thirst and sanity (port of the Java mod's Thirst / Sanity / Survival), shown on the action bar.
//
// Thirst 0-20 works like hunger: it goes down with what you do (running, jumping, fighting, mining), and drinks also
// give a hidden "hydration" that is used up first. Drink: water bottles (+7), the canteen (+8), milk (+5), soups,
// honey, melon, berries... or sneak and look at water with an empty hand (+2 a sip; river water can make you sick).
// Below 6 you're slow, below 3 weak, at 0 it hurts.
//
// Sanity 0-100. Normal life keeps it full; once the haunting starts the dark, the night, ghosts nearby (worse when you
// can see one) and every jumpscare wear it down. Light, daylight, friends, pets, a campfire, music, sleep and comfort
// food bring it back. Low sanity: whispers, then things that aren't there, then the world bends.
import { world, system, ItemStack } from "@minecraft/server";
import { pget, pset, players, playing, ok, playTo, later, mainHand, barBusy, hudBar, markBar, oops } from "./store.js";
import { storyActive, isNight } from "./story.js";
import { bloodMoon, isCalm } from "./atmos.js";
import { isSheltered, isUndergroundAt } from "./util.js";
import { flashOn } from "./flashlight.js";
import { T, L } from "./texts.js";

export const CFG = { THIRST: true, SANITY: true, THIRST_RATE: 1.0, HUD: true };
const MAX_T = 20, MAX_S = 100, VS_HUNGER = 1.1, HYDRATION = 0.6;
const rand = (a, b) => a + Math.random() * (b - a);
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const S = new Map();   // player.id -> { thirst, hyd, sanity, ex, last, jumping, shownAt, sipAt, lit, litAt, cozy, slept }
let api = {};
const extraHud = [];   // other systems add a line: (player) => string | ""
export function addHudLine(fn) { extraHud.push(fn); }

function st(p) {
  let s = S.get(p.id);
  if (!s) {
    s = { thirst: clamp(Number(pget(p, "hh:thirst", MAX_T)), 0, MAX_T), hyd: Number(pget(p, "hh:hyd", 0)), sanity: clamp(Number(pget(p, "hh:sanity", MAX_S)), 0, MAX_S),
      ex: 0, last: null, jumping: false, shownAt: -1000, sipAt: 0, lit: 15, litAt: -1000, cozy: 0, slept: false, dirty: false };
    if (isNaN(s.thirst)) s.thirst = MAX_T;
    if (isNaN(s.sanity)) s.sanity = MAX_S;
    if (isNaN(s.hyd)) s.hyd = 0;
    S.set(p.id, s);
  }
  return s;
}
function save(p, s) { if (!s.dirty) return; s.dirty = false; pset(p, "hh:thirst", s.thirst); pset(p, "hh:hyd", s.hyd); pset(p, "hh:sanity", s.sanity); }

export const getThirst = (p) => st(p).thirst;
export const getSanity = (p) => st(p).sanity;
export function setThirst(p, v) { const s = st(p); s.thirst = clamp(v, 0, MAX_T); s.hyd = Math.min(s.hyd, s.thirst); s.dirty = true; s.shownAt = system.currentTick; }
export function setSanity(p, v) { const s = st(p); s.sanity = clamp(v, 0, MAX_S); s.dirty = true; }
export function addSanity(p, v) { if (CFG.SANITY) setSanity(p, st(p).sanity + v); }
/** Drinking: thirst plus a little hidden hydration. */
export function drink(p, amount) {
  const s = st(p);
  s.thirst = clamp(s.thirst + amount, 0, MAX_T);
  s.hyd = clamp(s.hyd + amount * HYDRATION, 0, s.thirst);
  s.dirty = true; s.shownAt = system.currentTick;
}
/** A fright (jumpscare, a ghost face, a kill nearby). */
export function shock(p, amount) { if (CFG.SANITY && playing(p)) { addSanity(p, -amount); st(p).shownAt = system.currentTick; } }
/** Energy spent (the same units as hunger exhaustion: 4 = one point). */
export function exhaust(p, v) { try { st(p).ex += v; } catch (_) { oops(_); } }

// ---- how much a finished item quenches / comforts
const QUENCH = { "minecraft:potion": 7, "minecraft:milk_bucket": 5, "minecraft:honey_bottle": 3, "minecraft:mushroom_stew": 3, "minecraft:beetroot_soup": 3, "minecraft:rabbit_stew": 3,
  "minecraft:suspicious_stew": 3, "minecraft:melon_slice": 2, "minecraft:sweet_berries": 1, "minecraft:glow_berries": 1, "minecraft:apple": 1,
  "ghost:canteen": 8, "ghost:canteen_2": 8, "ghost:canteen_1": 8 };
const COMFORT = { "minecraft:cookie": 3, "minecraft:pumpkin_pie": 6, "minecraft:golden_apple": 8, "minecraft:golden_carrot": 8, "minecraft:enchanted_golden_apple": 30,
  "minecraft:honey_bottle": 1, "minecraft:sweet_berries": 1, "minecraft:cake": 4 };
const CANTEENS = ["ghost:canteen", "ghost:canteen_2", "ghost:canteen_1", "ghost:canteen_empty"];

// ---- movement: what the hunger bar would spend, thirst spends 10% more
function moveTick() {
  for (const p of players()) {
    try {
      const s = st(p), l = p.location;
      if (s.last && s.dim === p.dimension.id) {
        const d = Math.hypot(l.x - s.last.x, l.z - s.last.z);
        if (d < 8) {
          if (p.isSprinting) s.ex += 0.1 * d;
          else if (p.isSwimming || p.isInWater) s.ex += 0.01 * d;
        }
      }
      s.last = { x: l.x, y: l.y, z: l.z }; s.dim = p.dimension.id;
      const j = !!p.isJumping;
      if (j && !s.jumping) s.ex += p.isSprinting ? 0.2 : 0.05;
      s.jumping = j;
      if (p.isSneaking) sipCheck(p, s);
    } catch (_) { oops(_); }
  }
}

// ---- a sip straight from the water: sneak, empty hand, look at water close by
function waterAhead(p) {
  try {
    const hit = p.getBlockFromViewDirection({ maxDistance: 4, includeLiquidBlocks: true, includePassableBlocks: true });
    const b = hit && hit.block;
    if (!b) return null;
    const id = b.typeId;
    if (id === "minecraft:water" || id === "minecraft:flowing_water") return { block: b, clean: false };
    if (id === "minecraft:cauldron") {
      let lvl = 0, liq = "water";
      try { lvl = b.permutation.getState("fill_level") ?? 0; liq = b.permutation.getState("cauldron_liquid") ?? "water"; } catch (_) { oops(_); }
      if (lvl > 0 && liq === "water") return { block: b, clean: true };
    }
  } catch (_) { oops(_); }
  return null;
}
function sipCheck(p, s) {
  if (!CFG.THIRST || !playing(p)) return;
  const now = system.currentTick;
  if (now - s.sipAt < 14 || s.thirst >= MAX_T - 0.5) return;
  if (mainHand(p)) return;
  const w = waterAhead(p);
  if (!w) return;
  s.sipAt = now;
  drink(p, 2);
  try { p.dimension.playSound("random.drink", p.location, { volume: 0.6, pitch: 1.1 }); } catch (_) { oops(_); }
  try { const c = w.block.location; p.dimension.spawnParticle("minecraft:water_splash_particle", { x: c.x + 0.5, y: c.y + 1, z: c.z + 0.5 }); } catch (_) { oops(_); }
  // untreated water from rivers and lakes (a cauldron is clean)
  if (!w.clean && Math.random() < 0.15) { try { p.addEffect("nausea", 120, { amplifier: 0, showParticles: false }); } catch (_) { oops(_); } }
}

function thirstTick(p, s, now) {
  if (!CFG.THIRST || !playing(p)) { s.ex = 0; return; }
  let use = (s.ex + 0.004) / 4 * VS_HUNGER * CFG.THIRST_RATE;
  s.ex = 0;
  const fromHyd = Math.min(s.hyd, use);
  if (fromHyd > 0 || use > 0) s.dirty = true;
  s.hyd -= fromHyd;
  s.thirst = clamp(s.thirst - (use - fromHyd), 0, MAX_T);
  const t = s.thirst;
  try {
    if (t <= 0) { if (now % 80 < 20) p.applyDamage(1, { cause: "starve" }); }
    else if (t < 3) {
      p.addEffect("weakness", 60, { amplifier: 0, showParticles: false });
      p.addEffect("slowness", 60, { amplifier: 0, showParticles: false });
      if (Math.random() < 0.02) p.addEffect("nausea", 100, { amplifier: 0, showParticles: false });
    } else if (t < 6) p.addEffect("slowness", 60, { amplifier: 0, showParticles: false });
  } catch (_) { oops(_); }
}

// ---- light: there is no light level to ask for, so look for what gives light near the player
const LIGHTS = /torch|lantern|glowstone|sea_lantern|shroomlight|campfire|froglight|lit_|fire|lava|jack_o_lantern|end_rod|beacon|candle|glow_|magma|redstone_lamp|conduit|light_block|amethyst_cluster|respawn_anchor/;
const HELD_LIGHTS = /torch|lantern|glowstone|sea_lantern|shroomlight|froglight|jack_o_lantern|end_rod|lava_bucket/;
function scanLight(p, s) {
  const dim = p.dimension, l = p.location, x0 = Math.floor(l.x), y0 = Math.floor(l.y), z0 = Math.floor(l.z);
  let best = 0, cozy = 0;
  for (let dx = -6; dx <= 6; dx += 1) for (let dz = -6; dz <= 6; dz += 1) for (let dy = -2; dy <= 4; dy += 1) {
    // a third of the box each scan (the pattern shifts every time), close blocks always
    const near = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
    if (!near && ((dx + dy * 2 + dz * 3 + s.phase) % 3 + 3) % 3 !== 0) continue;
    let b;
    try { b = dim.getBlock({ x: x0 + dx, y: y0 + dy, z: z0 + dz }); } catch (_) { continue; }
    if (!b || b.isAir) continue;
    const id = b.typeId;
    if (!LIGHTS.test(id)) {
      if (id === "minecraft:jukebox") { try { if (b.getComponent("minecraft:record_player")?.isPlaying()) cozy = Math.max(cozy, 0.15); } catch (_) { oops(_); } }
      continue;
    }
    if (id.includes("redstone_torch") || id === "minecraft:redstone_lamp" || id === "minecraft:furnace" || id === "minecraft:campfire" && state(b, "extinguished") === true) continue;
    if (id.includes("candle") && state(b, "lit") !== true) continue;
    const d = Math.max(Math.abs(dx), Math.abs(dy), Math.abs(dz));
    best = Math.max(best, d <= 3 ? 13 : d <= 5 ? 10 : 8);
    if (id.includes("campfire") && d <= 5) cozy = Math.max(cozy, 0.08);
  }
  s.phase = ((s.phase ?? 0) + 1) % 3;
  s.scan = best; s.cozy = cozy;
}
function state(b, n) { try { return b.permutation.getState(n); } catch (_) { return undefined; } }
function lightOf(p, s, now, night, sky) {
  if (!night && sky) return 15;
  if (now - s.litAt >= 60) { s.litAt = now; try { scanLight(p, s); } catch (_) { oops(_); } }
  let v = s.scan ?? 0;
  try { if (flashOn(p)) v = Math.max(v, 12); } catch (_) { oops(_); }
  try { const h = mainHand(p); if (h && HELD_LIGHTS.test(h.typeId)) v = Math.max(v, 12); } catch (_) { oops(_); }
  try { const o = p.getComponent("minecraft:equippable").getEquipment("Offhand"); if (o && HELD_LIGHTS.test(o.typeId)) v = Math.max(v, 12); } catch (_) { oops(_); }
  if (!night && !sky) v = Math.max(v, 5);      // daylight leaking indoors
  if (night && sky) v = Math.max(v, 4);        // moonlight
  return v;
}

function seesGhost(p, g) {
  try {
    for (const h of p.getEntitiesFromViewDirection({ maxDistance: 32 })) if (h.entity && h.entity.id === g.id) return true;
  } catch (_) { oops(_); }
  return false;
}

function sanityTick(p, s, now) {
  if (!CFG.SANITY || !ok(p)) return;
  if (!storyActive() || !playing(p) || isCalm(p)) { if (s.sanity < MAX_S) { s.sanity = Math.min(MAX_S, s.sanity + 0.5); s.dirty = true; } return; }
  const dim = p.dimension, over = dim.id === "minecraft:overworld";
  const night = over && isNight();
  let sky = false, under = false;
  try { sky = over && !isSheltered(p) && !isUndergroundAt(dim, p.location); under = isUndergroundAt(dim, p.location); } catch (_) { oops(_); }
  const light = lightOf(p, s, now, night, sky);
  s.lit = light;
  let d = 0;
  // ---- what wears you down
  if (light < 4) d -= 0.10; else if (light < 8) d -= 0.03;
  if (night && sky) d -= 0.04;
  if (under && light < 8) d -= 0.04;
  if (over && bloodMoon()) d -= 0.06;
  let ghostNear = false;
  try {
    for (const g of dim.getEntities({ families: ["ghost"], location: p.location, maxDistance: 40 })) {
      ghostNear = true;
      const dist = Math.hypot(g.location.x - p.location.x, g.location.y - p.location.y, g.location.z - p.location.z);
      d -= dist < 12 ? 0.45 : 0.15;
      if (dist <= 32 && seesGhost(p, g)) d -= 0.55;
    }
  } catch (_) { oops(_); }
  if (s.thirst < 4) d -= 0.03;
  // ---- what holds you together
  if (!ghostNear) {
    if (light >= 12) d += 0.05;
    if (!night && sky) d += 0.05;
    try { if (dim.getPlayers({ location: p.location, maxDistance: 8 }).some((o) => o.id !== p.id)) d += 0.06; } catch (_) { oops(_); }
    try {
      for (const e of dim.getEntities({ location: p.location, maxDistance: 8, excludeTypes: ["minecraft:player", "minecraft:item"] })) {
        const t = e.getComponent("minecraft:tameable");
        if (t && t.isTamed && t.tamedToPlayerId === p.id) { d += 0.06; break; }
      }
    } catch (_) { oops(_); }
    d += s.cozy ?? 0;
  }
  if (d !== 0) { s.sanity = clamp(s.sanity + d, 0, MAX_S); s.dirty = true; }
  // sleep
  try {
    const sl = !!p.isSleeping;
    if (s.slept && !sl && !isNight()) addSanity(p, 30);
    s.slept = sl;
  } catch (_) { oops(_); }
  effects(p, s, now);
}
function effects(p, s, now) {
  const v = s.sanity;
  try {
    if (v < 60 && Math.random() < (v < 35 ? 0.03 : 0.012) && api.whisper) api.whisper(p);
    if (v < 35 && Math.random() < (v < 15 ? 0.05 : 0.02)) hallucinate(p);
    if (v < 15) {
      if (Math.random() < 0.06) p.addEffect("nausea", 120, { amplifier: 0, showParticles: false });
      if (Math.random() < 0.04) p.addEffect("darkness", 60, { amplifier: 0, showParticles: false });
    }
    if (v <= 0) {
      p.addEffect("weakness", 60, { amplifier: 0, showParticles: false });
      if (now % 200 < 20) p.applyDamage(1, { cause: "magic" });
    }
  } catch (_) { oops(_); }
}
/** Things that aren't there. */
export function hallucinate(p) {
  const l = p.location, v = p.getViewDirection(), bx = l.x - v.x * 3, bz = l.z - v.z * 3;
  const at = (x, y, z) => ({ x, y, z });
  switch (Math.floor(Math.random() * 7)) {
    case 0: for (let k = 0; k < 5; k++) later(k * 7 + 1, () => playTo(p, "step.stone", 1, rand(0.8, 1.0), at(bx, l.y, bz))); break;
    case 1: playTo(p, "ghost.knock.wood", 1.2, rand(0.8, 1.0), at(bx, l.y + 1, bz)); break;
    case 2: playTo(p, "ghost.growl", 0.8, rand(0.5, 0.8), at(bx, l.y + 1, bz)); break;
    case 3: playTo(p, "ghost.horror.whisper", 1.4, rand(0.9, 1.1), at(l.x + v.x, l.y + 1.6, l.z + v.z)); break;
    case 4: if (api.jumpscare) api.jumpscare(p, false); break;
    case 5: markBar(p); try { p.onScreenDisplay.setActionBar(T("sanity.behind")); } catch (_) { oops(_); } break;
    default: playTo(p, "ghost.scare.giggle", 0.9, rand(0.9, 1.1), at(bx, l.y + 1, bz));
  }
}

// ---- the HUD line
function bar(v, max, on, n = 10) {
  const k = Math.round(clamp(v / max, 0, 1) * n);
  return on + "|".repeat(k) + "§8" + "|".repeat(n - k);
}
function hud(p, s, now) {
  if (!CFG.HUD || !playing(p)) return;
  if (barBusy(p)) return;
  const parts = [];
  const low = s.thirst < 17 || s.sanity < 90, recent = now - s.shownAt < 100;
  if (low || recent) {
    if (CFG.THIRST) parts.push(`${s.thirst < 3 ? "§c" : "§b"}${L("น้ำ", "Water")} ${bar(s.thirst, MAX_T, s.thirst < 6 ? "§c" : "§b")}`);
    if (CFG.SANITY && (storyActive() || s.sanity < MAX_S)) parts.push(`${s.sanity < 15 ? "§c" : "§d"}${L("สติ", "Sanity")} ${bar(s.sanity, MAX_S, s.sanity < 35 ? "§5" : "§d")}`);
  }
  for (const fn of extraHud) { try { const x = fn(p); if (x) parts.push(x); } catch (_) { oops(_); } }
  if (parts.length) hudBar(p, parts.join("  §r"));
}

function tick() {
  const now = system.currentTick;
  for (const p of players()) {
    try {
      const s = st(p);
      thirstTick(p, s, now);
      sanityTick(p, s, now);
      if (Math.floor(now / 20) % 5 === 0) save(p, s);
    } catch (_) { oops(_); }
  }
}

function refill(p) {
  later(1, () => {
    try {
      const h = mainHand(p);
      if (!h || !CANTEENS.includes(h.typeId) || h.typeId === "ghost:canteen") return;
      const inv = p.getComponent("minecraft:inventory").container;
      inv.getSlot(p.selectedSlotIndex).setItem(new ItemStack("ghost:canteen", 1));
      try { p.dimension.playSound("bucket.fill_water", p.location, { volume: 0.8, pitch: 1.2 }); } catch (_) { oops(_); }
      markBar(p); p.onScreenDisplay.setActionBar("§b" + L("เติมน้ำเต็มกระติกแล้ว (3/3)", "Canteen filled (3/3)"));
    } catch (_) { oops(_); }
  });
}

export function initSurvival(a) {
  api = a || {};
  system.runInterval(() => { try { moveTick(); } catch (_) { oops(_); } }, 4);
  system.runInterval(() => { try { tick(); } catch (_) { oops(_); } }, 20);
  system.runInterval(() => { const now = system.currentTick; for (const p of players()) { try { hud(p, st(p), now); } catch (_) { oops(_); } } }, 10);
  try {
    world.afterEvents.itemCompleteUse.subscribe((ev) => {
      try {
        const p = ev.source, id = ev.itemStack.typeId;
        if (QUENCH[id] && CFG.THIRST) drink(p, QUENCH[id]);
        if (COMFORT[id]) { addSanity(p, COMFORT[id]); st(p).shownAt = system.currentTick; }
      } catch (_) { oops(_); }
    });
  } catch (_) { oops(_); }
  // the canteen: use it on water to fill it up again (an empty one can't be drunk from)
  try {
    world.beforeEvents.itemUse.subscribe((ev) => {
      try {
        const id = ev.itemStack && ev.itemStack.typeId;
        if (!id || !CANTEENS.includes(id) || id === "ghost:canteen") return;
        const p = ev.source;
        if (waterAhead(p)) { ev.cancel = true; refill(p); }
        else if (id === "ghost:canteen_empty") system.run(() => { try { markBar(p); p.onScreenDisplay.setActionBar("§7" + T("item.canteen.empty")); } catch (_) { oops(_); } });
      } catch (_) { oops(_); }
    });
  } catch (_) { oops(_); }
  try { world.afterEvents.playerBreakBlock.subscribe((ev) => exhaust(ev.player, 0.005)); } catch (_) { oops(_); }
  try {
    world.afterEvents.entityHitEntity.subscribe((ev) => { if (ev.damagingEntity && ev.damagingEntity.typeId === "minecraft:player") exhaust(ev.damagingEntity, 0.1); });
    world.afterEvents.entityHurt.subscribe((ev) => { if (ev.hurtEntity && ev.hurtEntity.typeId === "minecraft:player") exhaust(ev.hurtEntity, 0.1); });
  } catch (_) { oops(_); }
  try {
    world.afterEvents.playerSpawn.subscribe((ev) => {
      if (ev.initialSpawn) return;
      // a new life: not dying of thirst the moment you are back
      const s = st(ev.player);
      s.thirst = Math.max(s.thirst, 12); s.sanity = Math.max(s.sanity, 50); s.ex = 0; s.last = null; s.dirty = true;
    });
    world.afterEvents.playerLeave.subscribe((ev) => { S.delete(ev.playerId); });
  } catch (_) { oops(_); }
}
