// The way out (port of the Java mod's Rescue + HelicopterEntity). On day 7 a message comes through: help is on its
// way, a helicopter will come in 35-100 days, at a set time. When it lands it waits five minutes. Whoever is on board
// is flown to the haven - a beautiful land far away with no ghosts and no monsters. Whoever misses it waits for the
// next one, and the ghosts take it out on them (twice as cruel, five times on blood moons). In the haven, after 5-10
// days, they find you anyway: a helicopter waits (as long as it takes) to fly you back where it all began, for good -
// and there the ghosts are three times as cruel, six times on blood moons.
//
// Bedrock has no custom dimensions, so the haven is a place in the overworld, tens of thousands of blocks away.
import { world, system } from "@minecraft/server";
import { wget, wset, wjson, pget, pset, pjson, players, say, playTo, later, ok, playing, dimName, oops } from "./store.js";
import { day, timeOfDay, isNight, storyActive } from "./story.js";
import { setCruelty, setCalm, bloodMoon } from "./atmos.js";
import { addHudLine } from "./survival.js";
import { notify, setRescueInfo, addPlaces } from "./phone.js";
import { arrowTo, distTo } from "./nav.js";
import { T } from "./texts.js";

export const CFG = { ENABLED: true, DAY: 7, MIN_DAYS: 35, MAX_DAYS: 100, PICKUP_MINUTES: 5, MISSED: [2, 5], RETURN: [3, 6], HAVEN_MIN_DAYS: 5, HAVEN_MAX_DAYS: 10, HAVEN_DISTANCE: 30000 };
export const WAITING = 0, IN_HAVEN = 1, HOME = 2;
const HELI = "ghost:helicopter";
const PICKUP = 0, EXTRACT = 1, DROP_OFF = 2;
const ARRIVING = 0, LANDED = 1, LEAVING = 2;
const FLY_IN = 220, APPROACH = 150, TAKE_OFF = 110, TRANSFER_AT = 95;
const rand = (a, b) => a + Math.random() * (b - a), randInt = (a, b) => Math.floor(rand(a, b + 1));
const ease = (k) => { k = Math.max(0, Math.min(1, k)); return k * k * (3 - 2 * k); };
const over = () => world.getDimension("overworld");
const absNow = () => { try { return world.getAbsoluteTime(); } catch (_) { return 0; } };
let api = {};

// ---------------------------------------------------------------- who is where in the story
export const phase = (p) => Number(pget(p, "hh:r_phase", WAITING));
export const missed = (p) => Number(pget(p, "hh:r_missed", 0));
export const invaded = (p) => pget(p, "hh:r_invaded", false) === true;
export const inHaven = (p) => phase(p) === IN_HAVEN;
function aboard(p) { try { const r = p.getComponent("minecraft:riding"); return r && r.entityRidingOn && r.entityRidingOn.typeId === HELI ? r.entityRidingOn : null; } catch (_) { return null; } }
/** Somewhere the ghosts can't get you: on board a helicopter, or in the haven before they find it. */
export const safe = (p) => !!aboard(p) || (inHaven(p) && !invaded(p));
/** How much harder the ghosts hit this player. */
export function harshness(p, blood) {
  if (phase(p) === HOME) return CFG.RETURN[blood ? 1 : 0];
  if (missed(p) > 0) return CFG.MISSED[blood ? 1 : 0];
  return 1;
}
function harshest(blood) { let m = 1; for (const p of players()) { try { if (!safe(p)) m = Math.max(m, harshness(p, blood)); } catch (_) { oops(_); } } return m; }

