// Fighting back (port of the Java mod's combat, as far as Bedrock allows).
// Ghosts can't die, but every hit wears down their "poise"; at zero they are knocked out: they drop, lie there, and get
// up again after a while (angry). Soldier melee weapons hit ghosts harder; guns shoot (use the item; the rifle keeps
// firing while held; sneak + use to reload), grenades knock everything around. The three armour sets also make ghost
// hits softer.
import { world, system, ItemStack } from "@minecraft/server";
import { players, playing, ok, say, playTo, later, mainHand, invOf, markBar, oops } from "./store.js";
import { bloodMoon } from "./atmos.js";
import { T, L } from "./texts.js";

export const CFG = { KNOCKOUT: true, KO_SECONDS: 70, TOUGHNESS: 1.0 };
const BASE = [0, 70, 55, 100, 75, 85];
const GUNS = {
  "ghost:pistol": { mag: 12, damage: 7, poise: 9, delay: 6, auto: false, pellets: 1, spread: 1.4, range: 48, reload: 30, ammo: "ghost:pistol_ammo", sound: "gun.pistol" },
  "ghost:rifle": { mag: 30, damage: 5, poise: 6, delay: 3, auto: true, pellets: 1, spread: 2.4, range: 64, reload: 45, ammo: "ghost:rifle_ammo", sound: "gun.rifle" },
  "ghost:shotgun": { mag: 6, damage: 4, poise: 3.5, delay: 18, auto: false, pellets: 8, spread: 7, range: 24, reload: 50, ammo: "ghost:shotgun_shell", sound: "gun.shotgun" },
  "ghost:sniper": { mag: 5, damage: 24, poise: 30, delay: 30, auto: false, pellets: 1, spread: 0.15, range: 128, reload: 60, ammo: "ghost:sniper_ammo", sound: "gun.sniper" },
};
/** how hard each weapon knocks a ghost toward a knock-out (x its damage) */
const MELEE = { "ghost:combat_knife": [8, 1.0], "ghost:katana": [11, 1.3], "ghost:battle_axe": [13, 1.7], "ghost:stun_baton": [7, 2.6], "ghost:spear": [9, 1.3] };
const VANILLA = /(wooden|stone|iron|golden|diamond|netherite)_(sword|axe)$|trident$|mace$/;
const VDMG = { wooden: 4, golden: 4, stone: 5, iron: 6, diamond: 7, netherite: 8 };
const ARMOR = { tactical: 0.10, heavy: 0.20, hunter: 0.30 };
let api = {};
const isGhost = (e) => !!e && String(e.typeId).startsWith("ghost:ghost_");

