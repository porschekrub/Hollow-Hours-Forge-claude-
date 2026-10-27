package com.helperbuddy.brain;

import com.helperbuddy.entity.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finding food: hunting farm animals (it does not need to leave any for breeding), harvesting ripe crops and berries
 * and planting them again, picking up what drops.
 */
public final class FoodJob {
    final Brain b;
    final HelperEntity h;
    final Reach reach;
    boolean hunt = true, crops = true;
    /** Only this animal ("cow", "pig" ...), or null. */
    @Nullable
    String animal;
    @Nullable
    BlockPos crop;
    long nextScan, lastPick, lastFarScan;
    final Map<Long, Long> cropBl = new HashMap<>();

    FoodJob(Brain b) {
        this.b = b;
        this.h = b.h;
        this.reach = new Reach(b);
    }

    public void setOptions(boolean hunt, boolean crops, @Nullable String animal) {
        this.hunt = hunt;
        this.crops = crops;
        this.animal = animal;
    }

    public String describe(boolean th) {
        if (animal != null) return th ? "ล่า" + animalName(animal, true) : "hunting " + animalName(animal, false);
        if (hunt && crops) return th ? "ล่าสัตว์และเก็บผักผลไม้" : "hunting and harvesting";
        if (hunt) return th ? "ล่าสัตว์" : "hunting";
        return th ? "เก็บผักผลไม้ที่สุกแล้ว (ปลูกคืนให้ด้วย)" : "harvesting ripe crops (and replanting)";
    }

    static String animalName(String a, boolean th) {
        return switch (a) {
            case "cow" -> th ? "วัว" : "cows";
            case "pig" -> th ? "หมู" : "pigs";
            case "chicken" -> th ? "ไก่" : "chickens";
            case "sheep" -> th ? "แกะ" : "sheep";
            case "rabbit" -> th ? "กระต่าย" : "rabbits";
            default -> a;
        };
    }

    void end() {
        crop = null;
        b.combat().dropHunt();
    }

    // ------------------------------------------------------------------ who is food
    static boolean isFoodAnimal(LivingEntity e) {
        return e instanceof Cow || e instanceof Pig || e instanceof Chicken || e instanceof Sheep || e instanceof Rabbit;
    }

    static boolean matches(LivingEntity e, @Nullable String only) {
        if (only == null) return true;
        return switch (only) {
            case "cow" -> e instanceof Cow; // mooshrooms are cows too
            case "pig" -> e instanceof Pig;
            case "chicken" -> e instanceof Chicken;
            case "sheep" -> e instanceof Sheep;
            case "rabbit" -> e instanceof Rabbit;
            default -> e.getType() == EntityType.byString(only.contains(":") ? only : "minecraft:" + only).orElse(null);
        };
    }

    /** Grown-up, unnamed, free animals only: babies, pets and animals on a leash are never hunted. */
    static boolean huntable(LivingEntity e) {
        if (!e.isAlive() || !isFoodAnimal(e) || e.isBaby() || e.hasCustomName()) return false;
        if (e instanceof TamableAnimal t && t.isTame()) return false;
        if (e instanceof Animal a && a.isLeashed()) return false;
        return true;
    }

