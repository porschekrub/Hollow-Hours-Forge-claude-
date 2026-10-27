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
}
