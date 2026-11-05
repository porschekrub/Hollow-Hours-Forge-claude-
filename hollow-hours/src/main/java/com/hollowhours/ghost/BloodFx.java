package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.StalkerEntity;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CBlood;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Blood: every hit sprays blood (in the creature's own colour) away from whoever hit it, it splashes onto the ground
 * and stays there for a while; badly hurt creatures leave drips. Particles are made on the client (S2CBlood).
 */
public final class BloodFx {
    private BloodFx() {}

    /** 0xRRGGBB per creature; -1 = it doesn't bleed. */
    static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("zombie", 0x5A1A0C), Map.entry("zombie_villager", 0x5A1A0C), Map.entry("husk", 0x6B4A1E), Map.entry("drowned", 0x2F5B4A),
            Map.entry("zombified_piglin", 0x7A3A2A), Map.entry("zoglin", 0x6A2A1A),
            Map.entry("skeleton", 0xE8E4D6), Map.entry("stray", 0xD8E4EC), Map.entry("wither_skeleton", 0x2A2A2A), Map.entry("skeleton_horse", 0xE8E4D6),
            Map.entry("creeper", 0x3DAA3D), Map.entry("spider", 0x5E7A1E), Map.entry("cave_spider", 0x2E6A6A), Map.entry("silverfish", 0x8A8A8A),
            Map.entry("enderman", 0x9A33CC), Map.entry("endermite", 0x9A33CC), Map.entry("shulker", 0xB57AB5), Map.entry("ender_dragon", 0x9A33CC),
            Map.entry("slime", 0x6BD66B), Map.entry("magma_cube", 0xFF7A1A), Map.entry("blaze", 0xFFB21A), Map.entry("ghast", 0xEDEDF2),
            Map.entry("phantom", 0x5A7AB8), Map.entry("vex", 0xAFC8E8), Map.entry("allay", 0x7AD4F0), Map.entry("bee", 0xE8C53A),
            Map.entry("squid", 0x111122), Map.entry("glow_squid", 0x2AE0C8), Map.entry("guardian", 0x4A9A8A), Map.entry("elder_guardian", 0x4A9A8A),
            Map.entry("wither", 0x151515), Map.entry("warden", 0x0E4A55), Map.entry("strider", 0xB8442A), Map.entry("axolotl", 0xE87AA0),
            Map.entry("frog", 0x8A3020), Map.entry("snow_golem", -1), Map.entry("iron_golem", -1), Map.entry("armor_stand", -1));

    public static int color(LivingEntity e) {
        if (e instanceof ArmorStand) return -1;
        if (e instanceof GhostEntity || e instanceof StalkerEntity) return 0x120404;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        Integer c = COLORS.get(id);
        if (c != null) return c;
        if (e.getMobType() == MobType.UNDEAD) return 0x5A1A0C;
        if (e.getMobType() == MobType.ARTHROPOD) return 0x5E7A1E;
        return 0x8A0606;
    }

    /** A hit: blood flies away from the attacker. */
    public static void hurt(LivingEntity e, DamageSource src, float amount) {
        if (!HHConfig.BLOOD.get() || amount <= 0 || e.level().isClientSide) return;
        int col = color(e);
        if (col < 0) return;
        Vec3 dir = Vec3.ZERO;
        Entity by = src.getDirectEntity() != null ? src.getDirectEntity() : src.getEntity();
        if (by != null) dir = e.position().subtract(by.position()).multiply(1, 0, 1).normalize();
        int count = (int) Math.max(6, Math.min(40, amount * 4));
        double y = e.getY() + e.getBbHeight() * 0.65;
        send(e, new S2CBlood(e.getX(), y, e.getZ(), (float) dir.x, (float) dir.z, count, col, S2CBlood.BURST, e.getBbWidth()));
    }

    /** Low health: a few drops fall off it (every 15 ticks). */
    public static void drip(LivingEntity e) {
        if (!HHConfig.BLOOD.get() || e.isDeadOrDying() || e.getHealth() > e.getMaxHealth() * 0.3f) return;
        int col = color(e);
        if (col < 0) return;
        double y = e.getY() + e.getBbHeight() * 0.5;
        send(e, new S2CBlood(e.getX(), y, e.getZ(), 0, 0, e.getHealth() < e.getMaxHealth() * 0.15f ? 3 : 1, col, S2CBlood.DRIP, e.getBbWidth()));
    }

    static void send(Entity e, S2CBlood m) {
        HHNet.toTracking(e, m, true);
    }
}
