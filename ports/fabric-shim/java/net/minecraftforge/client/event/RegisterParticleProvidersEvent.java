package net.minecraftforge.client.event;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.eventbus.api.Event;

public class RegisterParticleProvidersEvent extends Event {
    /** (vanilla's ParticleEngine.SpriteParticleRegistration is private) */
    @FunctionalInterface
    public interface SpriteParticleRegistration<T extends ParticleOptions> {
        ParticleProvider<T> create(net.minecraft.client.particle.SpriteSet sprites);
    }

    public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, SpriteParticleRegistration<T> registration) {
        ParticleFactoryRegistry.getInstance().register(type, registration::create);
    }

    public <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider) {
        ParticleFactoryRegistry.getInstance().register(type, provider);
    }
}
