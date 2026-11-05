package net.minecraftforge.internal;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/** Forge's Player#setForcedPose (kept by the Player mixin). */
public final class Poses {
    private Poses() {}

    public interface Holder {
        Pose shim$forcedPose();

        void shim$forcePose(Pose pose);
    }

    public static void force(Player p, Pose pose) {
        ((Holder) p).shim$forcePose(pose);
    }

    public static Pose forced(Player p) {
        return ((Holder) p).shim$forcedPose();
    }
}
