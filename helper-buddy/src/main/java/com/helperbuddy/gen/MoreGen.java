package com.helperbuddy.gen;

import com.helperbuddy.gen.Gen.Base;
import com.helperbuddy.gen.Gen.Ctx;
import com.helperbuddy.gen.Gen.Ex;
import com.helperbuddy.gen.Gen.Opts;
import com.helperbuddy.gen.Gen.Pal;
import com.helperbuddy.gen.Gen.Plan;
import com.helperbuddy.gen.Gen.St;
import com.helperbuddy.gen.Gen.Wood;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.helperbuddy.gen.Gen.M;

/** Hotel, hospital, prison, school, library, church, barn / stable, market. */
public final class MoreGen {
    private MoreGen() {}

    public static final Map<String, String[][]> VARIANTS = new LinkedHashMap<>();

    static {
        VARIANTS.put("hotel", new String[][] {{"หลังคาเรียบ+สระดาดฟ้า", "flat roof + rooftop pool"}, {"หลังคาจั่ว", "gable roof"}, {"หลังคาทรงปั้นหยา", "hip roof"}});
        VARIANTS.put("hospital", new String[][] {{"หลังคาเรียบ", "flat roof"}, {"หลังคาจั่ว", "gable roof"}, {"หลังคาทรงปั้นหยา", "hip roof"}});
        VARIANTS.put("prison", new String[][] {{"คุกมาตรฐาน", "standard prison"}, {"คุกเข้ม (ดีพสเลท)", "dark deepslate prison"}, {"คุกหอคอยสูง", "tall-tower prison"}});
        VARIANTS.put("school", new String[][] {{"หลังคาเรียบ", "flat roof"}, {"หลังคาจั่ว", "gable roof"}, {"หลังคาทรงปั้นหยา", "hip roof"}});
        VARIANTS.put("library", new String[][] {{"หลังคาเรียบ", "flat roof"}, {"หลังคาจั่ว", "gable roof"}, {"หลังคาทรงปั้นหยา", "hip roof"}});
        VARIANTS.put("church", new String[][] {{"โบสถ์หินหอระฆังเหลี่ยม", "stone church with bell tower"}, {"โบสถ์ไม้", "wooden chapel"}, {"มหาวิหารหินทราย", "sandstone cathedral"}});
        VARIANTS.put("barn", new String[][] {{"โรงนาแดง", "red barn"}, {"คอกม้า", "horse stable"}, {"เล้าไก่/คอกสัตว์เล็ก", "chicken coop"}});
        VARIANTS.put("market", new String[][] {{"ตลาดผ้าทางสี", "striped awnings"}, {"ตลาดผ้าสีเดียว", "single-colour awnings"}, {"ตลาดไม้ + น้ำพุ", "wooden stalls + fountain"}});
    }

    public static boolean isMoreType(String id) {
        return id != null && VARIANTS.containsKey(id);
    }

    public static Plan generate(Opts o) {
        return switch (o.type) {
            case "hospital" -> hotel(o, "hospital");
            case "prison" -> prison(o);
            case "school" -> school(o);
            case "library" -> library(o);
            case "church" -> church(o);
            case "barn" -> barn(o);
            case "market" -> market(o);
            default -> hotel(o, "hotel");
        };
    }

    record B(String wid, Wood wd, int s, Pal P, int v) {}

    static B base(Opts o, String def) {
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        int s = Gen.clamp(o.sizeOr(1), 0, 2);
        return new B(wid, wd, s, Gen.palette(o, def, wid, wd), Gen.variantOf(o));
    }

    static final class G {
        int W, D, F, h = 3;
        Pal P;
        Wood wd;
        boolean tall;
        Integer ladX, ladZ;
        String pane, floor, ceil;
        Boolean pillars;
    }

    record Shell(int c, int top, int rowH, int ladX, int ladZ) {}

    static boolean winAt(int t, int len) {
        return t % 4 == 2 && t > 0 && t < len - 1;
    }

