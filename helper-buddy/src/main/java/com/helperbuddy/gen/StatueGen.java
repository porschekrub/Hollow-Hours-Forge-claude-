package com.helperbuddy.gen;

import com.helperbuddy.gen.Gen.Base;
import com.helperbuddy.gen.Gen.Ctx;
import com.helperbuddy.gen.Gen.Ex;
import com.helperbuddy.gen.Gen.Opts;
import com.helperbuddy.gen.Gen.Plan;
import com.helperbuddy.gen.Gen.St;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.helperbuddy.gen.Gen.M;

/** Pixel statues (a picture extruded into the depth) and the nether portal. */
public final class StatueGen {
    private StatueGen() {}

    static final Map<Character, String> CH = new HashMap<>();

    static {
        String[][] m = {{"G", "lime_concrete"}, {"g", "green_concrete"}, {"K", "black_concrete"}, {"W", "white_concrete"}, {"R", "red_concrete"},
                {"P", "pink_concrete"}, {"B", "blue_concrete"}, {"C", "cyan_concrete"}, {"Y", "yellow_concrete"}, {"O", "orange_concrete"},
                {"N", "brown_concrete"}, {"E", "gray_concrete"}, {"S", "light_gray_concrete"}, {"D", "diamond_block"}, {"L", "lime_concrete"},
                {"T", "oak_log"}, {"A", "light_blue_concrete"}, {"M", "magenta_concrete"}, {"U", "purple_concrete"}, {"F", "glowstone"}, {"X", "gold_block"}};
        for (String[] e : m) CH.put(e[0].charAt(0), e[1]);
    }

    public record Model(String id, String th, String en, Pattern re, String[] rows) {
        public String name(boolean thai) {
            return thai ? th : en;
        }
    }

    public static final Model[] MODELS = {
            new Model("creeper", "ครีปเปอร์", "creeper", Pattern.compile("(ครีปเปอร์|ครีปเปอ|creeper|คริปเปอร์)"), new String[] {
                    "GGGGGGGG", "GGGGGGGG", "GKKGGKKG", "GKKGGKKG", "GGGKKGGG", "GGKKKKGG", "GGKKKKGG", "GGKGGKGG",
                    "..GGGG..", "..GGGG..", "..GGGG..", "..GGGG..", ".GGGGGG.", ".GGGGGG.", ".GG..GG.", ".GG..GG."}),
            new Model("steve", "สตีฟ", "Steve", Pattern.compile("(สตีฟ|steve|ผู้เล่น|player|คน)"), new String[] {
                    ".NNNNNN.", ".NNNNNN.", ".NOOOOO.", ".OWBOBWO", ".OOOOOO.", ".OONNOO.",
                    "..OOOO..", "ACCCCCCA", "OCCCCCCO", "OCCCCCCO", "OCCCCCCO", "O.CCCC.O", "..BBBB..", "..BBBB..", "..BB.BB.", "..BB.BB.", "..EE.EE."}),
            new Model("zombie", "ซอมบี้", "zombie", Pattern.compile("(ซอมบี้|zombie)"), new String[] {
                    "..gggg..", ".gggggg.", ".gKggKg.", ".gggggg.", ".ggKKgg.", "..gggg..", "CCCCCCCC", "gCCCCCCg", "gCCCCCCg", "g.CCCC.g",
                    "..BBBB..", "..BB.BB.", "..BB.BB.", "..EE.EE."}),
            new Model("pig", "หมู", "pig", Pattern.compile("(หมู|pig)"), new String[] {
                    ".PPPPPP.", "PPPPPPPP", "PKPPPPKP", "PPPPPPPP", "PPPNNPPP", "PPPKKPPP", "PPPPPPPP", ".PPPPPP.", "..P..P..", "..P..P.."}),
            new Model("heart", "หัวใจ", "heart", Pattern.compile("(หัวใจ|heart|love)"), new String[] {
                    ".RRR...RRR.", "RRRRR.RRRRR", "RRWRRRRRRRR", "RRRRRRRRRRR", "RRRRRRRRRRR", ".RRRRRRRRR.", "..RRRRRRR..", "...RRRRR...", "....RRR....", ".....R....."}),
            new Model("sword", "ดาบ", "sword", Pattern.compile("(ดาบ|sword)"), new String[] {
                    ".......DD", "......DDD", ".....DDD.", "....DDD..", "...DDD...", "N.DDD....", ".NDD.....", "..NN.....", ".NN.N....", "NN...N..."}),
            new Model("tree", "ต้นไม้", "tree", Pattern.compile("(ต้นไม้|tree)"), new String[] {
                    "...GGG...", "..GGGGG..", ".GGGGGGG.", "GGGGGGGGG", ".GGGGGGG.", "GGGGGGGGG", ".GGGGGGG.", "..GGGGG..", "....N....", "....N....", "....N....", "...NNN..."}),
            new Model("diamond", "เพชร", "diamond", Pattern.compile("(เพชร|diamond|ไดมอนด์)"), new String[] {
                    "..AAAAA..", ".AAWAAAA.", "AAWAAAAAA", "AAAAAAAAA", ".AAAAAAA.", "..AAAAA..", "...AAA...", "....A...."}),
            new Model("mushroom", "เห็ด", "mushroom", Pattern.compile("(เห็ด|mushroom)"), new String[] {
                    "..RRRRR..", ".RRWRRWR.", "RRRRRRRRR", "RWRRRRRWR", "RRRRRRRRR", "...WWW...", "...WWW...", "...WWW...", "..WWWWW.."}),
            new Model("star", "ดาว", "star", Pattern.compile("(ดาว|star)"), new String[] {
                    "....Y....", "....Y....", "...YYY...", "YYYYYYYYY", ".YYYYYYY.", "..YYYYY..", "..YYYYY..", ".YYY.YYY.", ".YY...YY."}),
            new Model("snowman", "มนุษย์หิมะ", "snowman", Pattern.compile("(หิมะ|snowman|snow golem|โกเลม)"), new String[] {
                    "..NNNNN..", "...NNN...", "..WWWWW..", "..WKWKW..", "..WWOWW..", "..WWWWW..", ".WWWWWWW.", "NWWKWWWWN", "NWWWWWWWN", "..WKWWW..", ".WWWWWWW.", ".WWWWWWW.", "..WWWWW.."}),
            new Model("cat", "แมว", "cat", Pattern.compile("(แมว|cat\\b)"), new String[] {
                    "O.O...O.O", "OOO...OOO", "OOOOOOOOO", "OKOOOOOKO", "OOOOPOOOO", "OOOKOKOOO", ".OOOOOOO.", ".OOOOOOO.", "OOOOOOOOO", "OO.....OO"}),
    };

