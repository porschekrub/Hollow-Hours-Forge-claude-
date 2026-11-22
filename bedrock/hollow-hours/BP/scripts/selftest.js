// Checks the add-on runs on the real game (used by the automatic test on a Bedrock server; harmless otherwise):
//   /scriptevent hollow:test <name> [player]     -> "[test] PASS <name>" or "[test] FAIL <name>: why" in the content log
import { world, system, ItemStack } from "@minecraft/server";
import { players, invOf, mainHand, ok } from "./store.js";
import { storyActive, forceStory, sendAlert, alerted, resetAlert, day } from "./story.js";
import { bloodMoon, forceBlood, startFog, stopFog, fogOf, mods, CFG as ATMOS } from "./atmos.js";
import { getThirst, setThirst, getSanity, setSanity, drink, exhaust, hallucinate, shock } from "./survival.js";
import { _grave } from "./grave.js";
import { offer, inDeal, _betray } from "./betray.js";
import { spray } from "./blood.js";
import { T } from "./texts.js";
import { battery, setBattery, notify, send, openPhone, hasPhone, storyMessages, _phone } from "./phone.js";
import { navigate, navOf, stopNav } from "./nav.js";
import { phase, inHaven, invaded, harshness, havenSpot, rescueCommand, safe, _rescue, IN_HAVEN, HOME, WAITING } from "./rescue.js";
import { pget } from "./store.js";
import { _fun } from "./fun.js";
import { _combat, knockedOut, poiseHit, shoot } from "./combat.js";
import { _end, endgameCommand } from "./endgame.js";
import { getSanity as sanityOf } from "./survival.js";

let api = {};
const log = (s) => console.warn("[test] " + s);
const wait = (t) => new Promise((r) => system.runTimeout(r, t));
function need(c, why) { if (!c) throw new Error(why); }
const count = (e, id) => { const c = invOf(e); let n = 0; for (let i = 0; i < c.size; i++) { const it = c.getItem(i); if (it && it.typeId === id) n += it.amount; } return n; };

