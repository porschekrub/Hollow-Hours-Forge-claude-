package com.helperbuddy.brain;

import com.helperbuddy.build.BlockMap;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.gen.ExtraGen;
import com.helperbuddy.gen.Gen;
import com.helperbuddy.gen.HouseGen;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Building: clears the natural ground of the site, fills the foundation down to solid ground, then places the plan
 * block by block (5 / 10 / 20 / 40 blocks a second). Free by default; with "need blocks" (and always for a nether
 * portal) it uses real blocks from its bag and nearby chests.
 */
public final class BuildJob {
    static final double[] SPEEDS = {0.25, 0.5, 1.0, 2.0};
    static final double BUILD_REACH = 7;
    static final int MAX_FILL = 12;

    final Brain b;
    final HelperEntity h;

    // ---- the job (saved)
    @Nullable
    Gen.Opts opts;
    @Nullable
    List<CustomBlock> custom;
    String customName = "";
    BlockPos origin = BlockPos.ZERO;
    Direction facing = Direction.NORTH;
    String dim = "";
    boolean clearing;
    int idx, placed, speed = 1;
    final List<Fill> fill = new ArrayList<>();
    @Nullable
    Gen.Opts lastOpts;

    public record Fill(int x, int z, int n, String id) {}

    public record CustomBlock(int x, int y, int z, String id, @Nullable String f, @Nullable String half, @Nullable String axis) {}

    // ---- runtime
    record PB(Gen.Blk blk, @Nullable BlockState state) {}

    @Nullable
    Gen.Plan plan;
    List<PB> order;
    Map<Long, String> idOf;
    List<int[]> clearList;
    int clearIdx;
    double acc;
    long waitMsgAt = -9999, containersAt = -9999, outlineUntil;
    List<Bag.Store> containers = new ArrayList<>();
    boolean waiting;

    BuildJob(Brain b) {
        this.b = b;
        this.h = b.h;
    }

    public boolean active() {
        return opts != null || custom != null;
    }

    // ------------------------------------------------------------------ transform
    BlockPos toWorld(int x, int y, int z) {
        Direction l = facing.getCounterClockWise();
        return origin.offset(x * l.getStepX() + z * facing.getStepX(), y, x * l.getStepZ() + z * facing.getStepZ());
    }

    Vec3 cellCenter(double x, int y, double z) {
        Direction l = facing.getCounterClockWise();
        return new Vec3(origin.getX() + 0.5 + x * l.getStepX() + z * facing.getStepX(), origin.getY() + y + 0.5,
                origin.getZ() + 0.5 + x * l.getStepZ() + z * facing.getStepZ());
    }

    // ------------------------------------------------------------------ plan
    static Gen.Plan generate(@Nullable Gen.Opts o, @Nullable List<CustomBlock> custom) {
        if (custom != null) {
            Gen.Ctx c = new Gen.Ctx();
            int maxX = 0, maxZ = 0;
            for (CustomBlock cb : custom) {
                Gen.St st = new Gen.St();
                st.f = cb.f;
                st.half = cb.half;
                st.axis = cb.axis;
                c.put(cb.x, cb.y, cb.z, cb.id, 1 + cb.y * 0.001, st);
                maxX = Math.max(maxX, cb.x);
                maxZ = Math.max(maxZ, cb.z);
            }
            Gen.Plan p = Gen.finalize(c, maxX + 1, maxZ + 1, null);
            p.fillBlock = Gen.M + "cobblestone";
            return p;
        }
        return HouseGen.generate(o);
    }

    Gen.Plan plan() {
        if (plan == null) {
            plan = generate(opts, custom);
            build();
        }
        return plan;
    }

    void build() {
        List<Gen.Blk> all = new ArrayList<>();
        Gen.Ctx fc = new Gen.Ctx();
        for (Fill f : fill)
            for (int i = 1; i <= f.n; i++) fc.put(f.x, -i, f.z, f.id, 0.5, f.id.endsWith("_log") ? Gen.St.axis("y") : null);
        all.addAll(fc.map.values());
        all.addAll(plan.blocks);
        double cx = (plan.W - 1) / 2.0, cz = (plan.D - 1) / 2.0;
        java.util.function.ToDoubleFunction<Gen.Blk> ang = bl -> {
            double a = Math.atan2(bl.z - cz, bl.x - cx) + Math.PI / 2;
            if (a < 0) a += Math.PI * 2;
            return a;
        };
        all.sort((x, y) -> {
            int c = Double.compare(x.ph, y.ph);
            if (c != 0) return c;
            c = Integer.compare(x.y, y.y);
            if (c != 0) return c;
            c = Integer.compare(x.dep != null ? 1 : 0, y.dep != null ? 1 : 0);
            if (c != 0) return c;
            return Double.compare(ang.applyAsDouble(x), ang.applyAsDouble(y));
        });
        order = new ArrayList<>(all.size());
        idOf = new HashMap<>();
        for (Gen.Blk bl : all) {
            order.add(new PB(bl, BlockMap.state(bl, facing)));
            idOf.put(Gen.key(bl.x, bl.y, bl.z), bl.id);
        }
        clearList = null;
        clearIdx = 0;
    }

