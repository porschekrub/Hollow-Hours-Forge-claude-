import { EntityDamageCause, system } from "@minecraft/server";
import { face, isStuck, resetProgress, steer, stopMoving } from "./move.js";
import { getOwner, getOwnerId, getName, getSettings, notifyOwner, refreshNameTag, rt, setAi, setHpText, setWorking } from "./state.js";
import { getContainer, updateHandDisplay } from "./storage.js";
import { damageTool, enchantLevel, toolInfo } from "./tools.js";
import { dist, distXZ, tellOwner } from "./util.js";
import { report } from "./progress.js";
// The entity really has 1000 + its visible health. Everything above 1000 is the "visible" health, so the
// helper can be knocked out at 0 but can never actually die.
export const HP_BASE = 0; // (unused since v1.4: health is kept in a dynamic property)
// ------------------------------------------------------------------ levels (they only raise max health)
export const MAX_LEVEL = 4;
/** Total experience needed for each level (index 0 = level 1). */
export const LEVEL_XP = [0, 150, 450, 1000];
export const HP_PER_LEVEL = 250; // level 1 = 250 ... level 4 = 1000 (max)
export function getXp(h) {
    const v = h.getDynamicProperty("h:xp");
    return typeof v === "number" && Number.isFinite(v) ? v : 0;
}
export function levelFromXp(xp) {
    let l = 1;
    for (let i = 1; i < LEVEL_XP.length; i++)
        if (xp >= LEVEL_XP[i])
            l = i + 1;
    return Math.min(MAX_LEVEL, l);
}
export function getLevel(h) {
    return levelFromXp(getXp(h));
}
/** Max (visible) health: 250 at level 1 ... 1000 at level 4. */
export function maxHp(h) {
    return getLevel(h) * HP_PER_LEVEL;
}
/** Health needed to get up after being knocked out (10%). */
export const WAKE_FRACTION = 0.1;
export function wakeHp(h) {
    return maxHp(h) * WAKE_FRACTION;
}
function healthComp(h) {
    try {
        return h.getComponent("minecraft:health");
    }
    catch {
        return undefined;
    }
}
// v1.4: the helper's visible health is stored in a dynamic property ("h:hp"). The real entity health is only a
// buffer that is refilled after every hit, so healing/feeding can never be lost by engine health clamping.
const HP_KEY = "h:hp";
/** Refills the real entity health (it only absorbs hits, the helper's own HP is tracked separately). */
function keepEntityFull(h) {
    const c = healthComp(h);
    if (!c)
        return;
    try {
        if (c.currentValue < c.effectiveMax - 0.01)
            c.resetToMaxValue();
    }
    catch {
        /* ignore */
    }
}
export function effHp(h) {
    const max = maxHp(h);
    const v = h.getDynamicProperty(HP_KEY);
    if (typeof v !== "number" || !Number.isFinite(v))
        return max;
    return Math.max(0, Math.min(max, v));
}
export function setEffHp(h, v) {
    try {
        h.setDynamicProperty(HP_KEY, Math.max(0, Math.min(maxHp(h), v)));
    }
    catch {
        /* ignore */
    }
    keepEntityFull(h);
}
/** Called when the helper was hurt for `dmg` points: takes it off the visible health and refills the buffer. */
export function applyDamage(h, dmg) {
    if (!h.isValid)
        return;
    if (!(dmg > 0) || isKO(h)) {
        keepEntityFull(h);
        return;
    }
    setEffHp(h, effHp(h) - dmg);
}
export function isFullHp(h) {
    return effHp(h) >= maxHp(h) - 0.01;
}
export function isKO(h) {
    return h.getDynamicProperty("h:ko") === true;
}
setHpText((h) => {
    const lv = `§bLv.${getLevel(h)}§r`;
    if (isKO(h))
        return `${lv} §c[สลบ]`;
    const hp = Math.ceil(effHp(h));
    const max = maxHp(h);
    const col = hp > max * 0.5 ? "§a" : hp > max * 0.25 ? "§e" : "§c";
    return `${lv} ${col}${hp}/${max}`;
});
/** "เลเวล 2/5 • XP 150/300" */
export function levelText(h) {
    const lv = getLevel(h);
    const xp = Math.floor(getXp(h));
    if (lv >= MAX_LEVEL)
        return `§bเลเวล ${lv}/${MAX_LEVEL} §6(สูงสุด)§r • เลือดสูงสุด ${maxHp(h)}`;
    const next = LEVEL_XP[lv];
    return `§bเลเวล ${lv}/${MAX_LEVEL}§r • XP ${xp}/${next} (อีก ${next - xp} ถึงเลเวล ${lv + 1} = เลือด ${(lv + 1) * HP_PER_LEVEL})`;
}
export const LEVEL_HOWTO = "ผมได้ค่าประสบการณ์ (XP) จากการทำงานและการต่อสู้: §cกำจัดมอนสเตอร์§r ได้เยอะสุด, §bขุดแร่§r (เพชร/มรกต/เศษโบราณได้เยอะ), " +
    "§2ตัดไม้§r, §8ขุดเหมือง§r และ §dสร้างบ้านเสร็จ§r — เลเวลขึ้นแล้วเลือดเต็มทันที";
