// The last chapter (port of the Java mod's Endgame / Arena / WeepingEntity / RiftEntity).
//  1 COUNTDOWN - back home from the haven, the days are counted (20). Near the end the crying starts.
//  2 HUNT      - on that night all five come at once. Knock every one of them out: down they stay.
//  3 MAP       - with the last one down they remember who they were and fade. Everyone gets an old map of Mist Hollow
//                with the mountain marked. Under the mountain, in the old gold mine, the thing that weeps is waiting.
//  4 RIFT      - it is beaten; where it sank a rift of light opens. Step in: a flash, a boom, the credits.
//  5 DONE      - the world is quiet. No more ghosts. Play on.
import { world, system, ItemStack, BlockPermutation } from "@minecraft/server";
import { wget, wset, wjson, pget, pset, players, playing, ok, say, playTo, later, mainHand, invOf, markBar, oops } from "./store.js";
import { day, timeOfDay, setEnded } from "./story.js";
import { addHudLine, addSanity } from "./survival.js";
import { notify } from "./phone.js";
import { phase as rescuePhase, HOME, onHome } from "./rescue.js";
import { onKnockOut, setKeepDown, poiseHit, knockedOut } from "./combat.js";
import { arrowTo, distTo, navigate } from "./nav.js";
import { T, L } from "./texts.js";

export const CFG = { ENABLED: true, DAYS: 20, DISTANCE: 520, KO_KEEP: true };
export const NONE = 0, COUNTDOWN = 1, HUNT = 2, MAP = 3, RIFT = 4, DONE = 5;
const R_MOUNT = 40, H_MOUNT = 30, RX = 22, RY = 14, DEPTH = 19, MOUTH = 44, TRENCH = 56;
const rand = (a, b) => a + Math.random() * (b - a);
const over = () => world.getDimension("overworld");
let api = {};
const stage = () => Number(wget("hh:e_stage", NONE));
function setStage(s) { wset("hh:e_stage", s); setEnded(s >= MAP); }
export const endStage = stage;
const down = () => Number(wget("hh:e_down", 0));
const isDown = (n) => (down() & (1 << n)) !== 0;
const site = () => wjson("hh:e_site", null);
const ghostName = (n) => ({ rawtext: [{ translate: `entity.ghost:ghost_${n}.name` }] });
function msg(p, text, danger) { notify(p, "???", text, danger); }
function title(p, t, sub, stay = 90) { try { p.onScreenDisplay.setTitle(t, { subtitle: sub, fadeInDuration: 10, stayDuration: stay, fadeOutDuration: 30 }); } catch (_) { oops(_); } }

// ---------------------------------------------------------------- countdown
export function daysLeft() { return Number(wget("hh:e_from", day())) + CFG.DAYS - day(); }
function startCountdown() { if (stage() !== NONE || !CFG.ENABLED) return; setStage(COUNTDOWN); wset("hh:e_from", day()); }
function countdownTick() {
  const left = daysLeft(), tod = timeOfDay();
  for (const p of players()) {
    if (rescuePhase(p) !== HOME) continue;
    const warned = Number(pget(p, "hh:e_warned", 0));
    if (left <= 3 && warned < 1) { pset(p, "hh:e_warned", 1); msg(p, T("endgame.msg.crying"), true); say(p, "§8§o" + T("endgame.crying")); playTo(p, "boss.cry", 0.35, 0.8); }
    if (left <= 0 && warned < 2 && tod >= 6000) { pset(p, "hh:e_warned", 2); msg(p, T("endgame.msg.tonight"), true); title(p, "§4" + T("endgame.tonight"), "§7" + T("endgame.tonight.sub")); }
  }
  if (left <= 0 && tod >= 13000 && tod < 23000 && players().length) startHunt();
}

// ---------------------------------------------------------------- the hunt: all five at once
const missing = new Map();
export function startHunt() {
  setStage(HUNT); wset("hh:e_down", 0);
  for (const p of players()) { title(p, "§4" + T("endgame.hunt.title"), "§7" + T("endgame.hunt.sub")); msg(p, T("endgame.msg.hunt"), true); playTo(p, "boss.cry", 0.7, 0.7); }
  for (let n = 1; n <= 5; n++) later(20 + n * 50, () => spawnFor(n));
}
function huntTargets() { return over().getPlayers().filter((p) => playing(p)); }
function spawnFor(n) {
  if (stage() !== HUNT || isDown(n)) return null;
  const ts = huntTargets();
  if (!ts.length) return null;
  const p = ts[Math.floor(Math.random() * ts.length)];
  return api.huntNow ? api.huntNow(p, n) : null;
}
function huntTick() {
  const alive = {};
  try { for (const g of over().getEntities({ families: ["ghost"] })) { const n = Number(String(g.typeId).slice(-1)); if (String(g.typeId).startsWith("ghost:ghost_")) alive[n] = true; } } catch (_) { oops(_); }
  const now = system.currentTick;
  for (let n = 1; n <= 5; n++) {
    if (isDown(n) || alive[n]) { missing.delete(n); continue; }
    // one that is not down yet keeps coming back (a little while after it wandered off)
    if (!missing.has(n)) missing.set(n, now);
    if (now - missing.get(n) > 20 * 25 && spawnFor(n)) missing.delete(n);
  }
}
function knockedDown(g) {
  if (stage() !== HUNT) return;
  const n = Number(String(g.typeId).slice(-1));
  if (isDown(n)) return;
  wset("hh:e_down", down() | (1 << n));
  try { g.setDynamicProperty("hh:down_for_good", true); } catch (_) { oops(_); }
  let c = 0; for (let k = 1; k <= 5; k++) if (isDown(k)) c++;
  const [a, b] = T("endgame.down", "\u0001", c).split("\u0001");
  for (const p of players()) say(p, { rawtext: [{ text: "§6" + a }, ghostName(n), { text: "§6" + (b ?? "") }] });
  if (c >= 5) later(60, toMap);
}

