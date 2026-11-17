// Graves (port of the Java mod's Graves). When you die, everything you dropped and all your experience go into a grave
// where you fell, and you get a compass that points to it. Only you can open it (interact) or break it (hit it 3
// times: it crumbles and takes everything with it). After 15 minutes it crumbles by itself. What a crumbled grave took
// can be brought back with a Grave Restorer (3 uses): the grave rises again at your feet.
import { world, system, ItemStack } from "@minecraft/server";
import { pget, pset, pjson, players, say, later, ok, mainHand, invOf, dimName, markBar, oops } from "./store.js";
import { addHudLine } from "./survival.js";
import { T, L } from "./texts.js";

export const CFG = { ENABLED: true, MINUTES: 15, HITS: 3 };
const GRAVE = "ghost:grave", COMPASS = "ghost:grave_compass", RESTORER = "ghost:restorer";
const xpSeen = new Map();     // player.id -> total xp a moment ago (it is gone by the time the death event comes)
const dims = () => ["overworld", "nether", "the_end"].map((d) => { try { return world.getDimension(d); } catch (_) { return null; } }).filter(Boolean);
const key = (p) => "hhgrave:g" + String(p.id).replace(/[^0-9a-zA-Z]/g, "_");
const fmt = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;

function totalXp(p) {
  try { return p.getTotalXp(); } catch (_) { return 0; }
}
function graveOf(p) { return pjson(p, "hh:grave", null); }
function setGrave(p, g) { pset(p, "hh:grave", g ? JSON.stringify(g) : undefined); }

/** All loaded graves of this player. */
function findGraves(p) {
  const out = [];
  for (const d of dims()) { try { for (const e of d.getEntities({ type: GRAVE })) if (e.getDynamicProperty("hh:owner") === p.id) out.push(e); } catch (_) { oops(_); } }
  return out;
}
function label(g) {
  try { g.nameTag = `§7☠ §f${g.getDynamicProperty("hh:name") ?? "?"}  §e${fmt(Math.max(0, Number(g.getDynamicProperty("hh:left") ?? 0)))}`; } catch (_) { oops(_); }
}
function hasItem(p, id) {
  const c = invOf(p);
  if (!c) return false;
  for (let i = 0; i < c.size; i++) { try { const it = c.getItem(i); if (it && it.typeId === id) return true; } catch (_) { oops(_); } }
  return false;
}
function takeItems(p, id) {
  const c = invOf(p);
  if (!c) return;
  for (let i = 0; i < c.size; i++) { try { const it = c.getItem(i); if (it && it.typeId === id) c.setItem(i, undefined); } catch (_) { oops(_); } }
}
function give(p, stack) {
  try { const left = invOf(p).addItem(stack); if (left) p.dimension.spawnItem(left, p.location); } catch (_) { oops(_); }
}

/** Makes the grave: everything that just dropped around the place of death, and the experience. */
function bury(p, dim, loc, xp) {
  let drops = [];
  try { drops = dim.getEntities({ type: "minecraft:item", location: loc, maxDistance: 4 }); } catch (_) { oops(_); }
  const stacks = [];
  for (const e of drops) { try { const s = e.getComponent("minecraft:item").itemStack; if (s && s.typeId !== COMPASS) stacks.push([e, s]); else if (s) e.remove(); } catch (_) { oops(_); } }
  if (!stacks.length && xp <= 0) return null;   // keepInventory, or nothing to lose
  let y = loc.y;
  try { const hr = dim.heightRange; y = Math.max(hr.min + 1, Math.min(hr.max - 2, y)); } catch (_) { oops(_); }
  const g = dim.spawnEntity(GRAVE, { x: Math.floor(loc.x) + 0.5, y, z: Math.floor(loc.z) + 0.5 });
  const c = invOf(g);
  let kept = 0;
  for (const [e, s] of stacks) {
    try { const left = c.addItem(s); if (!left) { e.remove(); kept++; } } catch (_) { oops(_); }
  }
  try { for (const o of dim.getEntities({ type: "minecraft:xp_orb", location: loc, maxDistance: 5 })) o.remove(); } catch (_) { oops(_); }
  g.setDynamicProperty("hh:owner", p.id);
  g.setDynamicProperty("hh:name", p.name);
  g.setDynamicProperty("hh:left", CFG.MINUTES * 60);
  g.setDynamicProperty("hh:xp", xp);
  g.setDynamicProperty("hh:hits", 0);
  label(g);
  const at = g.location;
  setGrave(p, { x: Math.floor(at.x), y: Math.floor(at.y), z: Math.floor(at.z), dim: dimName(dim) });
  return { g, kept };
}

