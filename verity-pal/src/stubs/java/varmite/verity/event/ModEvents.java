package varmite.verity.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** Stand-in (see README.md). */
public class ModEvents {
    public static void updateAndSyncKarma(ServerLevel level, float amount) {}

    public static void setAndSyncKarma(ServerLevel level, float amount) {}

    public static boolean hasVerityInInventory(Player player) {
        throw new AssertionError();
    }

    public static void schedule(Runnable action, int delayInTicks) {}
}
