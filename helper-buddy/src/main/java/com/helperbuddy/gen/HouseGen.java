package com.helperbuddy.gen;

import com.helperbuddy.gen.Gen.Blk;
import com.helperbuddy.gen.Gen.Ctx;
import com.helperbuddy.gen.Gen.Ex;
import com.helperbuddy.gen.Gen.Opts;
import com.helperbuddy.gen.Gen.Plan;
import com.helperbuddy.gen.Gen.St;
import com.helperbuddy.gen.Gen.Wood;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import static com.helperbuddy.gen.Gen.M;

/** Standard houses (cottage, medieval, modern, thai) and the entry point of every generator. */
public final class HouseGen {
    private HouseGen() {}

    public static final String[][] WOODS = {
            {"oak", "ไม้โอ๊ค", "Oak"}, {"spruce", "ไม้สปรูซ", "Spruce"}, {"birch", "ไม้เบิร์ช", "Birch"},
            {"jungle", "ไม้ป่าดิบ", "Jungle"}, {"acacia", "ไม้อะเคเชีย", "Acacia"}, {"dark_oak", "ไม้โอ๊คเข้ม", "Dark oak"},
            {"mangrove", "ไม้โกงกาง", "Mangrove"}, {"cherry", "ไม้เชอร์รี่", "Cherry"}, {"pale_oak", "ไม้โอ๊คซีด", "Pale oak"},
    };
    public static final int[][] SIZES = {{7, 7}, {9, 11}, {11, 13}};
    public static final String[][] SIZE_NAMES = {{"เล็ก", "small"}, {"กลาง", "medium"}, {"ใหญ่", "large"}};
    public static final String[][] STYLES = {
            {"บ้านไม้คันทรี", "Country cottage"}, {"บ้านยุคกลาง", "Medieval house"}, {"บ้านโมเดิร์น", "Modern house"}, {"เรือนไทย", "Thai stilt house"},
    };
    public static final String[][] ROOFS = {
            {"แนะนำตามสไตล์", "recommended"}, {"ไม้เดียวกับบ้าน", "same wood"}, {"ไม้สปรูซ", "spruce"}, {"ไม้โอ๊คเข้ม", "dark oak"},
            {"อิฐแดง", "red brick"}, {"กระเบื้องดีพสเลท", "deepslate tiles"}, {"อิฐหิน", "stone bricks"},
    };

    /** Every generator: standard houses, extra types, statues and the portal. */
    public static Plan generate(Opts o) {
        if (ExtraGen.isExtraType(o)) return ExtraGen.generateExtra(o);
        if (o.style != null && o.style == 2) return modern(o);
        return standard(o);
    }

    // ------------------------------------------------------------------ style config
    static String frameLog(String w) {
        return switch (w) {
            case "oak", "birch", "dark_oak" -> M + "spruce_log";
            case "cherry" -> M + "cherry_log";
            default -> M + "dark_oak_log";
        };
    }

    static int recommendedRoof(int style, String w) {
        if (style == 1) return 5;
        if (style == 3) return 4;
        return w.equals("spruce") || w.equals("dark_oak") ? 3 : 2;
    }

    record RoofSet(String stairs, String slab, String full) {}

    static RoofSet roofSet(int opt, String w, int style) {
        if (opt <= 0 || opt > 6) opt = recommendedRoof(style, w);
        return switch (opt) {
            case 2 -> new RoofSet(M + "spruce_stairs", M + "spruce_slab", M + "spruce_planks");
            case 3 -> new RoofSet(M + "dark_oak_stairs", M + "dark_oak_slab", M + "dark_oak_planks");
            case 4 -> new RoofSet(M + "brick_stairs", M + "brick_slab", M + "brick_block");
            case 5 -> new RoofSet(M + "deepslate_tile_stairs", M + "deepslate_tile_slab", M + "deepslate_tiles");
            case 6 -> new RoofSet(M + "stone_brick_stairs", M + "stone_brick_slab", M + "stone_bricks");
            default -> {
                Wood s = new Wood(w);
                yield new RoofSet(s.stairs, s.slab, s.planks);
            }
        };
    }

    static final class Cfg {
        int stilts;
        String foundation, floor, beam, ceiling, window, roofType, stepId, chimney, gableWall, carpet, flatRoof, roofFill;
        IntFunction<String> frame, wall;
        int[] windowRows;
        int pitch;
        boolean stepSlab, veranda;
    }

