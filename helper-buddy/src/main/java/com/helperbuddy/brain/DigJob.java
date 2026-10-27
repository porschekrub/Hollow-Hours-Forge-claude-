package com.helperbuddy.brain;

import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.talk.Nlu;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** "Build a mine": a staircase down to a chosen depth, then a main tunnel with side branches. Ores in the walls are mined too. */
public final class DigJob {
    static final double REACH = 5.2;
    final Brain b;
    final HelperEntity h;

    // ---- the job (saved)
    @Nullable
    BlockPos origin;
    Direction dir = Direction.NORTH;
    int ty, mainLen, branchLen, spacing = 4, i, dug, ores;
    String dim = "";

    // ---- runtime
    record Step(BlockPos from, BlockPos[] cells, BlockPos floor, int seg, String kind, @Nullable BlockPos torch) {}
    List<Step> steps;
    Map<Long, Integer> cellStep;
    final ArrayDeque<BlockPos> vein = new ArrayDeque<>();
    int retry, stepAt = -1, tpCd;

    DigJob(Brain b) {
        this.b = b;
        this.h = b.h;
    }

    public boolean active() {
        return origin != null;
    }

    static int defaultY(Level l) {
        String d = l.dimension().location().getPath();
        if (d.contains("nether")) return 15;
        if (d.contains("end")) return 40;
        return -54;
    }

    /** Starts a new mine at the helper (or at `at`), digging toward `facing`. */
    public void start(Nlu.MineOpts o, Direction facing, @Nullable BlockPos at) {
        Level l = h.level();
        BlockPos org = at != null ? at : h.blockPosition();
        Direction d = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        if (o != null && o.dir != null) d = switch (o.dir) {
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            case "south" -> Direction.SOUTH;
            default -> Direction.NORTH;
        };
        int y = defaultY(l);
        if (o != null && o.flat) y = org.getY();
        else if (o != null && o.y != null) y = o.y;
        else if (o != null && o.depth != null) y = org.getY() - Math.max(1, o.depth);
        y = Math.max(l.getMinBuildHeight() + 5, Math.min(org.getY(), y));
        if (l.dimension().location().getPath().contains("nether")) y = Math.max(6, y);
        origin = org;
        dir = d;
        ty = y;
        mainLen = Math.max(4, Math.min(96, o != null && o.length != null ? o.length : 32));
        branchLen = Math.max(0, Math.min(32, o != null && o.branch != null ? o.branch : 10));
        spacing = branchLen == 0 ? 9999 : 4;
        i = dug = ores = 0;
        dim = l.dimension().location().toString();
        steps = null;
        vein.clear();
        retry = 0;
        stepAt = -1;
    }

    public void cancel() {
        origin = null;
        steps = null;
        vein.clear();
        h.setWorking(false);
    }

    public String describe(boolean th) {
        int depth = origin == null ? 0 : origin.getY() - ty;
        String dTh = switch (dir) {
            case EAST -> "ทิศตะวันออก";
            case WEST -> "ทิศตะวันตก";
            case SOUTH -> "ทิศใต้";
            default -> "ทิศเหนือ";
        };
        if (th) return (depth > 0 ? "บันไดลง " + depth + " บล็อก (ถึงระดับ Y=" + ty + ") " : "ที่ระดับ Y=" + ty + " ") + "อุโมงค์หลักยาว " + mainLen + " บล็อกไป" + dTh
                + (branchLen > 0 ? " แยกกิ่งซ้าย-ขวายาว " + branchLen + " บล็อกทุกๆ " + spacing + " บล็อก" : "");
        return (depth > 0 ? "a staircase " + depth + " blocks down (to Y=" + ty + "), " : "at Y=" + ty + ", ") + "a main tunnel " + mainLen + " blocks "
                + dir.getName() + (branchLen > 0 ? " with " + branchLen + "-block side branches every " + spacing + " blocks" : "");
    }

    int progress() {
        List<Step> s = plan();
        return s.isEmpty() ? 100 : Math.min(100, i * 100 / s.size());
    }

