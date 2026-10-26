// v1.6: pixel statues / models and the nether portal.
// A statue is a sprite (ASCII picture, drawn as seen from the front) extruded into the depth; size 2 doubles every pixel.
import { M, clamp, ctx, finalize, variantOf } from "./house_common.js";
export const STATUE_TYPES = [
    { id: "statue", th: "รูปปั้น / โมเดลพิกเซล (ครีปเปอร์ สตีฟ หมู หัวใจ ดาบ ฯลฯ)" },
    { id: "portal", th: "ประตูเนเธอร์ (ต้องใช้ออบซิเดียน 10 + เหล็กไฟ)" },
];
/** char -> block id */
const CH = {
    G: "lime_concrete", g: "green_concrete", K: "black_concrete", W: "white_concrete", R: "red_concrete", P: "pink_concrete",
    B: "blue_concrete", C: "cyan_concrete", Y: "yellow_concrete", O: "orange_concrete", N: "brown_concrete", E: "gray_concrete",
    S: "light_gray_concrete", D: "diamond_block", L: "lime_concrete", T: "oak_log", A: "light_blue_concrete", M: "magenta_concrete",
    U: "purple_concrete", F: "glowstone", X: "gold_block",
};
export const MODELS = [
    {
        id: "creeper", th: "ครีปเปอร์", re: /(ครีปเปอร์|ครีปเปอ|creeper|คริปเปอร์)/,
        rows: [
            "GGGGGGGG", "GGGGGGGG", "GKKGGKKG", "GKKGGKKG", "GGGKKGGG", "GGKKKKGG", "GGKKKKGG", "GGKGGKGG",
            "..GGGG..", "..GGGG..", "..GGGG..", "..GGGG..", ".GGGGGG.", ".GGGGGG.", ".GG..GG.", ".GG..GG.",
        ],
    },
    {
        id: "steve", th: "สตีฟ", re: /(สตีฟ|steve|ผู้เล่น|player|คน)/,
        rows: [
            ".NNNNNN.", ".NNNNNN.", ".NOOOOO.", ".OWBOBWO", ".OOOOOO.", ".OONNOO.",
            "..OOOO..", "ACCCCCCA", "OCCCCCCO", "OCCCCCCO", "OCCCCCCO", "O.CCCC.O", "..BBBB..", "..BBBB..", "..BB.BB.", "..BB.BB.", "..EE.EE.",
        ],
    },
    {
        id: "zombie", th: "ซอมบี้", re: /(ซอมบี้|zombie)/,
        rows: [
            "..gggg..", ".gggggg.", ".gKggKg.", ".gggggg.", ".ggKKgg.", "..gggg..", "CCCCCCCC", "gCCCCCCg", "gCCCCCCg", "g.CCCC.g",
            "..BBBB..", "..BB.BB.", "..BB.BB.", "..EE.EE.",
        ],
    },
    {
        id: "pig", th: "หมู", re: /(หมู|pig)/,
        rows: [
            ".PPPPPP.", "PPPPPPPP", "PKPPPPKP", "PPPPPPPP", "PPPNNPPP", "PPPKKPPP", "PPPPPPPP", ".PPPPPP.", "..P..P..", "..P..P..",
        ],
    },
    {
        id: "heart", th: "หัวใจ", re: /(หัวใจ|heart|love)/,
        rows: [
            ".RRR...RRR.", "RRRRR.RRRRR", "RRWRRRRRRRR", "RRRRRRRRRRR", "RRRRRRRRRRR", ".RRRRRRRRR.", "..RRRRRRR..", "...RRRRR...", "....RRR....", ".....R.....",
        ],
    },
    {
        id: "sword", th: "ดาบ", re: /(ดาบ|sword)/,
        rows: [
            ".......DD", "......DDD", ".....DDD.", "....DDD..", "...DDD...", "N.DDD....", ".NDD.....", "..NN.....", ".NN.N....", "NN...N...",
        ],
    },
    {
        id: "tree", th: "ต้นไม้", re: /(ต้นไม้|tree)/,
        rows: [
            "...GGG...", "..GGGGG..", ".GGGGGGG.", "GGGGGGGGG", ".GGGGGGG.", "GGGGGGGGG", ".GGGGGGG.", "..GGGGG..", "....N....", "....N....", "....N....", "...NNN...",
        ],
    },
    {
        id: "diamond", th: "เพชร", re: /(เพชร|diamond|ไดมอนด์)/,
        rows: [
            "..AAAAA..", ".AAWAAAA.", "AAWAAAAAA", "AAAAAAAAA", ".AAAAAAA.", "..AAAAA..", "...AAA...", "....A....",
        ],
    },
    {
        id: "mushroom", th: "เห็ด", re: /(เห็ด|mushroom)/,
        rows: [
            "..RRRRR..", ".RRWRRWR.", "RRRRRRRRR", "RWRRRRRWR", "RRRRRRRRR", "...WWW...", "...WWW...", "...WWW...", "..WWWWW..",
        ],
    },
    {
        id: "star", th: "ดาว", re: /(ดาว|star)/,
        rows: [
            "....Y....", "....Y....", "...YYY...", "YYYYYYYYY", ".YYYYYYY.", "..YYYYY..", "..YYYYY..", ".YYY.YYY.", ".YY...YY.",
        ],
    },
    {
        id: "snowman", th: "มนุษย์หิมะ", re: /(หิมะ|snowman|snow golem|โกเลม)/,
        rows: [
            "..NNNNN..", "...NNN...", "..WWWWW..", "..WKWKW..", "..WWOWW..", "..WWWWW..", ".WWWWWWW.", "NWWKWWWWN", "NWWWWWWWN", "..WKWWW..", ".WWWWWWW.", ".WWWWWWW.", "..WWWWW..",
        ],
    },
    {
        id: "cat", th: "แมว", re: /(แมว|cat\b)/,
        rows: [
            "O.O...O.O", "OOO...OOO", "OOOOOOOOO", "OKOOOOOKO", "OOOOPOOOO", "OOOKOKOOO", ".OOOOOOO.", ".OOOOOOO.", "OOOOOOOOO", "OO.....OO",
        ],
    },
];
export function modelOf(o) {
    if (typeof o.model === "number" && MODELS[o.model])
        return MODELS[o.model];
    return MODELS[variantOf(o) % MODELS.length];
}
export function modelName(o) {
    return modelOf(o).th;
}
function statue(o) {
    const m = modelOf(o);
    const scale = clamp(Math.floor(o.size ?? 1), 0, 2) === 2 ? 2 : 1;
    const depth = (m.id === "heart" || m.id === "star" || m.id === "sword" || m.id === "diamond" ? 2 : 4) * (scale === 2 ? 2 : 1);
    const rows = m.rows;
    const spriteW = Math.max(...rows.map((r) => r.length)) * scale;
    const spriteH = rows.length * scale;
    const margin = 2;
    const W = spriteW + margin * 2;
    const D = depth + margin * 2;
    const { map, put } = ctx();
    // pedestal
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++) {
            const edge = x === 0 || x === W - 1 || z === 0 || z === D - 1;
            put(x, 0, z, edge ? M + "stone_bricks" : M + "polished_andesite", 1);
        }
    for (let x = 1; x < W - 1; x++)
        for (let z = 1; z < D - 1; z++)
            put(x, 1, z, M + "smooth_stone", 1);
    const y0 = 2;
    for (let r = 0; r < rows.length; r++) {
        const row = rows[r];
        for (let cx = 0; cx < row.length; cx++) {
            const chr = row[cx];
            if (chr === "." || chr === " ")
                continue;
            const id = M + (CH[chr] ?? "stone");
            for (let sy = 0; sy < scale; sy++)
                for (let sx = 0; sx < scale; sx++)
                    for (let z = 0; z < depth; z++) {
                        const x = margin + cx * scale + sx;
                        const y = y0 + (rows.length - 1 - r) * scale + sy;
                        // hollow inside to keep it light? no: solid, it is only a few thousand blocks
                        put(x, y, margin + z, id, 1);
                    }
        }
    }
    // little lights at the foot
    for (const x of [1, W - 2])
        put(x, 2, 1, M + "lantern", 4, { hanging: false });
    void spriteH;
    return finalize(map, W, D, { fillBlock: M + "stone_bricks", clearArea: { x0: -1, x1: W, z0: -1, z1: D } });
}
// ------------------------------------------------------------------ nether portal (needs real materials)
function portal(o) {
    const s = clamp(Math.floor(o.size ?? 1), 0, 2);
    const iw = [2, 3, 4][s]; // inner width 2..4 (the vanilla minimum is 2 x 3)
    const ih = [3, 4, 5][s]; // inner height
    const W = iw + 2 + 4;
    const D = 5;
    const { map, put } = ctx();
    const x0 = 2; // frame left
    const x1 = x0 + iw + 1; // frame right
    const z = 2;
    // low platform (free decoration)
    for (let x = 0; x < W; x++)
        for (let zz = 0; zz < D; zz++)
            put(x, 0, zz, M + ((x + zz) % 2 === 0 ? "polished_blackstone_bricks" : "blackstone"), 1, undefined, { item: null });
    // obsidian frame (corners are not needed, like in the real game)
    for (let x = x0; x <= x1; x++) {
        for (let y = 1; y <= ih + 2; y++) {
            const edge = x === x0 || x === x1 || y === 1 || y === ih + 2;
            if (!edge)
                continue;
            const corner = (x === x0 || x === x1) && (y === 1 || y === ih + 2);
            if (corner)
                continue;
            put(x, y, z, M + "obsidian", 1);
        }
    }
    // the first portal block needs flint & steel; the rest follow from it
    const fx = x0 + 1, fy = 2;
    for (let x = x0 + 1; x <= x1 - 1; x++)
        for (let y = 2; y <= ih + 1; y++) {
            const first = x === fx && y === fy;
            put(x, y, z, M + "portal", 6, { axis: "x" }, first ? { item: M + "flint_and_steel" } : { item: null, dep: [fx - x, fy - y, 0] });
        }
    for (const x of [x0 - 1, x1 + 1])
        put(x, 1, z, M + "lantern", 4, { hanging: false }, { item: null });
    return finalize(map, W, D, { fillBlock: M + "blackstone", needsItems: true, clearArea: { x0: -1, x1: W, z0: -1, z1: D } });
}
export function generateStatue(o) {
    return o.type === "portal" ? portal(o) : statue(o);
}
