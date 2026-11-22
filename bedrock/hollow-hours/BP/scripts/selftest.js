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