/** "14:30" for a world day time (0 = 6:00). */
export function clock(t) { t = ((t % 24000) + 24000) % 24000; return `${String((Math.floor(t / 1000) + 6) % 24).padStart(2, "0")}:${String(Math.floor((t % 1000) * 60 / 1000)).padStart(2, "0")}`; }
const arriveAt = () => Number(wget("hh:r_arrive", -1));
/** Plan the next helicopter: 35-100 days from today, at a random quarter hour. */
export function schedule(days) {
  const now = absNow();
  const d = days ?? randInt(CFG.MIN_DAYS, Math.max(CFG.MIN_DAYS, CFG.MAX_DAYS));
  const tod = ((randInt(0, 23) - 6 + 24) % 24) * 1000 + randInt(0, 3) * 250;
  wset("hh:r_arrive", (Math.floor(now / 24000) + d) * 24000 + tod);
}
function when(at, p) {
  const now = absNow();
  return [Math.floor(at / 24000) - Math.floor(now / 24000), Math.floor(at / 24000) + 1, clock(at), CFG.PICKUP_MINUTES, harshness(p, false), harshness(p, true)];
}
function notice(p, key, danger, ...a) {
  notify(p, T(key + ".from"), T(key, ...a), danger);
  try { p.onScreenDisplay.setTitle((danger ? "§c" : "§b") + T(key + ".title"), { fadeInDuration: 10, stayDuration: 70, fadeOutDuration: 20 }); } catch (_) { oops(_); }
}

// ---------------------------------------------------------------- the ground
/** The surface at a column: { y (the first free block), water } - null while that part of the world isn't loaded. */
function surface(dim, x, z) {
  try {
    const b = dim.getTopmostBlock({ x: Math.floor(x), z: Math.floor(z) });
    if (!b) return null;
    let top = b;
    // not on top of a tree
    for (let i = 0; i < 24 && top && (top.typeId.includes("leaves") || top.typeId.includes("log") || top.typeId.includes("vine")); i++) top = top.below();
    if (!top) return null;
    return { y: top.location.y + 1, water: top.isLiquid || top.typeId.includes("ice"), id: top.typeId };
  } catch (_) { return null; }
}
/** A flat open spot min..max blocks from a place (no trees, no water); the place itself if none. */
export function landing(dim, near, min, max) {
  for (let i = 0; i < 60; i++) {
    const ang = rand(0, Math.PI * 2), dist = rand(min, max);
    const x = Math.floor(near.x + Math.cos(ang) * dist), z = Math.floor(near.z + Math.sin(ang) * dist);
    const s = surface(dim, x, z);
    if (!s || s.water) continue;
    let flat = true;
    for (const [dx, dz] of [[3, 0], [-3, 0], [0, 4], [0, -4], [3, 4], [-3, -4]]) {
      const t = surface(dim, x + dx, z + dz);
      if (!t || t.water || Math.abs(t.y - s.y) > 1) { flat = false; break; }
    }
    if (flat) return { x: x + 0.5, y: s.y, z: z + 0.5 };
  }
  const s = surface(dim, near.x, near.z);
  return { x: Math.floor(near.x) + 0.5, y: s ? s.y : near.y, z: Math.floor(near.z) + 0.5 };
}

