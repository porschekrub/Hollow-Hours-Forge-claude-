package com.helperbuddy.gen;

import com.helperbuddy.gen.Gen.Base;
import com.helperbuddy.gen.Gen.Cell;
import com.helperbuddy.gen.Gen.Ctx;
import com.helperbuddy.gen.Gen.Ex;
import com.helperbuddy.gen.Gen.Opts;
import com.helperbuddy.gen.Gen.Pal;
import com.helperbuddy.gen.Gen.Plan;
import com.helperbuddy.gen.Gen.St;
import com.helperbuddy.gen.Gen.Wood;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import static com.helperbuddy.gen.Gen.M;

/** Extra building types: underground, tower, castle, treehouse, mansion, safehouse, lighthouse, igloo, desert, pyramid, nether, box. */
public final class ExtraGen {
    private ExtraGen() {}

    /** id, Thai name, English name. */
    public static final String[][] TYPES = {
            {"standard", "บ้านทั่วไป", "House"},
            {"underground", "บ้านใต้ดิน", "Underground house"},
            {"tower", "หอคอย", "Tower"},
            {"castle", "ปราสาท", "Castle"},
            {"treehouse", "บ้านต้นไม้", "Tree house"},
            {"mansion", "คฤหาสน์", "Mansion"},
            {"safehouse", "บ้านหลบภัย", "Safe house"},
            {"lighthouse", "ประภาคาร", "Lighthouse"},
            {"igloo", "อิกลู", "Igloo"},
            {"desert", "บ้านทะเลทราย", "Desert house"},
            {"pyramid", "ปิรามิด", "Pyramid"},
            {"nether", "ป้อมเนเธอร์", "Nether fort"},
            {"box", "บ้านทรงกล่อง", "Box house"},
            {"hotel", "โรงแรม", "Hotel"},
            {"hospital", "โรงพยาบาล", "Hospital"},
            {"prison", "คุก", "Prison"},
            {"school", "โรงเรียน", "School"},
            {"library", "ห้องสมุด", "Library"},
            {"church", "โบสถ์", "Church"},
            {"barn", "โรงนา", "Barn"},
            {"market", "ตลาด", "Market"},
            {"statue", "รูปปั้น", "Statue"},
            {"portal", "ประตูเนเธอร์", "Nether portal"},
    };

    public static final String[][] MATS = {
            {"auto", "ตามประเภท", "automatic"}, {"stone", "อิฐหิน", "stone bricks"}, {"wood", "ไม้", "wood"},
            {"brick", "อิฐแดง", "red bricks"}, {"sand", "หินทราย", "sandstone"}, {"deepslate", "ดีพสเลท", "deepslate"},
            {"blackstone", "แบล็กสโตน", "blackstone"}, {"quartz", "ควอตซ์ขาว", "quartz"}, {"mud", "อิฐโคลน", "mud bricks"},
            {"prismarine", "ปริซึมารีน", "prismarine"},
    };

    static final Map<String, String> DEFAULT_MAT = new LinkedHashMap<>();

    static {
        String[][] d = {{"underground", "stone"}, {"tower", "stone"}, {"castle", "stone"}, {"treehouse", "wood"}, {"igloo", "snow"},
                {"desert", "sand"}, {"pyramid", "sand"}, {"nether", "blackstone"}, {"box", "stone"}, {"mansion", "quartz"},
                {"safehouse", "deepslate"}, {"lighthouse", "quartz"}};
        for (String[] e : d) DEFAULT_MAT.put(e[0], e[1]);
    }

    public static boolean isType(String id) {
        for (String[] t : TYPES) if (t[0].equals(id)) return true;
        return false;
    }

    public static boolean isExtraType(Opts o) {
        return o != null && o.type != null && !o.type.equals("standard") && isType(o.type);
    }

    public static String typeName(String id, boolean th) {
        for (String[] t : TYPES) if (t[0].equals(id)) return th ? t[1] : t[2];
        return th ? "บ้าน" : "House";
    }

    /** Name of the chosen variant, or null when the type has only one look. */
    public static String variantName(Opts o, boolean th) {
        int v = Gen.variantOf(o);
        if ("underground".equals(o.type)) return BunkerGen.VARIANTS[v % BunkerGen.VARIANTS.length][th ? 0 : 1];
        if (MoreGen.isMoreType(o.type)) {
            String[][] vs = MoreGen.VARIANTS.get(o.type);
            return vs[v % vs.length][th ? 0 : 1];
        }
        if ("statue".equals(o.type)) return StatueGen.modelOf(o).name(th);
        return null;
    }

    public static int variantCount(String type) {
        if ("underground".equals(type)) return BunkerGen.VARIANTS.length;
        if ("statue".equals(type)) return StatueGen.MODELS.length;
        if (MoreGen.isMoreType(type)) return MoreGen.VARIANTS.get(type).length;
        return 1;
    }

    static int dim(Integer v, int def) {
        return Gen.dim(v, def, 5, 31);
    }

    static final Map<String, String[]> P = new LinkedHashMap<>();

    static {
        P.put("stone", new String[] {"stone_bricks", "chiseled_stone_bricks", "stone_brick_stairs", "stone_brick_slab"});
        P.put("brick", new String[] {"brick_block", "stone_bricks", "brick_stairs", "brick_slab"});
        P.put("sand", new String[] {"cut_sandstone", "chiseled_sandstone", "sandstone_stairs", "sandstone_slab"});
        P.put("deepslate", new String[] {"deepslate_bricks", "polished_deepslate", "deepslate_brick_stairs", "deepslate_brick_slab"});
        P.put("blackstone", new String[] {"polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab"});
        P.put("quartz", new String[] {"quartz_block", "chiseled_quartz_block", "quartz_stairs", "quartz_slab"});
        P.put("mud", new String[] {"mud_bricks", "packed_mud", "mud_brick_stairs", "mud_brick_slab"});
        P.put("prismarine", new String[] {"prismarine_bricks", "dark_prismarine", "prismarine_brick_stairs", "prismarine_brick_slab"});
        P.put("snow", new String[] {"snow", "packed_ice", "spruce_stairs", "spruce_slab"});
    }