// ---------------------------------------------------------------- poise and knock-outs
const poise = new Map();   // ghost id -> poise left
const kos = new Map();     // ghost id -> game tick it wakes up
const koHooks = [];
let keepDown = () => false;
export const knockedOut = (g) => kos.has(g.id);
export function onKnockOut(fn) { koHooks.push(fn); }
/** the last chapter keeps the five down once they are knocked out */
export function setKeepDown(fn) { keepDown = fn; }
function maxPoise(g) {
  let m = BASE[Number(String(g.typeId).slice(-1))] || 70;
  m *= CFG.TOUGHNESS;
  if (bloodMoon()) m *= 1.4;
  return m;
}
/** Wears a ghost's poise down; knocks it out at zero. */
export function poiseHit(g, p, amount, ranged) {
  if (!CFG.KNOCKOUT || !ok(g) || kos.has(g.id) || amount <= 0) return false;
  let m = null;
  try { m = api.ensureMeta(g, system.currentTick); } catch (_) { oops(_); }
  if (m && (m.leaving || (api.leaving && api.leaving.has(g.id)))) return false;
  const left = (poise.get(g.id) ?? maxPoise(g)) - amount;
  poise.set(g.id, left);
  try {
    const l = g.location;
    g.dimension.spawnParticle("minecraft:soul_particle", { x: l.x, y: l.y + 1.3, z: l.z });
    if (!ranged) g.dimension.playSound("game.player.attack.strong", l, { volume: 1, pitch: 0.7 });
  } catch (_) { oops(_); }
  // a ghost that was only watching or searching comes for whoever hit it
  try { const st = g.getProperty("ghost:state") ?? 0; if (st === 0 || st >= 8) { if (m) { m.stalking = false; m.appeaseUntil = 0; } g.triggerEvent("ghost:start_snap"); } } catch (_) { oops(_); }
  if (left <= 0) knockOut(g, p);
  return true;
}
export function knockOut(g, by) {
  const now = system.currentTick;
  kos.set(g.id, now + CFG.KO_SECONDS * 20);
  try { const m = api.ensureMeta(g, now); m.appeaseUntil = now + CFG.KO_SECONDS * 20 + 1e6; m.ko = true; m.search = null; } catch (_) { oops(_); }
  try { g.triggerEvent("ghost:appease"); } catch (_) { oops(_); }
  try { g.playAnimation("animation.ghost.ko", { controller: "hh_ko", stopExpression: "0", blendOutTime: 0.4 }); } catch (_) { oops(_); }
  const l = g.location, dim = g.dimension, n = Number(String(g.typeId).slice(-1));
  try {
    for (let k = 0; k < 6; k++) dim.spawnParticle("minecraft:large_explosion", { x: l.x + Math.random() - 0.5, y: l.y + 0.5, z: l.z + Math.random() - 0.5 });
    dim.playSound("mob.zombie.wood", l, { volume: 1.4, pitch: 0.5 });
    dim.playSound(`ghost.voice.g${n}.rage`, l, { volume: 2.2, pitch: 0.8 });
  } catch (_) { oops(_); }
  for (const p of players()) {
    try { if (p.dimension.id === dim.id && Math.hypot(p.location.x - l.x, p.location.z - l.z) < 40) { markBar(p); p.onScreenDisplay.setActionBar("§6" + T("ko.down", CFG.KO_SECONDS)); } } catch (_) { oops(_); }
  }
  for (const fn of koHooks) { try { fn(g, by); } catch (_) { oops(_); } }
}
function wake(g) {
  kos.delete(g.id);
  poise.set(g.id, maxPoise(g) * 0.7);
  try { g.playAnimation("animation.ghost.ko_end", { controller: "hh_ko", stopExpression: "0" }); } catch (_) { oops(_); }
  try { const m = api.ensureMeta(g, system.currentTick); m.ko = false; m.appeaseUntil = system.currentTick; } catch (_) { oops(_); }   // the main loop wakes it angry
  try { g.triggerEvent("ghost:enrage"); g.dimension.playSound("ghost.growl", g.location, { volume: 2.4, pitch: 0.6 }); } catch (_) { oops(_); }
}
function koTick() {
  const now = system.currentTick;
  for (const [id, until] of kos) {
    const g = world.getEntity(id);
    if (!g || !ok(g)) { kos.delete(id); continue; }
    try {
      if (now % 10 === 0) g.dimension.spawnParticle("minecraft:critical_hit_emitter", { x: g.location.x, y: g.location.y + 0.4, z: g.location.z });
      const m = api.ensureMeta(g, now);
      m.appeaseUntil = now + 1e6;
      if (now >= until && !keepDown(g)) wake(g);
    } catch (_) { oops(_); }
  }
}

// ---------------------------------------------------------------- melee
function meleeFactor(item) {
  if (!item) return [1, 0.3];
  const m = MELEE[item.typeId];
  if (m) return m;
  const v = item.typeId.match(VANILLA);
  if (v) return [(VDMG[v[1]] ?? 6) + (v[2] === "axe" ? 2 : 0), 0.6];
  return [1, 0.3];
}
function onHit(p, g) {
  const item = mainHand(p);
  if (GUNS[item?.typeId]) return;
  let [dmg, f] = meleeFactor(item);
  if (p.isSprinting) f *= 1.6;   // a running blow
  poiseHit(g, p, dmg * f, false);
  if (item?.typeId === "ghost:stun_baton") { try { g.addEffect("slowness", 60, { amplifier: 3, showParticles: true }); } catch (_) { oops(_); } }
}

