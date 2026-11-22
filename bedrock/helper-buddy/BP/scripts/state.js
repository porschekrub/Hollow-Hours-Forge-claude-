import { system } from "@minecraft/server";
import { ORE_GROUPS } from "./data.js";
import { findPlayerById, tellOwner } from "./util.js";
export function defaultSettings() {
    const ores = {};
    for (const g of ORE_GROUPS)
        ores[g.id] = true;
    return { ores, dig: true, replant: true, pickup: true, chest: true, needBlocks: false, chestBuild: false, fight: true, keepJunk: false, selfCare: true };
}
export const DEFAULT_NAME = "น้องช่าง";
// ---------- persistent (dynamic properties) ----------
export function getMode(h) {
    const m = h.getDynamicProperty("h:mode");
    return (typeof m === "string" ? m : "follow");
}
export function setModeProp(h, m) {
    h.setDynamicProperty("h:mode", m);
}
export function getOwnerId(h) {
    const o = h.getDynamicProperty("h:owner");
    return typeof o === "string" && o.length ? o : undefined;
}
export function getOwnerName(h) {
    const o = h.getDynamicProperty("h:ownerName");
    return typeof o === "string" && o.length ? o : undefined;
}
export function getOwner(h) {
    return findPlayerById(getOwnerId(h), getOwnerName(h));
}
export function setOwner(h, p) {
    h.setDynamicProperty("h:owner", p.id);
    h.setDynamicProperty("h:ownerName", p.name);
    try {
        const tame = h.getComponent("minecraft:tameable");
        if (tame && !tame.isTamed)
            tame.tame(p);
    }
    catch {
        /* ignore */
    }
}
export function isOwner(h, p) {
    const id = getOwnerId(h);
    if (!id)
        return false;
    return id === p.id || getOwnerName(h) === p.name;
}
export function getName(h) {
    const n = h.getDynamicProperty("h:name");
    return typeof n === "string" && n.length ? n : DEFAULT_NAME;
}
export function setName(h, n) {
    h.setDynamicProperty("h:name", n);
}
export function getSettings(h) {
    const s = h.getDynamicProperty("h:cfg");
    const d = defaultSettings();
    if (typeof s !== "string")
        return d;
    try {
        const p = JSON.parse(s);
        return { ...d, ...p, ores: { ...d.ores, ...(p.ores ?? {}) } };
    }
    catch {
        return d;
    }
}
export function saveSettings(h, s) {
    h.setDynamicProperty("h:cfg", JSON.stringify(s));
}
export function oreEnabled(s, g) {
    return s.ores[g] !== false;
}
const RT = new Map();
export function rt(h) {
    let r = RT.get(h.id);
    if (!r) {
        r = {
            lastScan: 0, blacklist: new Map(), notify: new Map(), working: false, moving: false, jumpCd: 0,
            lastPickup: 0, busyUntil: 0, replant: [], lastFollowTp: 0, followUntil: 0,
            targetSince: 0, attackCd: 0, roamFails: 0, roamUntil: 0, needBl: new Map(),
        };
        RT.set(h.id, r);
    }
    return r;
}
export function dropRuntime(id) {
    RT.delete(id);
}
export function setAi(h, s) {
    const r = rt(h);
    if (r.ai === s)
        return;
    r.ai = s;
    try {
        h.triggerEvent(s === "follow" ? "helper:ai_follow" : s === "stay" ? "helper:ai_stay" : "helper:ai_work");
    }
    catch {
        /* ignore */
    }
}
export function setWorking(h, on) {
    const r = rt(h);
    if (r.working === on)
        return;
    r.working = on;
    try {
        h.setProperty("helper:working", on);
    }
    catch {
        /* ignore */
    }
}
export function setMoving(h, on) {
    const r = rt(h);
    if (r.moving === on)
        return;
    r.moving = on;
    try {
        h.setProperty("helper:moving", on);
    }
    catch {
        /* ignore */
    }
}
/** Rate-limited message to the owner. */
export function notifyOwner(h, key, msg, cooldownTicks = 300) {
    const r = rt(h);
    const now = system.currentTick;
    const last = r.notify.get(key) ?? -99999;
    if (now - last < cooldownTicks)
        return;
    r.notify.set(key, now);
    tellOwner(getOwner(h), getName(h), msg);
}
export function modeLabel(m) {
    switch (m) {
        case "follow": return "§bเดินตาม§r";
        case "stay": return "§7ยืนรอ§r";
        case "mine": return "§6ขุดแร่§r";
        case "chop": return "§2ตัดไม้§r";
        case "build": return "§dสร้างบ้าน§r";
        case "dig": return "§8สร้างเหมือง§r";
        case "gather": return "§eเก็บบล็อก§r";
        case "food": return "§cหาอาหาร§r";
        case "goto": return "§bเดินทาง§r";
    }
}
/** Where the helper was sent to work on its own (it stays within reach of this point). */
export function getHome(h) {
    const v = h.getDynamicProperty("h:home");
    return v && typeof v === "object" ? v : undefined;
}
export function setHome(h, p) {
    h.setDynamicProperty("h:home", p ? { x: p.x, y: p.y, z: p.z } : undefined);
}
let hpText = () => "";
/** Registered by combat.ts to avoid an import cycle. */
export function setHpText(fn) {
    hpText = fn;
}
export function refreshNameTag(h, extra) {
    const m = getMode(h);
    try {
        // A vanilla name tag was used on the helper: adopt it as the new name.
        const cur = h.nameTag;
        if (cur && !cur.startsWith("§e")) {
            const n = cur.replace(/§./g, "").split("\n")[0].trim().slice(0, 24);
            if (n.length)
                setName(h, n);
        }
    }
    catch {
        /* ignore */
    }
    const base = getName(h);
    const hp = hpText(h);
    const tag = `§e${base}§r ${hp}\n${modeLabel(m)}${extra ? " §7" + extra : ""}`;
    try {
        if (h.nameTag !== tag)
            h.nameTag = tag;
    }
    catch {
        /* ignore */
    }
}

// ---------- work speed (2.0, as in the Java edition): normal, fast, very fast, turbo
export const SPEED_NAMES = [["ปกติ", "normal"], ["เร็ว", "fast"], ["เร็วมาก", "very fast"], ["เทอร์โบ", "turbo"]];
export const WORK_MULT = [1, 2, 4, 10], MOVE_MULT = [1, 1.15, 1.3, 1.5];
export function speedLevel(h) {
    try { const v = h.getDynamicProperty("h:speed"); return typeof v === "number" ? Math.max(0, Math.min(3, v)) : 0; } catch { return 0; }
}
export function setSpeedLevel(h, v) { h.setDynamicProperty("h:speed", Math.max(0, Math.min(3, v))); }
export const workMult = (h) => WORK_MULT[speedLevel(h)];
export const moveMult = (h) => MOVE_MULT[speedLevel(h)];