    /** Local cells that must be emptied before building (top to bottom). */
    static List<int[]> clearSet(Gen.Plan plan, Gen.Opts opts) {
        Map<Long, int[]> out = new LinkedHashMap<>();
        java.util.function.Consumer<int[]> add = c -> out.put(Gen.key(c[0], c[1], c[2]), c);
        Gen.Area a = plan.clearArea;
        for (int x = a.x0; x <= a.x1; x++)
            for (int z = a.z0; z <= a.z1; z++)
                for (int y = 0; y <= plan.maxY; y++) add.accept(new int[] {x, y, z});
        if (plan.clearCells != null) for (Gen.Cell cc : plan.clearCells) add.accept(new int[] {cc.x(), cc.y(), cc.z()});
        for (Gen.Blk bl : plan.blocks) {
            if (bl.y < 0) continue; // below-ground features (pool, deck) simply replace the natural ground
            add.accept(new int[] {bl.x, bl.y, bl.z});
            if (bl.x < 0 || bl.x >= plan.W || bl.z < 0 || bl.z >= plan.D) {
                add.accept(new int[] {bl.x, bl.y + 1, bl.z});
                add.accept(new int[] {bl.x, bl.y + 2, bl.z});
            }
        }
        int c = (int) Math.floor(plan.doorX);
        int S = opts != null && !ExtraGen.isExtraType(opts) && opts.style != null && opts.style == 3 ? 3 : 0;
        for (int z = plan.front; z < 0; z++)
            for (int y = Math.max(0, S + z + 4); y <= S + 3; y++) add.accept(new int[] {c, y, z});
        List<int[]> arr = new ArrayList<>(out.values());
        arr.sort((p, q) -> p[1] != q[1] ? Integer.compare(q[1], p[1]) : p[2] != q[2] ? Integer.compare(p[2], q[2]) : Integer.compare(p[0], q[0]));
        return arr;
    }

    // ------------------------------------------------------------------ site
    public record Site(boolean ok, String th, String en, List<Fill> fill, int clearCount, boolean poolDropped, @Nullable Prepared job) {
        Site(boolean ok, String th, String en, List<Fill> fill, int clearCount, boolean poolDropped) {
            this(ok, th, en, fill, clearCount, poolDropped, null);
        }
    }

    static boolean natural(Level l, BlockPos p, BlockState s, Map<Long, Boolean> trees) {
        if (s.isAir()) return true;
        if (Jobs.isLog(s)) {
            Boolean known = trees.get(p.asLong());
            if (known != null) return known;
            Trees.Tree t = Trees.analyze(l, p);
            for (BlockPos q : t.logs()) trees.put(q.asLong(), t.natural());
            trees.put(p.asLong(), t.natural());
            return t.natural();
        }
        return Jobs.natural(l, p, s);
    }

    static String blockName(BlockState s) {
        return s.getBlock().getName().getString();
    }

