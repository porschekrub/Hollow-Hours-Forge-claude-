package com.veritypal.world;

import com.veritypal.V;
import com.veritypal.config.PalConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Something standing far away, not moving, staring at a player for a few seconds: Verity notices and says so. */
public final class Watchers {
    private Watchers() {}

    static final class Stare {
        int seconds;
        Vec3 at;
    }

    static final Map<UUID, Stare> STARES = new HashMap<>();
    static final Map<UUID, Long> TOLD = new HashMap<>();

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || !PalConfig.on(PalConfig.WARN_WATCHERS)) return;
        Mob b = V.body();
        for (ServerPlayer p : V.listeners(server)) {
            long told = TOLD.getOrDefault(p.getUUID(), 0L);
            if (System.currentTimeMillis() - told < 240_000) continue;
            Vec3 eye = p.getEyePosition();
            for (Entity e : p.level().getEntities(p, new AABB(p.blockPosition()).inflate(48), x -> watcher(x, p))) {
                LivingEntity le = (LivingEntity) e;
                Vec3 look = le.getViewVector(1);
                Vec3 to = eye.subtract(le.getEyePosition()).normalize();
                boolean staring = look.dot(to) > 0.97 && le.hasLineOfSight(p) && le.distanceTo(p) > 10;
                Stare s = STARES.computeIfAbsent(e.getUUID(), k -> new Stare());
                if (!staring || s.at != null && e.position().distanceTo(s.at) > 1.5) {
                    s.seconds = 0;
                    s.at = e.position();
                    continue;
                }
                if (s.at == null) s.at = e.position();
                if (++s.seconds < 3) continue;
                s.seconds = 0;
                TOLD.put(p.getUUID(), System.currentTimeMillis());
                double br = Places.bearing(e.getX() - p.getX(), e.getZ() - p.getZ());
                String what = e.getName().getString() + Senses.modOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
                int dist = (int) e.distanceTo(p);
                if (V.aiReady())
                    V.react(p, "You notice a " + what + " standing still about " + dist + " blocks to the " + Places.compass(br, false) + " (" + Places.relative(br, p.getYRot())
                            + "), staring at " + p.getGameProfile().getName() + ". Warn them in character (one short line, creeped out).");
                else
                    V.say(server, "เฮ้ย... มี" + e.getName().getString() + "ยืนจ้องนายอยู่ทาง" + Places.compass(br, true) + " ห่างไป " + dist + " บล็อก ขนลุกเลย",
                            "Psst... a " + e.getName().getString() + " is staring at you, " + dist + " blocks " + Places.compass(br, false) + ". Creepy");
                if (b != null) V.face("serious_3");
                break;
            }
        }
        if (server.getTickCount() % 1200 == 0) STARES.keySet().removeIf(id -> server.overworld().getEntity(id) == null);
    }

    static boolean watcher(Entity e, Player p) {
        if (!(e instanceof LivingEntity) || !e.isAlive() || e instanceof Player || V.isVerity(e) || e instanceof Animal || e instanceof AbstractVillager || e instanceof EnderMan) return false;
        String ns = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace();
        // the game's own monsters look at players all the time: only other mods' creatures (and the scary ones)
        return !ns.equals("minecraft") && !ns.equals("helperbuddy") && !ns.equals("verity") || Hide.scary(e);
    }
}
