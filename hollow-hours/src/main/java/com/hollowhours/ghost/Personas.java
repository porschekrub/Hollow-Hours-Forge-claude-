package com.hollowhours.ghost;

import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * What makes each ghost different (port of variety.js PERSONA + main.js GIMMICK).
 * 1 Wailing Dweller: hunts by sound. 2 Sunken Widow: never lets go, drowns you slowly. 3 Ashen Screamer: bursts of speed, burns.
 * 4 Smiling Void: warps behind you, flinches from your flashlight. 5 Antlered Hunger: freezes when watched far away, lunges up close.
 */
public final class Personas {
    private Personas() {}

    /** Behaviour while stalking (chasing=false) or chasing (true). Sets b.spdMul. */
    static void tick(GhostBrain b, ServerPlayer best, double bestD, long now, boolean chasing) {
        switch (b.kind.n) {
            case 1 -> wail(b, best, bestD, now, chasing);
            case 2 -> widow(b, best, bestD, now, chasing);
            case 3 -> ash(b, best, bestD, now, chasing);
            case 4 -> voidG(b, best, bestD, now, chasing);
            case 5 -> stag(b, best, bestD, chasing);
            default -> b.spdMul = 1;
        }
    }

    private static void wail(GhostBrain b, ServerPlayer best, double bestD, long now, boolean chasing) {
        boolean sprint = best.isSprinting(), sneak = best.isShiftKeyDown();
        if (!chasing && b.stalking && bestD < 48) {
            if (sprint) {
                b.stalkFor = Math.max(0, b.stalkFor - 70);
                if (R.chance(0.3)) Fx.playAt(best, "ghost.creak", b.g.getX(), b.g.getY(), b.g.getZ(), 1.4f, 0.7f);
            }
            if (now >= b.timer("lure") && bestD < 40) {
                b.setTimer("lure", now + (long) R.rand(200, 420));
                double a = Math.atan2(b.g.getZ() - best.getZ(), b.g.getX() - best.getX()) + Math.PI + R.rand(-0.8, 0.8);
                double d = R.rand(5, 11);
                Fx.playAt(best, R.pick("ghost.growl", "mob.ghast.moan", "ghost.creak"), best.getX() + Math.cos(a) * d, best.getY(), best.getZ() + Math.sin(a) * d, 1.6f, R.pitch(0.5, 0.9));
            }
        }
        if (chasing) {
            if (sprint && bestD < 45) b.lastDetect = now;      // running = it always knows where you are
            if (sneak && bestD < 9) b.lastDetect = now;        // too close: it hears your breath
            if (bestD < 12 && now >= b.timer("scream")) {
                b.setTimer("scream", now + 320);
                Fx.playAt(best, "mob.ghast.scream", b.g.getX(), b.g.getY(), b.g.getZ(), 2f, 0.6f);
                Fx.effect(best, MobEffects.BLINDNESS, 30, 0);
                Fx.effect(best, MobEffects.MOVEMENT_SLOWDOWN, 20, 0);
            }
        }
        b.spdMul = sprint ? 1.3 : sneak ? 0.95 : 1.1;
    }

    private static void widow(GhostBrain b, ServerPlayer best, double bestD, long now, boolean chasing) {
        b.spdMul = 1;
        if (chasing) {
            if (best.isInWater() || b.g.isInWater()) b.spdMul = 1.35;
            if (bestD < 9 && now >= b.timer("drag")) {
                b.setTimer("drag", now + 100);
                Fx.effect(best, MobEffects.WEAKNESS, 80, 0);
                Fx.effect(best, MobEffects.MOVEMENT_SLOWDOWN, 50, 0);
                Fx.playAt(best, "liquid.water", best.getX(), best.getY(), best.getZ(), 1.4f, 0.6f);
            }
            if (bestD > 24 && now >= b.timer("tide")) {               // ran too far: the water brings her back behind you
                b.setTimer("tide", now + 260);
                Vec3 spot = Director.findSpot(best, true, 11, 15);
                if (spot != null) {
                    b.teleportFacing(spot, best.getEyePosition());
                    Fx.playAt(best, "bubble.up", spot.x, spot.y, spot.z, 2f, 0.6f);
                }
            }
        } else if (b.stalking && bestD < 24 && R.chance(0.05)) {
            Fx.playAt(best, "mob.ghast.moan", b.g.getX(), b.g.getY(), b.g.getZ(), 1.8f, 0.45f);
        }
    }

    private static void ash(GhostBrain b, ServerPlayer best, double bestD, long now, boolean chasing) {
        if (!chasing) {
            b.spdMul = 1;
            return;
        }
        boolean on = b.vals.getOrDefault("burst", 0.0) > 0;
        if (now >= b.timer("burstUntil")) {
            on = !on;
            b.vals.put("burst", on ? 1.0 : 0.0);
            b.setTimer("burstUntil", now + (on ? 50 : 30));
            if (on) Fx.playAt(best, "mob.blaze.shoot", b.g.getX(), b.g.getY(), b.g.getZ(), 1.8f, 0.7f);
        }
        b.spdMul = on ? 1.9 : 0.85;
        if (on) b.lastDetect = now;
        if (bestD < 5) best.setSecondsOnFire(3);
    }