    static Shell shell(Ctx S, G g) {
        int W = g.W, D = g.D, F = g.F;
        Pal P = g.P;
        int c = (W - 1) / 2;
        int rowH = g.h + 1;
        int ladX = g.ladX != null ? g.ladX : W - 2;
        int ladZ = g.ladZ != null ? g.ladZ : D - 2;
        String pane = g.pane != null ? g.pane : M + "glass_pane";
        for (int f = 0; f < F; f++) {
            int y0 = rowH * f;
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (f > 0 && x == ladX && z == ladZ) continue;
                    boolean perim = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                    S.put(x, y0, z, perim ? P.wall : (g.floor != null ? g.floor : P.floor), 1);
                }
            for (int row = 1; row <= g.h; row++)
                for (int x = 0; x < W; x++)
                    for (int z = 0; z < D; z++) {
                        boolean perim = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                        if (!perim) continue;
                        boolean corner = (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
                        int t = z == 0 || z == D - 1 ? x : z;
                        int len = z == 0 || z == D - 1 ? W : D;
                        boolean isPillar = corner || (t % 4 == 0 && !Boolean.FALSE.equals(g.pillars));
                        boolean win = !isPillar && winAt(t, len) && (g.tall ? row >= 2 : row == 2) && !(f == 0 && z == 0 && Math.abs(x - c) <= 1);
                        S.put(x, y0 + row, z, isPillar ? P.pillar : win ? pane : P.wall, 1, isPillar && P.wood ? St.axis("y") : null);
                    }
        }
        int top = rowH * F;
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                boolean perim = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                S.put(x, top, z, perim ? P.wall : (g.ceil != null ? g.ceil : g.wd.planks), 1);
            }
        return new Shell(c, top, rowH, ladX, ladZ);
    }

    static void ladders(Ctx S, int F, int rowH, int x, int z, String f) {
        for (int k = 0; k < F - 1; k++)
            for (int y = rowH * k + 1; y <= rowH * (k + 1); y++) S.put(x, y, z, M + "ladder", 3, St.f(f));
    }

    static void roofOn(Ctx S, G g, int top, int kind, String roofStairs, String roofFull) {
        int W = g.W, D = g.D;
        Pal P = g.P;
        if (kind == 1) Gen.gableRoof(S, W, D, top + 1, roofStairs, roofFull, P.wall);
        else if (kind == 2) Gen.hipRoof(S, -1, W, -1, D, top + 1, roofStairs, roofFull);
        else {
            Gen.ring(S, 0, W - 1, 0, D - 1, top + 1, P.slab, 2);
            for (int[] p : new int[][] {{0, 0}, {W - 1, 0}, {0, D - 1}, {W - 1, D - 1}}) S.put(p[0], top + 1, p[1], P.pillar, 2);
        }
    }

    static void canopy(Ctx S, Pal P, int c) {
        int y = 3;
        for (int x : new int[] {c - 2, c + 2})
            for (int yy = 0; yy < y; yy++) S.put(x, yy, -3, P.pillar, 3, P.wood ? St.axis("y") : null);
        for (int x = c - 3; x <= c + 3; x++)
            for (int z = -3; z <= -1; z++) S.put(x, y, z, P.slab, 3, St.half("bottom"));
        S.put(c - 1, y - 1, -2, M + "lantern", 4, St.hanging(true));
        S.put(c + 1, y - 1, -2, M + "lantern", 4, St.hanging(true));
        for (int x = c - 1; x <= c + 1; x++) S.put(x, 0, -1, P.stairs, 3, St.fh("pz", "bottom"));
    }

    // ------------------------------------------------------------------ hotel / hospital
    static Plan hotel(Opts o, String kind) {
        boolean hosp = kind.equals("hospital");
        B b = base(o, hosp ? "quartz" : "brick");
        Wood wd = b.wd;
        Pal P = b.P;
        int s = b.s, v = b.v;
        int W = Gen.dim(o.w, new int[] {15, 17, 21}[s], 15, 41);
        int D = Gen.dim(o.d, new int[] {17, 21, 27}[s], 15, 41);
        int F = Gen.clamp(o.floors != null ? o.floors : new int[] {2, 3, 4}[s], 1, 8);
        G g = new G();
        g.W = W;
        g.D = D;
        g.F = F;
        g.P = P;
        g.wd = wd;
        g.tall = false;
        g.ladX = (W - 1) / 2 + 1;
        g.ladZ = D - 2;
        Ctx S = new Ctx();
        Shell sh = shell(S, g);
        int c = sh.c(), top = sh.top(), rowH = sh.rowH();
        ladders(S, F, rowH, c + 1, D - 2, "nz");
        boolean light = Opts.notOff(o.light), furn = Opts.notOff(o.furniture);
        for (int f = 0; f < F; f++) {
            int y0 = rowH * f;
            int zStart = f == 0 ? 8 : 1;
            for (int px : new int[] {c - 2, c + 2})
                for (int z = zStart - (f == 0 ? 1 : 0); z <= D - 2; z++)
                    for (int row = 1; row <= 3; row++) S.put(px, y0 + row, z, P.wall, 1);
            if (f == 0) {
                for (int x = 1; x <= W - 2; x++)
                    for (int row = 1; row <= 3; row++)
                        if (Math.abs(x - c) > 1) S.put(x, y0 + row, 7, P.wall, 1);
                for (int z = 1; z <= 6; z++) S.put(c, y0 + 1, z, M + "red_carpet", 5, null, Ex.opt());
                S.put(c - 2, y0 + 1, 3, M + "lectern", 5, St.f("pz"), Ex.opt());
                for (int x : new int[] {c - 3, c - 4}) S.put(x, y0 + 1, 3, wd.slab, 5, St.half("bottom"), Ex.opt());
                for (int x : new int[] {2, W - 3}) {
                    S.put(x, y0 + 1, 2, wd.stairs, 5, St.fh("pz", "bottom"), Ex.opt());
                    S.put(x, y0 + 1, 3, wd.stairs, 5, St.fh("pz", "bottom"), Ex.opt());
                }
                S.put(2, y0 + 1, 6, M + "barrel", 5, null, Ex.opt());
                S.put(W - 3, y0 + 1, 6, M + "chest", 5, St.f("nz"), Ex.opt());
                if (light)
                    for (int[] p : new int[][] {{3, 3}, {W - 4, 3}, {c, 5}}) S.put(p[0], y0 + 3, p[1], M + "lantern", 4, St.hanging(true));
                Gen.door(S, wd, c, 1, 0, "pz");
            }
            int k = 0;
            for (int z = zStart; z + 2 <= D - 3; z += 4, k++) {
                int zm = z + 1;
                for (int side : new int[] {0, 1}) {
                    int x0 = side == 0 ? 1 : c + 3;
                    int x1 = side == 0 ? c - 3 : W - 2;
                    for (int x = x0; x <= x1; x++)
                        for (int row = 1; row <= 3; row++) S.put(x, y0 + row, z + 3, P.wall, 1);
                    int dx = side == 0 ? c - 2 : c + 2;
                    if (hosp) Gen.ironDoor(S, dx, y0 + 1, zm, side == 0 ? "px" : "nx");
                    else Gen.door(S, wd, dx, y0 + 1, zm, side == 0 ? "px" : "nx");
                    if (furn) {
                        String[] roles = hosp ? new String[] {"brewing", "storage"} : k % 3 == 2 ? new String[] {"storage", "kitchen"} : new String[] {"bedroom"};
                        int beds = hosp ? 2 : k % 3 == 2 ? 0 : 2;
                        Gen.furnishRoom(new Gen.Room(y0, x0, x1, z, z + 2, Gen.keys((side == 0 ? c - 3 : c + 3) + "," + zm), roles, beds,
                                hosp ? M + "white_carpet" : M + "red_carpet", wd), S);
                    }
                    if (light) S.put(Math.floorDiv(x0 + x1, 2), y0 + 3, zm, M + "lantern", 4, St.hanging(true));
                }
                if (light) S.put(c, y0 + 3, zm, M + "lantern", 4, St.hanging(true));
            }
        }
        roofOn(S, g, top, v % 3, P.stairs, P.wall);
        if (v % 3 == 0 && !hosp) {
            int x0 = c - 3, x1 = c + 3, z0 = D - 10, z1 = D - 4;
            for (int x = x0 - 1; x <= x1 + 1; x++)
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
                    if (edge) S.put(x, top + 1, z, P.wall, 1);
                    else S.put(x, top + 1, z, M + "water", 3, null, Ex.item(null));
                }
        }
        if (hosp) {
            int cy = top - 2;
            for (int d = -2; d <= 2; d++) {
                S.put(c + d, cy, 0, M + "red_concrete", 3, null, Ex.opt());
                S.put(c, cy + d, 0, M + "red_concrete", 3, null, Ex.opt());
            }
        }
        canopy(S, P, c);
        return Gen.finalize(S, W, D, Base.of().fill(M + "stone_bricks").clear(-4, W + 3, -4, D + 3));
    }

    // ------------------------------------------------------------------ prison
    static Plan prison(Opts o) {
        B b = base(o, "stone");
        Wood wd = b.wd;
        int s = b.s, v = b.v;
        Pal P = v % 3 == 1 && o.matOr0() == 0 ? Gen.palette(o.withMat(5), "stone", "oak", wd) : b.P;
        int W = Gen.dim(o.w, new int[] {21, 25, 31}[s], 19, 41);
        int D = Gen.dim(o.d, new int[] {23, 29, 35}[s], 21, 45);
        int F = Gen.clamp(o.floors != null ? o.floors : new int[] {1, 2, 2}[s], 1, 3);
        int c = (W - 1) / 2;
        Ctx S = new Ctx();
        int WALL_H = 5;
        boolean light = Opts.notOff(o.light), furn = Opts.notOff(o.furniture);
        for (int y = 0; y < WALL_H; y++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++)
                    if (x == 0 || x == W - 1 || z == 0 || z == D - 1) S.put(x, y, z, P.wall, 1);
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                if ((x == 0 || x == W - 1 || z == 0 || z == D - 1) && (x + z) % 2 == 0) S.put(x, WALL_H, z, P.wall, 2);
        int TH = v % 3 == 2 ? 12 : 8;
        Object[][] towers = {{0, 0, "px", 2}, {W - 5, 0, "nx", 2}, {0, D - 5, "px", D - 3}, {W - 5, D - 5, "nx", D - 3}};
        for (Object[] t : towers) {
            int tx = (int) t[0], tz = (int) t[1];
            String fdir = (String) t[2];
            for (int y = 0; y <= TH; y++)
                for (int x = tx; x < tx + 5; x++)
                    for (int z = tz; z < tz + 5; z++) {
                        boolean edge = x == tx || x == tx + 4 || z == tz || z == tz + 4;
                        if (y == 0 || y == TH) S.put(x, y, z, P.wall, 1);
                        else if (edge) S.put(x, y, z, P.wall, 1);
                    }
            for (int x = tx; x < tx + 5; x++)
                for (int z = tz; z < tz + 5; z++)
                    if ((x + z) % 2 == 0 && (x == tx || x == tx + 4 || z == tz || z == tz + 4)) S.put(x, TH + 1, z, P.wall, 2);
            for (int[] p : new int[][] {{tx + 2, tz}, {tx + 2, tz + 4}, {tx, tz + 2}, {tx + 4, tz + 2}}) S.put(p[0], TH - 3, p[1], M + "iron_bars", 1);
            int dx = fdir.equals("px") ? tx + 4 : tx;
            Gen.door(S, wd, dx, 1, tz + 2, fdir);
            int lx = fdir.equals("px") ? tx + 3 : tx + 1;
            for (int y = 1; y <= TH; y++) S.put(lx, y, tz + 3, M + "ladder", 3, St.f("pz"));
            S.del(lx, TH, tz + 3);
            if (light) {
                S.put(tx + 2, TH - 1, tz + 2, M + "lantern", 4, St.hanging(true));
                S.put(tx + 2, 3, tz + 2, M + "lantern", 4, St.hanging(true));
            }
        }
        for (int y = 1; y <= 3; y++)
            for (int x : new int[] {c - 1, c, c + 1}) S.put(x, y, 0, M + "iron_bars", 1);
        Gen.ironDoor(S, c, 1, 0, "pz");
        S.put(c - 1, 1, 0, M + "iron_bars", 1);
        S.put(c + 1, 1, 0, M + "iron_bars", 1);
        S.put(c - 2, 4, -1, M + "lantern", 4, St.hanging(false));
        S.put(c + 2, 4, -1, M + "lantern", 4, St.hanging(false));
        int bx0 = 5, bx1 = W - 6, bz0 = 7, bz1 = D - 6;
        int bc = Math.floorDiv(bx0 + bx1, 2);
        int rowH = 4;
        int top = rowH * F;
        java.util.function.BiPredicate<Integer, Integer> inBlock = (x, z) -> x >= bx0 && x <= bx1 && z >= bz0 && z <= bz1;
        java.util.function.BiPredicate<Integer, Integer> bperim = (x, z) -> inBlock.test(x, z) && (x == bx0 || x == bx1 || z == bz0 || z == bz1);
        for (int f = 0; f < F; f++) {
            int y0 = rowH * f;
            for (int x = bx0; x <= bx1; x++)
                for (int z = bz0; z <= bz1; z++) {
                    if (f > 0 && x == bc + 1 && z == bz1 - 1) continue;
                    S.put(x, y0, z, bperim.test(x, z) ? P.wall : P.floor, 1);
                }
            for (int row = 1; row <= 3; row++)
                for (int x = bx0; x <= bx1; x++)
                    for (int z = bz0; z <= bz1; z++)
                        if (bperim.test(x, z))
                            S.put(x, y0 + row, z, row == 2 && (x - bx0) % 4 == 2 && (z == bz1 || x == bx0 || x == bx1) ? M + "iron_bars" : P.wall, 1);
            int zCells = f == 0 ? bz0 + 6 : bz0 + 1;
            if (f == 0) {
                for (int x = bx0 + 1; x <= bx1 - 1; x++)
                    for (int row = 1; row <= 3; row++) S.put(x, y0 + row, bz0 + 5, Math.abs(x - bc) <= 0 ? M + "iron_bars" : P.wall, 1);
                Gen.ironDoor(S, bc, y0 + 1, bz0 + 5, "pz");
                if (furn)
                    Gen.furnishRoom(new Gen.Room(y0, bx0 + 1, bx1 - 1, bz0 + 1, bz0 + 4, Gen.keys(bc + "," + (bz0 + 4), bc + "," + (bz0 + 1)),
                            new String[] {"workshop", "storage", "kitchen"}, 1, M + "gray_carpet", wd), S);
                if (light)
                    for (int x : new int[] {bx0 + 3, bx1 - 3}) S.put(x, y0 + 3, bz0 + 2, M + "lantern", 4, St.hanging(true));
            }
            int lPart = bc - 2, rPart = bc + 2;
            for (int px : new int[] {lPart, rPart})
                for (int z = zCells; z <= bz1 - 1; z++)
                    for (int row = 1; row <= 3; row++) S.put(px, y0 + row, z, M + "iron_bars", 1);
            for (int z = zCells; z + 2 <= bz1 - 2; z += 4) {
                int zm = z + 1;
                for (int side : new int[] {0, 1}) {
                    int x0 = side == 0 ? bx0 + 1 : rPart + 1;
                    int x1 = side == 0 ? lPart - 1 : bx1 - 1;
                    for (int x = x0; x <= x1; x++)
                        for (int row = 1; row <= 3; row++) S.put(x, y0 + row, z + 3, P.wall, 1);
                    int px = side == 0 ? lPart : rPart;
                    Gen.ironDoor(S, px, y0 + 1, zm, side == 0 ? "px" : "nx");
                    if (furn) {
                        if (side == 0) Gen.bed(S, x0, y0 + 1, zm, "nx");
                        else Gen.bed(S, x1, y0 + 1, zm, "px");
                        S.put(side == 0 ? x1 : x0, y0 + 1, z, M + "cauldron", 5, null, Ex.opt());
                    }
                    if (light) S.put(Math.floorDiv(x0 + x1, 2), y0 + 3, zm, M + "lantern", 4, St.hanging(true));
                }
                if (light) S.put(bc, y0 + 3, zm, M + "lantern", 4, St.hanging(true));
            }
        }
        for (int k = 0; k < F - 1; k++)
            for (int y = rowH * k + 1; y <= rowH * (k + 1); y++) S.put(bc + 1, y, bz1 - 1, M + "ladder", 3, St.f("nz"));
        for (int x = bx0; x <= bx1; x++)
            for (int z = bz0; z <= bz1; z++) S.put(x, top, z, P.wall, 1);
        Gen.ring(S, bx0, bx1, bz0, bz1, top + 1, P.slab, 2);
        Gen.ironDoor(S, bc, 1, bz0, "pz");
        for (int z = 1; z < bz0; z++) S.put(bc, -1, z, M + "stone_bricks", 1, null, Ex.opt());
        if (light)
            for (int x = 6; x < W - 6; x += 6) {
                S.put(x, WALL_H + 1, 0, M + "lantern", 4, St.hanging(false));
                S.put(x, WALL_H + 1, D - 1, M + "lantern", 4, St.hanging(false));
            }
        return Gen.finalize(S, W, D, Base.of().fill(M + "cobblestone").clear(-2, W + 1, -3, D + 1));
    }

    // ------------------------------------------------------------------ school
    static Plan school(Opts o) {
        B b = base(o, "brick");
        Wood wd = b.wd;
        Pal P = b.P;
        int s = b.s, v = b.v;
        int W = Gen.dim(o.w, new int[] {19, 23, 29}[s], 19, 41);
        int D = Gen.dim(o.d, new int[] {15, 19, 25}[s], 15, 41);
        int F = Gen.clamp(o.floors != null ? o.floors : new int[] {1, 2, 2}[s], 1, 4);
        G g = new G();
        g.W = W;
        g.D = D;
        g.F = F;
        g.P = P;
        g.wd = wd;
        g.tall = true;
        g.ladX = (W - 1) / 2 + 1;
        g.ladZ = D - 2;
        Ctx S = new Ctx();
        Shell sh = shell(S, g);
        int c = sh.c(), top = sh.top(), rowH = sh.rowH();
        ladders(S, F, rowH, c + 1, D - 2, "nz");
        boolean light = Opts.notOff(o.light), furn = Opts.notOff(o.furniture);
        int[] ps = {c - 2, c + 2};
        for (int f = 0; f < F; f++) {
            int y0 = rowH * f;
            for (int px : ps)
                for (int z = 1; z <= D - 2; z++)
                    for (int row = 1; row <= 3; row++) S.put(px, y0 + row, z, P.wall, 1);
            for (int z = 1; z + 4 <= D - 2; z += 6) {
                int zm = z + 2;
                for (int side : new int[] {0, 1}) {
                    int x0 = side == 0 ? 1 : c + 3;
                    int x1 = side == 0 ? c - 3 : W - 2;
                    for (int x = x0; x <= x1; x++)
                        for (int row = 1; row <= 3; row++) S.put(x, y0 + row, z + 5, P.wall, 1);
                    int px = side == 0 ? c - 2 : c + 2;
                    Gen.door(S, wd, px, y0 + 1, zm + 2, side == 0 ? "px" : "nx");
                    for (int zz = zm - 1; zz <= zm + 1; zz++) {
                        S.put(px, y0 + 2, zz, M + "black_concrete", 1);
                        S.put(px, y0 + 3, zz, M + "black_concrete", 1);
                    }
                    if (furn) {
                        String toBoard = side == 0 ? "px" : "nx";
                        String away = side == 0 ? "nx" : "px";
                        int dir = side == 0 ? 1 : -1;
                        int bx = px - dir;
                        S.put(bx - dir, y0 + 1, zm, M + "lectern", 5, St.f(toBoard.equals("px") ? "nx" : "px"), Ex.opt());
                        for (int col : new int[] {3, 5}) {
                            int dx = bx - dir * col;
                            if (dx < x0 || dx > x1) continue;
                            for (int zz = zm - 1; zz <= zm + 1; zz++) {
                                S.put(dx, y0 + 1, zz, wd.slab, 5, St.half("bottom"), Ex.opt());
                                int cx = dx - dir;
                                if (cx >= x0 && cx <= x1) S.put(cx, y0 + 1, zz, wd.stairs, 5, St.fh(away, "bottom"), Ex.opt());
                            }
                        }
                    }
                    if (light) {
                        S.put(Math.floorDiv(x0 + x1, 2), y0 + 3, zm - 1, M + "lantern", 4, St.hanging(true));
                        S.put(Math.floorDiv(x0 + x1, 2), y0 + 3, zm + 2, M + "lantern", 4, St.hanging(true));
                    }
                }
                if (light) S.put(c, y0 + 3, zm, M + "lantern", 4, St.hanging(true));
            }
        }
        Gen.door(S, wd, c, 1, 0, "pz");
        int fx = c - 5;
        for (int y = 0; y <= 8; y++) S.put(fx, y, -3, wd.fence, 3);
        for (int y = 6; y <= 7; y++)
            for (int x = fx + 1; x <= fx + 3; x++) S.put(x, y, -3, y == 6 ? M + "white_wool" : M + "red_wool", 3);
        S.put(fx, 9, -3, M + "lantern", 4, St.hanging(false));
        roofOn(S, g, top, v % 3, P.stairs, P.wall);
        canopy(S, P, c);
        return Gen.finalize(S, W, D, Base.of().fill(M + "stone_bricks").clear(-4, W + 3, -5, D + 3));
    }

    // ------------------------------------------------------------------ library
    static Plan library(Opts o) {
        B b = base(o, "stone");
        Wood wd = b.wd;
        Pal P = b.P;
        int s = b.s, v = b.v;
        int W = Gen.dim(o.w, new int[] {15, 19, 25}[s], 13, 41);
        int D = Gen.dim(o.d, new int[] {19, 23, 29}[s], 15, 41);
        int F = Gen.clamp(o.floors != null ? o.floors : new int[] {1, 2, 2}[s], 1, 4);
        G g = new G();
        g.W = W;
        g.D = D;
        g.F = F;
        g.P = P;
        g.wd = wd;
        g.tall = false;
        g.floor = wd.planks;
        Ctx S = new Ctx();
        Shell sh = shell(S, g);
        int c = sh.c(), top = sh.top(), rowH = sh.rowH();
        ladders(S, F, rowH, W - 2, D - 2, "nz");
        boolean light = Opts.notOff(o.light), furn = Opts.notOff(o.furniture);
        for (int f = 0; f < F; f++) {
            int y0 = rowH * f;
            for (int x : new int[] {3, 6, W - 4, W - 7})
                for (int z = 4; z <= D - 10; z++) {
                    if ((z - 4) % 6 == 5) continue;
                    S.put(x, y0 + 1, z, M + "bookshelf", 5, null, Ex.opt());
                    S.put(x, y0 + 2, z, M + "bookshelf", 5, null, Ex.opt());
                }
            for (int x : new int[] {1, W - 2})
                for (int z = 3; z <= D - 4; z += 1) {
                    if (z == D - 2) continue;
                    S.put(x, y0 + 1, z, M + "bookshelf", 5, null, Ex.opt());
                    S.put(x, y0 + 2, z, M + "bookshelf", 5, null, Ex.opt());
                }
            if (furn) {
                for (int x : new int[] {c - 3, c + 3})
                    for (int z : new int[] {3, 5}) {
                        S.put(x, y0 + 1, z, M + "lectern", 5, St.f("nz"), Ex.opt());
                        S.put(x, y0 + 1, z - 1, wd.stairs, 5, St.fh("nz", "bottom"), Ex.opt());
                    }
                Gen.furnishRoom(new Gen.Room(y0, 2, W - 3, D - 9, D - 2, Gen.keys(c + "," + (D - 9), (W - 2) + "," + (D - 2), (W - 3) + "," + (D - 2)),
                        f == 0 ? new String[] {"enchant", "storage"} : new String[] {"enchant", "brewing"}, 0, M + "red_carpet", wd), S);
            }
            if (light)
                for (int z = 4; z <= D - 4; z += 5)
                    for (int x : new int[] {c, 4, W - 5}) S.put(x, y0 + 3, z, M + "lantern", 4, St.hanging(true));
        }
        Gen.door(S, wd, c, 1, 0, "pz");
        roofOn(S, g, top, v % 3, P.stairs, P.wall);
        canopy(S, P, c);
        return Gen.finalize(S, W, D, Base.of().fill(M + "stone_bricks").clear(-4, W + 3, -4, D + 3));
    }

    // ------------------------------------------------------------------ church
    static Plan church(Opts o) {
        B b = base(o, "stone");
        Wood wd = b.wd;
        int s = b.s, v = b.v;
        int mat = o.matOr0() == 0 ? (v % 3 == 1 ? 2 : v % 3 == 2 ? 4 : 1) : o.mat;
        Pal P = Gen.palette(o.withMat(mat), "stone", Gen.woodId(o.wood), wd);
        int W = Gen.dim(o.w, new int[] {11, 13, 15}[s], 9, 25);
        int D = Gen.dim(o.d, new int[] {19, 23, 29}[s], 15, 41);
        int c = (W - 1) / 2;
        int H = 7;
        Ctx S = new Ctx();
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                boolean perim = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                S.put(x, 0, z, perim ? P.wall : (x + z) % 2 == 0 ? M + "polished_andesite" : M + "stone_bricks", 1);
            }
        for (int row = 1; row <= H; row++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    boolean perim = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                    if (!perim) continue;
                    int t = z == 0 || z == D - 1 ? x : z;
                    int len = z == 0 || z == D - 1 ? W : D;
                    boolean buttress = t % 4 == 0 || (x == 0 && z == 0);
                    boolean win = !buttress && t % 4 == 2 && t > 0 && t < len - 1 && row >= 3 && row <= 6 && z != 0;
                    S.put(x, row, z, buttress ? P.pillar : win ? M + "glass_pane" : P.wall, 1, buttress && P.wood ? St.axis("y") : null);
                }
        Gen.gableRoof(S, W, D, H + 1, P.stairs, P.wall, P.wall);
        if (Opts.notOff(o.furniture))
            for (int z = 4; z <= D - 9; z += 2)
                for (int x : new int[] {c - 3, c - 2, c + 2, c + 3}) S.put(x, 1, z, wd.stairs, 5, St.fh("nz", "bottom"), Ex.opt());
        for (int z = 1; z <= D - 3; z++) S.put(c, 1, z, M + "red_carpet", 5, null, Ex.opt());
        for (int x = c - 2; x <= c + 2; x++)
            for (int z = D - 5; z <= D - 3; z++) S.put(x, 1, z, P.slab, 1, St.half("bottom"));
        S.put(c, 1, D - 4, M + "quartz_block", 1);
        S.put(c, 2, D - 4, M + "lectern", 5, St.f("nz"), Ex.opt());
        for (int x : new int[] {c - 2, c + 2}) S.put(x, 2, D - 3, M + "lantern", 4, St.hanging(false));
        if (Opts.notOff(o.light))
            for (int z = 4; z <= D - 6; z += 5)
                for (int x : new int[] {c - 4, c + 4}) S.put(x, 5, z, M + "lantern", 4, St.hanging(true));
        int tx0 = c - 2, tx1 = c + 2, tz0 = -4, tz1 = 0;
        int TH = 14;
        for (int y = 0; y <= TH; y++)
            for (int x = tx0; x <= tx1; x++)
                for (int z = tz0; z <= tz1; z++) {
                    boolean edge = x == tx0 || x == tx1 || z == tz0 || z == tz1;
                    if (y == 0) S.put(x, y, z, P.wall, 1);
                    else if (edge) S.put(x, y, z, P.wall, 1);
                }
        for (int x = tx0; x <= tx1; x++)
            for (int z = tz0; z <= tz1; z++)
                if (!(x == tx0 + 1 && z == tz0 + 1)) S.put(x, 8, z, P.wall, 1);
        S.put(c, 9, tz0 + 2, M + "bell", 5, null, Ex.opt());
        Gen.hipRoof(S, tx0 - 1, tx1 + 1, tz0 - 1, tz1 + 1, TH + 1, P.stairs, P.wall);
        for (int y = 1; y <= 9; y++) S.put(tx0 + 1, y, tz0 + 1, M + "ladder", 3, St.f("pz"));
        for (int y = 10; y <= 12; y++) {
            S.del(c, y, tz0);
            S.del(tx0, y, tz0 + 2);
            S.del(tx1, y, tz0 + 2);
        }
        Gen.door(S, wd, c, 1, tz0, "pz");
        for (int y = 1; y <= 3; y++)
            for (int x : new int[] {c - 1, c, c + 1}) S.del(x, y, 0);
        for (int x = c - 1; x <= c + 1; x++) S.put(x, 4, 0, P.stairs, 1, St.fh("pz", "top"));
        for (int x = c - 1; x <= c + 1; x++) S.put(x, 0, tz0 - 1, P.stairs, 3, St.fh("pz", "bottom"));
        return Gen.finalize(S, W, D, Base.of().fill(M + "stone_bricks").clear(-2, W + 1, -7, D + 1));
    }

    // ------------------------------------------------------------------ barn / stable / coop
    static Plan barn(Opts o) {
        B b = base(o, "wood");
        Wood wd = b.wd;
        int s = b.s, v = b.v;
        int kind = v % 3;
        boolean redWall = kind == 0;
        Pal P = new Pal(kind == 0 ? M + "red_terracotta" : wd.planks, M + (kind == 0 ? "white_terracotta" : "stripped_" + Gen.woodId(o.wood) + "_log"),
                wd.stairs, wd.slab, wd.planks, !redWall);
        int W = Gen.dim(o.w, kind == 2 ? new int[] {9, 11, 13}[s] : new int[] {11, 13, 17}[s], 9, 31);
        int D = Gen.dim(o.d, kind == 2 ? new int[] {9, 11, 13}[s] : new int[] {15, 19, 25}[s], 9, 41);
        int c = (W - 1) / 2;
        int H = kind == 2 ? 3 : 5;
        Ctx S = new Ctx();
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                boolean perim = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                S.put(x, 0, z, perim ? M + "cobblestone" : M + "coarse_dirt", 1);
            }
        for (int row = 1; row <= H; row++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    boolean perim = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                    if (!perim) continue;
                    int t = z == 0 || z == D - 1 ? x : z;
                    boolean post = t % 4 == 0;
                    boolean win = !post && t % 4 == 2 && row == 3 && !(z == 0 && Math.abs(x - c) <= 2);
                    S.put(x, row, z, post ? P.pillar : win ? M + "glass_pane" : P.wall, 1);
                }
        Gen.gableRoof(S, W, D, H + 1, wd.stairs, wd.planks, P.wall);
        for (int y = 1; y <= 3; y++)
            for (int x = c - 1; x <= c + 1; x++) S.del(x, y, 0);
        Gen.door(S, wd, c - 1, 1, 0, "pz");
        Gen.door(S, wd, c + 1, 1, 0, "pz");
        S.put(c, 1, 0, wd.fence, 1);
        S.put(c, 2, 0, wd.fence, 1);
        for (int x : new int[] {c - 1, c + 1}) S.put(x, 3, 0, wd.planks, 1);
        if (Opts.notOff(o.furniture)) {
            int step = kind == 1 ? 3 : 4;
            for (int z = 3; z <= D - 4; z += step) {
                for (int side : new int[] {0, 1}) {
                    int wx = side == 0 ? 1 : W - 2;
                    int px = side == 0 ? 4 : W - 5;
                    for (int zz = z; zz <= z + step - 2 && zz <= D - 3; zz++) S.put(px, 1, zz, wd.fence, 5, null, Ex.opt());
                    S.put(px, 1, z + step - 1 <= D - 3 ? z + step - 1 : z, wd.fence, 5, null, Ex.opt());
                    S.put(wx, 1, z, M + (kind == 2 ? "composter" : "hay_block"), 5, null, Ex.opt());
                    S.put(wx, 1, z + 1, kind == 1 ? M + "barrel" : M + "hay_block", 5, null, Ex.opt());
                }
            }
            for (int x = 2; x <= W - 3; x++) {
                S.put(x, 1, D - 2, M + "hay_block", 5, null, Ex.opt());
                if (x % 2 == 0) S.put(x, 2, D - 2, M + "hay_block", 5, null, Ex.opt());
            }
            S.put(c, 1, D - 4, M + "cauldron", 5, null, Ex.opt());
        }
        if (Opts.notOff(o.light))
            for (int z = 3; z <= D - 3; z += 5) S.put(c, H, z, M + "lantern", 4, St.hanging(true));
        int px0 = 0, px1 = W - 1, pz0 = -9, pz1 = -2;
        for (int x = px0; x <= px1; x++)
            for (int z = pz0; z <= pz1; z++)
                if (x == px0 || x == px1 || z == pz0 || z == pz1)
                    if (!(z == pz1 && Math.abs(x - c) <= 1)) S.put(x, 0, z, wd.fence, 3);
        for (int z = -1; z >= pz1 + 1; z--) S.put(c, -1, z, M + "coarse_dirt", 1);
        S.put(c, -1, -1, M + "coarse_dirt", 1);
        return Gen.finalize(S, W, D, Base.of().fill(M + "dirt").clear(-2, W + 1, -11, D + 1));
    }

    // ------------------------------------------------------------------ market
    static Plan market(Opts o) {
        B b = base(o, "wood");
        Wood wd = b.wd;
        int s = b.s, v = b.v;
        int W = Gen.dim(o.w, new int[] {21, 27, 33}[s], 17, 41);
        int D = Gen.dim(o.d, new int[] {13, 17, 21}[s], 11, 41);
        int c = (W - 1) / 2;
        Ctx S = new Ctx();
        String[] cols = {"red", "white", "blue", "yellow", "green", "orange"};
        int kind = v % 3;
        boolean light = Opts.notOff(o.light);
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) S.put(x, 0, z, (x + z) % 5 == 0 ? M + "stone_bricks" : M + "gravel", 1, null, new Ex().withOpt(false));
        int si = 0;
        for (int x = 1; x + 4 <= W - 2; x += 6, si++) {
            for (int row : new int[] {0, 1}) {
                int z0 = row == 0 ? 1 : D - 4;
                int z1 = z0 + 2;
                int front = row == 0 ? z1 : z0;
                for (int px : new int[] {x, x + 4})
                    for (int pz : new int[] {z0, z1})
                        for (int y = 1; y <= 3; y++) S.put(px, y, pz, wd.fence, 1);
                String col = kind == 1 ? cols[v % cols.length] : cols[(si + row) % cols.length];
                for (int xx = x; xx <= x + 4; xx++)
                    for (int zz = z0; zz <= z1; zz++) {
                        boolean striped = kind == 0 && (xx - x) % 2 == 1;
                        S.put(xx, 4, zz, M + (striped ? "white" : col) + "_wool", 2);
                    }
                for (int xx = x + 1; xx <= x + 3; xx++) {
                    S.put(xx, 1, front, kind == 2 ? wd.planks : M + "barrel", 5, null, Ex.opt());
                    S.put(xx, 2, front, xx == x + 2 ? M + "chest" : wd.slab, 5, xx == x + 2 ? St.f(row == 0 ? "pz" : "nz") : St.half("bottom"), Ex.opt());
                }
                S.put(x + 2, 1, row == 0 ? z0 : z1, M + "crafting_table", 5, null, Ex.opt());
                if (light) S.put(x + 2, 3, row == 0 ? z1 : z0, M + "lantern", 4, St.hanging(true));
            }
        }
        int fz = D / 2;
        for (int x = c - 2; x <= c + 2; x++)
            for (int z = fz - 2; z <= fz + 2; z++) {
                boolean edge = Math.abs(x - c) == 2 || Math.abs(z - fz) == 2;
                if (edge) S.put(x, 1, z, kind == 2 ? M + "prismarine_bricks" : M + "stone_brick_slab", 1, kind == 2 ? null : St.half("bottom"));
                else S.put(x, 0, z, M + "water", 3, null, Ex.item(null));
            }
        S.put(c, 1, fz, wd.fence, 1);
        S.put(c, 2, fz, wd.fence, 1);
        S.put(c, 3, fz, M + "lantern", 4, St.hanging(false));
        if (light)
            for (int x = 4; x < W - 3; x += 8) {
                if (Math.abs(x - c) <= 3) continue;
                for (int y = 1; y <= 3; y++) S.put(x, y, fz, wd.fence, 3);
                S.put(x, 4, fz, M + "lantern", 4, St.hanging(false));
            }
        return Gen.finalize(S, W, D, Base.of().fill(M + "dirt").clear(-1, W, -1, D));
    }
}