    public String statusShort() {
        if (!active()) return "";
        return progress() + "%" + (Bag.hasKind(h, Bag.Kind.PICKAXE) ? "" : (Say.thai(h.owner()) ? " ไม่มีอีเต้อ" : " no pickaxe"));
    }

    // ------------------------------------------------------------------ plan
    List<Step> plan() {
        if (steps != null) return steps;
        steps = new ArrayList<>();
        cellStep = new HashMap<>();
        if (origin == null) return steps;
        int bseg = 100;
        BlockPos p = origin;
        int n = 0;
        while (p.getY() > ty) {
            BlockPos nx = p.relative(dir).below();
            n++;
            steps.add(new Step(p, new BlockPos[] {nx.above(2), nx.above(), nx}, nx.below(), 0, "stairs", n % 8 == 0 ? nx : null));
            p = nx;
        }
        Direction left = dir.getCounterClockWise(), right = dir.getClockWise();
        for (int k = 1; k <= mainLen; k++) {
            BlockPos nx = p.relative(dir);
            steps.add(new Step(p, new BlockPos[] {nx.above(), nx}, nx.below(), 1, "main", k % 6 == 1 ? nx : null));
            p = nx;
            if (k % spacing == 0 && k < mainLen) {
                for (Direction side : new Direction[] {left, right}) {
                    bseg++;
                    BlockPos q = p;
                    for (int bb = 1; bb <= branchLen; bb++) {
                        BlockPos nq = q.relative(side);
                        steps.add(new Step(q, new BlockPos[] {nq.above(), nq}, nq.below(), bseg, "branch", bb == branchLen || bb % 7 == 0 ? nq : null));
                        q = nq;
                    }
                }
            }
        }
        for (int s = 0; s < steps.size(); s++) for (BlockPos c : steps.get(s).cells) cellStep.put(c.asLong(), s);
        return steps;
    }

    // ------------------------------------------------------------------ helpers
    static final Item[] FILL = {Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.NETHERRACK, Items.DIRT, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF,
            Items.BLACKSTONE, Items.BASALT, Items.END_STONE, Items.STONE, Items.DEEPSLATE};

    /** A solid block (from the bag when it has one, otherwise cobblestone: safety first). */
    void placeFiller(BlockPos p) {
        BlockState st = Blocks.COBBLESTONE.defaultBlockState();
        for (Item f : FILL)
            if (Bag.count(h, f) > 0 && f instanceof BlockItem bi) {
                Bag.take(h, f, 1);
                st = bi.getBlock().defaultBlockState();
                break;
            }
        h.level().setBlock(p, st, 3);
    }

    /** Stops water and lava from pouring into a freshly opened block. */
    void seal(BlockPos p) {
        Level l = h.level();
        for (Direction d : Direction.values()) {
            BlockPos q = p.relative(d);
            var fs = l.getFluidState(q);
            if (fs.isEmpty()) continue;
            Integer si = cellStep.get(q.asLong());
            if (si != null && si < i && !fs.is(net.minecraft.tags.FluidTags.LAVA)) continue; // water in our own tunnel is harmless
            placeFiller(q);
        }
    }

    void blockedSegment(String th, String en) {
        List<Step> s = plan();
        Step st = s.get(i);
        if (st.kind.equals("stairs")) {
            ty = st.from.getY();
            steps = null;
            Say.notify(h, "mine:deep", 200, "ลงลึกต่อไม่ได้ (เจอ " + th + ") เลยขุดอุโมงค์ที่ระดับ Y=" + ty + " แทนนะ", "Can't go deeper (" + en + "), so I'll dig the tunnel at Y=" + ty);
            return;
        }
        if (st.kind.equals("main")) {
            Say.notify(h, "mine:main", 200, "อุโมงค์หลักไปต่อไม่ได้ (เจอ " + th + ") งานสร้างเหมืองจบแค่นี้", "The main tunnel is blocked (" + en + "), so the mine ends here");
            i = s.size();
            return;
        }
        int seg = st.seg;
        while (i < s.size() && s.get(i).seg == seg) i++;
    }

