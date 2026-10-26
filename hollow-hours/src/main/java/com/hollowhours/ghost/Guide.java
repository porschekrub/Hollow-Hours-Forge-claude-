package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CGuide;
import com.hollowhours.registry.HHItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Sends the ghost guide's live status to the client, which opens the book screen. */
public final class Guide {
    private Guide() {}

    public static void open(ServerPlayer p, int page) {
        float[] awake = new float[GhostKind.values().length];
        java.util.Arrays.fill(awake, -1);
        GhostEntity nearest = null;
        double nd = Double.MAX_VALUE;
        for (GhostEntity g : Director.allGhosts(p.server)) {
            int i = g.kind.n - 1;
            if (i < 0 || i >= awake.length) continue;
            if (g.level() != p.level()) {
                if (awake[i] == -1) awake[i] = -2;
                continue;
            }
            float d = (float) g.position().distanceTo(p.position());
            if (awake[i] < 0 || d < awake[i]) awake[i] = d;
            if (d < nd) {
                nd = d;
                nearest = g;
            }
        }
        ItemStack flash = ItemStack.EMPTY;
        InteractionHand h = Flashlight.heldHand(p);
        if (h != null) flash = p.getItemInHand(h);
        else for (ItemStack s : p.getInventory().items) if (s.is(HHItems.FLASHLIGHT.get())) {
            flash = s;
            break;
        }
        boolean has = !flash.isEmpty();
        HHNet.toPlayer(p, new S2CGuide(page, Soul.get(p), awake, has ? Flashlight.charge(flash) : 0, Flashlight.FULL, has && Flashlight.on(flash), has,
                Flashlight.batteries(p), Curses.asksLeft(p), HHConfig.ASK_LIMIT.get(), nearest == null ? 0 : nearest.kind.n));
    }
}
