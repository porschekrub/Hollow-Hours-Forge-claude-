const M = "minecraft:";
const ROLE_ITEMS = {
    kitchen: ["crafting_table", "furnace", "smoker", "blast_furnace", "cauldron", "barrel", "chest"],
    storage: ["chest", "barrel", "chest", "barrel", "chest"],
    workshop: ["anvil", "smithing_table", "grindstone", "stonecutter_block", "loom", "cartography_table", "fletching_table", "composter"],
    enchant: [],
    bedroom: ["lectern", "chest", "jukebox", "barrel"],
    brewing: ["brewing_stand", "cauldron"],
};
/** Which rooms go on which floor. */
export function rolesFor(floors, f) {
    if (floors === 1)
        return { roles: ["kitchen", "enchant", "workshop", "brewing", "bedroom", "storage"], beds: 1 };
    if (floors === 2) {
        return f === 0
            ? { roles: ["kitchen", "workshop", "storage"], beds: 0 }
            : { roles: ["enchant", "brewing", "bedroom", "storage"], beds: 2 };
    }
    if (f === 0)
        return { roles: ["kitchen", "storage"], beds: 0 };
    if (f === 1)
        return { roles: ["enchant", "workshop", "brewing"], beds: 0 };
    return { roles: ["bedroom", "storage"], beds: 2 };
}
const k2 = (x, z) => `${x},${z}`;
export function furnishRoom(r, put, has) {
    const used = new Set(); // cells with furniture
    const keep = new Set(r.reserved); // cells that must stay empty
    const y = r.y + 1;
    const inside = (x, z) => x >= r.x0 && x <= r.x1 && z >= r.z0 && z <= r.z1;
    const free = (x, z) => inside(x, z) && !used.has(k2(x, z)) && !keep.has(k2(x, z)) && !has(x, y, z);
    const place = (x, z, id, st, extra) => {
        put(x, y, z, id, 5, st, { opt: true, ...extra });
        used.add(k2(x, z));
    };
    const W = r.x1 - r.x0 + 1;
    const D = r.z1 - r.z0 + 1;
    // ---- enchanting nook: table with bookshelves exactly two blocks away (enough for level 30)
    let nookDone = false;
    if (r.roles.includes("enchant") && W >= 5 && D >= 5 && W * D >= 35) {
        for (const side of [0, 1]) {
            if (nookDone)
                break;
            const wallX = side === 0 ? r.x0 : r.x1;
            const dir = side === 0 ? 1 : -1;
            for (let zt = r.z1 - 2; zt >= r.z0 + 2 && !nookDone; zt--) {
                let ok = true;
                for (let i = 0; i <= 3 && ok; i++)
                    for (let dz = -2; dz <= 2 && ok; dz++)
                        if (!free(wallX + dir * i, zt + dz))
                            ok = false;
                if (!ok)
                    continue;
                for (let dz = -2; dz <= 2; dz++) {
                    place(wallX, zt + dz, M + "bookshelf");
                    put(wallX, y + 1, zt + dz, M + "bookshelf", 5, undefined, { opt: true });
                }
                for (let i = 1; i <= 3; i++) {
                    place(wallX + dir * i, zt - 2, M + "bookshelf");
                    place(wallX + dir * i, zt + 2, M + "bookshelf");
                    for (let dz = -1; dz <= 1; dz++)
                        keep.add(k2(wallX + dir * i, zt + dz));
                }
                keep.delete(k2(wallX + dir * 2, zt));
                place(wallX + dir * 2, zt, M + "enchanting_table");
                nookDone = true;
            }
        }
    }
    // ---- ring of wall-adjacent cells, walked around the room
    const ring = [];
    for (let x = r.x0; x <= r.x1; x++)
        ring.push({ x, z: r.z1, f: "nz", inX: 0, inZ: -1 });
    for (let z = r.z1 - 1; z >= r.z0; z--)
        ring.push({ x: r.x1, z, f: "nx", inX: -1, inZ: 0 });
    for (let x = r.x1 - 1; x >= r.x0; x--)
        ring.push({ x, z: r.z0, f: "pz", inX: 0, inZ: 1 });
    for (let z = r.z0 + 1; z <= r.z1 - 1; z++)
        ring.push({ x: r.x0, z, f: "px", inX: 1, inZ: 0 });
    // ---- beds: head against the wall
    let beds = r.beds;
    for (const s of ring) {
        if (beds <= 0)
            break;
        const fx = s.x + s.inX;
        const fz = s.z + s.inZ;
        if (!free(s.x, s.z) || !free(fx, fz))
            continue;
        // keep the cell in front of the foot free so the bed is reachable
        if (!inside(fx + s.inX, fz + s.inZ) || keep.has(k2(fx + s.inX, fz + s.inZ)))
            continue;
        const toHead = s.inX === 1 ? "nx" : s.inX === -1 ? "px" : s.inZ === 1 ? "nz" : "pz";
        place(fx, fz, M + "bed", { f: toHead, head: false }, { item: M + "bed" });
        place(s.x, s.z, M + "bed", { f: toHead, head: true }, { item: null, dep: [s.inX, 0, s.inZ] });
        keep.add(k2(fx + s.inX, fz + s.inZ));
        beds--;
    }
    // ---- single blocks along the walls, by priority
    const queue = [];
    for (const role of r.roles)
        for (const id of ROLE_ITEMS[role])
            queue.push(id);
    if (r.roles.includes("enchant") && !nookDone)
        queue.push("enchanting_table", "bookshelf", "bookshelf");
    const seen = new Set();
    const items = queue.filter((id) => {
        // one of each (except storage blocks and bookshelves)
        if (id === "chest" || id === "barrel" || id === "bookshelf" || id === "cauldron")
            return true;
        if (seen.has(id))
            return false;
        seen.add(id);
        return true;
    });
    let qi = 0;
    for (const s of ring) {
        if (qi >= items.length)
            break;
        if (!free(s.x, s.z))
            continue;
        const id = items[qi++];
        placeItem(place, s.x, s.z, id, s.f);
    }
    // ---- dining table in the kitchen, rug elsewhere
    const cx = Math.floor((r.x0 + r.x1) / 2);
    const cz = Math.floor((r.z0 + r.z1) / 2);
    if (r.roles.includes("kitchen") && W >= 5 && D >= 5 && free(cx, cz) && free(cx - 1, cz) && free(cx + 1, cz)) {
        place(cx, cz, r.wood.fence);
        put(cx, y + 1, cz, r.wood.plate, 5, undefined, { opt: true, dep: [0, -1, 0] });
        place(cx - 1, cz, r.wood.stairs, { f: "nx", half: "bottom" });
        place(cx + 1, cz, r.wood.stairs, { f: "px", half: "bottom" });
    }
    else {
        const half = W >= 7 && D >= 7 ? 1 : 0;
        for (let x = cx - half; x <= cx + half + (half ? 0 : 1); x++)
            for (let z = cz - half; z <= cz + half; z++)
                if (free(x, z))
                    place(x, z, r.carpet);
    }
}
function placeItem(place, x, z, id, f) {
    switch (id) {
        case "furnace":
        case "smoker":
        case "blast_furnace":
        case "chest":
        case "lectern":
        case "anvil":
        case "stonecutter_block":
        case "loom":
        case "grindstone":
            place(x, z, M + id, { f });
            break;
        default:
            place(x, z, M + id);
    }
}
