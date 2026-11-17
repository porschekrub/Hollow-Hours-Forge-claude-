// Finding food: hunting farm animals, harvesting ripe crops and berries, cooking, and eating by itself.
import { BlockPermutation, ItemStack, system } from "@minecraft/server";
import { addXp, effHp, foodValue, foodValueId, isKO, maxHp, setEffHp, setTarget, } from "./combat.js";
import { face, hopToward, isStuck, resetProgress, steer, stopMoving } from "./move.js";
import { report } from "./progress.js";
import { scanNearest } from "./scan.js";
import { getMode, getName, getOwner, getSettings, notifyOwner, refreshNameTag, rt, setAi, setWorking } from "./state.js";
import { countId, getContainer, storageTake, storeOrDrop } from "./storage.js";
import { center, dist, distXZ, itemName, key, randInt, safeBlock, tellOwner } from "./util.js";
import { SEARCH_RADIUS, pickupItems, tickRoam } from "./work.js";
const M = "minecraft:";
export const FOOD_ANIMALS = {
    [M + "cow"]: "วัว", [M + "mooshroom"]: "วัวเห็ด", [M + "pig"]: "หมู", [M + "chicken"]: "ไก่", [M + "sheep"]: "แกะ",
    [M + "rabbit"]: "กระต่าย",
};
/** How many adult animals of each kind it leaves alone (0 = it may hunt them all). */
const KEEP_ALIVE = 0; // v1.6: no need to keep animals for breeding
const CROPS = {
    [M + "wheat"]: { ripe: 7, seed: M + "wheat_seeds", drops: () => [[M + "wheat", 1], [M + "wheat_seeds", randInt(1, 3)]] }, // always enough to replant
    [M + "carrots"]: { ripe: 7, seed: M + "carrot", drops: () => [[M + "carrot", randInt(2, 5)]] },
    [M + "potatoes"]: {
        ripe: 7, seed: M + "potato",
        drops: () => [[M + "potato", randInt(2, 5)], [M + "poisonous_potato", Math.random() < 0.02 ? 1 : 0]],
    },
    [M + "beetroot"]: { ripe: 7, seed: M + "beetroot_seeds", drops: () => [[M + "beetroot", 1], [M + "beetroot_seeds", randInt(1, 4)]] },
    [M + "sweet_berry_bush"]: { ripe: 2, reset: 1, drops: () => [[M + "sweet_berries", randInt(1, 3)]] },
};
// what it picks up while looking for food
const LOOT = new Set([
    "leather", "feather", "rabbit_hide", "rabbit_foot", "egg", "wheat_seeds", "beetroot_seeds", "wheat", "mushroom",
].map((x) => M + x));
export function isFoodLoot(id) {
    return foodValueId(id) > 0 || LOOT.has(id) || id.endsWith("_wool");
}
// ------------------------------------------------------------------ cooking
const COOK = {
    beef: "cooked_beef", porkchop: "cooked_porkchop", chicken: "cooked_chicken", mutton: "cooked_mutton",
    rabbit: "cooked_rabbit", cod: "cooked_cod", salmon: "cooked_salmon", potato: "baked_potato",
};
const FUEL = { coal: 8, charcoal: 8, coal_block: 80, blaze_rod: 12 };
function fuelCredit(h) {
    const v = h.getDynamicProperty("h:fuel");
    return typeof v === "number" ? v : 0;
}
/**
 * Cooks raw food in the bag with coal/charcoal (one piece of coal cooks 8) and turns 3 wheat into bread.
 * Returns how many items were made.
 */
