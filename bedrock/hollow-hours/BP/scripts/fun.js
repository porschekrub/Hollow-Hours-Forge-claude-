// The playground and the living room, to keep your mind together (port of the Java mod's FunBlocks / BallEntity).
// Bedrock has no blocks of this shape, so each one is a thing you place with its item (and take back by hitting it
// while sneaking): swing (sit and swing), slide, trampoline (bounce higher and higher), basketball hoop and soccer goal
// (score with a ball), arcade machine (Ghost Tap without a battery) and a TV (watching it calms you down).
import { world, system, ItemStack } from "@minecraft/server";
import { players, playing, ok, say, playTo, later, mainHand, invOf, markBar, oops } from "./store.js";
import { addSanity, addCozy, exhaust } from "./survival.js";
import { playTap } from "./phone.js";
import { T, L } from "./texts.js";

const PLACE = { "ghost:swing_item": "ghost:swing", "ghost:slide_item": "ghost:slide", "ghost:trampoline_item": "ghost:trampoline", "ghost:hoop_item": "ghost:hoop",
  "ghost:goal_item": "ghost:goal", "ghost:arcade_item": "ghost:arcade", "ghost:tv_item": "ghost:tv" };
const ITEM_OF = Object.fromEntries(Object.entries(PLACE).map(([k, v]) => [v, k]));
const BALLS = { "ghost:basketball": "ghost:basketball_e", "ghost:soccer_ball": "ghost:soccer_ball_e" };
const BALL_ITEM = Object.fromEntries(Object.entries(BALLS).map(([k, v]) => [v, k]));
const rand = (a, b) => a + Math.random() * (b - a);
const dims = () => ["overworld", "nether", "the_end"].map((d) => { try { return world.getDimension(d); } catch (_) { return null; } }).filter(Boolean);
/** the way a thing faces (its front, toward whoever placed it) */
function front(e) { const r = e.getRotation().y * Math.PI / 180; return { x: -Math.sin(r), z: Math.cos(r) }; }

// ---------------------------------------------------------------- sanity for playing (once per cooldown per kind)
const lastReward = new Map();
export function reward(p, kind, amount, cooldown) {
  const k = p.id + kind, now = system.currentTick;
  if (now - (lastReward.get(k) ?? -1e9) < cooldown) return;
  lastReward.set(k, now);
  addSanity(p, amount);
}

// ---------------------------------------------------------------- placing and taking back
const lastPlace = new Map();
function place(p, block, face, itemId) {
  const off = { Up: [0, 1, 0], Down: [0, -1, 0], North: [0, 0, -1], South: [0, 0, 1], East: [1, 0, 0], West: [-1, 0, 0] }[String(face)] || [0, 1, 0];
  const b = block.location, x = b.x + off[0], y = b.y + off[1], z = b.z + off[2], dim = block.dimension;
  const at = { x: x + 0.5, y, z: z + 0.5 };
  try { const here = dim.getBlock({ x, y, z }), up = dim.getBlock({ x, y: y + 1, z }); if (!here || !here.isAir || !up || !up.isAir) { markBar(p); p.onScreenDisplay.setActionBar("§7" + L("วางตรงนี้ไม่ได้ (ต้องมีที่ว่างสูง 2 ช่อง)", "No room here (it needs 2 blocks of space)")); return; } } catch (_) { return; }
  try { if (dim.getEntities({ families: ["ghost_fun"], location: at, maxDistance: 0.9 }).length) return; } catch (_) { oops(_); }
  const type = PLACE[itemId];
  const e = dim.spawnEntity(type, at);
  const yaw = Math.round((Math.atan2(-(p.location.x - at.x), p.location.z - at.z) * 180 / Math.PI) / 90) * 90;
  e.teleport(at, { rotation: { x: 0, y: yaw } });
  try { dim.playSound("dig.wood", at, { volume: 1, pitch: 1.1 }); } catch (_) { oops(_); }
  let creative = false;
  try { creative = String(p.getGameMode()).toLowerCase() === "creative"; } catch (_) { oops(_); }
  if (!creative) {
    const h = mainHand(p);
    if (h && h.typeId === itemId) { const slot = invOf(p).getSlot(p.selectedSlotIndex); if (h.amount > 1) { h.amount--; slot.setItem(h); } else slot.setItem(undefined); }
  }
  return e;
}
function takeBack(p, e) {
  const dim = e.dimension, l = { ...e.location }, item = ITEM_OF[e.typeId];
  try { const r = e.getComponent("minecraft:rideable"); if (r && r.getRiders().length) return; } catch (_) { oops(_); }
  try { e.remove(); } catch (_) { return; }
  try { dim.playSound("dig.wood", l, { volume: 1, pitch: 0.8 }); } catch (_) { oops(_); }
  let creative = false;
  try { creative = String(p.getGameMode()).toLowerCase() === "creative"; } catch (_) { oops(_); }
  if (!creative && item) { try { dim.spawnItem(new ItemStack(item, 1), { x: l.x, y: l.y + 0.5, z: l.z }); } catch (_) { oops(_); } }
}

