// CI only: simulated players the add-ons under test see as real players.
//   scriptevent hhtest:spawn <name> [x y z]      scriptevent hhtest:chat <name> <text>
//   scriptevent hhtest:leave <name>              scriptevent hhtest:look <name> <x> <y> <z>
//   scriptevent hhtest:use <name>                scriptevent hhtest:move <name> <x> <y> <z>
//   scriptevent hhtest:respawn|jump|where <name>  scriptevent hhtest:break <name> <x> <y> <z>
import { world, system, GameMode } from "@minecraft/server";
import { spawnSimulatedPlayer } from "@minecraft/server-gametest";

const bots = new Map();
const log = (s) => console.warn("[hhtest] " + s);

system.afterEvents.scriptEventReceive.subscribe((ev) => {
  try {
    if (!ev.id.startsWith("hhtest:")) return;
    const op = ev.id.slice(7), a = (ev.message || "").trim().split(/\s+/), name = a[0] || "Steve";
    const num = (i, d) => (a[i] !== undefined && !isNaN(+a[i]) ? +a[i] : d);
    if (op === "spawn") {
      const dim = world.getDimension("overworld");
      const p = spawnSimulatedPlayer({ dimension: dim, x: num(1, 0.5), y: num(2, -60), z: num(3, 0.5) }, name, GameMode.Survival);
      bots.set(name, p);
      log("spawned " + name + " id=" + p.id + " at " + JSON.stringify(p.location));
      return;
    }
    const p = bots.get(name);
    if (!p) return log("no bot " + name);
    if (op === "chat") p.chat(a.slice(1).join(" "));
    else if (op === "leave") { p.disconnect(); bots.delete(name); }
    else if (op === "look") p.lookAtLocation({ x: num(1, 0), y: num(2, 0), z: num(3, 0) });
    else if (op === "move") p.navigateToLocation({ x: num(1, 0), y: num(2, 0), z: num(3, 0) });
    else if (op === "use") p.useItemInSlot(num(1, 0));
    else if (op === "jump") p.jump();
    else if (op === "respawn") p.respawn();
    else if (op === "break") p.breakBlock({ x: num(1, 0), y: num(2, 0), z: num(3, 0) });
    else if (op === "stopuse") p.stopUsingItem();
    else if (op === "tp") p.teleport({ x: num(1, 0), y: num(2, 0), z: num(3, 0) });
    else if (op === "where") log(name + " at " + JSON.stringify(p.location) + " hp=" + p.getComponent("health")?.currentValue);
    log(op + " ok");
  } catch (e) { console.error("[hhtest] " + e + " " + (e && e.stack)); }
});
system.run(() => log("driver ready"));