// ---------------------------------------------------------------- guns
function ammoOf(it) { const v = it.getDynamicProperty("hh:ammo"); return typeof v === "number" ? v : 0; }
function setAmmo(p, it, n) {
  it.setDynamicProperty("hh:ammo", n);
  const spec = GUNS[it.typeId];
  try { it.setLore([`§e${T("gun.ammo", n, spec.mag)}`, `§7${L("ใช้: ยิง · ย่อ + ใช้: บรรจุกระสุน", "Use: shoot · Sneak + use: reload")}`]); } catch (_) { oops(_); }
  invOf(p).getSlot(p.selectedSlotIndex).setItem(it);
}
function countItem(p, id) { const c = invOf(p); let n = 0; for (let i = 0; i < c.size; i++) { const x = c.getItem(i); if (x && x.typeId === id) n += x.amount; } return n; }
function takeItem(p, id, n) {
  const c = invOf(p);
  for (let i = 0; i < c.size && n > 0; i++) { const x = c.getItem(i); if (!x || x.typeId !== id) continue; const k = Math.min(n, x.amount); n -= k; if (x.amount > k) { x.amount -= k; c.setItem(i, x); } else c.setItem(i, undefined); }
}
const reloading = new Map();   // player id -> tick it ends
const lastShot = new Map(), holding = new Map();
let creativeOf = (p) => { try { return String(p.getGameMode()).toLowerCase() === "creative"; } catch (_) { return false; } };
function reload(p) {
  const it = mainHand(p), spec = it && GUNS[it.typeId];
  if (!spec || reloading.has(p.id)) return false;
  const have = ammoOf(it), need = spec.mag - have;
  if (need <= 0) return false;
  const stock = creativeOf(p) ? need : Math.min(need, countItem(p, spec.ammo));
  if (stock <= 0) { markBar(p); p.onScreenDisplay.setActionBar("§c" + T("gun.noammo", L("กระสุน", "ammo"))); playTo(p, "gun.empty", 1, 1); return false; }
  reloading.set(p.id, system.currentTick + spec.reload);
  playTo(p, "gun.reload", 1, 1);
  markBar(p); p.onScreenDisplay.setActionBar("§7" + T("gun.reloading"));
  const slot = p.selectedSlotIndex, type = it.typeId;
  later(spec.reload, () => {
    reloading.delete(p.id);
    if (!ok(p) || p.selectedSlotIndex !== slot) return;          // switched away: the reload is lost
    const now = mainHand(p);
    if (!now || now.typeId !== type) return;
    const get = creativeOf(p) ? need : Math.min(need, countItem(p, spec.ammo));
    if (!creativeOf(p)) takeItem(p, spec.ammo, get);
    setAmmo(p, now, ammoOf(now) + get);
    playTo(p, "gun.cock", 1, 1);
  });
  return true;
}
function rot(v, deg) {
  // a random turn of up to deg degrees away from the aim
  const a = (Math.random() - 0.5) * 2 * deg * Math.PI / 180, b = (Math.random() - 0.5) * 2 * deg * Math.PI / 180;
  const ca = Math.cos(a), sa = Math.sin(a);
  let x = v.x * ca - v.z * sa, z = v.x * sa + v.z * ca, y = v.y + Math.sin(b);
  const len = Math.hypot(x, y, z) || 1;
  return { x: x / len, y: y / len, z: z / len };
}
const lastHits = [];
export function shoot(p) {
  const it = mainHand(p), spec = it && GUNS[it.typeId];
  if (!spec) return false;
  const now = system.currentTick;
  if (reloading.has(p.id) || now - (lastShot.get(p.id) ?? -100) < spec.delay) return false;
  const have = ammoOf(it);
  if (have <= 0) { reload(p); return false; }
  lastShot.set(p.id, now);
  setAmmo(p, it, have - 1);
  const dim = p.dimension, eye = p.getHeadLocation(), aim = p.getViewDirection();
  try { dim.playSound(spec.sound, eye, { volume: 3, pitch: 0.95 + Math.random() * 0.1 }); } catch (_) { oops(_); }
  const spread = p.isSneaking ? spec.spread * 0.4 : spec.spread;
  lastHits.length = 0;
  let hits = 0;
  for (let k = 0; k < spec.pellets; k++) {
    const dir = rot(aim, spread);
    let range = spec.range;
    try { const b = dim.getBlockFromRay(eye, dir, { maxDistance: spec.range, includePassableBlocks: false }); if (b) range = Math.hypot(b.block.location.x + b.faceLocation.x - eye.x, b.block.location.y + b.faceLocation.y - eye.y, b.block.location.z + b.faceLocation.z - eye.z); } catch (_) { oops(_); }
    let target = null;
    try { for (const h of dim.getEntitiesFromRay(eye, dir, { maxDistance: range })) { if (h.entity.id !== p.id && h.entity.typeId !== "minecraft:item") { target = h; break; } } } catch (_) { oops(_); }
    const end = target ? target.distance : range;
    for (let d = 1.5; d < Math.min(end, 24); d += 2.5) { try { dim.spawnParticle("minecraft:basic_crit_particle", { x: eye.x + dir.x * d, y: eye.y + dir.y * d, z: eye.z + dir.z * d }); } catch (_) { break; } }
    if (!target) continue;
    const e = target.entity;
    hits++;
    lastHits.push(e.id);
    if (isGhost(e)) poiseHit(e, p, spec.poise, true);
    else { try { e.applyDamage(spec.damage, { cause: "entityAttack", damagingEntity: p }); } catch (_) { oops(_); } }
  }
  // the kick
  try { p.runCommand(`camerashake add @s ${spec.pellets > 1 ? 0.25 : spec.damage > 20 ? 0.35 : 0.08} 0.12 rotational`); } catch (_) { oops(_); }
  // and the noise: every ghost around hears where it came from
  if (api.heard) { try { api.heard(p, 64); } catch (_) { oops(_); } }
  return hits;
}

