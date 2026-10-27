package com.helperbuddy.brain;

import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/** Breaks one block at a time like a player would: right tool, real breaking time, real drops (fortune, silk touch). */
public final class Breaker {
    final HelperEntity h;
    @Nullable
    BlockPos pos;
    BlockState state;
    float progress, perTick;
    int lastStage = -1;
    /** Called with the broken block's state when it is done. */
    @Nullable
    Consumer<BlockState> done;

    Breaker(HelperEntity h) {
        this.h = h;
    }

    public boolean busy() {
        return pos != null;
    }

    @Nullable
    public BlockPos target() {
        return pos;
    }

    public void cancel() {
        if (pos != null) h.level().destroyBlockProgress(h.getId(), pos, -1);
        pos = null;
        done = null;
        h.setWorking(false);
    }

    /** Never breaks: bedrock, spawners, portals, other people's containers ... */
    public static boolean breakable(HelperEntity h, BlockPos p, BlockState s) {
        if (s.isAir() || s.getDestroySpeed(h.level(), p) < 0) return false;
        if (s.is(BlockTags.WITHER_IMMUNE) && !s.is(net.minecraft.world.level.block.Blocks.OBSIDIAN) && !s.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN))
            return false;
        if (s.is(net.minecraft.world.level.block.Blocks.SPAWNER) || s.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                || s.is(net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME) || s.is(net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST))
            return false;
        return h.level().getBlockEntity(p) == null || s.is(BlockTags.BEDS) || s.is(BlockTags.SIGNS);
    }

    /**
     * Starts breaking. `byHand` allows breaking without the right tool (slowly, maybe without drops).
     * Returns false when it cannot (missing tool: the caller asks the owner).
     */
    public boolean start(BlockPos p, boolean byHand, @Nullable Consumer<BlockState> onDone) {
        BlockState s = h.level().getBlockState(p);
        if (!breakable(h, p, s)) return false;
        boolean ok = Bag.equipFor(h, s);
        if (!ok && !byHand && s.requiresCorrectToolForDrops()) return false;
        float hard = s.getDestroySpeed(h.level(), p);
        ItemStack t = h.tool();
        float sp = t.isEmpty() ? 1f : Bag.speedOn(t, s);
        boolean harvest = Bag.canHarvest(t, s);
        perTick = hard <= 0 ? 1f : (float) (sp / hard / (harvest ? 30f : 100f) * HBConfig.WORK_SPEED.get() * (1 + 0.1 * (h.helperLevel() - 1)));
        pos = p.immutable();
        state = s;
        progress = 0;
        lastStage = -1;
        done = onDone;
        h.getNavigation().stop();
        h.setWorking(true);
        return true;
    }

    /** One tick; returns true on the tick the block breaks. */
    public boolean tick() {
        if (pos == null) return false;
        BlockState now = h.level().getBlockState(pos);
        if (now.getBlock() != state.getBlock()) {
            cancel();
            return false;
        }
        h.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40f, 40f);
        progress += perTick;
        if (h.tickCount % 5 == 0) {
            h.swing(InteractionHand.MAIN_HAND);
            h.act("swing");
            h.level().playSound(null, pos, state.getSoundType(h.level(), pos, h).getHitSound(), SoundSource.BLOCKS, 0.5f, 0.8f);
        }
        int stage = (int) (progress * 10f);
        if (stage != lastStage) {
            h.level().destroyBlockProgress(h.getId(), pos, Math.min(9, stage));
            lastStage = stage;
        }
        if (progress < 1f) return false;
        BlockPos p = pos;
        BlockState s = state;
        Consumer<BlockState> cb = done;
        pos = null;
        done = null;
        h.level().destroyBlockProgress(h.getId(), p, -1);
        h.setWorking(false);
        harvest(h, p, s, true);
        if (cb != null) cb.accept(s);
        return true;
    }

    /** Breaks the block now, drops go into the bag. Returns false when it could not. */
    public static boolean harvest(HelperEntity h, BlockPos p, BlockState s, boolean useTool) {
        if (!(h.level() instanceof ServerLevel sl)) return false;
        if (!ForgeEventFactory.onEntityDestroyBlock(h, p, s)) return false;
        ItemStack tool = useTool ? h.tool() : ItemStack.EMPTY;
        BlockEntity be = sl.getBlockEntity(p);
        List<ItemStack> drops = Bag.canHarvest(tool, s) ? Block.getDrops(s, sl, p, be, h, tool) : List.of();
        int fortune = tool.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE);
        int silk = tool.getEnchantmentLevel(Enchantments.SILK_TOUCH);
        int xp = Bag.canHarvest(tool, s) ? s.getExpDrop(sl, sl.random, p, fortune, silk) : 0;
        if (!sl.destroyBlock(p, false, h)) return false;
        Vec3 c = Vec3.atCenterOf(p);
        boolean keepJunk = h.settings.get("keepJunk");
        boolean spilled = false;
        for (ItemStack d : drops) {
            if (!keepJunk && Jobs.isJunk(d) && !h.brain().wanted(d) && Bag.count(h, d.getItem()) >= 64) continue; // extra rubble is thrown away
            if (!Bag.addOrDrop(h, d, c)) spilled = true;
            h.brain().report("item", d);
        }
        h.brain().report("break", s);
        // experience: mends the tool first, the rest goes to the owner
        if (xp > 0) {
            int left = Bag.mend(h, xp);
            ServerPlayer o = h.owner();
            if (left > 0 && o != null) {
                if (o.level() == sl && o.distanceToSqr(h) < 48 * 48) sl.addFreshEntity(new ExperienceOrb(sl, o.getX(), o.getY() + 0.5, o.getZ(), left));
                else o.giveExperiencePoints(left);
            }
        }
        h.addXp(Jobs.xpFor(s));
        if (useTool && !tool.isEmpty() && Bag.damageTool(h)) {
            h.level().playSound(null, h.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 1f, 1f);
            Bag.Kind k = Bag.kindOf(tool);
            boolean spare = k != null && Bag.hasKind(h, k);
            Say.owner(h, Say.mix(h.owner(), spare ? "§c{} พังแล้ว!§r ผมเปลี่ยนไปใช้อันสำรองแทนนะ" : "§c{} พังแล้ว!§r ส่งอันใหม่ให้หน่อยนะ (หนังสือสั่งงาน > ส่งของ ได้จากระยะไกล)",
                    spare ? "§cMy {} broke!§r I switched to a spare one" : "§cMy {} broke!§r Please send me a new one (the command book works from anywhere)", tool.getHoverName()));
        }
        if (spilled || Bag.isFull(h)) h.brain().onBagFull();
        return true;
    }
}