// ---------------------------------------------------------------- the slide: climb on at the back, slide down the front
function slide(p, e) {
  const f = front(e), l = e.location;
  p.teleport({ x: l.x - f.x * 0.35, y: l.y + 1.1, z: l.z - f.z * 0.35 }, { facingLocation: { x: l.x + f.x * 4, y: l.y, z: l.z + f.z * 4 } });
  later(1, () => { try { p.applyKnockback(f.x, f.z, 1.1, 0.15); } catch (_) { oops(_); } });
  try { p.dimension.playSound("game.player.attack.strong", l, { volume: 0.8, pitch: 1.6 }); } catch (_) { oops(_); }
  reward(p, "slide", 5, 60);
}

// ---------------------------------------------------------------- the trampoline: a little higher every bounce
const bounces = new Map();
function trampolineTick() {
  for (const p of players()) {
    try {
      if (!playing(p)) continue;
      const v = p.getVelocity();
      if (v.y > -0.25) continue;
      const t = p.dimension.getEntities({ type: "ghost:trampoline", location: p.location, maxDistance: 1.6 })[0];
      if (!t) continue;
      const dy = p.location.y - t.location.y;
      if (Math.abs(p.location.x - t.location.x) > 0.6 || Math.abs(p.location.z - t.location.z) > 0.6 || dy < 0.2 || dy > 1.4) continue;
      const now = system.currentTick, b = bounces.get(p.id);
      const n = b && now - b.at < 60 ? Math.min(b.n + 1, 6) : 0;
      bounces.set(p.id, { n, at: now });
      if (p.isSneaking) continue;   // sneak to land and stop
      p.applyKnockback(0, 0, 0, 0.55 + n * 0.12);
      p.addEffect("slow_falling", 1, { amplifier: 0, showParticles: false });   // (resets the fall: no damage)
      try { p.dimension.playSound("mob.slime.big", p.location, { volume: 0.6, pitch: 1.3 }); } catch (_) { oops(_); }
      reward(p, "bounce", 1.5, 10);
      exhaust(p, 0.05);
    } catch (_) { oops(_); }
  }
}

