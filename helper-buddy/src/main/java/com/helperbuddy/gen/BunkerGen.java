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
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;

import static com.helperbuddy.gen.Gen.M;

/** Underground bunker variants (variant 0 is the classic cellar in ExtraGen). 1 military, 2 cavern, 3 vault, 4 silo. */
public final class BunkerGen {
    private BunkerGen() {}

    public static final String[][] VARIANTS = {
            {"ห้องใต้ดินคลาสสิก", "classic cellar"}, {"บังเกอร์ทหาร", "military bunker"}, {"โถงถ้ำทรงกลม", "round cavern hall"},
            {"ห้องนิรภัย", "vault"}, {"ไซโลหลุมลึก", "deep silo"},
    };
    static final String CARPET = M + "gray_carpet";

    record B(Wood wd, Pal P, int s) {}

    static B base(Opts o, String def) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        return new B(wd, Gen.palette(o, def, wid, wd), Gen.clamp(o.sizeOr(1), 0, 2));
    }

    public static Plan generate(Opts o) {
        return switch (Gen.variantOf(o) % VARIANTS.length) {
            case 2 -> cavern(o);
            case 3 -> vault(o);
            case 4 -> silo(o);
            default -> military(o);
        };
    }

    // ------------------------------------------------------------------ 1. military corridor bunker
    static Plan military(Opts o) {
        B b = base(o, "stone");
        Wood wd = b.wd;
        Pal P = b.P;
        int s = b.s;
        int W = Gen.dim(o.w, new int[] {13, 15, 17}[s], 13, 31);
        int D = Gen.dim(o.d, new int[] {19, 25, 31}[s], 15, 41);
        int F = Gen.clamp(o.floorsOr(1), 1, 3);
        int c = (W - 1) / 2;
        Ctx ctx = new Ctx();
        List<Cell> clear = new ArrayList<>();
        IntUnaryOperator y0f = f -> -2 - 4 * (F - f);
        BiPredicate<Integer, Integer> perim = (x, z) -> x == 0 || x == W - 1 || z == 0 || z == D - 1;
        BiPredicate<Integer, Integer> corner = (x, z) -> (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
        int ladZ = D - 2;
        boolean light = Opts.notOff(o.light), furn = Opts.notOff(o.furniture);
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                for (int y = y0f.applyAsInt(0); y <= -2; y++) clear.add(new Cell(x, y, z));
        String[][] seq = {{"bedroom"}, {"kitchen"}, {"workshop"}, {"storage", "storage"}, {"brewing", "storage"}, {"bedroom"}, {"workshop", "storage"}};
        for (int f = 0; f < F; f++) {
            int y0 = y0f.applyAsInt(f);
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (f > 0 && x == c && z == ladZ) continue;
                    ctx.put(x, y0, z, perim.test(x, z) ? P.wall : P.floor, 1);
                }
            for (int row = 1; row <= 3; row++)
                for (int x = 0; x < W; x++)
                    for (int z = 0; z < D; z++)
                        if (perim.test(x, z))
                            ctx.put(x, y0 + row, z, corner.test(x, z) ? P.pillar : P.wall, 1, corner.test(x, z) && P.wood ? St.axis("y") : null);
            for (int px : new int[] {c - 2, c + 2})
                for (int z = 1; z <= D - 2; z++)
                    for (int row = 1; row <= 3; row++) ctx.put(px, y0 + row, z, P.wall, 1);
            int k = 0;
            for (int z = 1; z + 2 <= D - 3; z += 4, k++) {
                int zm = z + 1;
                for (int side : new int[] {0, 1}) {
                    int x0 = side == 0 ? 1 : c + 3;
                    int x1 = side == 0 ? c - 3 : W - 2;
                    for (int x = x0; x <= x1; x++)
                        for (int row = 1; row <= 3; row++) ctx.put(x, y0 + row, z + 3, P.wall, 1);
                    int doorX = side == 0 ? c - 2 : c + 2;
                    Gen.ironDoor(ctx, doorX, y0 + 1, zm, side == 0 ? "px" : "nx");
                    String[] roles = seq[(k * 2 + side) % seq.length];
                    if (furn) {
                        boolean bedroom = false;
                        for (String r : roles) if (r.equals("bedroom")) bedroom = true;
                        Gen.furnishRoom(new Gen.Room(y0, x0, x1, z, z + 2, Gen.keys((side == 0 ? c - 3 : c + 3) + "," + zm), roles, bedroom ? 2 : 0, CARPET, wd), ctx);
                    }
                    if (light) ctx.put(Math.floorDiv(x0 + x1, 2), y0 + 4, zm, M + "sea_lantern", 4);
                }
                if (light) ctx.put(c, y0 + 4, zm, M + "sea_lantern", 4);
            }
            for (int x = c - 1; x <= c + 1; x++)
                for (int row = 1; row <= 3; row++)
                    if (f == 0 && D >= 21) ctx.put(x, y0 + row, D - 5, x == c && row <= 2 ? M + "iron_bars" : P.wall, 1);
            if (f == 0 && D >= 21) {
                Gen.ironDoor(ctx, c, y0 + 1, D - 5, "pz");
                for (int x = c - 1; x <= c + 1; x++)
                    for (int z = D - 4; z <= D - 2; z++)
                        ctx.put(x, y0 + 1, z, x == c ? M + "redstone_block" : M + "furnace", 5, x == c ? null : St.f("nz"), Ex.opt());
                ctx.put(c, y0 + 4, D - 3, M + "sea_lantern", 4);
            }
        }
        for (int f = 0; f < F - 1; f++)
            for (int y = y0f.applyAsInt(f) + 1; y <= y0f.applyAsInt(f + 1); y++) ctx.put(c, y, ladZ, M + "ladder", 3, St.f("nz"));
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) ctx.put(x, -2, z, perim.test(x, z) ? P.wall : P.floor, 1);
        Gen.entranceStairs(ctx, clear, c, y0f.applyAsInt(F - 1), P, wd, true, light);
        return Gen.finalize(ctx, W, D, Base.of().clear(0, -1, 0, -1).cells(clear).fill(M + "cobblestone").under());
    }

    // ------------------------------------------------------------------ circular helpers
    interface Inside {
        boolean test(int x, int z);
    }

    static void ringStations(Ctx ctx, int cx, int cz, double rad, int y, String[] items, Inside inside, boolean skipFront) {
        int n = items.length;
        for (int j = 0; j < n; j++) {
            double th = ((j + 0.5) / n) * Math.PI * 2;
            if (skipFront && Math.abs(th - Math.PI) < 0.55) continue;
            int x = (int) Gen.jsRound(cx + StrictMath.sin(th) * rad);
            int z = (int) Gen.jsRound(cz + StrictMath.cos(th) * rad);
            int dx = cx - x, dz = cz - z;
            String f = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "px" : "nx") : dz > 0 ? "pz" : "nz";
            String id = items[j];
            if (id.equals("bed")) {
                String toHead = f.equals("px") ? "nx" : f.equals("nx") ? "px" : f.equals("pz") ? "nz" : "pz";
                int[] d = Gen.dirVec(toHead);
                if (inside.test(x, z) && inside.test(x - d[0], z - d[1]) && !ctx.has(x, y, z) && !ctx.has(x - d[0], y, z - d[1])) Gen.bed(ctx, x, y, z, toHead);
                continue;
            }
            if (!inside.test(x, z) || ctx.has(x, y, z)) continue;
            boolean facing = Gen.in(id, "furnace", "chest", "smoker", "blast_furnace", "anvil", "grindstone", "stonecutter_block", "loom", "lectern");
            ctx.put(x, y, z, M + id, 5, facing ? St.f(f) : null, Ex.opt());
        }
    }

    static double hyp(int x, int z, int R) {
        return StrictMath.hypot(x - R, z - R);
    }

    // ------------------------------------------------------------------ 2. round cavern hall
    static Plan cavern(Opts o) {
        B b = base(o, "deepslate");
        Wood wd = b.wd;
        Pal P = b.P;
        int s = b.s;
        int R = o.w != null ? Gen.clamp(Math.floorDiv(o.w, 2), 6, 16) : new int[] {7, 9, 11}[s];
        int W = 2 * R + 1, D = W, c = R;
        Ctx ctx = new Ctx();
        List<Cell> clear = new ArrayList<>();
        int yF = -7;
        BiPredicate<Integer, Integer> isWall = (x, z) -> hyp(x, z, R) > R - 1 && hyp(x, z, R) <= R;
        Inside isIn = (x, z) -> hyp(x, z, R) <= R - 1;
        boolean light = Opts.notOff(o.light);
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                double d = hyp(x, z, R);
                if (d > R) continue;
                ctx.put(x, yF, z, isWall.test(x, z) ? P.wall : ((x + z) % 2 == 0 ? P.floor : P.wall), 1);
                ctx.put(x, -2, z, P.wall, 1);
                for (int y = yF + 1; y <= -3; y++) {
                    if (isWall.test(x, z)) ctx.put(x, y, z, P.wall, 1);
                    else clear.add(new Cell(x, y, z));
                }
            }
        for (int j = 0; j < 8; j++) {
            double th = (j / 8.0) * Math.PI * 2 + Math.PI / 8;
            int x = (int) Gen.jsRound(R + StrictMath.sin(th) * (R - 3));
            int z = (int) Gen.jsRound(R + StrictMath.cos(th) * (R - 3));
            if (!isIn.test(x, z)) continue;
            for (int y = yF + 1; y <= -3; y++) ctx.put(x, y, z, P.pillar, 1, P.wood ? St.axis("y") : null);
        }
        for (int x = R - 3; x <= R + 3; x++)
            for (int z = R - 3; z <= R + 3; z++) {
                double d = hyp(x, z, R);
                if (d <= 1.2) ctx.put(x, yF + 1, z, M + "water", 3, null, Ex.item(null));
                else if (d <= 2.3) ctx.put(x, yF + 1, z, P.pillar, 1, P.wood ? St.axis("y") : null);
            }
        ctx.put(R, yF, R, M + "sea_lantern", 4);
        if (light) {
            for (int j = 0; j < 8; j++) {
                double th = (j / 8.0) * Math.PI * 2;
                int x = (int) Gen.jsRound(R + StrictMath.sin(th) * (R / 2.0));
                int z = (int) Gen.jsRound(R + StrictMath.cos(th) * (R / 2.0));
                ctx.put(x, -2, z, M + "sea_lantern", 4);
            }
            ctx.put(R, -2, R, M + "sea_lantern", 4);
        }
        if (Opts.notOff(o.furniture)) {
            String[] items = {"bed", "chest", "crafting_table", "bed", "furnace", "barrel", "anvil", "bed", "brewing_stand", "chest", "smoker", "bed", "lectern", "barrel", "grindstone", "stonecutter_block"};
            ringStations(ctx, R, R, R - 2, yF + 1, items, isIn, true);
        }
        Gen.entranceStairs(ctx, clear, c, yF, P, wd, false, light);
        return Gen.finalize(ctx, W, D, Base.of().clear(0, -1, 0, -1).cells(clear).fill(M + "cobblestone").under());
    }

    // ------------------------------------------------------------------ 3. vault
    static Plan vault(Opts o) {
        B b = base(o, "blackstone");
        Wood wd = b.wd;
        Pal P = b.P;
        int s = b.s;
        int W = Gen.dim(o.w, new int[] {13, 15, 17}[s], 11, 31);
        int D = Gen.dim(o.d, new int[] {21, 25, 29}[s], 17, 41);
        int c = (W - 1) / 2;
        Ctx ctx = new Ctx();
        List<Cell> clear = new ArrayList<>();
        int y0 = -6;
        BiPredicate<Integer, Integer> perim = (x, z) -> x == 0 || x == W - 1 || z == 0 || z == D - 1;
        BiPredicate<Integer, Integer> corner = (x, z) -> (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
        int sepZ = D / 2 - 1;
        boolean light = Opts.notOff(o.light);
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                for (int y = y0; y <= -2; y++) clear.add(new Cell(x, y, z));
                ctx.put(x, y0, z, perim.test(x, z) ? P.wall : z > sepZ ? P.floor : wd.planks, 1);
                ctx.put(x, -2, z, perim.test(x, z) ? P.wall : P.floor, 1);
            }
        for (int row = 1; row <= 3; row++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++)
                    if (perim.test(x, z)) ctx.put(x, y0 + row, z, corner.test(x, z) ? P.pillar : P.wall, 1, corner.test(x, z) && P.wood ? St.axis("y") : null);
        for (int x = 1; x <= W - 2; x++)
            for (int row = 1; row <= 3; row++) ctx.put(x, y0 + row, sepZ, Math.abs(x - c) <= 2 && row <= 3 ? M + "iron_bars" : P.wall, 1);
        Gen.ironDoor(ctx, c, y0 + 1, sepZ, "pz");
        ctx.put(c - 1, y0 + 1, sepZ, P.wall, 1);
        ctx.put(c + 1, y0 + 1, sepZ, P.wall, 1);
        ctx.put(c - 1, y0 + 2, sepZ, P.wall, 1);
        ctx.put(c + 1, y0 + 2, sepZ, P.wall, 1);
        ctx.put(c - 1, y0 + 3, sepZ, P.wall, 1);
        ctx.put(c + 1, y0 + 3, sepZ, P.wall, 1);
        for (int x : new int[] {c - 2, c + 2})
            for (int row = 1; row <= 3; row++) ctx.put(x, y0 + row, sepZ, M + "iron_bars", 1);
        for (int z = sepZ + 1; z <= D - 2; z++) ctx.put(c, y0, z, M + "gold_block", 1);
        String[] shows = {M + "diamond_block", M + "emerald_block", M + "gold_block", M + "netherite_block", M + "lapis_block", M + "iron_block"};
        int si = 0;
        for (int z = sepZ + 3; z <= D - 4; z += 3)
            for (int x : new int[] {c - 3, c + 3}) {
                if (x < 2 || x > W - 3) continue;
                ctx.put(x, y0 + 1, z, shows[si++ % shows.length], 5, null, Ex.opt());
                ctx.put(x, y0 + 2, z, M + "lantern", 4, St.hanging(false));
            }
        for (int x = 2; x <= W - 3; x += 2) ctx.put(x, y0 + 1, D - 2, M + "chest", 5, St.f("nz"), Ex.opt());
        for (int z = sepZ + 2; z <= D - 3; z += 2) {
            ctx.put(1, y0 + 1, z, M + "chest", 5, St.f("px"), Ex.opt());
            ctx.put(W - 2, y0 + 1, z, M + "chest", 5, St.f("nx"), Ex.opt());
        }
        if (Opts.notOff(o.furniture))
            Gen.furnishRoom(new Gen.Room(y0, 1, W - 2, 2, sepZ - 1, Gen.keys(c + ",1", c + ",2", c + "," + (sepZ - 1), (c - 1) + "," + (sepZ - 1), (c + 1) + "," + (sepZ - 1)),
                    new String[] {"kitchen", "workshop", "bedroom", "storage", "brewing"}, 3, CARPET, wd), ctx);
        if (light)
            for (int z = 3; z <= D - 3; z += 4)
                for (int x : new int[] {3, c, W - 4}) ctx.put(x, -2, z, M + "sea_lantern", 4);
        Gen.entranceStairs(ctx, clear, c, y0, P, wd, true, light);
        return Gen.finalize(ctx, W, D, Base.of().clear(0, -1, 0, -1).cells(clear).fill(M + "cobblestone").under());
    }

    // ------------------------------------------------------------------ 4. deep silo
    static Plan silo(Opts o) {
        B b = base(o, "stone");
        Wood wd = b.wd;
        Pal P = b.P;
        int s = b.s;
        int R = o.w != null ? Gen.clamp(Math.floorDiv(o.w, 2), 5, 10) : new int[] {5, 6, 7}[s];
        int W = 2 * R + 1, D = W, c = R;
        int L = Gen.clamp(o.floors != null ? o.floors : new int[] {3, 4, 5}[s], 2, 6);
        Ctx ctx = new Ctx();
        List<Cell> clear = new ArrayList<>();
        IntUnaryOperator y0f = f -> -2 - 4 * (L - f);
        BiPredicate<Integer, Integer> isWall = (x, z) -> hyp(x, z, R) > R - 1 && hyp(x, z, R) <= R;
        BiPredicate<Integer, Integer> isIn = (x, z) -> hyp(x, z, R) <= R - 1;
        String[][] kinds = {{"bed", "bed", "chest", "bed", "bed", "barrel", "chest", "lectern"},
                {"crafting_table", "furnace", "smoker", "chest", "barrel", "chest", "blast_furnace", "barrel"},
                {"anvil", "grindstone", "stonecutter_block", "loom", "chest", "enchanting_table", "bookshelf", "brewing_stand"}};
        boolean light = Opts.notOff(o.light);
        for (int f = 0; f < L; f++) {
            int y0 = y0f.applyAsInt(f);
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (hyp(x, z, R) > R) continue;
                    if (!(f > 0 && x == c && z == c)) ctx.put(x, y0, z, isWall.test(x, z) ? P.wall : (x + z) % 2 == 0 ? P.floor : P.wall, 1);
                    for (int row = 1; row <= 3; row++) {
                        if (isWall.test(x, z)) ctx.put(x, y0 + row, z, P.wall, 1);
                        else clear.add(new Cell(x, y0 + row, z));
                    }
                    if (f == 0) clear.add(new Cell(x, y0, z));
                }
            for (int row = 1; row <= 3; row++) ctx.put(c, y0 + row, c + 1, P.pillar, 1, P.wood ? St.axis("y") : null);
            if (light) ctx.put(c, y0 + 4, c - 1, M + "sea_lantern", 4);
            if (Opts.notOff(o.furniture)) {
                String[] items = kinds[(L - 1 - f) % kinds.length].clone();
                ringStations(ctx, c, c, R - 2, y0 + 1, items, (x, z) -> isIn.test(x, z) && hyp(x, z, R) > 2.2, f == L - 1);
            }
        }
        for (int f = 0; f < L - 1; f++)
            for (int y = y0f.applyAsInt(f) + 1; y <= y0f.applyAsInt(f + 1); y++) ctx.put(c, y, c, M + "ladder", 3, St.f("nz"));
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                if (hyp(x, z, R) <= R) ctx.put(x, -2, z, isWall.test(x, z) ? P.wall : P.floor, 1);
        if (light)
            for (int j = 0; j < 4; j++) {
                double th = (j / 4.0) * Math.PI * 2 + 0.7;
                ctx.put((int) Gen.jsRound(c + StrictMath.sin(th) * (R - 2)), -2, (int) Gen.jsRound(c + StrictMath.cos(th) * (R - 2)), M + "sea_lantern", 4);
            }
        Gen.entranceStairs(ctx, clear, c, y0f.applyAsInt(L - 1), P, wd, false, light);
        return Gen.finalize(ctx, W, D, Base.of().clear(0, -1, 0, -1).cells(clear).fill(M + "cobblestone").under());
    }
}
