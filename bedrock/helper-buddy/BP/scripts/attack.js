// 2.0 (from the Java edition): "ตีมัน", "ไปล่าตัวนั้น", "kill it", "attack that" - it goes for the one you are looking
// at, the one you just hit, or the one that hit you. "ตีไอร่อนโกเลม", "ฆ่าชาวบ้าน 2 ตัว", "kill 5 zombies": any creature
// by name. And the work speed: "เร็วๆ", "เร็วสุดๆ", "ช้าลง", "work faster", "as fast as you can".
import { system, world } from "@minecraft/server";
import { MOBS, ATTACK_VERB, PRONOUN, SPEED_MAX, SPEED_UP, SPEED_DOWN, SPEED_NORMAL, SPEED_FRAME } from "./words.js";
import { setTarget, onKill } from "./combat.js";
import { getSettings, saveSettings, speedLevel, setSpeedLevel, SPEED_NAMES, rt, getOwnerId } from "./state.js";
import { say } from "./actions.js";

const re = (s) => new RegExp(s, "i");
const VERB = re(ATTACK_VERB), PRON = re(PRONOUN), FOOD = /^(cow|pig|chicken|sheep|rabbit|mooshroom|salmon|cod|goat)$/;
const S_MAX = re(SPEED_MAX), S_UP = re(SPEED_UP), S_DOWN = re(SPEED_DOWN), S_NORMAL = re(SPEED_NORMAL);
const frame = (w) => re(SPEED_FRAME.replace("%s", w));

export function mobIn(t) {
    for (const m of MOBS) if (re(`(${m[1]})`).test(t) || re(`\\b(${m[2]})\\b`).test(t)) return m;
    return null;
}
/** "speed" sentences: the new level (0-3), or a step up / down; null when it isn't about speed. */
export function speedOf(t) {
    if (frame(SPEED_NORMAL).test(t)) return { to: 0 };
    if (frame(SPEED_MAX).test(t)) return { to: 3 };
    if (frame(SPEED_DOWN).test(t)) return { step: -1 };
    if (frame(SPEED_UP).test(t)) return { step: 1 };
    return null;
}
/** "attack" sentences: { mob: [id, th, en] | null, n } or null. */
export function attackOf(t, n) {
    if (!VERB.test(t)) return null;
    const mob = mobIn(t);
    if (mob) {
        // "ล่าวัว", "hunt pigs" is hunting for food (the food job), not a fight
        if (FOOD.test(mob[0]) && /(ล่า|hunt|หา|เอา|get|food|อาหาร|เนื้อ|meat)/i.test(t) && !/(ตี|ฆ่า|kill|attack)/i.test(t)) return null;
        return { mob, n: n || 1 };
    }
    if (PRON.test(t)) return { mob: null, n: 1 };
    return null;
}

// ---------- what the owner is fighting
const lastHit = new Map(), lastHurtBy = new Map();
export function ownerHit(p, e) { lastHit.set(p.id, { e, at: system.currentTick }); }
export function ownerHurtBy(p, e) { lastHurtBy.set(p.id, { e, at: system.currentTick }); }
const alive = (e) => { try { return e && e.isValid && (e.getComponent("minecraft:health")?.currentValue ?? 0) > 0; } catch { return false; } };
/** The one the owner means: looked at, just hit, or that just hit them. */
export function targetFor(p) {
    try {
        for (const hit of p.getEntitiesFromViewDirection({ maxDistance: 32 })) {
            const e = hit.entity;
            if (e && e.typeId !== "helper:helper" && e.typeId !== "minecraft:item" && alive(e)) return e;
        }
    } catch { /* ignore */ }
    const now = system.currentTick;
    for (const m of [lastHit, lastHurtBy]) { const r = m.get(p.id); if (r && now - r.at < 20 * 30 && alive(r.e)) return r.e; }
    return null;
}
function nearestOf(p, h, id) {
    let best = null, bd = 1e9;
    for (const c of [p.location, h.location]) {
        let list = [];
        try { list = h.dimension.getEntities({ type: id, location: c, maxDistance: 48 }); } catch { list = []; }
        for (const e of list) {
            if (!alive(e)) continue;
            try { const t = e.getComponent("minecraft:tameable"); if (t?.isTamed && t.tamedToPlayerId === p.id) continue; } catch { /* ignore */ }
            const d = Math.hypot(e.location.x - h.location.x, e.location.z - h.location.z);
            if (d < bd) { bd = d; best = e; }
        }
    }
    return best;
}
/** Goes for a creature; with a name and a number it keeps going until that many are down. */
export function cmdAttack(p, h, it) {
    const s = getSettings(h);
    if (!s.fight) { s.fight = true; saveSettings(h, s); }
    let e = it.mob ? nearestOf(p, h, "minecraft:" + it.mob[0]) : targetFor(p);
    if (!e) {
        say(p, h, it.mob ? `ไม่เห็น${it.mob[1].split("|")[0]}แถวนี้เลยครับ` : "ตัวไหนครับ? มองไปที่มัน หรือตีมันก่อน แล้วสั่งอีกที");
        return false;
    }
    const r = rt(h);
    r.hunt = it.mob && it.n > 1 ? { id: "minecraft:" + it.mob[0], left: it.n, owner: p.id } : undefined;
    setTarget(h, e, true);
    say(p, h, it.mob ? `ลุยเลย! ${it.mob[1].split("|")[0]}${it.n > 1 ? ` ${it.n} ตัว` : ""}` : "จัดให้ครับ!");
    return true;
}
/** After a kill: the next one of the kind asked for. */
export function nextHunt(h, p) {
    const r = rt(h);
    if (!r.hunt) return;
    r.hunt.left--;
    if (r.hunt.left <= 0 || !p) { r.hunt = undefined; return; }
    const e = nearestOf(p, h, r.hunt.id);
    if (e) setTarget(h, e, true); else r.hunt = undefined;
}
export function cmdSpeed(p, h, sp) {
    const was = speedLevel(h), to = sp.to !== undefined ? sp.to : Math.max(0, Math.min(3, was + sp.step));
    setSpeedLevel(h, to);
    const n = SPEED_NAMES[to][0];
    if (to === was) say(p, h, to === 3 ? "นี่ก็เร็วสุดแล้วครับ! (เทอร์โบ)" : to === 0 ? "ทำงานความเร็วปกติอยู่แล้วครับ" : `ความเร็วตอนนี้: ${n}`);
    else say(p, h, to > was ? `ได้เลย! ทำงานแบบ${n} 💨` : `โอเค ทำแบบ${n}ครับ`);
}

onKill((h) => { try { const id = getOwnerId(h); nextHunt(h, world.getAllPlayers().find((p) => p.id === id)); } catch { /* ignore */ } });