    boolean goTo(BlockPos p) {
        Vec3 goal = Vec3.atBottomCenterOf(p);
        double dxz = Math.hypot(h.getX() - goal.x, h.getZ() - goal.z);
        double dy = Math.abs(h.getY() - p.getY());
        if (dxz <= 0.7 && dy <= 1.2) {
            b.mover().stop();
            return true;
        }
        if (tpCd > 0) tpCd--;
        if (dxz > 12 || dy > 3) {
            tp(goal);
            return false;
        }
        Mover.Res r = b.mover().moveTo(goal, 1.0, 0.45);
        if (r == Mover.Res.NO_PATH || b.mover().stuck(30)) tp(goal);
        return false;
    }

    void tp(Vec3 goal) {
        if (tpCd > 0) return;
        tpCd = 10;
        h.teleportTo(goal.x, goal.y, goal.z);
        h.getNavigation().stop();
        b.mover().resetProgress();
    }

    void finish() {
        Say.owner(h, "§aสร้างเหมืองเสร็จแล้ว!§r ขุดไป " + dug + " บล็อก ได้แร่ " + ores + " ก้อน ผมรออยู่ในเหมืองนะ (ทางเข้าอยู่ที่ " + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")",
                "§aThe mine is done!§r I dug " + dug + " blocks and found " + ores + " ores. I'm waiting inside (entrance at " + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")");
        h.addXp(15);
        b.report("dig", null);
        cancel();
        h.setMode(Mode.STAY);
    }

    void placeTorch(BlockPos p) {
        Level l = h.level();
        BlockState torch = Blocks.TORCH.defaultBlockState();
        if (!l.getBlockState(p).isAir() || !torch.canSurvive(l, p)) return;
        if (Bag.count(h, Items.TORCH) <= 0) {
            Say.notify(h, "mine:torch", 20 * 300, "ถ้าใส่ §eคบเพลิง (Torch)§r ไว้ในกระเป๋าผม ผมจะปักไฟในเหมืองให้ด้วยนะ",
                    "Put some §etorches§r in my bag and I'll light up the mine too");
            return;
        }
        l.setBlock(p, torch, 3);
        Bag.take(h, Items.TORCH, 1);
    }

    void afterDug(BlockPos p, BlockState was) {
        dug++;
        if (Jobs.isOre(was)) ores++;
        seal(p);
        for (Direction d : Direction.values()) {
            BlockPos q = p.relative(d);
            if (cellStep.containsKey(q.asLong()) || vein.contains(q)) continue;
            String g = Jobs.oreGroup(h.level().getBlockState(q));
            if (g != null && h.settings.ore(g) && vein.size() < 48) vein.add(q);
        }
    }