    static Cfg styleCfg(Opts o) {
        String wid = Gen.woodId(o.wood);
        Wood w = new Wood(wid);
        Cfg c = new Cfg();
        int style = o.style == null ? 0 : o.style;
        switch (style) {
            case 1 -> {
                String timber = wid.equals("spruce") || wid.equals("dark_oak") ? w.log : M + "dark_oak_log";
                String plaster = M + "white_terracotta";
                c.stilts = 0;
                c.foundation = M + "stone_bricks";
                c.floor = w.planks;
                c.frame = f -> timber;
                c.wall = f -> f == 0 ? M + "stone_bricks" : plaster;
                c.beam = timber;
                c.ceiling = w.planks;
                c.window = M + "glass_pane";
                c.windowRows = new int[] {2};
                c.roofType = "gable";
                c.pitch = 1;
                c.stepId = M + "stone_brick_stairs";
                c.stepSlab = false;
                c.chimney = M + "stone_bricks";
                c.veranda = false;
                c.gableWall = plaster;
                c.carpet = M + "red_carpet";
                c.flatRoof = "";
                c.roofFill = w.planks;
            }
            case 2 -> {
                c.stilts = 0;
                c.foundation = M + "gray_concrete";
                c.floor = w.planks;
                c.frame = f -> M + "gray_concrete";
                c.wall = f -> M + "smooth_quartz";
                c.beam = M + "gray_concrete";
                c.ceiling = M + "smooth_quartz";
                c.window = M + "light_gray_stained_glass";
                c.windowRows = new int[] {1, 2};
                c.roofType = "flat";
                c.pitch = 0;
                c.stepId = M + "smooth_quartz_slab";
                c.stepSlab = true;
                c.chimney = null;
                c.veranda = false;
                c.gableWall = M + "smooth_quartz";
                c.carpet = M + "white_carpet";
                c.flatRoof = M + "smooth_quartz_slab";
                c.roofFill = M + "smooth_quartz";
            }
            case 3 -> {
                c.stilts = 3;
                c.foundation = w.log;
                c.floor = w.planks;
                c.frame = f -> w.log;
                c.wall = f -> w.planks;
                c.beam = w.log;
                c.ceiling = w.planks;
                c.window = M + "glass_pane";
                c.windowRows = new int[] {2};
                c.roofType = "gable";
                c.pitch = 2;
                c.stepId = w.stairs;
                c.stepSlab = false;
                c.chimney = null;
                c.veranda = true;
                c.gableWall = w.planks;
                c.carpet = M + "yellow_carpet";
                c.flatRoof = "";
                c.roofFill = w.planks;
            }
            default -> {
                String timber = frameLog(wid);
                c.stilts = 0;
                c.foundation = M + "stone_bricks";
                c.floor = w.planks;
                c.frame = f -> timber;
                c.wall = f -> w.planks;
                c.beam = timber;
                c.ceiling = w.planks;
                c.window = M + "glass_pane";
                c.windowRows = new int[] {2};
                c.roofType = "gable";
                c.pitch = 1;
                c.stepId = M + "stone_brick_stairs";
                c.stepSlab = false;
                c.chimney = M + "brick_block";
                c.veranda = false;
                c.gableWall = w.planks;
                c.carpet = M + "red_carpet";
                c.flatRoof = "";
                c.roofFill = w.planks;
            }
        }
        return c;
    }

    static List<Integer> pillarsFor(int L) {
        List<Integer> out = new ArrayList<>();
        if (L <= 7) {
            out.add(0);
            out.add(L - 1);
            return out;
        }
        if (L <= 11) {
            out.add(0);
            out.add(Math.floorDiv(L - 1, 2));
            out.add(L - 1);
            return out;
        }
        for (int i = 0; i < L - 1; i += 4) out.add(i);
        out.add(L - 1);
        return out;
    }