function open(p, g) {
  if (!ok(g) || !ok(p)) return;
  const owner = g.getDynamicProperty("hh:owner");
  if (owner !== p.id) { say(p, "§7" + T("grave.notyours", g.getDynamicProperty("hh:name") ?? "?")); return; }
  const c = invOf(g), mine = invOf(p);
  let left = 0;
  for (let i = 0; i < c.size; i++) {
    try {
      const it = c.getItem(i);
      if (!it) continue;
      const rest = mine.addItem(it);
      c.setItem(i, rest ?? undefined);
      if (rest) left++;
    } catch (_) { oops(_); }
  }
  const xp = Number(g.getDynamicProperty("hh:xp") ?? 0);
  if (xp > 0) { try { p.addExperience(xp); } catch (_) { oops(_); } g.setDynamicProperty("hh:xp", 0); }
  try { p.dimension.playSound("random.orb", p.location, { volume: 0.8, pitch: 0.8 }); } catch (_) { oops(_); }
  if (left > 0) { say(p, "§e" + T("grave.partial", left)); return; }
  say(p, "§a" + T("grave.opened"));
  puff(g);
  try { g.remove(); } catch (_) { oops(_); }
  setGrave(p, null);
  takeItems(p, COMPASS);
}
function puff(g) {
  try {
    const l = g.location, d = g.dimension;
    for (let k = 0; k < 8; k++) d.spawnParticle("minecraft:basic_smoke_particle", { x: l.x + Math.random() - 0.5, y: l.y + Math.random(), z: l.z + Math.random() - 0.5 });
    d.playSound("dig.gravel", l, { volume: 1, pitch: 0.7 });
  } catch (_) { oops(_); }
}

/** The grave is gone, with everything in it: it is put away where a Grave Restorer can find it again. */
function crumble(g, why) {
  if (!ok(g)) return;
  const owner = g.getDynamicProperty("hh:owner"), dim = g.dimension;
  const p = players().find((x) => x.id === owner);
  puff(g);
  let stored = false;
  try {
    const c = invOf(g);
    let any = Number(g.getDynamicProperty("hh:xp") ?? 0) > 0;
    for (let i = 0; i < c.size && !any; i++) if (c.getItem(i)) any = true;
    if (any) {
      // lifted to the top of the sky (nothing else is ever there) and saved, alone, as a structure of the world
      const l = g.location;
      let top = 318;
      try { top = dim.heightRange.max - 2; } catch (_) { oops(_); }
      const at = { x: Math.floor(l.x), y: top, z: Math.floor(l.z) };
      g.teleport({ x: at.x + 0.5, y: at.y, z: at.z + 0.5 });
      const id = "hhgrave:g" + String(owner).replace(/[^0-9a-zA-Z]/g, "_");
      try { world.structureManager.delete(id); } catch (_) { oops(_); }
      world.structureManager.createFromWorld(id, dim, at, { x: at.x, y: at.y + 1, z: at.z }, { includeBlocks: false, includeEntities: true, saveMode: "World" });
      stored = true;
    }
  } catch (e) { try { console.warn("[hollow] grave could not be stored: " + e); } catch (_) { oops(_); } }
  try { g.remove(); } catch (_) { oops(_); }
  if (p) {
    setGrave(p, null);
    takeItems(p, COMPASS);
    pset(p, "hh:lost", stored);
    say(p, "§c" + T(why === "smashed" ? "grave.smashed" : "grave.expired"));
  }
}

