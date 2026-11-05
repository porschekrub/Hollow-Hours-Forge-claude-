package com.hollowhours.endgame;

import com.hollowhours.HollowHours;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.registry.HHItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The mountain and the old gold mine of Mist Hollow, built where the map points (once someone comes close enough for
 * the chunks to be loaded). A cut in the ground leads to the mine mouth at the foot of the mountain; the tunnel slopes
 * down to a ledge high in the wall of a great cavern, and on its floor the thing that weeps sits buried to the waist.
 * Built a few thousand blocks per tick so nobody notices.
 */
public final class Arena {
    private Arena() {}

    public static final int R_MOUNT = 40, H_MOUNT = 30, RX = 22, RY = 14, DEPTH = 19, MOUTH = 44, TRENCH = 56;
    static int ledgeY;
    static final List<long[]> forced = new ArrayList<>();

    record Op(BlockPos pos, BlockState state) {}

    static final List<Op> QUEUE = new ArrayList<>();
    static int queued;
    static List<BlockPos> chests = new ArrayList<>();
    static List<BlockPos> signs = new ArrayList<>();

    public static boolean building() {
        return !QUEUE.isEmpty();
    }

    public static int queued() {
        return queued;
    }

    /** CI / commands: plan the mountain now (its chunks must be loaded). */
    public static void planNow(ServerLevel level) {
        Endgame.Data d = Endgame.data(level.getServer());
        if (d.site != null) plan(level, d);
    }

    // ------------------------------------------------------------------ ticking
    /** Every 20 ticks (from Endgame.tick) while the map or the rift stage is on. */
    static void tick(MinecraftServer server, Endgame.Data d, long now) {
        if (d.site == null) return;
        ServerLevel level = server.overworld();
        if (d.built == 0 && QUEUE.isEmpty()) {
            boolean near = false;
            for (ServerPlayer p : level.players())
                if (Math.hypot(p.getX() - d.site.getX(), p.getZ() - d.site.getZ()) < 150) near = true;
            if (near) {
                // load (and keep loaded) everything the mountain touches while it is built
                int r = TRENCH + 12;
                for (int cx = (d.site.getX() - r) >> 4; cx <= (d.site.getX() + r) >> 4; cx++)
                    for (int cz = (d.site.getZ() - r) >> 4; cz <= (d.site.getZ() + r) >> 4; cz++) {
                        level.setChunkForced(cx, cz, true);
                        level.getChunk(cx, cz);
                        forced.add(new long[]{cx, cz});
                    }
                plan(level, d);
            }
            return;
        }
        if (d.built != 2 || d.center == null) return;
        if (d.stage == Endgame.MAP) {
            Vec3 c = Vec3.atBottomCenterOf(d.center);
            List<WeepingEntity> bosses = level.getEntitiesOfClass(WeepingEntity.class, new AABB(c, c).inflate(48));
            if (!bosses.isEmpty()) return;
            for (ServerPlayer p : level.players()) {
                if (!p.isAlive() || p.isSpectator()) continue;
                double hd = Math.hypot(p.getX() - c.x, p.getZ() - c.z);
                if (hd < RX - 3 && Math.abs(p.getY() - c.y) < RY) {
                    spawnBoss(level, c);
                    break;
                }
            }
        } else if (d.stage == Endgame.RIFT) {
            Vec3 c = Vec3.atBottomCenterOf(d.center);
            boolean any = !level.getEntitiesOfClass(RiftEntity.class, new AABB(c, c).inflate(24)).isEmpty();
            boolean bossAround = !level.getEntitiesOfClass(WeepingEntity.class, new AABB(c, c).inflate(48)).isEmpty();
            if (!any && !bossAround && now < d.riftUntil && level.isLoaded(d.center)) {
                RiftEntity rift = HHEntities.RIFT.get().create(level);
                if (rift != null) {
                    rift.moveTo(c.x, c.y + 1.2, c.z, 0, 0);
                    level.addFreshEntity(rift);
                }
            }
        }
    }

