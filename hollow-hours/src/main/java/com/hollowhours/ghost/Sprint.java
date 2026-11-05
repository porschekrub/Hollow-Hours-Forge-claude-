package com.hollowhours.ghost;

import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CHud;
import com.hollowhours.util.Fx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * All-out sprint (port of sprint.js): while running, tap sneak twice = dash much faster (tap twice again = stop).
 * Full stamina lasts 10 s and refills in 15 s. Running out = out of breath: no running at all (not even a normal
 * sprint) until stamina is back to 15%. Dashing is LOUD.
 */
public final class Sprint {
    private Sprint() {}

    static final double DRAIN = 1 / 200.0, REFILL = 1 / 300.0, MIN_START = 0.15, BREATH_BACK = 0.15;
    static final int TAP_WINDOW = 12, SPRINT_MEMORY = 16;

    static final class S {
        double sta = 1;
        boolean turbo, sneak, hudOn, outOfBreath;
        long lastPress = -99, lastSprint = -99, nextPant, panting, exhausted, nextFx;
        int slow;
        double lx = Double.NaN, lz;
    }

    private static final Map<UUID, S> STATES = new HashMap<>();

    static S st(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new S());
    }

    public static boolean isDashing(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        return s != null && s.turbo;
    }

    /** Takes stamina for a move (a dodge); false when there isn't enough. */
    public static boolean spend(ServerPlayer p, double amount) {
        S s = st(p);
        if (s.outOfBreath || s.sta < amount) return false;
        s.sta -= amount;
        return true;
    }

    /** Value for the HUD bar: -1 hides it. */
    public static float staminaHudValue(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        if (s == null || (!s.turbo && s.sta >= 1)) return -1;
        return (float) s.sta;
    }

    public static void tick(ServerPlayer p, long now) {
        S s = st(p);
        boolean sneak = p.isShiftKeyDown(), sprint = p.isSprinting();
        double sp = Double.isNaN(s.lx) ? 0 : Math.hypot(p.getX() - s.lx, p.getZ() - s.lz);
        s.lx = p.getX();
        s.lz = p.getZ();
        if (sprint) s.lastSprint = now;
        boolean hidden = Hiding.isHidden(p) || KillSequence.isVictim(p);
        if (sneak && !s.sneak) {
            if (now - s.lastPress <= TAP_WINDOW) {
                s.lastPress = -99;
                if (s.turbo) stop(p, s, now, false);
                else if (!hidden && (now - s.lastSprint <= SPRINT_MEMORY || sp > 0.2)) start(p, s, now);
            } else s.lastPress = now;
        }
        s.sneak = sneak;
        if (s.turbo) {
            if (hidden) {
                stop(p, s, now, false);
                return;
            }
            s.sta = Math.max(0, s.sta - DRAIN);
            if (now % 10 == 0) Fx.effect(p, MobEffects.MOVEMENT_SPEED, 16, 2);
            if (now >= s.nextFx) {
                s.nextFx = now + 10;
                Perception.impulse(p, p.position(), 26);
            }
            if (s.sta < 0.3 && now >= s.nextPant) {
                s.nextPant = now + 30;
                Fx.sound(p.level(), "ghost.pant", p.position(), (float) (0.6 + (0.3 - s.sta) * 2), 1.05f, 10);
            }
            s.slow = sp < 0.1 && !sneak ? s.slow + 1 : 0;
            if (s.sta <= 0) stop(p, s, now, true);
            else if (s.slow >= 10) stop(p, s, now, false);
        } else if (s.sta < 1) {
            s.sta = Math.min(1, s.sta + REFILL);
            if (s.outOfBreath) {
                if (s.sta >= BREATH_BACK) {
                    s.outOfBreath = false;
                    Fx.actionbar(p, Component.translatable("hollowhours.sprint.again"));
                } else if (p.isSprinting()) p.setSprinting(false);
            }
            if (now < s.panting && now >= s.nextPant) {
                s.nextPant = now + 44;
                Fx.sound(p.level(), "ghost.pant.heavy", p.position(), 0.7f, 1f, 12);
            }
        }
        if (s.turbo || s.sta < 1) {
            s.hudOn = true;
            if (now % 4 == 0 && !hidden) sendHud(p, s, now);
        } else if (s.hudOn) {
            s.hudOn = false;
            if (!hidden) {
                sendHud(p, s, now);
                Fx.actionbar(p, Component.translatable("hollowhours.sprint.ready"));
            }
        }
    }

    static void sendHud(ServerPlayer p, S s, long now) {
        HHNet.toPlayer(p, new S2CHud(0, 0, -1, 0, s.turbo || s.sta < 1 ? (float) s.sta : -1, s.turbo, s.outOfBreath || s.exhausted > now, Soul.get(p)));
    }

    static void start(ServerPlayer p, S s, long now) {
        if (s.sta < MIN_START) {
            Fx.actionbar(p, Component.translatable("hollowhours.sprint.tired"));
            Fx.sound(p.level(), "ghost.pant", p.position(), 0.8f, 1f, 10);
            return;
        }
        s.turbo = true;
        s.slow = 0;
        s.nextFx = now;
        Fx.sound(p.level(), "ghost.dash", p.position(), 1f, 1f, 10);
    }

    static void stop(ServerPlayer p, S s, long now, boolean empty) {
        s.turbo = false;
        if (empty) {
            s.outOfBreath = true;
            s.exhausted = now + 50;
            Fx.effect(p, MobEffects.MOVEMENT_SLOWDOWN, 50, 1);
            Fx.sound(p.level(), "ghost.pant.heavy", p.position(), 1f, 1f, 12);
            s.panting = now + 70;
            Fx.shake(p, 0.15f, 24);
        }
    }

    public static void onRespawn(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        if (s != null) {
            s.turbo = false;
            s.outOfBreath = false;
            s.sta = 1;
        }
    }

    public static void forget(ServerPlayer p) {
        STATES.remove(p.getUUID());
    }
}
