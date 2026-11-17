// The phone (port of the Java mod's phone, with Bedrock's own menus instead of a drawn screen).
// Battery 0-100 (random when you get it): it drains a little in your pocket, more every time you use it, a lot while
// gaming. Charge it with a power bank (3 charges). Apps: Messages, Chat (Mom, Dad, your best friend - they answer -
// and every real player: text or your location), Location (navigate home, to the helicopter, your grave, a friend),
// Rescue (the countdown), Health, and Ghost Tap (a game that calms you down).
import { world, system, ItemStack } from "@minecraft/server";
import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import { pget, pset, pjson, players, say, playTo, later, ok, invOf, mainHand, dimName, markBar, oops } from "./store.js";
import { storyActive, alerted, onAlert, day, timeOfDay } from "./story.js";
import { getThirst, getSanity, addSanity } from "./survival.js";
import { navigate, stopNav, navOf } from "./nav.js";
import { T, L, has } from "./texts.js";

export const PHONE = "ghost:phone", CHARGER = "ghost:charger";
export const CFG = { GIVE: true, LOST_NIGHT: 15 };
const NPCS = ["mom", "dad", "friend"], KEEP = 14;
const rand = (a, b) => a + Math.random() * (b - a);
let rescueInfo = () => ({ lines: [T("rescue.app.nonews")], heli: null });
const places = [];   // (player) => [{ name, x, y, z, dim, live? }]
export function setRescueInfo(fn) { rescueInfo = fn; }
export function addPlaces(fn) { places.push(fn); }