    /** Every tick: a slice of the building work. */
    public static void serverTick(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;
        ServerLevel level = server.overworld();
        int n = Math.min(QUEUE.size(), 5000);
        for (int i = 0; i < n; i++) {
            Op op = QUEUE.get(i);
            level.setBlock(op.pos, op.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        QUEUE.subList(0, n).clear();
        if (QUEUE.isEmpty()) finish(level);
    }

    public static void spawnBoss(ServerLevel level, Vec3 c) {
        WeepingEntity boss = HHEntities.WEEPING.get().create(level);
        if (boss == null) return;
        // it faces the ledge the players come down from
        float yaw = 0;
        Endgame.Data d = Endgame.data(level.getServer());
        if (d.site != null) {
            Vec3 mouth = mouthDir(d);
            yaw = (float) Math.toDegrees(Math.atan2(-mouth.x, mouth.z));
        }
        boss.moveTo(c.x, c.y, c.z, yaw, 0);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        boss.startRising();
        level.addFreshEntity(boss);
        HollowHours.LOG.info("[Hollow Hours] the thing under the mountain wakes at {}", c);
    }

    public static Vec3 mouthDir(MinecraftServer server) {
        return mouthDir(Endgame.data(server));
    }

    /** the floor height of the tunnel where it meets the cavern */
    public static int ledge(MinecraftServer server) {
        return ledgeY;
    }

    static Vec3 mouthDir(Endgame.Data d) {
        long seed = d.site.asLong();
        double a = new Random(seed).nextDouble() * Math.PI * 2;
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    // ------------------------------------------------------------------ the plan
    static void plan(ServerLevel level, Endgame.Data d) {
        BlockPos site = d.site;
        Random rnd = new Random(site.asLong() * 31 + 7);
        int cx = site.getX(), cz = site.getZ();
        // ground level: the median of the land around
        List<Integer> hs = new ArrayList<>();
        for (int i = -3; i <= 3; i++)
            for (int j = -3; j <= 3; j++) hs.add(surface(level, cx + i * 9, cz + j * 9));
        hs.sort(Integer::compare);
        int base = hs.get(hs.size() / 2);
        // flat / shallow worlds: no room below, so the cavern sits at ground level inside the mountain
        boolean shallow = base - DEPTH < level.getMinBuildHeight() + 8;
        int floorY = shallow ? base : base - DEPTH;
        BlockPos center = new BlockPos(cx, floorY, cz);
        Vec3 dir = mouthDir(d);
        QUEUE.clear();
        chests.clear();
        signs.clear();

        // ---- the mountain
        for (int dx = -R_MOUNT; dx <= R_MOUNT; dx++) {
            for (int dz = -R_MOUNT; dz <= R_MOUNT; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                double ang = Math.atan2(dz, dx);
                double edge = R_MOUNT * (0.86 + 0.14 * Math.sin(ang * 3 + 1.3) * Math.cos(ang * 5 - 0.4));
                if (dist >= edge) continue;
                double k = 1 - dist / edge;
                double h = H_MOUNT * Math.pow(k, 1.6) * (0.9 + 0.12 * Math.sin(dx * 0.31 + dz * 0.17) + 0.08 * Math.cos(dx * 0.11 - dz * 0.27))
                        + 2.5 * Math.max(0, Math.sin(ang * 4 + dist * 0.25)) * k;
                int top = base + (int) Math.round(h);
                int ground = surface(level, cx + dx, cz + dz);
                if (top <= ground) continue;
                for (int y = ground; y <= top; y++) {
                    BlockState st;
                    int fromTop = top - y;
                    if (fromTop == 0) {
                        if (top - base > 24) st = Blocks.SNOW_BLOCK.defaultBlockState();
                        else if (top - base < 4) st = rnd.nextInt(3) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                        else st = pick(rnd, Blocks.STONE, Blocks.STONE, Blocks.ANDESITE, Blocks.GRAVEL, Blocks.TUFF, Blocks.COBBLESTONE);
                    } else if (fromTop < 3 && top - base < 4) st = Blocks.DIRT.defaultBlockState();
                    else st = rnd.nextInt(9) == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    put(cx + dx, y, cz + dz, st);
                }
                if (top - base < 4 && top - base > 0 && rnd.nextInt(30) == 0) put(cx + dx, top + 1, cz + dz, Blocks.DEAD_BUSH.defaultBlockState());
            }
        }

        // ---- the cavern: an uneven dome, walls of deepslate run through with gold
        for (int dx = -RX - 3; dx <= RX + 3; dx++) {
            for (int dz = -RX - 3; dz <= RX + 3; dz++) {
                for (int dy = -2; dy <= RY + 3; dy++) {
                    double wob = 1 + 0.08 * Math.sin(dx * 0.4 + dy * 0.3) * Math.cos(dz * 0.35 - dy * 0.2) + 0.05 * Math.sin((dx + dz) * 0.7);
                    double e = (sq(dx / (RX * wob)) + sq(Math.max(0, dy) / (double) RY) + sq(dz / (RX * wob)));
                    int x = cx + dx, y = floorY + dy, z = cz + dz;
                    if (dy < 0) {
                        if (e < 1.0 && dy == -1) put(x, y, z, floorBlock(rnd, dx, dz));
                        else if (e < 1.25) put(x, y, z, wall(rnd, x, y, z));
                        continue;
                    }
                    if (e < 1.0) put(x, y, z, Blocks.AIR.defaultBlockState());
                    else if (e < 1.32) put(x, y, z, wall(rnd, x, y, z));
                }
            }
        }
        // the pit it sits in, rubble and old bones
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r < 4.6) put(cx + dx, floorY - 1, cz + dz, r < 3 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState());
            }
        for (int i = 0; i < 26; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 7 + rnd.nextDouble() * (RX - 9);
            int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
            int kind = rnd.nextInt(10);
            BlockState st = kind < 4 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : kind < 6 ? Blocks.BONE_BLOCK.defaultBlockState()
                    : kind < 8 ? Blocks.TUFF.defaultBlockState() : Blocks.RAW_GOLD_BLOCK.defaultBlockState();
            put(x, floorY, z, st);
            if (rnd.nextInt(3) == 0) put(x, floorY + 1, z, kind < 6 ? Blocks.SKELETON_SKULL.defaultBlockState() : Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState());
        }
        // stalactites
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * (RX - 4);
            int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
            double e0 = sq(r / RX);
            int ceil = floorY + (int) Math.floor(RY * Math.sqrt(Math.max(0, 1 - e0)) - 0.5);
            int len = 1 + rnd.nextInt(3);
            for (int k = 0; k < len; k++) {
                DripstoneThickness th = k == len - 1 ? DripstoneThickness.TIP : k == 0 && len > 2 ? DripstoneThickness.BASE : DripstoneThickness.FRUSTUM;
                put(x, ceil - k, z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                        .setValue(PointedDripstoneBlock.THICKNESS, th));
            }
        }
        // soul lanterns on chains, candles round the pit
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * Math.PI * 2 + 0.4, r = 11;
            int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
            int ceil = floorY + (int) Math.floor(RY * Math.sqrt(Math.max(0, 1 - sq(r / RX))) - 0.5);
            int bottom = floorY + 7 + rnd.nextInt(3);
            for (int y = ceil; y > bottom; y--) put(x, y, z, Blocks.CHAIN.defaultBlockState());
            put(x, bottom, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        for (int i = 0; i < 10; i++) {
            double a = i / 10.0 * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(a) * 6.5), z = cz + (int) Math.round(Math.sin(a) * 6.5);
            put(x, floorY, z, Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + rnd.nextInt(3)).setValue(CandleBlock.LIT, true));
        }