// ---------------------------------------------------------------- the map
let fadedAt = -1e9;
function pickSite() {
  let cx = 0, cz = 0, n = 0;
  for (const p of over().getPlayers()) { cx += p.location.x; cz += p.location.z; n++; }
  if (!n) { const s = world.getDefaultSpawnLocation(); cx = s.x; cz = s.z; } else { cx /= n; cz /= n; }
  const a = rand(0, Math.PI * 2);
  return { x: Math.floor(cx + Math.cos(a) * CFG.DISTANCE), z: Math.floor(cz + Math.sin(a) * CFG.DISTANCE), dir: rand(0, Math.PI * 2) };
}
export function toMap() {
  if (stage() >= MAP) return;
  fadedAt = system.currentTick;
  if (!site()) wset("hh:e_site", JSON.stringify(pickSite()));
  setStage(MAP);
  // the five remember who they were, and fade
  let i = 0;
  for (const g of over().getEntities({ families: ["ghost"] })) {
    if (!String(g.typeId).startsWith("ghost:ghost_")) { try { g.remove(); } catch (_) { oops(_); } continue; }
    const n = Number(String(g.typeId).slice(-1)), delay = 20 + i++ * 50;
    later(delay, () => { for (const p of players()) if (ok(g) && distTo(p, g.location) < 64) say(p, { rawtext: [{ text: "§b" }, ghostName(n), { text: "§7: " + T(`endgame.freed.${n}`) }] }); });
    later(delay + 70, () => {
      if (!ok(g)) return;
      try { const l = g.location; for (let k = 0; k < 10; k++) g.dimension.spawnParticle("minecraft:soul_particle", { x: l.x + rand(-0.5, 0.5), y: l.y + rand(0, 2), z: l.z + rand(-0.5, 0.5) }); g.dimension.playSound("boss.release", l, { volume: 1.6, pitch: 1.2 }); } catch (_) { oops(_); }
      try { g.remove(); } catch (_) { oops(_); }
    });
  }
  later(40 + i * 50 + 80, () => {
    for (const p of players()) { giveMap(p); title(p, "§6" + T("endgame.map.title"), "§7" + T("endgame.map.sub"), 100); msg(p, T("endgame.msg.map"), false); }
  });
}
function giveMap(p) {
  if (pget(p, "hh:e_map", false)) return;
  pset(p, "hh:e_map", true);
  const left = invOf(p).addItem(new ItemStack("ghost:old_map", 1));
  if (left) p.dimension.spawnItem(left, p.location);
  const s = site();
  if (s) navigate(p, L("หมู่บ้านหมอก", "Mist Hollow"), { x: s.x, y: 64, z: s.z, dim: "overworld" });
}
function mapLine(p) {
  const h = mainHand(p);
  if (!h || h.typeId !== "ghost:old_map") return "";
  const s = site();
  if (!s) return "";
  const d = distTo(p, s);
  if (d < RX) return "§6" + T("endgame.map.here");
  return "§6" + T("endgame.map.dist", d, arrowTo(p, s));
}

