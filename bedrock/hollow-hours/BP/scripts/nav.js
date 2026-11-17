// "Take me there": one place per player, shown on the action bar as an arrow and a distance until they arrive.
import { system } from "@minecraft/server";
import { say, dimName, oops } from "./store.js";
import { addHudLine } from "./survival.js";
import { T } from "./texts.js";

const target = new Map();   // player.id -> { name, x, y, z, dim, live: () => {x,y,z,dim}|null }
const ARROWS = ["↑", "↗", "→", "↘", "↓", "↙", "←", "↖"];

/** an arrow pointing from where the player looks to a place */
export function arrowTo(p, to) {
  const v = p.getViewDirection();
  let a = Math.atan2(to.z - p.location.z, to.x - p.location.x) - Math.atan2(v.z, v.x);
  a = ((a * 180 / Math.PI) % 360 + 360) % 360;
  return ARROWS[Math.round(a / 45) % 8];
}
export const distTo = (p, to) => Math.round(Math.hypot(to.x - p.location.x, to.z - p.location.z));
/** live: a function giving the place each time (a helicopter, a friend), or leave it out for a fixed spot */
export function navigate(p, name, place, live) { target.set(p.id, { name, ...place, live }); }
export function stopNav(p) { target.delete(p.id); }
export const navOf = (p) => target.get(p.id);

function line(p) {
  const t = target.get(p.id);
  if (!t) return "";
  let at = t;
  if (t.live) { try { const l = t.live(); if (!l) { target.delete(p.id); return ""; } at = l; } catch (_) { oops(_); } }
  if (at.dim && at.dim !== dimName(p.dimension)) return `§7◎ ${T("map.nav.elsewhere", t.name)}`;
  const d = distTo(p, at);
  if (d <= 4 && !t.live) { target.delete(p.id); say(p, "§a" + T("map.arrived", t.name)); return ""; }
  return `§a◎ ${t.name} §f${d} m ${arrowTo(p, at)}`;
}
export function initNav() { addHudLine(line); }
