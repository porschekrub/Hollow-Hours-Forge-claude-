import { ItemStack } from "@minecraft/server";
import { DIG_SOFT, DIG_STONE, LEAF_LIKE, LOGS, ORES, hardnessOf, needsPickaxeToDrop, requiredLevel, toolInfoFromId, toolKindFor, } from "./data.js";
import { getContainer, getTool, setTool, updateHandDisplay } from "./storage.js";
import { itemName, randInt, roman } from "./util.js";
export function toolInfo(item) {
    if (!item)
        return undefined;
    let tags = [];
    try {
        tags = item.getTags();
    }
    catch {
        /* ignore */
    }
    const info = toolInfoFromId(item.typeId, tags);
    if (info.kind === "other")
        return undefined;
    return info;
}
export function enchantLevel(item, id) {
    if (!item)
        return 0;
    try {
        const ench = item.getComponent("minecraft:enchantable");
        const e = ench?.getEnchantment(id.includes(":") ? id : "minecraft:" + id);
        return e ? e.level : 0;
    }
    catch {
        return 0;
    }
}
export function enchantList(item) {
    if (!item)
        return "";
    try {
        const ench = item.getComponent("minecraft:enchantable");
        if (!ench)
            return "";
        const list = ench.getEnchantments();
        return list.map((e) => `${prettyEnchant(e.type.id)} ${roman(e.level)}`).join(", ");
    }
    catch {
        return "";
    }
}
const ENCH_TH = {
    efficiency: "ขุดเร็ว (Efficiency)",
    unbreaking: "ทนทาน (Unbreaking)",
    fortune: "นำโชค (Fortune)",
    silk_touch: "สัมผัสไหม (Silk Touch)",
    mending: "ซ่อมแซม (Mending)",
};
function prettyEnchant(id) {
    const k = id.replace("minecraft:", "");
    return ENCH_TH[k] ?? k;
}
export function durabilityText(item) {
    if (!item)
        return "";
    try {
        const d = item.getComponent("minecraft:durability");
        if (!d)
            return "";
        const left = d.maxDurability - d.damage;
        const pct = left / d.maxDurability;
        const col = pct > 0.5 ? "§a" : pct > 0.2 ? "§e" : "§c";
        return `${col}${left}/${d.maxDurability}§r`;
    }
    catch {
        return "";
    }
}
export function toolLabel(item) {
    if (!item)
        return "§7(ไม่มี)§r";
    return `§f${itemName(item.typeId)}§r ${durabilityText(item)}`;
}
/** Break time for a block using the helper's tool (vanilla formula). */
export function breakPlan(blockId, tool, handOnly = false) {
    const hardness = hardnessOf(blockId);
    const kind = toolKindFor(blockId);
    const info = handOnly ? undefined : toolInfo(tool);
    let speed = 1;
    let correct = false;
    if (info && kind !== "hand" && info.kind === kind) {
        correct = true;
        speed = info.speed;
        const eff = enchantLevel(tool, "efficiency");
        if (eff > 0)
            speed += eff * eff + 1;
    }
    let canHarvest = true;
    if (needsPickaxeToDrop(blockId)) {
        canHarvest = !!info && info.kind === "pickaxe" && info.level >= requiredLevel(blockId);
    }
    const perTick = speed / hardness / (canHarvest ? 30 : 100);
    const ticks = Math.max(1, Math.ceil(1 / perTick));
    return { ticks, canHarvest, usesTool: !!info && !handOnly && (correct || kind !== "hand") };
}
/** Applies one use of durability to the helper's tool. Returns false if the tool broke. */
export function damageTool(h) {
    const c = getContainer(h);
    const tool = c?.getItem(0);
    if (!c || !tool)
        return { broke: false };
    let dur;
    try {
        dur = tool.getComponent("minecraft:durability");
    }
    catch {
        dur = undefined;
    }
    if (!dur)
        return { broke: false };
    const unb = enchantLevel(tool, "unbreaking");
    if (unb > 0 && Math.random() >= 1 / (unb + 1))
        return { broke: false };
    const next = dur.damage + 1;
    if (next >= dur.maxDurability) {
        setTool(h, undefined);
        return { broke: true, name: itemName(tool.typeId) };
    }
    dur.damage = next;
    c.setItem(0, tool);
    return { broke: false };
}
/** Uses XP to repair a Mending tool (2 durability per XP). Returns XP left over. */
export function applyMending(h, xp) {
    if (xp <= 0)
        return 0;
    const c = getContainer(h);
    const tool = c?.getItem(0);
    if (!c || !tool || enchantLevel(tool, "mending") <= 0)
        return xp;
    try {
        const dur = tool.getComponent("minecraft:durability");
        if (!dur || dur.damage <= 0)
            return xp;
        const repair = Math.min(dur.damage, xp * 2);
        dur.damage = dur.damage - repair;
        c.setItem(0, tool);
        return xp - Math.ceil(repair / 2);
    }
    catch {
        return xp;
    }
}
function fortuneOre(count, level) {
    if (level <= 0)
        return count;
    let bonus = Math.floor(Math.random() * (level + 2)) - 1;
    if (bonus < 0)
        bonus = 0;
    return count * (bonus + 1);
}
/** Computes drops for a block mined with the given tool (Fortune / Silk Touch aware). */
export function computeDrops(block, tool, plan, handOnly = false) {
    const id = block.typeId;
    const items = [];
    let xp = 0;
    if (!plan.canHarvest)
        return { items, xp };
    const silk = !handOnly && enchantLevel(tool, "silk_touch") > 0;
    const fortune = handOnly ? 0 : enchantLevel(tool, "fortune");
    const mk = (t, n) => {
        try {
            if (n > 0)
                items.push(new ItemStack(t, n));
        }
        catch {
            /* unknown item on this version */
        }
    };
    const ore = ORES[id];
    if (ore) {
        if (silk && ore.fortune !== "none") {
            mk(ore.silk, 1);
        }
        else {
            let n = randInt(ore.min, ore.max);
            if (ore.fortune === "ore")
                n = fortuneOre(n, fortune);
            else if (ore.fortune === "uniform")
                n += randInt(0, fortune);
            mk(ore.drop, n);
            xp = randInt(ore.xp[0], ore.xp[1]);
        }
        return { items, xp };
    }
    if (LEAF_LIKE.has(id)) {
        if (silk) {
            mk(id, 1);
            return { items, xp };
        }
        const sap = id.replace("_leaves", "_sapling");
        const chance = [0.05, 0.0625, 0.083, 0.1][Math.min(fortune, 3)];
        if (id.endsWith("_leaves") && id !== "minecraft:azalea_leaves" && Math.random() < chance) {
            mk(id === "minecraft:mangrove_leaves" ? "minecraft:mangrove_propagule" : sap, 1);
        }
        if (id.endsWith("_leaves") && Math.random() < 0.02)
            mk("minecraft:stick", randInt(1, 2));
        if ((id === "minecraft:oak_leaves" || id === "minecraft:dark_oak_leaves") && Math.random() < 0.005)
            mk("minecraft:apple", 1);
        return { items, xp };
    }
    if (id === "minecraft:stone" && !silk)
        return { items: [mkSafe("minecraft:cobblestone")], xp };
    if (id === "minecraft:deepslate" && !silk)
        return { items: [mkSafe("minecraft:cobbled_deepslate")], xp };
    if (id === "minecraft:grass_block" && !silk)
        return { items: [mkSafe("minecraft:dirt")], xp };
    if ((id === "minecraft:mycelium" || id === "minecraft:podzol") && !silk)
        return { items: [mkSafe("minecraft:dirt")], xp };
    if (id === "minecraft:gravel" && !silk) {
        const fl = [0.1, 0.14, 0.25, 1][Math.min(fortune, 3)];
        return { items: [mkSafe(Math.random() < fl ? "minecraft:flint" : "minecraft:gravel")], xp };
    }
    if (id === "minecraft:clay" && !silk) {
        mk("minecraft:clay_ball", 4);
        return { items, xp };
    }
    if (id === "minecraft:snow" && !silk) {
        mk("minecraft:snowball", 4);
        return { items, xp };
    }
    if (id.startsWith("minecraft:suspicious_"))
        return { items, xp };
    // default: the block itself
    let st;
    try {
        st = block.getItemStack(1, false);
    }
    catch {
        st = undefined;
    }
    if (!st) {
        try {
            st = new ItemStack(id, 1);
        }
        catch {
            st = undefined;
        }
    }
    if (st)
        items.push(st);
    return { items: items.filter((x) => !!x), xp };
}
function mkSafe(t) {
    return new ItemStack(t, 1);
}
/** Picks the best tool of a kind from storage and equips it (swapping the current one into storage). */
export function equipBest(h, kind) {
    const c = getContainer(h);
    if (!c)
        return undefined;
    const score = (it) => {
        const info = toolInfo(it);
        if (!info || info.kind !== kind)
            return -1;
        const eff = enchantLevel(it, "efficiency");
        let durLeft = 1;
        try {
            const d = it.getComponent("minecraft:durability");
            if (d)
                durLeft = (d.maxDurability - d.damage) / d.maxDurability;
        }
        catch {
            /* ignore */
        }
        return info.level * 100 + info.speed * 10 + eff * 5 + durLeft;
    };
    let bestSlot = -1;
    let best = score(c.getItem(0));
    for (let i = 1; i < c.size; i++) {
        const s = score(c.getItem(i));
        if (s > best) {
            best = s;
            bestSlot = i;
        }
    }
    if (bestSlot > 0) {
        const cur = c.getItem(0);
        const nw = c.getItem(bestSlot);
        c.setItem(0, nw);
        c.setItem(bestSlot, cur);
    }
    updateHandDisplay(h);
    const t = c.getItem(0);
    return toolInfo(t)?.kind === kind ? t : undefined;
}
export function hasToolKind(h, kind) {
    const c = getContainer(h);
    if (!c)
        return false;
    for (let i = 0; i < c.size; i++)
        if (toolInfo(c.getItem(i))?.kind === kind)
            return true;
    return false;
}
export function isTool(item) {
    return !!toolInfo(item);
}
export { getTool, DIG_SOFT, DIG_STONE, LOGS };