// ---------------------------------------------------------------- balls: throw (use the item), kick (hit it), pick up (use it)
const balls = new Map();   // id -> { last: player id, vy, ground }
function throwBall(p, itemId) {
  const v = p.getViewDirection(), h = p.getHeadLocation();
  const e = p.dimension.spawnEntity(BALLS[itemId], { x: h.x + v.x * 0.8, y: h.y - 0.2 + v.y * 0.8, z: h.z + v.z * 0.8 });
  const power = itemId === "ghost:soccer_ball" ? 0.9 : 1.05;
  e.applyImpulse({ x: v.x * power, y: v.y * power + 0.25, z: v.z * power });
  balls.set(e.id, { last: p.id });
  const slot = invOf(p).getSlot(p.selectedSlotIndex), it = slot.getItem();
  if (it && it.typeId === itemId) { if (it.amount > 1) { it.amount--; slot.setItem(it); } else slot.setItem(undefined); }
}
function kick(p, e) {
  const v = p.getViewDirection(), soccer = e.typeId === "ghost:soccer_ball_e";
  const power = (soccer ? 1.0 : 0.7) * (p.isSprinting ? 1.35 : 1);
  e.clearVelocity();
  e.applyImpulse({ x: v.x * power, y: Math.max(0.12, v.y * power + 0.15), z: v.z * power });
  try { e.dimension.playSound(soccer ? "dig.cloth" : "hit.basalt", e.location, { volume: 1, pitch: soccer ? 0.8 : 1.5 }); } catch (_) { oops(_); }
  const b = balls.get(e.id) || {};
  b.last = p.id; balls.set(e.id, b);
  exhaust(p, 0.05);
}
function pickUp(p, e) {
  const item = BALL_ITEM[e.typeId];
  try { e.remove(); } catch (_) { return; }
  const left = invOf(p).addItem(new ItemStack(item, 1));
  if (left) p.dimension.spawnItem(left, p.location);
}
function score(e, basket) {
  const l = e.location, dim = e.dimension;
  try {
    for (let k = 0; k < 6; k++) dim.spawnParticle("minecraft:villager_happy", { x: l.x + rand(-0.6, 0.6), y: l.y + rand(0, 1), z: l.z + rand(-0.6, 0.6) });
    dim.spawnParticle("minecraft:totem_particle", { x: l.x, y: l.y + 0.5, z: l.z });
    dim.playSound("random.levelup", l, { volume: 0.8, pitch: basket ? 1.4 : 1.1 });
  } catch (_) { oops(_); }
  const by = players().find((x) => x.id === balls.get(e.id)?.last);
  if (by) {
    reward(by, basket ? "basket" : "goal", 6, 20);
    try { by.onScreenDisplay.setTitle("§e§l" + T(basket ? "fun.basket" : "fun.goal"), { fadeInDuration: 2, stayDuration: 25, fadeOutDuration: 8 }); } catch (_) { oops(_); }
  }
}
function ballTick() {
  for (const dim of dims()) {
    let list = [];
    try { list = dim.getEntities({ families: ["ghost_ball"] }); } catch (_) { continue; }
    for (const e of list) {
      try {
        const v = e.getVelocity(), b = balls.get(e.id) || {};
        balls.set(e.id, b);
        const soccer = e.typeId === "ghost:soccer_ball_e";
        // bounce: it was falling fast and now it stopped
        if ((b.vy ?? 0) < -0.2 && Math.abs(v.y) < 0.05) {
          const up = -b.vy * (soccer ? 0.55 : 0.78);
          if (up > 0.08) {
            e.applyImpulse({ x: 0, y: up, z: 0 });
            try { dim.playSound(soccer ? "dig.cloth" : "hit.basalt", e.location, { volume: Math.min(1, up * 2), pitch: soccer ? 0.8 : 1.5 }); } catch (_) { oops(_); }
          }
        }
        // dribble: a player walking into it pushes it along
        if (Math.abs(v.x) + Math.abs(v.z) < 0.05) {
          for (const p of dim.getPlayers({ location: e.location, maxDistance: 0.8 })) {
            const d = { x: e.location.x - p.location.x, z: e.location.z - p.location.z }, len = Math.hypot(d.x, d.z) || 1;
            e.applyImpulse({ x: d.x / len * 0.25, y: 0.02, z: d.z / len * 0.25 });
            b.last = p.id;
          }
        }
        // through a hoop (falling down through the rim) or into a goal
        const now = system.currentTick;
        if (now - (b.scoredAt ?? -100) > 40) {
          for (const h of dim.getEntities({ type: "ghost:hoop", location: e.location, maxDistance: 3 })) {
            const f = front(h), rim = { x: h.location.x + f.x * 0.25, y: h.location.y + 1.5, z: h.location.z + f.z * 0.25 };
            const py = (b.y ?? e.location.y) + 0.25, cy = e.location.y + 0.25;
            if (py >= rim.y && cy < rim.y && Math.hypot(e.location.x - rim.x, e.location.z - rim.z) < 0.35) { b.scoredAt = now; score(e, true); }
          }
          for (const g of dim.getEntities({ type: "ghost:goal", location: e.location, maxDistance: 2 })) {
            const f = front(g), dx = e.location.x - g.location.x, dz = e.location.z - g.location.z;
            // inside the frame, coming in from the open front
            if (Math.abs(dx) < 0.48 && Math.abs(dz) < 0.48 && e.location.y - g.location.y < 0.8 && (v.x * -f.x + v.z * -f.z) > 0.02) { b.scoredAt = now; score(e, false); }
          }
        }
        b.vy = v.y; b.y = e.location.y;
      } catch (_) { oops(_); }
    }
  }
  if (balls.size > 200) for (const id of [...balls.keys()]) { if (!world.getEntity(id)) balls.delete(id); }
}

