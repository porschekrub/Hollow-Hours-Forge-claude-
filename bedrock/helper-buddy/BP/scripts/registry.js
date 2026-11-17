import { world } from "@minecraft/server";
import { getOwnerId, getOwnerName } from "./state.js";
import { HELPER_ID } from "./util.js";
/** All helper entities currently loaded in the world. */
export const helpers = new Map();
export function track(e) {
    try {
        if (e.isValid && e.typeId === HELPER_ID)
            helpers.set(e.id, e);
    }
    catch {
        /* ignore */
    }
}
export function rescan() {
    for (const id of ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"]) {
        try {
            for (const e of world.getDimension(id).getEntities({ type: HELPER_ID }))
                track(e);
        }
        catch {
            /* ignore */
        }
    }
}
/** Helpers that belong to a player (any distance, any dimension). */
export function ownedHelpers(p) {
    const out = [];
    for (const [id, h] of helpers) {
        try {
            if (!h.isValid) {
                helpers.delete(id);
                continue;
            }
            if (getOwnerId(h) === p.id || getOwnerName(h) === p.name)
                out.push(h);
        }
        catch {
            /* ignore */
        }
    }
    return out;
}
const lastPick = new Map();
/** The helper a player talked to / selected last. */
export function rememberHelper(p, h) {
    lastPick.set(p.id, h.id);
}
export function lastHelper(p) {
    const id = lastPick.get(p.id);
    const h = id ? helpers.get(id) : undefined;
    return h && h.isValid ? h : undefined;
}
