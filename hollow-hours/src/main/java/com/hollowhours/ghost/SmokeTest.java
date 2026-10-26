package com.hollowhours.ghost;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.BoardsEntity;
import com.hollowhours.entity.ClosetEntity;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.entity.StalkerEntity;
import com.hollowhours.entity.StalkerKind;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.registry.HHItems;
import com.hollowhours.registry.HHSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Run by the CI server smoke test (-Dhollowhours.smoketest=true): loads all data, spawns every entity, ticks the
 * ghost systems for a moment, then prints "SMOKE TEST OK" and stops the server. Any exception fails the build.
 */
final class SmokeTest {
    private SmokeTest() {}

    static void run(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            BlockPos base = level.getSharedSpawnPos();
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ());
            Vec3 at = new Vec3(base.getX() + 0.5, y, base.getZ() + 0.5);
            check(GhostTalk.data().lines.containsKey("en") && GhostTalk.data().lines.containsKey("th"), "dialogue languages");
            for (GhostKind k : GhostKind.values()) {
                check(!KillTracks.killsFor(k.n).isEmpty(), "kill tracks for ghost " + k.n);
                check(!GhostTalk.pool("en", k.n, "greet").isEmpty() && !GhostTalk.pool("th", k.n, "greet").isEmpty(), "lines for ghost " + k.n);
            }
            check(KillTracks.get("grab") != null && KillTracks.get("scream") != null && KillTracks.get("hold") != null, "core kill tracks");
            for (String s : HHSounds.NAMES) check(HHSounds.get(s) != null, "sound " + s);
            check(GhostTalk.classify("you are so stupid").intent().equals("taunt"), "intent taunt");
            check(GhostTalk.classify("ขอร้องอย่าทำร้ายฉัน").intent().equals("plead"), "intent plead th");
            check("minecraft:diamond".equals(Curses.resolveItem("give me 5 diamonds")), "resolve item en");
            check("minecraft:iron_pickaxe".equals(Curses.resolveItem("ขออีเต้อเหล็กหน่อย")), "resolve item th");
            ItemStack cursed = Curses.makeCursed(Items.IRON_SWORD, 1, 3);
            check(Curses.isCursed(cursed), "curse item");

            for (GhostKind k : GhostKind.values()) {
                GhostEntity g = Director.spawnGhost(level, k, at.add(k.n * 3, 0, 0));
                check(g != null, "spawn ghost " + k.n);
                for (int i = 0; i < 40; i++) g.tick();
                g.discard();
            }
            for (StalkerKind k : StalkerKind.values()) {
                StalkerEntity s = HHEntities.stalker(k).create(level);
                check(s != null, "stalker " + k);
                s.moveTo(at.x, at.y, at.z - 4);
                level.addFreshEntity(s);
                s.discard();
            }
            ClosetEntity c = HHEntities.CLOSET.get().create(level);
            check(c != null, "closet");
            c.moveTo(at.x + 2, at.y, at.z + 2, 90, 0);
            level.addFreshEntity(c);
            c.pulseDoor(ClosetEntity.DOOR_OPEN, 5);
            c.discard();
            // a door with boards
            BlockPos door = BlockPos.containing(at).offset(0, 0, 6);
            level.setBlock(door.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState(), 3);
            level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            BoardsEntity b = BoardsEntity.create(level, door, BoardsEntity.KIND_DOOR, 0, 1, Vec3.atBottomCenterOf(door).add(0, 0, 0.2));
            b.addBoard(0);
            b.addBoard(2);
            check(b.hitsToClear(1, false)[1] == 1, "iron board too strong for power 1");
            check(Breach.hitOpening(level, door, 3, false, new Vec3(0, 0, 1)) != Breach.Res.GONE, "hit boarded door");
            b.discard();
            check(new ItemStack(HHItems.FLASHLIGHT.get()).getItem() != Items.AIR, "items registered");
            HollowHours.LOG.info("SMOKE TEST OK");
        } catch (Throwable t) {
            HollowHours.LOG.error("SMOKE TEST FAILED", t);
        }
        server.halt(false);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