    Site analyze(Level l, Gen.Opts o, boolean poolDropped) {
        Gen.Plan p = generate(o, custom);
        List<Fill> fl = new ArrayList<>();
        int clearCount = 0;
        Map<Long, Boolean> trees = new HashMap<>();
        for (int[] c : clearSet(p, o)) {
            BlockPos w = toWorld(c[0], c[1], c[2]);
            if (!l.isLoaded(w)) return new Site(false, "พื้นที่ยังโหลดไม่ครบ ลองเดินเข้าไปใกล้ๆ อีกนิด", "The area is not loaded yet, walk a bit closer", fl, 0, poolDropped);
            BlockState s = l.getBlockState(w);
            if (!s.getFluidState().isEmpty())
                return new Site(false, "มีน้ำหรือลาวาในพื้นที่ที่จะสร้าง ลองหาที่แห้งๆ นะ", "There is water or lava where it would stand. Find a dry spot", fl, 0, poolDropped);
            if (!natural(l, w, s, trees))
                return new Site(false, "มีบล็อก §e" + blockName(s) + "§r ขวางอยู่ที่ (" + w.getX() + ", " + w.getY() + ", " + w.getZ() + ") ผมจะไม่ทุบของที่คุณสร้างไว้ ลองย้ายที่หรือหันไปทางอื่นนะ",
                        "There is §e" + blockName(s) + "§r in the way at (" + w.getX() + ", " + w.getY() + ", " + w.getZ() + "). I never break your builds: move or face another way",
                        fl, 0, poolDropped);
            if (!s.isAir()) clearCount++;
        }
        // a swimming pool needs natural ground around it, otherwise it is left out
        if (o != null && Boolean.TRUE.equals(o.pool)) {
            boolean ok = true;
            for (Gen.Blk bl : p.blocks) {
                if (!"pool".equals(bl.tag)) continue;
                BlockPos w = toWorld(bl.x, bl.y, bl.z);
                BlockState s = l.getBlockState(w);
                if (s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || !s.isAir() && s.getFluidState().isEmpty() && !natural(l, w, s, trees)
                        || bl.id.endsWith("water") && s.isAir()) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                Gen.Opts o2 = o.copy();
                o2.pool = false;
                return analyze(l, o2, true);
            }
        }
        // foundation: fill under the outer walls down to solid ground
        int S = o != null && !ExtraGen.isExtraType(o) && o.style != null && o.style == 3 ? 3 : 0;
        Map<Long, Fill> cols = new LinkedHashMap<>();
        for (Gen.Blk bl : p.blocks) {
            if (bl.y != 0 || bl.optional()) continue;
            boolean inside = bl.x > 0 && bl.x < p.W - 1 && bl.z > 0 && bl.z < p.D - 1;
            if (inside && S == 0) continue;
            String id = S > 0 && bl.id.endsWith("_log") ? bl.id : bl.id.contains("stripped_") ? bl.id : p.fillBlock;
            cols.put(Gen.key(bl.x, 0, bl.z), new Fill(bl.x, bl.z, 0, id));
        }
        for (Fill col : cols.values()) {
            int depth = 0;
            for (int y = -1; y >= -MAX_FILL - 1; y--) {
                BlockPos w = toWorld(col.x, y, col.z);
                BlockState s = l.getBlockState(w);
                if (s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))
                    return new Site(false, "มีลาวาอยู่ใต้พื้นที่สร้าง อันตราย! ลองย้ายที่นะ", "There is lava under the site. Too dangerous, move somewhere else", fl, clearCount, poolDropped);
                if (!Mover.passable(l, w) && s.getFluidState().isEmpty()) break;
                depth++;
                if (depth > MAX_FILL)
                    return new Site(false, "พื้นตรงนี้ต่ำ/เป็นหน้าผาเกินไป ลองหาที่ราบกว่านี้นะ", "The ground here drops too much (a cliff). Find flatter ground", fl, clearCount, poolDropped);
            }
            if (depth > 0) fl.add(new Fill(col.x, col.z, depth, col.id));
        }
        return new Site(true, "", "", fl, clearCount, poolDropped);
    }

    // ------------------------------------------------------------------ variety
    /** Fills the options the player did not choose with random ones, so no two houses look the same. */
    public static void randomize(Gen.Opts o, RandomSource r) {
        if (o.variant == null) o.variant = r.nextInt(1000);
        if (o.wood == null) o.wood = r.nextInt(8);
        String t = o.type == null ? "standard" : o.type;
        if (t.equals("standard")) {
            if (o.style == null) o.style = r.nextInt(4);
            if (o.roof == null) o.roof = r.nextInt(3) == 0 ? 0 : r.nextInt(7);
            if (o.porch == null && r.nextBoolean()) o.porch = true;
            if (o.chimney == null && r.nextInt(3) == 0) o.chimney = true;
        } else if (o.mat == null && (t.equals("tower") || t.equals("castle") || t.equals("box") || t.equals("underground") || t.equals("safehouse")
                || t.equals("mansion") || t.equals("hotel") || t.equals("hospital") || t.equals("school") || t.equals("library") || t.equals("prison")
                || t.equals("lighthouse"))) {
            int[] nice = {0, 0, 1, 3, 4, 5, 6, 7, 8, 9};
            o.mat = nice[r.nextInt(nice.length)];
        }
    }

    // ------------------------------------------------------------------ start / stop
    static Direction facingOf(Player p) {
        return p.getDirection().getAxis().isHorizontal() ? p.getDirection() : Direction.NORTH;
    }

    /** Origin so the house stands in front of the spot `at` (where the player stands), its door facing them. */
    BlockPos originFor(Level lv, BlockPos at, Gen.Plan plan, Direction f) {
        Direction l = f.getCounterClockWise();
        int c = Math.floorDiv(plan.minX + plan.maxX, 2);
        int py = at.getY();
        for (int i = 0; i < 24 && Mover.passable(lv, new BlockPos(at.getX(), py - 1, at.getZ())); i++) py--;
        int back = plan.minZ - 2;
        return new BlockPos(at.getX() - c * l.getStepX() - back * f.getStepX(), py, at.getZ() - c * l.getStepZ() - back * f.getStepZ());
    }

    /** A building planned at a fixed spot, ready to start now or later (a step of a longer order). */
    public record Prepared(@Nullable Gen.Opts opts, @Nullable List<CustomBlock> custom, String name, BlockPos origin, Direction facing, String dim,
                           List<Fill> fill, int speed) {}

    /** Plans a building in front of the player and checks the site (does not start it). */
    public Site prepare(ServerPlayer p, @Nullable Gen.Opts o0, @Nullable List<CustomBlock> customBlocks, String name) {
        return prepareAt(p.level(), p.blockPosition(), facingOf(p), o0, customBlocks, name);
    }

    /** Plans a building in front of `at` facing `f` (no player needed: used by tests too). */
    public Site prepareAt(Level level, BlockPos at, Direction f, @Nullable Gen.Opts o0, @Nullable List<CustomBlock> customBlocks, String name) {
        Gen.Opts o = o0 == null ? null : o0.copy();
        int sp = HBConfig.BUILD_SPEED_DEFAULT.get();
        if (o != null && o.speed != null) sp = Math.max(0, Math.min(3, o.speed));
        if (o != null) o.speed = null;
        // analysis runs with the new spot in the fields, then they are put back
        Gen.Opts pOpts = opts;
        List<CustomBlock> pCustom = custom;
        BlockPos pOrigin = origin;
        Direction pFacing = facing;
        custom = customBlocks;
        Gen.Plan pl = generate(o, customBlocks);
        origin = originFor(level, at, pl, f);
        facing = f;
        Site s = analyze(level, o, false);
        Prepared pr = null;
        if (s.ok()) {
            if (o != null && s.poolDropped()) o.pool = false;
            pr = new Prepared(o, customBlocks, name == null ? "" : name, origin, facing, level.dimension().location().toString(), new ArrayList<>(s.fill()), sp);
        }
        opts = pOpts;
        custom = pCustom;
        origin = pOrigin;
        facing = pFacing;
        return new Site(s.ok(), s.th(), s.en(), s.fill(), s.clearCount(), s.poolDropped(), pr);
    }

    /** Starts a prepared building now. */
    public void start(Prepared pr) {
        b.leaveJob();
        opts = pr.opts() == null ? null : pr.opts().copy();
        custom = pr.custom();
        customName = pr.name();
        origin = pr.origin();
        facing = pr.facing();
        dim = pr.dim();
        speed = pr.speed();
        clearing = true;
        idx = 0;
        placed = 0;
        fill.clear();
        fill.addAll(pr.fill());
        plan = null;
        waiting = false;
        outlineUntil = h.level().getGameTime() + 60;
        if (opts != null) lastOpts = opts.copy();
        h.setMode(Mode.BUILD);
    }

    /** Plans and starts at once. */
    public Site begin(ServerPlayer p, @Nullable Gen.Opts o, @Nullable List<CustomBlock> customBlocks, String name) {
        Site s = prepare(p, o, customBlocks, name);
        if (s.ok() && s.job() != null) start(s.job());
        return s;
    }

    public static CompoundTag savePrepared(Prepared pr) {
        CompoundTag t = new CompoundTag();
        if (pr.opts() != null) t.put("opts", saveOpts(pr.opts()));
        if (pr.custom() != null) t.put("custom", saveCustom(pr.custom()));
        t.putString("cname", pr.name());
        t.putLong("o", pr.origin().asLong());
        t.putInt("f", pr.facing().get2DDataValue());
        t.putString("dim", pr.dim());
        t.put("fill", saveFill(pr.fill()));
        t.putInt("speed", pr.speed());
        return t;
    }

    public static Prepared loadPrepared(CompoundTag t) {
        return new Prepared(t.contains("opts") ? loadOpts(t.getCompound("opts")) : null, t.contains("custom") ? loadCustom(t.getList("custom", Tag.TAG_COMPOUND)) : null,
                t.getString("cname"), BlockPos.of(t.getLong("o")), Direction.from2DDataValue(t.getInt("f")), t.getString("dim"),
                loadFill(t.getList("fill", Tag.TAG_COMPOUND)), t.getInt("speed"));
    }

    static ListTag saveCustom(List<CustomBlock> custom) {
        ListTag cl = new ListTag();
        for (CustomBlock cb : custom) {
            CompoundTag c = new CompoundTag();
            c.putInt("x", cb.x);
            c.putInt("y", cb.y);
            c.putInt("z", cb.z);
            c.putString("id", cb.id);
            if (cb.f != null) c.putString("f", cb.f);
            if (cb.half != null) c.putString("h", cb.half);
            if (cb.axis != null) c.putString("a", cb.axis);
            cl.add(c);
        }
        return cl;
    }

    static List<CustomBlock> loadCustom(ListTag l) {
        List<CustomBlock> out = new ArrayList<>();
        for (Tag x : l) {
            CompoundTag c = (CompoundTag) x;
            out.add(new CustomBlock(c.getInt("x"), c.getInt("y"), c.getInt("z"), c.getString("id"), c.contains("f") ? c.getString("f") : null,
                    c.contains("h") ? c.getString("h") : null, c.contains("a") ? c.getString("a") : null));
        }
        return out;
    }

    static ListTag saveFill(List<Fill> fill) {
        ListTag fl = new ListTag();
        for (Fill f : fill) {
            CompoundTag c = new CompoundTag();
            c.putInt("x", f.x);
            c.putInt("z", f.z);
            c.putInt("n", f.n);
            c.putString("id", f.id);
            fl.add(c);
        }
        return fl;
    }

    static List<Fill> loadFill(ListTag l) {
        List<Fill> out = new ArrayList<>();
        for (Tag x : l) {
            CompoundTag c = (CompoundTag) x;
            out.add(new Fill(c.getInt("x"), c.getInt("z"), c.getInt("n"), c.getString("id")));
        }
        return out;
    }

    public void cancel() {
        opts = null;
        custom = null;
        plan = null;
        order = null;
        h.setWorking(false);
    }

    public Gen.Opts lastOpts() {
        return lastOpts;
    }

    public boolean free() {
        boolean portal = opts != null && "portal".equals(opts.type);
        return HBConfig.FREE_BUILD.get() && !h.settings.get("needBlocks") && !portal;
    }

    public int progress() {
        if (!active()) return 0;
        plan();
        if (clearing) return 0;
        return Math.min(100, idx * 100 / Math.max(1, order.size()));
    }

    public String name(boolean th) {
        if (custom != null) return customName.isEmpty() ? (th ? "สิ่งก่อสร้าง" : "building") : customName;
        if (opts == null) return "";
        String n = ExtraGen.typeName(opts.type == null ? "standard" : opts.type, th);
        String v = ExtraGen.variantName(opts, th);
        return v == null ? n : n + " (" + v + ")";
    }

    public String statusShort(boolean th) {
        if (!active()) return "";
        if (clearing) return th ? "เคลียร์พื้นที่" : "clearing";
        if (waiting) return th ? "รอบล็อก " + progress() + "%" : "need blocks " + progress() + "%";
        return progress() + "%";
    }

    // ------------------------------------------------------------------ materials
    /** Required / optional count of each item still to place. */
    public Map<Item, int[]> remaining() {
        Map<Item, int[]> m = new LinkedHashMap<>();
        if (!active()) return m;
        plan();
        for (int i = clearing ? 0 : idx; i < order.size(); i++) {
            Gen.Blk bl = order.get(i).blk;
            String it = bl.placeItem();
            if (it == null) continue;
            Item item = BlockMap.item(it);
            if (item == null || item == Items.AIR) continue;
            int[] e = m.computeIfAbsent(item, k -> new int[2]);
            if (bl.optional()) e[1]++;
            else e[0]++;
        }
        // one flint and steel lights a whole portal
        int[] fs = m.get(Items.FLINT_AND_STEEL);
        if (fs != null) {
            fs[0] = Math.min(fs[0], 1);
            fs[1] = 0;
        }
        return m;
    }

    /** "obsidian x10, ..." of what it still lacks. */
    public String missingText(boolean th, int maxLines) {
        StringBuilder sb = new StringBuilder();
        int lines = 0;
        for (Map.Entry<Item, int[]> e : remaining().entrySet()) {
            int need = e.getValue()[0] - Bag.count(h, e.getKey());
            if (need <= 0) continue;
            if (lines++ >= maxLines) break;
            sb.append("\n§c").append(new ItemStack(e.getKey()).getHoverName().getString()).append(th ? " §fขาด " : " §fmissing ").append(need);
        }
        return sb.toString();
    }

    public int missingCount() {
        int n = 0;
        for (Map.Entry<Item, int[]> e : remaining().entrySet()) n += Math.max(0, e.getValue()[0] - Bag.count(h, e.getKey()));
        return n;
    }

    /** Moves the blocks still needed from the player's inventory into the helper's bag. */
    public int takeFrom(Player p) {
        int moved = 0;
        for (Map.Entry<Item, int[]> e : remaining().entrySet()) {
            int need = e.getValue()[0] + e.getValue()[1] - Bag.count(h, e.getKey());
            for (int i = 0; i < p.getInventory().getContainerSize() && need > 0; i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (!st.is(e.getKey())) continue;
                int take = Math.min(need, st.getCount());
                ItemStack left = Bag.add(h, st.copyWithCount(take));
                int put = take - left.getCount();
                if (put <= 0) return moved;
                st.shrink(put);
                moved += put;
                need -= put;
            }
        }
        p.getInventory().setChanged();
        return moved;
    }

    // ------------------------------------------------------------------ helpers
    boolean canReplace(Level l, BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() && !s.blocksMotion()) return true;
        return Jobs.natural(l, p, s) && !Jobs.isLog(s) || Jobs.isLog(s) && Trees.isNaturalTreeLog(l, p);
    }

    void storeCleared(BlockState s) {
        Item item = null;
        if (Jobs.isLog(s)) item = s.getBlock().asItem();
        else if (s.is(Blocks.STONE)) item = Items.COBBLESTONE;
        else if (s.is(Blocks.DEEPSLATE)) item = Items.COBBLED_DEEPSLATE;
        else if (s.is(net.minecraft.tags.BlockTags.DIRT)) item = Items.DIRT;
        else if (s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.COBBLESTONE)) item = s.getBlock().asItem();
        if (item == null) return;
        if (Bag.count(h, item) >= 64 && !h.settings.get("keepJunk") && !Jobs.isLog(s)) return;
        ItemStack st = new ItemStack(item);
        if (Bag.hasRoomFor(h, st)) Bag.add(h, st);
    }

    int[] standPoint(Gen.Blk bl) {
        Gen.Plan p = plan;
        int minX = p.minX - 2, maxX = p.maxX + 2, minZ = p.minZ - 2, maxZ = p.maxZ + 2;
        int cx = Math.max(minX, Math.min(maxX, bl.x)), cz = Math.max(minZ, Math.min(maxZ, bl.z));
        int[][] c = {{cx, minZ}, {maxX, cz}, {cx, maxZ}, {minX, cz}};
        int[] best = c[0];
        double bd = Double.MAX_VALUE;
        for (int[] q : c) {
            double d = Math.pow(q[0] - bl.x, 2) + Math.pow(q[1] - bl.z, 2);
            if (d < bd - 0.01) {
                bd = d;
                best = q;
            }
        }
        return best;
    }

    boolean teleportNear(int lx, int lz) {
        BlockPos base = toWorld(lx, 0, lz);
        Level l = h.level();
        for (int dy = 4; dy >= -10; dy--) {
            BlockPos p = base.above(dy);
            if (Mover.standable(l, p)) {
                if (l instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, h.getX(), h.getY() + 1, h.getZ(), 6, 0.3, 0.5, 0.3, 0);
                h.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                h.getNavigation().stop();
                l.playSound(null, p, SoundEvents.SHULKER_TELEPORT, SoundSource.NEUTRAL, 0.4f, 1.4f);
                b.mover().resetProgress();
                return true;
            }
        }
        return false;
    }

    void outline() {
        if (!(h.level() instanceof ServerLevel sl)) return;
        Gen.Plan p = plan;
        double x0 = p.minX - 0.5, x1 = p.maxX + 0.5, z0 = p.minZ - 0.5, z1 = p.maxZ + 0.5;
        for (double x = x0; x <= x1; x += 1) {
            Vec3 a = cellCenter(x, 0, z0), c = cellCenter(x, 0, z1);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, a.x, a.y - 0.3, a.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y - 0.3, c.z, 1, 0, 0, 0, 0);
        }
        for (double z = z0; z <= z1; z += 1) {
            Vec3 a = cellCenter(x0, 0, z), c = cellCenter(x1, 0, z);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, a.x, a.y - 0.3, a.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y - 0.3, c.z, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ tick
    void tick() {
        if (!active()) {
            h.setMode(Mode.FOLLOW);
            return;
        }
        Level l = h.level();
        if (!l.dimension().location().toString().equals(dim)) {
            b.mover().stop();
            Say.notify(h, "build:dim", 20 * 60, "ผมอยู่คนละมิติกับบ้านที่กำลังสร้าง พาผมกลับไปที่เดิมก่อนนะ", "I'm in another dimension than the building. Take me back there first");
            return;
        }
        plan();
        long now = l.getGameTime();
        if (now < outlineUntil && now % 10 == 0) outline();
        if (h.tickCount % 10 == 0) h.setWorking(false);
        // -------- stage 1: clearing the site (natural blocks only)
        if (clearing) {
            if (clearList == null) {
                clearList = clearSet(plan, opts);
                clearIdx = 0;
            }
            Vec3 fw = cellCenter((plan.W - 1) / 2.0, 0, plan.minZ - 2);
            if (Math.hypot(h.getX() - fw.x, h.getZ() - fw.z) > 3) {
                Mover.Res r = b.mover().moveTo(new Vec3(fw.x, fw.y - 0.5, fw.z), 1.2, 1.5);
                if (r == Mover.Res.NO_PATH || b.mover().stuck(60)) teleportNear((plan.W - 1) / 2, plan.minZ - 2);
            } else b.mover().stop();
            int budget = 10, scans = 0;
            Map<Long, Boolean> trees = new HashMap<>();
            while (budget > 0 && scans++ < 400 && clearIdx < clearList.size()) {
                int[] c = clearList.get(clearIdx++);
                BlockPos w = toWorld(c[0], c[1], c[2]);
                BlockState s = l.getBlockState(w);
                if (s.isAir() || !s.getFluidState().isEmpty() && !s.blocksMotion()) continue;
                if (!natural(l, w, s, trees)) continue;
                budget--;
                l.setBlock(w, Blocks.AIR.defaultBlockState(), 3);
                storeCleared(s);
                if (budget % 3 == 0) {
                    if (l instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CRIT, w.getX() + 0.5, w.getY() + 0.5, w.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
                    l.playSound(null, w, s.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.5f, 1f);
                    h.swing(InteractionHand.MAIN_HAND);
                }
                b.mover().look(w);
                h.setWorking(true);
            }
            // things that fell (saplings, flowers) go into the bag
            if (now % 20 == 0) b.work.pickup(s -> true, 6);
            if (clearIdx >= clearList.size()) {
                clearing = false;
                idx = 0;
            }
            return;
        }
        // -------- stage 2: placing blocks
        boolean free = free();
        acc += SPEEDS[Math.max(0, Math.min(3, speed))];
        int guard = 0;
        waiting = false;
        while (acc >= 1 && guard++ < 60) {
            if (idx >= order.size()) {
                finish();
                return;
            }
            PB pb = order.get(idx);
            Gen.Blk bl = pb.blk;
            BlockPos w = toWorld(bl.x, bl.y, bl.z);
            if (!l.isLoaded(w)) return;
            BlockState cur = l.getBlockState(w);
            BlockState want = pb.state;
            if (want == null || cur.is(want.getBlock()) && !want.isAir()) {
                idx++;
                continue;
            }
            if (!canReplace(l, w, cur)) {
                idx++;
                continue;
            }
            if (bl.dep != null) {
                int dx = bl.x + bl.dep[0], dy = bl.y + bl.dep[1], dz = bl.z + bl.dep[2];
                String wantId = idOf.get(Gen.key(dx, dy, dz));
                BlockState ds = BlockMap.state(wantId == null ? "air" : wantId, null, facing);
                if (wantId == null || ds == null || !l.getBlockState(toWorld(dx, dy, dz)).is(ds.getBlock())) {
                    idx++;
                    continue;
                }
            }
            // reach: walk to a stand point around the building (it can reach far once there)
            Vec3 cc = cellCenter(bl.x, bl.y, bl.z);
            if (Math.hypot(h.getX() - cc.x, h.getZ() - cc.z) > BUILD_REACH) {
                int[] sp = standPoint(bl);
                Vec3 spw = cellCenter(sp[0], 0, sp[1]);
                double dsp = Math.hypot(h.getX() - spw.x, h.getZ() - spw.z);
                if (dsp > 1.5) {
                    Mover.Res r = b.mover().moveTo(new Vec3(spw.x, spw.y - 0.5, spw.z), 1.15, 1.0);
                    if (r == Mover.Res.MOVING && !b.mover().stuck(50)) return;
                    if (r != Mover.Res.ARRIVED) {
                        teleportNear(sp[0], sp[1]);
                        return;
                    }
                }
            }
            b.mover().stop();
            // somebody standing in the way
            if (want.blocksMotion()) {
                boolean blocked = false;
                for (Entity e : l.getEntities(h, new AABB(w), e -> !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb) && e.isPickable() || e instanceof Player)) {
                    blocked = true;
                    if (e instanceof Player) Say.notify(h, "inway", 200, "ขอทางหน่อยครับ มีคนยืนขวางจุดที่จะวางบล็อก", "Excuse me, someone is standing where the next block goes");
                    else if (e instanceof LivingEntity le)
                        le.push((h.getRandom().nextDouble() - 0.5) * 0.6, 0.3, (h.getRandom().nextDouble() - 0.5) * 0.6);
                }
                if (blocked) {
                    acc = 0;
                    return;
                }
            }
            // materials
            String itemId = bl.placeItem();
            Item item = itemId == null ? null : BlockMap.item(itemId);
            boolean usesItem = item != null && item != Items.AIR && !free;
            if (usesItem && Bag.count(h, item) <= 0) {
                if (h.settings.get("chestBuild")) {
                    if (now - containersAt > 100) {
                        containers = Bag.containersNear(l, h.blockPosition(), 10);
                        containersAt = now;
                    }
                    if (!containers.isEmpty()) {
                        int[] rem = remaining().get(item);
                        int need = rem == null ? 1 : rem[0] + rem[1];
                        final Item fi = item;
                        Bag.pull(h, containers, s -> s.is(fi), Math.min(need, 64 * 4));
                    }
                }
                if (Bag.count(h, item) <= 0) {
                    if (bl.optional()) {
                        idx++;
                        continue;
                    }
                    acc = 0;
                    waiting = true;
                    if (now - waitMsgAt > 20 * 25) {
                        waitMsgAt = now;
                        boolean th = Say.thai(h.owner());
                        Say.owner(h, "§eบล็อกหมดแล้ว รอเราเอามาให้อยู่นะ!§r ยังขาด:" + missingText(true, 6) + "\n§7(คลิกขวาที่ตัวผม > งานสร้าง > ส่งบล็อกจากกระเป๋าเรา หรือวางไว้ในหีบใกล้ๆ)",
                                "§eI ran out of blocks, waiting for you!§r Still missing:" + missingText(false, 6) + "\n§7(right-click me > Building > give blocks, or put them in a chest nearby)");
                        if (th) h.act("wave");
                    }
                    return;
                }
            }
            if (!place(l, w, want)) {
                idx++;
                continue;
            }
            if (usesItem) {
                if (item == Items.FLINT_AND_STEEL) {
                    for (int i = 0; i < h.bag.getContainerSize(); i++)
                        if (h.bag.getItem(i).is(Items.FLINT_AND_STEEL)) {
                            h.bag.getItem(i).hurtAndBreak(1, h, e -> {});
                            break;
                        }
                } else Bag.take(h, item, 1);
            }
            idx++;
            placed++;
            acc -= 1;
            b.mover().look(BlockPos.containing(cc));
            h.setWorking(true);
            if (placed % 4 == 0) {
                h.swing(InteractionHand.MAIN_HAND);
                h.act("place");
            }
            l.playSound(null, w, want.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7f, 0.9f + h.getRandom().nextFloat() * 0.2f);
            if (placed % 6 == 0 && l instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, cc.x, cc.y, cc.z, 1, 0.2, 0.2, 0.2, 0);
        }
        if (acc > 1) acc = 1;
    }

    /** Puts one block in the world with its connections (fences, panes, stairs) and both halves of doors and beds. */
    boolean place(Level l, BlockPos w, BlockState want) {
        Block bl = want.getBlock();
        if (bl == Blocks.NETHER_PORTAL) {
            if (l.getBlockState(w).is(Blocks.NETHER_PORTAL)) return true;
            var shape = PortalShape.findEmptyPortalShape(l, w, want.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS));
            if (shape.isEmpty()) return false;
            shape.get().createPortalBlocks();
            l.playSound(null, w, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f, 1f);
            return true;
        }
        if (bl == Blocks.WATER) {
            l.setBlock(w, Blocks.WATER.defaultBlockState(), 3);
            return true;
        }
        if (BlockMap.twoPart(want)) {
            BlockPos o = BlockMap.otherHalf(want, w);
            BlockState os = BlockMap.otherHalfState(want);
            if (o == null || os == null || !canReplace(l, o, l.getBlockState(o))) return false;
            l.setBlock(o, os, 2 | 16);
            l.setBlock(w, want, 3);
            l.blockUpdated(o, os.getBlock());
            return true;
        }
        BlockState s = Block.updateFromNeighbourShapes(want, l, w);
        if (s.isAir()) return false; // cannot stand there (a flower without ground, a lantern without a ceiling)
        l.setBlock(w, s, 3);
        return true;
    }

    void finish() {
        Level l = h.level();
        BlockPos door = toWorld((int) Math.floor(plan.doorX), 1, 0);
        l.playSound(null, h.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, 1f);
        if (l instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, door.getX() + 0.5, door.getY() + 2, door.getZ() + 0.5, 30, 3, 2.5, 3, 0);
        String n = name(true), ne = name(false);
        Say.owner(h, "§a§lสร้าง" + n + "เสร็จแล้ว!§r ขอให้มีความสุขกับที่ใหม่นะครับ", "§a§lThe " + ne + " is finished!§r Enjoy your new place");
        int size = opts != null && opts.size != null ? opts.size : 1;
        int floors = opts != null && opts.floors != null ? opts.floors : 1;
        h.addXp((size == 0 ? 30 : size == 2 ? 80 : 50) + 10 * (floors - 1));
        h.act("cheer");
        b.report("build", null);
        cancel();
        h.setMode(b.plan.active() ? Mode.STAY : Mode.FOLLOW);
    }

    // ------------------------------------------------------------------ save
    public static CompoundTag saveOpts(Gen.Opts o) {
        CompoundTag t = new CompoundTag();
        if (o.type != null) t.putString("type", o.type);
        Object[][] ints = {{"style", o.style}, {"size", o.size}, {"floors", o.floors}, {"wood", o.wood}, {"roof", o.roof}, {"mat", o.mat}, {"variant", o.variant},
                {"model", o.model}, {"w", o.w}, {"d", o.d}, {"height", o.height}};
        for (Object[] e : ints) if (e[1] != null) t.putInt((String) e[0], (Integer) e[1]);
        Object[][] bools = {{"light", o.light}, {"furniture", o.furniture}, {"porch", o.porch}, {"chimney", o.chimney}, {"pool", o.pool}, {"floorsExact", o.floorsExact}};
        for (Object[] e : bools) if (e[1] != null) t.putBoolean((String) e[0], (Boolean) e[1]);
        return t;
    }

    public static Gen.Opts loadOpts(CompoundTag t) {
        Gen.Opts o = new Gen.Opts();
        o.type = t.contains("type") ? t.getString("type") : null;
        o.style = t.contains("style") ? t.getInt("style") : null;
        o.size = t.contains("size") ? t.getInt("size") : null;
        o.floors = t.contains("floors") ? t.getInt("floors") : null;
        o.wood = t.contains("wood") ? t.getInt("wood") : null;
        o.roof = t.contains("roof") ? t.getInt("roof") : null;
        o.mat = t.contains("mat") ? t.getInt("mat") : null;
        o.variant = t.contains("variant") ? t.getInt("variant") : null;
        o.model = t.contains("model") ? t.getInt("model") : null;
        o.w = t.contains("w") ? t.getInt("w") : null;
        o.d = t.contains("d") ? t.getInt("d") : null;
        o.height = t.contains("height") ? t.getInt("height") : null;
        o.light = t.contains("light") ? t.getBoolean("light") : null;
        o.furniture = t.contains("furniture") ? t.getBoolean("furniture") : null;
        o.porch = t.contains("porch") ? t.getBoolean("porch") : null;
        o.chimney = t.contains("chimney") ? t.getBoolean("chimney") : null;
        o.pool = t.contains("pool") ? t.getBoolean("pool") : null;
        o.floorsExact = t.contains("floorsExact") ? t.getBoolean("floorsExact") : null;
        return o;
    }

    void save(CompoundTag jobs) {
        if (lastOpts != null) jobs.put("lastHouse", saveOpts(lastOpts));
        if (!active()) {
            jobs.remove("build");
            return;
        }
        CompoundTag t = savePrepared(new Prepared(opts, custom, customName, origin, facing, dim, fill, speed));
        t.putBoolean("clearing", clearing);
        t.putInt("idx", idx);
        t.putInt("placed", placed);
        jobs.put("build", t);
    }

    void load(CompoundTag jobs) {
        lastOpts = jobs.contains("lastHouse") ? loadOpts(jobs.getCompound("lastHouse")) : null;
        if (!jobs.contains("build")) {
            opts = null;
            custom = null;
            return;
        }
        CompoundTag t = jobs.getCompound("build");
        Prepared pr = loadPrepared(t);
        opts = pr.opts();
        custom = pr.custom();
        if (opts == null && custom == null) return;
        customName = pr.name();
        origin = pr.origin();
        facing = pr.facing();
        dim = pr.dim();
        speed = pr.speed();
        fill.clear();
        fill.addAll(pr.fill());
        clearing = t.getBoolean("clearing");
        idx = t.getInt("idx");
        placed = t.getInt("placed");
        plan = null;
    }
}
