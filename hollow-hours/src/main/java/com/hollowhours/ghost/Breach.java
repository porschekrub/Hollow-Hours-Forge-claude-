package com.hollowhours.ghost;

import com.hollowhours.entity.BoardsEntity;
import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Doors / windows / gates / trapdoors crack under a ghost's blows until they burst (port of breach.js).
 * Strength: glass (3 hits) < wood < iron; a ghost can only break what its power allows (most: wood).
 * Cracks are saved with the world and shown with the vanilla crack texture; only a repair kit fixes them,
 * mining a cracked block makes it crumble with no drop.
 */
public final class Breach {
    private Breach() {}

    public enum Res { BROKEN, CRACK, BLOCKED, GONE }

    /** kind: door, trapdoor, gate, glass, pane. fam: wood, iron, glass. cls: 0 glass, 1 wood, 2 stone, 3 iron. */
    public record Info(String kind, String fam, int cls, int hp) {
        public boolean isDoorLike() {
            return kind.equals("door") || kind.equals("gate");
        }
    }

    public static final double[] POWER_DMG = {0, 1.0, 1.5, 2.0};
    static final double STUCK_CAP = 0.74;

    @Nullable
    public static Info classify(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof DoorBlock) return b == Blocks.IRON_DOOR ? new Info("door", "iron", 3, 16) : new Info("door", "wood", 1, 6);
        if (b instanceof TrapDoorBlock) return b == Blocks.IRON_TRAPDOOR ? new Info("trapdoor", "iron", 3, 12) : new Info("trapdoor", "wood", 1, 5);
        if (b instanceof FenceGateBlock) return new Info("gate", "wood", 1, 5);
        if (s.is(Tags.Blocks.GLASS_PANES)) return new Info("pane", "glass", 0, 3);
        if (s.is(Tags.Blocks.GLASS)) return new Info("glass", "glass", 0, b == Blocks.TINTED_GLASS ? 4 : 3);
        return null;
    }

    public static boolean isOpen(BlockState s) {
        return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
    }

    /** Doors are always addressed by their lower half. */
    public static BlockPos basePos(BlockState s, BlockPos pos) {
        if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return pos.below();
        return pos;
    }

    // ------------------------------------------------------------------ saved cracks
    public static final class Data extends SavedData {
        final Map<Long, CompoundTag> cracks = new HashMap<>();

        static Data load(CompoundTag tag) {
            Data d = new Data();
            ListTag list = tag.getList("cracks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                d.cracks.put(c.getLong("p"), c);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (CompoundTag c : cracks.values()) if (!c.getBoolean("tmp")) list.add(c);
            tag.put("cracks", list);
            return tag;
        }
    }

    public static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data::load, Data::new, "hollowhours_cracks");
    }

    @Nullable
    public static CompoundTag crackOf(ServerLevel level, BlockPos pos) {
        return data(level).cracks.get(pos.asLong());
    }

    static int stageOf(CompoundTag e) {
        double h = e.getDouble("h"), hp = e.getDouble("hp");
        if ("glass".equals(e.getString("f"))) return hp <= 3 ? (h >= 2 ? 8 : 4) : (int) Math.min(9, Math.ceil(9 * h / hp));
        return (int) Math.max(1, Math.min(9, Math.ceil(9 * h / hp)));
    }

    static int breakerId(BlockPos pos, int part) {
        return -1_000_000 - (int) ((pos.asLong() * 31 + part) & 0x3FFFFFFF);
    }

    static void showCrack(ServerLevel level, BlockPos pos, int stage, boolean door) {
        level.destroyBlockProgress(breakerId(pos, 0), pos, stage);
        if (door) level.destroyBlockProgress(breakerId(pos, 1), pos.above(), stage);
    }

    static void clearCrackVisual(ServerLevel level, BlockPos pos) {
        level.destroyBlockProgress(breakerId(pos, 0), pos, -1);
        level.destroyBlockProgress(breakerId(pos, 1), pos.above(), -1);
    }

    public static void clearCrack(ServerLevel level, BlockPos pos) {
        Data d = data(level);
        if (d.cracks.remove(pos.asLong()) != null) d.setDirty();
        clearCrackVisual(level, pos);
    }

    /** Temporary crack on an ordinary block (walls being smashed). frac 0..1 */
    public static void crackGeneric(ServerLevel level, BlockPos pos, double frac) {
        level.destroyBlockProgress(breakerId(pos, 2), pos, (int) Math.max(0, Math.min(9, Math.round(frac * 9))));
    }

    public static void clearGeneric(ServerLevel level, BlockPos pos) {
        level.destroyBlockProgress(breakerId(pos, 2), pos, -1);
    }

    // ------------------------------------------------------------------ hitting
    /** One blow from a ghost of the given power. dir = direction of the blow (outside -> inside). */
    public static Res hitOpening(ServerLevel level, BlockPos pos, int power, boolean rage, Vec3 dir) {
        BlockState s = level.getBlockState(pos);
        Info info = classify(s);
        if (info == null) return Res.GONE;
        BoardsEntity boards = BoardsEntity.at(level, pos);
        if (boards != null && boards.count() > 0) return boards.hit(power, rage, dir);
        Data d = data(level);
        CompoundTag e = d.cracks.get(pos.asLong());
        if (e == null || !info.kind.equals(e.getString("k"))) {
            e = new CompoundTag();
            e.putLong("p", pos.asLong());
            e.putString("k", info.kind);
            e.putString("f", info.fam);
            e.putDouble("h", 0);
            e.putDouble("hp", info.hp);
        }
        boolean canBreak = info.cls <= power;
        double dmg = info.fam.equals("glass") ? 1 : POWER_DMG[Math.max(1, Math.min(3, power))] * (rage ? 1.5 : 1);
        double before = e.getDouble("h"), hp = e.getDouble("hp");
        double h = before + dmg;
        if (!canBreak) h = Math.min(before + dmg * 1.6, hp * STUCK_CAP);
        e.putDouble("h", h);
        e.putLong("t", level.getGameTime());
        if (canBreak && h >= hp - 1e-6) {
            d.cracks.remove(pos.asLong());
            d.setDirty();
            clearCrackVisual(level, pos);
            burstFx(level, pos, info, s, dir);
            removeNoDrop(level, pos, info);
            return Res.BROKEN;
        }
        d.cracks.put(pos.asLong(), e);
        d.setDirty();
        int stage = stageOf(e);
        showCrack(level, pos, stage, info.kind.equals("door"));
        hitFx(level, pos, info, s, dir, stage, power >= 3 || rage);
        return !canBreak && before >= hp * STUCK_CAP - 1e-6 ? Res.BLOCKED : Res.CRACK;
    }

    static Vec3 center(BlockPos pos, Info info) {
        return Vec3.atBottomCenterOf(pos).add(0, info.kind.equals("door") ? 1.0 : 0.5, 0);
    }

    static void hitFx(ServerLevel level, BlockPos pos, Info info, BlockState s, Vec3 dir, int stage, boolean heavy) {
        Vec3 c = center(pos, info);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), c.x - dir.x * 0.3, c.y, c.z - dir.z * 0.3, 14, 0.25, 0.4, 0.25, 0.15);
        switch (info.fam) {
            case "glass" -> {
                Fx.sound(level, "ghost.glass.crack", c, 1.4f, R.pitch(0.9, 1.15), 40);
                Fx.sound(level, "ghost.knock.glass", c, 1.2f, R.pitch(0.7, 0.85), 30);
            }
            case "iron" -> {
                Fx.sound(level, "ghost.bang.metal", c, 2.4f, R.pitch(0.7, 0.85), 64);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.2);
            }
            default -> {
                Fx.sound(level, "ghost.bang.wood", c, 2.4f, R.pitch(0.85, 1.05), 56);
                level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 3, 0.3, 0.4, 0.3, 0.02);
            }
        }
        if (stage >= 6 && R.chance(0.5)) Fx.sound(level, "random.anvil_land", c, 0.35f, R.pitch(0.5, 0.6), 24);
        Fx.shakeNear(level, c, 14, heavy ? 0.55f : 0.32f, 7);
    }

    static void burstFx(ServerLevel level, BlockPos pos, Info info, BlockState s, Vec3 dir) {
        Vec3 c = center(pos, info);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.3, 0.5, 0.3, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), c.x, c.y, c.z, 60, 0.5, 0.8, 0.5, 0.35);
        if (info.fam.equals("glass")) {
            Fx.sound(level, "ghost.glass.shatter", c, 2.2f, R.pitch(0.9, 1.1), 64);
            Fx.sound(level, "random.glass", c, 1.5f, 0.8f, 48);
        } else {
            Fx.sound(level, "ghost.burst", c, 2.6f, R.pitch(0.9, 1.05), 80);
            Fx.sound(level, "random.explode", c, 1.6f, R.pitch(0.95, 1.15), 80);
        }
        Fx.shakeNear(level, c, 18, 0.9f, 14);
        for (ServerPlayer p : level.players()) {
            double dist = p.position().distanceTo(c);
            if (dist > 4.5) continue;
            Vec3 away = p.position().subtract(c).multiply(1, 0, 1).normalize();
            p.knockback(1.1, -away.x, -away.z);
            p.hurtMarked = true;
            Fx.fade(p, 0x3C0000, 1, 2, 7);
        }
    }

    static void removeNoDrop(ServerLevel level, BlockPos pos, Info info) {
        if (info.kind.equals("door")) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3 | 32);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(2.5)).forEach(it -> {
            if (it.tickCount < 3) it.discard();
        });
    }

    // ------------------------------------------------------------------ repair
    public static boolean repair(ServerPlayer p, ServerLevel level, BlockPos clicked, String kitFam) {
        BlockState s = level.getBlockState(clicked);
        Info info = classify(s);
        if (info == null) return false;
        BlockPos pos = basePos(s, clicked);
        BoardsEntity boards = BoardsEntity.at(level, pos);
        boolean boardFix = boards != null && boards.damageLevel() > 0 && boards.repairWith(kitFam);
        CompoundTag e = crackOf(level, pos);
        if (e == null && !boardFix) {
            Fx.actionbar(p, Component.translatable("hollowhours.repair.none"));
            return true;
        }
        if (e != null) {
            boolean ok = kitFam.equals(info.fam) || (kitFam.equals("iron") && info.fam.equals("iron"));
            if (!ok) {
                Fx.actionbar(p, Component.translatable("hollowhours.repair.wrong", Component.translatable("item.hollowhours.repair_" + info.fam)));
                return true;
            }
            clearCrack(level, pos);
        }
        if (!p.getAbilities().instabuild) p.getMainHandItem().shrink(1);
        Vec3 c = center(pos, info);
        Fx.sound(level, "ghost.repair", c, 1.2f, R.pitch(0.95, 1.1), 24);
        if (info.fam.equals("glass")) Fx.sound(level, "random.glass", c, 0.4f, 1.6f, 16);
        level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.02);
        Fx.actionbar(p, Component.translatable("hollowhours.repair.done." + (info.fam.equals("glass") ? "glass" : "door")));
        return true;
    }

    /** Mining a cracked door/gate/trapdoor: it crumbles, no drop. Returns true when handled. */
    public static boolean onPlayerBreak(ServerPlayer p, ServerLevel level, BlockPos clicked, BlockState s) {
        Info info = classify(s);
        if (info == null) return false;
        BlockPos pos = basePos(s, clicked);
        if (crackOf(level, pos) == null) return false;
        if (info.fam.equals("glass")) {
            clearCrack(level, pos);
            return false;
        }
        clearCrack(level, pos);
        removeNoDrop(level, pos, info);
        Vec3 c = center(pos, info);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), c.x, c.y, c.z, 30, 0.3, 0.6, 0.3, 0.1);
        Fx.sound(level, info.fam.equals("wood") ? "ghost.bang.wood" : "ghost.bang.metal", c, 0.6f, 1.3f, 20);
        Fx.actionbar(p, Component.translatable("hollowhours.repair.crumble"));
        return true;
    }

    public static void onPlace(ServerLevel level, BlockPos pos) {
        Data d = data(level);
        if (d.cracks.containsKey(pos.asLong()) || d.cracks.containsKey(pos.below().asLong())) {
            clearCrack(level, pos);
            CompoundTag below = d.cracks.get(pos.below().asLong());
            if (below != null && "door".equals(below.getString("k"))) clearCrack(level, pos.below());
        }
    }

    /** Every few seconds: refresh crack textures near players, forget cracks whose block is gone. */
    public static void maintain(ServerLevel level) {
        Data d = data(level);
        boolean dirty = false;
        for (Iterator<CompoundTag> it = d.cracks.values().iterator(); it.hasNext(); ) {
            CompoundTag e = it.next();
            BlockPos pos = BlockPos.of(e.getLong("p"));
            if (!level.isLoaded(pos)) continue;
            boolean near = false;
            List<ServerPlayer> ps = level.players();
            for (ServerPlayer p : ps) if (p.blockPosition().closerThan(pos, 56)) near = true;
            if (!near) continue;
            BlockState s = level.getBlockState(pos);
            Info info = classify(s);
            if (info == null || !info.kind.equals(e.getString("k")) || !basePos(s, pos).equals(pos)) {
                it.remove();
                dirty = true;
                clearCrackVisual(level, pos);
                continue;
            }
            showCrack(level, pos, stageOf(e), info.kind.equals("door"));
        }
        if (dirty) d.setDirty();
    }

    /** Boards hit numbers (for route scoring): total hits to clear, and whether one is too strong. */
    public static int[] boardsHits(@Nullable BoardsEntity b, int power, boolean rage) {
        if (b == null) return new int[]{0, 0};
        return b.hitsToClear(power, rage);
    }
}
