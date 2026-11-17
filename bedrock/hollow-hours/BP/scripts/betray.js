// "Tell me where your friend is... and I might spare you." (port of the Java mod's betrayal, step 3 of its kill sequence)
// When a ghost has you almost dead and someone else is in the world, a quarter of the time it stops and makes the
// offer: pick a friend to sell, or refuse. You have 10 seconds. Refuse or run out of time = it kills you right away.
// Sell = it lets you go and hunts your friend instead.
import { world, system } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";
import { players, playing, ok, say, playTo, later, markBar, oops } from "./store.js";
import { T } from "./texts.js";

export const CFG = { CHANCE: 0.25, SECONDS: 10, HEALTH: 8, COOLDOWN: 5 * 60 * 20 };
const deals = new Map();     // player.id -> { ghost, until, done }
const lastAt = new Map();
let api = {};

export const inDeal = (p) => deals.has(p.id);
function friendsOf(p) {
  return players().filter((o) => o.id !== p.id && ok(o) && playing(o) && o.dimension.id === p.dimension.id && !(api.isHidden && api.isHidden(o)));
}
function hp(p) { try { return p.getComponent("minecraft:health").currentValue; } catch (_) { return 20; } }

/** Starts the offer (returns false when there is nobody to sell). */
export function offer(p, g) {
  const friends = friendsOf(p);
  if (!friends.length || deals.has(p.id)) return false;
  const now = system.currentTick, n = Number(String(g.typeId).slice(-1)) || 1;
  const deal = { ghost: g, until: now + CFG.SECONDS * 20, done: false };
  deals.set(p.id, deal);
  lastAt.set(p.id, now);
  // the ghost stops and holds you with its eyes
  try { const m = api.ensureMeta(g, now); m.appeaseUntil = now + CFG.SECONDS * 20 + 60; g.triggerEvent("ghost:appease"); } catch (_) { oops(_); }
  try { p.addEffect("slowness", CFG.SECONDS * 20 + 20, { amplifier: 6, showParticles: false }); p.addEffect("resistance", CFG.SECONDS * 20 + 20, { amplifier: 4, showParticles: false }); } catch (_) { oops(_); }
  try { p.runCommand("camera @s fade time 0.2 0.3 0.6 color 20 0 0"); } catch (_) { oops(_); }
  playTo(p, "ghost.betray.whisper", 1.4, 1);
  const line = T("betray.offer." + n);
  say(p, "§4" + line);
  for (let k = 1; k <= CFG.SECONDS; k++) later(k * 20, () => { if (!deal.done && ok(p)) { playTo(p, "ghost.betray.tick", 0.8, 1 + k * 0.04); markBar(p); try { p.onScreenDisplay.setActionBar(`§c${T("betray.hint")} §f${CFG.SECONDS - k}`); } catch (_) { oops(_); } } });
  later(CFG.SECONDS * 20, () => { if (!deal.done) refuse(p, deal, "betray.timeout"); });
  const form = new ActionFormData().title("§4" + T("betray.title")).body("§c" + line + "\n\n§7" + T("betray.hint"));
  for (const f of friends) form.button("§l" + f.name);
  form.button("§c" + T("betray.refuse"));
  const show = (tries) => {
    form.show(p).then((r) => {
      if (deal.done) return;
      if (r.canceled) {
        // a chat window or another menu was open: try again while there is time
        // (or it was closed by mistake: it comes back, there is no walking away from this)
        if (tries < 30 && system.currentTick < deal.until - 15) later(r.cancelationReason === "UserBusy" ? 15 : 6, () => show(tries + 1));
        return;
      }
      const f = friends[r.selection];
      if (!f) refuse(p, deal, "betray.refused." + Math.floor(Math.random() * 3)); else sell(p, deal, f);
    }).catch(() => {});
  };
  show(0);
  return true;
}
function free(p, deal) {
  deal.done = true;
  deals.delete(p.id);
  try { p.removeEffect("slowness"); p.removeEffect("resistance"); } catch (_) { oops(_); }
}
function refuse(p, deal, key) {
  if (deal.done) return;
  free(p, deal);
  if (!ok(p)) return;
  say(p, "§4" + T(key));
  playTo(p, "ghost.betray.laugh", 1.4, 1);
  const g = deal.ghost;
  later(12, () => {
    try { if (ok(g)) { const m = api.ensureMeta(g, system.currentTick); m.appeaseUntil = 0; g.triggerEvent("ghost:resume"); } } catch (_) { oops(_); }
    try { if (ok(g)) p.applyDamage(1000, { cause: "entityAttack", damagingEntity: g }); else p.kill(); } catch (_) { try { p.kill(); } catch (__) {} }
  });
}
function sell(p, deal, f) {
  if (deal.done) return;
  free(p, deal);
  const g = deal.ghost;
  if (!ok(f) || f.dimension.id !== p.dimension.id) {   // the one you named is gone: it takes you after all
    deals.set(p.id, deal); deal.done = false;
    refuse(p, deal, "betray.gone");
    return;
  }
  say(p, "§4" + T("betray.sold"));
  playTo(p, "ghost.betray.sold", 1.4, 1);
  try { p.addEffect("speed", 200, { amplifier: 1, showParticles: false }); } catch (_) { oops(_); }
  try {
    f.onScreenDisplay.setTitle(T("betray.victim.title"), { subtitle: T("betray.victim.sub", p.name), fadeInDuration: 5, stayDuration: 70, fadeOutDuration: 20 });
    playTo(f, "ghost.betray.laugh", 1.4, 0.9);
  } catch (_) { oops(_); }
  // the ghost leaves you and comes out of the dark behind your friend, already running
  later(20, () => {
    try {
      if (!ok(g) || !ok(f)) return;
      const now = system.currentTick, m = api.ensureMeta(g, now);
      const spot = api.findSpot(f, true, 9, 14);
      if (spot) g.teleport(spot, { facingLocation: f.location });
      m.appeaseUntil = 0; m.stalking = false; m.chaseStart = 0; m.lostAt = 0; m.born = now - 200;
      g.triggerEvent("ghost:resume");
      if (api.jumpscare) api.jumpscare(f, true);
    } catch (_) { oops(_); }
  });
}

/** for tests */
export const _betray = { sell: (p, f) => { const d = deals.get(p.id); if (d) sell(p, d, f); }, deals };

export function initBetray(a) {
  api = a || {};
  try {
    world.afterEvents.entityHurt.subscribe((ev) => {
      try {
        const p = ev.hurtEntity, g = ev.damageSource && ev.damageSource.damagingEntity;
        if (!p || p.typeId !== "minecraft:player" || !g || !String(g.typeId).startsWith("ghost:ghost_")) return;
        if (deals.has(p.id) || !playing(p)) return;
        const h = hp(p);
        if (h <= 0 || h > CFG.HEALTH) return;
        if (system.currentTick - (lastAt.get(p.id) ?? -1e9) < CFG.COOLDOWN) return;
        if (Math.random() >= CFG.CHANCE) return;
        offer(p, g);
      } catch (_) { oops(_); }
    });
    world.afterEvents.entityDie.subscribe((ev) => { try { const d = deals.get(ev.deadEntity.id); if (d) { d.done = true; deals.delete(ev.deadEntity.id); } } catch (_) { oops(_); } });
    world.afterEvents.playerLeave.subscribe((ev) => { const d = deals.get(ev.playerId); if (d) { d.done = true; deals.delete(ev.playerId); } });
  } catch (_) { oops(_); }
}
