package net.minecraftforge.internal;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

public final class Explosions {
    private Explosions() {}

    /** Forge's Explosion#getPosition. */
    public static Vec3 center(Explosion ex) {
        var a = (net.minecraftforge.internal.mixin.ExplosionAccess) ex;
        return new Vec3(a.shim$x(), a.shim$y(), a.shim$z());
    }
}
