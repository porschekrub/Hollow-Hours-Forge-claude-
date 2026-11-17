// The haunting's weather (port of the Java mod's Atmosphere), saved with the world:
// - Blood moon: at dusk each night there is a 10% chance the moon rises red. Until dawn the ghosts come 3x as often,
//   hit 3x as hard, run a bit faster, and a hunt may bring two ghosts at once. The whole sky turns dark red.
// - Fog: now and then thick fog rolls in for a few minutes (more likely at night, always a little on a blood moon).
import { world, system } from "@minecraft/server";
import { wget, wset, players, say, playTo, ok, oops } from "./store.js";
import { storyActive, isNight, timeOfDay, onStoryChange } from "./story.js";
import { T } from "./texts.js";

export const CFG = { BLOOD_CHANCE: 0.10, FOG_CHANCE: 0.03, FOG: true };
const rand = (a, b) => a + Math.random() * (b - a);
let raining = false;
let blood = false, force = 0, fogUntil = 0, fogStrength = 0, nextFogCheck = 0;
const riseHooks = [];
/** how much crueller the ghosts are (the rescue raises it): (player or null for "the worst of everyone", blood moon?) => 1.. */
let cruelty = () => 1;
/** players the haunting leaves alone (the haven, before it is found) */
let calmFor = () => false;
export function setCruelty(fn) { cruelty = fn; }
export function setCalm(fn) { calmFor = fn; }
export const isCalm = (p) => { try { return calmFor(p); } catch (_) { return false; } };

export const bloodMoon = () => blood;
export function onBloodRise(fn) { riseHooks.push(fn); }
/** Commands / tests: 1 = force the next (or current) night to be a blood moon, -1 = never, 0 = random. */
export function forceBlood(v) { force = v; if (v < 0 && blood) { blood = false; wset("hh:blood", false); } tick(); }
const absNow = () => { try { return world.getAbsoluteTime(); } catch (_) { return 0; } };
export function startFog(ticks, strength) { fogUntil = absNow() + ticks; fogStrength = strength; wset("hh:fog_until", fogUntil); wset("hh:fog_strength", strength); sync(); }
export function stopFog() { fogUntil = 0; wset("hh:fog_until", 0); sync(); }
export function fogNow() {
  let f = fogUntil > absNow() && CFG.FOG ? fogStrength : 0;
  if (blood) f = Math.max(f, 0.35);
  return f;
}
/** How the ghosts change tonight. */
export function mods(p) {
  const b = blood ? 3 : 1;
  let all = 1, mine = 1;
  try { all = cruelty(null, blood); if (p) mine = cruelty(p, blood); } catch (_) { oops(_); }
  return { gap: 1 / Math.max(b, all), damage: Math.max(b, mine), speed: blood ? 1.15 : 1, maxAlive: blood ? 2 : 1 };
}

function announce() {
  for (const p of players()) {
    try {
      if (p.dimension.id !== "minecraft:overworld" || isCalm(p)) continue;
      p.onScreenDisplay.setTitle(T("bloodmoon.title"), { subtitle: T("bloodmoon.sub"), fadeInDuration: 20, stayDuration: 80, fadeOutDuration: 30 });
      playTo(p, "ghost.scare.bell", 1.6, 0.5);
      playTo(p, "ghost.horror.choir", 1.2, 0.7);
      say(p, "§4" + T("bloodmoon.chat"));
    } catch (_) { oops(_); }
  }
  for (const fn of riseHooks) { try { fn(); } catch (_) { oops(_); } }
}

function tick() {
  const abs = absNow(), night = isNight(), nightNo = Math.floor(abs / 24000);
  const random = force === 0 && storyActive();
  if (night && wget("hh:rolled_night", -1) !== nightNo) {
    wset("hh:rolled_night", nightNo);
    blood = force > 0 || (random && Math.random() < CFG.BLOOD_CHANCE);
    wset("hh:blood", blood);
    if (blood) announce();
  } else if (!night && blood) {
    blood = false; wset("hh:blood", false);
    for (const p of players()) { try { if (p.dimension.id === "minecraft:overworld") say(p, "§7§o" + T("bloodmoon.end")); } catch (_) { oops(_); } }
  }
  if (force > 0 && night && !blood) { blood = true; wset("hh:blood", true); announce(); }
  // ---- fog
  if (!CFG.FOG) fogUntil = 0;
  else if (abs >= fogUntil && abs >= nextFogCheck && storyActive()) {
    nextFogCheck = abs + 60 * 20;
    if (Math.random() < CFG.FOG_CHANCE * (night ? 2 : 1) * (raining ? 1.5 : 1)) startFog(Math.round(rand(120 * 20, 360 * 20)), rand(0.55, 1.0));
  }
  if (fogUntil > 0 && abs >= fogUntil) { fogUntil = 0; wset("hh:fog_until", 0); }
  sync();
}

// ---- what each player sees: one fog setting pushed under our own name, swapped when it changes
const shown = new Map();
function wanted(p) {
  try {
    if (p.dimension.id !== "minecraft:overworld" || isCalm(p)) return "";
    const f = fogNow();
    if (blood) return f > 0.6 ? "ghost:fog_blood_thick" : "ghost:fog_blood";
    if (f <= 0) return "";
    return f >= 0.75 ? "ghost:fog_thick" : "ghost:fog_light";
  } catch (_) { return ""; }
}
function sync() {
  for (const p of players()) {
    try {
      const want = wanted(p), had = shown.get(p.id);
      if (had === want) continue;
      shown.set(p.id, want);
      try { p.runCommand("fog @s remove hh"); } catch (_) { oops(_); }
      if (want) p.runCommand(`fog @s push ${want} hh`);
    } catch (_) { oops(_); }
  }
}
/** what a player's sky looks like right now ("" = normal) - for tests */
export const fogOf = (p) => shown.get(p.id) ?? "";

export function initAtmos() {
  blood = wget("hh:blood", false) === true;
  fogUntil = wget("hh:fog_until", 0);
  fogStrength = wget("hh:fog_strength", 0);
  system.runInterval(() => { try { tick(); } catch (_) { oops(_); } }, 20);
  onStoryChange(() => sync());
  try { world.afterEvents.weatherChange.subscribe((ev) => { raining = String(ev.newWeather).toLowerCase() !== "clear"; }); } catch (_) { oops(_); }
  try { world.afterEvents.playerSpawn.subscribe((ev) => { shown.delete(ev.player.id); }); } catch (_) { oops(_); }
  try { world.afterEvents.playerLeave.subscribe((ev) => { shown.delete(ev.playerId); }); } catch (_) { oops(_); }
  try { world.afterEvents.playerDimensionChange.subscribe((ev) => { shown.delete(ev.player.id); }); } catch (_) { oops(_); }
}