    static Pal palette(Opts o, String type, String wid, Wood wd) {
        int mi = o.matOr0();
        String m = mi >= 0 && mi < MATS.length ? MATS[mi][0] : "auto";
        if (m.equals("auto")) m = DEFAULT_MAT.getOrDefault(type, "stone");
        if (m.equals("wood")) {
            String logId = wid.equals("oak") || wid.equals("birch") ? "spruce" : "dark_oak";
            return new Pal(wd.planks, M + logId + "_log", wd.stairs, wd.slab, null, true);
        }
        String[] q = P.getOrDefault(m, P.get("stone"));
        return new Pal(M + q[0], M + q[1], M + q[2], M + q[3], null, false);
    }

    static Gen.Roles rolesForFloor(int F, int f) {
        if (F <= 3) return Gen.rolesFor(F, f);
        if (f == 0) return Gen.rolesFor(3, 0);
        if (f == 1) return Gen.rolesFor(3, 1);
        if (f == F - 1) return Gen.rolesFor(3, 2);
        return new Gen.Roles(new String[] {"storage", "workshop"}, 0);
    }

    // ------------------------------------------------------------------ above-ground box houses
    static Plan boxHouse(Opts o, String type) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        Pal P = palette(o, type, wid, wd);
        int s = Gen.clamp(o.sizeOr(1), 0, 2);
        int F = Gen.clamp(o.floorsOr(2), 1, 8);
        int W, D;
        if (type.equals("tower")) {
            W = D = dim(o.w, new int[] {5, 7, 9}[s]);
            if (o.height != null && o.height != 0) F = (int) Math.ceil(o.height / 4.0);
            else if (!Opts.on(o.floorsExact)) F = F * 2;
            F = Math.min(15, F);
        } else if (type.equals("castle") || type.equals("nether")) {
            W = dim(o.w, new int[] {9, 11, 13}[s]);
            D = dim(o.d, new int[] {9, 13, 15}[s]);
            F = Math.min(4, F);
        } else {
            W = dim(o.w, new int[] {7, 9, 11}[s]);
            D = dim(o.d, new int[] {7, 11, 13}[s]);
            F = Math.min(5, F);
        }
        final int S = 0;
        IntUnaryOperator yf = f -> S + 4 * f;
        int c = (W - 1) / 2;
        Ctx ctx = new Ctx();
        final int fW = W, fD = D;
        java.util.function.BiPredicate<Integer, Integer> isPerim = (x, z) -> x == 0 || x == fW - 1 || z == 0 || z == fD - 1;
        java.util.function.BiPredicate<Integer, Integer> isCorner = (x, z) -> (x == 0 || x == fW - 1) && (z == 0 || z == fD - 1);
        boolean slit = type.equals("castle") || type.equals("nether") || type.equals("tower");
        String glass = slit ? M + "iron_bars" : M + "glass_pane";
        int ladderX = W - 2, ladderZ = D - 2;
        String roofFull = P.wood ? wd.planks : P.wall;
        int topY = yf.applyAsInt(F);
        for (int f = 0; f < F; f++) {
            int y0 = yf.applyAsInt(f);
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (f > 0 && x == ladderX && z == ladderZ) continue;
                    if (isPerim.test(x, z)) ctx.put(x, y0, z, isCorner.test(x, z) ? P.pillar : P.wall, 1, isCorner.test(x, z) && P.wood ? St.axis("y") : null);
                    else ctx.put(x, y0, z, wd.planks, 1);
                }
            for (int row = 1; row <= 3; row++) {
                for (int x = 0; x < W; x++)
                    for (int z = 0; z < D; z++) {
                        if (!isPerim.test(x, z)) continue;
                        int y = y0 + row;
                        if (isCorner.test(x, z)) {
                            ctx.put(x, y, z, P.pillar, 1, P.wood ? St.axis("y") : null);
                            continue;
                        }
                        boolean win = false;
                        if (row == 2) {
                            if (z == 0) win = x >= 2 && x <= W - 3 && Math.abs(x - c) >= 2 && x % 2 == 0;
                            else if (z == D - 1) win = x >= 2 && x <= W - 3 && x % 3 == 1;
                            else win = z >= 2 && z <= D - 3 && z % 3 == 1;
                        }
                        ctx.put(x, y, z, win ? glass : P.wall, 1);
                    }
            }
        }
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                ctx.put(x, topY, z, isCorner.test(x, z) ? P.pillar : isPerim.test(x, z) ? P.wall : roofFull, 1, isCorner.test(x, z) && P.wood ? St.axis("y") : null);
        if (type.equals("tower") || type.equals("box")) {
            Gen.hipRoof(ctx, 0, W - 1, 0, D - 1, topY + 1, P.stairs, roofFull);
        } else if (type.equals("castle") || type.equals("nether")) {
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++)
                    if (isPerim.test(x, z) && (isCorner.test(x, z) || (x + z) % 2 == 0)) ctx.put(x, topY + 1, z, P.wall, 2);
            for (int[] p : new int[][] {{0, 0}, {W - 1, 0}, {0, D - 1}, {W - 1, D - 1}}) ctx.put(p[0], topY + 2, p[1], P.wall, 2);
        } else {
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++)
                    if (isPerim.test(x, z)) ctx.put(x, topY + 1, z, P.slab, 2);
        }
        if (type.equals("castle") || type.equals("nether")) {
            int H = topY + 3;
            for (int[] cxz : new int[][] {{0, 0}, {W - 1, 0}, {0, D - 1}, {W - 1, D - 1}}) {
                int cx = cxz[0], cz = cxz[1];
                for (int x = cx - 1; x <= cx + 1; x++)
                    for (int z = cz - 1; z <= cz + 1; z++)
                        for (int y = 0; y <= H; y++) ctx.put(x, y, z, x == cx && z == cz ? P.pillar : P.wall, 1);
                for (int x = cx - 1; x <= cx + 1; x++)
                    for (int z = cz - 1; z <= cz + 1; z++) {
                        boolean eX = x != cx, eZ = z != cz;
                        if (eX && eZ) ctx.put(x, H + 1, z, P.wall, 2);
                        else if (eX) ctx.put(x, H + 1, z, P.stairs, 2, St.fh(x < cx ? "px" : "nx", "bottom"));
                        else if (eZ) ctx.put(x, H + 1, z, P.stairs, 2, St.fh(z < cz ? "pz" : "nz", "bottom"));
                        else ctx.put(x, H + 1, z, P.wall, 2);
                    }
                ctx.put(cx, H + 2, cz, P.slab, 2);
                for (int[] dd : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                    int px = cx + dd[0], pz = cz + dd[1];
                    if (px < 0 || px > W - 1 || pz < 0 || pz > D - 1) {
                        ctx.put(px, 3, pz, M + "iron_bars", 1);
                        ctx.put(px, topY + 2, pz, M + "iron_bars", 1);
                    }
                }
            }
        }
        Gen.door(ctx, wd, c, yf.applyAsInt(0) + 1, 0, "pz");
        ctx.put(c, 0, -1, P.stairs, 3, St.fh("pz", "bottom"));
        if (Opts.notOff(o.porch)) {
            for (int x : new int[] {c - 2, c + 2})
                for (int y = 0; y <= 1; y++) ctx.put(x, y, -1, P.pillar, 3, P.wood ? St.axis("y") : null);
            ctx.put(c - 2, 2, -1, M + "lantern", 4, St.hanging(false));
            ctx.put(c + 2, 2, -1, M + "lantern", 4, St.hanging(false));
        }
        for (int f = 0; f < F - 1; f++)
            for (int y = yf.applyAsInt(f) + 1; y <= yf.applyAsInt(f + 1); y++) ctx.put(ladderX, y, ladderZ, M + "ladder", 3, St.f("nz"));
        if (Opts.notOff(o.light)) {
            int[] zs = D <= 7 ? new int[] {D / 2} : new int[] {D / 3, (2 * D) / 3};
            for (int f = 0; f < F; f++)
                for (int z : zs) ctx.put(c, yf.applyAsInt(f) + 3, z, M + "lantern", 4, St.hanging(true));
        }
        if (Opts.notOff(o.furniture)) {
            for (int f = 0; f < F; f++) {
                Set<String> reserved = new java.util.LinkedHashSet<>();
                if (f == 0) {
                    reserved.add(c + ",1");
                    reserved.add(c + ",2");
                }
                if (F > 1) {
                    reserved.add(ladderX + "," + ladderZ);
                    reserved.add(ladderX + "," + (ladderZ - 1));
                }
                if (type.equals("castle") || type.equals("nether")) {
                    for (int[] p : new int[][] {{1, 1}, {W - 2, 1}, {1, D - 2}, {W - 2, D - 2}}) reserved.add(p[0] + "," + p[1]);
                }
                Gen.Roles rr = rolesForFloor(F, f);
                Gen.furnishRoom(new Gen.Room(yf.applyAsInt(f), 1, W - 2, 1, D - 2, reserved, rr.roles(), W >= 9 ? rr.beds() : Math.min(rr.beds(), 1),
                        M + (type.equals("castle") || type.equals("nether") ? "red_carpet" : "white_carpet"), wd), ctx);
            }
        }
        String fillBlock = P.wall.equals(wd.planks) ? M + "cobblestone" : P.wall;
        return Gen.finalize(ctx, W, D, Base.of().fill(fillBlock));
    }

    // ------------------------------------------------------------------ underground
    static Plan underground(Opts o) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        Pal P = palette(o, "underground", wid, wd);
        int s = Gen.clamp(o.sizeOr(1), 0, 2);
        int W = dim(o.w, new int[] {7, 9, 11}[s]);
        int D = dim(o.d, new int[] {7, 11, 13}[s]);
        int F = Gen.clamp(o.floorsOr(1), 1, 5);
        int c = (W - 1) / 2;
        Ctx ctx = new Ctx();
        IntUnaryOperator y0f = f -> -2 - 4 * (F - f);
        java.util.function.BiPredicate<Integer, Integer> isPerim = (x, z) -> x == 0 || x == W - 1 || z == 0 || z == D - 1;
        java.util.function.BiPredicate<Integer, Integer> isCorner = (x, z) -> (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
        int ladderX = W - 2, ladderZ = D - 2;
        List<Cell> clearCells = new ArrayList<>();
        int lowest = y0f.applyAsInt(0);
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                for (int y = lowest; y <= -2; y++) clearCells.add(new Cell(x, y, z));
        for (int f = 0; f < F; f++) {
            int y0 = y0f.applyAsInt(f);
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (f > 0 && x == ladderX && z == ladderZ) continue;
                    ctx.put(x, y0, z, isPerim.test(x, z) ? P.wall : wd.planks, 1);
                }
            for (int row = 1; row <= 3; row++)
                for (int x = 0; x < W; x++)
                    for (int z = 0; z < D; z++)
                        if (isPerim.test(x, z))
                            ctx.put(x, y0 + row, z, isCorner.test(x, z) ? P.pillar : P.wall, 1, isCorner.test(x, z) && P.wood ? St.axis("y") : null);
        }
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) ctx.put(x, -2, z, isPerim.test(x, z) ? P.wall : wd.planks, 1);
        for (int f = 0; f < F - 1; f++)
            for (int y = y0f.applyAsInt(f) + 1; y <= y0f.applyAsInt(f + 1); y++) ctx.put(ladderX, y, ladderZ, M + "ladder", 3, St.f("nz"));
        int yTop = y0f.applyAsInt(F - 1);
        int steps = -1 - yTop;
        int zs = -1 - steps;
        for (int k = 0; k <= steps; k++) {
            int z = zs + k;
            int y = -1 - k;
            ctx.put(c, y, z, P.stairs, 1, St.fh("nz", "bottom"));
            for (int yy = y + 1; yy <= y + 3; yy++) clearCells.add(new Cell(c, yy, z));
            if (k % 2 == 1) {
                ctx.put(c - 1, y + 2, z, M + "sea_lantern", 4);
                ctx.put(c + 1, y + 2, z, M + "sea_lantern", 4);
            }
        }
        Gen.door(ctx, wd, c, yTop + 1, 0, "pz");
        for (int dx : new int[] {-1, 1})
            for (int y = 0; y <= 2; y++) ctx.put(c + dx, y, zs, P.pillar, 3, P.wood ? St.axis("y") : null);
        for (int dx = -1; dx <= 1; dx++) ctx.put(c + dx, 3, zs, P.wall, 3);
        ctx.put(c, 2, zs, M + "lantern", 4, St.hanging(true));
        if (Opts.notOff(o.light)) {
            int[] lz = D <= 7 ? new int[] {D / 2} : new int[] {D / 3, (2 * D) / 3};
            for (int f = 0; f < F; f++)
                for (int z : lz)
                    for (int x : W >= 9 ? new int[] {c - 2, c + 2} : new int[] {c}) ctx.put(x, y0f.applyAsInt(f) + 4, z, M + "sea_lantern", 4);
        }
        if (Opts.notOff(o.furniture)) {
            for (int f = 0; f < F; f++) {
                Set<String> reserved = new java.util.LinkedHashSet<>();
                if (f == F - 1) {
                    reserved.add(c + ",1");
                    reserved.add(c + ",2");
                }
                if (F > 1) {
                    reserved.add(ladderX + "," + ladderZ);
                    reserved.add(ladderX + "," + (ladderZ - 1));
                }
                Gen.Roles rr = rolesForFloor(F, F - 1 - f);
                Gen.furnishRoom(new Gen.Room(y0f.applyAsInt(f), 1, W - 2, 1, D - 2, reserved, rr.roles(), W >= 9 ? rr.beds() : Math.min(rr.beds(), 1), M + "gray_carpet", wd), ctx);
            }
        }
        return Gen.finalize(ctx, W, D, Base.of().clear(0, -1, 0, -1).cells(clearCells).fill(M + "cobblestone").under());
    }

    // ------------------------------------------------------------------ igloo
    static Plan igloo(Opts o) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        int s = Gen.clamp(o.sizeOr(1), 0, 2);
        int r = new int[] {4, 5, 7}[s];
        int L = 3;
        int W = 2 * r + 1;
        int D = 2 * r + 1 + L;
        int cx = r;
        int cz = r + L;
        Ctx ctx = new Ctx();
        String SNOW = M + "snow";
        for (int x = 0; x < W; x++)
            for (int z = L; z < D; z++)
                for (int y = 0; y <= r; y++) {
                    int dx = x - cx, dz = z - cz;
                    double d = Math.sqrt(dx * dx + dz * dz + y * y);
                    if (y == 0) {
                        if (d <= r + 0.3) ctx.put(x, 0, z, d <= r - 1 ? wd.planks : SNOW, 1);
                    } else if (d <= r + 0.3 && d > r - 1) ctx.put(x, y, z, SNOW, 1);
                }
        for (int[] a : new int[][] {{-1, 0}, {1, 0}, {0, 1}})
            for (int yy : new int[] {2, 3}) {
                int x = (int) Gen.jsRound(cx + a[0] * (r - 0.5)), z = (int) Gen.jsRound(cz + a[1] * (r - 0.5));
                if (ctx.has(x, yy, z)) ctx.put(x, yy, z, M + "glass", 1);
            }
        for (int z = 0; z < L; z++) {
            ctx.put(cx, 0, z, wd.planks, 1);
            for (int dx : new int[] {-1, 1})
                for (int y = 0; y <= 2; y++) ctx.put(cx + dx, y, z, SNOW, 1);
            for (int dx = -1; dx <= 1; dx++) ctx.put(cx + dx, 3, z, SNOW, 1);
        }
        for (int y = 1; y <= 2; y++) ctx.del(cx, y, L);
        Gen.door(ctx, wd, cx, 1, L, "pz");
        ctx.put(cx, 0, -1, M + "spruce_stairs", 3, St.fh("pz", "bottom"));
        if (Opts.notOff(o.light)) {
            ctx.put(cx, r - 1, cz, M + "lantern", 4, St.hanging(true));
            ctx.put(cx - 1, 2, 0, M + "lantern", 4, St.hanging(false));
        }
        if (Opts.notOff(o.furniture)) {
            int h = Math.max(2, (int) Math.floor((r - 1) / 1.5));
            Set<String> reserved = Gen.keys(cx + "," + (L + 1), cx + "," + (L + 2));
            Gen.Roles rr = Gen.rolesFor(1, 0);
            Gen.furnishRoom(new Gen.Room(0, cx - h, cx + h, cz - h, cz + h, reserved, rr.roles(), 1, M + "light_blue_carpet", wd), ctx);
        }
        return Gen.finalize(ctx, W, D, Base.of().door(cx).clear(0, W - 1, 0, D - 1).fill(M + "dirt"));
    }

    // ------------------------------------------------------------------ pyramid
    static Plan pyramid(Opts o) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        Pal P = palette(o, "pyramid", wid, wd);
        int s = Gen.clamp(o.sizeOr(1), 0, 2);
        int N = new int[] {13, 17, 23}[s];
        int c = (N - 1) / 2;
        Ctx ctx = new Ctx();
        int layers = (int) Math.ceil(N / 2.0);
        for (int y = 0; y < layers; y++)
            for (int x = 0; x < N; x++)
                for (int z = 0; z < N; z++) {
                    int i = Math.min(Math.min(x, N - 1 - x), Math.min(z, N - 1 - z));
                    if (i < y) continue;
                    if (y == 0) ctx.put(x, 0, z, i <= 1 ? P.wall : wd.planks, 1);
                    else if (i <= y + 1 || y >= layers - 2) ctx.put(x, y, z, y % 4 == 0 ? P.pillar : P.wall, 1);
                }
        for (int z = 0; z <= 4; z++)
            for (int y = 1; y <= 3; y++) ctx.del(c, y, z);
        for (int z = 0; z <= 4; z++) {
            for (int dx : new int[] {-1, 1})
                for (int y = 1; y <= 3; y++)
                    if (!ctx.has(c + dx, y, z)) ctx.put(c + dx, y, z, P.wall, 1);
            ctx.put(c, 4, z, P.wall, 1);
        }
        Gen.door(ctx, wd, c, 1, 2, "pz");
        ctx.put(c, 0, -1, P.stairs, 3, St.fh("pz", "bottom"));
        if (Opts.notOff(o.light)) {
            int h = N / 2 - 3;
            for (int[] d : new int[][] {{-h, -h}, {h, -h}, {-h, h}, {h, h}}) ctx.put(c + d[0], 1, c + d[1] + 1, M + "lantern", 4, St.hanging(false));
        }
        if (Opts.notOff(o.furniture)) {
            int h = N / 2 - 3;
            Gen.Roles rr = Gen.rolesFor(1, 0);
            Set<String> reserved = Gen.keys(c + ",5", c + ",6");
            Gen.furnishRoom(new Gen.Room(0, c - h, c + h, 5, c + h + 1, reserved, rr.roles(), 2, M + "yellow_carpet", wd), ctx);
        }
        return Gen.finalize(ctx, N, N, Base.of().door(c).fill(P.wall));
    }

    // ------------------------------------------------------------------ tree house
    static double hash3(int x, int y, int z) {
        int h0 = ((x + 101) * 73856093) ^ ((y + 57) * 19349663) ^ ((z + 233) * 83492791);
        int h1 = h0 ^ (h0 >>> 13);
        long u = (h1 * 1274126177) & 0xFFFFFFFFL;
        return (u % 1000) / 1000.0;
    }

    static Plan treehouse(Opts o) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        int s = Gen.clamp(o.sizeOr(1), 0, 2);
        int W = dim(o.w, new int[] {9, 11, 13}[s]);
        int D = W;
        int c = (W - 1) / 2;
        int PY = 8;
        String accent = wid.equals("dark_oak") || wid.equals("spruce") ? M + "oak_log" : M + "dark_oak_log";
        String roofStairs = wid.equals("spruce") || wid.equals("dark_oak") ? M + "oak_stairs" : M + "spruce_stairs";
        String roofFull = wid.equals("spruce") || wid.equals("dark_oak") ? M + "oak_planks" : M + "spruce_planks";
        int TOP = PY + 14;
        Ctx ctx = new Ctx();
        java.util.function.BiPredicate<Integer, Integer> isPerim = (x, z) -> x == 0 || x == W - 1 || z == 0 || z == D - 1;
        java.util.function.BiPredicate<Integer, Integer> isCorner = (x, z) -> (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
        for (int x = -1; x <= W; x++)
            for (int z = -1; z <= D; z++) {
                boolean in = x >= 0 && x < W && z >= 0 && z < D;
                ctx.put(x, PY, z, in ? (isPerim.test(x, z) ? accent : wd.planks) : wd.planks, 1,
                        isPerim.test(x, z) && in ? St.axis(x == 0 || x == W - 1 ? "z" : "x") : null);
            }
        for (int x = c - 3; x <= c + 3; x++)
            for (int z = -3; z <= -2; z++) ctx.put(x, PY, z, wd.planks, 1);
        for (int x = -1; x <= W; x++)
            for (int z : new int[] {-1, D}) ctx.put(x, PY + 1, z, wd.fence, 3);
        for (int z = 0; z < D; z++)
            for (int x : new int[] {-1, W}) ctx.put(x, PY + 1, z, wd.fence, 3);
        for (int x = c - 3; x <= c + 3; x++) if (x != c) ctx.put(x, PY + 1, -3, wd.fence, 3);
        for (int z : new int[] {-3, -2})
            for (int x : new int[] {c - 3, c + 3}) ctx.put(x, PY + 1, z, wd.fence, 3);
        for (int x = c - 3; x <= c + 3; x++) ctx.del(x, PY + 1, -1);
        int[][] posts = {{-1, -1}, {W, -1}, {-1, D}, {W, D}, {c - 3, -3}, {c + 3, -3}, {c, -3}, {-1, c}, {W, c}, {c, D}};
        for (int[] p : posts)
            for (int y = 0; y < PY; y++) ctx.put(p[0], y, p[1], wd.log, 1, St.axis("y"));
        for (int[] b : new int[][] {{-1, -1, 1, 1}, {W, -1, -1, 1}, {-1, D, 1, -1}, {W, D, -1, -1}})
            for (int i = 1; i <= 2; i++)
                ctx.put(b[0] + b[2] * i, PY - 1 - (i - 1), b[1] + b[3] * i, wd.stairs, 3, St.fh(b[2] > 0 ? "px" : "nx", "top"), Ex.opt());
        for (int y = 1; y <= PY + 1; y++) ctx.put(c, y, -4, M + "ladder", 3, St.f("nz"));
        for (int y = 0; y < PY; y++) ctx.put(c, y, -3, wd.log, 1, St.axis("y"));
        for (int row = 1; row <= 3; row++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (!isPerim.test(x, z)) continue;
                    int y = PY + row;
                    if (isCorner.test(x, z)) {
                        ctx.put(x, y, z, accent, 1, St.axis("y"));
                        continue;
                    }
                    boolean win = false;
                    if (row == 2) {
                        if (z == 0) win = Math.abs(x - c) >= 2 && x >= 2 && x <= W - 3 && (x - c) % 2 == 0;
                        else if (z == D - 1) win = x >= 2 && x <= W - 3 && (x - c) % 2 == 0;
                        else win = z >= 2 && z <= D - 3 && z % 2 == 0;
                    }
                    ctx.put(x, y, z, win ? M + "glass_pane" : wd.planks, 1);
                }
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                ctx.put(x, PY + 4, z, isPerim.test(x, z) ? accent : wd.planks, 1, isPerim.test(x, z) ? St.axis(x == 0 || x == W - 1 ? "z" : "x") : null);
        Gen.hipRoof(ctx, -1, W, -1, D, PY + 5, roofStairs, roofFull);
        Gen.door(ctx, wd, c, PY + 1, 0, "pz");
        for (int y = 0; y <= TOP; y++) {
            int r = y < PY + 5 ? 1 : y < PY + 9 ? 1 : 0;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) ctx.put(c + dx, y, c + dz, wd.log, 1, St.axis("y"));
        }
        for (int[] d : new int[][] {{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-2, -1}, {2, 1}, {-1, 2}, {1, -2}})
            for (int y = 0; y <= 1; y++) ctx.put(c + d[0], y, c + d[1], wd.log, 1, St.axis("y"));
        for (int k = 3; k <= 7; k++) {
            ctx.put(c + k, PY + 8, c, wd.log, 1, St.axis("x"));
            ctx.put(c - k, PY + 8, c, wd.log, 1, St.axis("x"));
            ctx.put(c, PY + 9, c + k, wd.log, 1, St.axis("z"));
            ctx.put(c, PY + 9, c - k, wd.log, 1, St.axis("z"));
        }
        int[] rad = {5, 7, 8, 8, 7, 6, 4, 2};
        String leaf = wd.leaves;
        interface Res {
            boolean test(int x, int y, int z);
        }
        Res inReserved = (x, y, z) -> {
            if (x >= -1 && x <= W && z >= -1 && z <= D && y >= PY + 1 && y <= PY + 4) return true;
            return x >= c - 4 && x <= c + 4 && z >= -5 && z <= 0 && y >= 1 && y <= PY + 4;
        };
        for (int i = 0; i < rad.length; i++) {
            int y = PY + 6 + i;
            for (int dx = -rad[i]; dx <= rad[i]; dx++)
                for (int dz = -rad[i]; dz <= rad[i]; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rad[i] + 0.2 - hash3(dx, y, dz) * 1.6) continue;
                    int x = c + dx, z = c + dz;
                    if (ctx.has(x, y, z) || inReserved.test(x, y, z)) continue;
                    ctx.put(x, y, z, leaf, 4);
                }
        }
        for (int x = -4; x <= W + 3; x++)
            for (int z = -2; z <= D + 3; z++) {
                boolean outside = x < -1 || x > W || z > D;
                if (!outside) continue;
                for (int y = PY - 2; y <= PY + 3; y++) {
                    if (hash3(x, y, z) > 0.14 || ctx.has(x, y, z) || inReserved.test(x, y, z)) continue;
                    if (Math.abs(x - c) <= 4 && z < 0) continue;
                    ctx.put(x, y, z, leaf, 4);
                }
            }
        for (int[] p : new int[][] {{-1, -1}, {W, -1}, {-1, D}, {W, D}}) {
            ctx.put(p[0], PY + 2, p[1], wd.fence, 3);
            ctx.put(p[0], PY + 3, p[1], M + "lantern", 4, St.hanging(false));
        }
        for (int x : new int[] {c - 3, c + 3}) ctx.put(x, PY + 2, -3, M + "lantern", 4, St.hanging(false));
        if (Opts.notOff(o.light)) {
            for (int z : new int[] {D / 3, (2 * D) / 3})
                for (int x : new int[] {W / 4, (int) Math.ceil((3 * W) / 4.0) - 1}) ctx.put(x, PY + 3, z, M + "lantern", 4, St.hanging(true));
        }
        if (Opts.notOff(o.furniture)) {
            Gen.Roles rr = Gen.rolesFor(1, 0);
            Gen.furnishRoom(new Gen.Room(PY, 1, W - 2, 1, D - 2, Gen.keys(c + ",1", c + ",2"), rr.roles(), 2, M + "green_carpet", wd), ctx);
        }
        return Gen.finalize(ctx, W, D, Base.of().fill(wd.log).clear(-2, W + 1, -4, D + 1));
    }

    // ------------------------------------------------------------------ mansion
    static Plan mansion(Opts o) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        Pal P0 = palette(o, "mansion", wid, wd);
        boolean quartz = P0.wall.equals(M + "quartz_block");
        String WALL = quartz ? M + "smooth_quartz" : P0.wall;
        String PIL = quartz ? M + "quartz_pillar" : P0.pillar;
        String STAIRS = P0.stairs;
        int s = Gen.clamp(o.sizeOr(1), 0, 2);
        int W = dim(o.w, new int[] {11, 15, 19}[s]);
        int D = dim(o.d, new int[] {11, 13, 15}[s]);
        int F = Gen.clamp(o.floorsOr(2), 1, 4);
        int c = (W - 1) / 2;
        IntUnaryOperator yf = f -> 4 * f;
        int topY = yf.applyAsInt(F);
        Ctx ctx = new Ctx();
        java.util.function.BiPredicate<Integer, Integer> isPerim = (x, z) -> x == 0 || x == W - 1 || z == 0 || z == D - 1;
        java.util.function.BiPredicate<Integer, Integer> isCorner = (x, z) -> (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
        for (int f = 0; f < F; f++) {
            int y0 = yf.applyAsInt(f);
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) ctx.put(x, y0, z, isPerim.test(x, z) ? (isCorner.test(x, z) ? PIL : WALL) : wd.planks, 1);
            for (int row = 1; row <= 3; row++)
                for (int x = 0; x < W; x++)
                    for (int z = 0; z < D; z++) {
                        if (!isPerim.test(x, z)) continue;
                        int y = y0 + row;
                        boolean onFrontBack = z == 0 || z == D - 1;
                        boolean pillarCol = onFrontBack ? (x - c) % 4 == 0 : z % 4 == 0;
                        if (isCorner.test(x, z) || pillarCol) {
                            ctx.put(x, y, z, PIL, 1);
                            continue;
                        }
                        boolean win = row >= 2 && (onFrontBack ? x >= 1 && x <= W - 2 : z >= 1 && z <= D - 2);
                        ctx.put(x, y, z, win ? M + "glass_pane" : WALL, 1);
                    }
        }
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) ctx.put(x, topY, z, isPerim.test(x, z) ? WALL : wd.planks, 1);
        Gen.hipRoof(ctx, -1, W, -1, D, topY + 1, M + "deepslate_tile_stairs", M + "deepslate_tiles");
        for (int f = 0; f < F - 1; f++) {
            ctx.del(1, yf.applyAsInt(f + 1), 3);
            ctx.del(1, yf.applyAsInt(f + 1), 4);
            for (int k = 0; k < 4; k++) ctx.put(1, yf.applyAsInt(f) + 1 + k, 2 + k, STAIRS, 3, St.fh("pz", "bottom"));
        }
        List<Integer> colX = new ArrayList<>();
        for (int x : new int[] {c - 4, c - 2, c + 2, c + 4}) if (x >= 1 && x <= W - 2) colX.add(x);
        int pw = Math.min(c - 1, 5);
        for (int x = c - pw; x <= c + pw; x++)
            for (int z = -3; z <= -1; z++) {
                ctx.put(x, 0, z, WALL, 1);
                ctx.put(x, 4, z, WALL, 1);
            }
        for (int x : colX)
            for (int y = 1; y <= 3; y++) ctx.put(x, y, -3, PIL, 1);
        for (int x = c - pw; x <= c + pw; x++) ctx.put(x, 5, -3, M + "iron_bars", 3);
        for (int x : new int[] {c - pw, c + pw})
            for (int z : new int[] {-2, -1}) ctx.put(x, 5, z, M + "iron_bars", 3);
        for (int x = c - 1; x <= c + 1; x++) ctx.put(x, 0, -4, STAIRS, 3, St.fh("pz", "bottom"));
        Gen.door(ctx, wd, c, 1, 0, "pz");
        if (F >= 2) Gen.door(ctx, wd, c, 5, 0, "pz");
        if (Opts.notOff(o.light)) {
            for (int f = 0; f < F; f++)
                for (int z : new int[] {D / 3, (2 * D) / 3})
                    for (int x : new int[] {c - 3, c, c + 3})
                        if (x > 2) ctx.put(x, yf.applyAsInt(f) + 4, z, M + "sea_lantern", 4);
            for (int x : colX) ctx.put(x, 4, -2, M + "lantern", 4, St.hanging(true));
        }
        if (Opts.notOff(o.porch)) {
            for (int z = -10; z <= -5; z++)
                for (int x = c - 1; x <= c + 1; x++) ctx.put(x, -1, z, M + "stone_bricks", 1, null, Ex.opt());
            for (int z : new int[] {-6, -10})
                for (int x : new int[] {c - 3, c + 3}) {
                    ctx.put(x, 0, z, M + "stone_brick_wall", 3, null, Ex.opt());
                    ctx.put(x, 1, z, M + "stone_brick_wall", 3, null, Ex.opt());
                    ctx.put(x, 2, z, M + "lantern", 4, St.hanging(false));
                }
            for (int x = c - 7; x <= c + 7; x++) {
                if (Math.abs(x - c) <= 2) continue;
                ctx.put(x, 0, -11, wd.leaves, 4);
                ctx.put(x, 1, -11, wd.leaves, 4);
            }
            for (int x = c - 7; x <= c + 7; x += 7)
                for (int y = 0; y <= 1; y++) ctx.put(x, y, -11, wd.fence, 3, null, Ex.opt());
        }
        if (Opts.notOff(o.pool)) {
            int px0 = 3, px1 = W - 4, pz0 = D + 1, pz1 = D + 5;
            for (int x = px0 - 1; x <= px1 + 1; x++)
                for (int z = pz0 - 1; z <= pz1 + 1; z++) {
                    boolean edge = x == px0 - 1 || x == px1 + 1 || z == pz0 - 1 || z == pz1 + 1;
                    if (edge) ctx.put(x, -1, z, M + "smooth_quartz", 1, null, Ex.opt().withTag("pool"));
                    else {
                        ctx.put(x, -2, z, M + "smooth_quartz", 1, null, Ex.opt().withTag("pool"));
                        ctx.put(x, -1, z, M + "water", 6, null, Ex.opt().withTag("pool").withItem(null));
                    }
                }
            for (int x = px0 - 1; x <= px1 + 1; x++) ctx.put(x, -1, D, M + "smooth_quartz", 1, null, Ex.opt().withTag("pool"));
        }
        if (Opts.notOff(o.furniture)) {
            for (int f = 0; f < F; f++) {
                Set<String> reserved = new java.util.LinkedHashSet<>();
                if (f == 0) {
                    reserved.add(c + ",1");
                    reserved.add(c + ",2");
                }
                for (int z = 1; z <= 7; z++) {
                    reserved.add("1," + z);
                    reserved.add("2," + z);
                }
                Gen.Roles rr = rolesForFloor(F, f);
                Gen.furnishRoom(new Gen.Room(yf.applyAsInt(f), 1, W - 2, 1, D - 2, reserved, rr.roles(), Math.min(rr.beds() + (f == F - 1 ? 1 : 0), 3), M + "red_carpet", wd), ctx);
            }
        }
        return Gen.finalize(ctx, W, D, Base.of().fill(M + "stone_bricks").clear(-1, W, -1, D));
    }

    // ------------------------------------------------------------------ safe house
    static Plan safehouse(Opts o) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        Pal P = palette(o, "safehouse", wid, wd);
        int s = Gen.clamp(o.sizeOr(0), 0, 2);
        int W = dim(o.w, new int[] {7, 9, 11}[s]);
        int D = dim(o.d, new int[] {7, 9, 11}[s]);
        int F = Gen.clamp(o.floorsOr(1), 1, 3);
        int c = (W - 1) / 2;
        IntUnaryOperator yf = f -> 4 * f;
        int topY = yf.applyAsInt(F);
        Ctx ctx = new Ctx();
        java.util.function.BiPredicate<Integer, Integer> isPerim = (x, z) -> x == 0 || x == W - 1 || z == 0 || z == D - 1;
        String INNER = M + "stone_bricks";
        int ladderX = W - 2, ladderZ = D - 2;
        for (int f = 0; f < F; f++) {
            int y0 = yf.applyAsInt(f);
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (f > 0 && x == ladderX && z == ladderZ) continue;
                    ctx.put(x, y0, z, isPerim.test(x, z) ? P.wall : wd.planks, 1);
                }
            for (int row = 1; row <= 3; row++)
                for (int x = 0; x < W; x++)
                    for (int z = 0; z < D; z++) {
                        if (!isPerim.test(x, z)) continue;
                        boolean slit = row == 2 && ((z == 0 || z == D - 1) ? (x - c) % 2 == 0 && Math.abs(x - c) >= 2 : z % 2 == 0 && z >= 2 && z <= D - 3);
                        ctx.put(x, y0 + row, z, slit ? M + "iron_bars" : P.wall, 1);
                    }
        }
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) ctx.put(x, topY, z, P.wall, 1);
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                if (isPerim.test(x, z)) ctx.put(x, topY + 1, z, P.slab, 2);
        for (int[] p : new int[][] {{1, 1}, {W - 2, 1}, {1, D - 2}, {W - 2, D - 2}, {c, D / 2}}) ctx.put(p[0], topY + 1, p[1], M + "sea_lantern", 4);
        ctx.put(c - 1, 1, 1, P.wall, 1);
        for (int y = 1; y <= 3; y++)
            for (int x : new int[] {c - 1, c + 1})
                for (int z : new int[] {1, 2}) ctx.put(x, y, z, P.wall, 1);
        for (int x = c - 1; x <= c + 1; x++) ctx.put(x, 4, 1, P.wall, 1);
        Gen.door(ctx, wd, c, 1, 0, "pz");
        Gen.door(ctx, wd, c, 1, 2, "pz");
        ctx.put(c, 3, 1, M + "lantern", 4, St.hanging(true));
        ctx.put(c, 0, -1, P.stairs, 3, St.fh("pz", "bottom"));
        for (int f = 0; f < F - 1; f++)
            for (int y = yf.applyAsInt(f) + 1; y <= yf.applyAsInt(f + 1); y++) ctx.put(ladderX, y, ladderZ, M + "ladder", 3, St.f("nz"));
        for (int f = 0; f < F; f++)
            for (int z : new int[] {D / 3 + 1, (2 * D) / 3})
                for (int x : W >= 9 ? new int[] {2, W - 3} : new int[] {c})
                    if (z > 2) ctx.put(x, yf.applyAsInt(f) + 3, z, M + "lantern", 4, St.hanging(true));
        List<int[]> lights = new ArrayList<>();
        for (int x = -4; x <= W + 3; x += 4) {
            lights.add(new int[] {x, -4});
            lights.add(new int[] {x, D + 3});
        }
        for (int z = -4; z <= D + 3; z += 4) {
            lights.add(new int[] {-4, z});
            lights.add(new int[] {W + 3, z});
        }
        for (int[] p : lights) ctx.put(p[0], -1, p[1], M + "sea_lantern", 4, null, Ex.opt());
        if (Opts.notOff(o.furniture)) {
            for (int f = 0; f < F; f++) {
                Set<String> reserved = new java.util.LinkedHashSet<>();
                if (f == 0) for (int z = 1; z <= 4; z++) reserved.add(c + "," + z);
                if (F > 1) {
                    reserved.add(ladderX + "," + ladderZ);
                    reserved.add(ladderX + "," + (ladderZ - 1));
                }
                Gen.Roles rr = f == 0 && F == 1 ? new Gen.Roles(new String[] {"storage", "kitchen", "workshop", "bedroom", "storage", "brewing"}, 2) : rolesForFloor(F, f);
                Gen.furnishRoom(new Gen.Room(yf.applyAsInt(f), 1, W - 2, 3, D - 2, reserved, rr.roles(), Math.min(rr.beds(), 3), M + "gray_carpet", wd), ctx);
            }
        }
        return Gen.finalize(ctx, W, D, Base.of().fill(INNER).clear(-4, W + 3, -4, D + 3));
    }

    // ------------------------------------------------------------------ lighthouse
    static Plan lighthouse(Opts o) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        int s = Gen.clamp(o.sizeOr(1), 0, 2);
        int r0 = new int[] {4, 5, 6}[s];
        double hv = o.height != null ? o.height : (Opts.on(o.floorsExact) && o.floors != null && o.floors != 0 ? o.floors * 5 : new int[] {14, 18, 22}[s]);
        int H = (int) Math.max(12, Math.min(60, Gen.jsRound(hv)));
        int W = 2 * r0 + 1;
        int D = W;
        int cx = r0, cz = r0;
        Ctx ctx = new Ctx();
        String WHITE = M + "quartz_block", RED = M + "red_concrete";
        IntUnaryOperator radAt = y -> r0 - (int) Math.floor((y * 2) / (double) H);
        for (int y = 0; y <= H; y++) {
            int r = radAt.applyAsInt(y);
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                    if (d <= r + 0.3 && d > r - 1) {
                        if (y == 0 || y == H) continue;
                        boolean band = Math.floorDiv(y - 1, 3) % 2 == 0;
                        ctx.put(x, y, z, band ? WHITE : RED, 1);
                    }
                }
        }
        for (int y = 0; y <= H; y += 5) {
            int r = radAt.applyAsInt(y);
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                    if (d > r + 0.3) continue;
                    if (y > 0 && y < H && x == cx && z == cz) continue;
                    if (y == 0 || d <= r - 1) ctx.put(x, y, z, wd.planks, 1);
                }
        }
        for (int y = 1; y <= H - 1; y++) {
            ctx.put(cx, y, cz + 1, M + "stone_bricks", 1);
            ctx.put(cx, y, cz, M + "ladder", 3, St.f("nz"));
        }
        int rl = radAt.applyAsInt(H - 3);
        for (int y = H - 3; y <= H - 1; y++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                    if (d <= rl + 0.3 && d > rl - 1) ctx.put(x, y, z, M + "glass", 1);
                }
        for (int y = H - 3; y <= H - 2; y++) ctx.put(cx, y, cz - 1, M + "sea_lantern", 4);
        ctx.put(cx, H - 2, cz + 1, M + "sea_lantern", 4);
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d <= rl + 1.3) ctx.put(x, H - 4, z, d > rl + 0.3 ? M + "quartz_slab" : WHITE, 2, d > rl + 0.3 ? St.half("bottom") : null);
                if (d <= rl + 1.3 && d > rl + 0.3) ctx.put(x, H - 3, z, M + "iron_bars", 3);
                if (d <= rl + 0.8) ctx.put(x, H, z, RED, 2);
                if (d <= rl - 0.5) ctx.put(x, H + 1, z, M + "red_concrete", 2);
                if (d <= rl - 1.5) ctx.put(x, H + 2, z, M + "red_concrete", 2);
            }
        ctx.put(cx, H + 3, cz, M + "lantern", 4, St.hanging(false));
        for (int y = 1; y <= 2; y++) ctx.del(cx, y, 0);
        Gen.door(ctx, wd, cx, 1, 0, "pz");
        ctx.put(cx, 0, -1, M + "quartz_stairs", 3, St.fh("pz", "bottom"));
        if (Opts.notOff(o.light))
            for (int y = 3; y < H - 4; y += 5) ctx.put(cx - 1, y, cz - 1, M + "lantern", 4, St.hanging(false));
        return Gen.finalize(ctx, W, D, Base.of().fill(M + "stone_bricks").door(cx));
    }

    public static Plan generateExtra(Opts o) {
        switch (o.type) {
            case "underground":
                return Gen.variantOf(o) % BunkerGen.VARIANTS.length == 0 ? underground(o) : BunkerGen.generate(o);
            case "statue":
            case "portal":
                return StatueGen.generate(o);
            case "igloo":
                return igloo(o);
            case "pyramid":
                return pyramid(o);
            case "treehouse":
                return treehouse(o);
            case "mansion":
                return mansion(o);
            case "safehouse":
                return safehouse(o);
            case "lighthouse":
                return lighthouse(o);
            default:
                if (MoreGen.isMoreType(o.type)) return MoreGen.generate(o);
                return boxHouse(o, o.type);
        }
    }
}
