// Checks the add-on runs on the real game (used by the automatic test on a Bedrock server; harmless otherwise):
//   /scriptevent helper:test <name>     -> "[test] PASS <name>" or "[test] FAIL <name>: why" in the content log
import { world, system } from "@minecraft/server";
import { parseTalk } from "./talk.js";
import { cmdAttack, cmdSpeed } from "./attack.js";
import { speedLevel, getOwnerId, workMult } from "./state.js";

const log = (s) => console.warn("[test] " + s);
const wait = (t) => new Promise((r) => system.runTimeout(r, t));
function need(c, why) { if (!c) throw new Error(why); }
const helperOf = (p) => p.dimension.getEntities({ type: "helper:helper", location: p.location, maxDistance: 32 }).find((h) => h.getDynamicProperty("h:owner") === p.id);

const TESTS = {
  async words() {
    const cases = [
      ["ตีมัน", (r) => r.k === "attack" && !r.mob], ["ไปล่ามัน", (r) => r.k === "attack" && !r.mob], ["kill it", (r) => r.k === "attack"],
      ["ตีไอร่อนโกเลม", (r) => r.k === "attack" && r.mob && r.mob[0] === "iron_golem"], ["ฆ่าชาวบ้าน 2 ตัว", (r) => r.k === "attack" && r.mob[0] === "villager" && r.n === 2],
      ["kill 5 zombies", (r) => r.k === "attack" && r.mob[0] === "zombie" && r.n === 5], ["attack that", (r) => r.k === "attack"],
      ["เร็วๆ", (r) => r.k === "speed" && r.sp.step === 1], ["เร็วสุดๆ", (r) => r.k === "speed" && r.sp.to === 3], ["ช้าลง", (r) => r.k === "speed" && r.sp.step === -1],
      ["work faster", (r) => r.k === "speed"], ["as fast as you can", (r) => r.k === "speed" && r.sp.to === 3],
      ["ไม่ต้องสู้", (r) => r.k === "fight" && r.on === false], ["หยุด", (r) => r.k === "stay"], ["ตามมา", (r) => r.k === "follow"],
    ];
    const bad = [];
    for (const [s, ok] of cases) { const r = parseTalk(s); if (!ok(r)) bad.push(`${s} -> ${JSON.stringify(r).slice(0, 80)}`); }
    need(!bad.length, bad.join(" | "));
  },
  async helper(p) {
    let h = helperOf(p);
    if (!h) { h = p.dimension.spawnEntity("helper:helper", { x: p.location.x + 2, y: p.location.y, z: p.location.z }); await wait(40); }
    need(h && h.isValid, "no helper");
    need(getOwnerId(h) === p.id, "the helper is not Steve's");
    cmdSpeed(p, h, { to: 3 });
    need(speedLevel(h) === 3 && workMult(h) === 10, "speed " + speedLevel(h));
    cmdSpeed(p, h, { step: -1 });
    need(speedLevel(h) === 2, "speed down " + speedLevel(h));
    cmdSpeed(p, h, { to: 0 });
  },
  // told to: it goes for an iron golem (it never would on its own)
  async golem(p) {
    const h = helperOf(p);
    need(h, "run 'helper' first");
    const g = p.dimension.spawnEntity("minecraft:iron_golem", { x: p.location.x + 5, y: p.location.y, z: p.location.z + 1 });
    await wait(10);
    const hp0 = g.getComponent("minecraft:health").currentValue;
    need(cmdAttack(p, h, { mob: ["iron_golem", "ไอร่อนโกเลม", "iron golems?"], n: 1 }), "no attack");
    for (let i = 0; i < 20 && g.isValid && g.getComponent("minecraft:health").currentValue >= hp0; i++) await wait(10);
    need(!g.isValid || g.getComponent("minecraft:health").currentValue < hp0, "the golem was never hit");
    try { g.remove(); } catch (_) { /* gone */ }
  },
};

system.afterEvents.scriptEventReceive.subscribe((ev) => {
  if (ev.id !== "helper:test") return;
  const name = String(ev.message || "").trim();
  const fn = TESTS[name];
  if (!fn) { log("FAIL " + name + ": no such test"); return; }
  const p = world.getAllPlayers().find((x) => x.name === "Steve") || world.getAllPlayers()[0];
  if (!p) { log("FAIL " + name + ": no player"); return; }
  fn(p).then(() => log("PASS " + name)).catch((e) => log("FAIL " + name + ": " + e + " " + String(e && e.stack ? e.stack : "").split("\n").slice(0, 3).join(" | ")));
});