    private static void voidG(GhostBrain b, ServerPlayer best, double bestD, long now, boolean chasing) {
        if (!chasing) {
            b.spdMul = 1;
            return;
        }
        if (now >= b.timer("wobble")) {
            b.setTimer("wobble", now + (long) R.rand(25, 70));
            b.spdMul = R.rand(0.8, 1.6);
        }
        double dot = W.lookDot(best, b.g.position());
        if (bestD > 7 && bestD < 40 && dot < 0.2 && now >= b.timer("blink")) {   // not looking: it is suddenly behind you
            b.setTimer("blink", now + (long) R.rand(120, 220));
            Vec3 spot = Director.findSpot(best, true, 4, 6);
            if (spot != null) {
                b.teleportFacing(spot, best.getEyePosition());
                Fx.playAt(best, "mob.endermen.portal", spot.x, spot.y, spot.z, 1.6f, 0.6f);
                b.setTimer("wobble", now + 60);
                b.spdMul = 1.5;
            }
        }
        if (bestD < 14 && now >= b.timer("flinch") && dot > 0.9 && Flashlight.isOn(best)) {   // flashlight in its face
            b.setTimer("flinch", now + 300);
            Vec3 spot = Director.findSpot(best, false, 16, 22);
            if (spot != null) {
                b.teleportFacing(spot, best.getEyePosition());
                Fx.playAt(best, "mob.witch.ambient", spot.x, spot.y, spot.z, 2f, 0.5f);
                b.setTimer("wobble", now + 70);
                b.spdMul = 1.7;
            }
        }
    }

    private static void stag(GhostBrain b, ServerPlayer best, double bestD, boolean chasing) {
        if (!chasing) {
            b.spdMul = 1;
            return;
        }
        double dot = W.lookDot(best, b.g.position());
        if (dot > 0.8 && bestD < 40) b.spdMul = bestD > 6 ? 0.02 : 0.4;
        else b.spdMul = 1.2;
    }

    // ------------------------------------------------------------------ signature effects (every 5 ticks)
    static void gimmick(GhostBrain b, ServerPlayer best, double bestD, long now, boolean chasing) {
        var g = b.g;
        var level = g.level();
        switch (b.kind.n) {
            case 1 -> {
                if (!chasing && b.stalking && bestD <= 40 && R.chance(0.05)) Director.mimicSound(best);
                if (chasing && R.chance(0.02)) {
                    Fx.playAt(best, "ghost.growl", g.getX(), g.getY(), g.getZ(), 2.5f, 1.6f);
                    Fx.effect(best, MobEffects.BLINDNESS, 25, 0);
                    Fx.shake(best, 0.5f, 10);
                }
            }
            case 2 -> {
                if (R.chance(0.7)) Fx.puff(level, ParticleTypes.SPLASH, g.position(), 0.6);
                if (R.chance(0.3)) Fx.puff(level, ParticleTypes.DRIPPING_WATER, g.position(), 0.5);
                if (bestD <= 24 && R.chance(0.08)) Fx.playAt(best, R.chance(0.5) ? "bubble.up" : "liquid.water", g.getX(), g.getY(), g.getZ(), 1.5f, R.pitch(0.6, 1));
                if (chasing && bestD <= 10) {
                    Fx.effect(best, MobEffects.MOVEMENT_SLOWDOWN, 30, 0);
                    Fx.effect(best, MobEffects.DIG_SLOWDOWN, 60, 0);
                }
            }
            case 3 -> {
                Fx.puff(level, ParticleTypes.FLAME, g.position(), 0.5);
                if (R.chance(0.3)) Fx.puff(level, ParticleTypes.LAVA, g.position(), 0.5);
                if (R.chance(0.4)) Fx.puff(level, ParticleTypes.LARGE_SMOKE, g.position(), 0.2);
                if (R.chance(0.12)) Fx.playAt(best, "fire.fire", g.getX(), g.getY(), g.getZ(), 1.5f, R.pitch(0.6, 1.0));
                if (R.chance(0.03)) Fx.playAt(best, "mob.blaze.breathe", g.getX(), g.getY(), g.getZ(), 1.5f, 0.5f);
                if (chasing && bestD <= 3.5) best.setSecondsOnFire(4);
            }
            case 4 -> {
                if (!chasing && b.stalking && R.chance(0.2)) {          // the image twitches
                    double x = g.getX() + R.rand(-1.2, 1.2), z = g.getZ() + R.rand(-1.2, 1.2);
                    int y = Mth.floor(g.getY());
                    if (W.passable(level, x, y, z) && W.passable(level, x, y + 1, z)) b.teleportFacing(new Vec3(x, y, z), best.getEyePosition());
                }
                if (chasing && now >= b.timer("nextBlink") && bestD > 6 && !W.isSheltered(best)) {
                    b.setTimer("nextBlink", now + (long) R.rand(100, 220));
                    if (W.lookDot(best, g.position()) < 0.3) {
                        Vec3 spot = Director.findSpot(best, true, 5, 8);
                        if (spot != null) {
                            b.teleportFacing(spot, best.getEyePosition());
                            Fx.playAt(best, "mob.witch.ambient", spot.x, spot.y, spot.z, 2f, 0.6f);
                        }
                    }
                }
            }
            case 5 -> {
                if (!chasing && b.stalking) {
                    if (W.lookDot(best, g.position()) > 0.85 && bestD < 24) {
                        double lt = b.vals.getOrDefault("lookTicks", 0.0) + 1;
                        b.vals.put("lookTicks", lt);
                        if (lt >= 8) {                                    // never stare at it for 2 seconds
                            b.snap(best);
                            return;
                        }
                    } else b.vals.put("lookTicks", 0.0);
                    if (bestD <= 14 && R.chance(0.1)) Fx.playAt(best, "mob.skeleton.step", g.getX(), g.getY(), g.getZ(), 1.5f, 0.6f);
                }
                if (bestD <= 14 && R.chance(0.05)) {
                    Fx.playSelf(best, "random.fizz", 0.8f, 1);
                    Fx.effect(best, MobEffects.DARKNESS, 80, 1);
                }
            }
            default -> {}
        }
    }
}