export function cookInBag(h, max = 64) {
    let made = 0;
    let credit = fuelCredit(h);
    for (const [raw, cooked] of Object.entries(COOK)) {
        while (made < max && countId(h, M + raw) > 0) {
            if (credit <= 0) {
                const fuel = Object.keys(FUEL).find((f) => countId(h, M + f) > 0);
                if (!fuel)
                    break;
                storageTake(h, M + fuel, 1);
                credit += FUEL[fuel];
            }
            storageTake(h, M + raw, 1);
            storeOrDrop(h, new ItemStack(M + cooked, 1));
            credit--;
            made++;
        }
    }
    h.setDynamicProperty("h:fuel", credit);
    // bread needs no fuel
    while (made < max && countId(h, M + "wheat") >= 3) {
        storageTake(h, M + "wheat", 3);
        storeOrDrop(h, new ItemStack(M + "bread", 1));
        made++;
    }
    if (made > 0) {
        try {
            h.dimension.playSound("block.furnace.lit", h.location, { volume: 0.6 });
            h.dimension.spawnParticle("minecraft:basic_smoke_particle", { x: h.location.x, y: h.location.y + 1.8, z: h.location.z });
        }
        catch {
            /* ignore */
        }
    }
    return made;
}
export function hasRawFood(h) {
    return Object.keys(COOK).some((r) => countId(h, M + r) > 0) || countId(h, M + "wheat") >= 3;
}
// ------------------------------------------------------------------ eating by itself
const BAD_FOOD = new Set(["rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "suspicious_stew", "chorus_fruit"].map((x) => M + x));
const PRECIOUS = new Set(["golden_apple", "enchanted_golden_apple", "golden_carrot"].map((x) => M + x));
/** Food in the bag it is willing to eat by itself. */
export function edibleInBag(h, allowPrecious = false) {
    const c = getContainer(h);
    const out = [];
    if (!c)
        return out;
    for (let i = 1; i < c.size; i++) {
        const it = c.getItem(i);
        if (!it || BAD_FOOD.has(it.typeId))
            continue;
        if (PRECIOUS.has(it.typeId) && !allowPrecious)
            continue;
        const v = foodValue(it);
        if (v > 0)
            out.push({ slot: i, item: it, value: v });
    }
    return out;
}
/** Eats one piece of food from its own bag. Returns the item eaten. */
export function eatFromBag(h, announce) {
    if (isKO(h))
        return undefined;
    const max = maxHp(h);
    const hp = effHp(h);
    const missing = max - hp;
    if (missing <= 0.5)
        return undefined;
    const list = edibleInBag(h, hp < max * 0.2);
    if (!list.length)
        return undefined;
    // the biggest meal that does not waste much, otherwise the smallest one
    const heal = (v) => Math.round(v * max * 0.05);
    const fit = list.filter((f) => heal(f.value) <= missing + max * 0.05).sort((a, b) => b.value - a.value);
    const pick = fit[0] ?? list.sort((a, b) => a.value - b.value)[0];
    const c = getContainer(h);
    if (!c)
        return undefined;
    const it = c.getItem(pick.slot);
    if (!it)
        return undefined;
    if (it.amount > 1) {
        it.amount--;
        c.setItem(pick.slot, it);
    }
    else
        c.setItem(pick.slot, undefined);
    setEffHp(h, Math.min(max, hp + heal(pick.value)));
    try {
        h.dimension.playSound("random.eat", h.location, { volume: 0.8 });
        h.dimension.spawnParticle("minecraft:heart_particle", { x: h.location.x, y: h.location.y + 2, z: h.location.z });
    }
    catch {
        /* ignore */
    }
    refreshNameTag(h);
    if (announce) {
        tellOwner(getOwner(h), getName(h), `กิน §e${itemName(pick.item.typeId)}§r ไป 1 ชิ้น (เลือด ${Math.ceil(effHp(h))}/${max})`);
    }
    else {
        notifyOwner(h, "selfeat", `ผมเจ็บนิดหน่อย เลยกิน ${itemName(pick.item.typeId)} ในกระเป๋าไปแล้วนะ (เลือด ${Math.ceil(effHp(h))}/${max})`, 20 * 60);
    }
    return pick.item.typeId;
}
const WORK_MODES = ["mine", "chop", "gather", "dig", "food"];
/**
 * Looking after itself (every tick): eats when below half health, cooks raw food, and when it is hungry
 * with nothing to eat while working it goes to find food. Returns true while it is out looking for food.
 */
export function tickSelfCare(h, tick) {
    const r = rt(h);
    const s = getSettings(h);
    if (!s.selfCare || isKO(h)) {
        r.hungryUntil = undefined;
        return false;
    }
    const slot = (tick + (h.id.charCodeAt(h.id.length - 1) || 0)) % 40;
    const fighting = !!r.target && r.target.isValid && r.targetKind === "fight";
    const max = maxHp(h);
    if (slot === 7 && !fighting && effHp(h) < max * 0.5)
        eatFromBag(h, false);
    if (slot === 21 && tick % 120 < 40 && hasRawFood(h))
        cookInBag(h, 1);
    const now = system.currentTick;
    if (r.hungryUntil !== undefined) {
        // done when it found something to eat (and ate it), or after two minutes
        if (now > r.hungryUntil || edibleInBag(h).length > 0 || !WORK_MODES.includes(getMode(h))) {
            r.hungryUntil = undefined;
            if (edibleInBag(h).length)
                eatFromBag(h, false);
            notifyOwner(h, "hungry:back", "ได้อาหารแล้ว กลับไปทำงานต่อนะ", 20 * 30);
            return false;
        }
        return true;
    }
    if (slot === 13 && effHp(h) < max * 0.35 && !fighting && edibleInBag(h, true).length === 0) {
        if (WORK_MODES.includes(getMode(h)) && getMode(h) !== "food") {
            r.hungryUntil = now + 20 * 120;
            r.task = undefined;
            setWorking(h, false);
            notifyOwner(h, "hungry", `§eผมหิวแล้ว§r (เลือด ${Math.ceil(effHp(h))}/${max}) ขอไปหาอาหารก่อนนะ แล้วจะกลับมาทำงานต่อ`, 20 * 60);
            return true;
        }
        notifyOwner(h, "hungry:ask", `§eผมหิวแล้ว§r (เลือด ${Math.ceil(effHp(h))}/${max}) มีอาหารให้ผมไหม? หรือสั่ง "หาอาหาร" ให้ผมไปหาเองก็ได้`, 20 * 120);
    }
    return false;
}
export function getFoodOptions(h) {
    const v = h.getDynamicProperty("h:food");
    if (typeof v === "string") {
        try {
            return { hunt: true, crops: true, ...JSON.parse(v) };
        }
        catch {
            /* ignore */
        }
    }
    return { hunt: true, crops: true };
}
export function setFoodOptions(h, o) {
    h.setDynamicProperty("h:food", o ? JSON.stringify(o) : undefined);
}
function isHuntable(e) {
    try {
        if (!e.isValid || !FOOD_ANIMALS[e.typeId])
            return false;
        if (e.nameTag && e.nameTag.length)
            return false; // named animals are pets
        if (e.getComponent("minecraft:is_baby"))
            return false;
        const tame = e.getComponent("minecraft:tameable");
        if (tame?.isTamed)
            return false;
        const leash = e.getComponent("minecraft:leashable");
        if (leash?.isLeashed)
            return false;
        return (e.getComponent("minecraft:health")?.currentValue ?? 0) > 0;
    }
    catch {
        return false;
    }
}
/** Nearest animal it may hunt (it leaves at least two of each kind). */
function findAnimal(h, only) {
    let list = [];
    try {
        list = h.dimension.getEntities({ location: h.location, maxDistance: SEARCH_RADIUS });
    }
    catch {
        list = [];
    }
    // only grown-up, unnamed animals count: it always leaves two of those (pets and babies are never hunted)
    const count = new Map();
    for (const e of list)
        if (isHuntable(e))
            count.set(e.typeId, (count.get(e.typeId) ?? 0) + 1);
    let best;
    let bd = Infinity;
    for (const e of list) {
        if (only && e.typeId !== only && !(only === M + "cow" && e.typeId === M + "mooshroom"))
            continue;
        if (!isHuntable(e) || (count.get(e.typeId) ?? 0) <= KEEP_ALIVE)
            continue;
        const d = dist(e.location, h.location);
        if (d < bd) {
            bd = d;
            best = e;
        }
    }
    return best;
}
const cropBl = new Map();
function findCrop(h) {
    const now = system.currentTick;
    const b = scanNearest(h.dimension, { x: h.location.x, y: h.location.y + 1, z: h.location.z }, SEARCH_RADIUS, Object.keys(CROPS), (blk) => {
        const k = `${h.id}|${key(blk.location)}`;
        const until = cropBl.get(k);
        if (until !== undefined && now < until)
            return false;
        const crop = CROPS[blk.typeId];
        let g = 0;
        try {
            g = Number(blk.permutation.getState("growth") ?? 0);
        }
        catch {
            g = 0;
        }
        return g >= crop.ripe;
    });
    return b?.location;
}
function harvest(h, p) {
    const b = safeBlock(h.dimension, p);
    const crop = b ? CROPS[b.typeId] : undefined;
    if (!b || !crop)
        return false;
    const id = b.typeId;
    const drops = crop.drops().filter(([, n]) => n > 0);
    try {
        if (crop.reset !== undefined) {
            b.setPermutation(BlockPermutation.resolve(id, { growth: crop.reset }));
        }
        else {
            // replant with one of the seeds it just harvested
            const seedDrop = drops.find(([d]) => d === crop.seed);
            if (seedDrop && seedDrop[1] > 0) {
                seedDrop[1]--;
                b.setPermutation(BlockPermutation.resolve(id, { growth: 0 }));
            }
            else if (crop.seed && countId(h, crop.seed) > 0) {
                storageTake(h, crop.seed, 1);
                b.setPermutation(BlockPermutation.resolve(id, { growth: 0 }));
            }
            else
                b.setType(M + "air");
        }
    }
    catch {
        return false;
    }
    for (const [d, n] of drops) {
        if (n <= 0)
            continue;
        try {
            storeOrDrop(h, new ItemStack(d, n), center(p));
            report(h, "item", d, n);
        }
        catch {
            /* unknown item on this version */
        }
    }
    try {
        h.dimension.playSound(crop.reset !== undefined ? "block.sweet_berry_bush.pick" : "dig.grass", center(p), { volume: 0.8 });
    }
    catch {
        /* ignore */
    }
    addXp(h, 0.5);
    return true;
}
const FR = new Map();
function fr(h) {
    let f = FR.get(h.id);
    if (!f) {
        f = { since: 0, lastScan: -100, lastPick: -100, fails: 0 };
        FR.set(h.id, f);
    }
    return f;
}
/** One tick of looking for food (food mode, or when it went hungry). */
export function tickFood(h, o = getFoodOptions(h)) {
    const r = rt(h);
    const f = fr(h);
    const now = system.currentTick;
    setAi(h, "work");
    if (now - f.lastPick >= 10) {
        f.lastPick = now;
        pickupItems(h, isFoodLoot, 8);
    }
    if (r.target && r.target.isValid)
        return; // hunting: the combat code walks and attacks
    // ---- walking to a ripe crop
    if (f.crop) {
        const c = center(f.crop);
        const flat = distXZ(h.location, c);
        if (flat <= 1.8 && Math.abs(f.crop.y - h.location.y) <= 2.2) {
            stopMoving(h);
            face(h, c);
            setWorking(h, true);
            harvest(h, f.crop);
            system.runTimeout(() => {
                try {
                    if (h.isValid)
                        setWorking(h, false);
                }
                catch {
                    /* ignore */
                }
            }, 6);
            f.crop = undefined;
            f.lastScan = now - 16; // look for the next one soon
            return;
        }
        const res = steer(h, { x: c.x, y: h.location.y, z: c.z }, 1.2);
        if (res.status !== "moving" && res.status !== "arrived") {
            if (f.fails++ > 6 || !hopToward(h, c)) {
                cropBl.set(`${h.id}|${key(f.crop)}`, now + 20 * 60);
                f.crop = undefined;
            }
        }
        else if (isStuck(h, flat) || now - f.since > 20 * 40) {
            resetProgress(h);
            if (f.fails++ > 6 || !hopToward(h, c)) {
                cropBl.set(`${h.id}|${key(f.crop)}`, now + 20 * 60);
                f.crop = undefined;
            }
        }
        return;
    }
    if (now - f.lastScan >= 20) {
        f.lastScan = now;
        if (o.crops && !o.animal) {
            const c = findCrop(h);
            if (c) {
                f.crop = c;
                f.since = now;
                f.fails = 0;
                r.roam = undefined;
                resetProgress(h);
                return;
            }
        }
        if (o.hunt) {
            const a = findAnimal(h, o.animal);
            if (a) {
                r.roam = undefined;
                setTarget(h, a, true, "hunt");
                return;
            }
        }
    }
    tickRoam(h, "food");
}
export function forgetFoodRuntime(id) {
    FR.delete(id);
}
export function describeFoodOptions(o) {
    if (o.animal)
        return `ล่า${FOOD_ANIMALS[o.animal] ?? itemName(o.animal)}`;
    if (o.hunt && o.crops)
        return "ล่าสัตว์และเก็บผักผลไม้";
    if (o.hunt)
        return "ล่าสัตว์";
    return "เก็บเกี่ยวผักผลไม้ที่สุกแล้ว (ปลูกคืนให้ด้วย)";
}
