// Saved values: on the world and on each player (dynamic properties), never throwing.
import { world, system, ScreenDisplay } from "@minecraft/server";

export function wget(key, def) { try { const v = world.getDynamicProperty(key); return v === undefined ? def : v; } catch (_) { return def; } }
export function wset(key, v) { try { world.setDynamicProperty(key, v); } catch (_) {} }
export function pget(p, key, def) { try { const v = p.getDynamicProperty(key); return v === undefined ? def : v; } catch (_) { return def; } }
export function pset(p, key, v) { try { p.setDynamicProperty(key, v); } catch (_) {} }
export function wjson(key, def) { try { const v = world.getDynamicProperty(key); return typeof v === "string" ? JSON.parse(v) : def; } catch (_) { return def; } }
export function pjson(p, key, def) { try { const v = p.getDynamicProperty(key); return typeof v === "string" ? JSON.parse(v) : def; } catch (_) { return def; } }
export const ok = (e) => { try { if (!e) return false; return typeof e.isValid === "function" ? e.isValid() : e.isValid !== false; } catch (_) { return false; } };
export function gameMode(p) { try { return String(p.getGameMode()).toLowerCase(); } catch (_) { return "survival"; } }
export const playing = (p) => { const g = gameMode(p); return g !== "creative" && g !== "spectator"; };
export function players() { try { return world.getAllPlayers(); } catch (_) { return []; } }
export function say(p, text) { try { p.sendMessage(text); } catch (_) {} }
export function playTo(p, id, vol = 1, pitch = 1, at) { try { p.playSound(id, at ? { volume: vol, pitch, location: at } : { volume: vol, pitch }); } catch (_) {} }
export function later(t, fn) { system.runTimeout(() => { try { fn(); } catch (_) {} }, Math.max(1, Math.round(t))); }
export function mainHand(p) { try { return p.getComponent("minecraft:equippable").getEquipment("Mainhand"); } catch (_) { return undefined; } }
export function setMainHand(p, item) { try { return p.getComponent("minecraft:equippable").setEquipment("Mainhand", item); } catch (_) { return false; } }
export function invOf(e) { try { return e.getComponent("minecraft:inventory").container; } catch (_) { return undefined; } }
export const dimName = (d) => String(d && d.id ? d.id : d).replace("minecraft:", "");

// ---- the action bar is shared: messages from the rest of the add-on win over the survival HUD for a few seconds
const barAt = new Map();
let anyBarAt = -1000, hudWriting = false;
export function markBar(p) { try { barAt.set(p.id, system.currentTick); } catch (_) {} }
export function barBusy(p, hold = 70) {
  const t = system.currentTick;
  return t - anyBarAt < hold || t - (barAt.get(p.id) ?? -1000) < hold;
}
/** The HUD's own writes (they must not count as "someone else is using the bar"). */
export function hudBar(p, text) {
  hudWriting = true;
  try { p.onScreenDisplay.setActionBar(text); } catch (_) {}
  hudWriting = false;
}
try {
  const proto = ScreenDisplay.prototype, d = Object.getOwnPropertyDescriptor(proto, "setActionBar");
  if (d && typeof d.value === "function") {
    const orig = d.value;
    Object.defineProperty(proto, "setActionBar", { ...d, value: function (...a) { if (!hudWriting) anyBarAt = system.currentTick; return orig.apply(this, a); } });
  }
} catch (_) {}

// ---- mistakes are written to the content log once each (never thrown: one broken thing must not stop the rest)
const seenErr = new Set();
export function oops(e) {
  try {
    const line = String(e && e.stack ? e.stack : "").split("\n").find((x) => x.includes("(")) || "";
    const k = String(e) + line;
    if (seenErr.has(k) || seenErr.size > 200) return;
    seenErr.add(k);
    if (/UnloadedChunk|OutOfWorldBoundaries|InvalidEntity|InvalidActor|not valid|no longer valid/i.test(String(e))) return;   // the world moved on: fine
    console.warn(`[hollow] error: ${e} ${line.trim()}`);
  } catch (_) {}
}