/** The Grave Restorer: the lost grave rises again at your feet. */
function restore(p) {
  const id = key(p);
  let has = false;
  try { has = !!world.structureManager.get(id); } catch (_) { oops(_); }
  if (!has) { say(p, "§7" + T("item.restorer.none")); return false; }
  const dim = p.dimension, l = p.location;
  let top = 318;
  try { top = dim.heightRange.max - 2; } catch (_) { oops(_); }
  const at = { x: Math.floor(l.x), y: top, z: Math.floor(l.z) };
  try { world.structureManager.place(id, dim, at, { includeBlocks: false, includeEntities: true }); } catch (e) { say(p, "§7" + T("item.restorer.none")); return false; }
  later(2, () => {
    try {
      let g = null;
      // (a grave that comes back out of a structure may have lost what was written on it: then it is the nameless one)
      const near = dim.getEntities({ type: GRAVE, location: { x: at.x + 0.5, y: at.y, z: at.z + 0.5 }, maxDistance: 8 });
      for (const e of near) if (e.getDynamicProperty("hh:owner") === p.id) g = e;
      if (!g) for (const e of near) if (e.getDynamicProperty("hh:owner") === undefined) g = e;
      if (!g) return;
      g.setDynamicProperty("hh:owner", p.id);
      g.setDynamicProperty("hh:name", p.name);
      try { world.structureManager.delete(id); } catch (_) { oops(_); }
      g.teleport({ x: Math.floor(p.location.x) + 0.5, y: p.location.y, z: Math.floor(p.location.z) + 0.5 });
      g.setDynamicProperty("hh:left", CFG.MINUTES * 60);
      g.setDynamicProperty("hh:hits", 0);
      label(g);
      const c = invOf(g);
      let n = 0;
      for (let i = 0; i < c.size; i++) if (c.getItem(i)) n++;
      setGrave(p, { x: Math.floor(p.location.x), y: Math.floor(p.location.y), z: Math.floor(p.location.z), dim: dimName(dim) });
      pset(p, "hh:lost", false);
      say(p, "§a" + T("restorer.done", n));
      try { dim.playSound("block.end_portal_frame.fill", p.location, { volume: 1, pitch: 0.6 }); } catch (_) { oops(_); }
    } catch (_) { oops(_); }
  });
  return true;
}

// ---- the compass: while you hold it the action bar shows the way
const ARROWS = ["↑", "↗", "→", "↘", "↓", "↙", "←", "↖"];
function compassLine(p) {
  const h = mainHand(p);
  if (!h || h.typeId !== COMPASS) return "";
  const g = graveOf(p);
  if (!g) return "§7☠ " + L("ไม่มีหลุมศพ", "no grave");
  if (g.dim !== dimName(p.dimension)) return "§7☠ " + T("item.grave_compass.dim", g.dim);
  const dx = g.x + 0.5 - p.location.x, dz = g.z + 0.5 - p.location.z, dist = Math.round(Math.hypot(dx, dz));
  const v = p.getViewDirection();
  // angle from where you look to where the grave is, clockwise
  let a = Math.atan2(dz, dx) - Math.atan2(v.z, v.x);
  a = ((a * 180 / Math.PI) % 360 + 360) % 360;
  const arrow = ARROWS[Math.round(a / 45) % 8];
  const dy = g.y - Math.floor(p.location.y);
  return `§e☠ ${arrow} §f${dist} m${Math.abs(dy) > 3 ? (dy > 0 ? " §7▲" : " §7▼") + Math.abs(dy) : ""}`;
}

function tick() {
  for (const d of dims()) {
    let list = [];
    try { list = d.getEntities({ type: GRAVE }); } catch (_) { continue; }
    for (const g of list) {
      try {
        const left = Number(g.getDynamicProperty("hh:left") ?? 0) - 1;
        g.setDynamicProperty("hh:left", left);
        if (left <= 0) crumble(g, "expired"); else label(g);
      } catch (_) { oops(_); }
    }
  }
  for (const p of players()) { try { xpSeen.set(p.id, totalXp(p)); } catch (_) { oops(_); } }
}

