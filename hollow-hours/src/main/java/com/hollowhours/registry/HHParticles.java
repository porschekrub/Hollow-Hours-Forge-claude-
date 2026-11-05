package com.hollowhours.registry;

import com.hollowhours.HollowHours;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class HHParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, HollowHours.MODID);
    /** Blood drops (their sprites); the coloured drops themselves are made by the client from S2CBlood. */
    public static final Supplier<SimpleParticleType> BLOOD = PARTICLES.register("blood", () -> new SimpleParticleType(false));

    private HHParticles() {}
}