// ---------------------------------------------------------------- battery
export const battery = (p) => Math.max(0, Math.min(100, Number(pget(p, "hh:bat", 60))));
export function setBattery(p, v) { pset(p, "hh:bat", Math.max(0, Math.min(100, v))); }
export function drain(p, v) { setBattery(p, battery(p) - v); }
export function hasPhone(p) {
  const c = invOf(p);
  if (!c) return false;
  for (let i = 0; i < c.size; i++) { try { const it = c.getItem(i); if (it && it.typeId === PHONE) return true; } catch (_) { oops(_); } }
  return false;
}
function clock() {
  const t = timeOfDay(), h = (Math.floor(t / 1000) + 6) % 24, m = Math.floor((t % 1000) * 60 / 1000);
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

// ---------------------------------------------------------------- messages (the inbox)
function inbox(p) { return pjson(p, "hh:inbox", []); }
/** A message arrives: a tone, a line on the screen, and it is kept in Messages. */
export function notify(p, from, text, danger) {
  try {
    const list = inbox(p);
    list.push([from, text, day()]);
    while (list.length > KEEP || JSON.stringify(list).length > 6000) list.shift();
    pset(p, "hh:inbox", JSON.stringify(list));
    pset(p, "hh:unread", Number(pget(p, "hh:unread", 0)) + 1);
    playTo(p, "phone.notify", 0.8, danger ? 0.8 : 1.1);
    say(p, `${danger ? "§c" : "§b"}[${from}] §f${text}`);
  } catch (_) { oops(_); }
}

// ---------------------------------------------------------------- chats
// a line is [who, type, payload]: who = "me" / "them"; type t = text, k = text key, l = a place "x,y,z,dim",
// x = not delivered, n = no signal
function chats(p) { return pjson(p, "hh:chats", null); }
function saveChats(p, c) {
  let s = JSON.stringify(c);
  while (s.length > 9000) { let big = null; for (const k of Object.keys(c)) if (!big || c[k].length > c[big].length) big = k; c[big].shift(); s = JSON.stringify(c); }
  pset(p, "hh:chats", s);
}
function chatLog(p) {
  let c = chats(p);
  if (!c) { c = { mom: [["them", "k", "phone.msg.mom"]], friend: [["them", "k", "phone.msg.friend"]], dad: [["them", "k", "chat.dad.hello"]] }; saveChats(p, c); }
  return c;
}
function addLine(p, contact, line) {
  const c = chatLog(p);
  (c[contact] = c[contact] || []).push(line);
  while (c[contact].length > KEEP) c[contact].shift();
  saveChats(p, c);
}
function lineText(l) {
  const [who, type, payload] = l;
  const me = who === "me";
  let t = payload;
  if (type === "k") t = T(payload);
  else if (type === "l") { const a = String(payload).split(","); t = `◉ ${a[0]}, ${a[1]}, ${a[2]}`; }
  if (type === "x") return `§8${L("คุณ", "You")}: ${payload} §c(${L("ส่งไม่ถึง", "not delivered")})`;
  if (type === "n") return `§8${L("คุณ", "You")}: ${payload} §c(${L("ไม่มีสัญญาณ", "no signal")})`;
  return me ? `§7${L("คุณ", "You")}: §f${t}` : `§b${t}`;
}
const npcName = (n) => T("chat.notice." + n + ".title");
/** night 15: Mom, Dad and your friend are gone */
export function contactsLost(p) {
  const d = day(), t = timeOfDay();
  return Number(pget(p, "hh:chat_stage", 0)) >= 3 || d > CFG.LOST_NIGHT || (d === CFG.LOST_NIGHT && t >= 13000);
}
function category(text) {
  const t = text.toLowerCase(), hasAny = (...w) => w.some((x) => t.includes(x));
  if (t === "where") return "where";
  if (hasAny("help", "scared", "afraid", "ghost", "monster", "save", "ช่วย", "กลัว", "ผี", "ตาย", "หนี", "น่ากลัว")) return "scared";
  if (hasAny("love", "miss", "รัก", "คิดถึง", "ขอบคุณ", "thank")) return "love";
  if (hasAny("where", "home", "come", "อยู่ไหน", "ที่ไหน", "บ้าน", "มาหา", "มารับ")) return "where";
  if (hasAny("hi", "hello", "hey", "yo", "สวัสดี", "หวัดดี", "ดี", "ฮัลโหล")) return "greet";
  return "other";
}
function npcReply(p, who, text) {
  const danger = storyActive() || alerted(p);
  later(rand(50, 110), () => {
    if (!ok(p) || contactsLost(p)) return;
    const key = `chat.${who}.${danger ? "danger" : "calm"}.${category(text)}.${Math.floor(Math.random() * 3)}`;
    addLine(p, who, ["them", "k", key]);
    notify(p, npcName(who), T(key), false);
  });
}
/** From the phone: a message (or your location) to a contact ("mom" / "dad" / "friend" / "p:<player id>"). */
export function send(p, contact, isLocation, text) {
  text = String(text || "").replace(/\|/g, "/").trim().slice(0, 160);
  const type = isLocation ? "l" : "t";
  const payload = isLocation ? `${Math.floor(p.location.x)},${Math.floor(p.location.y)},${Math.floor(p.location.z)},${dimName(p.dimension)}` : text;
  if (!isLocation && !text) return;
  if (NPCS.includes(contact)) {
    const gone = contactsLost(p);
    addLine(p, contact, ["me", gone ? "x" : type, payload]);
    if (!gone) npcReply(p, contact, isLocation ? "where" : text);
    return;
  }
  const o = players().find((x) => "p:" + x.id === contact);
  if (!o || o.id === p.id) { addLine(p, contact, ["me", "x", payload]); return; }
  let alive = true;
  try { alive = o.getComponent("minecraft:health").currentValue > 0; } catch (_) { oops(_); }
  if (!alive) { addLine(p, contact, ["me", "n", payload]); return; }
  addLine(p, contact, ["me", type, payload]);
  addLine(o, "p:" + p.id, ["them", type, payload]);
  pset(o, "hh:name:" + p.id, p.name);
  pset(p, "hh:name:" + o.id, o.name);
  notify(o, p.name, isLocation ? T("chat.notice.location", p.name) : text, false);
}
/** Each of them writes, a few seconds apart (1 = after the alert, 2 = it gets bad where they are, 3 = the last one). */
export function storyMessages(p, stage) {
  const part = stage === 1 ? "story1" : stage === 2 ? "story2" : "final";
  pset(p, "hh:chat_stage", Math.max(stage, Number(pget(p, "hh:chat_stage", 0))));
  let delay = 40;
  for (const n of NPCS) {
    later(delay, () => { if (!ok(p)) return; addLine(p, n, ["them", "k", `chat.${n}.${part}`]); notify(p, npcName(n), T(`chat.${n}.${part}`), stage === 3); });
    delay += stage === 3 ? 120 : 80;
  }
}

// ---------------------------------------------------------------- the menus
async function show(p, form) {
  for (let i = 0; i < 6; i++) {
    const r = await form.show(p);
    if (!r.canceled || r.cancelationReason !== "UserBusy") return r;
    await new Promise((res) => system.runTimeout(res, 10));
  }
  return { canceled: true };
}
function head(p) { return `§l${T("item.phone")}§r  §7${Math.round(battery(p))}%  ·  ${T("phone.day", day())} ${clock()}`; }

export async function openPhone(p) {
  try {
    if (battery(p) <= 0) { markBar(p); p.onScreenDisplay.setActionBar("§c" + T("phone.dead")); playTo(p, "phone.click", 0.6, 0.6); return; }
    drain(p, 0.4);
    playTo(p, "phone.click", 0.6, 1);
    const unread = Number(pget(p, "hh:unread", 0));
    const f = new ActionFormData().title(head(p)).body(navOf(p) ? `§a◎ ${navOf(p).name}` : "")
      .button(T("phone.app.messages") + (unread ? ` §c(${unread})` : ""), "textures/items/paper")
      .button(T("phone.app.chat"), "textures/items/book_writable")
      .button(T("phone.app.map"), "textures/items/compass_item")
      .button(T("phone.app.rescue"), "textures/items/fireworks")
      .button(T("phone.app.health"), "textures/items/apple")
      .button(T("phone.app.tap"), "textures/ghost_ui/g4");
    const r = await show(p, f);
    if (r.canceled) return;
    [appMessages, appChat, appMap, appRescue, appHealth, appTap][r.selection](p);
  } catch (_) { oops(_); }
}
async function appMessages(p) {
  pset(p, "hh:unread", 0);
  const list = inbox(p).slice().reverse();
  const body = list.length ? list.map((m) => `§e${m[0]} §8(${T("phone.day", m[2])})\n§f${m[1]}`).join("\n\n") : "§7—";
  const r = await show(p, new ActionFormData().title(T("phone.app.messages")).body(body).button("§l←"));
  if (!r.canceled) openPhone(p);
}
async function appChat(p) {
  const c = chatLog(p);
  const ids = [...NPCS];
  for (const o of players()) if (o.id !== p.id) ids.push("p:" + o.id);
  for (const k of Object.keys(c)) if (!ids.includes(k)) ids.push(k);
  const nameOf = (id) => NPCS.includes(id) ? npcName(id) : (players().find((x) => "p:" + x.id === id)?.name ?? pget(p, "hh:name:" + id.slice(2), "?"));
  const f = new ActionFormData().title(T("phone.app.chat"));
  for (const id of ids) { const last = (c[id] || []).slice(-1)[0]; f.button(`§l${nameOf(id)}§r${last ? "\n§8" + lineText(last).replace(/§./g, "").slice(0, 30) : ""}`); }
  f.button("§l←");
  const r = await show(p, f);
  if (r.canceled) return;
  if (r.selection >= ids.length) return openPhone(p);
  chatWith(p, ids[r.selection], nameOf(ids[r.selection]));
}
async function chatWith(p, id, name) {
  const log = (chatLog(p)[id] || []).slice(-8);
  const place = log.slice().reverse().find((l) => l[0] === "them" && l[1] === "l");
  const f = new ActionFormData().title(name).body(log.length ? log.map(lineText).join("\n") : "§7—")
    .button(L("เขียนข้อความ", "Write a message")).button(L("ส่งตำแหน่งของฉัน", "Send my location"));
  if (place) f.button(L("นำทางไปตำแหน่งที่ส่งมา", "Navigate to the location they sent"));
  f.button("§l←");
  const r = await show(p, f);
  if (r.canceled) return;
  if (r.selection === 0) {
    const m = await show(p, new ModalFormData().title(name).textField(L("ข้อความ", "Message"), "..."));
    if (!m.canceled && m.formValues && String(m.formValues[0] || "").trim()) { send(p, id, false, String(m.formValues[0])); drain(p, 0.3); }
    return chatWith(p, id, name);
  }
  if (r.selection === 1) { send(p, id, true, ""); return chatWith(p, id, name); }
  if (place && r.selection === 2) { const a = String(place[2]).split(","); navigate(p, name, { x: +a[0], y: +a[1], z: +a[2], dim: a[3] }); return; }
  appChat(p);
}
async function appMap(p) {
  const list = [];
  try { const s = p.getSpawnPoint(); if (s) list.push({ name: T("map.home"), x: s.x, y: s.y, z: s.z, dim: dimName(s.dimension) }); } catch (_) { oops(_); }
  if (!list.length) { try { const s = world.getDefaultSpawnLocation(); list.push({ name: T("map.home"), x: s.x, y: s.y, z: s.z, dim: "overworld" }); } catch (_) { oops(_); } }
  for (const fn of places) { try { for (const x of fn(p) || []) list.push(x); } catch (_) { oops(_); } }
  const l = p.location, v = p.getViewDirection();
  const dirs = ["e", "se", "s", "sw", "w", "nw", "n", "ne"];
  const facing = dirs[Math.round(((Math.atan2(v.z, v.x) * 180 / Math.PI) + 360) % 360 / 45) % 8];
  const f = new ActionFormData().title(T("phone.app.map")).body(`§f${Math.floor(l.x)}, ${Math.floor(l.y)}, ${Math.floor(l.z)}\n§7${T("map.facing", T("map.dir." + facing))}`);
  for (const x of list) f.button(`§l${x.name}§r\n§8${x.dim && x.dim !== dimName(p.dimension) ? T("map.elsewhere") : Math.round(Math.hypot(x.x - l.x, x.z - l.z)) + " m"}`);
  if (navOf(p)) f.button("§c" + T("map.stop"));
  f.button("§l←");
  const r = await show(p, f);
  if (r.canceled) return;
  if (r.selection < list.length) { const x = list[r.selection]; navigate(p, x.name, x, x.live); drain(p, 0.3); return; }
  if (navOf(p) && r.selection === list.length) { stopNav(p); return; }
  openPhone(p);
}
async function appRescue(p) {
  const info = rescueInfo(p);
  const f = new ActionFormData().title(T("phone.app.rescue")).body(info.lines.join("\n\n"));
  if (info.heli) f.button("§a" + T("rescue.app.navigate"));
  f.button("§l←");
  const r = await show(p, f);
  if (r.canceled) return;
  if (info.heli && r.selection === 0) { navigate(p, T("map.heli"), info.heli, info.live); return; }
  openPhone(p);
}
async function appHealth(p) {
  const body = `§b${T("phone.thirst", getThirst(p).toFixed(0))}\n§d${T("phone.sanity", getSanity(p).toFixed(0))}\n\n§7${T("phone.health.tip")}\n\n§7${T("phone.batteryhealth", 100)}`;
  const r = await show(p, new ActionFormData().title(T("phone.app.health")).body(body).button("§l←"));
  if (!r.canceled) openPhone(p);
}
/** Ghost Tap: eight rounds, tap the ghost before it hides (under two seconds). It calms you down and eats battery. */
async function appTap(p) {
  let score = 0;
  const best = Number(pget(p, "hh:tap_best", 0));
  for (let round = 0; round < 8; round++) {
    if (battery(p) <= 0 || !ok(p)) break;
    const at = Math.floor(Math.random() * 6), t0 = system.currentTick;
    const f = new ActionFormData().title(`${T("phone.app.tap")}  ${round + 1}/8`).body(T("phone.score", score, best));
    for (let i = 0; i < 6; i++) { if (i === at) f.button("§l!!", `textures/ghost_ui/g${1 + Math.floor(Math.random() * 5)}`); else f.button("§8·"); }
    const r = await show(p, f);
    if (r.canceled) break;
    const quick = system.currentTick - t0 <= 40;
    if (r.selection === at && quick) { score++; playTo(p, "phone.game", 0.7, 1 + score * 0.05); } else playTo(p, "phone.click", 0.6, 0.6);
    drain(p, 0.5);
  }
  if (score > best) pset(p, "hh:tap_best", score);
  addSanity(p, score * 1.2);
  markBar(p);
  try { p.onScreenDisplay.setActionBar("§a" + T("phone.score", score, Math.max(score, best))); } catch (_) { oops(_); }
}

// ---------------------------------------------------------------- the power bank
function charge(p) {
  if (!hasPhone(p)) { say(p, "§7" + T("item.charger.nothing")); return false; }
  if (battery(p) >= 99) { say(p, "§7" + T("item.charger.nothing")); return false; }
  setBattery(p, 100);
  playTo(p, "phone.charge", 0.9, 1);
  return true;
}

function tick() {
  const d = day();
  for (const p of players()) {
    try {
      // first join: a phone with a random battery
      if (CFG.GIVE && !pget(p, "hh:phone_given", false)) {
        pset(p, "hh:phone_given", true);
        setBattery(p, Math.round(rand(35, 90)));
        const left = invOf(p).addItem(new ItemStack(PHONE, 1));
        if (left) p.dimension.spawnItem(left, p.location);
        chatLog(p);
      }
      // the messages that come on their own
      const stage = Number(pget(p, "hh:chat_stage", 0));
      const lost = d > CFG.LOST_NIGHT || (d === CFG.LOST_NIGHT && timeOfDay() >= 13000);
      const want = lost ? 3 : d >= 8 && storyActive() ? 2 : storyActive() || alerted(p) ? 1 : 0;
      if (want > stage && hasPhone(p)) storyMessages(p, want);
    } catch (_) { oops(_); }
  }
}

/** for tests */
export const _phone = { chatLog, inbox, category, charge, lineText };

export function initPhone() {
  system.runInterval(() => { try { tick(); } catch (_) { oops(_); } }, 40);
  // in your pocket it drains slowly: about 1% every 4 minutes
  system.runInterval(() => { for (const p of players()) { try { if (hasPhone(p)) drain(p, 0.25); } catch (_) { oops(_); } } }, 1200);
  onAlert((p) => { try { const list = inbox(p); list.push([T("phone.msg.alert.from"), T("phone.msg.alert"), day()]); pset(p, "hh:inbox", JSON.stringify(list.slice(-KEEP))); } catch (_) { oops(_); } });
  try {
    world.afterEvents.itemUse.subscribe((ev) => {
      try {
        const p = ev.source, it = ev.itemStack;
        if (!it) return;
        if (it.typeId === PHONE) openPhone(p);
        else if (it.typeId === CHARGER) {
          if (!charge(p)) return;
          const h = mainHand(p);
          if (!h || h.typeId !== CHARGER) return;
          const d = h.getComponent("minecraft:durability"), slot = invOf(p).getSlot(p.selectedSlotIndex);
          if (d && d.damage + 1 >= d.maxDurability) { slot.setItem(undefined); say(p, "§a" + T("item.charger.done", 0)); }
          else if (d) { d.damage = d.damage + 1; slot.setItem(h); say(p, "§a" + T("item.charger.done", d.maxDurability - d.damage)); }
        }
      } catch (_) { oops(_); }
    });
  } catch (_) { oops(_); }
}