// ---------------------------------------------------------------- grenades
function explode(e, by) {
  const dim = e.dimension, l = { ...e.location };
  try { e.remove(); } catch (_) { oops(_); }
  try { dim.createExplosion(l, 3, by && ok(by) ? { breaksBlocks: false, causesFire: false, source: by } : { breaksBlocks: false, causesFire: false }); } catch (_) { oops(_); }
  try { dim.playSound("gun.grenade", l, { volume: 4, pitch: 1 }); } catch (_) { oops(_); }
  try { for (const g of dim.getEntities({ families: ["ghost"], location: l, maxDistance: 6 })) if (isGhost(g)) poiseHit(g, by, 40 * (1 - Math.hypot(g.location.x - l.x, g.location.z - l.z) / 7), true); } catch (_) { oops(_); }
  if (api.heard && by) { try { api.heard(by, 80); } catch (_) { oops(_); } }
}
const thrown = new Map();   // grenade id -> { at, by }

// ---------------------------------------------------------------- armour: ghost hits are softer
export function ghostCut(p) {
  let v = 0;
  try {
    const eq = p.getComponent("minecraft:equippable");
    for (const s of ["Head", "Chest", "Legs", "Feet"]) { const it = eq.getEquipment(s); const m = it && it.typeId.match(/^ghost:(tactical|heavy|hunter)_/); if (m) v += ARMOR[m[1]] / 4; }
  } catch (_) { oops(_); }
  return v;
}

/** for tests */
export const _combat = { poise, kos, maxPoise, reload, ammoOf, setAmmo, wake, explode, GUNS, lastHits };

export function initCombat(a) {
  api = a || {};
  system.runInterval(() => { try { koTick(); } catch (_) { oops(_); } }, 5);
  world.afterEvents.entityHitEntity.subscribe((ev) => {
    try { const p = ev.damagingEntity, g = ev.hitEntity; if (p && p.typeId === "minecraft:player" && isGhost(g)) onHit(p, g); } catch (_) { oops(_); }
  });
  // guns: use = shoot (held: the rifle keeps firing), sneak + use = reload
  world.afterEvents.itemStartUse.subscribe((ev) => {
    try {
      const p = ev.source, it = ev.itemStack;
      if (!it || !GUNS[it.typeId]) return;
      if (p.isSneaking) { reload(p); return; }
      shoot(p);
      if (GUNS[it.typeId].auto) holding.set(p.id, true);
    } catch (_) { oops(_); }
  });
  const stop = (ev) => { try { holding.delete(ev.source.id); } catch (_) { oops(_); } };
  world.afterEvents.itemStopUse.subscribe(stop);
  world.afterEvents.itemReleaseUse.subscribe(stop);
  system.runInterval(() => {
    for (const id of holding.keys()) {
      const p = players().find((x) => x.id === id);
      if (!p) { holding.delete(id); continue; }
      try { const it = mainHand(p); if (!it || !GUNS[it.typeId] || !GUNS[it.typeId].auto) { holding.delete(id); continue; } shoot(p); } catch (_) { oops(_); }
    }
  }, 1);
  // ammo on the action bar while you hold a gun
  system.runInterval(() => {
    for (const p of players()) {
      try {
        const it = mainHand(p), spec = it && GUNS[it.typeId];
        if (!spec || reloading.has(p.id)) continue;
        markBar(p);
        p.onScreenDisplay.setActionBar(`§e${T("gun.ammo", ammoOf(it), spec.mag)}  §7(${countItem(p, spec.ammo)})`);
      } catch (_) { oops(_); }
    }
  }, 10);
  // grenades: they go off 2.5 seconds after they are thrown
  world.afterEvents.entitySpawn.subscribe((ev) => {
    try {
      const e = ev.entity;
      if (e.typeId !== "ghost:grenade_e") return;
      let by = null;
      try { by = e.getComponent("minecraft:projectile")?.owner ?? null; } catch (_) { oops(_); }
      if (!by) { let best = 9; for (const p of e.dimension.getPlayers({ location: e.location, maxDistance: 4 })) { const d = Math.hypot(p.location.x - e.location.x, p.location.z - e.location.z); if (d < best) { best = d; by = p; } } }
      const id = e.id;
      later(50, () => { const g = world.getEntity(id); if (g) explode(g, by); });
    } catch (_) { oops(_); }
  });
  // armour softens a ghost's hit (given back right after it lands)
  world.afterEvents.entityHurt.subscribe((ev) => {
    try {
      const p = ev.hurtEntity;
      if (!p || p.typeId !== "minecraft:player" || !isGhost(ev.damageSource.damagingEntity)) return;
      const cut = ghostCut(p);
      if (cut <= 0) return;
      const h = p.getComponent("minecraft:health");
      if (h.currentValue > 0) h.setCurrentValue(Math.min(h.effectiveMax, h.currentValue + ev.damage * cut));
    } catch (_) { oops(_); }
  });
}
