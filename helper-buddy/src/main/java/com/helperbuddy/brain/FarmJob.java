package com.helperbuddy.brain;

import com.helperbuddy.compat.Compat;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Farmer: around its spot it harvests ripe crops and plants them again, plants seeds on empty farmland, and with a hoe
 * turns grass and dirt next to water into new farmland (as long as it has seeds to plant).
 */
public final class FarmJob {
    static final int R = 12;
    final Brain b;
    final HelperEntity h;
    final Reach reach;
    @Nullable
    BlockPos target;
    String action = "";
    long nextScan;
    int planted, harvested, tilled;

    FarmJob(Brain b) {
        this.b = b;
        this.h = b.h;
        this.reach = new Reach(b);
    }

    static boolean isSeed(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    int seedSlot() {
        for (int i = 0; i < h.bag.getContainerSize(); i++) if (isSeed(h.bag.getItem(i))) return i;
        return -1;
    }

    static boolean nearWater(Level l, BlockPos p) {
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-4, 0, -4), p.offset(4, 1, 4))) if (l.getFluidState(q).is(FluidTags.WATER)) return true;
        return false;
    }

    public String status(boolean th) {
        return th ? "เก็บ " + harvested + " ปลูก " + planted : "harvested " + harvested + ", planted " + planted;
    }

    void tick() {
        if (h.home() == null) h.setHome(h.blockPosition());
        long now = h.level().getGameTime();
        if (now % 20 == 0) b.work.pickup(s -> isSeed(s) || Care.foodValue(s) > 0 || s.is(Items.WHEAT) || s.is(Items.BEETROOT_SEEDS) || s.is(Items.WHEAT_SEEDS), 8);
        if (b.breaker.busy()) {
            b.breaker.tick();
            return;
        }
        Level l = h.level();
        if (target == null) {
            if (now < nextScan) {
                BlockPos home = h.home();
                if (home.distSqr(h.blockPosition()) > 36) b.mover().moveTo(home, 1.0, 2);
                return;
            }
            nextScan = now + 40;
            find();
            if (target == null) return;
        }
        Reach.St r = reach.tick(target, 3.2, true);
        if (r == Reach.St.FAIL) {
            target = null;
            return;
        }
        if (r != Reach.St.READY) return;
        b.mover().stop();
        b.mover().look(target);
        BlockPos p = target;
        target = null;
        nextScan = now + 4;
        switch (action) {
            case "harvest" -> {
                if (b.food.harvest(p)) harvested++;
            }
            case "plant" -> {
                int slot = seedSlot();
                if (slot < 0 || !l.getBlockState(p).isAir() || !(l.getBlockState(p.below()).getBlock() instanceof FarmBlock)) return;
                ItemStack seed = h.bag.getItem(slot);
                BlockState crop = ((BlockItem) seed.getItem()).getBlock().defaultBlockState();
                l.setBlock(p, crop, 3);
                seed.shrink(1);
                h.bag.setChanged();
                l.playSound(null, p, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8f, 1f);
                h.swing(InteractionHand.MAIN_HAND);
                planted++;
                h.addXp(0.2f);
            }
            case "till" -> {
                BlockState s = l.getBlockState(p);
                if (!(s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.DIRT_PATH)) || !l.getBlockState(p.above()).isAir()) return;
                if (!Bag.equip(h, Bag.Kind.HOE)) return;
                l.setBlock(p, Blocks.FARMLAND.defaultBlockState(), 3);
                Compat.hurtItem(h.tool(), 1, h, net.minecraft.world.InteractionHand.MAIN_HAND);
                l.playSound(null, p, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1f, 1f);
                h.swing(InteractionHand.MAIN_HAND);
                tilled++;
            }
            default -> {
            }
        }
    }

    void find() {
        Level l = h.level();
        BlockPos c = h.home();
        BlockPos best = null;
        String act = "";
        double bd = Double.MAX_VALUE;
        boolean hasSeed = seedSlot() >= 0;
        boolean hoe = Bag.hasKind(h, Bag.Kind.HOE);
        int emptyFarmland = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-R, -3, -R), c.offset(R, 3, R))) {
            BlockState s = l.getBlockState(q);
            double d = q.distSqr(h.blockPosition());
            String a = null;
            if (FoodJob.isCrop(s) && FoodJob.ripe(s)) a = "harvest";
            else if (s.isAir() && l.getBlockState(q.below()).getBlock() instanceof FarmBlock) {
                emptyFarmland++;
                if (hasSeed) a = "plant";
            } else if (hasSeed && hoe && (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT)) && l.getBlockState(q.above()).isAir() && nearWater(l, q)) {
                a = "till";
                d += 64; // new fields last
            }
            if (a == null) continue;
            if (a.equals("harvest")) d -= 32;
            if (d < bd) {
                bd = d;
                best = q.immutable();
                act = a;
            }
        }
        target = best;
        action = act;
        if (best == null && !hasSeed && emptyFarmland > 0)
            Say.notify(h, "farm:seeds", 20 * 120, "ไม่มีเมล็ดพันธุ์ให้ปลูกแล้ว ใส่ §eเมล็ดข้าวสาลี แครอท หรือมันฝรั่ง§r ในกระเป๋าผมนะ",
                    "I have no seeds left. Put §ewheat seeds, carrots or potatoes§r in my bag");
        if (best == null && hasSeed && !hoe && emptyFarmland == 0)
            Say.notify(h, "farm:hoe", 20 * 120, "ถ้ามี§eจอบ (Hoe)§r ผมจะไถดินข้างน้ำทำแปลงใหม่ให้ด้วยนะ", "Give me a §ehoe§r and I'll turn the dirt next to water into new fields");
    }

    void save(CompoundTag jobs) {
        CompoundTag t = new CompoundTag();
        t.putInt("planted", planted);
        t.putInt("harvested", harvested);
        jobs.put("farm", t);
    }

    void load(CompoundTag jobs) {
        CompoundTag t = jobs.getCompound("farm");
        planted = t.getInt("planted");
        harvested = t.getInt("harvested");
    }
}
