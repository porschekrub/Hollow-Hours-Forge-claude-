package com.hollowhours.client;

import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * How scared each player is right now (0..1), worked out on the client from what it can see: ghosts nearby (more
 * when one is running at you), being held, a jumpscare, the blood moon, fog, night, low health.
 * Drives the scared player animations, the first-person sway and the horror shader.
 */
public final class Fear {
    private Fear() {}

    /** CI screenshots: a fixed fear level (negative = off). */
    public static float force = -1;
    static float local, localO;
    private static final Map<Integer, Float> OTHERS = new HashMap<>();

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            local = localO = 0;
            OTHERS.clear();
            return;
        }
        localO = local;
        float t = target(mc.player);
        if (KillCam.active() || Jumpscare.active()) t = 1;
        local += (t - local) * (t > local ? 0.25f : 0.04f);
        OTHERS.clear();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            OTHERS.put(p.getId(), target(p));
        }
    }

    static float target(Player p) {
        Minecraft mc = Minecraft.getInstance();
        float partial = 1;
        float base = 0.1f + 0.12f * Atmos.night(mc.level, partial) + 0.12f * Atmos.fog(partial) + 0.3f * Atmos.blood(partial);
        float hp = p.getHealth() / Math.max(1, p.getMaxHealth());
        if (hp < 0.4f) base += 0.3f * (1 - hp / 0.4f);
        float ghost = 0;
        for (GhostEntity g : mc.level.getEntitiesOfClass(GhostEntity.class, p.getBoundingBox().inflate(48))) {
            if (g.isInvisible()) continue;
            float d = (float) g.distanceTo(p);
            float k = Mth.clamp(1 - (d - 4) / 40f, 0, 1);
            GhostAnim a = g.anim();
            if (a == GhostAnim.CHASE || a == GhostAnim.FOLLOW_RUN || a == GhostAnim.CRAWL_CHASE || a == GhostAnim.BASH || a == GhostAnim.LUNGE) k *= 1.4f;
            ghost = Math.max(ghost, k);
        }
        if (p.getVehicle() instanceof GhostEntity) ghost = 1;
        return Mth.clamp(Math.max(base, ghost), 0, 1);
    }

    /** The local player's fear, smoothed. */
    public static float local(float partial) {
        if (force >= 0) return force;
        return Mth.lerp(partial, localO, local);
    }

    public static float of(Entity e, float partial) {
        if (force >= 0) return force;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player) return local(partial);
        return OTHERS.getOrDefault(e.getId(), 0.15f);
    }
}
