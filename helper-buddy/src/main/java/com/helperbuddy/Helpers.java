package com.helperbuddy;

import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.registry.HBRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Finding helpers in all loaded dimensions. */
public final class Helpers {
    private Helpers() {}

    public static List<HelperEntity> all(MinecraftServer server) {
        List<HelperEntity> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels())
            for (Entity e : l.getEntities(HBRegistry.HELPER.get(), e -> e.isAlive())) if (e instanceof HelperEntity h) out.add(h);
        return out;
    }

    /** The player's helpers (loaded ones). */
    public static List<HelperEntity> owned(Player p) {
        List<HelperEntity> out = new ArrayList<>();
        if (p.getServer() == null) return out;
        for (HelperEntity h : all(p.getServer())) if (h.isOwner(p)) out.add(h);
        return out;
    }

    @Nullable
    public static HelperEntity byUuid(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e instanceof HelperEntity h && h.isAlive()) return h;
        }
        return null;
    }

    /** By network id, looked up in every dimension (the command book works from anywhere). */
    @Nullable
    public static HelperEntity byId(MinecraftServer server, int id, @Nullable UUID uuid) {
        if (uuid != null) {
            HelperEntity h = byUuid(server, uuid);
            if (h != null) return h;
        }
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e instanceof HelperEntity h && h.isAlive()) return h;
        }
        return null;
    }

    // ------------------------------------------------------------------ working far away
    /** Keeps a helper's chunks loaded while it works (or travels) and its owner is online. */
    static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
            net.minecraft.server.level.TicketType.create("helperbuddy", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 60);

    /** Keeps the chunks around a spot loaded for the next 3 seconds (a helper is about to arrive or to be called there). */
    public static void hold(ServerLevel level, net.minecraft.core.BlockPos at) {
        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(at);
        level.getChunkSource().addRegionTicket(TICKET, cp, 3, cp);
    }

    public static void keepLoaded(MinecraftServer server, long ticks) {
        if (ticks % 20 != 0) return;
        HelperIndex index = HelperIndex.get(server);
        boolean keep = com.helperbuddy.config.HBConfig.KEEP_LOADED.get();
        for (HelperEntity h : all(server)) {
            index.note(h);
            if (!keep) continue;
            var m = h.mode();
            // a waiting helper stays where it is and sleeps with its chunk; everything else goes on while its owner plays:
            // work, travel, and following (so it is never left behind in an unloaded chunk when its owner rides or flies off)
            if (m == com.helperbuddy.entity.Mode.STAY) continue;
            if (h.ownerId() == null || server.getPlayerList().getPlayer(h.ownerId()) == null) continue;
            hold((ServerLevel) h.level(), h.blockPosition());
        }
    }
}
