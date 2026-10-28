package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CStats;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sends thirst and sanity to the player's HUD when they change. */
public final class Survival {
    private Survival() {}

    private static final Map<UUID, float[]> SENT = new HashMap<>();

    public static void sync(ServerPlayer p) {
        float t = HHConfig.THIRST.get() ? Thirst.get(p) : -1, s = HHConfig.SANITY.get() ? Sanity.get(p) : -1;
        float[] last = SENT.get(p.getUUID());
        if (last != null && Math.abs(last[0] - t) < 0.05f && Math.abs(last[1] - s) < 0.25f) return;
        SENT.put(p.getUUID(), new float[] {t, s});
        HHNet.toPlayer(p, new S2CStats(t, s));
    }

    public static void forceSync(ServerPlayer p) {
        SENT.remove(p.getUUID());
        sync(p);
    }

    public static void forget(ServerPlayer p) {
        SENT.remove(p.getUUID());
    }
}
