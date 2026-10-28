package com.hollowhours.physics;

import com.hollowhours.config.HHConfig;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Later;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Physics that make the world feel heavy and real:
 * - chop one log of a tree and the whole tree tips over and crashes down (sneak to break just that log);
 * - mine one ore and every touching ore of the same kind cracks out of the rock, one after another, bursting into
 *   the air (sneak to mine just one);
 * - explosions throw blocks through the air instead of deleting them; they land as rubble.
 */
public final class Physics {
    private Physics() {}

    private static final Set<BlockPos> BUSY = new HashSet<>();

    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level) || p.isShiftKeyDown() || p.isCreative()) return;
        BlockPos pos = e.getPos();
        if (BUSY.contains(pos)) return;
        BlockState s = e.getState();
        if (HHConfig.TREE_FELLING.get() && s.is(BlockTags.LOGS)) fell(p, level, pos);
        else if (HHConfig.VEIN_MINING.get() > 1 && s.is(Tags.Blocks.ORES) && p.hasCorrectToolForDrops(s)) vein(p, level, pos, s);
    }

    // ------------------------------------------------------------------ trees
    static void fell(ServerPlayer p, ServerLevel level, BlockPos stump) {
        List<BlockPos> logs = new ArrayList<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(stump);
        seen.add(stump);
        while (!q.isEmpty() && logs.size() < 300) {
            BlockPos c = q.poll();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = c.offset(dx, dy, dz);
                        if (n.getY() < stump.getY() || !seen.add(n)) continue;
                        if (level.getBlockState(n).is(BlockTags.LOGS)) {
                            logs.add(n);
                            q.add(n);
                        }
                    }
        }
        if (logs.isEmpty()) return;
        // leaves that belong to it (natural ones only: a log wall of a house is not a tree)
        Set<BlockPos> leaves = new HashSet<>();
        for (BlockPos l : logs) {
            for (BlockPos n : BlockPos.betweenClosed(l.offset(-3, -1, -3), l.offset(3, 3, 3))) {
                if (leaves.size() >= 700) break;
                BlockState ns = level.getBlockState(n);
                if (ns.getBlock() instanceof LeavesBlock && !ns.getValue(LeavesBlock.PERSISTENT)) leaves.add(n.immutable());
            }
        }
        if (leaves.size() < 6) return;
        Vec3 away = Vec3.atBottomCenterOf(stump).subtract(p.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = Vec3.directionFromRotation(0, p.getYRot());
        away = away.normalize();
        // the falling tree: everything above the stump, taken out of the world
        FallingTreeEntity tree = HHEntities.FALLING_TREE.get().create(level);
        if (tree == null) return;
        tree.moveTo(stump.getX() + 0.5, stump.getY(), stump.getZ() + 0.5);
        tree.dirX = (float) away.x;
        tree.dirZ = (float) away.z;
        tree.by = p.getUUID();
        tree.tool = p.getMainHandItem().copy();
        List<BlockPos> all = new ArrayList<>(logs);
        all.addAll(leaves);
        for (BlockPos b : all) {
            BlockState bs = level.getBlockState(b);
            tree.states.add(bs);
            tree.rel.add(b.subtract(stump));
            level.setBlock(b, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        level.addFreshEntity(tree);
        // chopping a whole tree wears the axe a little
        ItemStack axe = p.getMainHandItem();
        if (axe.isDamageableItem()) axe.hurtAndBreak(Math.max(0, logs.size() / 4), p, pl -> pl.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        p.causeFoodExhaustion(0.005f * logs.size());
        level.playSound(null, stump, net.minecraft.sounds.SoundEvents.WOOD_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1.4f, 0.5f);
    }

    // ------------------------------------------------------------------ ore veins
    public static String oreKind(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().replace("deepslate_", "");
    }

    static void vein(ServerPlayer p, ServerLevel level, BlockPos start, BlockState s) {
        String kind = oreKind(s);
        int max = HHConfig.VEIN_MINING.get();
        List<BlockPos> vein = new ArrayList<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && vein.size() < max - 1) {
            BlockPos c = q.poll();
            for (BlockPos n : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
                BlockPos ni = n.immutable();
                if (!seen.add(ni)) continue;
                BlockState ns = level.getBlockState(ni);
                if (ns.is(Tags.Blocks.ORES) && oreKind(ns).equals(kind)) {
                    vein.add(ni);
                    q.add(ni);
                    if (vein.size() >= max - 1) break;
                }
            }
        }
        if (vein.isEmpty()) return;
        vein.sort(Comparator.comparingDouble(b -> b.distSqr(start)));
        Vec3 center = Vec3.atCenterOf(start);
        int fortune = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, p.getMainHandItem());
        int silk = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, p.getMainHandItem());
        BUSY.addAll(vein);
        for (int i = 0; i < vein.size(); i++) {
            BlockPos b = vein.get(i);
            // one after another, cracking outward through the rock
            Later.run(2 + i * 2, () -> {
                BUSY.remove(b);
                BlockState bs = level.getBlockState(b);
                ItemStack tool = p.getMainHandItem();
                if (!oreKind(bs).equals(kind) || !p.isAlive() || p.hasDisconnected() || tool.isEmpty() || !p.hasCorrectToolForDrops(bs)) return;
                List<ItemStack> drops = Block.getDrops(bs, level, b, level.getBlockEntity(b), p, tool);
                level.levelEvent(2001, b, Block.getId(bs));
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
                Vec3 at = Vec3.atCenterOf(b);
                Vec3 out = at.subtract(center).normalize();
                for (ItemStack d : drops) {
                    ItemEntity ie = new ItemEntity(level, at.x, at.y, at.z, d);
                    ie.setDeltaMovement(out.x * 0.25 + (level.random.nextDouble() - 0.5) * 0.15, 0.25 + level.random.nextDouble() * 0.2,
                            out.z * 0.25 + (level.random.nextDouble() - 0.5) * 0.15);
                    ie.setDefaultPickUpDelay();
                    level.addFreshEntity(ie);
                }
                int xp = bs.getExpDrop(level, level.random, b, fortune, silk);
                if (xp > 0) ExperienceOrb.award(level, at, xp);
                tool.hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
                p.causeFoodExhaustion(0.005f);
            });
        }
    }

    // ------------------------------------------------------------------ explosions
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!HHConfig.EXPLOSION_DEBRIS.get() || !(e.getLevel() instanceof ServerLevel level)) return;
        Explosion ex = e.getExplosion();
        Vec3 c = ex.getPosition();
        List<BlockPos> list = e.getAffectedBlocks();
        int thrown = 0;
        List<BlockPos> fly = new ArrayList<>();
        for (BlockPos b : list) {
            if (thrown >= 48) break;
            BlockState s = level.getBlockState(b);
            if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, b) < 0 || !s.isCollisionShapeFullBlock(level, b)) continue;
            if (s.is(Blocks.TNT) || level.random.nextFloat() > 0.35f) continue;
            fly.add(b);
            thrown++;
        }
        list.removeAll(fly);
        for (BlockPos b : fly) {
            BlockState s = level.getBlockState(b);
            FallingBlockEntity fb = FallingBlockEntity.fall(level, b, s);
            Vec3 d = Vec3.atCenterOf(b).subtract(c);
            double dist = Math.max(0.8, d.length());
            Vec3 v = d.normalize().scale(Math.min(1.2, 2.2 / dist)).add(0, 0.35 + level.random.nextDouble() * 0.35, 0);
            fb.setDeltaMovement(v);
            fb.dropItem = true;
            fb.setHurtsEntities(1.0f, 10);
            fb.hurtMarked = true;
        }
    }
}