    // ------------------------------------------------------------------ tick
    void tick() {
        if (!active()) {
            h.setMode(Mode.STAY);
            return;
        }
        if (!h.level().dimension().location().toString().equals(dim)) {
            b.mover().stop();
            Say.notify(h, "mine:dim", 20 * 60, "เหมืองที่สั่งไว้อยู่คนละมิติกับผม พาผมกลับไปก่อนนะ", "The mine is in another dimension. Take me back there first");
            return;
        }
        if (b.breaker.busy()) {
            b.breaker.tick();
            return;
        }
        h.setWorking(false);
        if (Bag.isFull(h)) {
            b.onBagFull();
            if (Bag.isFull(h)) {
                b.mover().stop();
                return;
            }
        }
        List<Step> s = plan();
        if (i >= s.size()) {
            finish();
            return;
        }
        Step st = s.get(i);
        if (stepAt != i) {
            stepAt = i;
            retry = 0;
        }
        if (!goTo(st.from)) return;
        Level l = h.level();
        Vec3 eye = h.getEyePosition();
        // ---- ores in the walls
        while (!vein.isEmpty()) {
            BlockPos p = vein.peek();
            BlockState bs = l.getBlockState(p);
            if (!Jobs.isOre(bs) || eye.distanceTo(Vec3.atCenterOf(p)) > REACH) {
                vein.poll();
                continue;
            }
            if (!Bag.equip(h, Bag.Kind.PICKAXE) || !Bag.canHarvest(h.tool(), bs)) {
                vein.poll();
                continue;
            }
            vein.poll();
            b.breaker.start(p, false, was -> afterDug(p, was));
            return;
        }
        // ---- the blocks of this step
        for (BlockPos c : st.cells) {
            if (!l.isLoaded(c)) {
                b.mover().stop();
                return;
            }
            BlockState bs = l.getBlockState(c);
            if (!bs.getFluidState().isEmpty() && !bs.blocksMotion()) {
                seal(c);
                placeFiller(c); // turn the liquid into a block and dig it out next
                return;
            }
            if (Mover.passable(l, c) && !bs.is(Blocks.COBWEB)) continue;
            if (!Breaker.breakable(h, c, bs) || l.getBlockEntity(c) != null) {
                blockedSegment(bs.getBlock().getName().getString(), bs.getBlock().getName().getString());
                return;
            }
            if (++retry > 60) {
                blockedSegment("ทรายหรือกรวดที่ไหลลงมาไม่หยุด", "sand or gravel that keeps falling");
                return;
            }
            Bag.Kind k = Jobs.toolFor(bs);
            if (bs.requiresCorrectToolForDrops() && !Bag.equipFor(h, bs)) {
                if (k != null && !Bag.hasKind(h, k)) Reach.needTool(h, k);
                else Say.notify(h, "mine:tier", 20 * 45, "ทางเหมืองมี " + bs.getBlock().getName().getString() + " ขวางอยู่ แต่เครื่องมือของผมขุดไม่ได้ ส่งอันที่ดีกว่านี้ให้หน่อยนะ (ผมรออยู่)",
                        "There's " + bs.getBlock().getName().getString() + " in the way and my tool can't break it. Send me a better one, please (I'll wait)");
                b.mover().stop();
                retry--;
                return;
            }
            b.mover().look(c);
            final BlockPos cell = c;
            if (!b.breaker.start(c, true, was -> afterDug(cell, was))) {
                retry--;
                b.mover().stop();
            }
            return;
        }
        // ---- step done: solid floor, light, next
        BlockState fl = l.getBlockState(st.floor);
        if (Mover.passable(l, st.floor) || !fl.getFluidState().isEmpty()) placeFiller(st.floor);
        if (st.torch != null && h.settings.get("torches")) placeTorch(st.torch);
        i++;
        if (i % 20 == 0) Say.notify(h, "mine:progress", 20 * 60, "สร้างเหมืองไปแล้ว " + progress() + "%", "Mine progress: " + progress() + "%");
    }

    // ------------------------------------------------------------------ save
    void save(CompoundTag jobs) {
        if (origin == null) {
            jobs.remove("mine");
            return;
        }
        CompoundTag m = new CompoundTag();
        m.putLong("o", origin.asLong());
        m.putInt("d", dir.get3DDataValue());
        m.putInt("ty", ty);
        m.putInt("ml", mainLen);
        m.putInt("bl", branchLen);
        m.putInt("sp", spacing);
        m.putInt("i", i);
        m.putInt("dug", dug);
        m.putInt("ores", ores);
        m.putString("dim", dim);
        jobs.put("mine", m);
    }

    void load(CompoundTag jobs) {
        if (!jobs.contains("mine")) {
            origin = null;
            return;
        }
        CompoundTag m = jobs.getCompound("mine");
        origin = BlockPos.of(m.getLong("o"));
        dir = Direction.from3DDataValue(m.getInt("d"));
        ty = m.getInt("ty");
        mainLen = m.getInt("ml");
        branchLen = m.getInt("bl");
        spacing = Math.max(1, m.getInt("sp"));
        i = m.getInt("i");
        dug = m.getInt("dug");
        ores = m.getInt("ores");
        dim = m.getString("dim");
        steps = null;
    }
}