    public static Model modelOf(Opts o) {
        if (o.model != null && o.model >= 0 && o.model < MODELS.length) return MODELS[o.model];
        return MODELS[Gen.variantOf(o) % MODELS.length];
    }

    static Plan statue(Opts o) {
        Model m = modelOf(o);
        int scale = Gen.clamp(o.sizeOr(1), 0, 2) == 2 ? 2 : 1;
        int depth = (Gen.in(m.id, "heart", "star", "sword", "diamond") ? 2 : 4) * (scale == 2 ? 2 : 1);
        String[] rows = m.rows;
        int maxLen = 0;
        for (String r : rows) maxLen = Math.max(maxLen, r.length());
        int spriteW = maxLen * scale;
        int margin = 2;
        int W = spriteW + margin * 2;
        int D = depth + margin * 2;
        Ctx ctx = new Ctx();
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                boolean edge = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                ctx.put(x, 0, z, edge ? M + "stone_bricks" : M + "polished_andesite", 1);
            }
        for (int x = 1; x < W - 1; x++)
            for (int z = 1; z < D - 1; z++) ctx.put(x, 1, z, M + "smooth_stone", 1);
        int y0 = 2;
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int cx = 0; cx < row.length(); cx++) {
                char chr = row.charAt(cx);
                if (chr == '.' || chr == ' ') continue;
                String id = M + CH.getOrDefault(chr, "stone");
                for (int sy = 0; sy < scale; sy++)
                    for (int sx = 0; sx < scale; sx++)
                        for (int z = 0; z < depth; z++) {
                            int x = margin + cx * scale + sx;
                            int y = y0 + (rows.length - 1 - r) * scale + sy;
                            ctx.put(x, y, margin + z, id, 1);
                        }
            }
        }
        for (int x : new int[] {1, W - 2}) ctx.put(x, 2, 1, M + "lantern", 4, St.hanging(false));
        return Gen.finalize(ctx, W, D, Base.of().fill(M + "stone_bricks").clear(-1, W, -1, D));
    }

    static Plan portal(Opts o) {
        int s = Gen.clamp(o.sizeOr(1), 0, 2);
        int iw = new int[] {2, 3, 4}[s];
        int ih = new int[] {3, 4, 5}[s];
        int W = iw + 2 + 4;
        int D = 5;
        Ctx ctx = new Ctx();
        int x0 = 2;
        int x1 = x0 + iw + 1;
        int z = 2;
        for (int x = 0; x < W; x++)
            for (int zz = 0; zz < D; zz++)
                ctx.put(x, 0, zz, M + ((x + zz) % 2 == 0 ? "polished_blackstone_bricks" : "blackstone"), 1, null, Ex.item(null));
        for (int x = x0; x <= x1; x++) {
            for (int y = 1; y <= ih + 2; y++) {
                boolean edge = x == x0 || x == x1 || y == 1 || y == ih + 2;
                if (!edge) continue;
                boolean corner = (x == x0 || x == x1) && (y == 1 || y == ih + 2);
                if (corner) continue;
                ctx.put(x, y, z, M + "obsidian", 1);
            }
        }
        int fx = x0 + 1, fy = 2;
        for (int x = x0 + 1; x <= x1 - 1; x++)
            for (int y = 2; y <= ih + 1; y++) {
                boolean first = x == fx && y == fy;
                ctx.put(x, y, z, M + "portal", 6, St.axis("x"), first ? Ex.item(M + "flint_and_steel") : Ex.noItemDep(fx - x, fy - y, 0));
            }
        for (int x : new int[] {x0 - 1, x1 + 1}) ctx.put(x, 1, z, M + "lantern", 4, St.hanging(false), Ex.item(null));
        return Gen.finalize(ctx, W, D, Base.of().fill(M + "blackstone").items().clear(-1, W, -1, D));
    }

    public static Plan generate(Opts o) {
        return "portal".equals(o.type) ? portal(o) : statue(o);
    }
}