        // ---- the mine: a cut in the ground, the mouth, the tunnel sloping down to a ledge in the cavern wall
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Direction up = Direction.getNearest(dir.x, 0, dir.z); // walking out = uphill
        int entryY = surface(level, cx + (int) Math.round(dir.x * TRENCH), cz + (int) Math.round(dir.z * TRENCH));
        ledgeY = shallow ? floorY : floorY + 4;
        int lastFloor = entryY;
        for (double t = TRENCH; t >= RX - 1; t -= 0.5) {
            int fy = Math.max(ledgeY, entryY - (int) Math.floor((TRENCH - t) / 2.0));
            boolean inside = t < RX + 0.5;
            for (double s = -2; s <= 2; s += 0.5) {
                int x = cx + (int) Math.floor(dir.x * t + side.x * s + 0.5), z = cz + (int) Math.floor(dir.z * t + side.z * s + 0.5);
                boolean edgeCol = Math.abs(s) > 1.6;
                put(x, fy - 1, z, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                for (int y = fy; y <= fy + 4; y++) put(x, y, z, Blocks.AIR.defaultBlockState());
                if (!inside) {
                    // walls and roof of rock so water / gravel stays out
                    put(x, fy + 5, z, y5(level, x, fy + 5, z));
                }
                if (fy < lastFloor && !edgeCol) put(x, fy, z, Blocks.COBBLED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up));
            }
            // side walls
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int x = cx + (int) Math.floor(dir.x * t + side.x * 2.6 * sgn + 0.5), z = cz + (int) Math.floor(dir.z * t + side.z * 2.6 * sgn + 0.5);
                for (int y = fy - 1; y <= fy + 4; y++) if (!inside) put(x, y, z, y5(level, x, y, z));
            }
            // timber frames every 4 blocks, lanterns every 8
            if (!inside && Math.abs(t - Math.round(t)) < 1e-6 && (int) Math.round(t) % 4 == 0 && t < MOUTH + 1) {
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    int x = cx + (int) Math.floor(dir.x * t + side.x * 2 * sgn + 0.5), z = cz + (int) Math.floor(dir.z * t + side.z * 2 * sgn + 0.5);
                    for (int y = fy; y <= fy + 3; y++) put(x, y, z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                }
                for (double s = -2; s <= 2; s += 0.5) {
                    int x = cx + (int) Math.floor(dir.x * t + side.x * s + 0.5), z = cz + (int) Math.floor(dir.z * t + side.z * s + 0.5);
                    put(x, fy + 4, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
                if ((int) Math.round(t) % 8 == 0) {
                    int x = cx + (int) Math.floor(dir.x * t + 0.5), z = cz + (int) Math.floor(dir.z * t + 0.5);
                    put(x, fy + 3, z, (t < 30 ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
            }
            lastFloor = fy;
        }
        // the ledge: a ramp of stairs down to the cavern floor
        for (int k = 0; k <= 4 && ledgeY > floorY; k++) {
            double t = RX - 2 - k;
            int fy = ledgeY - 1 - k;
            for (double s = -1.5; s <= 1.5; s += 0.5) {
                int x = cx + (int) Math.floor(dir.x * t + side.x * s + 0.5), z = cz + (int) Math.floor(dir.z * t + side.z * s + 0.5);
                for (int y = floorY; y < fy; y++) put(x, y, z, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                if (fy >= floorY) put(x, fy, z, Blocks.COBBLED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up));
            }
        }
        // the mouth: a heavy frame in the slope, the old warning nailed to it
        {
            double t = MOUTH;
            int fy = Math.max(ledgeY, entryY - (int) Math.floor((TRENCH - t) / 2.0));
            for (double s = -3; s <= 3; s += 0.5) {
                int x = cx + (int) Math.floor(dir.x * (t + 1) + side.x * s + 0.5), z = cz + (int) Math.floor(dir.z * (t + 1) + side.z * s + 0.5);
                boolean post = Math.abs(s) >= 2.5;
                if (post) for (int y = fy - 1; y <= fy + 5; y++) put(x, y, z, Blocks.SPRUCE_LOG.defaultBlockState());
                put(x, fy + 5, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                        Math.abs(side.x) > Math.abs(side.z) ? Direction.Axis.X : Direction.Axis.Z));
            }
            int x = cx + (int) Math.floor(dir.x * (t + 2) + side.x * 3.5 + 0.5), z = cz + (int) Math.floor(dir.z * (t + 2) + side.z * 3.5 + 0.5);
            BlockPos sp = new BlockPos(x, fy + 2, z);
            put(sp.getX(), sp.getY(), sp.getZ(), Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, up));
            signs.add(sp);
            // a chest someone left at the mouth: what you need for what is down there
            int cx2 = cx + (int) Math.floor(dir.x * (t + 3) - side.x * 1.6 + 0.5), cz2 = cz + (int) Math.floor(dir.z * (t + 3) - side.z * 1.6 + 0.5);
            int cy = Math.max(ledgeY, entryY - (int) Math.floor((TRENCH - t - 3) / 2.0));
            BlockPos cp = new BlockPos(cx2, cy, cz2);
            put(cp.getX(), cp.getY(), cp.getZ(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, up.getOpposite().getClockWise()));
            chests.add(cp);
        }
        // the White Deer shrine beside the cut (the rule: never dig deeper than the shrine)
        {
            int sx = cx + (int) Math.round(dir.x * (TRENCH - 2) + side.x * 6), sz = cz + (int) Math.round(dir.z * (TRENCH - 2) + side.z * 6);
            int sy = surface(level, sx, sz);
            for (int i = -1; i <= 1; i++)
                for (int j = -1; j <= 1; j++) put(sx + i, sy, sz + j, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            put(sx, sy + 1, sz, Blocks.QUARTZ_PILLAR.defaultBlockState());
            put(sx, sy + 2, sz, Blocks.QUARTZ_PILLAR.defaultBlockState());
            put(sx, sy + 3, sz, Blocks.BONE_BLOCK.defaultBlockState());
            put(sx, sy + 4, sz, Blocks.SKELETON_SKULL.defaultBlockState());
            put(sx + 1, sy + 1, sz, Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
            put(sx - 1, sy + 1, sz, Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2).setValue(CandleBlock.LIT, true));
            put(sx, sy + 1, sz + 1, Blocks.WHITE_WOOL.defaultBlockState());
            put(sx, sy + 1, sz - 1, Blocks.POPPY.defaultBlockState());
        }
        queued = QUEUE.size();
        d.built = 1;
        d.center = center;
        d.setDirty();
        HollowHours.LOG.info("[Hollow Hours] building the mountain at {} (cavern floor {}, {} blocks)", site, floorY, queued);
    }

    static void finish(ServerLevel level) {
        Endgame.Data d = Endgame.data(level.getServer());
        for (BlockPos cp : chests) {
            if (level.getBlockEntity(cp) instanceof ChestBlockEntity ch) {
                ItemStack[] loot = {new ItemStack(HHItems.RIFLE_AMMO.get(), 48), new ItemStack(HHItems.PISTOL_AMMO.get(), 32), new ItemStack(HHItems.SHOTGUN_SHELL.get(), 16),
                        new ItemStack(HHItems.SNIPER_AMMO.get(), 10), new ItemStack(HHItems.GRENADE.get(), 6), new ItemStack(Items.GOLDEN_APPLE, 4),
                        new ItemStack(Items.COOKED_BEEF, 16), new ItemStack(Items.TORCH, 32), new ItemStack(HHItems.BATTERY.get(), 3)};
                for (int i = 0; i < loot.length; i++) ch.setItem(i * 3 % 27, loot[i]);
            }
        }
        for (BlockPos sp : signs) {
            if (level.getBlockEntity(sp) instanceof SignBlockEntity sign) {
                var text = sign.getFrontText();
                for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.translatable("hollowhours.endgame.sign." + i));
                sign.setText(text, true);
                sign.setWaxed(true);
                sign.setChanged();
                level.sendBlockUpdated(sp, sign.getBlockState(), sign.getBlockState(), 3);
            }
        }
        d.built = 2;
        d.setDirty();
        for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], false);
        forced.clear();
        HollowHours.LOG.info("[Hollow Hours] the mountain is built ({} blocks)", queued);
    }

    // ------------------------------------------------------------------ helpers
    static void put(int x, int y, int z, BlockState st) {
        if (st == null) return;
        QUEUE.add(new Op(new BlockPos(x, y, z), st));
    }

    static double sq(double v) {
        return v * v;
    }

    static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
    }

    static BlockState pick(Random r, Block... bs) {
        return bs[r.nextInt(bs.length)].defaultBlockState();
    }

    static BlockState wall(Random r, int x, int y, int z) {
        double v = Math.sin(x * 0.21 + y * 0.37) + Math.sin(z * 0.19 - y * 0.29) + Math.sin((x + z) * 0.13 + y * 0.11);
        if (Math.abs(v) < 0.09) return r.nextInt(5) == 0 ? Blocks.RAW_GOLD_BLOCK.defaultBlockState() : Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
        int k = r.nextInt(20);
        if (k < 12) return Blocks.DEEPSLATE.defaultBlockState();
        if (k < 15) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        if (k < 18) return Blocks.TUFF.defaultBlockState();
        return Blocks.BLACKSTONE.defaultBlockState();
    }

    static BlockState floorBlock(Random r, int dx, int dz) {
        int k = r.nextInt(12);
        if (k < 6) return Blocks.DEEPSLATE.defaultBlockState();
        if (k < 8) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        if (k < 10) return Blocks.TUFF.defaultBlockState();
        if (k < 11) return Blocks.GRAVEL.defaultBlockState();
        return Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
    }

    /** Rock for the tunnel shell underground (null = leave it): caves, water and loose blocks are sealed off. */
    static BlockState y5(ServerLevel level, int x, int y, int z) {
        if (y >= surface(level, x, z)) return null;
        BlockState now = level.getBlockState(new BlockPos(x, y, z));
        if (now.isAir() || !now.getFluidState().isEmpty() || now.is(Blocks.GRAVEL) || now.is(Blocks.SAND)) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        return null;
    }

    static List<BlockPos> listOf(BlockPos... ps) {
        return new ArrayList<>(Arrays.asList(ps));
    }
}
