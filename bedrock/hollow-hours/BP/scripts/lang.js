// =====================================================================
//  Hollow Hours — bilingual layer (Thai / English)
//  - The whole addon keeps its original Thai text.
//  - When the language is "en", every outgoing text (chat, action bar, titles,
//    item names/lore, forms, commands) is translated through lang_dict.js.
//  - Language is chosen on first join, and can be changed any time from the
//    Ghost Guide book (button "Language / ภาษา") or with:
//      /scriptevent ghost:lang en     |     /scriptevent ghost:lang th
//  - Item / entity names (lang files) follow the game's own language setting.
// =====================================================================
import * as mc from "@minecraft/server";
import { ActionFormData, ModalFormData, MessageFormData } from "@minecraft/server-ui";
import { EN } from "./lang_dict.js";

const { world, system } = mc;
const KEY = "ghost:lang";
const CHOSEN = "ghost:lang_chosen";
const THAI = /[\u0E00-\u0E7F]/;

let cur = null;
export function getLang() {
  if (cur) return cur;
  try { const v = world.getDynamicProperty(KEY); if (v === "en" || v === "th") { cur = v; return cur; } } catch (_) {}
  return "en";
}
export const isEn = () => getLang() === "en";
export function setLang(l) {
  cur = l === "en" ? "en" : "th";
  cache.clear();
  try { world.setDynamicProperty(KEY, cur); world.setDynamicProperty(CHOSEN, true); } catch (_) {}
}

// ---------- phrase translation ----------
const ENTRIES = Object.entries(EN).sort((a, b) => b[0].length - a[0].length);
const cache = new Map();
export function tx(s) {
  if (typeof s !== "string" || !THAI.test(s) || getLang() !== "en") return s;
  const hit = cache.get(s);
  if (hit !== undefined) return hit;
  let out = s;
  for (const [k, v] of ENTRIES) { if (out.includes(k)) out = out.split(k).join(v); }
  if (cache.size > 4000) cache.clear();
  cache.set(s, out);
  return out;
}
// translate strings nested in arrays / rawtext objects
function deep(x, d = 0) {
  if (typeof x === "string") return tx(x);
  if (d > 4 || x === null || typeof x !== "object") return x;
  if (Array.isArray(x)) return x.map((y) => deep(y, d + 1));
  if (Object.getPrototypeOf(x) !== Object.prototype) return x;
  const o = {};
  for (const k of Object.keys(x)) o[k] = deep(x[k], d + 1);
  return o;
}
export const cap = (s) => (typeof s === "string" ? s.replace(/^(§.)*[a-z]/, (m) => m.slice(0, -1) + m.slice(-1).toUpperCase()) : s);

// ---------- hook the output sinks ----------
function wrapMethod(proto, name, mapArgs) {
  try {
    if (!proto) return false;
    const d = Object.getOwnPropertyDescriptor(proto, name);
    if (!d || typeof d.value !== "function") return false;
    const orig = d.value;
    const fn = function (...a) { try { a = mapArgs(a); } catch (_) {} return orig.apply(this, a); };
    try { Object.defineProperty(proto, name, { ...d, value: fn }); } catch (_) { proto[name] = fn; }
    return true;
  } catch (_) { return false; }
}
function wrapSetter(proto, name) {
  try {
    if (!proto) return false;
    const d = Object.getOwnPropertyDescriptor(proto, name);
    if (!d || typeof d.set !== "function") return false;
    const set = d.set;
    Object.defineProperty(proto, name, { ...d, set(v) { try { v = tx(v); } catch (_) {} return set.call(this, v); } });
    return true;
  } catch (_) { return false; }
}
const first = (a) => { a[0] = deep(a[0]); return a; };
const allStr = (a) => a.map((x) => (typeof x === "string" ? tx(x) : Array.isArray(x) ? x.map((y) => (typeof y === "string" ? tx(y) : y)) : x));
const cmd = (a) => { if (typeof a[0] === "string" && THAI.test(a[0])) a[0] = tx(a[0]); return a; };

