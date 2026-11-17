// Texts shared with the Java mod (texts_data.js is generated from its lang files) + small helpers for new ones.
import { TEXTS } from "./texts_data.js";
import { getLang } from "./lang.js";

export const isTh = () => getLang() === "th";
/** A text by its Java key (without "hollowhours."), with %s / %1$s filled in. */
export function T(key, ...a) {
  const e = TEXTS[key];
  const s = e ? e[isTh() ? 1 : 0] : key;
  let i = 0;
  return s.replace(/%%/g, "\u0001").replace(/%(\d+)\$s|%s|%d/g, (m, n) => { const v = n ? a[Number(n) - 1] : a[i++]; return v === undefined ? "" : String(v); }).replace(/\u0001/g, "%");
}
export const has = (key) => TEXTS[key] !== undefined;
/** A new text written here in both languages. */
export const L = (th, en) => (isTh() ? th : en);