/** for tests */
export const _grave = { bury, open, crumble, restore, findGraves, graveOf, compassLine };

export function initGraves() {
  system.runInterval(() => { try { tick(); } catch (_) { oops(_); } }, 20);
  addHudLine(compassLine);
  try {
    world.afterEvents.entityDie.subscribe((ev) => {
      try {
        const p = ev.deadEntity;
        if (!CFG.ENABLED || !p || p.typeId !== "minecraft:player") return;
        const dim = p.dimension, loc = { x: p.location.x, y: p.location.y, z: p.location.z }, xp = xpSeen.get(p.id) ?? 0;
        // an older grave is lost when you die again
        for (const old of findGraves(p)) crumble(old, "expired");
        later(2, () => {
          const r = bury(p, dim, loc, xp);
          if (r) pset(p, "hh:grave_msg", true);
        });
      } catch (_) { oops(_); }
    });
    world.afterEvents.playerSpawn.subscribe((ev) => {
      try {
        const p = ev.player;
        if (ev.initialSpawn) return;
        later(10, () => {
          const g = graveOf(p);
          if (!g || !pget(p, "hh:grave_msg", false)) return;
          pset(p, "hh:grave_msg", false);
          say(p, "§e" + T("grave.made", g.x, g.y, g.z, CFG.MINUTES));
          if (!hasItem(p, COMPASS)) give(p, new ItemStack(COMPASS, 1));
        });
      } catch (_) { oops(_); }
    });
    world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
      try {
        if (ev.target.typeId !== GRAVE) return;
        ev.cancel = true;
        const p = ev.player, g = ev.target;
        system.run(() => { try { open(p, g); } catch (_) { oops(_); } });
      } catch (_) { oops(_); }
    });
    world.afterEvents.entityHitEntity.subscribe((ev) => {
      try {
        const g = ev.hitEntity, p = ev.damagingEntity;
        if (!g || g.typeId !== GRAVE || !p || p.typeId !== "minecraft:player") return;
        if (g.getDynamicProperty("hh:owner") !== p.id) { say(p, "§7" + T("grave.notyours", g.getDynamicProperty("hh:name") ?? "?")); return; }
        const hits = Number(g.getDynamicProperty("hh:hits") ?? 0) + 1;
        g.setDynamicProperty("hh:hits", hits);
        try { g.dimension.playSound("dig.stone", g.location, { volume: 1, pitch: 0.7 }); } catch (_) { oops(_); }
        if (hits >= CFG.HITS) crumble(g, "smashed");
        else { markBar(p); p.onScreenDisplay.setActionBar("§c" + T("grave.hit", hits, CFG.HITS)); }
      } catch (_) { oops(_); }
    });
    world.afterEvents.itemUse.subscribe((ev) => {
      try {
        const p = ev.source, it = ev.itemStack;
        if (!it) return;
        if (it.typeId === COMPASS) {
          const g = graveOf(p);
          say(p, g ? "§e" + T("item.grave_compass.pos", g.x, g.y, g.z) + (g.dim !== dimName(p.dimension) ? " §7(" + T("item.grave_compass.dim", g.dim) + ")" : "") : "§7" + L("ไม่มีหลุมศพ", "You have no grave"));
        } else if (it.typeId === RESTORER) {
          if (!restore(p)) return;
          const h = mainHand(p);
          if (!h || h.typeId !== RESTORER) return;
          const d = h.getComponent("minecraft:durability");
          const slot = p.getComponent("minecraft:inventory").container.getSlot(p.selectedSlotIndex);
          if (d && d.damage + 1 >= d.maxDurability) { slot.setItem(undefined); try { p.dimension.playSound("random.break", p.location); } catch (_) { oops(_); } }
          else if (d) { d.damage = d.damage + 1; slot.setItem(h); say(p, "§7" + T("item.restorer.left", d.maxDurability - d.damage)); }
        }
      } catch (_) { oops(_); }
    });
  } catch (_) { oops(_); }
}