let hooked = false;
function hookAll() {
  if (hooked) return; hooked = true;
  try {
    wrapMethod(mc.Player?.prototype, "sendMessage", first);
    try { wrapMethod(Object.getPrototypeOf(world), "sendMessage", first); } catch (_) {}
    const sd = mc.ScreenDisplay?.prototype;
    wrapMethod(sd, "setActionBar", first);
    wrapMethod(sd, "setTitle", first);
    wrapMethod(sd, "updateSubtitle", first);
    wrapSetter(mc.Entity?.prototype, "nameTag");
    wrapSetter(mc.ItemStack?.prototype, "nameTag");
    wrapMethod(mc.ItemStack?.prototype, "setLore", (a) => { if (Array.isArray(a[0])) a[0] = a[0].map(tx); return a; });
    for (const C of [mc.Entity, mc.Player, mc.Dimension]) {
      wrapMethod(C?.prototype, "runCommand", cmd);
      wrapMethod(C?.prototype, "runCommandAsync", cmd);
    }
    for (const m of ["title", "body", "button", "label", "header"]) wrapMethod(ActionFormData?.prototype, m, allStr);
    for (const m of ["title", "dropdown", "textField", "submitButton", "toggle", "slider", "label", "header"]) wrapMethod(ModalFormData?.prototype, m, allStr);
    for (const m of ["title", "body", "button1", "button2"]) wrapMethod(MessageFormData?.prototype, m, allStr);
  } catch (_) {}
}
hookAll();

// ---------- language menu ----------
export async function openLangMenu(p) {
  const f = new ActionFormData()
    .title("§l🌐 Language / ภาษา")
    .body("§7Choose the language for ghost messages, books and menus.\n§7เลือกภาษาของข้อความผี หนังสือ และเมนู\n\n§8(Item names follow the game's own language setting / ชื่อไอเทมจะเปลี่ยนตามภาษาของเกม)")
    .button("§l🇬🇧 English")
    .button("§l🇹🇭 ภาษาไทย");
  const r = await f.show(p);
  if (r.canceled) return r.cancelationReason === "UserBusy" ? "busy" : "cancel";
  setLang(r.selection === 0 ? "en" : "th");
  try { p.onScreenDisplay.setActionBar(r.selection === 0 ? "§aLanguage: English" : "§aภาษา: ไทย"); } catch (_) {}
  return "ok";
}

export function initLang() {
  try {
    system.afterEvents.scriptEventReceive.subscribe((ev) => {
      try {
        if (ev.id !== "ghost:lang") return;
        const v = String(ev.message || "").trim().toLowerCase();
        if (v !== "en" && v !== "th") return;
        setLang(v);
        const who = ev.sourceEntity;
        if (who && who.sendMessage) who.sendMessage(v === "en" ? "§aLanguage: English" : "§aภาษา: ไทย");
      } catch (_) {}
    });
  } catch (_) {}
  try {
    world.afterEvents.playerSpawn.subscribe((ev) => {
      try {
        if (!ev.initialSpawn) return;
        let chosen = false;
        try { chosen = world.getDynamicProperty(CHOSEN) === true; } catch (_) {}
        if (chosen) return;
        const p = ev.player;
        let tries = 0;
        const attempt = () => {
          try {
            let again = false;
            try { again = world.getDynamicProperty(CHOSEN) === true; } catch (_) {}
            if (again || !p.isValid() || ++tries > 25) return;
            openLangMenu(p).then((res) => {
              if (res === "busy") system.runTimeout(attempt, 60);
              else if (res === "cancel") { try { world.setDynamicProperty(CHOSEN, true); } catch (_) {} }
            }).catch(() => system.runTimeout(attempt, 80));
          } catch (_) {}
        };
        system.runTimeout(attempt, 140);
      } catch (_) {}
    });
  } catch (_) {}
}
