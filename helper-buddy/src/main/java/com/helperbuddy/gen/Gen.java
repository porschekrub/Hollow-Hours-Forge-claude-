package com.helperbuddy.gen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Core of the building generators (pure Java, no Minecraft classes, so it can be tested on its own).
 * Local coordinates: x = left -> right seen from the front, z = front -> back, y = up; y = 0 is one block above the
 * ground the player stands on, the surface (top ground block) is y = -1 and underground rooms use negative y.
 * Block ids are written the way the original generators wrote them ("minecraft:wooden_door" ...); the builder maps them
 * to real Java Edition blocks.
 */
public final class Gen {
    private Gen() {}

    public static final String M = "minecraft:";
    public static final String[] WOOD_IDS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"};

    // ------------------------------------------------------------------ options
    /** What the player asked for. Null fields are "not said" (the generators use their own default). */
    public static final class Opts {
        public String type;
        public Integer style, size, floors, wood, roof, mat, variant, model, w, d, height, speed;
        public Boolean light, furniture, porch, chimney, pool, floorsExact;

        public Opts copy() {
            Opts o = new Opts();
            o.type = type;
            o.style = style;
            o.size = size;
            o.floors = floors;
            o.wood = wood;
            o.roof = roof;
            o.mat = mat;
            o.variant = variant;
            o.model = model;
            o.w = w;
            o.d = d;
            o.height = height;
            o.speed = speed;
            o.light = light;
            o.furniture = furniture;
            o.porch = porch;
            o.chimney = chimney;
            o.pool = pool;
            o.floorsExact = floorsExact;
            return o;
        }

        public Opts withMat(int m) {
            Opts o = copy();
            o.mat = m;
            return o;
        }

        int sizeOr(int def) {
            return size == null ? def : size;
        }

        int floorsOr(int def) {
            return floors == null ? def : floors;
        }

        int matOr0() {
            return mat == null ? 0 : mat;
        }

        /** JS truthiness of a boolean option that may be missing. */
        static boolean on(Boolean b) {
            return b != null && b;
        }

        /** "o.x !== false" */
        static boolean notOff(Boolean b) {
            return b == null || b;
        }
    }

    // ------------------------------------------------------------------ block states
    /** Abstract block states: f = facing (px/nx/pz/nz), half, upper (door), hinge, hanging, head (bed), axis, persistent. */
    public static final class St {
        public String f, half, axis;
        public Boolean upper, hinge, hanging, head, persistent;

        public static St f(String f) {
            St s = new St();
            s.f = f;
            return s;
        }

        public static St fh(String f, String half) {
            St s = new St();
            s.f = f;
            s.half = half;
            return s;
        }

        public static St half(String half) {
            St s = new St();
            s.half = half;
            return s;
        }

        public static St axis(String a) {
            St s = new St();
            s.axis = a;
            return s;
        }

        public static St hanging(boolean h) {
            St s = new St();
            s.hanging = h;
            return s;
        }

        public static St persistent() {
            St s = new St();
            s.persistent = true;
            return s;
        }

        public static St door(String f, boolean upper) {
            St s = new St();
            s.f = f;
            s.upper = upper;
            s.hinge = false;
            return s;
        }

        public static St bed(String f, boolean head) {
            St s = new St();
            s.f = f;
            s.head = head;
            return s;
        }