// ---------------------------------------------------------------- the swing and the TV keep you calm
function cozy(p) {
  let c = 0;
  try { const r = p.getComponent("minecraft:riding"); if (r && r.entityRidingOn && r.entityRidingOn.typeId === "ghost:swing") c = 0.3; } catch (_) { oops(_); }
  try { for (const t of p.dimension.getEntities({ type: "ghost:tv", location: p.location, maxDistance: 8 })) if (t.getProperty("ghost:on")) c = Math.max(c, 0.2); } catch (_) { oops(_); }
  return c;
}

/** for tests */
export const _fun = { place, takeBack, slide, kick, throwBall, pickUp, score, balls, reward };

export function initFun() {
  addCozy(cozy);
  system.runInterval(() => { try { trampolineTick(); } catch (_) { oops(_); } }, 1);
  system.runInterval(() => { try { ballTick(); } catch (_) { oops(_); } }, 1);
  // the swing: a little sanity every few seconds you swing
  system.runInterval(() => { for (const p of players()) { try { const r = p.getComponent("minecraft:riding"); if (r && r.entityRidingOn && r.entityRidingOn.typeId === "ghost:swing") reward(p, "swing", 1, 100); } catch (_) { oops(_); } } }, 20);
  world.beforeEvents.playerInteractWithBlock.subscribe((ev) => {
    try {
      const it = ev.itemStack;
      if (!it || !PLACE[it.typeId]) return;
      ev.cancel = true;
      const p = ev.player, t = system.currentTick;
      if (t - (lastPlace.get(p.id) ?? -99) < 8) return;
      lastPlace.set(p.id, t);
      const block = ev.block, face = ev.blockFace, id = it.typeId;
      system.run(() => { try { place(p, block, face, id); } catch (_) { oops(_); } });
    } catch (_) { oops(_); }
  });
  world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
    try {
      const e = ev.target, p = ev.player, id = e.typeId;
      if (id === "ghost:slide") { ev.cancel = true; system.run(() => { try { slide(p, e); } catch (_) { oops(_); } }); }
      else if (id === "ghost:arcade") { ev.cancel = true; system.run(() => { try { playTap(p, true); } catch (_) { oops(_); } }); }
      else if (id === "ghost:tv") {
        ev.cancel = true;
        system.run(() => { try { const on = !e.getProperty("ghost:on"); e.setProperty("ghost:on", on); e.dimension.playSound("random.click", e.location, { volume: 0.6, pitch: on ? 1.4 : 0.9 }); } catch (_) { oops(_); } });
      } else if (BALL_ITEM[id]) { ev.cancel = true; system.run(() => { try { pickUp(p, e); } catch (_) { oops(_); } }); }
    } catch (_) { oops(_); }
  });
  world.afterEvents.itemUse.subscribe((ev) => {
    try { const it = ev.itemStack; if (it && BALLS[it.typeId]) throwBall(ev.source, it.typeId); } catch (_) { oops(_); }
  });
  world.afterEvents.entityHitEntity.subscribe((ev) => {
    try {
      const p = ev.damagingEntity, e = ev.hitEntity;
      if (!p || p.typeId !== "minecraft:player" || !e) return;
      if (BALL_ITEM[e.typeId]) kick(p, e);
      else if (ITEM_OF[e.typeId] && p.isSneaking) takeBack(p, e);
      else if (ITEM_OF[e.typeId]) { markBar(p); p.onScreenDisplay.setActionBar("§7" + L("ย่อแล้วตีเพื่อเก็บคืน", "Sneak and hit it to take it back")); }
    } catch (_) { oops(_); }
  });
}
