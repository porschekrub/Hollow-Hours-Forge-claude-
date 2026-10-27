package com.helperbuddy.brain;

import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.entity.Settings;
import com.helperbuddy.talk.Nlu;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mining ores, chopping trees and collecting blocks on its own: it looks around (up to the search radius, 100 blocks by
 * default), walks or digs its way there, works, replants, picks up what fell, and explores around its work spot.
 */
public final class WorkJob {
    static final int BURIED_RADIUS = 10;
    static final double REACH = 4.3;
    static final double TREE_REACH = 13;

    final Brain b;
    final HelperEntity h;
    final Reach reach;

    // ---- current task
    @Nullable
    BlockPos target;
    boolean chopping, treeWork;
    List<BlockPos> tree = new ArrayList<>();
    List<BlockPos> treeBase = new ArrayList<>();
    @Nullable
    BlockState treeLog;

    // ---- orders
    /** Blocks to collect in GATHER mode. */
    final Set<Block> gather = new LinkedHashSet<>();
    /** Only these ore groups ("mine diamonds"); empty = the settings decide. */
    final List<String> oreOnly = new ArrayList<>();

    long nextScan, lastFarScan, lastPickup;
    int roamFails;
    @Nullable
    Vec3 roam;
    long roamUntil;
    final Map<Long, Long> blacklist = new HashMap<>();
    final Map<Long, Bag.Kind> needBl = new HashMap<>();
    record Replant(BlockPos pos, Item sapling, long until) {}
    final List<Replant> replant = new ArrayList<>();

    WorkJob(Brain b) {
        this.b = b;
        this.h = b.h;
        this.reach = new Reach(b);
    }

