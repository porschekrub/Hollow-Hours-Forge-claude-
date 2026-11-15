package com.veritypal.world;

import com.veritypal.V;
import com.veritypal.VerityPal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Verity builds a small house next to the player (magic: no blocks needed), a few blocks at a time so it can be
 * watched going up: a floor, walls with windows, a door, a roof, a bed, a crafting table, a furnace, a chest and light.
 */
public final class HouseBuilder {
    private HouseBuilder() {}

    record Place(BlockPos pos, BlockState state) {}

    static final class Job {
        final ServerLevel level;
        final List<Place> todo = new ArrayList<>();
        int i;

        Job(ServerLevel level) {
            this.level = level;
        }
    }

    static final List<Job> JOBS = new ArrayList<>();

    record Style(Block floor, Block wall, Block corner, Block window, Block door, Block stairs, Block slab, Block bed) {}

    static Style style(String s) {
        String x = s == null ? "" : s.toLowerCase(Locale.ROOT);
        if (x.contains("stone") || x.contains("หิน") || x.contains("castle") || x.contains("cobble"))
            return new Style(Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.STONE_BRICK_STAIRS,
                    Blocks.STONE_BRICK_SLAB, Blocks.BLUE_BED);
        if (x.contains("cherry") || x.contains("pink") || x.contains("ชมพู") || x.contains("cute") || x.contains("น่ารัก"))
            return new Style(Blocks.CHERRY_PLANKS, Blocks.CHERRY_PLANKS, Blocks.STRIPPED_CHERRY_LOG, Blocks.PINK_STAINED_GLASS_PANE, Blocks.CHERRY_DOOR, Blocks.CHERRY_STAIRS,
                    Blocks.CHERRY_SLAB, Blocks.PINK_BED);
        if (x.contains("spruce") || x.contains("dark") || x.contains("cabin") || x.contains("กระท่อม"))
            return new Style(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_SLAB, Blocks.GREEN_BED);
        return new Style(Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.GLASS_PANE, Blocks.OAK_DOOR, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.YELLOW_BED);
    }

    public static boolean busy() {
        return !JOBS.isEmpty();
    }

