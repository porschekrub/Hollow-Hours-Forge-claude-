package net.minecraftforge.internal.mixin;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Explosion.class)
public interface ExplosionAccess {
    @Accessor("x")
    double shim$x();

    @Accessor("y")
    double shim$y();

    @Accessor("z")
    double shim$z();
}