/** Gives the helper experience; levels up (and heals fully) when it reaches the next level. */
export function addXp(h, amount) {
    if (!(amount > 0) || !h.isValid)
        return;
    const before = getLevel(h);
    const xp = Math.min(LEVEL_XP[LEVEL_XP.length - 1], getXp(h) + amount);
    h.setDynamicProperty("h:xp", Math.round(xp * 100) / 100);
    const after = levelFromXp(xp);
    if (after <= before)
        return;
    if (!isKO(h))
        setEffHp(h, maxHp(h)); // a new level heals completely
    try {
        h.dimension.playSound("random.levelup", h.location, { volume: 1, pitch: 1.1 });
        for (let i = 0; i < 10; i++) {
            h.dimension.spawnParticle("minecraft:villager_happy", {
                x: h.location.x + (Math.random() - 0.5) * 1.4, y: h.location.y + Math.random() * 2.2, z: h.location.z + (Math.random() - 0.5) * 1.4,
            });
        }
    }
    catch {
        /* ignore */
    }
    const owner = getOwner(h);
    try {
        owner?.playSound("random.levelup", { volume: 0.6, pitch: 1.3 });
    }
    catch {
        /* ignore */
    }
    tellOwner(owner, getName(h), `§6§lเลเวลอัป!§r ตอนนี้ผม §bเลเวล ${after}§r แล้ว เลือดสูงสุด §a${maxHp(h)}§r` +
        (after >= MAX_LEVEL ? " §6(เลเวลสูงสุดแล้ว!)" : ` §7(ต่อไป: XP ${LEVEL_XP[after]} = เลือด ${(after + 1) * HP_PER_LEVEL})`));
    refreshNameTag(h);
}
// XP for each ore group
const ORE_XP = {
    coal: 2, copper: 2, iron: 3, gold: 4, redstone: 3, lapis: 4, diamond: 10, emerald: 10, nether: 2, debris: 15,
};
export function oreXp(group) {
    return ORE_XP[group] ?? 2;
}
/** Called when the helper is loaded: upgrades helpers from older versions and fixes the damage group. */
export function initHealth(h) {
    if (h.getDynamicProperty("h:hpv") !== 4) {
        h.setDynamicProperty("h:hpv", 4);
        const ko = h.getDynamicProperty("h:ko") === true;
        setEffHp(h, ko ? 0 : maxHp(h));
    }
    keepEntityFull(h);
    try {
        h.triggerEvent(isKO(h) ? "helper:ko" : "helper:wake");
        h.setProperty("helper:ko", isKO(h));
    }
    catch {
        /* ignore */
    }
}
function knockOut(h) {
    h.setDynamicProperty("h:ko", true);
    setEffHp(h, 0);
    const r = rt(h);
    r.target = undefined;
    stopMoving(h);
    setWorking(h, false);
    setAi(h, "work");
    try {
        h.triggerEvent("helper:ko");
        h.setProperty("helper:ko", true);
        h.dimension.playSound("game.player.hurt", h.location, { volume: 1, pitch: 0.7 });
    }
    catch {
        /* ignore */
    }
    refreshNameTag(h);
    tellOwner(getOwner(h), getName(h), `§c§lผมสลบแล้ว...§r เอาอาหารอะไรก็ได้มาให้กินหน่อย (ยกเว้นเนื้อเน่า) ต้องได้เลือดอย่างน้อย 10% (${Math.ceil(wakeHp(h))}) ถึงจะลุกขึ้นได้\n§7(ถืออาหารแล้วกดที่ตัวผม หรือส่งผ่านหนังสือสั่งงาน)`);
}
function wake(h) {
    h.setDynamicProperty("h:ko", false);
    try {
        h.triggerEvent("helper:wake");
        h.setProperty("helper:ko", false);
        h.dimension.playSound("random.levelup", h.location, { volume: 0.6, pitch: 1.4 });
    }
    catch {
        /* ignore */
    }
    tellOwner(getOwner(h), getName(h), "§aผมฟื้นแล้ว! พร้อมลุยต่อครับ");
    refreshNameTag(h);
}
/** Health check every tick. Returns true while knocked out (the helper does nothing else). */
export function tickHealth(h) {
    if (!isKO(h)) {
        if (effHp(h) <= 0.01) {
            knockOut(h);
            return true;
        }
        if (system.currentTick % 20 === 0)
            keepEntityFull(h);
        return false;
    }
    if (effHp(h) >= wakeHp(h)) {
        wake(h);
        return false;
    }
    stopMoving(h);
    setAi(h, "work");
    if (system.currentTick % 40 === 0) {
        try {
            h.dimension.spawnParticle("minecraft:villager_angry", { x: h.location.x, y: h.location.y + 0.6, z: h.location.z });
        }
        catch {
            /* ignore */
        }
    }
    return true;
}
// ------------------------------------------------------------------ feeding
const FOOD = {
    apple: 4, baked_potato: 5, beef: 3, cooked_beef: 8, bread: 5, carrot: 3, chicken: 2, cooked_chicken: 6, cod: 2,
    cooked_cod: 5, mutton: 2, cooked_mutton: 6, porkchop: 3, cooked_porkchop: 8, rabbit: 3, cooked_rabbit: 5, salmon: 2,
    cooked_salmon: 6, cookie: 2, golden_apple: 10, enchanted_golden_apple: 20, golden_carrot: 6, melon_slice: 2,
    mushroom_stew: 6, beetroot: 1, beetroot_soup: 6, pumpkin_pie: 8, potato: 1, sweet_berries: 2, glow_berries: 2,
    dried_kelp: 1, honey_bottle: 6, rabbit_stew: 10, suspicious_stew: 6, tropical_fish: 1, pufferfish: 1, spider_eye: 2,
    chorus_fruit: 4, poisonous_potato: 2,
};
/** Nutrition of an item id (0 = not food / unknown, -1 = rotten flesh). */
export function foodValueId(typeId) {
    if (typeId === "minecraft:rotten_flesh")
        return -1;
    return FOOD[typeId.replace("minecraft:", "")] ?? 0;
}
/** Nutrition of an item, 0 if it is not food, -1 for rotten flesh (refused). */
export function foodValue(item) {
    if (item.typeId === "minecraft:rotten_flesh")
        return -1;
    const id = item.typeId.replace("minecraft:", "");
    if (FOOD[id] !== undefined)
        return FOOD[id];
    try {
        const f = item.getComponent("minecraft:food");
        if (f)
            return Math.max(1, f.nutrition);
    }
    catch {
        /* ignore */
    }
    return 0;
}
/**
 * Feeds the helper from a container slot (player's hand). Returns true when the item was handled as food
 * (eaten or refused), false when it is not food at all.
 */