    /** @return null when it started, else why not (English, for the AI) */
    @Nullable
    public static String start(ServerPlayer p, String styleName) {
        if (!JOBS.isEmpty()) return "you are already building a house";
        ServerLevel level = p.serverLevel();
        Direction face = p.getDirection();
        Style st = style(styleName);
        // the house: 7 x 7 outside, 4 blocks of wall, the door facing the player, 3 blocks in front of them
        int W = 7;
        BlockPos center = p.blockPosition().relative(face, 3 + W / 2);
        int ground = Places.surfaceY(level, center.getX(), center.getZ(), p.blockPosition().getY());
        if (Math.abs(ground - p.blockPosition().getY()) > 6) ground = p.blockPosition().getY();
        // not on top of someone's things
        BlockPos min = center.offset(-W / 2, 0, -W / 2).atY(ground), max = center.offset(W / 2, 0, W / 2).atY(ground + 6);
        for (BlockPos q : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(q) != null) return "there are the player's things (a chest or a machine) where the house would go: they should stand somewhere more open";
            if (level.getBlockState(q).liquid() && q.getY() == ground) return "there is water where the house would go";
        }
        Job job = new Job(level);
        Direction back = face, door = face.getOpposite();
        Direction right = face.getClockWise();
        int y0 = ground;
        // foundation down to the ground, the floor
        for (int dx = -W / 2; dx <= W / 2; dx++)
            for (int dz = -W / 2; dz <= W / 2; dz++) {
                BlockPos col = center.offset(dx, 0, dz).atY(y0 - 1);
                for (int d = 0; d < 5; d++) {
                    BlockPos q = col.below(d);
                    BlockState s = level.getBlockState(q);
                    if (!s.isAir() && !s.canBeReplaced()) break;
                    job.todo.add(new Place(q, Blocks.COBBLESTONE.defaultBlockState()));
                }
                job.todo.add(new Place(center.offset(dx, 0, dz).atY(y0 - 1), st.floor.defaultBlockState()));
            }
        // clear the inside and the walls' space
        for (int dy = 0; dy <= 5; dy++)
            for (int dx = -W / 2; dx <= W / 2; dx++)
                for (int dz = -W / 2; dz <= W / 2; dz++) {
                    BlockPos q = center.offset(dx, 0, dz).atY(y0 + dy);
                    if (!level.getBlockState(q).isAir()) job.todo.add(new Place(q, Blocks.AIR.defaultBlockState()));
                }
        // walls (windows in the middle of each side), corner posts
        for (int dy = 0; dy < 4; dy++)
            for (int dx = -W / 2; dx <= W / 2; dx++)
                for (int dz = -W / 2; dz <= W / 2; dz++) {
                    boolean edgeX = Math.abs(dx) == W / 2, edgeZ = Math.abs(dz) == W / 2;
                    if (!edgeX && !edgeZ) continue;
                    BlockPos q = center.offset(dx, 0, dz).atY(y0 + dy);
                    BlockState s;
                    if (edgeX && edgeZ) s = st.corner.defaultBlockState();
                    else if ((dy == 1 || dy == 2) && (Math.abs(dx) == 1 || Math.abs(dz) == 1) && dy < 3) s = st.window.defaultBlockState();
                    else s = st.wall.defaultBlockState();
                    job.todo.add(new Place(q, s));
                }
        // the door, in the wall that faces the player
        BlockPos doorPos = center.relative(door, W / 2).atY(y0);
        BlockState d = st.door.defaultBlockState().setValue(DoorBlock.FACING, face).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        job.todo.add(new Place(doorPos, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)));
        job.todo.add(new Place(doorPos.above(), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        // roof: stairs along the two long sides, slabs on top
        for (int dx = -W / 2 - 1; dx <= W / 2 + 1; dx++)
            for (int dz = -W / 2 - 1; dz <= W / 2 + 1; dz++) {
                BlockPos q = center.offset(dx, 0, dz).atY(y0 + 4);
                job.todo.add(new Place(q, st.slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)));
            }
        for (int dx = -W / 2; dx <= W / 2; dx++)
            for (int dz = -W / 2; dz <= W / 2; dz++)
                if (Math.abs(dx) < W / 2 - 1 && Math.abs(dz) < W / 2 - 1)
                    job.todo.add(new Place(center.offset(dx, 0, dz).atY(y0 + 4), st.wall.defaultBlockState()));
        for (int k = -W / 2; k <= W / 2; k++) {
            BlockPos a = center.relative(right, k).relative(back, W / 2 + 1).atY(y0 + 3);
            BlockPos b2 = center.relative(right, k).relative(door, W / 2 + 1).atY(y0 + 3);
            job.todo.add(new Place(a, st.stairs.defaultBlockState().setValue(StairBlock.FACING, door).setValue(StairBlock.HALF, Half.BOTTOM)));
            job.todo.add(new Place(b2, st.stairs.defaultBlockState().setValue(StairBlock.FACING, back).setValue(StairBlock.HALF, Half.BOTTOM)));
        }
        // inside: bed in a back corner, crafting table, furnace, chest, lanterns
        BlockPos bedFoot = center.relative(back, 1).relative(right, 2).atY(y0);
        BlockPos bedHead = bedFoot.relative(back, 1);
        job.todo.add(new Place(bedFoot, st.bed.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, back).setValue(BedBlock.PART, BedPart.FOOT)));
        job.todo.add(new Place(bedHead, st.bed.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, back).setValue(BedBlock.PART, BedPart.HEAD)));
        job.todo.add(new Place(center.relative(back, 2).relative(right, -2).atY(y0), Blocks.CRAFTING_TABLE.defaultBlockState()));
        job.todo.add(new Place(center.relative(back, 2).relative(right, -1).atY(y0),
                Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, door)));
        job.todo.add(new Place(center.relative(back, 2).atY(y0), Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, door)));
        job.todo.add(new Place(center.atY(y0 + 3), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true)));
        job.todo.add(new Place(doorPos.relative(door).relative(right, 1).atY(y0 + 2), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, door)));
        job.todo.add(new Place(doorPos.relative(door).relative(right, -1).atY(y0 + 2), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, door)));
        JOBS.add(job);
        VerityPal.LOG.info("[Verity Pal] building a house at {} ({} steps)", center.atY(y0), job.todo.size());
        Mob v = V.body();
        if (v != null && v.level() == level) v.getNavigation().moveTo(doorPos.getX() + 0.5, doorPos.getY(), doorPos.getZ() + 0.5, 1.2);
        return null;
    }

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Job job = JOBS.get(0);
        for (int n = 0; n < 6 && job.i < job.todo.size(); n++) {
            Place pl = job.todo.get(job.i++);
            job.level.setBlock(pl.pos(), pl.state(), 2 | 16);
            if (!pl.state().isAir()) {
                if (job.i % 3 == 0)
                    job.level.sendParticles(ParticleTypes.END_ROD, pl.pos().getX() + 0.5, pl.pos().getY() + 0.5, pl.pos().getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
                if (job.i % 5 == 0)
                    job.level.playSound(null, pl.pos(), pl.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7f, 1.1f);
            }
        }
        if (job.i >= job.todo.size()) {
            JOBS.remove(0);
            // neighbours update once the whole house stands (doors, beds and torches are whole by then)
            for (Place pl : job.todo) job.level.blockUpdated(pl.pos(), pl.state().getBlock());
            VerityPal.LOG.info("[Verity Pal] the house is done");
        }
    }

    public static void finishAll() {
        while (!JOBS.isEmpty()) {
            Job job = JOBS.get(0);
            while (job.i < job.todo.size()) {
                Place pl = job.todo.get(job.i++);
                job.level.setBlock(pl.pos(), pl.state(), 2 | 16);
            }
            JOBS.remove(0);
        }
    }
}