// ---------------------------------------------------------------- the mountain and the mine
const queue = [];
let planned = false;
function surface(dim, x, z) {
  try { const b = dim.getTopmostBlock({ x, z }); if (!b) return null; let t = b; for (let i = 0; i < 20 && t && /leaves|log|vine|snow_layer|grass$|fern|flower|bush/.test(t.typeId); i++) t = t.below(); return t ? t.location.y + 1 : null; } catch (_) { return null; }
}
function put(x, y, z, id, states) { queue.push([x, y, z, id, states]); }
const sq = (v) => v * v;
function mulberry(seed) { return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
function plan(dim, s) {
  const rnd = mulberry(s.x * 31 + s.z * 7 + 7), pickR = (...a) => a[Math.floor(rnd() * a.length)];
  const cx = s.x, cz = s.z;
  const hs = [];
  for (let i = -3; i <= 3; i++) for (let j = -3; j <= 3; j++) { const h = surface(dim, cx + i * 9, cz + j * 9); if (h !== null) hs.push(h); }
  if (hs.length < 30) return false;   // not loaded yet
  hs.sort((a, b) => a - b);
  const base = hs[Math.floor(hs.length / 2)];
  let minY = -64;
  try { minY = dim.heightRange.min; } catch (_) { oops(_); }
  const shallow = base - DEPTH < minY + 8, floorY = shallow ? base : base - DEPTH;
  const dir = { x: Math.cos(s.dir), z: Math.sin(s.dir) }, side = { x: -dir.z, z: dir.x };
  queue.length = 0;
  // ---- the mountain
  for (let dx = -R_MOUNT; dx <= R_MOUNT; dx++) for (let dz = -R_MOUNT; dz <= R_MOUNT; dz++) {
    const dist = Math.hypot(dx, dz), ang = Math.atan2(dz, dx);
    const edge = R_MOUNT * (0.86 + 0.14 * Math.sin(ang * 3 + 1.3) * Math.cos(ang * 5 - 0.4));
    if (dist >= edge) continue;
    const k = 1 - dist / edge;
    const h = H_MOUNT * Math.pow(k, 1.6) * (0.9 + 0.12 * Math.sin(dx * 0.31 + dz * 0.17) + 0.08 * Math.cos(dx * 0.11 - dz * 0.27)) + 2.5 * Math.max(0, Math.sin(ang * 4 + dist * 0.25)) * k;
    const top = base + Math.round(h), ground = surface(dim, cx + dx, cz + dz) ?? base;
    if (top <= ground) continue;
    for (let y = ground; y <= top; y++) {
      const fromTop = top - y;
      let id;
      if (fromTop === 0) id = top - base > 24 ? "minecraft:snow" : top - base < 4 ? (rnd() < 0.33 ? "minecraft:coarse_dirt" : "minecraft:grass_block") : pickR("minecraft:stone", "minecraft:stone", "minecraft:andesite", "minecraft:gravel", "minecraft:tuff", "minecraft:cobblestone");
      else if (fromTop < 3 && top - base < 4) id = "minecraft:dirt";
      else id = rnd() < 0.11 ? "minecraft:andesite" : "minecraft:stone";
      put(cx + dx, y, cz + dz, id);
    }
  }
  // ---- the cavern: an uneven dome, walls of deepslate run through with gold
  const wall = () => { const r = rnd(); return r < 0.04 ? "minecraft:deepslate_gold_ore" : r < 0.1 ? "minecraft:cobbled_deepslate" : r < 0.13 ? "minecraft:tuff" : "minecraft:deepslate"; };
  for (let dx = -RX - 3; dx <= RX + 3; dx++) for (let dz = -RX - 3; dz <= RX + 3; dz++) for (let dy = -2; dy <= RY + 3; dy++) {
    const wob = 1 + 0.08 * Math.sin(dx * 0.4 + dy * 0.3) * Math.cos(dz * 0.35 - dy * 0.2) + 0.05 * Math.sin((dx + dz) * 0.7);
    const e = sq(dx / (RX * wob)) + sq(Math.max(0, dy) / RY) + sq(dz / (RX * wob));
    const x = cx + dx, y = floorY + dy, z = cz + dz;
    if (dy < 0) { if (e < 1.0 && dy === -1) put(x, y, z, rnd() < 0.2 ? "minecraft:gravel" : rnd() < 0.3 ? "minecraft:cobbled_deepslate" : "minecraft:deepslate"); else if (e < 1.25) put(x, y, z, wall()); continue; }
    if (e < 1.0) put(x, y, z, "minecraft:air"); else if (e < 1.32) put(x, y, z, wall());
  }
  // the pit it sits in, rubble, old bones
  for (let dx = -5; dx <= 5; dx++) for (let dz = -5; dz <= 5; dz++) { const r = Math.hypot(dx, dz); if (r < 4.6) put(cx + dx, floorY - 1, cz + dz, r < 3 ? "minecraft:blackstone" : "minecraft:basalt"); }
  for (let i = 0; i < 26; i++) {
    const a = rnd() * Math.PI * 2, r = 7 + rnd() * (RX - 9), x = cx + Math.round(Math.cos(a) * r), z = cz + Math.round(Math.sin(a) * r), kind = Math.floor(rnd() * 10);
    put(x, floorY, z, kind < 4 ? "minecraft:cobbled_deepslate" : kind < 6 ? "minecraft:bone_block" : kind < 8 ? "minecraft:tuff" : "minecraft:raw_gold_block");
    if (rnd() < 0.33) put(x, floorY + 1, z, kind < 6 ? "minecraft:skeleton_skull" : "minecraft:cobbled_deepslate_wall");
  }
  // soul lanterns on chains, candles round the pit
  for (let i = 0; i < 6; i++) {
    const a = i / 6 * Math.PI * 2 + 0.4, r = 11, x = cx + Math.round(Math.cos(a) * r), z = cz + Math.round(Math.sin(a) * r);
    const ceil = floorY + Math.floor(RY * Math.sqrt(Math.max(0, 1 - sq(r / RX))) - 0.5), bottom = floorY + 7 + Math.floor(rnd() * 3);
    for (let y = ceil; y > bottom; y--) put(x, y, z, "minecraft:chain");
    put(x, bottom, z, "minecraft:soul_lantern", { hanging: true });
  }
  for (let i = 0; i < 10; i++) { const a = i / 10 * Math.PI * 2; put(cx + Math.round(Math.cos(a) * 6.5), floorY, cz + Math.round(Math.sin(a) * 6.5), "minecraft:white_candle", { candles: Math.floor(rnd() * 3), lit: true }); }
  // ---- the mine: a cut in the ground, the tunnel sloping down to a ledge in the cavern wall, stairs to the floor
  const entryY = surface(dim, cx + Math.round(dir.x * TRENCH), cz + Math.round(dir.z * TRENCH)) ?? base;
  const ledgeY = shallow ? floorY : floorY + 4;
  for (let t = TRENCH; t >= RX - 1; t -= 0.5) {
    const fy = Math.max(ledgeY, entryY - Math.floor((TRENCH - t) / 2)), inside = t < RX + 0.5;
    for (let sd = -2; sd <= 2; sd += 0.5) {
      const x = cx + Math.floor(dir.x * t + side.x * sd + 0.5), z = cz + Math.floor(dir.z * t + side.z * sd + 0.5);
      put(x, fy - 1, z, "minecraft:cobbled_deepslate");
      for (let y = fy; y <= fy + 4; y++) put(x, y, z, "minecraft:air");
      if (!inside) put(x, fy + 5, z, "minecraft:stone");
    }
    for (const sgn of [-1, 1]) { const x = cx + Math.floor(dir.x * t + side.x * 2.6 * sgn + 0.5), z = cz + Math.floor(dir.z * t + side.z * 2.6 * sgn + 0.5); if (!inside) for (let y = fy - 1; y <= fy + 4; y++) put(x, y, z, "minecraft:stone"); }
    if (!inside && Number.isInteger(t) && t % 4 === 0 && t < MOUTH + 1) {
      for (const sgn of [-1, 1]) { const x = cx + Math.floor(dir.x * t + side.x * 2 * sgn + 0.5), z = cz + Math.floor(dir.z * t + side.z * 2 * sgn + 0.5); for (let y = fy; y <= fy + 3; y++) put(x, y, z, "minecraft:stripped_spruce_log"); }
      for (let sd = -2; sd <= 2; sd += 0.5) put(cx + Math.floor(dir.x * t + side.x * sd + 0.5), fy + 4, cz + Math.floor(dir.z * t + side.z * sd + 0.5), "minecraft:spruce_planks");
      if (t % 8 === 0) put(cx + Math.floor(dir.x * t + 0.5), fy + 3, cz + Math.floor(dir.z * t + 0.5), t < 30 ? "minecraft:soul_lantern" : "minecraft:lantern", { hanging: true });
    }
  }
  for (let k = 0; k <= 4 && ledgeY > floorY; k++) {
    const t = RX - 2 - k, fy = ledgeY - 1 - k;
    for (let sd = -1.5; sd <= 1.5; sd += 0.5) { const x = cx + Math.floor(dir.x * t + side.x * sd + 0.5), z = cz + Math.floor(dir.z * t + side.z * sd + 0.5); for (let y = floorY; y <= fy; y++) put(x, y, z, "minecraft:cobbled_deepslate"); }
  }
  // the warning at the mouth
  const mf = Math.max(ledgeY, entryY - Math.floor((TRENCH - MOUTH) / 2));
  for (let sd = -3; sd <= 3; sd += 0.5) {
    const x = cx + Math.floor(dir.x * (MOUTH + 1) + side.x * sd + 0.5), z = cz + Math.floor(dir.z * (MOUTH + 1) + side.z * sd + 0.5);
    if (Math.abs(sd) >= 2.5) for (let y = mf; y <= mf + 5; y++) put(x, y, z, "minecraft:dark_oak_log"); else put(x, mf + 5, z, "minecraft:dark_oak_log");
  }
  wset("hh:e_center", JSON.stringify({ x: cx + 0.5, y: floorY, z: cz + 0.5, entry: { x: cx + dir.x * (TRENCH - 2), y: entryY, z: cz + dir.z * (TRENCH - 2) } }));
  return true;
}
const center = () => wjson("hh:e_center", null);
let building = false;
function loadSite(s, on) {
  const d = over();
  try { d.runCommand("tickingarea remove hh_mine"); } catch (_) { /* none */ }
  try { d.runCommand("tickingarea remove hh_mine2"); } catch (_) { /* none */ }
  if (!on) return;
  try { d.runCommand(`tickingarea add circle ${s.x} 0 ${s.z} 4 hh_mine`); } catch (_) { oops(_); }
  try { d.runCommand(`tickingarea add circle ${Math.floor(s.x + Math.cos(s.dir) * 40)} 0 ${Math.floor(s.z + Math.sin(s.dir) * 40)} 3 hh_mine2`); } catch (_) { oops(_); }
}
let waitedLoad = 0;
function buildTick() {
  const s = site();
  if (!s || wget("hh:e_built", 0) === 2) return;
  const near = players().some((p) => p.dimension.id === "minecraft:overworld" && distTo(p, s) < 160);
  if (!near && !building) return;
  const d = over();
  if (!planned) {
    if (!building) { building = true; loadSite(s, true); waitedLoad = 0; }
    if (++waitedLoad < 3) return;
    planned = plan(d, s);
    if (!planned) { if (waitedLoad > 60) { loadSite(s, true); waitedLoad = 0; } return; }
  }
  // a few thousand blocks a tick, so nobody notices
  let n = 0;
  while (queue.length && n < 2500) {
    const [x, y, z, id, states] = queue.shift();
    n++;
    try { const b = d.getBlock({ x, y, z }); if (!b) continue; if (states) b.setPermutation(BlockPermutation.resolve(id, states)); else if (b.typeId !== id) b.setType(id); } catch (_) { /* outside the world */ }
  }
  if (!queue.length) { wset("hh:e_built", 2); building = false; loadSite(s, false); }
}

// ---------------------------------------------------------------- the thing that weeps
const LENGTH = { rise: 80, slam_near_l: 48, slam_near_r: 48, slam_far_l: 48, slam_far_r: 48, sweep: 48, scream: 52, weep: 72, grab: 24, hold: 100, throw: 20, stun: 40, defeat: 140 };
const SLAM_NEAR = [4.16, 2.44], SLAM_FAR = [6.53, 2.68], GRAB_AT = [4.88, -0.37], HOLD_AT = [3.9, -1.0, 3.8];
const boss = { e: null, act: null, t: 0, next: 0, phase: 1, yaw: 0, aim: null, swept: new Set(), tears: [], victim: null, prog: 0, holdEnd: 0, stunUntil: 0, cryAt: 0, hand: 0, screamDue: false, jumpWas: false, deadAt: 0 };
const fwd = (yaw) => { const r = yaw * Math.PI / 180; return { x: -Math.sin(r), z: Math.cos(r) }; };
const right = (yaw) => { const f = fwd(yaw); return { x: -f.z, z: f.x }; };
function local(yaw, fw, sd, up) { const l = boss.e.location, f = fwd(yaw), r = right(yaw); return { x: l.x + f.x * fw + r.x * sd, y: l.y + up, z: l.z + f.z * fw + r.z * sd }; }
const yawTo = (from, to) => Math.atan2(-(to.x - from.x), to.z - from.z) * 180 / Math.PI;
function play(name) {
  boss.act = name; boss.t = 0;
  try { boss.e.setProperty("ghost:act", name === "idle" ? 0 : 1); } catch (_) { oops(_); }
  if (name !== "idle") { try { boss.e.playAnimation(`animation.weeping.${name}`, { controller: "hh_boss", stopExpression: "0", blendOutTime: 0.15 }); } catch (_) { oops(_); } }
  else { try { boss.e.playAnimation("animation.ghost.ko_end", { controller: "hh_boss", stopExpression: "0" }); } catch (_) { oops(_); } }
}
function face(yaw) { boss.yaw = yaw; try { boss.e.setRotation({ x: 0, y: yaw }); } catch (_) { oops(_); } }
function bossPlayers(r) { const e = boss.e; return e.dimension.getPlayers({ location: e.location, maxDistance: r }).filter((p) => playing(p)); }
function hurt(p, dmg) { try { p.applyDamage(dmg, { cause: "entityAttack", damagingEntity: boss.e }); } catch (_) { oops(_); } }
function push(p, x, y, z) { try { const len = Math.hypot(x, z) || 1; p.applyKnockback(x / len, z / len, Math.hypot(x, z), y); } catch (_) { oops(_); } }
function ring(at, r) { try { for (let a = 0; a < 16; a++) { const k = a / 16 * Math.PI * 2; boss.e.dimension.spawnParticle("minecraft:redstone_wire_dust_particle", { x: at.x + Math.cos(k) * r, y: at.y + 0.15, z: at.z + Math.sin(k) * r }); } } catch (_) { oops(_); } }
const sound = (id, at, vol, pitch) => { try { boss.e.dimension.playSound(id, at, { volume: vol, pitch }); } catch (_) { oops(_); } };

export function spawnBoss() {
  const c = center();
  if (!c) return null;
  const e = over().spawnEntity("ghost:weeping", { x: c.x, y: c.y - 2.2, z: c.z });
  boss.e = e; boss.phase = 1; boss.next = system.currentTick + 120; boss.victim = null; boss.tears = [];
  face(yawTo(c, c.entry));
  play("rise");
  for (const p of bossPlayers(60)) title(p, "§4" + T("endgame.boss.title"), "§7" + T("endgame.boss.sub"));
  sound("boss.rise", e.location, 5, 1);
  return e;
}
function choose(t) {
  const l = boss.e.location, d = Math.hypot(t.location.x - l.x, t.location.z - l.z), r = Math.random();
  if (boss.screamDue && boss.phase >= 2) { boss.screamDue = false; play("scream"); return; }
  if (d < 3.6) { if (r < 0.45) startGrab(t); else if (r < 0.85 || boss.phase < 2) startSweep(t); else play("scream"); }
  else if (d < 6.0) { if (r < 0.5) startSlam(t, false); else if (r < 0.75) startSweep(t); else startGrab(t); }
  else if (d < 9.2) { if (r < 0.6) startSlam(t, true); else if (r < 0.8) startWeep(); else startSweep(t); }
  else { if (r < 0.65 || boss.phase < 2) startWeep(); else play("scream"); }
}
function startSlam(t, far) {
  const k = far ? SLAM_FAR : SLAM_NEAR;
  boss.hand ^= 1;
  const sd = boss.hand === 0 ? k[1] : -k[1], base = yawTo(boss.e.location, t.location);
  let best = base, bd = 1e9;
  for (let i = -60; i <= 60; i += 2) { const p = local(base + i, k[0], sd, 0), dd = Math.hypot(p.x - t.location.x, p.z - t.location.z); if (dd < bd) { bd = dd; best = base + i; } }
  face(best);
  boss.aim = local(best, k[0], sd, 0);
  play(far ? (boss.hand === 0 ? "slam_far_l" : "slam_far_r") : (boss.hand === 0 ? "slam_near_l" : "slam_near_r"));
  sound("boss.windup", { ...boss.e.location, y: boss.e.location.y + 4 }, 2.5, 0.9);
}
function startSweep(t) { face(yawTo(boss.e.location, t.location)); boss.swept.clear(); play("sweep"); sound("boss.windup", boss.e.location, 2.5, 0.7); }
function startWeep() { play("weep"); sound("boss.cry", boss.e.location, 4, 0.8); }
function startGrab(t) { face(yawTo(boss.e.location, t.location)); play("grab"); sound("boss.windup", boss.e.location, 2.5, 1.2); }
function sweepAngle(t) { const k = [[18, 85], [22, 10], [24.4, -17], [28, -51], [31, -100]]; if (t <= k[0][0]) return k[0][1]; for (let i = 1; i < k.length; i++) if (t <= k[i][0]) { const f = (t - k[i - 1][0]) / (k[i][0] - k[i - 1][0]); return k[i - 1][1] + (k[i][1] - k[i - 1][1]) * f; } return k[k.length - 1][1]; }
function actTick() {
  const a = boss.act, t = boss.t, ph = boss.phase, l = boss.e.location;
  if (a.startsWith("slam")) {
    if (t < 21 && t % 4 === 0) ring(boss.aim, 3);
    if (t === 21) {
      for (const p of bossPlayers(40)) {
        if (Math.hypot(p.location.x - boss.aim.x, p.location.z - boss.aim.z) > 3 || Math.abs(p.location.y - boss.aim.y) > 3) continue;
        hurt(p, ph === 3 ? 13 : ph === 2 ? 11 : 9);
        push(p, p.location.x - boss.aim.x || 0.01, 0.55, p.location.z - boss.aim.z);
      }
      try { boss.e.dimension.spawnParticle("minecraft:huge_explosion_emitter", { ...boss.aim, y: boss.aim.y + 0.4 }); } catch (_) { oops(_); }
      sound("boss.slam", boss.aim, 4, 0.9 + Math.random() * 0.2);
      try { for (const p of bossPlayers(18)) p.runCommand("camerashake add @s 0.9 0.5 positional"); } catch (_) { oops(_); }
      boss.stunUntil = system.currentTick + 16;   // the hand stays down a moment: hit it now
    }
  } else if (a === "sweep") {
    if (t >= 4 && t < 18 && t % 4 === 0) for (let g = -80; g <= 80; g += 16) { const rad = g * Math.PI / 180; for (const r of [3, 6.5]) { const f = fwd(boss.yaw), rt = right(boss.yaw); try { boss.e.dimension.spawnParticle("minecraft:basic_flame_particle", { x: l.x + f.x * Math.cos(rad) * r + rt.x * Math.sin(rad) * r, y: l.y + 0.15, z: l.z + f.z * Math.cos(rad) * r + rt.z * Math.sin(rad) * r }); } catch (_) { oops(_); } } }
    if (t === 18) sound("boss.sweep", l, 3, 1);
    if (t >= 18 && t <= 31) {
      const a0 = sweepAngle(t - 1), a1 = sweepAngle(t), lo = Math.min(a0, a1) - 8, hi = Math.max(a0, a1) + 8;
      const f = fwd(boss.yaw), rt = right(boss.yaw);
      for (const p of bossPlayers(12)) {
        if (boss.swept.has(p.id)) continue;
        const dx = p.location.x - l.x, dz = p.location.z - l.z, fw = dx * f.x + dz * f.z, sd = dx * rt.x + dz * rt.z, r = Math.hypot(fw, sd);
        if (r < 1.2 || r > 7.2 || p.location.y - l.y > 4.4) continue;
        const ang = Math.atan2(sd, fw) * 180 / Math.PI;
        if (ang < lo || ang > hi || !p.isOnGround) continue;   // jumped over it
        boss.swept.add(p.id);
        hurt(p, ph === 3 ? 12 : ph === 2 ? 10 : 8);
        push(p, -rt.x, 0.5, -rt.z);
      }
    }
  } else if (a === "scream" && t === 16) {
    sound("boss.scream", l, 5, 1);
    try { boss.e.dimension.spawnParticle("minecraft:sonic_explosion", { x: l.x, y: l.y + 5.5, z: l.z }); } catch (_) { oops(_); }
    for (const p of bossPlayers(32)) {
      try { p.addEffect("darkness", 120, { amplifier: 0, showParticles: false }); p.addEffect("slowness", 50, { amplifier: 1, showParticles: false }); } catch (_) { oops(_); }
      hurt(p, 3);
      push(p, p.location.x - l.x, 0.4, p.location.z - l.z);
      addSanity(p, -8);
    }
  } else if (a === "weep") {
    const every = ph === 3 ? 3 : ph === 2 ? 4 : 5;
    if (t >= 14 && t <= 60 && t % every === 0) for (const p of bossPlayers(40)) {
      const v = p.getVelocity(), at = Math.random() < 0.33 ? { x: p.location.x + v.x * 12, z: p.location.z + v.z * 12 } : { x: p.location.x + rand(-3.5, 3.5), z: p.location.z + rand(-3.5, 3.5) };
      const rock = ph === 3 && Math.random() < 0.33;
      boss.tears.push({ x: at.x, y: p.location.y, z: at.z, at: system.currentTick + 18, rock });
      try { boss.e.dimension.spawnParticle("minecraft:ink_emitter", { x: at.x, y: p.location.y + 0.1, z: at.z }); boss.e.dimension.spawnParticle("minecraft:falling_dust_dragon_egg_particle", { x: at.x, y: p.location.y + 8, z: at.z }); } catch (_) { oops(_); }
    }
  } else if (a === "grab" && t === 11 && !boss.victim) {
    const g = local(boss.yaw, GRAB_AT[0], GRAB_AT[1], 0);
    let best = null, bd = 2.6;
    for (const p of bossPlayers(12)) { const d = Math.hypot(p.location.x - g.x, p.location.z - g.z); if (d < bd && Math.abs(p.location.y - l.y) < 5) { bd = d; best = p; } }
    if (best) { boss.victim = best; boss.prog = 0.25; boss.holdEnd = system.currentTick + 113; sound("boss.grab", best.location, 3, 1); }
  }
  boss.t++;
  if (a !== "hold" && boss.t >= (LENGTH[a] ?? 40)) endAttack();
}
function endAttack() {
  const was = boss.act;
  if (boss.victim) { play("hold"); return; }
  play("idle");
  boss.next = system.currentTick + (was === "stun" ? 10 : boss.phase === 3 ? 18 : boss.phase === 2 ? 28 : 38) + Math.floor(Math.random() * 20);
}
function holdTick() {
  const p = boss.victim;
  if (!ok(p) || p.getComponent("minecraft:health").currentValue <= 0) { boss.victim = null; return; }
  const h = local(boss.yaw, HOLD_AT[0], HOLD_AT[1], HOLD_AT[2]);
  try { p.teleport(h, { facingLocation: { ...boss.e.location, y: boss.e.location.y + 5 } }); } catch (_) { oops(_); }
  // break free: jump, again and again
  const j = !!p.isJumping;
  if (j && !boss.jumpWas) boss.prog = Math.min(1, boss.prog + 0.07);
  boss.jumpWas = j;
  if (boss.prog < 1) boss.prog -= 0.0095 * (1 + 0.18 * (boss.phase - 1));
  const bars = Math.round(Math.max(0, boss.prog) * 20);
  markBar(p);
  try { p.onScreenDisplay.setActionBar(`§c${L("กดกระโดดรัว ๆ เพื่อดิ้นให้หลุด!", "Mash jump to break free!")} §a${"|".repeat(bars)}§8${"|".repeat(20 - bars)}`); } catch (_) { oops(_); }
  if (boss.prog >= 1) release(p, true);
  else if (boss.prog <= 0 || system.currentTick >= boss.holdEnd) { boss.victim = null; play("throw"); hurt(p, boss.phase === 3 ? 16 : 13); const f = fwd(boss.yaw), rt = right(boss.yaw); push(p, f.x - rt.x, 0.7, f.z - rt.z); }
}
function release(p, freed) {
  boss.victim = null;
  if (freed && ok(p)) {
    const f = fwd(boss.yaw);
    push(p, f.x, 0.3, f.z);
    try { p.addEffect("resistance", 30, { amplifier: 4, showParticles: false }); } catch (_) { oops(_); }
    play("stun");
    boss.stunUntil = system.currentTick + LENGTH.stun;
    sound("boss.hurt", boss.e.location, 3, 0.6);
  }
}
function tearsTick() {
  const now = system.currentTick;
  for (let i = boss.tears.length - 1; i >= 0; i--) {
    const t = boss.tears[i];
    if (now < t.at) continue;
    boss.tears.splice(i, 1);
    for (const p of bossPlayers(48)) {
      if (Math.hypot(p.location.x - t.x, p.location.z - t.z) > (t.rock ? 1.8 : 1.4) || Math.abs(p.location.y - t.y) > 2.5) continue;
      hurt(p, t.rock ? 7 : boss.phase === 3 ? 6 : 5);
      try { p.addEffect("slowness", 40, { amplifier: 0 }); } catch (_) { oops(_); }
    }
    sound(t.rock ? "boss.slam" : "boss.tear", t, t.rock ? 1.2 : 1, t.rock ? 1.5 : 1);
  }
}
function bossTick() {
  const e = boss.e;
  if (!e || !ok(e)) {
    // find it again after a restart
    if (stage() === MAP && wget("hh:e_built", 0) === 2) { try { boss.e = over().getEntities({ type: "ghost:weeping" })[0] ?? null; if (boss.e) play("idle"); } catch (_) { oops(_); } }
    return;
  }
  if (boss.deadAt) return;
  const h = e.getComponent("minecraft:health");
  if (boss.act === "rise") { if (boss.t % 4 === 0) { try { for (const p of bossPlayers(40)) p.runCommand("camerashake add @s 0.6 0.3 positional"); } catch (_) { oops(_); } } boss.t++; if (boss.t >= LENGTH.rise) { play("idle"); boss.next = system.currentTick + 30; } return; }
  const ps = bossPlayers(40);
  if (!ps.length) { if (boss.act && boss.act !== "idle" && boss.act !== "hold") play("idle"); return; }
  const f = h.currentValue / h.effectiveMax, want = f <= 0.33 ? 3 : f <= 0.66 ? 2 : 1;
  if (want > boss.phase) { boss.phase = want; boss.screamDue = true; for (const p of bossPlayers(64)) { markBar(p); p.onScreenDisplay.setActionBar("§c" + T("endgame.boss.phase" + want)); } }
  if (boss.victim) holdTick();
  tearsTick();
  // its music, for everyone in the cavern
  if (system.currentTick >= (boss.musicAt ?? 0)) { boss.musicAt = system.currentTick + 830; for (const p of bossPlayers(48)) playTo(p, "ghost.music.boss", 0.9, 1); }
  if (!boss.act || boss.act === "idle") {
    const now = system.currentTick;
    if (now >= boss.cryAt) { boss.cryAt = now + 70 + Math.floor(Math.random() * 50); sound("boss.cry", { ...e.location, y: e.location.y + 5 }, 3, 0.95 + Math.random() * 0.1); }
    let t = null, bd = 1e9;
    for (const p of ps) { const d = distTo(p, e.location); if (d < bd) { bd = d; t = p; } }
    if (t) { const want = yawTo(e.location, t.location); let dy = ((want - boss.yaw + 540) % 360) - 180; face(boss.yaw + Math.max(-5, Math.min(5, dy))); }
    if (now >= boss.next && t) choose(t);
    return;
  }
  if (boss.act !== "hold") actTick();
}
function bossHurt(ev) {
  const e = ev.hurtEntity;
  if (!boss.e || e.id !== boss.e.id || boss.deadAt) return;
  // the hand that came down: x1.5; a grabbed friend is let go after a hard hit
  if (system.currentTick < boss.stunUntil) { try { e.applyDamage(ev.damage * 0.5); } catch (_) { oops(_); } }
  if (boss.victim && ev.damage >= 6) release(boss.victim, true);
  if (e.getComponent("minecraft:health").currentValue <= 1) defeat();
}
function defeat() {
  if (boss.deadAt) return;
  boss.deadAt = system.currentTick;
  if (boss.victim) release(boss.victim, true);
  const e = boss.e, l = { ...e.location };
  try { e.getComponent("minecraft:health").setCurrentValue(1); } catch (_) { oops(_); }
  play("defeat");
  sound("boss.defeat", l, 5, 1);
  for (const p of players()) { title(p, "§f" + T("endgame.won.title"), "§7" + T("endgame.won.sub"), 120); }
  later(LENGTH.defeat, () => {
    try { e.remove(); } catch (_) { oops(_); }
    setStage(RIFT);
    const r = over().spawnEntity("ghost:rift", { x: l.x, y: l.y + 2.2, z: l.z });
    try { r.dimension.playSound("rift.hum", r.location, { volume: 2, pitch: 1 }); } catch (_) { oops(_); }
    for (const p of players()) say(p, "§f" + T("endgame.rift"));
  });
}

// ---------------------------------------------------------------- the rift and the credits
function riftTick() {
  let r = null;
  try { r = over().getEntities({ type: "ghost:rift" })[0]; } catch (_) { oops(_); }
  if (!r) { const c = center(); if (c && stage() === RIFT) { try { r = over().spawnEntity("ghost:rift", { x: c.x, y: c.y, z: c.z }); } catch (_) { /* not loaded */ } } return; }
  if (system.currentTick % 40 === 0) { try { r.dimension.playSound("rift.hum", r.location, { volume: 1.5, pitch: 1 }); } catch (_) { oops(_); } }
  try { for (let k = 0; k < 3; k++) r.dimension.spawnParticle("minecraft:endrod", { x: r.location.x + rand(-1, 1), y: r.location.y + rand(0, 4), z: r.location.z + rand(-1, 1) }); } catch (_) { oops(_); }
  for (const p of r.dimension.getPlayers({ location: r.location, maxDistance: 2 })) if (!pget(p, "hh:e_credits", false)) credits(p);
}
export function credits(p) {
  pset(p, "hh:e_credits", true);
  try { p.camera.fade({ fadeTime: { fadeInTime: 0.1, holdTime: 3, fadeOutTime: 2 }, fadeColor: { red: 1, green: 1, blue: 1 } }); } catch (_) { oops(_); }
  playTo(p, "ending.boom", 1, 1);
  later(40, () => { try { p.runCommand("stopsound @s ghost.music.boss"); } catch (_) { oops(_); } playTo(p, "ghost.music.credits", 0.9, 1); });
  // home, safe, while the names go by
  later(30, () => { try { const s = p.getSpawnPoint() ?? { ...world.getDefaultSpawnLocation(), dimension: over() }; p.teleport({ x: s.x + 0.5, y: s.y + 0.1, z: s.z + 0.5 }, { dimension: s.dimension ?? over() }); } catch (_) { oops(_); } });
  const lines = [["§4§l" + T("credits.title"), ""], ["§7" + T("credits.epilogue.1"), ""], ["§7" + T("credits.epilogue.2"), ""], ["§7" + T("credits.epilogue.3"), ""], ["§7" + T("credits.epilogue.4"), ""], ["§7" + T("credits.epilogue.5"), ""], ["§6" + T("credits.five"), ""]];
  for (let n = 1; n <= 5; n++) lines.push(["§f" + T(`credits.p${n}.name`), "§7" + T(`credits.p${n}.was`)]);
  lines.push(["§4" + T("credits.boss"), "§7" + T("credits.boss.was")], ["§6" + T("credits.survivors"), "§f" + players().map((x) => x.name).join(", ")], ["§7" + T("credits.made"), "§fporschekrub §7" + T("credits.team")], ["§f§l" + T("credits.end"), "§7" + T("credits.thanks")]);
  lines.forEach(([a, b], i) => later(80 + i * 90, () => { if (ok(p)) title(p, a, b, 70); }));
  later(80 + lines.length * 90 + 40, () => { if (ok(p)) { say(p, "§a" + T("credits.peace")); msg(p, T("endgame.msg.peace"), false); } });
  // the last one through closes the story for good
  later(80 + lines.length * 90, () => { if (players().every((q) => pget(q, "hh:e_credits", false))) finish(); });
}
function finish() {
  setStage(DONE);
  try { for (const r of over().getEntities({ type: "ghost:rift" })) r.remove(); } catch (_) { oops(_); }
  for (const d of ["overworld", "nether", "the_end"]) { try { for (const g of world.getDimension(d).getEntities({ families: ["ghost"] })) g.remove(); } catch (_) { oops(_); } }
}

// ---------------------------------------------------------------- the clock
function tick() {
  if (!CFG.ENABLED) return;
  switch (stage()) {
    case NONE: if (players().some((p) => rescuePhase(p) === HOME)) startCountdown(); break;
    case COUNTDOWN: countdownTick(); break;
    case HUNT: huntTick(); break;
    case MAP: {
      for (const p of players()) giveMap(p);
      // no ghost walks the world any more (one that was far away fades as soon as it is seen again)
      if (system.currentTick - fadedAt > 400) {
        for (const d of ["overworld", "nether", "the_end"]) { try { for (const g of world.getDimension(d).getEntities({ families: ["ghost"] })) { try { g.dimension.spawnParticle("minecraft:soul_particle", g.location); } catch (_) { oops(_); } g.remove(); } } catch (_) { oops(_); } }
      }
      const c = center();
      if (c && wget("hh:e_built", 0) === 2 && !(boss.e && ok(boss.e)) && !over().getEntities({ type: "ghost:weeping" }).length) {
        // someone walked into the cavern: it wakes
        if (players().some((p) => p.dimension.id === "minecraft:overworld" && Math.hypot(p.location.x - c.x, p.location.z - c.z) < RX - 4 && Math.abs(p.location.y - c.y) < 8)) spawnBoss();
      }
      break;
    }
    case RIFT: riftTick(); break;
    default: break;
  }
}

/** for tests and commands */
export const _end = { boss, center, site, queue, stage: () => stage(), setStage, daysLeft, isDown, toMap, startHunt, defeat, finish };
export function endgameCommand(a, p, tell) {
  if (a === "skip") { setStage(COUNTDOWN); wset("hh:e_from", day() - CFG.DAYS); tell("countdown over: the hunt is tonight"); }
  else if (a === "hunt") { startHunt(); tell("the hunt begins"); }
  else if (a === "map") { toMap(); tell("the map"); }
  else if (a === "reset") { setStage(NONE); wset("hh:e_down", 0); wset("hh:e_site", undefined); wset("hh:e_center", undefined); wset("hh:e_built", 0); planned = false; queue.length = 0; for (const q of players()) for (const k of ["hh:e_map", "hh:e_credits", "hh:e_warned"]) pset(q, k, undefined); tell("the last chapter is reset"); }
  else { const names = ["none", "countdown", "hunt", "map", "rift", "done"]; const s = site(); tell(`endgame: ${names[stage()]}` + (stage() === COUNTDOWN ? ` (${daysLeft()} days left)` : "") + (s ? ` · mountain at ${s.x} ${s.z}` : "") + (wget("hh:e_built", 0) === 2 ? " (built)" : "")); }
}

export function initEndgame(a) {
  api = a || {};
  setEnded(stage() >= MAP);
  onHome(() => startCountdown());
  onKnockOut((g) => knockedDown(g));
  setKeepDown((g) => stage() === HUNT && isDown(Number(String(g.typeId).slice(-1))));
  addHudLine(mapLine);
  system.runInterval(() => { try { tick(); } catch (_) { oops(_); } }, 20);
  system.runInterval(() => { try { buildTick(); } catch (_) { oops(_); } }, 1);
  system.runInterval(() => { try { if (stage() === MAP) bossTick(); } catch (_) { oops(_); } }, 1);
  world.afterEvents.entityHurt.subscribe((ev) => { try { bossHurt(ev); } catch (_) { oops(_); } });
}
