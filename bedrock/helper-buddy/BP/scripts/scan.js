import { BlockVolume } from "@minecraft/server";
import { safeBlock } from "./util.js";
/** How far (up/down) the helper looks. Horizontal view is `radius`, vertical view is capped to keep scans fast. */
const MAX_VERTICAL = 64;
/** Search tiers: close blocks are found with a cheap, small scan; the far scans only run when nothing is near. */
function tiers(radius) {
    const t = [];
    for (const r of [24, 48, 72, 100, 140]) {
        if (r < radius)
            t.push(r);
    }
    t.push(radius);
    return t;
}
function scanTier(dim, center, radius, types, typeSet, accept) {
    const cx = Math.floor(center.x);
    const cy = Math.floor(center.y);
    const cz = Math.floor(center.z);
    const vr = Math.min(radius, MAX_VERTICAL);
    let ymin = cy - vr;
    let ymax = cy + vr;
    try {
        ymin = Math.max(dim.heightRange.min, ymin);
        ymax = Math.min(dim.heightRange.max - 1, ymax);
    }
    catch {
        /* ignore */
    }
    const size = radius <= 32 ? 8 : 16;
    const cells = [];
    for (let x0 = cx - radius; x0 <= cx + radius; x0 += size)
        for (let y0 = ymin; y0 <= ymax; y0 += size)
            for (let z0 = cz - radius; z0 <= cz + radius; z0 += size) {
                const x1 = Math.min(x0 + size - 1, cx + radius);
                const y1 = Math.min(y0 + size - 1, ymax);
                const z1 = Math.min(z0 + size - 1, cz + radius);
                const px = Math.max(x0, Math.min(center.x, x1 + 1));
                const py = Math.max(y0, Math.min(center.y, y1 + 1));
                const pz = Math.max(z0, Math.min(center.z, z1 + 1));
                const dmin = Math.sqrt((px - center.x) ** 2 + (py - center.y) ** 2 + (pz - center.z) ** 2);
                if (dmin > radius)
                    continue;
                cells.push({ x0, y0, z0, x1, y1, z1, dmin });
            }
    cells.sort((a, b) => a.dmin - b.dmin);
    let best;
    let bestD = Infinity;
    let budget = 40000; // blocks inspected one by one (keeps a single scan from freezing the game)
    for (const c of cells) {
        if (best && c.dmin > bestD)
            break;
        if (budget <= 0)
            break;
        let any = false;
        try {
            // third argument = ignore chunks that are not loaded (no error, they simply count as empty)
            any = dim.containsBlock(new BlockVolume({ x: c.x0, y: c.y0, z: c.z0 }, { x: c.x1, y: c.y1, z: c.z1 }), { includeTypes: types }, true);
        }
        catch {
            any = false; // not loaded / not readable: skip (the old version scanned every block here, which is very slow)
        }
        if (!any)
            continue;
        for (let x = c.x0; x <= c.x1; x++)
            for (let y = c.y0; y <= c.y1; y++)
                for (let z = c.z0; z <= c.z1; z++) {
                    const d = Math.sqrt((x + 0.5 - center.x) ** 2 + (y + 0.5 - center.y) ** 2 + (z + 0.5 - center.z) ** 2);
                    if (d > radius || d >= bestD)
                        continue;
                    budget--;
                    const b = safeBlock(dim, { x, y, z });
                    if (!b || !typeSet.has(b.typeId))
                        continue;
                    if (!accept(b))
                        continue;
                    best = b;
                    bestD = d;
                }
    }
    return best;
}
/**
 * Finds the nearest block of the given types within `radius` of `center`.
 * v1.6: searches in growing rings (24, 48, 72, 100 blocks) so near blocks are found fast and far ones only when needed.
 */
export function scanNearest(dim, center, radius, types, accept) {
    if (!types.length)
        return undefined;
    const typeSet = new Set(types);
    for (const r of tiers(radius)) {
        const found = scanTier(dim, center, r, types, typeSet, accept);
        if (found)
            return found;
    }
    return undefined;
}
