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
 * faster the more worn the battery is), and a game calms you down (sanity). The arcade machine runs the same games
 * without a battery, as long as you stand at it.
 */
public final class PhoneUse {
    private PhoneUse() {}

    public static final int OPEN = 0, CLOSE = 1, GAME = 2, BEAT = 3, ARCADE = 4, ARCADE_GAME = 5;

    private static final class Use {
        long beat, game;
        boolean arcade;
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
        u.arcade = action == ARCADE || action == ARCADE_GAME;
        if (action == GAME || action == ARCADE_GAME) u.game = now;
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
            boolean gaming = now - u.game < 45;
            if (u.arcade) {
                if (gaming && nearArcade(p)) Sanity.add(p, 0.75f);
                continue;
            }
            ItemStack phone = PhoneItem.find(p, true);
            if (phone == null) continue;
            // per second: screen on ~30 minutes per charge, games ~12 minutes (more as the battery wears)
            float drain = (0.055f + (gaming ? 0.085f : 0)) * PhoneItem.drainMult(phone) * com.hollowhours.config.HHConfig.PHONE_DRAIN.get().floatValue();
            PhoneItem.setBattery(phone, PhoneItem.battery(phone) - drain);
            PhoneItem.addWear(phone, 1 / 60f);
            if (gaming) Sanity.add(p, 0.6f);
        }
    }

    static boolean nearArcade(ServerPlayer p) {
        net.minecraft.world.level.block.Block arcade = com.hollowhours.registry.HHBlocks.ARCADE.get();
        for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().offset(-4, -2, -4), p.blockPosition().offset(4, 3, 4)))
            if (p.level().getBlockState(b).is(arcade)) return true;
        return false;
    }

    public static void forget(ServerPlayer p) {
        USE.remove(p.getUUID());
    }
}