    @Nullable
    LivingEntity findAnimal(@Nullable String only) {
        int r = Brain.radius();
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : h.level().getEntitiesOfClass(LivingEntity.class, new AABB(h.blockPosition()).inflate(r, 24, r), e -> huntable(e) && matches(e, only))) {
            double d = e.distanceToSqr(h);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ crops
    static boolean ripe(BlockState s) {
        Block bl = s.getBlock();
        if (bl instanceof CropBlock c) return c.isMaxAge(s);
        if (bl instanceof SweetBerryBushBlock) return s.getValue(SweetBerryBushBlock.AGE) >= 2;
        if (bl instanceof CocoaBlock) return s.getValue(CocoaBlock.AGE) >= 2;
        return false;
    }

    static boolean isCrop(BlockState s) {
        Block bl = s.getBlock();
        return bl instanceof CropBlock || bl instanceof SweetBerryBushBlock || bl instanceof CocoaBlock;
    }

    @Nullable
    BlockPos findCrop() {
        ServerLevel sl = b.level();
        long now = sl.getGameTime();
        java.util.function.BiPredicate<BlockPos, BlockState> ok = (p, s) -> {
            Long until = cropBl.get(p.asLong());
            return (until == null || now > until) && ripe(s);
        };
        BlockPos near = Scan.nearest(sl, h.blockPosition(), 24, 12, FoodJob::isCrop, ok);
        if (near != null || now - lastFarScan < 100) return near;
        lastFarScan = now;
        return Scan.nearest(sl, h.blockPosition(), Brain.radius(), 32, FoodJob::isCrop, ok);
    }

    /** Harvests a ripe crop with its real drops, and plants it again with one of the seeds. */
    public boolean harvest(BlockPos p) {
        Level l = h.level();
        if (!(l instanceof ServerLevel sl)) return false;
        BlockState s = l.getBlockState(p);
        if (!isCrop(s) || !ripe(s)) return false;
        Block bl = s.getBlock();
        List<ItemStack> drops = Block.getDrops(s, sl, p, null, h, ItemStack.EMPTY);
        BlockState after;
        if (bl instanceof SweetBerryBushBlock) after = s.setValue(SweetBerryBushBlock.AGE, 1);
        else if (bl instanceof CocoaBlock) after = s.setValue(CocoaBlock.AGE, 0);
        else {
            CropBlock c = (CropBlock) bl;
            ItemStack seed = bl.getCloneItemStack(l, p, s);
            boolean replant = false;
            if (h.settings.get("replant") && !seed.isEmpty()) {
                for (ItemStack d : drops)
                    if (d.is(seed.getItem()) && d.getCount() > 0) {
                        d.shrink(1);
                        replant = true;
                        break;
                    }
                if (!replant && Bag.take(h, seed.getItem(), 1) > 0) replant = true;
            }
            after = replant ? c.getStateForAge(0) : Blocks.AIR.defaultBlockState();
        }
        l.setBlock(p, after, 3);
        for (ItemStack d : drops) {
            if (d.isEmpty()) continue;
            Bag.addOrDrop(h, d, net.minecraft.world.phys.Vec3.atCenterOf(p));
            b.report("item", d);
        }
        l.playSound(null, p, bl instanceof SweetBerryBushBlock ? SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES : SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8f, 1f);
        h.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        h.act("swing");
        h.addXp(0.5f);
        b.report("harvest", s);
        return true;
    }

    static boolean isFoodLoot(ItemStack s) {
        return Care.foodValue(s) > 0 || s.is(Items.LEATHER) || s.is(Items.FEATHER) || s.is(Items.RABBIT_HIDE) || s.is(Items.RABBIT_FOOT) || s.is(Items.EGG)
                || s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS) || s.is(Items.WHEAT) || s.is(Items.COCOA_BEANS)
                || s.is(net.minecraft.tags.ItemTags.WOOL);
    }

    // ------------------------------------------------------------------ ticks
    /** FOOD mode: the options from the last order. */
    void tickMode() {
        tick(hunt, crops, animal);
    }

    /** One tick of looking for food (FOOD mode, an order step, or when it went hungry). */
    void tick(boolean hunt, boolean crops, @Nullable String only) {
        long now = h.level().getGameTime();
        if (now - lastPick >= 10) {
            lastPick = now;
            b.work.pickup(FoodJob::isFoodLoot, 8);
        }
        if (b.breaker.busy()) {
            b.breaker.tick();
            return;
        }
        LivingEntity hunted = b.combat().target();
        if (hunted != null && hunted.isAlive() && b.combat().hunting) return; // the combat code walks and attacks
        if (crop != null) {
            if (!ripe(h.level().getBlockState(crop))) {
                crop = null;
                return;
            }
            Reach.St r = reach.tick(crop, 3.0, true);
            if (r == Reach.St.FAIL) {
                cropBl.put(crop.asLong(), now + 20 * 60);
                crop = null;
                return;
            }
            if (r == Reach.St.READY) {
                b.mover().stop();
                b.mover().look(crop);
                harvest(crop);
                crop = null;
                nextScan = now + 4;
            }
            return;
        }
        if (now >= nextScan) {
            nextScan = now + 20;
            if (crops && only == null) {
                BlockPos c = findCrop();
                if (c != null) {
                    crop = c;
                    reach.reset(c);
                    b.work.roam = null;
                    return;
                }
            }
            if (hunt) {
                LivingEntity a = findAnimal(only);
                if (a != null) {
                    b.work.roam = null;
                    b.combat().setTarget(a, false, true);
                    return;
                }
            }
        }
        if (b.combat().target() == null) roamFood();
    }

    void roamFood() {
        b.work.roamTick(h.mode()); // reuses the explorer of the work job
    }

    /** After a hunt: the drops are picked up by the next ticks. */
    void afterKill(LivingEntity t) {
        lastPick = 0;
        b.report("hunt", t);
    }

    // ------------------------------------------------------------------ save
    void save(CompoundTag jobs) {
        CompoundTag f = new CompoundTag();
        f.putBoolean("hunt", hunt);
        f.putBoolean("crops", crops);
        if (animal != null) f.putString("animal", animal);
        jobs.put("food", f);
    }

    void load(CompoundTag jobs) {
        CompoundTag f = jobs.getCompound("food");
        hunt = !f.contains("hunt") || f.getBoolean("hunt");
        crops = !f.contains("crops") || f.getBoolean("crops");
        animal = f.contains("animal") ? f.getString("animal") : null;
    }
}