        /** Canonical text (sorted keys), used by the tests and as a cache key. */
        public String canon() {
            StringBuilder b = new StringBuilder("{");
            Map<String, Object> m = new java.util.TreeMap<>();
            if (axis != null) m.put("axis", axis);
            if (f != null) m.put("f", f);
            if (half != null) m.put("half", half);
            if (hanging != null) m.put("hanging", hanging);
            if (head != null) m.put("head", head);
            if (hinge != null) m.put("hinge", hinge);
            if (persistent != null) m.put("persistent", persistent);
            if (upper != null) m.put("upper", upper);
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('"').append(e.getKey()).append("\":");
                if (e.getValue() instanceof String s) b.append('"').append(s).append('"');
                else b.append(e.getValue());
            }
            return b.append('}').toString();
        }
    }

    // ------------------------------------------------------------------ extras of a block
    /** item: the item used to place it (unset = the block id itself, null = nothing needed); dep: placed only after this cell. */
    public static final class Ex {
        boolean itemSet;
        String item;
        int[] dep;
        Boolean opt;
        String tag;

        public static Ex item(String id) {
            Ex e = new Ex();
            e.itemSet = true;
            e.item = id;
            return e;
        }

        public static Ex noItemDep(int dx, int dy, int dz) {
            Ex e = item(null);
            e.dep = new int[] {dx, dy, dz};
            return e;
        }

        public static Ex opt() {
            Ex e = new Ex();
            e.opt = true;
            return e;
        }

        public Ex withOpt(Boolean o) {
            opt = o;
            return this;
        }

        public Ex withDep(int dx, int dy, int dz) {
            dep = new int[] {dx, dy, dz};
            return this;
        }

        public Ex withTag(String t) {
            tag = t;
            return this;
        }

        public Ex withItem(String i) {
            itemSet = true;
            item = i;
            return this;
        }
    }

    public static final class Blk {
        public final int x, y, z;
        public final String id;
        public final double ph;
        public final St st;
        public final boolean itemSet;
        public final String item;
        public final int[] dep;
        public final Boolean opt;
        public final String tag;

        Blk(int x, int y, int z, String id, double ph, St st, Ex ex) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.ph = ph;
            this.st = st;
            this.itemSet = ex != null && ex.itemSet;
            this.item = ex == null ? null : ex.item;
            this.dep = ex == null ? null : ex.dep;
            this.opt = ex == null ? null : ex.opt;
            this.tag = ex == null ? null : ex.tag;
        }

        /** The item that places this block, or null when none is needed. */
        public String placeItem() {
            return itemSet ? item : id;
        }

        public boolean optional() {
            return opt != null && opt;
        }
    }

    /** One cell of the "must be emptied" list. */
    public record Cell(int x, int y, int z) {}

    public static final class Area {
        public final int x0, x1, z0, z1;

        public Area(int x0, int x1, int z0, int z1) {
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
        }
    }

    public static final class Plan {
        public List<Blk> blocks;
        public int W, D, minX, maxX, minZ, maxZ, maxY, front;
        public double doorX;
        public Area clearArea;
        public String fillBlock;
        public List<Cell> clearCells;
        public boolean underground, needsItems;
    }

    /** Overrides for finalize ("...base" in the original). */
    public static final class Base {
        Double doorX;
        Area clearArea;
        String fillBlock;
        List<Cell> clearCells;
        Boolean underground, needsItems;
        Integer front;

        public static Base of() {
            return new Base();
        }

        public Base door(double x) {
            doorX = x;
            return this;
        }

        public Base clear(int x0, int x1, int z0, int z1) {
            clearArea = new Area(x0, x1, z0, z1);
            return this;
        }

        public Base fill(String id) {
            fillBlock = id;
            return this;
        }

        public Base cells(List<Cell> c) {
            clearCells = c;
            return this;
        }

        public Base under() {
            underground = true;
            return this;
        }

        public Base items() {
            needsItems = true;
            return this;
        }
    }

    // ------------------------------------------------------------------ the block map
    public static final class Ctx {
        public final LinkedHashMap<Long, Blk> map = new LinkedHashMap<>();

        static long k(int x, int y, int z) {
            return ((long) (x + 2048) << 40) | ((long) (y + 2048) << 20) | (z + 2048);
        }

        public void put(int x, int y, int z, String id, double ph, St st, Ex ex) {
            map.put(k(x, y, z), new Blk(x, y, z, id, ph, st, ex));
        }

        public void put(int x, int y, int z, String id, double ph, St st) {
            put(x, y, z, id, ph, st, null);
        }

        public void put(int x, int y, int z, String id, double ph) {
            put(x, y, z, id, ph, null, null);
        }

        public void put(int x, int y, int z, String id) {
            put(x, y, z, id, 1, null, null);
        }

        public boolean has(int x, int y, int z) {
            return map.containsKey(k(x, y, z));
        }

        public void del(int x, int y, int z) {
            map.remove(k(x, y, z));
        }
    }

    // ------------------------------------------------------------------ small helpers
    /** Packed key of a local cell (same as the block map uses). */
    public static long key(int x, int y, int z) {
        return Ctx.k(x, y, z);
    }

    public static int clamp(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    public static int odd(int n) {
        return n % 2 == 0 ? n + 1 : n;
    }

    /** Size override from the player; always odd so the door sits in the middle. */
    public static int dim(Integer v, int def, int lo, int hi) {
        return v == null ? def : odd(clamp(v, lo, hi));
    }

    public static int dim(Integer v, int def) {
        return dim(v, def, 5, 41);
    }

    public static int variantOf(Opts o) {
        return o.variant == null ? 0 : Math.abs(o.variant);
    }

    public static String woodId(Integer w) {
        return w != null && w >= 0 && w < WOOD_IDS.length ? WOOD_IDS[w] : "oak";
    }

    public static boolean in(int v, int... set) {
        for (int s : set) if (s == v) return true;
        return false;
    }

    public static boolean in(String v, String... set) {
        for (String s : set) if (s.equals(v)) return true;
        return false;
    }

    // ------------------------------------------------------------------ wood set
    public static final class Wood {
        public final String planks, log, stairs, slab, fence, door, plate, leaves, trapdoor;

        public Wood(String w) {
            planks = M + w + "_planks";
            log = M + w + "_log";
            stairs = M + w + "_stairs";
            slab = M + w + "_slab";
            fence = M + w + "_fence";
            door = w.equals("oak") ? M + "wooden_door" : M + w + "_door";
            plate = w.equals("oak") ? M + "wooden_pressure_plate" : M + w + "_pressure_plate";
            leaves = M + w + "_leaves";
            trapdoor = w.equals("oak") ? M + "trapdoor" : M + w + "_trapdoor";
        }
    }

    // ------------------------------------------------------------------ palette (house_common)
    public static final class Pal {
        public final String wall, pillar, stairs, slab, floor;
        public final boolean wood;
        public String full; // used by roof helpers

        public Pal(String wall, String pillar, String stairs, String slab, String floor, boolean wood) {
            this.wall = wall;
            this.pillar = pillar;
            this.stairs = stairs;
            this.slab = slab;
            this.floor = floor;
            this.wood = wood;
        }
    }

    static final String[] MAT_IDS = {"auto", "stone", "wood", "brick", "sand", "deepslate", "blackstone", "quartz", "mud", "prismarine"};
    static final Map<String, String[]> PAL = new LinkedHashMap<>();

    static {
        PAL.put("stone", new String[] {"stone_bricks", "chiseled_stone_bricks", "stone_brick_stairs", "stone_brick_slab", "polished_andesite"});
        PAL.put("brick", new String[] {"brick_block", "stone_bricks", "brick_stairs", "brick_slab", "stone_bricks"});
        PAL.put("sand", new String[] {"cut_sandstone", "chiseled_sandstone", "sandstone_stairs", "sandstone_slab", "smooth_sandstone"});
        PAL.put("deepslate", new String[] {"deepslate_bricks", "polished_deepslate", "deepslate_brick_stairs", "deepslate_brick_slab", "polished_deepslate"});
        PAL.put("blackstone", new String[] {"polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab", "polished_blackstone"});
        PAL.put("quartz", new String[] {"quartz_block", "chiseled_quartz_block", "quartz_stairs", "quartz_slab", "smooth_quartz"});
        PAL.put("mud", new String[] {"mud_bricks", "packed_mud", "mud_brick_stairs", "mud_brick_slab", "packed_mud"});
        PAL.put("prismarine", new String[] {"prismarine_bricks", "dark_prismarine", "prismarine_brick_stairs", "prismarine_brick_slab", "prismarine"});
    }

    /** Wall palette (house_common). `def` is the material used when the player did not choose one. */
    public static Pal palette(Opts o, String def, String wid, Wood wd) {
        int mi = o.matOr0();
        String m = mi >= 0 && mi < MAT_IDS.length ? MAT_IDS[mi] : "auto";
        if (m.equals("auto")) m = def;
        if (m.equals("wood")) {
            String logId = wid.equals("oak") || wid.equals("birch") ? "spruce" : "dark_oak";
            return new Pal(wd.planks, M + logId + "_log", wd.stairs, wd.slab, wd.planks, true);
        }
        String[] q = PAL.getOrDefault(m, PAL.get("stone"));
        return new Pal(M + q[0], M + q[1], M + q[2], M + q[3], M + q[4], false);
    }

    // ------------------------------------------------------------------ pieces
    public static void door(Ctx c, Wood wd, int x, int y, int z, String f) {
        c.put(x, y, z, wd.door, 3, St.door(f, false), Ex.item(wd.door));
        c.put(x, y + 1, z, wd.door, 3, St.door(f, true), Ex.noItemDep(0, -1, 0));
    }

    public static void ironDoor(Ctx c, int x, int y, int z, String f) {
        c.put(x, y, z, M + "iron_door", 3, St.door(f, false), Ex.item(M + "iron_door"));
        c.put(x, y + 1, z, M + "iron_door", 3, St.door(f, true), Ex.noItemDep(0, -1, 0));
    }

    public static int[] dirVec(String f) {
        return switch (f) {
            case "px" -> new int[] {1, 0};
            case "nx" -> new int[] {-1, 0};
            case "pz" -> new int[] {0, 1};
            default -> new int[] {0, -1};
        };
    }

    /** A bed whose head is at (hx,hz) and whose foot is one cell away: `toHead` is the direction foot -> head. */
    public static void bed(Ctx c, int hx, int y, int hz, String toHead) {
        int[] d = dirVec(toHead);
        c.put(hx - d[0], y, hz - d[1], M + "bed", 5, St.bed(toHead, false), Ex.item(M + "bed").withOpt(true));
        Ex e = Ex.item(null).withDep(-d[0], 0, -d[1]).withOpt(true);
        c.put(hx, y, hz, M + "bed", 5, St.bed(toHead, true), e);
    }

    public static void box(Ctx c, int x0, int x1, int y0, int y1, int z0, int z1, String id, double ph, St st) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) c.put(x, y, z, id, ph, st);
    }

    public static void ring(Ctx c, int x0, int x1, int z0, int z1, int y, String id, double ph) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                if (x == x0 || x == x1 || z == z0 || z == z1) c.put(x, y, z, id, ph);
    }

    public static final Comparator<Blk> ORDER = (a, b) -> {
        int r = Double.compare(a.ph, b.ph);
        if (r != 0) return r;
        r = Integer.compare(a.y, b.y);
        if (r != 0) return r;
        r = Integer.compare(a.z, b.z);
        if (r != 0) return r;
        return Integer.compare(a.x, b.x);
    };

    public static Plan finalize(Ctx c, int W, int D, Base base) {
        List<Blk> blocks = new ArrayList<>(c.map.values());
        blocks.sort(ORDER);
        int minX = 0, maxX = W - 1, minZ = 0, maxZ = D - 1, maxY = 0;
        for (Blk b : blocks) {
            minX = Math.min(minX, b.x);
            maxX = Math.max(maxX, b.x);
            minZ = Math.min(minZ, b.z);
            maxZ = Math.max(maxZ, b.z);
            maxY = Math.max(maxY, b.y);
        }
        Plan p = new Plan();
        p.blocks = blocks;
        p.W = W;
        p.D = D;
        p.minX = minX;
        p.maxX = maxX;
        p.minZ = minZ;
        p.maxZ = maxZ;
        p.maxY = maxY;
        p.front = 0;
        p.doorX = (W - 1) / 2.0;
        p.clearArea = new Area(0, W - 1, 0, D - 1);
        p.fillBlock = M + "cobblestone";
        if (base != null) {
            if (base.doorX != null) p.doorX = base.doorX;
            if (base.clearArea != null) p.clearArea = base.clearArea;
            if (base.fillBlock != null) p.fillBlock = base.fillBlock;
            if (base.clearCells != null) p.clearCells = base.clearCells;
            if (base.underground != null) p.underground = base.underground;
            if (base.needsItems != null) p.needsItems = base.needsItems;
            if (base.front != null) p.front = base.front;
        }
        return p;
    }

    /** Gable roof: the ridge runs along z, the slopes go down toward x = 0 and x = W-1. */
    public static void gableRoof(Ctx c, int W, int D, int y, String stairs, String full, String gableWall) {
        for (int i = 0; i <= (W - 1) / 2; i++) {
            int a = i, b = W - 1 - i, yy = y + i;
            for (int z = 0; z < D; z++) {
                if (a == b) c.put(a, yy, z, full, 2);
                else {
                    c.put(a, yy, z, stairs, 2, St.fh("px", "bottom"));
                    c.put(b, yy, z, stairs, 2, St.fh("nx", "bottom"));
                }
            }
            for (int x = a + 1; x <= b - 1; x++) {
                c.put(x, yy, 0, gableWall, 1);
                c.put(x, yy, D - 1, gableWall, 1);
            }
        }
    }

    /** Hip roof (hollow) that closes into a ridge or a point. */
    public static void hipRoof(Ctx c, int x0, int x1, int z0, int z1, int y, String stairs, String full) {
        for (int i = 0;; i++) {
            int a = x0 + i, b = x1 - i, cc = z0 + i, d = z1 - i;
            if (a > b || cc > d) break;
            int yy = y + i;
            for (int x = a; x <= b; x++)
                for (int z = cc; z <= d; z++) {
                    boolean eX = x == a || x == b;
                    boolean eZ = z == cc || z == d;
                    if (!eX && !eZ) continue;
                    if (a == b || cc == d || (eX && eZ)) c.put(x, yy, z, full, 2);
                    else if (z == cc) c.put(x, yy, z, stairs, 2, St.fh("pz", "bottom"));
                    else if (z == d) c.put(x, yy, z, stairs, 2, St.fh("nz", "bottom"));
                    else if (x == a) c.put(x, yy, z, stairs, 2, St.fh("px", "bottom"));
                    else c.put(x, yy, z, stairs, 2, St.fh("nx", "bottom"));
                }
        }
    }

    /** Stairs from the surface down to a door in the front wall (z = 0) of an underground room. */
    public static void entranceStairs(Ctx ctx, List<Cell> clear, int c, int yTop, Pal P, Wood wd, boolean iron, boolean lights) {
        int steps = -1 - yTop;
        int zs = -1 - steps;
        for (int k = 0; k <= steps; k++) {
            int z = zs + k;
            int y = -1 - k;
            ctx.put(c, y, z, P.stairs, 1, St.fh("nz", "bottom"));
            for (int yy = y + 1; yy <= y + 3; yy++) clear.add(new Cell(c, yy, z));
            if (lights && k % 2 == 1) {
                ctx.put(c - 1, y + 2, z, M + "sea_lantern", 4);
                ctx.put(c + 1, y + 2, z, M + "sea_lantern", 4);
            }
        }
        if (iron) ironDoor(ctx, c, yTop + 1, 0, "pz");
        else door(ctx, wd, c, yTop + 1, 0, "pz");
        for (int dx : new int[] {-1, 1})
            for (int y = 0; y <= 2; y++) ctx.put(c + dx, y, zs, P.pillar, 3, P.wood ? St.axis("y") : null);
        for (int dx = -1; dx <= 1; dx++) ctx.put(c + dx, 3, zs, P.wall, 3);
        ctx.put(c, 2, zs, M + "lantern", 4, St.hanging(true));
    }

    // ------------------------------------------------------------------ interior (interior.js)
    public record Roles(String[] roles, int beds) {}

    static final Map<String, String[]> ROLE_ITEMS = new LinkedHashMap<>();

    static {
        ROLE_ITEMS.put("kitchen", new String[] {"crafting_table", "furnace", "smoker", "blast_furnace", "cauldron", "barrel", "chest"});
        ROLE_ITEMS.put("storage", new String[] {"chest", "barrel", "chest", "barrel", "chest"});
        ROLE_ITEMS.put("workshop", new String[] {"anvil", "smithing_table", "grindstone", "stonecutter_block", "loom", "cartography_table", "fletching_table", "composter"});
        ROLE_ITEMS.put("enchant", new String[] {});
        ROLE_ITEMS.put("bedroom", new String[] {"lectern", "chest", "jukebox", "barrel"});
        ROLE_ITEMS.put("brewing", new String[] {"brewing_stand", "cauldron"});
    }

    /** Which rooms go on which floor. */
    public static Roles rolesFor(int floors, int f) {
        if (floors == 1) return new Roles(new String[] {"kitchen", "enchant", "workshop", "brewing", "bedroom", "storage"}, 1);
        if (floors == 2) {
            return f == 0 ? new Roles(new String[] {"kitchen", "workshop", "storage"}, 0)
                    : new Roles(new String[] {"enchant", "brewing", "bedroom", "storage"}, 2);
        }
        if (f == 0) return new Roles(new String[] {"kitchen", "storage"}, 0);
        if (f == 1) return new Roles(new String[] {"enchant", "workshop", "brewing"}, 0);
        return new Roles(new String[] {"bedroom", "storage"}, 2);
    }

    public static final class Room {
        public int y, x0, x1, z0, z1, beds;
        public Set<String> reserved;
        public String[] roles;
        public String carpet;
        public String fence, plate, stairs;

        public Room(int y, int x0, int x1, int z0, int z1, Set<String> reserved, String[] roles, int beds, String carpet, Wood wd) {
            this.y = y;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.reserved = reserved;
            this.roles = roles;
            this.beds = beds;
            this.carpet = carpet;
            this.fence = wd.fence;
            this.plate = wd.plate;
            this.stairs = wd.stairs;
        }

        boolean hasRole(String r) {
            for (String s : roles) if (s.equals(r)) return true;
            return false;
        }
    }

    public static Set<String> keys(String... ks) {
        Set<String> s = new java.util.LinkedHashSet<>();
        for (String k : ks) s.add(k);
        return s;
    }

    public static String k2(int x, int z) {
        return x + "," + z;
    }

    private record RingCell(int x, int z, String f, int inX, int inZ) {}

    public static void furnishRoom(Room r, Ctx c) {
        Set<String> used = new HashSet<>();
        Set<String> keep = new HashSet<>(r.reserved);
        int y = r.y + 1;
        int W = r.x1 - r.x0 + 1;
        int D = r.z1 - r.z0 + 1;
        java.util.function.BiPredicate<Integer, Integer> inside = (x, z) -> x >= r.x0 && x <= r.x1 && z >= r.z0 && z <= r.z1;
        java.util.function.BiPredicate<Integer, Integer> free = (x, z) -> inside.test(x, z) && !used.contains(k2(x, z)) && !keep.contains(k2(x, z)) && !c.has(x, y, z);
        interface Place {
            void at(int x, int z, String id, St st, Ex ex);
        }
        Place place = (x, z, id, st, ex) -> {
            Ex e = ex == null ? new Ex() : ex;
            Ex merged = new Ex();
            merged.opt = true;
            if (e.itemSet) merged.withItem(e.item);
            merged.dep = e.dep;
            if (e.opt != null) merged.opt = e.opt;
            merged.tag = e.tag;
            c.put(x, y, z, id, 5, st, merged);
            used.add(k2(x, z));
        };
        // ---- enchanting nook: table with bookshelves exactly two blocks away (enough for level 30)
        boolean nookDone = false;
        if (r.hasRole("enchant") && W >= 5 && D >= 5 && W * D >= 35) {
            for (int side : new int[] {0, 1}) {
                if (nookDone) break;
                int wallX = side == 0 ? r.x0 : r.x1;
                int dir = side == 0 ? 1 : -1;
                for (int zt = r.z1 - 2; zt >= r.z0 + 2 && !nookDone; zt--) {
                    boolean ok = true;
                    for (int i = 0; i <= 3 && ok; i++)
                        for (int dz = -2; dz <= 2 && ok; dz++)
                            if (!free.test(wallX + dir * i, zt + dz)) ok = false;
                    if (!ok) continue;
                    for (int dz = -2; dz <= 2; dz++) {
                        place.at(wallX, zt + dz, M + "bookshelf", null, null);
                        c.put(wallX, y + 1, zt + dz, M + "bookshelf", 5, null, Ex.opt());
                    }
                    for (int i = 1; i <= 3; i++) {
                        place.at(wallX + dir * i, zt - 2, M + "bookshelf", null, null);
                        place.at(wallX + dir * i, zt + 2, M + "bookshelf", null, null);
                        for (int dz = -1; dz <= 1; dz++) keep.add(k2(wallX + dir * i, zt + dz));
                    }
                    keep.remove(k2(wallX + dir * 2, zt));
                    place.at(wallX + dir * 2, zt, M + "enchanting_table", null, null);
                    nookDone = true;
                }
            }
        }
        // ---- ring of wall-adjacent cells, walked around the room
        List<RingCell> ring = new ArrayList<>();
        for (int x = r.x0; x <= r.x1; x++) ring.add(new RingCell(x, r.z1, "nz", 0, -1));
        for (int z = r.z1 - 1; z >= r.z0; z--) ring.add(new RingCell(r.x1, z, "nx", -1, 0));
        for (int x = r.x1 - 1; x >= r.x0; x--) ring.add(new RingCell(x, r.z0, "pz", 0, 1));
        for (int z = r.z0 + 1; z <= r.z1 - 1; z++) ring.add(new RingCell(r.x0, z, "px", 1, 0));
        // ---- beds: head against the wall
        int beds = r.beds;
        for (RingCell s : ring) {
            if (beds <= 0) break;
            int fx = s.x + s.inX;
            int fz = s.z + s.inZ;
            if (!free.test(s.x, s.z) || !free.test(fx, fz)) continue;
            if (!inside.test(fx + s.inX, fz + s.inZ) || keep.contains(k2(fx + s.inX, fz + s.inZ))) continue;
            String toHead = s.inX == 1 ? "nx" : s.inX == -1 ? "px" : s.inZ == 1 ? "nz" : "pz";
            place.at(fx, fz, M + "bed", St.bed(toHead, false), Ex.item(M + "bed"));
            place.at(s.x, s.z, M + "bed", St.bed(toHead, true), Ex.item(null).withDep(s.inX, 0, s.inZ));
            keep.add(k2(fx + s.inX, fz + s.inZ));
            beds--;
        }
        // ---- single blocks along the walls, by priority
        List<String> queue = new ArrayList<>();
        for (String role : r.roles) for (String id : ROLE_ITEMS.get(role)) queue.add(id);
        if (r.hasRole("enchant") && !nookDone) {
            queue.add("enchanting_table");
            queue.add("bookshelf");
            queue.add("bookshelf");
        }
        Set<String> seen = new HashSet<>();
        List<String> items = new ArrayList<>();
        for (String id : queue) {
            if (id.equals("chest") || id.equals("barrel") || id.equals("bookshelf") || id.equals("cauldron")) {
                items.add(id);
                continue;
            }
            if (seen.contains(id)) continue;
            seen.add(id);
            items.add(id);
        }
        int qi = 0;
        for (RingCell s : ring) {
            if (qi >= items.size()) break;
            if (!free.test(s.x, s.z)) continue;
            String id = items.get(qi++);
            switch (id) {
                case "furnace", "smoker", "blast_furnace", "chest", "lectern", "anvil", "stonecutter_block", "loom", "grindstone" ->
                        place.at(s.x, s.z, M + id, St.f(s.f), null);
                default -> place.at(s.x, s.z, M + id, null, null);
            }
        }
        // ---- dining table in the kitchen, rug elsewhere
        int cx = Math.floorDiv(r.x0 + r.x1, 2);
        int cz = Math.floorDiv(r.z0 + r.z1, 2);
        if (r.hasRole("kitchen") && W >= 5 && D >= 5 && free.test(cx, cz) && free.test(cx - 1, cz) && free.test(cx + 1, cz)) {
            place.at(cx, cz, r.fence, null, null);
            c.put(cx, y + 1, cz, r.plate, 5, null, Ex.opt().withDep(0, -1, 0));
            place.at(cx - 1, cz, r.stairs, St.fh("nx", "bottom"), null);
            place.at(cx + 1, cz, r.stairs, St.fh("px", "bottom"), null);
        } else {
            int half = W >= 7 && D >= 7 ? 1 : 0;
            for (int x = cx - half; x <= cx + half + (half != 0 ? 0 : 1); x++)
                for (int z = cz - half; z <= cz + half; z++)
                    if (free.test(x, z)) place.at(x, z, r.carpet, null, null);
        }
    }

    /** JS Math.round (rounds .5 up). */
    public static long jsRound(double v) {
        return (long) Math.floor(v + 0.5);
    }

    public static Supplier<String> constant(String s) {
        return () -> s;
    }
}