export function feedFrom(p, h, c, slot) {
    const item = c.getItem(slot);
    if (!item)
        return false;
    const n = foodValue(item);
    if (n === 0)
        return false;
    const name = getName(h);
    if (n < 0) {
        tellOwner(p, name, "§cไม่เอาเนื้อเน่านะครับ!§r กินแล้วท้องเสีย ขออย่างอื่นได้ไหม");
        return true;
    }
    const hp = effHp(h);
    const max = maxHp(h);
    if (hp >= max - 0.01) {
        tellOwner(p, name, "อิ่มแล้วครับ เลือดเต็มแล้ว");
        return true;
    }
    // each food point heals 5% of max health (bread = 25%, steak = 40%), so food stays useful at every level
    const heal = Math.round(n * max * 0.05);
    const target = Math.min(max, hp + heal);
    setEffHp(h, target);
    let after = effHp(h);
    if (after < target - 0.5) {
        // safety net: the value did not stick, write it once more
        setEffHp(h, target);
        after = effHp(h);
    }
    if (isKO(h) && after >= wakeHp(h))
        tickHealth(h); // gets up right away
    refreshNameTag(h);
    if (item.amount > 1) {
        item.amount -= 1;
        c.setItem(slot, item);
    }
    else
        c.setItem(slot, undefined);
    try {
        h.dimension.playSound("random.eat", h.location, { volume: 0.9 });
        system.runTimeout(() => {
            try {
                if (h.isValid)
                    h.dimension.playSound("random.burp", h.location, { volume: 0.7 });
            }
            catch {
                /* ignore */
            }
        }, 12);
        for (let i = 0; i < 4; i++) {
            h.dimension.spawnParticle("minecraft:heart_particle", {
                x: h.location.x + (Math.random() - 0.5), y: h.location.y + 1.8 + Math.random() * 0.4, z: h.location.z + (Math.random() - 0.5),
            });
        }
    }
    catch {
        /* ignore */
    }
    const ko = isKO(h);
    if (ko) {
        tellOwner(p, name, `อร่อย! (เลือด ${Math.ceil(after)}/${max}) ยังลุกไม่ไหว ต้องได้อีก ${Math.ceil(wakeHp(h) - after)} ถึงจะฟื้น`);
    }
    else {
        tellOwner(p, name, `ขอบคุณครับ อร่อยมาก! (เลือด ${Math.ceil(hp)} → ${Math.ceil(after)}/${max})`);
    }
    return true;
}
// ------------------------------------------------------------------ targeting
const NEVER = new Set([
    "minecraft:player", "helper:helper", "minecraft:villager", "minecraft:villager_v2", "minecraft:wandering_trader",
    "minecraft:iron_golem", "minecraft:snow_golem", "minecraft:armor_stand", "minecraft:item", "minecraft:xp_orb",
    "minecraft:allay", "minecraft:npc", "minecraft:arrow", "minecraft:trident", "minecraft:painting", "minecraft:copper_golem",
]);
function canTarget(h, e, explicit) {
    try {
        if (!e.isValid || e.id === h.id || NEVER.has(e.typeId))
            return false;
        if (!e.getComponent("minecraft:health"))
            return false;
        const tame = e.getComponent("minecraft:tameable");
        if (tame?.isTamed)
            return false; // never fight pets
        if (!explicit) {
            // v1.6: creepers are fought too (the helper hits them from a longer reach, see tickCombat)
            return e.matches({ families: ["monster"] });
        }
        return true;
    }
    catch {
        return false;
    }
}
// targets the helper could not reach recently (so it does not keep trying)
const unreachable = new Map();
export function setTarget(h, e, explicit, kind = "fight") {
    if ((kind === "fight" && !getSettings(h).fight) || isKO(h))
        return;
    if (!canTarget(h, e, explicit))
        return;
    const k = `${h.id}|${e.id}`;
    const until = unreachable.get(k);
    if (until !== undefined) {
        if (system.currentTick < until && !explicit)
            return;
        unreachable.delete(k);
    }
    const r = rt(h);
    if (r.target && r.target.isValid && r.target.id === e.id)
        return;
    // fighting a monster is more important than hunting
    if (kind === "hunt" && r.target && r.target.isValid && r.targetKind === "fight")
        return;
    r.target = e;
    r.targetKind = kind;
    r.targetSince = system.currentTick;
    resetProgress(h);
}
/** Looks for monsters close to the helper or its owner. */
export function scanThreats(h) {
    if (!getSettings(h).fight || isKO(h))
        return;
    const r = rt(h);
    if (r.target && r.target.isValid)
        return;
    const centers = [h.location];
    const owner = getOwner(h);
    if (owner && owner.dimension.id === h.dimension.id && dist(owner.location, h.location) < 24)
        centers.push(owner.location);
    let best;
    let bd = 99;
    for (const c of centers) {
        let list = [];
        try {
            list = h.dimension.getEntities({ location: c, maxDistance: 10, families: ["monster"] });
        }
        catch {
            list = [];
        }
        for (const e of list) {
            if (!canTarget(h, e, false))
                continue;
            const d = dist(e.location, h.location);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
    }
    if (best)
        setTarget(h, best, false);
}
// ------------------------------------------------------------------ fighting
const MAT_DMG = { wooden: 0, golden: 0, stone: 1, copper: 1, iron: 2, diamond: 3, netherite: 4 };
const KIND_DMG = { sword: 5, axe: 4, pickaxe: 3, shovel: 2, hoe: 2 };
function weaponScore(it) {
    const info = toolInfo(it);
    if (!info)
        return 3; // bare hands
    return (KIND_DMG[info.kind] ?? 2) + (MAT_DMG[info.material] ?? 1) + enchantLevel(it, "sharpness") * 1.25;
}
/** Puts the best weapon in hand (swords first) and returns its damage. */
function equipWeapon(h) {
    const c = getContainer(h);
    if (!c)
        return 3;
    let bestSlot = 0;
    let best = weaponScore(c.getItem(0));
    for (let i = 1; i < c.size; i++) {
        const it = c.getItem(i);
        if (!it || !toolInfo(it))
            continue;
        const sc = weaponScore(it) + (toolInfo(it)?.kind === "sword" ? 0.5 : 0);
        if (sc > best + 0.4) {
            best = sc;
            bestSlot = i;
        }
    }
    if (bestSlot > 0) {
        const cur = c.getItem(0);
        c.setItem(0, c.getItem(bestSlot));
        c.setItem(bestSlot, cur);
        updateHandDisplay(h);
    }
    return weaponScore(c.getItem(0));
}
const swingUntil = new Map();
/** Fights the current target. Returns true while busy fighting. */
export function tickCombat(h) {
    const r = rt(h);
    const now = system.currentTick;
    const su = swingUntil.get(h.id);
    if (su !== undefined && now >= su) {
        setWorking(h, false);
        swingUntil.delete(h.id);
    }
    const t = r.target;
    if (!t)
        return false;
    let alive = false;
    try {
        alive = t.isValid && t.dimension.id === h.dimension.id && (t.getComponent("minecraft:health")?.currentValue ?? 0) > 0;
    }
    catch {
        alive = false;
    }
    if (!alive || dist(t.location, h.location) > 32 || now - r.targetSince > 20 * 90 ||
        (r.targetKind !== "hunt" && !getSettings(h).fight)) {
        r.target = undefined;
        stopMoving(h);
        return false;
    }
    setAi(h, "work");
    const d3 = dist(t.location, h.location);
    const dy = Math.abs(t.location.y - h.location.y);
    // v1.6: a creeper blows up when it gets close, so the helper strikes it from further away (and harder)
    const creeper = t.typeId === "minecraft:creeper";
    const reach = creeper ? 4.6 : 2.6;
    face(h, { x: t.location.x, y: t.location.y + 1, z: t.location.z });
    if (d3 > reach || dy > 2) {
        const res = steer(h, t.location, creeper ? 3.8 : 1.5);
        if (res.status === "blocked" || res.status === "cliff" || isStuck(h, distXZ(h.location, t.location), 60)) {
            // can't reach it: give up on this one for a while
            if (unreachable.size > 200)
                unreachable.clear();
            unreachable.set(`${h.id}|${t.id}`, now + 20 * 15);
            r.target = undefined;
            stopMoving(h);
            return false;
        }
        if (r.attackCd > 0)
            r.attackCd--;
        return true;
    }
    stopMoving(h);
    if (r.attackCd > 0) {
        r.attackCd--;
        return true;
    }
    const dmg = equipWeapon(h) * (creeper ? 2 : 1);
    let before = 0;
    let maxH = 20;
    try {
        const hc = t.getComponent("minecraft:health");
        before = hc?.currentValue ?? 0;
        maxH = hc?.effectiveMax ?? 20;
    }
    catch {
        /* ignore */
    }
    try {
        t.applyDamage(dmg, { cause: EntityDamageCause.entityAttack, damagingEntity: h });
    }
    catch {
        /* ignore */
    }
    let killed = false;
    try {
        killed = before > 0 && (!t.isValid || (t.getComponent("minecraft:health")?.currentValue ?? 0) <= 0);
    }
    catch {
        killed = true;
    }
    if (killed) {
        addXp(h, Math.max(3, Math.min(50, Math.round(maxH / 2)))); // zombie = 10 XP
        report(h, "kill", t.typeId);
    }
    try {
        const dx = t.location.x - h.location.x;
        const dz = t.location.z - h.location.z;
        const l = Math.sqrt(dx * dx + dz * dz) || 1;
        const kb = (creeper ? 1.1 : 0.4) + enchantLevel(getContainer(h)?.getItem(0), "knockback") * 0.4;
        t.applyKnockback({ x: (dx / l) * kb, z: (dz / l) * kb }, 0.3);
    }
    catch {
        /* ignore */
    }
    try {
        const fire = enchantLevel(getContainer(h)?.getItem(0), "fire_aspect");
        if (fire > 0)
            t.setOnFire(4 * fire, true);
    }
    catch {
        /* ignore */
    }
    const weapon = getContainer(h)?.getItem(0);
    if (weapon && toolInfo(weapon)) {
        const res = damageTool(h);
        if (res.broke)
            notifyOwner(h, "weaponbroke", `§c${res.name} พังแล้ว!§r ส่งอาวุธ/เครื่องมือใหม่มาให้หน่อยนะ`, 40);
    }
    try {
        h.dimension.playSound("game.player.attack.strong", h.location, { volume: 0.8 });
    }
    catch {
        /* ignore */
    }
    setWorking(h, true);
    swingUntil.set(h.id, now + 6);
    r.attackCd = creeper ? 7 : 12;
    return true;
}
/** Ids of the owner's helpers near a position (for "defend the owner" reactions). */
export function ownedHelpersNear(helpers, owner, maxDist) {
    const out = [];
    for (const h of helpers) {
        try {
            if (!h.isValid || getOwnerId(h) !== owner.id || h.dimension.id !== owner.dimension.id)
                continue;
            if (dist(h.location, owner.location) <= maxDist)
                out.push(h);
        }
        catch {
            /* ignore */
        }
    }
    return out;
}
export function describeHealth(h) {
    if (isKO(h))
        return `§cสลบอยู่§r (เลือด ${Math.ceil(effHp(h))}/${maxHp(h)} ต้องได้ ${Math.ceil(wakeHp(h))} ถึงจะฟื้น)`;
    return `เลือด ${Math.ceil(effHp(h))}/${maxHp(h)}`;
}

/** Raw numbers for troubleshooting (shown at the bottom of the status). */
export function healthDebug(h) {
    const c = healthComp(h);
    const raw = h.getDynamicProperty(HP_KEY);
    return `§8[hp ${typeof raw === "number" ? raw.toFixed(1) : "?"} • บัฟเฟอร์ ${c ? `${Math.round(c.currentValue)}/${Math.round(c.effectiveMax)}` : "?"} • v${"1.6.0"}]`;
}

/**
 * v1.6: the helper was hurt by something. Whatever hurt it (a mob, an arrow, an explosion ...) becomes its target,
 * so it always fights back. With no visible attacker (explosion, void ...) it picks the closest hostile mob nearby.
 */
export function fightBack(h, attacker) {
    if (!h.isValid || isKO(h) || !getSettings(h).fight)
        return;
    try {
        if (attacker && attacker.isValid && attacker.id !== h.id && attacker.typeId !== "minecraft:player" && attacker.typeId !== "helper:helper") {
            setTarget(h, attacker, true);
            return;
        }
        let best;
        let bd = 99;
        for (const e of h.dimension.getEntities({ location: h.location, maxDistance: 12, families: ["monster"] })) {
            if (!canTarget(h, e, false))
                continue;
            const d = dist(e.location, h.location);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best)
            setTarget(h, best, true);
    }
    catch {
        /* ignore */
    }
}
