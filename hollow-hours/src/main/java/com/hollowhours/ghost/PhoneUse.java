package com.hollowhours.ghost;

import com.hollowhours.item.PhoneItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Phone screen time, from the client's phone screen: while it is open the battery drains (faster while gaming, and
 * faster the more worn the battery is), and a game calms you down (sanity).
 */
public final class PhoneUse {
    private PhoneUse() {}

    public static final int OPEN = 0, CLOSE = 1, GAME = 2, BEAT = 3;

    private static final class Use {
        long beat, game;
    }

    private static final Map<UUID, Use> USE = new HashMap<>();

    public static void action(ServerPlayer p, int action) {
        long now = p.serverLevel().getGameTime();
        if (action == CLOSE) {
            USE.remove(p.getUUID());
            return;
        }
        Use u = USE.computeIfAbsent(p.getUUID(), k -> new Use());
        u.beat = now;
        if (action == GAME) u.game = now;
    }

    public static boolean using(ServerPlayer p) {
        return USE.containsKey(p.getUUID());
    }

    /** Every 20 ticks. */
    public static void tick(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, Use>> it = USE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Use> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Use u = e.getValue();
            if (p == null || now - u.beat > 100) {
                it.remove();
                continue;
            }
            ItemStack phone = PhoneItem.find(p, true);
            if (phone == null) continue;
            boolean gaming = now - u.game < 45;
            float drain = (0.15f + (gaming ? 0.45f : 0)) * PhoneItem.drainMult(phone);
            PhoneItem.setBattery(phone, PhoneItem.battery(phone) - drain);
            PhoneItem.addWear(phone, 1 / 60f);
            if (gaming) Sanity.add(p, 0.6f);
        }
    }

    public static void forget(ServerPlayer p) {
        USE.remove(p.getUUID());
    }
}
