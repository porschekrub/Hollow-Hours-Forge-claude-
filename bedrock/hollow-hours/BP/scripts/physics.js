// Physics that make the world feel heavy (port of the Java mod's Physics, as far as Bedrock allows):
// - chop one log of a tree and the whole tree comes down, log after log from the bottom up (sneak to break just one);
// - mine one ore and every touching ore of the same kind cracks out of the rock, one after another (sneak for one).
import { world, system } from "@minecraft/server";
import { playing, oops } from "./store.js";
import { exhaust } from "./survival.js";

export const CFG = { TREES: true, ORES: true, MAX_LOGS: 96, MAX_ORES: 32 };
const isLog = (id) => /_log$|_stem$|^minecraft:log2?$/.test(id) && !id.includes("stripped");
const isOre = (id) => /_ore$/.test(id) || id === "minecraft:ancient_debris";
const k = (x, y, z) => `${x},${y},${z}`;

function connected(dim, start, id, max, diag, upOnly) {
  const seen = new Set([k(start.x, start.y, start.z)]), out = [], q = [start];
  while (q.length && out.length < max) {
    const c = q.shift();
    for (let dx = -1; dx <= 1; dx++) for (let dy = upOnly ? 0 : -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
      if (!dx && !dy && !dz) continue;
      if (!diag && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) !== 1) continue;
      const x = c.x + dx, y = c.y + dy, z = c.z + dz, kk = k(x, y, z);
      if (seen.has(kk)) continue;
      seen.add(kk);
      let b;
      try { b = dim.getBlock({ x, y, z }); } catch (_) { continue; }
      if (!b || b.typeId !== id) continue;
      const p = { x, y, z };
      out.push(p); q.push(p);
      if (out.length >= max) break;
    }
  }
  return out;
}
/** A real tree has leaves that grew there (not placed by a player) touching its logs. */
function hasNaturalLeaves(dim, logs) {
  for (const l of logs.slice(-12)) {
    for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, 1, 0]]) {
      try {
        const b = dim.getBlock({ x: l.x + dx, y: l.y + dy, z: l.z + dz });
        if (!b || !b.typeId.includes("leaves")) continue;
        let placed = false;
        try { placed = b.permutation.getState("persistent_bit") === true; } catch (_) { oops(_); }
        if (!placed) return true;
      } catch (_) { oops(_); }
    }
  }
  return false;
}
function breakAt(dim, p) {
  try { dim.runCommand(`setblock ${p.x} ${p.y} ${p.z} air destroy`); return true; } catch (_) { return false; }
}

export function initPhysics() {
  try {
    world.afterEvents.playerBreakBlock.subscribe((ev) => {
      try {
        const p = ev.player, id = ev.brokenBlockPermutation.type.id, at = ev.block.location, dim = ev.block.dimension;
        if (!p || p.isSneaking || !playing(p)) return;
        if (CFG.TREES && isLog(id)) {
          const logs = connected(dim, at, id, CFG.MAX_LOGS, true, true);
          if (!logs.length || !hasNaturalLeaves(dim, logs)) return;
          logs.sort((a, b) => a.y - b.y);
          try { dim.playSound("ghost.creak", at, { volume: 1.2, pitch: 0.7 }); } catch (_) { oops(_); }
          logs.forEach((l, i) => system.runTimeout(() => { if (breakAt(dim, l) && i % 3 === 0) { try { dim.playSound("dig.wood", l, { volume: 1, pitch: 0.8 }); } catch (_) { oops(_); } } }, 2 + i));
          exhaust(p, 0.005 * logs.length);
        } else if (CFG.ORES && isOre(id)) {
          const tool = ev.itemStackBeforeBreak;
          let silk = false;
          try { silk = !!tool?.getComponent("minecraft:enchantable")?.hasEnchantment("silk_touch"); } catch (_) { oops(_); }
          if (silk) return;
          const ores = connected(dim, at, id, CFG.MAX_ORES, true, false);
          ores.forEach((o, i) => system.runTimeout(() => {
            if (!breakAt(dim, o)) return;
            try { dim.playSound("dig.stone", o, { volume: 1, pitch: 1.1 + i * 0.03 }); dim.spawnParticle("minecraft:critical_hit_emitter", { x: o.x + 0.5, y: o.y + 0.5, z: o.z + 0.5 }); } catch (_) { oops(_); }
          }, 3 + i * 3));
          exhaust(p, 0.005 * ores.length);
        }
      } catch (_) { oops(_); }
    });
  } catch (_) { oops(_); }
}
