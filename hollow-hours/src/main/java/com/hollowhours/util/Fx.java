package com.hollowhours.util;

import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CScreenFx;
import com.hollowhours.registry.HHSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Server -> player feedback: sounds, titles, action bar, effects, particles, screen effects. */
public final class Fx {
    private Fx() {}

    // ------------------------------------------------------------------ sounds
    /** Plays a sound that only this player hears, coming from (x, y, z). Like "playsound ... @s x y z". */
    public static void playAt(ServerPlayer p, String sound, double x, double y, double z, float vol, float pitch) {
        playAt(p, HHSounds.get(sound), x, y, z, vol, pitch);
    }

    public static void playAt(ServerPlayer p, SoundEvent se, double x, double y, double z, float vol, float pitch) {
        if (p == null || p.connection == null) return;
        p.connection.send(new ClientboundSoundPacket(Holder.direct(se), SoundSource.HOSTILE, x, y, z, vol, pitch, p.getRandom().nextLong()));
    }

    public static void playAt(ServerPlayer p, String sound, Vec3 at, float vol, float pitch) {
        playAt(p, sound, at.x, at.y, at.z, vol, pitch);
    }

    /** Sound at the player's own position (only they hear it). */
    public static void playSelf(ServerPlayer p, String sound, float vol, float pitch) {
        playAt(p, sound, p.getX(), p.getY() + 1, p.getZ(), vol, pitch);
    }

    /** Everyone within `range` hears it from `at`. */
    public static void sound(Level level, String sound, Vec3 at, float vol, float pitch, double range) {
        if (!(level instanceof ServerLevel sl)) return;
        SoundEvent se = HHSounds.get(sound);
        for (ServerPlayer p : sl.players()) {
            if (p.position().distanceToSqr(at) <= range * range) playAt(p, se, at.x, at.y, at.z, vol, pitch);
        }
    }

    // ------------------------------------------------------------------ text
    public static void actionbar(ServerPlayer p, Component c) {
        if (p != null && p.connection != null) p.connection.send(new ClientboundSetActionBarTextPacket(c));
    }

    public static void actionbarKey(ServerPlayer p, String key, Object... args) {
        actionbar(p, Component.translatable(key, args));
    }

    public static void title(ServerPlayer p, Component title, Component sub, int in, int stay, int out) {
        if (p == null || p.connection == null) return;
        p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub == null ? Component.empty() : sub));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void msg(ServerPlayer p, Component c) {
        if (p != null) p.sendSystemMessage(c);
    }

    // ------------------------------------------------------------------ effects
    public static void effect(ServerPlayer p, MobEffect e, int ticks, int amp) {
        if (p != null && ticks > 0) p.addEffect(new MobEffectInstance(e, ticks, Math.max(0, amp), false, false, false));
    }

    // ------------------------------------------------------------------ particles
    public static void particles(Level level, ParticleOptions type, double x, double y, double z, int count, double spread, double speed) {
        if (level instanceof ServerLevel sl) sl.sendParticles(type, x, y, z, count, spread, spread, spread, speed);
    }

    public static void puff(Level level, ParticleOptions type, Vec3 at, double spread) {
        particles(level, type, at.x + R.rand(-spread, spread), at.y + R.rand(0.2, 3.5), at.z + R.rand(-spread, spread), 1, 0, 0);
    }

    // ------------------------------------------------------------------ screen effects (handled by the client)
    public static void shake(ServerPlayer p, float strength, int ticks) {
        HHNet.toPlayer(p, S2CScreenFx.shake(strength, ticks));
    }

    public static void fade(ServerPlayer p, int rgb, int in, int hold, int out) {
        HHNet.toPlayer(p, S2CScreenFx.fade(rgb, in, hold, out));
    }

    /** Shake everyone near a point (strength falls off with distance). */
    public static void shakeNear(Level level, Vec3 at, double radius, float strength, int ticks) {
        if (!(level instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.players()) {
            double d = p.position().distanceTo(at);
            if (d > radius) continue;
            float k = (float) (1 - Math.min(1, d / radius));
            shake(p, Math.max(0.05f, strength * (0.35f + 0.65f * k)), ticks);
        }
    }
}