// ---------------------------------------------------------------- helicopters
const helis = new Map();   // entity.id -> flight { e, kind, state, t, pad, from, yaw, wait, riders }
function hset(e, f) { try { e.setDynamicProperty("hh:flight", JSON.stringify({ kind: f.kind, state: f.state, t: f.t, pad: f.pad, from: f.from, yaw: f.yaw, wait: f.wait })); } catch (_) { oops(_); } }
function flightOf(e) {
  let f = helis.get(e.id);
  if (!f) {
    let d = null;
    try { const s = e.getDynamicProperty("hh:flight"); if (typeof s === "string") d = JSON.parse(s); } catch (_) { oops(_); }
    // after a restart: one that was on its way in or out is simply gone; one that was waiting still waits
    if (!d || d.state !== LANDED) return null;
    f = { e, ...d }; helis.set(e.id, f);
  }
  return f;
}
/** A helicopter flies in from far away and sets down on the pad. */
export function arrive(dim, pad, kind, toward, waitTicks) {
  const ang = toward ? Math.atan2(pad.z - toward.z, pad.x - toward.x) + rand(-0.5, 0.5) : rand(0, Math.PI * 2);
  // it comes in from 48 blocks out: further away the world may not be loaded, and nothing can appear there
  let from = { x: pad.x + Math.cos(ang) * 48, y: pad.y + 32, z: pad.z + Math.sin(ang) * 48 };
  let e = null;
  for (const at of kind === DROP_OFF ? [pad] : [from, { x: pad.x, y: pad.y + 30, z: pad.z }, pad]) {
    try { e = dim.spawnEntity(HELI, at); from = at; break; } catch (_) { /* not loaded there: closer */ }
  }
  if (!e) throw new Error("no place to bring a helicopter in near " + JSON.stringify(pad));
  const yaw = Math.atan2(-(pad.x - from.x), pad.z - from.z) * 180 / Math.PI;
  const f = { e, kind, state: kind === DROP_OFF ? LANDED : ARRIVING, t: 0, pad, from, yaw, wait: waitTicks ?? -1 };
  helis.set(e.id, f); hset(e, f);
  return f;
}
function liveHelis(kind) { const out = []; for (const f of helis.values()) if (ok(f.e) && (kind === undefined || f.kind === kind)) out.push(f); return out; }
function nearestHeli(p, kind) {
  let best = null, bd = 1e9;
  for (const f of liveHelis(kind)) { try { if (f.e.dimension.id !== p.dimension.id) continue; const d = distTo(p, f.e.location); if (d < bd) { bd = d; best = f; } } catch (_) { oops(_); } }
  return best;
}
function riders(f) { try { return f.e.getComponent("minecraft:rideable").getRiders().filter((r) => r.typeId === "minecraft:player"); } catch (_) { return []; } }
function mmss(ticks) { const s = Math.max(0, Math.floor(ticks / 20)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`; }

function heliTick() {
  for (const [id, f] of [...helis]) {
    try {
      if (!ok(f.e)) { helis.delete(id); continue; }
      const e = f.e, dim = e.dimension;
      f.t++;
      if (f.t % 38 === 1) { try { dim.playSound("rescue.rotor", e.location, { volume: 3, pitch: 1 }); } catch (_) { oops(_); } }
      if (f.state === ARRIVING) {
        const hover = { x: f.pad.x, y: f.pad.y + 14, z: f.pad.z };
        let at;
        if (f.t < APPROACH) { const k = ease(f.t / APPROACH); at = { x: f.from.x + (hover.x - f.from.x) * k, y: f.from.y + (hover.y - f.from.y) * k, z: f.from.z + (hover.z - f.from.z) * k }; }
        else { const k = ease((f.t - APPROACH) / (FLY_IN - APPROACH)); at = { x: hover.x, y: hover.y + (f.pad.y - hover.y) * k, z: hover.z }; }
        e.teleport(at, { rotation: { x: 0, y: f.yaw } });
        if (f.t >= FLY_IN) { f.state = LANDED; f.t = 0; hset(e, f); }
      } else if (f.state === LANDED) {
        if (f.t % 20 === 0) landedCheck(f);
      } else {
        const k = ease(f.t / TAKE_OFF), fw = f.yaw * Math.PI / 180;
        const at = { x: f.pad.x - Math.sin(fw) * 30 * k * k, y: f.pad.y + 40 * k, z: f.pad.z + Math.cos(fw) * 30 * k * k };
        e.teleport(at, { rotation: { x: 0, y: f.yaw } });
        if (f.t === TRANSFER_AT - 25) for (const r of riders(f)) { try { r.camera.fade({ fadeTime: { fadeInTime: 1, holdTime: 2.2, fadeOutTime: 1.5 }, fadeColor: { red: 0, green: 0, blue: 0 } }); } catch (_) { oops(_); } }
        if (f.t === TRANSFER_AT) transfer(f);
        if (f.t >= TAKE_OFF + (f.hold ?? 0)) { if (f.t < 600 && f.busy) continue; helis.delete(id); try { e.remove(); } catch (_) { oops(_); } }
      }
    } catch (_) { oops(_); }
  }
}
/** Time to go? A pickup leaves when its time is up or everyone it came for is aboard; an extraction when all are. */
function landedCheck(f) {
  const e = f.e, on = riders(f);
  if (f.kind === DROP_OFF) { if (f.t >= 60) takeOff(f); return; }
  const want = f.kind === PICKUP ? WAITING : IN_HAVEN;
  const near = e.dimension.getPlayers({ location: e.location, maxDistance: f.kind === PICKUP ? 96 : 160 }).filter((p) => phase(p) === want && playing(p));
  const all = on.length > 0 && near.every((p) => on.some((r) => r.id === p.id));
  if (all) { f.full = (f.full ?? 0) + 20; if (f.full >= 100) takeOff(f); } else f.full = 0;
  if (f.kind === PICKUP && f.wait >= 0 && f.t >= f.wait) takeOff(f);
  if (f.kind === PICKUP && on.length && !havenBusy && !havenSpot()) prepareHaven();
}
function takeOff(f) {
  f.state = LEAVING; f.t = 0;
  try { f.e.dimension.playSound("rescue.rotor", f.e.location, { volume: 4, pitch: 1.15 }); } catch (_) { oops(_); }
}
export function ticksLeft(f) { return f.state === ARRIVING ? f.wait + FLY_IN - f.t : f.state === LANDED ? Math.max(0, f.wait - f.t) : 0; }

// ---------------------------------------------------------------- the flights
function transfer(f) {
  const list = riders(f).filter((p) => ok(p));
  if (!list.length) return;
  if (f.kind === PICKUP) {
    if (!havenSpot()) {          // the haven isn't found yet: circle a little longer
      f.busy = true; f.t = TRANSFER_AT - 1;
      if (!havenBusy) prepareHaven();
      f.waited = (f.waited ?? 0) + 1;
      if (f.waited < 900) return;
    }
    f.busy = false;
    toHaven(list);
  } else if (f.kind === EXTRACT) toHome(list);
  try { f.e.getComponent("minecraft:rideable").ejectRiders(); } catch (_) { oops(_); }
}
function toHaven(list) {
  const spot = havenSpot() ?? { x: CFG.HAVEN_DISTANCE, y: 120, z: 0 };
  const dim = over();
  decorate(dim, spot);
  for (const p of list) {
    try {
      // where they were picked up: that is where they are brought back to
      pset(p, "hh:r_home", JSON.stringify({ x: Math.floor(p.location.x), y: Math.floor(p.location.y), z: Math.floor(p.location.z) }));
      try { const s = p.getSpawnPoint(); if (s) pset(p, "hh:r_spawn", JSON.stringify({ x: s.x, y: s.y, z: s.z, dim: dimName(s.dimension) })); } catch (_) { oops(_); }
      const at = { x: spot.x + rand(-2, 2), y: spot.y + 0.2, z: spot.z + rand(-2, 2) };
      p.teleport(at, { dimension: dim });
      p.addEffect("resistance", 200, { amplifier: 4, showParticles: false });
      p.addEffect("slow_falling", 200, { amplifier: 0, showParticles: false });
      try { p.setSpawnPoint({ dimension: dim, x: Math.floor(spot.x), y: Math.floor(spot.y), z: Math.floor(spot.z) }); } catch (_) { oops(_); }
      pset(p, "hh:r_phase", IN_HAVEN); pset(p, "hh:r_invaded", false);
      pset(p, "hh:r_invade_at", absNow() + randInt(CFG.HAVEN_MIN_DAYS, CFG.HAVEN_MAX_DAYS) * 24000 + randInt(0, 23999));
      later(60, () => { if (ok(p)) notice(p, "rescue.arrived", false); });
    } catch (_) { oops(_); }
  }
  // the helicopter that brought them flies off again
  later(30, () => { try { const f = arrive(dim, { x: spot.x + 6, y: spot.y, z: spot.z + 6 }, DROP_OFF); f.t = 40; } catch (_) { oops(_); } });
  later(200, () => { try { dim.runCommand("tickingarea remove hh_haven"); } catch (_) { /* none */ } });
}
function toHome(list) {
  const dim = over();
  let home = null;
  for (const p of list) { const h = pjson(p, "hh:r_home", null); if (h) { home = h; break; } }
  if (!home) { try { const s = world.getDefaultSpawnLocation(); home = { x: s.x, y: 100, z: s.z }; } catch (_) { home = { x: 0, y: 100, z: 0 }; } }
  for (const p of list) {
    try {
      p.teleport({ x: home.x + rand(-2, 2), y: home.y + 0.5, z: home.z + rand(-2, 2) }, { dimension: dim });
      p.addEffect("resistance", 200, { amplifier: 4, showParticles: false });
      p.addEffect("slow_falling", 200, { amplifier: 0, showParticles: false });
      const s = pjson(p, "hh:r_spawn", null);
      try { p.setSpawnPoint(s ? { dimension: world.getDimension(s.dim), x: s.x, y: s.y, z: s.z } : undefined); } catch (_) { oops(_); }
      pset(p, "hh:r_phase", HOME);
      pset(p, "hh:r_home_day", day());
      later(60, () => { if (ok(p)) notice(p, "rescue.home", true, CFG.RETURN[0], CFG.RETURN[1]); });
    } catch (_) { oops(_); }
  }
  later(40, () => { try { const s = surface(dim, home.x + 6, home.z + 6); const f = arrive(dim, { x: home.x + 6.5, y: s ? s.y : home.y, z: home.z + 6.5 }, DROP_OFF); f.t = 40; } catch (_) { oops(_); } });
  for (const fn of homeHooks) { try { fn(list); } catch (_) { oops(_); } }
}
const homeHooks = [];
/** called with the players who were just flown back for good (the last chapter starts counting) */
export function onHome(fn) { homeHooks.push(fn); }

// ---------------------------------------------------------------- the haven
export function havenSpot() { return wjson("hh:haven", null); }
let havenBusy = false;
/** Looks for dry land far away (that part of the world has to be made first, which takes a few seconds). */
export function prepareHaven() {
  if (havenBusy || havenSpot()) return;
  havenBusy = true;
  const dim = over();
  let base;
  try { const s = world.getDefaultSpawnLocation(); const a = rand(0, Math.PI * 2); base = { x: Math.floor(s.x + Math.cos(a) * CFG.HAVEN_DISTANCE), z: Math.floor(s.z + Math.sin(a) * CFG.HAVEN_DISTANCE) }; }
  catch (_) { base = { x: CFG.HAVEN_DISTANCE, z: 0 }; }
  let tries = 0, waited = 0, cand = { ...base };
  const load = () => { try { dim.runCommand("tickingarea remove hh_haven"); } catch (_) { /* none yet */ } try { dim.runCommand(`tickingarea add circle ${cand.x} 0 ${cand.z} 3 hh_haven`); } catch (_) { oops(_); } };
  load();
  const id = system.runInterval(() => {
    try {
      waited++;
      const s = surface(dim, cand.x, cand.z);
      if (!s) { if (waited > 30) { waited = 0; load(); } return; }
      if (s.water && tries < 14) {            // sea: try further along
        tries++; waited = 0;
        cand = { x: base.x + tries * 160 * (tries % 2 ? 1 : -1), z: base.z + tries * 110 };
        load();
        return;
      }
      const pad = landing(dim, { x: cand.x, y: s.y, z: cand.z }, 0, 24);
      if (s.water) {                          // nothing but sea anywhere: a little island
        for (let dx = -5; dx <= 5; dx++) for (let dz = -5; dz <= 5; dz++) if (dx * dx + dz * dz <= 27) for (let dy = -3; dy < 0; dy++) { try { dim.getBlock({ x: cand.x + dx, y: s.y + dy + 1, z: cand.z + dz }).setType(dy === -1 ? "minecraft:grass_block" : "minecraft:dirt"); } catch (_) { oops(_); } }
        pad.x = cand.x + 0.5; pad.y = s.y + 1; pad.z = cand.z + 0.5;
      }
      wset("hh:haven", JSON.stringify({ x: pad.x, y: pad.y, z: pad.z }));
      havenBusy = false;
      system.clearRun(id);
    } catch (_) { oops(_); }
  }, 10);
}
/** Makes the place lovely, once: blossom trees, flowers, animals, a fire that is already burning. */
function decorate(dim, spot) {
  if (wget("hh:haven_built", false)) return;
  wset("hh:haven_built", true);
  const put = (x, y, z, id) => { try { const b = dim.getBlock({ x, y, z }); if (b && (b.isAir || /grass$|fern|flower|petals|snow_layer/.test(b.typeId))) b.setType(id); } catch (_) { /* outside what is loaded */ } };
  const cx = Math.floor(spot.x), cz = Math.floor(spot.z);
  const tree = (x, z) => {
    const s = surface(dim, x, z);
    if (!s || s.water || !/grass|dirt|podzol|moss/.test(s.id)) return;
    const h = randInt(4, 6);
    for (let i = 0; i < h; i++) put(x, s.y + i, z, "minecraft:cherry_log");
    for (let dy = -2; dy <= 2; dy++) { const r = dy >= 2 ? 1 : dy >= 0 ? 3 : 2; for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++) if (dx * dx + dz * dz <= r * r + 1 && (dx || dz || dy >= 0)) put(x + dx, s.y + h - 1 + dy, z + dz, "minecraft:cherry_leaves"); }
  };
  let n = 0;
  for (let i = 0; i < 40 && n < 12; i++) { const a = rand(0, Math.PI * 2), d = rand(9, 30); tree(cx + Math.round(Math.cos(a) * d), cz + Math.round(Math.sin(a) * d)); n++; }
  const FLOWERS = ["minecraft:pink_petals", "minecraft:pink_petals", "minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:allium", "minecraft:oxeye_daisy", "minecraft:pink_tulip", "minecraft:lily_of_the_valley"];
  for (let i = 0; i < 90; i++) {
    const a = rand(0, Math.PI * 2), d = rand(2, 28), x = cx + Math.round(Math.cos(a) * d), z = cz + Math.round(Math.sin(a) * d), s = surface(dim, x, z);
    if (s && !s.water && /grass_block|moss/.test(s.id)) put(x, s.y, z, FLOWERS[randInt(0, FLOWERS.length - 1)]);
  }
  const fs = surface(dim, cx + 4, cz - 3);
  if (fs && !fs.water) put(cx + 4, fs.y, cz - 3, "minecraft:campfire");
  for (const [type, count] of [["minecraft:cow", 3], ["minecraft:sheep", 3], ["minecraft:chicken", 4], ["minecraft:pig", 2], ["minecraft:horse", 2], ["minecraft:rabbit", 2], ["minecraft:bee", 2]]) {
    for (let i = 0; i < count; i++) { try { const a = rand(0, Math.PI * 2), d = rand(6, 22), x = cx + Math.cos(a) * d, z = cz + Math.sin(a) * d, s = surface(dim, x, z); if (s && !s.water) dim.spawnEntity(type, { x, y: s.y, z }); } catch (_) { /* not loaded yet */ } }
  }
}
function havenTick(p, abs) {
  if (!playing(p)) return;
  const dim = p.dimension;
  if (!invaded(p)) {
    // no monsters here
    try { for (const m of dim.getEntities({ location: p.location, maxDistance: 72, families: ["monster"] })) if (!m.nameTag && !m.typeId.startsWith("ghost:")) m.remove(); } catch (_) { oops(_); }
    if (abs < Number(pget(p, "hh:r_invade_at", 1e15))) return;
    pset(p, "hh:r_invaded", true);
    notice(p, "rescue.invasion", true);
    playTo(p, "ghost.snap", 1, 0.7);
    try { p.runCommand("camerashake add @s 0.6 1.5 positional"); } catch (_) { oops(_); }
  }
  if (aboard(p)) return;
  // the helicopter waits somewhere out there, for as long as it takes
  if (!nearestHeli(p, EXTRACT)) arrive(dim, landing(dim, p.location, 28, 42), EXTRACT, p.location, -1);
  // and they hunt you all the way there
  if (system.currentTick % 200 < 20) {
    try {
      const want = isNight() ? 3 : 2;
      if (dim.getEntities({ families: ["ghost"], location: p.location, maxDistance: 64 }).length < want && api.huntNow) api.huntNow(p);
    } catch (_) { oops(_); }
  }
}

// ---------------------------------------------------------------- the clock ticks (every second)
function tick() {
  if (!CFG.ENABLED) return;
  const abs = absNow();
  if (!wget("hh:r_announced", false) && day() >= CFG.DAY) { wset("hh:r_announced", true); schedule(); }
  const all = players();
  const waiting = all.filter((p) => phase(p) === WAITING && playing(p));
  const pickupLive = liveHelis(PICKUP).length > 0, announced = wget("hh:r_announced", false);
  // someone still waiting and nothing planned (they missed it, or joined late): another one is coming
  if (announced && arriveAt() < 0 && !pickupLive && !wget("hh:r_pickup", false) && waiting.length) schedule();
  const at = arriveAt();
  if (at > abs) for (const p of waiting) {
    if (Number(pget(p, "hh:r_told", -1)) === at) continue;
    pset(p, "hh:r_told", at);
    notice(p, missed(p) > 0 ? "rescue.again" : "rescue.coming", false, ...when(at, p));
  }
  // it's here
  if (at >= 0 && abs >= at) {
    wset("hh:r_arrive", -1);
    wset("hh:r_pickup", true);
    dispatch(waiting);
  }
  // the pickup is over: whoever is still here missed it
  if (wget("hh:r_pickup", false) && !pickupLive && !dispatching && system.currentTick > 300) {
    wset("hh:r_pickup", false);
    const left = players().filter((p) => phase(p) === WAITING && playing(p));
    for (const p of left) { pset(p, "hh:r_missed", missed(p) + 1); playTo(p, "ghost.growl", 1, 0.6); }
    if (left.length) { schedule(); for (const p of left) { pset(p, "hh:r_told", arriveAt()); notice(p, "rescue.missed", true, ...when(arriveAt(), p)); } }
  }
  for (const p of all) { try { if (phase(p) === IN_HAVEN) havenTick(p, abs); } catch (_) { oops(_); } }
}
let dispatching = false;
/** A helicopter comes down near everyone still waiting in the overworld (one for each group of friends). */
export function dispatch(waiting) {
  const pads = [];
  dispatching = true;
  later(40, () => { dispatching = false; });
  for (const p of waiting) {
    try {
      if (p.dimension.id !== "minecraft:overworld") { notice(p, "rescue.away", true); continue; }
      if (!pads.some((v) => Math.hypot(v.x - p.location.x, v.z - p.location.z) < 96)) {
        const pad = landing(p.dimension, p.location, 14, 26);
        pads.push(pad);
        arrive(p.dimension, pad, PICKUP, p.location, CFG.PICKUP_MINUTES * 60 * 20);
      }
      notice(p, "rescue.landing", false, CFG.PICKUP_MINUTES);
    } catch (_) { oops(_); }
  }
}

// ---------------------------------------------------------------- what the player sees
function hudLine(p) {
  const h = aboard(p);
  const ph = phase(p);
  if (h) { const f = helis.get(h.id); if (!f) return ""; return "§b" + (f.state === LEAVING ? T("rescue.hud.leaving") : f.kind === PICKUP ? T("rescue.hud.aboard", mmss(ticksLeft(f))) : T("rescue.hud.aboard_out")); }
  if (ph === WAITING) { const f = nearestHeli(p, PICKUP); if (!f || f.state === LEAVING) return ""; return "§b" + T("rescue.hud.pickup", mmss(ticksLeft(f)), distTo(p, f.e.location), arrowTo(p, f.e.location)); }
  if (ph === IN_HAVEN && invaded(p)) { const f = nearestHeli(p, EXTRACT); if (!f) return ""; return "§c" + T("rescue.hud.extract", distTo(p, f.e.location), arrowTo(p, f.e.location)); }
  return "";
}
function appInfo(p) {
  const ph = phase(p), at = arriveAt(), abs = absNow(), lines = [];
  let heli = null, live;
  const f = nearestHeli(p, ph === IN_HAVEN ? EXTRACT : PICKUP);
  if (f) { heli = { ...f.e.location, dim: dimName(f.e.dimension) }; live = () => (ok(f.e) ? { ...f.e.location, dim: dimName(f.e.dimension) } : null); }
  if (ph === HOME) lines.push("§c" + T("rescue.app.nobody"), "§7" + T("rescue.app.harsh", CFG.RETURN[0], CFG.RETURN[1]));
  else if (ph === IN_HAVEN) lines.push(invaded(p) ? "§c§l" + T("rescue.app.run") + "§r\n§c" + T("rescue.app.runmap") : "§a" + T("rescue.app.safe"));
  else if (f) lines.push("§a§l" + T("rescue.app.here"), "§f" + T("rescue.app.board"));
  else if (at > abs) {
    const left = at - abs, d = Math.floor(left / 24000), h = Math.floor((left % 24000) / 1000), m = Math.floor((left % 1000) * 60 / 1000);
    lines.push("§b" + T("rescue.app.arrives"), "§f§l" + T("rescue.app.left", d, h, m), "§7" + T("rescue.app.when", Math.floor(at / 24000) + 1, clock(at)), "§7" + T("rescue.app.ready"));
    if (missed(p) > 0) lines.push("§c" + T("rescue.app.harsh", CFG.MISSED[0], CFG.MISSED[1]));
  } else lines.push("§7" + T(wget("hh:r_announced", false) ? "rescue.app.nosignal" : "rescue.app.nonews"));
  return { lines, heli, live };
}

/** for tests and commands */
export const _rescue = { liveHelis, riders, surface, tick, takeOff, toHome, toHaven, decorate, PICKUP, EXTRACT, LANDED, LEAVING, helis };
/** /scriptevent hollow:rescue now|invade|reset|status */
export function rescueCommand(a, p, tell) {
  const who = p ? [p] : players();
  if (a === "now") { wset("hh:r_announced", true); wset("hh:r_arrive", absNow()); tell("the helicopter is coming now"); }
  else if (a === "invade") { for (const q of who) if (phase(q) === IN_HAVEN) pset(q, "hh:r_invade_at", 0); tell("the haven is found"); }
  else if (a === "reset") {
    for (const q of who) for (const k of ["hh:r_phase", "hh:r_missed", "hh:r_invaded", "hh:r_invade_at", "hh:r_told"]) pset(q, k, undefined);
    wset("hh:r_arrive", -1); wset("hh:r_announced", false); wset("hh:r_pickup", false);
    for (const f of liveHelis()) { try { f.e.remove(); } catch (_) { oops(_); } } helis.clear();
    tell("rescue story reset");
  } else {
    const at = arriveAt();
    tell(`rescue: ${at < 0 ? "nothing planned" : "day " + (Math.floor(at / 24000) + 1) + " at " + clock(at)} · helicopters ${liveHelis().length} · haven ${havenSpot() ? JSON.stringify(havenSpot()) : "not found yet"}` + (p ? ` · phase ${phase(p)} · missed ${missed(p)}` : ""));
  }
}

export function initRescue(a) {
  api = a || {};
  setCruelty((p, blood) => (p ? (safe(p) ? 1 : harshness(p, blood)) : harshest(blood)));
  setCalm(safe);
  addHudLine(hudLine);
  setRescueInfo(appInfo);
  addPlaces((p) => { const f = nearestHeli(p); return f ? [{ name: T("map.heli"), ...f.e.location, dim: dimName(f.e.dimension), live: () => (ok(f.e) ? { ...f.e.location, dim: dimName(f.e.dimension) } : null) }] : []; });
  system.runInterval(() => { try { tick(); } catch (_) { oops(_); } }, 20);
  system.runInterval(() => { try { if (helis.size) heliTick(); } catch (_) { oops(_); } }, 1);
  // helicopters still standing after a restart
  system.runInterval(() => { try { for (const e of over().getEntities({ type: HELI })) if (!helis.has(e.id) && !flightOf(e)) e.remove(); } catch (_) { /* nobody around */ } }, 100);
  try {
    // only the people it came for may board
    world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
      try {
        if (ev.target.typeId !== HELI) return;
        const f = helis.get(ev.target.id), p = ev.player;
        const okFor = f && f.state === LANDED && ((f.kind === PICKUP && phase(p) === WAITING) || (f.kind === EXTRACT && phase(p) === IN_HAVEN));
        if (!okFor) { ev.cancel = true; system.run(() => say(p, "§7" + T("rescue.board.wrong"))); }
      } catch (_) { oops(_); }
    });
  } catch (_) { oops(_); }
}