    // ------------------------------------------------------------------ orders
    public void setGather(String[] ids) {
        gather.clear();
        if (ids != null) for (String id : ids) {
            Block bl = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.contains(":") ? id : "minecraft:" + id));
            if (bl != Blocks.AIR) gather.add(bl);
            if (bl == Blocks.STONE) gather.add(Blocks.COBBLESTONE);
        }
    }

    public void setOreOnly(List<String> groups) {
        oreOnly.clear();
        if (groups != null) oreOnly.addAll(groups);
    }

    public Set<Block> gatherBlocks() {
        return gather;
    }

    /** Items the current gather order is collecting (never thrown away as rubble). */
    boolean wantsItem(ItemStack st) {
        if (st.getItem() instanceof BlockItem bi && gather.contains(bi.getBlock())) return true;
        for (Block g : gather) if (g == Blocks.STONE && st.is(Items.COBBLESTONE)) return true;
        return false;
    }

    void end() {
        target = null;
        tree.clear();
        treeBase.clear();
        treeWork = false;
        roam = null;
        b.breaker.cancel();
        h.setWorking(false);
    }

    // ------------------------------------------------------------------ blacklist
    void blacklist(BlockPos p, int ticks) {
        blacklist.put(p.asLong(), h.level().getGameTime() + ticks);
        if (blacklist.size() > 4000) blacklist.clear();
    }

    boolean blacklisted(BlockPos p) {
        Bag.Kind need = needBl.get(p.asLong());
        if (need != null) {
            if (!Bag.hasKind(h, need)) return true;
            needBl.remove(p.asLong());
        }
        Long until = blacklist.get(p.asLong());
        if (until == null) return false;
        if (h.level().getGameTime() > until) {
            blacklist.remove(p.asLong());
            return false;
        }
        return true;
    }

    void fail() {
        if (target != null) {
            blacklist(target, 20 * 45);
            if (chopping) for (BlockPos l : tree) blacklist(l, 20 * 90);
        }
        end();
    }

    // ------------------------------------------------------------------ main
    void tick(Mode mode) {
        if (h.home() == null) h.setHome(h.blockPosition());
        long now = h.level().getGameTime();
        if (b.breaker.busy()) {
            b.breaker.tick();
            return;
        }
        if (target != null) {
            if (chopping) tickChop();
            else tickMine(mode);
            if (target == null) roamFails = 0;
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
        if (now - lastPickup >= 20) {
            lastPickup = now;
            if (h.settings.get("pickup")) pickup(mode == Mode.CHOP ? WorkJob::isTreeLoot : s -> wantsItem(s) || isTreeLoot(s), 9);
            tryReplant();
        }
        if (now >= nextScan) {
            nextScan = now + 20;
            boolean found = switch (mode) {
                case MINE -> findOre();
                case CHOP -> findTree();
                case GATHER -> findGather();
                default -> false;
            };
            if (found) {
                roam = null;
                b.mover().resetProgress();
                return;
            }
            if (mode == Mode.GATHER && gather.isEmpty()) {
                h.setMode(Mode.STAY);
                return;
            }
        }
        if (target == null && h.mode() == mode) roamTick(mode);
    }

    // ------------------------------------------------------------------ scanning (near first, the full radius every few seconds)
    @Nullable
    BlockPos scan(java.util.function.Predicate<BlockState> want, java.util.function.BiPredicate<BlockPos, BlockState> ok) {
        ServerLevel sl = b.level();
        BlockPos c = BlockPos.containing(h.getEyePosition());
        BlockPos near = Scan.nearest(sl, c, 24, 24, want, ok);
        if (near != null) return near;
        long now = sl.getGameTime();
        if (now - lastFarScan < 100) return null;
        lastFarScan = now;
        int r = Brain.radius();
        return Scan.nearest(sl, c, r, Math.min(r, 64), want, ok);
    }

    boolean findOre() {
        if (!Bag.equip(h, Bag.Kind.PICKAXE)) {
            b.mover().stop();
            Say.notify(h, "notool:pickaxe", 20 * 30, "§eยังไม่มีอีเต้อ (Pickaxe)!§r ส่งอีเต้อให้ผมหน่อย (คลิกขวาที่ตัวผมขณะถืออีเต้อ หรือใช้หนังสือสั่งงาน)",
                    "§eI have no pickaxe!§r Please give me one (right-click me holding it, or use the command book)");
            return false;
        }
        Settings s = h.settings;
        Level l = h.level();
        Vec3 eye = h.getEyePosition();
        boolean dig = s.get("dig");
        final String[] tooHard = {null};
        BlockPos p = scan(st -> {
            String g = Jobs.oreGroup(st);
            return g != null && (oreOnly.isEmpty() ? s.ore(g) : oreOnly.contains(g));
        }, (pos, st) -> {
            if (blacklisted(pos)) return false;
            if (!Bag.canHarvest(bestPick(), st)) {
                tooHard[0] = Jobs.oreGroup(st);
                return false;
            }
            if (Jobs.exposed(l, pos)) return true;
            return dig && eye.distanceTo(Vec3.atCenterOf(pos)) <= BURIED_RADIUS && !Jobs.touchesLava(l, pos);
        });
        if (p == null) {
            if (tooHard[0] != null)
                Say.notify(h, "tier:" + tooHard[0], 20 * 60, "เจอ" + Nlu.oreName(tooHard[0], true) + " แต่อีเต้อที่มีขุดไม่ได้ ต้องใช้อีเต้อที่ดีกว่านี้",
                        "I found " + Nlu.oreName(tooHard[0], false) + " but my pickaxe is too weak for it");
            return false;
        }
        start(p, false);
        return true;
    }

    ItemStack bestPick() {
        ItemStack best = Bag.isKind(h.tool(), Bag.Kind.PICKAXE) ? h.tool() : ItemStack.EMPTY;
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (Bag.isKind(s, Bag.Kind.PICKAXE) && Bag.tierScore(s) > Bag.tierScore(best)) best = s;
        }
        return best;
    }

    /** Plants that regrow from their lowest block: it leaves that one. */
    static boolean keepsBase(Block bl) {
        return bl == Blocks.BAMBOO || bl == Blocks.SUGAR_CANE || bl == Blocks.CACTUS || bl == Blocks.KELP_PLANT || bl == Blocks.KELP;
    }

    boolean findGather() {
        if (gather.isEmpty()) return false;
        Level l = h.level();
        Vec3 eye = h.getEyePosition();
        boolean dig = h.settings.get("dig");
        BlockPos p = scan(st -> gather.contains(st.getBlock()), (pos, st) -> {
            if (blacklisted(pos) || reach.underPlayer(pos) || Jobs.touchesLava(l, pos)) return false;
            Block bl = st.getBlock();
            if (keepsBase(bl)) {
                // the second block of the stalk: everything above comes down with it, the root stays
                BlockState below = l.getBlockState(pos.below());
                if (!isSamePlant(below, bl)) return false;
                if (isSamePlant(l.getBlockState(pos.below(2)), bl)) return false;
                return true;
            }
            if (st.requiresCorrectToolForDrops() && !hasToolFor(st)) return false;
            if (Jobs.exposed(l, pos)) return true;
            return dig && eye.distanceTo(Vec3.atCenterOf(pos)) <= 6;
        });
        if (p == null) {
            // only wrong tools around?
            for (Block g : gather) {
                BlockState st = g.defaultBlockState();
                if (st.requiresCorrectToolForDrops() && !hasToolFor(st)) {
                    Bag.Kind k = Jobs.toolFor(st);
                    if (k != null) Reach.needTool(h, k);
                    break;
                }
            }
            return false;
        }
        start(p, false);
        return true;
    }

    static boolean isSamePlant(BlockState s, Block bl) {
        if (bl == Blocks.KELP || bl == Blocks.KELP_PLANT) return s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT);
        return s.is(bl);
    }

    boolean hasToolFor(BlockState st) {
        if (Bag.canHarvest(h.tool(), st)) return true;
        for (int i = 0; i < h.bag.getContainerSize(); i++) if (Bag.canHarvest(h.bag.getItem(i), st)) return true;
        return false;
    }

    boolean findTree() {
        boolean axe = Bag.equip(h, Bag.Kind.AXE);
        if (!axe)
            Say.notify(h, "notool:axe", 20 * 120, "ยังไม่มีขวาน เลยตัดด้วยมือไปก่อนนะ (ช้าหน่อย) ส่งขวานมาให้ผมจะเร็วขึ้นมาก",
                    "I have no axe, so I'll chop by hand for now (slow). Give me an axe to go much faster");
        for (int attempt = 0; attempt < 5; attempt++) {
            BlockPos found = scan(Jobs::isLog, (pos, st) -> !blacklisted(pos));
            if (found == null) return false;
            Trees.Tree t = Trees.analyze(h.level(), found);
            if (!t.natural() || t.logs().isEmpty()) {
                for (BlockPos l : t.logs()) blacklist(l, 20 * 300);
                blacklist(found, 20 * 300);
                continue;
            }
            List<BlockPos> base = new ArrayList<>(t.base());
            base.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(h.position())));
            target = base.get(0);
            chopping = true;
            treeWork = false;
            tree = new ArrayList<>(t.logs());
            treeBase = new ArrayList<>(t.base());
            treeLog = t.logState();
            reach.reset(target);
            return true;
        }
        return false;
    }

    void start(BlockPos p, boolean chop) {
        target = p.immutable();
        chopping = chop;
        reach.reset(target);
    }

    // ------------------------------------------------------------------ mining / gathering one block
    void tickMine(Mode mode) {
        Level l = h.level();
        BlockState st = l.getBlockState(target);
        boolean still = mode == Mode.GATHER ? gather.contains(st.getBlock()) : Jobs.isOre(st);
        if (!still) {
            end();
            nextScan = 0; // next block of the vein right away
            return;
        }
        Reach.St r = reach.tick(target, REACH, false);
        if (r == Reach.St.FAIL) {
            fail();
            return;
        }
        if (r != Reach.St.READY) return;
        boolean hand = !st.requiresCorrectToolForDrops();
        BlockPos p = target;
        if (!b.breaker.start(p, hand, s -> {
            nextScan = 0;
        })) {
            Bag.Kind k = Jobs.toolFor(st);
            if (k != null) {
                Reach.needTool(h, k);
                needBl.put(p.asLong(), k);
            }
            fail();
            return;
        }
        // the breaker reports when it is done; the task ends when the block is gone
        target = null;
    }

    // ------------------------------------------------------------------ chopping a whole tree
    void tickChop() {
        Level l = h.level();
        if (!treeWork) {
            Reach.St r = reach.tick(target, 3.2, true);
            if (r == Reach.St.FAIL) {
                fail();
                return;
            }
            if (r != Reach.St.READY) return;
            treeWork = true;
            b.mover().stop();
        }
        Vec3 eye = h.getEyePosition();
        tree.removeIf(p -> !Jobs.isLog(l.getBlockState(p)));
        if (tree.isEmpty()) {
            afterTree();
            return;
        }
        tree.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p -> eye.distanceToSqr(Vec3.atCenterOf(p))));
        BlockPos next = null;
        for (BlockPos p : tree) if (eye.distanceTo(Vec3.atCenterOf(p)) <= TREE_REACH) {
            next = p;
            break;
        }
        if (next == null) {
            afterTree();
            return;
        }
        Bag.equip(h, Bag.Kind.AXE);
        if (!b.breaker.start(next, true, null)) {
            blacklist(next, 20 * 120);
            tree.remove(next);
        }
    }

    static Item saplingFor(BlockState log) {
        Block bl = log.getBlock();
        if (bl == Blocks.OAK_LOG || bl == Blocks.OAK_WOOD) return Items.OAK_SAPLING;
        if (bl == Blocks.SPRUCE_LOG || bl == Blocks.SPRUCE_WOOD) return Items.SPRUCE_SAPLING;
        if (bl == Blocks.BIRCH_LOG || bl == Blocks.BIRCH_WOOD) return Items.BIRCH_SAPLING;
        if (bl == Blocks.JUNGLE_LOG || bl == Blocks.JUNGLE_WOOD) return Items.JUNGLE_SAPLING;
        if (bl == Blocks.ACACIA_LOG || bl == Blocks.ACACIA_WOOD) return Items.ACACIA_SAPLING;
        if (bl == Blocks.DARK_OAK_LOG || bl == Blocks.DARK_OAK_WOOD) return Items.DARK_OAK_SAPLING;
        if (bl == Blocks.CHERRY_LOG || bl == Blocks.CHERRY_WOOD) return Items.CHERRY_SAPLING;
        if (bl == Blocks.MANGROVE_LOG || bl == Blocks.MANGROVE_WOOD) return Items.MANGROVE_PROPAGULE;
        if (bl == Blocks.CRIMSON_STEM) return Items.CRIMSON_FUNGUS;
        if (bl == Blocks.WARPED_STEM) return Items.WARPED_FUNGUS;
        return null;
    }

    void afterTree() {
        if (h.settings.get("replant") && treeLog != null) {
            Item sap = saplingFor(treeLog);
            if (sap != null) {
                long until = h.level().getGameTime() + 20 * 180;
                for (BlockPos p : treeBase) replant.add(new Replant(p, sap, until));
            }
        }
        b.report("tree", treeLog);
        end();
        lastPickup = 0;
        nextScan = h.level().getGameTime() + 15; // let the saplings fall first
    }

    void tryReplant() {
        if (replant.isEmpty()) return;
        Level l = h.level();
        long now = l.getGameTime();
        replant.removeIf(r -> r.until < now);
        Map<Item, List<Replant>> by = new HashMap<>();
        for (Replant r : replant) by.computeIfAbsent(r.sapling, k -> new ArrayList<>()).add(r);
        List<Replant> keep = new ArrayList<>();
        for (Map.Entry<Item, List<Replant>> e : by.entrySet()) {
            Item sap = e.getKey();
            if (!(sap instanceof BlockItem bi)) continue;
            Block block = bi.getBlock();
            List<Replant> valid = new ArrayList<>();
            for (Replant r : e.getValue()) {
                BlockState below = l.getBlockState(r.pos.below());
                if (Jobs.isAirish(l, r.pos) && block.defaultBlockState().canSurvive(l, r.pos) && r.pos.distToCenterSqr(h.position()) < 14 * 14
                        && !below.isAir()) valid.add(r);
            }
            if (valid.isEmpty()) continue;
            boolean big = sap == Items.DARK_OAK_SAPLING;
            int have = Bag.count(h, sap);
            boolean needAll = big && valid.size() >= 4;
            if (have <= 0 || needAll && have < valid.size()) {
                keep.addAll(valid);
                continue;
            }
            int n = needAll ? valid.size() : big ? 0 : Math.min(have, valid.size());
            for (int i = 0; i < n; i++) {
                BlockPos p = valid.get(i).pos;
                l.setBlock(p, block.defaultBlockState(), 3);
                Bag.take(h, sap, 1);
                l.playSound(null, p, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8f, 1f);
                h.act("place");
            }
            for (int i = n; i < valid.size(); i++) keep.add(valid.get(i));
        }
        replant.clear();
        replant.addAll(keep);
    }

    // ------------------------------------------------------------------ picking things up
    static boolean isTreeLoot(ItemStack s) {
        return s.is(ItemTags.SAPLINGS) || s.is(Items.STICK) || s.is(Items.APPLE) || s.is(ItemTags.LOGS) || s.is(Items.MANGROVE_PROPAGULE)
                || s.is(Items.CRIMSON_FUNGUS) || s.is(Items.WARPED_FUNGUS);
    }

    /** Picks up dropped items nearby that match `want`. */
    public int pickup(java.util.function.Predicate<ItemStack> want, double radius) {
        int picked = 0;
        for (ItemEntity e : h.level().getEntitiesOfClass(ItemEntity.class, h.getBoundingBox().inflate(radius, 4, radius))) {
            if (!e.isAlive() || e.hasPickUpDelay() && e.getAge() < 10) continue;
            ItemStack st = e.getItem();
            if (st.isEmpty() || !want.test(st)) continue;
            int before = st.getCount();
            ItemStack left = Bag.add(h, st);
            int got = before - left.getCount();
            if (got <= 0) continue;
            b.report("item", st.copyWithCount(got));
            picked += got;
            if (left.isEmpty()) e.discard();
            else e.setItem(left);
        }
        if (picked > 0) h.level().playSound(null, h.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4f, 1.4f);
        return picked;
    }

    // ------------------------------------------------------------------ exploring
    void roamTick(Mode mode) {
        long now = h.level().getGameTime();
        BlockPos home = h.home() != null ? h.home() : h.blockPosition();
        int radius = Brain.radius();
        if (roam == null || now > roamUntil) {
            if (roam != null) roamFails++;
            if (roamFails >= 6) {
                switch (mode) {
                    case MINE -> Say.notify(h, "roam:mine", 20 * 120, "แถวนี้ไม่เหลือแร่ที่ผมมองเห็นแล้ว ลองพาผมไปที่ถ้ำหรือใต้ดินแล้วสั่งใหม่ หรือสั่ง 'สร้างเหมือง' ก็ได้นะ",
                            "No ores I can see around here. Take me to a cave and ask again, or tell me to 'build a mine'");
                    case CHOP -> Say.notify(h, "roam:chop", 20 * 120, "แถวนี้ไม่เหลือต้นไม้ธรรมชาติแล้ว ลองพาผมไปที่ป่าแล้วสั่งตัดไม้ใหม่นะ",
                            "No natural trees left around here. Take me to a forest and ask again");
                    case FOOD -> Say.notify(h, "roam:food", 20 * 120, "แถวนี้ไม่มีสัตว์หรือพืชผักที่สุกให้หาอาหารแล้ว ลองพาผมไปที่อื่นนะ",
                            "No animals or ripe crops left around here. Take me somewhere else");
                    default -> Say.notify(h, "roam:gather", 20 * 120, "แถวนี้ไม่เหลือบล็อกที่ต้องการแล้ว ลองพาผมไปที่อื่นแล้วสั่งใหม่นะ",
                            "Nothing like that left around here. Take me somewhere else and ask again");
                }
            }
            roam = null;
            for (int i = 0; i < 8; i++) {
                double a = h.getRandom().nextDouble() * Math.PI * 2, len = 10 + h.getRandom().nextDouble() * 10;
                Vec3 p = new Vec3(h.getX() + Math.cos(a) * len, h.getY(), h.getZ() + Math.sin(a) * len);
                if (Math.hypot(p.x - home.getX(), p.z - home.getZ()) <= radius) {
                    roam = p;
                    break;
                }
            }
            if (roam == null) roam = new Vec3(home.getX() + (h.getRandom().nextDouble() - 0.5) * 16, home.getY(), home.getZ() + (h.getRandom().nextDouble() - 0.5) * 16);
            BlockPos land = Mover.safeSpotNear(h.level(), BlockPos.containing(roam), 2);
            if (land != null) roam = Vec3.atBottomCenterOf(land);
            roamUntil = now + 20 * 15;
            b.mover().resetProgress();
        }
        Mover.Res r = b.mover().moveTo(roam, 1.1, 1.5);
        if (r == Mover.Res.ARRIVED) {
            roam = null;
            nextScan = 0;
            lastFarScan = 0;
        } else if (r == Mover.Res.NO_PATH || b.mover().stuck(60)) roam = null;
    }

    // ------------------------------------------------------------------ save
    void save(CompoundTag jobs) {
        CompoundTag w = new CompoundTag();
        ListTag g = new ListTag();
        for (Block bl : gather) g.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(bl).toString()));
        w.put("gather", g);
        ListTag o = new ListTag();
        for (String s : oreOnly) o.add(StringTag.valueOf(s));
        w.put("ores", o);
        jobs.put("work", w);
    }

    void load(CompoundTag jobs) {
        CompoundTag w = jobs.getCompound("work");
        gather.clear();
        for (Tag t : w.getList("gather", Tag.TAG_STRING)) {
            Block bl = BuiltInRegistries.BLOCK.get(new ResourceLocation(t.getAsString()));
            if (bl != Blocks.AIR) gather.add(bl);
        }
        oreOnly.clear();
        for (Tag t : w.getList("ores", Tag.TAG_STRING)) oreOnly.add(t.getAsString());
    }
}