const TESTS = {
  async texts() {
    need(T("grave.made", 1, 2, 3, 15).includes("15"), "placeholders: " + T("grave.made", 1, 2, 3, 15));
    need(T("bloodmoon.title").length > 3 && T("betray.offer.3").length > 10, "missing texts");
  },
  async story(p) {
    forceStory(-1); await wait(25);
    need(!storyActive(), "held back but active");
    forceStory(1); await wait(25);
    need(storyActive(), "started but not active");
    resetAlert(p); sendAlert(p);
    need(alerted(p), "alert not remembered");
    need(day() >= 1, "day");
    await wait(60);
  },
  async bloodmoon(p) {
    ATMOS.FOG_CHANCE = 0;   // no fog rolling in by chance while the sky is being checked
    stopFog();
    forceStory(1);
    world.setTimeOfDay(14000); await wait(10);
    forceBlood(1); await wait(45);
    need(bloodMoon(), "no blood moon at night when forced");
    need(Math.abs(mods().gap - 1 / 3) < 0.01 && mods().damage === 3 && mods().maxAlive === 2, "mods " + JSON.stringify(mods()));
    need(fogOf(p).startsWith("ghost:fog_blood"), "sky: " + fogOf(p));
    forceBlood(-1); await wait(45);
    need(!bloodMoon(), "still a blood moon");
    need(fogOf(p) === "", "sky after: " + fogOf(p));
    forceBlood(0); world.setTimeOfDay(1000);
  },
  async fog(p) {
    forceStory(1);
    startFog(600, 0.9); await wait(30);
    need(fogOf(p) === "ghost:fog_thick", "fog: " + fogOf(p));
    stopFog(); await wait(30);
    need(fogOf(p) === "", "fog after: " + fogOf(p));
  },
  async thirst(p) {
    setThirst(p, 10); drink(p, 7);
    need(Math.abs(getThirst(p) - 17) < 0.01, "drink: " + getThirst(p));
    exhaust(p, 40); await wait(45);
    const t = getThirst(p);
    need(t < 12 && t > 8, "after work: " + t);
    setThirst(p, 2); await wait(45);
    need(!!p.getEffect("slowness"), "no slowness when parched");
    setThirst(p, 20);
  },
  async sanity(p) {
    forceStory(1);
    world.setTimeOfDay(18000); await wait(5);
    setSanity(p, 50); await wait(70);
    const s = getSanity(p);
    need(s < 50, "sanity did not drop in the dark: " + s);
    shock(p, 10);
    need(getSanity(p) < s - 9, "shock");
    for (let i = 0; i < 12; i++) hallucinate(p);
    await wait(40);
    setSanity(p, 100); world.setTimeOfDay(1000);
  },
  // phase 1: die with things -> a grave holds them
  async grave1(p) {
    for (const g of _grave.findGraves(p)) g.remove();
    invOf(p).addItem(new ItemStack("minecraft:diamond", 5));
    invOf(p).addItem(new ItemStack("minecraft:iron_sword", 1));
    p.addLevels(7); await wait(45);
    p.kill(); await wait(30);
    const gs = _grave.findGraves(p);
    need(gs.length === 1, "graves: " + gs.length);
    need(count(gs[0], "minecraft:diamond") === 5, "diamonds in the grave: " + count(gs[0], "minecraft:diamond"));
    need(Number(gs[0].getDynamicProperty("hh:xp")) > 50, "xp in the grave: " + gs[0].getDynamicProperty("hh:xp"));
    need(!!_grave.graveOf(p), "player doesn't know its grave");
    need(gs[0].nameTag.includes(p.name), "label: " + gs[0].nameTag);
  },
  // phase 2 (after respawning): it crumbles, the restorer brings it back, opening returns everything
  async grave2(p) {
    await wait(20);
    need(count(p, "ghost:grave_compass") === 1, "no compass after respawn: " + count(p, "ghost:grave_compass"));
    let g = _grave.findGraves(p)[0];
    need(g, "grave gone");
    _grave.crumble(g, "smashed"); await wait(10);
    need(_grave.findGraves(p).length === 0, "grave still there after crumbling");
    need(count(p, "ghost:grave_compass") === 0, "compass kept");
    need(_grave.restore(p), "restorer found nothing");
    await wait(20);
    g = _grave.findGraves(p)[0];
    need(g, "grave did not come back");
    need(count(g, "minecraft:diamond") === 5, "diamonds after restore: " + count(g, "minecraft:diamond"));
    const d = Math.hypot(g.location.x - p.location.x, g.location.z - p.location.z);
    need(d < 3, "restored grave is " + d.toFixed(1) + " away");
    _grave.open(p, g); await wait(5);
    need(count(p, "minecraft:diamond") === 5 && count(p, "minecraft:iron_sword") === 1, "things back: " + count(p, "minecraft:diamond"));
    need(p.level >= 6, "levels back: " + p.level);
    need(_grave.findGraves(p).length === 0, "empty grave stays");
  },
  // sold: the ghost goes after the friend
  async betray1(p) {
    const f = players().find((x) => x.id !== p.id);
    need(f, "needs a second player");
    forceStory(1);
    const g = p.dimension.spawnEntity("ghost:ghost_3", { x: p.location.x + 3, y: p.location.y, z: p.location.z });
    await wait(10);
    need(offer(p, g), "no offer");
    need(inDeal(p), "not in a deal");
    await wait(30);
    _betray.sell(p, f); await wait(40);
    need(!inDeal(p), "deal not over");
    need(ok(p) && p.getComponent("minecraft:health").currentValue > 0, "seller died");
    const d = Math.hypot(g.location.x - f.location.x, g.location.z - f.location.z);
    need(d < 20, "ghost is " + d.toFixed(1) + " from the friend");
    g.remove();
  },
  // refused (time runs out): dead
  async betray2(p) {
    const g = p.dimension.spawnEntity("ghost:ghost_1", { x: p.location.x + 3, y: p.location.y, z: p.location.z });
    await wait(10);
    need(offer(p, g), "no offer");
    await wait(10 * 20 + 40);
    need(!inDeal(p), "deal not over");
    need(p.getComponent("minecraft:health").currentValue <= 0, "refusing did not kill: " + p.getComponent("minecraft:health").currentValue);
    try { g.remove(); } catch (_) {}
  },
  async blood(p) {
    spray(p, null, 6);
    const z = p.dimension.spawnEntity("minecraft:zombie", { x: p.location.x + 2, y: p.location.y, z: p.location.z });
    await wait(5); spray(z, p, 4); await wait(10); z.remove();
  },
  // a real hunt, start to finish, must not throw anything
  async hunt(p) {
    forceStory(1);
    const spot = api.findSpot(p, true, 10, 14);
    need(spot, "no spot");
    const g = p.dimension.spawnEntity("ghost:ghost_2", spot);
    const m = api.ensureMeta(g, system.currentTick);
    m.stalking = false; m.born = system.currentTick - 200;
    await wait(4);
    g.triggerEvent("ghost:ready"); g.triggerEvent("ghost:start_snap"); api.jumpscare(p, false);
    await wait(200);
    need(ok(g), "ghost vanished at once");
    const st = g.getProperty("ghost:state");
    log("hunt: ghost state " + st + ", player health " + p.getComponent("minecraft:health").currentValue.toFixed(1));
    g.remove();
  },
  async phone(p) {
    const f = players().find((x) => x.id !== p.id);
    // (the phone given on joining may be lying in one of the graves of the earlier tests by now)
    need(pget(p, "hh:phone_given", false) === true, "no phone given on first join");
    if (!hasPhone(p)) invOf(p).addItem(new ItemStack("ghost:phone", 1));
    need(hasPhone(p), "no phone");
    need(battery(p) >= 30 && battery(p) <= 100, "battery " + battery(p));
    const n0 = _phone.inbox(p).length;
    notify(p, "Test", "hello", false);
    need(_phone.inbox(p).length === n0 + 1, "inbox");
    forceStory(-1); resetAlert(p);
    send(p, "mom", false, "hi mom"); await wait(130);
    const mom = _phone.chatLog(p).mom;
    need(mom.length >= 3 && mom[mom.length - 1][0] === "them", "mom did not answer: " + JSON.stringify(mom.slice(-2)));
    need(mom[mom.length - 1][2].includes("calm.greet"), "wrong kind of answer: " + mom[mom.length - 1][2]);
    need(_phone.lineText(mom[mom.length - 1]).length > 5, "line text");
    send(p, "p:" + f.id, false, "run");
    send(p, "p:" + f.id, true, "");
    const got = _phone.chatLog(f)["p:" + p.id];
    need(got && got.length === 2 && got[1][1] === "l", "friend did not get it: " + JSON.stringify(got));
    setBattery(p, 20); need(_phone.charge(p) && battery(p) === 100, "charging");
    storyMessages(p, 1); await wait(230);
    need(_phone.chatLog(p).dad.some((l) => l[2] === "chat.dad.story1"), "story message");
    navigate(p, "x", { x: p.location.x + 50, y: p.location.y, z: p.location.z, dim: "overworld" });
    need(navOf(p), "navigation"); stopNav(p);
    openPhone(p).catch(() => {});   // (a simulated player never answers a menu: it only must not throw)
    await wait(5);
    forceStory(0);
  },
  // the helicopter comes, both board, it flies them to the haven
  async alive(p) {
    for (const q of players()) need(q.getComponent("minecraft:health").currentValue > 0, q.name + " is dead");
  },
  async rescue1(p) {
    const f = players().find((x) => x.id !== p.id);
    forceStory(-1);
    for (const g of p.dimension.getEntities({ families: ["ghost"] })) g.remove();
    rescueCommand("reset", null, () => {});
    const home = { x: p.location.x, z: p.location.z };
    rescueCommand("now", null, () => {}); await wait(50);
    let hs = _rescue.liveHelis(_rescue.PICKUP);
    need(hs.length === 1, "helicopters: " + hs.length);
    const h = hs[0];
    need(Math.hypot(h.pad.x - home.x, h.pad.z - home.z) < 40, "pad too far");
    await wait(230);
    need(h.state === _rescue.LANDED, "not landed: state " + h.state);
    need(Math.abs(h.e.location.y - h.pad.y) < 1.5, "not on the ground: " + h.e.location.y + " vs " + h.pad.y);
    const ride = h.e.getComponent("minecraft:rideable");
    need(ride.addRider(p) && ride.addRider(f), "could not board");
    await wait(10);
    need(_rescue.riders(h).length === 2 && safe(p), "riders: " + _rescue.riders(h).length);
    for (let i = 0; i < 60 && phase(p) !== IN_HAVEN; i++) await wait(20);
    need(phase(p) === IN_HAVEN && phase(f) === IN_HAVEN, "not flown to the haven: " + phase(p) + " " + phase(f));
    const spot = havenSpot();
    need(spot, "no haven");
    await wait(40);
    need(Math.hypot(p.location.x - spot.x, p.location.z - spot.z) < 12, "not at the haven: " + JSON.stringify(p.location) + " " + JSON.stringify(spot));
    need(Math.hypot(spot.x - home.x, spot.z - home.z) > 20000, "the haven is too close");
    need(inHaven(p) && !invaded(p) && safe(p), "not safe in the haven");
    log("haven at " + JSON.stringify(spot) + ", player at " + JSON.stringify(p.location) + " hp " + p.getComponent("minecraft:health").currentValue);
    need(p.getComponent("minecraft:health").currentValue > 0, "died on arrival");
  },
  // the haven is found: the helicopter waits, flies them back, the ghosts are crueller
  async rescue2(p) {
    forceBlood(-1);
    const f = players().find((x) => x.id !== p.id);
    need(phase(p) === IN_HAVEN, "run rescue1 first");
    rescueCommand("invade", null, () => {}); await wait(50);
    need(invaded(p) && !safe(p), "not invaded");
    let hs = _rescue.liveHelis(_rescue.EXTRACT);
    need(hs.length >= 1, "no helicopter to get out");
    const h = hs[0];
    for (let i = 0; i < 20 && h.state !== _rescue.LANDED; i++) await wait(20);
    need(h.state === _rescue.LANDED, "extraction did not land");
    const ride = h.e.getComponent("minecraft:rideable");
    need(ride.addRider(p) && ride.addRider(f), "could not board");
    for (let i = 0; i < 30 && phase(p) !== HOME; i++) await wait(20);
    need(phase(p) === HOME && phase(f) === HOME, "not flown home: " + phase(p));
    await wait(40);
    need(Math.hypot(p.location.x, p.location.z) < 200, "not home: " + JSON.stringify(p.location));
    need(harshness(p, false) === 3 && harshness(p, true) === 6 && mods(p).damage === 3, "cruelty " + harshness(p, false) + " " + JSON.stringify(mods(p)));
    need(p.getComponent("minecraft:health").currentValue > 0, "died on the way home");
    for (const g of p.dimension.getEntities({ families: ["ghost"] })) g.remove();
    rescueCommand("reset", null, () => {});
    forceStory(0); forceBlood(0);
  },
  async playground(p) {
    forceStory(-1);
    const base = { x: Math.floor(p.location.x) + 6, y: Math.floor(p.location.y), z: Math.floor(p.location.z) };
    const dim = p.dimension, below = (dx, dz) => dim.getBlock({ x: base.x + dx, y: base.y - 1, z: base.z + dz });
    const made = {};
    let i = 0;
    for (const n of ["swing", "slide", "trampoline", "hoop", "goal", "arcade", "tv"]) {
      invOf(p).setItem(p.selectedSlotIndex, new ItemStack(`ghost:${n}_item`, 1));
      const e = _fun.place(p, below(i * 3, 0), "Up", `ghost:${n}_item`);
      need(e && ok(e), "could not place " + n);
      made[n] = e; i++;
    }
    await wait(10);
    // the TV turns on and calms
    made.tv.setProperty("ghost:on", true);
    // the swing: ride it
    need(made.swing.getComponent("minecraft:rideable").addRider(p), "could not sit on the swing");
    await wait(10);
    made.swing.getComponent("minecraft:rideable").ejectRiders();
    // the slide throws you forward
    _fun.slide(p, made.slide); await wait(10);
    // a ball dropped through the hoop's rim scores
    const h = made.hoop, r = h.getRotation().y * Math.PI / 180, f = { x: -Math.sin(r), z: Math.cos(r) };
    const ball = dim.spawnEntity("ghost:basketball_e", { x: h.location.x + f.x * 0.25, y: h.location.y + 2.6, z: h.location.z + f.z * 0.25 });
    _fun.balls.set(ball.id, { last: p.id });
    const s0 = sanityOf(p);
    setSanity(p, 50);
    await wait(30);
    need(_fun.balls.get(ball.id)?.scoredAt, "no basket: ball at " + JSON.stringify(ball.location) + " hoop " + JSON.stringify(h.location));
    need(sanityOf(p) > 50, "no reward for the basket");
    // kicked balls move
    const b2 = dim.spawnEntity("ghost:soccer_ball_e", { x: p.location.x + 1, y: p.location.y, z: p.location.z });
    await wait(5);
    const before = { ...b2.location };
    _fun.kick(p, b2); await wait(10);
    need(Math.hypot(b2.location.x - before.x, b2.location.z - before.z) > 0.5, "the kick did not move the ball");
    _fun.pickUp(p, b2);
    need(count(p, "ghost:soccer_ball") === 1, "ball not picked up");
    // taking things back gives the item back
    p.isSneaking = true;
    _fun.takeBack(p, made.trampoline); await wait(5);
    need(!ok(made.trampoline), "trampoline still there");
    for (const e of Object.values(made)) { try { e.remove(); } catch (_) {} }
    try { ball.remove(); } catch (_) {}
    p.isSneaking = false;
    setSanity(p, Math.max(s0, 60));
    forceStory(0);
  },
  async combat(p) {
    forceStory(1);
    for (const g of p.dimension.getEntities({ families: ["ghost"] })) g.remove();
    const g = p.dimension.spawnEntity("ghost:ghost_3", { x: p.location.x + 6, y: p.location.y, z: p.location.z });
    await wait(10);
    const max = _combat.maxPoise(g);
    need(max >= 100, "poise " + max);
    // melee: an axe wears it down until it drops
    invOf(p).setItem(p.selectedSlotIndex, new ItemStack("ghost:battle_axe", 1));
    let n = 0;
    while (!knockedOut(g) && n < 30) { poiseHit(g, p, 13 * 1.7, false); n++; }
    need(knockedOut(g), "not knocked out after " + n + " hits");
    need(n >= 4 && n <= 6, "knocked out after " + n + " axe hits");
    await wait(30);
    need(ok(g) && g.getProperty("ghost:state") === 0, "a knocked-out ghost moved on: state " + g.getProperty("ghost:state"));
    const at = { ...g.location };
    await wait(40);
    need(Math.hypot(g.location.x - at.x, g.location.z - at.z) < 0.5, "a knocked-out ghost walked");
    _combat.wake(g); await wait(10);
    need(!knockedOut(g), "did not wake");
    // a gun: loaded, aimed, fired
    invOf(p).setItem(p.selectedSlotIndex, new ItemStack("ghost:sniper", 1));
    invOf(p).addItem(new ItemStack("ghost:sniper_ammo", 10));
    await wait(2);
    need(_combat.reload(p), "no reload");
    await wait(70);
    need(_combat.ammoOf(mainHand(p)) === 5, "ammo after reload: " + _combat.ammoOf(mainHand(p)));
    // it stands still out in the open, nobody else around
    for (const o of players()) if (o.id !== p.id) o.teleport({ x: p.location.x - 20, y: p.location.y, z: p.location.z - 20 });
    g.triggerEvent("ghost:appease");
    const gm = _combat.kos; gm.delete(g.id);
    g.teleport({ x: p.location.x + 8, y: p.location.y, z: p.location.z });
    await wait(5);
    // (a simulated player turns its head through the test driver)
    p.dimension.runCommand(`scriptevent hhtest:look ${p.name} ${g.location.x.toFixed(2)} ${(g.location.y + 1.2).toFixed(2)} ${g.location.z.toFixed(2)}`);
    await wait(10);
    const vd = p.getViewDirection();
    log("aim " + JSON.stringify(vd) + " ghost " + JSON.stringify(g.location) + " me " + JSON.stringify(p.getHeadLocation()));
    const before = _combat.poise.get(g.id);
    const hits = shoot(p);
    need(_combat.ammoOf(mainHand(p)) === 4, "a shot did not use a round");
    need(_combat.lastHits.includes(g.id), "the shot missed the ghost: hit " + JSON.stringify(_combat.lastHits.map((id) => world.getEntity(id)?.typeId)));
    need(_combat.poise.get(g.id) < before, "the hit did not count");
    // a grenade at its feet
    _combat.explode(p.dimension.spawnEntity("ghost:grenade_e", { x: g.location.x, y: g.location.y + 0.3, z: g.location.z }), p);
    await wait(5);
    g.remove(); forceStory(0);
  },
  // the last chapter, start to finish
  async endgame(p) {
    const f = players().find((x) => x.id !== p.id);
    endgameCommand("reset", null, () => {});
    for (const g of p.dimension.getEntities({ families: ["ghost"] })) g.remove();
    world.setTimeOfDay(14000);
    endgameCommand("skip", null, () => {});
    for (let i = 0; i < 10 && _end.stage() !== 2; i++) await wait(20);
    need(_end.stage() === 2, "the hunt did not start: stage " + _end.stage());
    for (let i = 0; i < 25 && p.dimension.getEntities({ families: ["ghost"] }).length < 5; i++) await wait(20);
    const gs = p.dimension.getEntities({ families: ["ghost"] }).filter((g) => String(g.typeId).startsWith("ghost:ghost_"));
    need(gs.length >= 5, "only " + gs.length + " ghosts came");
    for (const g of gs) { let n = 0; while (!knockedOut(g) && n < 40) { poiseHit(g, p, 25, false); n++; } }
    for (let n = 1; n <= 5; n++) need(_end.isDown(n), "ghost " + n + " not down for good");
    for (let i = 0; i < 25 && (count(p, "ghost:old_map") === 0 || p.dimension.getEntities({ families: ["ghost"] }).length); i++) await wait(20);
    need(_end.stage() === 3, "no map stage: " + _end.stage());
    need(count(p, "ghost:old_map") === 1, "no map");
    need(!storyActive(), "the haunting goes on after the five are free");
    await wait(40);
    const left = p.dimension.getEntities({ families: ["ghost"] });
    need(left.length === 0, "the five did not fade: " + left.map((e) => e.typeId).join(", "));
    const s = _end.site();
    need(s && Math.hypot(s.x - p.location.x, s.z - p.location.z) > 400, "mountain site " + JSON.stringify(s));
    // go there: it is built
    p.teleport({ x: s.x + Math.cos(s.dir) * 70, y: 40, z: s.z + Math.sin(s.dir) * 70 });
    p.addEffect("slow_falling", 600, { amplifier: 0 }); p.addEffect("resistance", 6000, { amplifier: 4 });
    f.teleport({ x: s.x + Math.cos(s.dir) * 72, y: 40, z: s.z + Math.sin(s.dir) * 72 });
    for (let i = 0; i < 90 && world.getDynamicProperty("hh:e_built") !== 2; i++) await wait(20);
    need(world.getDynamicProperty("hh:e_built") === 2, "the mountain was not built (" + _end.queue.length + " left)");
    const c = _end.center();
    need(c, "no cavern");
    const top = p.dimension.getTopmostBlock({ x: Math.floor(s.x), z: Math.floor(s.z) });
    need(top && top.location.y > c.y + 15, "no mountain over the cavern: top " + (top && top.location.y) + " floor " + c.y);
    const inside = [];
    for (const dy of [0, 1, 3, 6, 10]) inside.push(p.dimension.getBlock({ x: Math.floor(c.x) + 3, y: c.y + dy, z: Math.floor(c.z) + 3 })?.typeId ?? "?");
    log("cavern column: " + inside.join(", "));
    need(inside.filter((x) => x === "minecraft:air").length >= 4, "the cavern is not hollow: " + inside.join(", "));
    log("mountain at " + JSON.stringify(s) + ", cavern floor " + c.y + ", top " + top.location.y);
    // into the cavern: it wakes
    p.teleport({ x: c.x + 8, y: c.y, z: c.z });
    for (let i = 0; i < 10 && !(_end.boss.e && ok(_end.boss.e)); i++) await wait(20);
    need(_end.boss.e && ok(_end.boss.e), "it did not wake");
    await wait(140);   // it rises, then it fights
    need(_end.boss.act && _end.boss.act !== "rise", "it never fought: " + _end.boss.act);
    log("boss: " + _end.boss.act + " phase " + _end.boss.phase + " hp " + _end.boss.e.getComponent("minecraft:health").currentValue);
    // beaten
    _end.boss.e.applyDamage(320, { cause: "entityAttack", damagingEntity: p }); await wait(20);
    need(_end.boss.phase >= 2, "no second phase: " + _end.boss.phase);
    _end.boss.e.applyDamage(400, { cause: "entityAttack", damagingEntity: p });
    for (let i = 0; i < 12 && _end.stage() !== 4; i++) await wait(20);
    need(_end.stage() === 4, "no rift: stage " + _end.stage());
    await wait(25);
    const r = p.dimension.getEntities({ type: "ghost:rift" })[0];
    need(r, "the rift is not there");
    p.teleport(r.location); f.teleport(r.location); await wait(30);
    need(pget(p, "hh:e_credits", false) === true, "no credits");
    _end.finish(); await wait(5);
    need(_end.stage() === 5 && !storyActive(), "not over");
    endgameCommand("reset", null, () => {});
    forceStory(0);
  },
  async canteen(p) {
    const inv = invOf(p);
    inv.setItem(p.selectedSlotIndex, new ItemStack("ghost:canteen_empty", 1));
    await wait(5);
    need(mainHand(p)?.typeId === "ghost:canteen_empty", "not holding the canteen");
  },
};

export function initSelfTest(a) {
  api = a || {};
  system.afterEvents.scriptEventReceive.subscribe((ev) => {
    if (ev.id !== "hollow:test") return;
    const [name, who] = String(ev.message || "").trim().split(/\s+/);
    const fn = TESTS[name];
    if (!fn) { log("FAIL " + name + ": no such test"); return; }
    const p = players().find((x) => x.name === (who || "Steve")) || players()[0];
    if (!p) { log("FAIL " + name + ": no player"); return; }
    fn(p).then(() => log("PASS " + name)).catch((e) => log("FAIL " + name + ": " + e + " " + String(e && e.stack ? e.stack : "").split("\n").slice(0, 3).join(" | ")));
  });
}
