// The story's timeline (port of the Java mod's Story): the first nights are completely normal - no ghosts, no omens,
// no fog. On the day the haunting begins (day 3) an emergency alert comes through: something escaped, stay inside,
// lock the doors and windows, don't let it see you. From that night on the ghosts hunt.
import { world, system } from "@minecraft/server";
import { wget, wset, pget, pset, players, say, playTo, later, ok, markBar, oops } from "./store.js";
import { T } from "./texts.js";
import { ALERT_SECONDS } from "./texts_data.js";
import { getLang } from "./lang.js";

export const START_NIGHT = 3;
const FORCED = "hh:story_forced", ALERTED = "hh:alerted";
let active = false, listeners = [];

/** Day number of the world (1 = the first day). */
export function day() { try { return Math.floor(world.getAbsoluteTime() / 24000) + 1; } catch (_) { return 1; } }
export function timeOfDay() { try { return world.getTimeOfDay(); } catch (_) { return 0; } }
export function isNight(t = timeOfDay()) { return t >= 12500 && t < 23400; }
function calendar() { const d = day(), t = timeOfDay(); return d > START_NIGHT || (d === START_NIGHT && t >= 12500); }
function compute() {
  if (ended) return false;
  const f = wget(FORCED, 0);
  if (f) return f > 0;
  return calendar();
}
/** Is the haunting on? (cached each second) */
export const storyActive = () => active;
let ended = false;
/** The last chapter is over (or the five are free): no more haunting. */
export function setEnded(v) { ended = !!v; active = compute(); }
/** Commands / tests: 1 start now, -1 hold back, 0 follow the calendar. */
export function forceStory(mode) { wset(FORCED, mode); tick(); }
export function onStoryChange(fn) { listeners.push(fn); }

function alertDue() {
  if (active) return true;
  const d = day(), t = timeOfDay();
  return !wget(FORCED, 0) && (d > START_NIGHT || (d === START_NIGHT && t >= 2000 && t < 12500));
}
/** The emergency broadcast: sirens, then the announcement read out line by line with subtitles. */
export function sendAlert(p) {
  pset(p, ALERTED, true);
  const lang = getLang() === "th" ? "th" : "en", secs = ALERT_SECONDS[lang];
  try { p.onScreenDisplay.setTitle("§4§l" + T("alert.title"), { subtitle: "§c" + T("alert.sub.siren"), fadeInDuration: 5, stayDuration: 70, fadeOutDuration: 15 }); } catch (_) { oops(_); }
  later(10, () => playTo(p, "phone.siren", 0.8, 1));
  let at = 80;
  for (let i = 0; i < secs.length; i++) {
    const len = Math.round(secs[i] * 20), line = i;
    later(at, () => {
      if (!ok(p)) return;
      playTo(p, `phone.voice.${lang}.${line}`, 1, 1);
      // the subtitle stays for the whole spoken line
      for (let k = 0; k < len + 8; k += 20) later(k + 1, () => { if (ok(p)) { markBar(p); try { p.onScreenDisplay.setActionBar("§f" + T("alert.line." + line)); } catch (_) { oops(_); } } });
    });
    at += len + 10;
  }
  later(at + 10, () => { if (ok(p)) say(p, "§c" + T("alert.chat")); });
  for (const fn of alertHooks) { try { fn(p); } catch (_) { oops(_); } }
}
const alertHooks = [];
export function onAlert(fn) { alertHooks.push(fn); }
export const alerted = (p) => pget(p, ALERTED, false) === true;
export function resetAlert(p) { pset(p, ALERTED, false); }

const joinedAt = new Map();
function tick() {
  const was = active;
  active = compute();
  if (was !== active) for (const fn of listeners) { try { fn(active); } catch (_) { oops(_); } }
  const due = alertDue(), now = system.currentTick;
  for (const p of players()) {
    if (!joinedAt.has(p.id)) joinedAt.set(p.id, now);
    if (!due || now - joinedAt.get(p.id) < 120 || alerted(p)) continue;
    sendAlert(p);
  }
}
export function initStory() {
  active = compute();
  system.runInterval(() => { try { tick(); } catch (_) { oops(_); } }, 20);
  try { world.afterEvents.playerLeave.subscribe((ev) => { joinedAt.delete(ev.playerId); }); } catch (_) { oops(_); }
}
