import { EquipmentSlot } from "@minecraft/server";
import { dist } from "./util.js";
export const TOOL_SLOT = 0;
export function getContainer(e) {
    try {
        if (!e.isValid)
            return undefined;
        return e.getComponent("minecraft:inventory")?.container;
    }
    catch {
        return undefined;
    }
}
export function getTool(h) {
    return getContainer(h)?.getItem(TOOL_SLOT);
}
const handShown = new Map();
/** Show the equipped tool in the helper's hand (cosmetic copy). */
export function updateHandDisplay(h, force = false) {
    const tool = getTool(h);
    const id = tool?.typeId ?? "";
    if (!force && handShown.get(h.id) === id)
        return;
    handShown.set(h.id, id);
    try {
        h.runCommand(`replaceitem entity @s slot.weapon.mainhand 0 ${id === "" ? "air" : id}`);
    }
    catch {
        /* ignore */
    }
    try {
        const eq = h.getComponent("minecraft:equippable");
        if (eq)
            eq.setEquipment(EquipmentSlot.Mainhand, tool ? tool.clone() : undefined);
    }
    catch {
        /* equippable is player-only on some versions */
    }
}
export function setTool(h, item) {
    const c = getContainer(h);
    if (!c)
        return;
    c.setItem(TOOL_SLOT, item);
    updateHandDisplay(h);
}
/** Adds to storage slots (1..size-1). Returns the amount that did not fit. */
export function storageAdd(h, item) {
    const c = getContainer(h);
    if (!c)
        return item.amount;
    let remaining = item.amount;
    // merge into existing stacks first
    for (let i = 1; i < c.size && remaining > 0; i++) {
        const cur = c.getItem(i);
        if (!cur || cur.amount >= cur.maxAmount)
            continue;
        if (!cur.isStackableWith(item))
            continue;
        const add = Math.min(remaining, cur.maxAmount - cur.amount);
        cur.amount += add;
        c.setItem(i, cur);
        remaining -= add;
    }
    for (let i = 1; i < c.size && remaining > 0; i++) {
        if (c.getItem(i))
            continue;
        const put = item.clone();
        const n = Math.min(remaining, put.maxAmount);
        put.amount = n;
        c.setItem(i, put);
        remaining -= n;
    }
    return remaining;
}
/** Adds to storage; anything that does not fit is dropped at `dropAt`. Returns true if all fit. */
export function storeOrDrop(h, item, dropAt) {
    const left = storageAdd(h, item);
    if (left <= 0)
        return true;
    try {
        const rest = item.clone();
        rest.amount = left;
        h.dimension.spawnItem(rest, dropAt ?? h.location);
    }
    catch {
        /* ignore */
    }
    return false;
}
export function storageCount(h, match) {
    const c = getContainer(h);
    if (!c)
        return 0;
    let n = 0;
    for (let i = 1; i < c.size; i++) {
        const it = c.getItem(i);
        if (it && match(it))
            n += it.amount;
    }
    return n;
}
export function countId(h, typeId) {
    return storageCount(h, (it) => it.typeId === typeId);
}
/** Removes up to n items matching typeId from storage. Returns how many were removed. */
export function storageTake(h, typeId, n) {
    const c = getContainer(h);
    if (!c)
        return 0;
    let taken = 0;
    for (let i = c.size - 1; i >= 1 && taken < n; i--) {
        const it = c.getItem(i);
        if (!it || it.typeId !== typeId)
            continue;
        const t = Math.min(n - taken, it.amount);
        if (t >= it.amount)
            c.setItem(i, undefined);
        else {
            it.amount -= t;
            c.setItem(i, it);
        }
        taken += t;
    }
    return taken;
}
export function usedSlots(h) {
    const c = getContainer(h);
    if (!c)
        return { used: 0, total: 0 };
    let used = 0;
    for (let i = 1; i < c.size; i++)
        if (c.getItem(i))
            used++;
    return { used, total: c.size - 1 };
}
export function isStorageFull(h) {
    const c = getContainer(h);
    if (!c)
        return true;
    for (let i = 1; i < c.size; i++)
        if (!c.getItem(i))
            return false;
    return true;
}
/** Summary of storage: typeId -> amount */
export function storageSummary(h) {
    const m = new Map();
    const c = getContainer(h);
    if (!c)
        return m;
    for (let i = 1; i < c.size; i++) {
        const it = c.getItem(i);
        if (it)
            m.set(it.typeId, (m.get(it.typeId) ?? 0) + it.amount);
    }
    return m;
}
export function playerContainer(p) {
    try {
        return p.getComponent("minecraft:inventory")?.container;
    }
    catch {
        return undefined;
    }
}
/** Gives an item to a player; leftovers drop at their feet. */
export function giveToPlayer(p, item) {
    const c = playerContainer(p);
    let left = item;
    if (c) {
        try {
            left = c.addItem(item);
        }
        catch {
            left = item;
        }
    }
    if (left && left.amount > 0) {
        try {
            p.dimension.spawnItem(left, p.location);
        }
        catch {
            /* ignore */
        }
    }
}
/** Moves everything (optionally the tool too) from the helper to the player. Returns item count moved. */
export function giveAllToPlayer(h, p, includeTool, filter) {
    const c = getContainer(h);
    if (!c)
        return 0;
    let n = 0;
    for (let i = includeTool ? 0 : 1; i < c.size; i++) {
        const it = c.getItem(i);
        if (!it)
            continue;
        if (filter && i !== 0 && !filter(it))
            continue;
        c.setItem(i, undefined);
        n += it.amount;
        giveToPlayer(p, it);
    }
    if (includeTool)
        updateHandDisplay(h);
    return n;
}
/** Nearby chests / barrels (block containers) within radius of a point. */
export function nearbyContainers(h, origin, radius) {
    const out = [];
    const dim = h.dimension;
    const r = Math.ceil(radius);
    const ox = Math.floor(origin.x);
    const oy = Math.floor(origin.y);
    const oz = Math.floor(origin.z);
    for (let x = -r; x <= r; x++)
        for (let y = -3; y <= 3; y++)
            for (let z = -r; z <= r; z++) {
                let b;
                try {
                    b = dim.getBlock({ x: ox + x, y: oy + y, z: oz + z });
                }
                catch {
                    continue;
                }
                if (!b)
                    continue;
                const t = b.typeId;
                if (t !== "minecraft:chest" && t !== "minecraft:barrel" && t !== "minecraft:trapped_chest")
                    continue;
                if (dist(b.location, origin) > radius + 0.5)
                    continue;
                try {
                    const cont = b.getComponent("minecraft:inventory")?.container;
                    if (cont)
                        out.push(cont);
                }
                catch {
                    /* ignore */
                }
            }
    return out;
}
/** Takes up to n items of typeId from nearby containers into helper storage. Returns amount moved. */
export function pullFromContainers(h, containers, typeId, n) {
    let moved = 0;
    for (const c of containers) {
        for (let i = 0; i < c.size && moved < n; i++) {
            const it = c.getItem(i);
            if (!it || it.typeId !== typeId)
                continue;
            const take = Math.min(it.amount, n - moved);
            const part = it.clone();
            part.amount = take;
            const left = storageAdd(h, part);
            const put = take - left;
            if (put <= 0)
                return moved;
            if (put >= it.amount)
                c.setItem(i, undefined);
            else {
                it.amount -= put;
                c.setItem(i, it);
            }
            moved += put;
        }
        if (moved >= n)
            break;
    }
    return moved;
}
/** Puts helper storage items (not the tool) into containers. Returns amount moved. */
export function depositToContainers(h, containers, keep) {
    const hc = getContainer(h);
    if (!hc)
        return 0;
    let moved = 0;
    for (let i = 1; i < hc.size; i++) {
        let it = hc.getItem(i);
        if (!it)
            continue;
        if (keep && keep(it))
            continue;
        for (const c of containers) {
            if (!it)
                break;
            const before = it.amount;
            let left;
            try {
                left = c.addItem(it);
            }
            catch {
                left = it;
            }
            const after = left ? left.amount : 0;
            moved += before - after;
            it = left;
        }
        hc.setItem(i, it && it.amount > 0 ? it : undefined);
    }
    return moved;
}