    static List<Integer> windowsBetween(List<Integer> pillars) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i + 1 < pillars.size(); i++) {
            int a = pillars.get(i);
            int b = pillars.get(i + 1);
            int m = b - a - 1;
            if (m == 3) out.add(a + 2);
            else if (m == 4) {
                out.add(a + 2);
                out.add(a + 3);
            } else if (m == 5) {
                out.add(a + 2);
                out.add(a + 4);
            } else if (m >= 6) {
                for (int k = a + 2; k <= b - 2; k++) out.add(k);
            } else if (m == 2) out.add(a + 1);
        }
        return out;
    }

    static List<Integer> frontWindows(int W, boolean upper) {
        int c = (W - 1) / 2;
        List<Integer> base = new ArrayList<>();
        if (W <= 7) base.addAll(List.of(1, 5));
        else if (W <= 9) base.addAll(List.of(2, 6));
        else base.addAll(List.of(2, 3, W - 4, W - 3));
        if (upper) base.add(c);
        List<Integer> out = new ArrayList<>();
        for (int x : base) if (x > 0 && x < W - 1) out.add(x);
        out.sort(Integer::compare);
        return out;
    }

    static List<Integer> wideWindows(int W, boolean upper) {
        int c = (W - 1) / 2;
        List<Integer> out = new ArrayList<>();
        for (int x = 1; x <= W - 2; x++) if (upper || Math.abs(x - c) > 1) out.add(x);
        return out;
    }

    record Win(int x, int y, int z) {}

    record RCell(int x, int y, String kind, String f) {}

    // ------------------------------------------------------------------ standard house
    static Plan standard(Opts o) {
        int[] sz = SIZES[Math.max(0, Math.min(2, o.size == null ? 1 : o.size))];
        int W = sz[0];
        int D = sz[1];
        int F = Math.max(1, Math.min(3, o.floors == null ? 2 : o.floors));
        Cfg cfg = styleCfg(o);
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        int style = o.style == null ? 0 : o.style;
        RoofSet roof = roofSet(o.roof == null ? 1 : o.roof, wid, style); // missing roof = same wood (as the original)
        int S = cfg.stilts;
        int c = (W - 1) / 2;
        int top = S + 4 * F;
        IntFunction<Integer> yf = f -> S + 4 * f;
        Ctx ctx = new Ctx();
        List<Integer> sidePillars = pillarsFor(D);
        List<Integer> backPillars = pillarsFor(W);
        java.util.function.BiPredicate<Integer, Integer> isPerim = (x, z) -> x == 0 || x == W - 1 || z == 0 || z == D - 1;
        java.util.function.BiPredicate<Integer, Integer> isCorner = (x, z) -> (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
        java.util.function.BiPredicate<Integer, Integer> pillarAt = (x, z) -> {
            if (isCorner.test(x, z)) return true;
            if ((x == 0 || x == W - 1) && sidePillars.contains(z)) return true;
            return z == D - 1 && backPillars.contains(x);
        };
        int ladderX = W - 2;
        int ladderZ = D - 2;
        // ---------------- stilts (thai)
        if (S > 0) {
            List<int[]> posts = new ArrayList<>();
            for (int z : sidePillars) {
                posts.add(new int[] {0, z});
                posts.add(new int[] {W - 1, z});
            }
            for (int x : backPillars) {
                posts.add(new int[] {x, 0});
                posts.add(new int[] {x, D - 1});
            }
            posts.add(new int[] {c, D / 2});
            if (cfg.veranda) {
                posts.add(new int[] {0, -3});
                posts.add(new int[] {W - 1, -3});
            }
            for (int[] p : posts)
                for (int y = 0; y < S; y++) ctx.put(p[0], y, p[1], cfg.frame.apply(0), 1, St.axis("y"));
        }
        // ---------------- floors & walls
        List<Win> wins = new ArrayList<>();
        for (int f = 0; f < F; f++) {
            int y0 = yf.apply(f);
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) {
                    if (f > 0 && x == ladderX && z == ladderZ) continue;
                    if (isPerim.test(x, z)) {
                        if (f == 0 && S == 0) ctx.put(x, y0, z, cfg.foundation, 1);
                        else if (isCorner.test(x, z) || pillarAt.test(x, z)) ctx.put(x, y0, z, cfg.frame.apply(f), 1, St.axis("y"));
                        else ctx.put(x, y0, z, cfg.beam, 1, St.axis(x == 0 || x == W - 1 ? "z" : "x"));
                    } else ctx.put(x, y0, z, cfg.floor, 1);
                }
            List<Integer> fw = style == 2 ? wideWindows(W, f > 0) : frontWindows(W, f > 0);
            List<Integer> bw = windowsBetween(backPillars);
            List<Integer> sw = windowsBetween(sidePillars);
            for (int row = 1; row <= 3; row++) {
                int y = y0 + row;
                final int rr = row;
                boolean isWinRow = Arrays.stream(cfg.windowRows).anyMatch(v -> v == rr);
                for (int x = 0; x < W; x++)
                    for (int z = 0; z < D; z++) {
                        if (!isPerim.test(x, z)) continue;
                        if (pillarAt.test(x, z)) {
                            ctx.put(x, y, z, cfg.frame.apply(f), 1, St.axis("y"));
                            continue;
                        }
                        if (f == 0 && z == 0 && x == c && row <= 2) continue;
                        boolean win = false;
                        if (isWinRow) {
                            if (z == 0) win = fw.contains(x);
                            else if (z == D - 1) win = bw.contains(x);
                            else win = sw.contains(z);
                        }
                        if (win) {
                            ctx.put(x, y, z, cfg.window, 3);
                            if (row == 2) wins.add(new Win(x, y, z));
                        } else ctx.put(x, y, z, cfg.wall.apply(f), 1);
                    }
            }
        }
        // top (ceiling) layer
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                if (isPerim.test(x, z)) {
                    if (isCorner.test(x, z) || pillarAt.test(x, z)) ctx.put(x, top, z, cfg.frame.apply(F - 1), 1, St.axis("y"));
                    else ctx.put(x, top, z, cfg.beam, 1, St.axis(x == 0 || x == W - 1 ? "z" : "x"));
                } else ctx.put(x, top, z, cfg.ceiling, 1);
            }
        // ---------------- roof
        int roofTop = top;
        if (cfg.roofType.equals("flat")) {
            for (int x = -1; x <= W; x++)
                for (int z = -1; z <= D; z++) {
                    boolean inside = x >= 0 && x <= W - 1 && z >= 0 && z <= D - 1;
                    if (!inside) {
                        ctx.put(x, top, z, cfg.flatRoof, 2, St.half("top"));
                        continue;
                    }
                    if (isPerim.test(x, z)) ctx.put(x, top + 1, z, cfg.frame.apply(F - 1), 2);
                    else ctx.put(x, top + 1, z, cfg.flatRoof, 2, St.half("bottom"));
                }
            roofTop = top + 1;
        } else {
            List<RCell> cells = new ArrayList<>();
            int cc = c;
            if (cfg.pitch == 1) {
                for (int i = 0; i <= cc; i++) {
                    int xl = i - 1;
                    int xr = W - i;
                    int y = top + i;
                    cells.add(new RCell(xl, y, "stair", "px"));
                    cells.add(new RCell(xr, y, "stair", "nx"));
                    if (i >= 1) {
                        cells.add(new RCell(xl, y - 1, "fill", null));
                        cells.add(new RCell(xr, y - 1, "fill", null));
                    }
                }
                cells.add(new RCell(cc, top + cc + 1, "slab", null));
                cells.add(new RCell(cc, top + cc, "fill", null));
                roofTop = top + cc + 1;
            } else {
                for (int i = 0; i <= cc; i++) {
                    int xl = i - 1;
                    int xr = W - i;
                    int y = top + 2 * i;
                    cells.add(new RCell(xl, y, "full", null));
                    cells.add(new RCell(xr, y, "full", null));
                    cells.add(new RCell(xl, y + 1, "stair", "px"));
                    cells.add(new RCell(xr, y + 1, "stair", "nx"));
                    if (i >= 1) {
                        cells.add(new RCell(xl, y - 1, "fill", null));
                        cells.add(new RCell(xr, y - 1, "fill", null));
                    }
                }
                int yr = top + 2 * cc;
                cells.add(new RCell(cc, yr, "full", null));
                cells.add(new RCell(cc, yr + 1, "full", null));
                cells.add(new RCell(cc, yr + 2, "slab", null));
                cells.add(new RCell(cc, yr - 1, "fill", null));
                roofTop = yr + 2;
            }
            for (RCell cell : cells) {
                if (cell.kind.equals("fill")) {
                    if (cell.x < 0 || cell.x > W - 1) continue;
                    for (int z = 0; z < D; z++) if (!ctx.has(cell.x, cell.y, z)) ctx.put(cell.x, cell.y, z, cfg.roofFill, 2);
                    continue;
                }
                for (int z = -1; z <= D; z++) {
                    if (cell.kind.equals("stair")) ctx.put(cell.x, cell.y, z, roof.stairs(), 2, St.fh(cell.f, "bottom"));
                    else if (cell.kind.equals("full")) ctx.put(cell.x, cell.y, z, roof.full(), 2);
                    else ctx.put(cell.x, cell.y, z, roof.slab(), 2, St.half("bottom"));
                }
            }
            for (int z : new int[] {0, D - 1}) {
                for (int x = 0; x < W; x++) {
                    int lowest = Integer.MAX_VALUE;
                    for (RCell cell : cells) if (cell.x == x) lowest = Math.min(lowest, cell.y);
                    for (int y = top + 1; y < lowest; y++) if (!ctx.has(x, y, z)) ctx.put(x, y, z, cfg.gableWall, 2);
                }
                int lowestC = Integer.MAX_VALUE;
                for (RCell cell : cells) if (cell.x == cc) lowestC = Math.min(lowestC, cell.y);
                int hC = lowestC - (top + 1);
                if (hC >= 2) ctx.put(cc, top + 1 + Math.floorDiv(hC - 1, 2), z, cfg.window, 3);
            }
            if (style == 3) {
                ctx.put(cc, roofTop + 1, -1, wd.fence, 3);
                ctx.put(cc, roofTop + 1, D, wd.fence, 3);
                ctx.put(-1, top + 1, -1, wd.fence, 3);
                ctx.put(W, top + 1, -1, wd.fence, 3);
                ctx.put(-1, top + 1, D, wd.fence, 3);
                ctx.put(W, top + 1, D, wd.fence, 3);
            }
        }
        // ---------------- door
        int y0 = yf.apply(0);
        if (style == 2) {
            for (int row = 1; row <= 3; row++) {
                ctx.put(c - 1, y0 + row, 0, wd.planks, 1);
                ctx.put(c + 1, y0 + row, 0, wd.planks, 1);
            }
            ctx.put(c, y0 + 3, 0, wd.planks, 1);
        }
        ctx.put(c, y0 + 1, 0, wd.door, 3, St.door("pz", false), Ex.item(wd.door));
        ctx.put(c, y0 + 2, 0, wd.door, 3, St.door("pz", true), Ex.noItemDep(0, -1, 0));
        // ---------------- ladders between floors
        for (int f = 0; f < F - 1; f++)
            for (int y = yf.apply(f) + 1; y <= yf.apply(f + 1); y++) ctx.put(ladderX, y, ladderZ, M + "ladder", 3, St.f("nz"));
        // ---------------- porch / veranda
        int front = -3;
        boolean light = Opts.on(o.light);
        if (cfg.veranda) {
            for (int x = 0; x < W; x++)
                for (int z = -3; z <= -1; z++) ctx.put(x, S, z, wd.planks, 1);
            for (int z = -3; z <= -1; z++) {
                ctx.put(0, S + 1, z, wd.fence, 3);
                ctx.put(W - 1, S + 1, z, wd.fence, 3);
            }
            for (int x = 1; x < W - 1; x++) if (x != c) ctx.put(x, S + 1, -3, wd.fence, 3);
            for (int i = 1; i <= S; i++) {
                int y = S - i;
                int z = -3 - i;
                ctx.put(c, y, z, wd.stairs, 3, St.fh("pz", "bottom"));
                for (int yy = 0; yy < y; yy++) ctx.put(c, yy, z, wd.planks, 1);
            }
            front = -4 - S;
            if (light) {
                ctx.put(0, S + 2, -3, M + "lantern", 4, St.hanging(false));
                ctx.put(W - 1, S + 2, -3, M + "lantern", 4, St.hanging(false));
            }
        } else if (Opts.on(o.porch)) {
            if (cfg.stepSlab) ctx.put(c, 0, -1, cfg.stepId, 3, St.half("bottom"));
            else ctx.put(c, 0, -1, cfg.stepId, 3, St.fh("pz", "bottom"));
            if (style == 2) {
                for (int x = c - 1; x <= c + 1; x++)
                    for (int z = -2; z <= -1; z++) ctx.put(x, 3, z, M + "smooth_quartz_slab", 3, St.half("top"));
                for (int x : new int[] {1, 2, W - 3, W - 2}) {
                    if (Math.abs(x - c) <= 1) continue;
                    ctx.put(x, 0, -1, (x + W) % 2 != 0 ? M + "azalea_leaves_flowered" : M + "azalea_leaves", 4, St.persistent(), Ex.opt());
                }
                front = -4;
            }
            if (style != 2)
                for (int x = c - 1; x <= c + 1; x++) ctx.put(x, 3, -1, roof.stairs(), 3, St.fh("pz", "bottom"));
            if (style != 2)
                for (int x : new int[] {c - 3, c + 3}) {
                    ctx.put(x, 0, -2, wd.fence, 3);
                    ctx.put(x, 1, -2, wd.fence, 3);
                    if (light) ctx.put(x, 2, -2, M + "lantern", 4, St.hanging(false));
                }
            front = -4;
        }
        // ---------------- chimney
        if (Opts.on(o.chimney) && cfg.chimney != null && cfg.roofType.equals("gable")) {
            int zc = D - 3;
            String ch = cfg.chimney;
            for (int y = 0; y <= top + 3; y++) {
                ctx.del(-1, y, zc);
                ctx.put(-1, y, zc, ch, 1);
            }
            ctx.put(-1, top + 4, zc, M + "campfire", 4, null, Ex.opt());
        }
        // ---------------- window sills (decoration)
        if (style == 0 || style == 1) {
            for (Win wv : wins) {
                int ox = wv.x;
                int oz = wv.z;
                String f;
                if (wv.z == 0) {
                    oz = -1;
                    f = "pz";
                } else if (wv.z == D - 1) {
                    oz = D;
                    f = "nz";
                } else if (wv.x == 0) {
                    ox = -1;
                    f = "px";
                } else {
                    ox = W;
                    f = "nx";
                }
                if (!ctx.has(ox, wv.y - 1, oz) && !ctx.has(ox, wv.y, oz)) ctx.put(ox, wv.y - 1, oz, wd.stairs, 4, St.fh(f, "top"), Ex.opt());
            }
        }
        // ---------------- lights
        if (light) {
            int[] zs = D <= 7 ? new int[] {D / 2} : new int[] {D / 3, (2 * D) / 3};
            for (int f = 0; f < F; f++)
                for (int z : zs) {
                    if (style == 2) {
                        for (int x : W >= 9 ? new int[] {c - 2, c + 2} : new int[] {c}) ctx.put(x, yf.apply(f) + 4, z, M + "sea_lantern", 4);
                    } else ctx.put(c, yf.apply(f) + 3, z, M + "lantern", 4, St.hanging(true));
                }
        }
        // ---------------- furniture
        if (Opts.on(o.furniture)) {
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
                Gen.Roles rr = Gen.rolesFor(F, f);
                Gen.furnishRoom(new Gen.Room(yf.apply(f), 1, W - 2, 1, D - 2, reserved, rr.roles(), W >= 9 ? rr.beds() : Math.min(rr.beds(), 1), cfg.carpet, wd), ctx);
            }
        }
        // ---------------- finalize
        Plan p = Gen.finalize(ctx, W, D, null);
        p.maxY = Math.max(p.maxY, roofTop);
        p.front = front;
        p.doorX = c;
        p.fillBlock = S > 0 ? wd.log : cfg.foundation;
        return p;
    }

    /** Bill of materials: item id -> {required, optional}. */
    public static Map<String, int[]> billOfMaterials(Plan plan) {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (Blk b : plan.blocks) {
            String item = b.placeItem();
            if (item == null || item.isEmpty()) continue;
            int[] e = m.computeIfAbsent(item, k -> new int[2]);
            if (b.optional()) e[1]++;
            else e[0]++;
        }
        return m;
    }

    // ------------------------------------------------------------------ modern
    static String[] facadePattern(int L) {
        if (L < 3) {
            String[] a = new String[Math.max(0, L)];
            Arrays.fill(a, "W");
            return a;
        }
        int n = L - 2;
        int g = Math.max(1, (int) Gen.jsRound((n + 1) / 4.0));
        int glass = n - (g - 1);
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < g; i++) sizes.add(glass / g + (i < glass % g ? 1 : 0));
        List<String> out = new ArrayList<>();
        out.add("J");
        for (int i = 0; i < sizes.size(); i++) {
            for (int k = 0; k < sizes.get(i); k++) out.add("G");
            if (i < sizes.size() - 1) out.add("J");
        }
        out.add("J");
        return out.toArray(new String[0]);
    }

    record BoxB(int x0, int x1, int z0, int z1, int y0) {}

    record Lad(int x, int z, int from) {}

    record TDoor(int x, int z, int y, String f) {}

    record Face(List<int[]> cells, boolean front) {}

    static Plan modern(Opts o) {
        int[] sz = SIZES[Math.max(0, Math.min(2, o.size == null ? 1 : o.size))];
        int W = sz[0];
        int D = sz[1];
        int F = Math.max(1, Math.min(3, o.floors == null ? 2 : o.floors));
        String wid = Gen.woodId(o.wood);
        Wood wd = new Wood(wid);
        String WHITE = M + "smooth_quartz";
        String BLACK = M + "black_concrete";
        String GRAY = M + "gray_concrete";
        String GLASS = M + "glass_pane";
        String SLAT = M + "stripped_" + wid + "_log";
        int sh = W >= 9 ? 4 : 3;
        int xd = W >= 9 ? W - 3 : W - 2;
        Ctx ctx = new Ctx();
        List<BoxB> boxes = new ArrayList<>();
        boxes.add(new BoxB(0, W - 1, 0, D - 1, 0));
        if (F >= 2) boxes.add(new BoxB(sh, W - 1 + sh, 0, D - 1, 4));
        if (F >= 3) boxes.add(new BoxB(sh, sh + W - 4, 2, D - 1, 8));
        List<Lad> ladders = new ArrayList<>();
        if (F >= 2) ladders.add(new Lad(W - 2, D - 2, 0));
        if (F >= 3) ladders.add(new Lad(sh + 1, D - 2, 1));
        int zMid = D / 2;
        int b2xm = Math.floorDiv(sh + sh + W - 4, 2);
        List<TDoor> terraceDoors = new ArrayList<>();
        if (F >= 2) terraceDoors.add(new TDoor(sh, zMid, 4, "px"));
        if (F >= 3) terraceDoors.add(new TDoor(b2xm, 2, 8, "pz"));
        for (int i = 0; i < boxes.size(); i++) {
            BoxB b = boxes.get(i);
            boolean top = i == boxes.size() - 1;
            for (int x = b.x0; x <= b.x1; x++)
                for (int z = b.z0; z <= b.z1; z++) {
                    boolean perim = x == b.x0 || x == b.x1 || z == b.z0 || z == b.z1;
                    ctx.put(x, b.y0, z, perim ? WHITE : wd.planks, 1);
                }
            final int ii = i;
            java.util.function.BiFunction<Integer, Integer, String> wallMat = (x, z) -> ii == 1 && (x >= W || (x == b.x1 && z > b.z0 && z < b.z1)) ? GRAY : WHITE;
            int[] glassRows = i >= 1 ? new int[] {1, 2, 3} : new int[] {1, 2};
            List<Face> faces = new ArrayList<>();
            if (i == 0) {
                List<int[]> left = new ArrayList<>();
                for (int x = 1; x <= xd - 2; x++) left.add(new int[] {x, 0});
                faces.add(new Face(left, true));
                List<int[]> right = new ArrayList<>();
                for (int x = xd + 2; x <= W - 2; x++) right.add(new int[] {x, 0});
                faces.add(new Face(right, true));
            } else {
                List<int[]> fr = new ArrayList<>();
                for (int x = b.x0 + 1; x <= b.x1 - 1; x++) fr.add(new int[] {x, b.z0});
                faces.add(new Face(fr, true));
            }
            List<int[]> back = new ArrayList<>();
            for (int x = b.x0 + 1; x <= b.x1 - 1; x++) back.add(new int[] {x, b.z1});
            faces.add(new Face(back, false));
            List<int[]> lf = new ArrayList<>();
            for (int z = b.z0 + 1; z <= b.z1 - 1; z++) lf.add(new int[] {b.x0, z});
            faces.add(new Face(lf, false));
            List<int[]> rt = new ArrayList<>();
            for (int z = b.z0 + 1; z <= b.z1 - 1; z++) rt.add(new int[] {b.x1, z});
            faces.add(new Face(rt, false));
            for (int[] cxz : new int[][] {{b.x0, b.z0}, {b.x1, b.z0}, {b.x0, b.z1}, {b.x1, b.z1}})
                for (int row = 1; row <= 3; row++) ctx.put(cxz[0], b.y0 + row, cxz[1], WHITE, 1);
            for (Face fc : faces) {
                String[] pat = facadePattern(fc.cells.size());
                int[] rows = fc.front ? glassRows : new int[] {1, 2};
                int maxRow = Arrays.stream(rows).max().orElse(0);
                for (int idx = 0; idx < fc.cells.size(); idx++) {
                    int x = fc.cells.get(idx)[0];
                    int z = fc.cells.get(idx)[1];
                    for (int row = 1; row <= 3; row++) {
                        String t = pat[idx];
                        String id = wallMat.apply(x, z);
                        final int rr = row;
                        boolean inRows = Arrays.stream(rows).anyMatch(v -> v == rr);
                        if (t.equals("G")) id = inRows ? GLASS : BLACK;
                        else if (t.equals("J")) id = row <= maxRow || rows.length == 2 ? BLACK : id;
                        ctx.put(x, b.y0 + row, z, id, id.equals(GLASS) ? 3 : 1);
                    }
                }
            }
            int zf = top ? b.z0 - 2 : b.z0 - 1;
            int xr = i == 0 && F == 1 ? b.x1 + sh + 1 : b.x1 + 1;
            for (int x = b.x0 - 1; x <= xr; x++)
                for (int z = zf; z <= b.z1 + 1; z++) ctx.put(x, b.y0 + 4, z, WHITE, 2);
        }
        for (int i = 1; i < boxes.size(); i++) {
            BoxB b = boxes.get(i);
            for (int x = b.x0; x <= b.x1; x++)
                for (int z = b.z0; z <= b.z1; z++) {
                    boolean perim = x == b.x0 || x == b.x1 || z == b.z0 || z == b.z1;
                    ctx.put(x, b.y0, z, perim ? WHITE : wd.planks, 1);
                }
        }
        for (int i = 0; i + 1 < boxes.size(); i++) {
            BoxB b = boxes.get(i);
            BoxB up = boxes.get(i + 1);
            int y = b.y0 + 5;
            java.util.function.BiPredicate<Integer, Integer> inSlab = (x, z) -> x >= b.x0 - 1 && x <= b.x1 + 1 && z >= b.z0 - 1 && z <= b.z1 + 1;
            java.util.function.BiPredicate<Integer, Integer> covered = (x, z) -> x >= up.x0 && x <= up.x1 && z >= up.z0 && z <= up.z1;
            for (int x = b.x0 - 1; x <= b.x1 + 1; x++)
                for (int z = b.z0 - 1; z <= b.z1 + 1; z++) {
                    if (covered.test(x, z)) continue;
                    boolean edge = !inSlab.test(x + 1, z) || !inSlab.test(x - 1, z) || !inSlab.test(x, z + 1) || !inSlab.test(x, z - 1);
                    if (edge) ctx.put(x, y, z, GLASS, 3);
                }
            int[][] planters = i == 0 ? new int[][] {{up.x0 - 1, b.z0}, {up.x0 - 1, b.z1}} : new int[][] {{up.x0, up.z0 - 1}, {up.x1, up.z0 - 1}};
            for (int n = 0; n < planters.length; n++)
                ctx.put(planters[n][0], y, planters[n][1], n % 2 != 0 ? M + "azalea_leaves" : M + "azalea_leaves_flowered", 4, St.persistent(), Ex.opt());
        }
        int px0 = W;
        int px1 = W + sh - 1;
        for (int z : new int[] {0, D - 1})
            for (int y = 0; y <= 3; y++) ctx.put(px1, y, z, SLAT, 1, St.axis("y"));
        boolean pool = Opts.on(o.pool);
        for (int x = px0; x <= px1 + 1; x++)
            for (int z = -1; z <= D; z++) {
                boolean water = pool && x >= px0 && x <= px1 - 1 && z >= 1 && z <= D - 2;
                if (water) {
                    ctx.put(x, -2, z, WHITE, 1, null, Ex.opt().withTag("pool"));
                    ctx.put(x, -1, z, M + "water", 6, null, Ex.opt().withTag("pool").withItem(null));
                } else ctx.put(x, -1, z, wd.planks, 1, null, Ex.opt().withTag(pool ? "pool" : null));
            }
        if (pool)
            for (int z = 1; z <= D - 2; z++) ctx.put(W - 1, -1, z, WHITE, 1, null, Ex.opt().withTag("pool"));
        for (int z = 0; z <= D - 1; z++)
            ctx.put(px1 + 2, 0, z, z % 3 == 1 ? M + "azalea_leaves_flowered" : M + "azalea_leaves", 4, St.persistent(), Ex.opt());
        for (int row = 1; row <= 3; row++) {
            ctx.put(xd - 1, row, 0, wd.planks, 1);
            ctx.put(xd + 1, row, 0, wd.planks, 1);
        }
        ctx.put(xd, 3, 0, wd.planks, 1);
        ctx.put(xd, 1, 0, wd.door, 3, St.door("pz", false), Ex.item(wd.door));
        ctx.put(xd, 2, 0, wd.door, 3, St.door("pz", true), Ex.noItemDep(0, -1, 0));
        if (Opts.on(o.porch)) {
            ctx.put(xd, 0, -1, M + "smooth_quartz_slab", 3, St.half("bottom"));
            String[] flowers = {M + "poppy", M + "red_tulip", M + "poppy", M + "azalea_leaves_flowered"};
            for (int x = 0; x <= xd - 2; x++) {
                ctx.put(x, 0, -1, M + "grass_block", 4, null, Ex.opt());
                String fl = flowers[x % flowers.length];
                ctx.put(x, 1, -1, fl, 5, fl.contains("leaves") ? St.persistent() : null, Ex.opt().withDep(0, -1, 0));
            }
        }
        for (TDoor d : terraceDoors) {
            ctx.put(d.x, d.y + 1, d.z, wd.door, 3, St.door(d.f, false), Ex.item(wd.door));
            ctx.put(d.x, d.y + 2, d.z, wd.door, 3, St.door(d.f, true), Ex.noItemDep(0, -1, 0));
            ctx.put(d.x, d.y + 3, d.z, BLACK, 1);
        }
        for (Lad l : ladders) {
            BoxB b = boxes.get(l.from);
            BoxB nb = boxes.get(l.from + 1);
            ctx.del(l.x, nb.y0, l.z);
            for (int y = b.y0 + 1; y <= nb.y0; y++) ctx.put(l.x, y, l.z, M + "ladder", 3, St.f("nz"));
        }
        Set<String> ladderCells = new java.util.HashSet<>();
        for (Lad l : ladders) ladderCells.add(l.x + "," + l.z);
        if (Opts.on(o.light)) {
            for (BoxB b : boxes)
                for (int x = b.x0 + 2; x <= b.x1 - 2; x += 3)
                    for (int z = b.z0 + 2; z <= b.z1 - 2; z += 3) {
                        if (ladderCells.contains(x + "," + z)) continue;
                        ctx.put(x, b.y0 + 4, z, M + "sea_lantern", 4);
                    }
            if (F == 1)
                for (int z = 2; z <= D - 3; z += 3) ctx.put(W + 1, 4, z, M + "sea_lantern", 4);
        }
        if (Opts.on(o.furniture)) {
            for (int i = 0; i < boxes.size(); i++) {
                BoxB b = boxes.get(i);
                Set<String> reserved = new java.util.LinkedHashSet<>();
                if (i == 0) {
                    reserved.add(xd + ",1");
                    reserved.add(xd + ",2");
                }
                for (Lad l : ladders) {
                    if (l.from == i || l.from == i - 1) {
                        reserved.add(l.x + "," + l.z);
                        reserved.add(l.x + "," + (l.z - 1));
                    }
                }
                for (TDoor d : terraceDoors) {
                    if (d.y != b.y0) continue;
                    if (d.f.equals("px")) {
                        reserved.add((d.x + 1) + "," + d.z);
                        reserved.add((d.x + 2) + "," + d.z);
                    } else {
                        reserved.add(d.x + "," + (d.z + 1));
                        reserved.add(d.x + "," + (d.z + 2));
                    }
                }
                Gen.Roles rr = Gen.rolesFor(F, i);
                Gen.furnishRoom(new Gen.Room(b.y0, b.x0 + 1, b.x1 - 1, b.z0 + 1, b.z1 - 1, reserved, rr.roles(),
                        W >= 9 ? rr.beds() : Math.min(rr.beds(), 1), M + "white_carpet", wd), ctx);
            }
        }
        Plan p = Gen.finalize(ctx, W, D, null);
        p.front = p.minZ - 1;
        p.doorX = xd;
        p.clearArea = new Gen.Area(p.minX, p.maxX, p.minZ, p.maxZ);
        p.fillBlock = WHITE;
        return p;
    }
}
