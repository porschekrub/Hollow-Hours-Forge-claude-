package com.hollowhours.combat;

import com.hollowhours.config.HHConfig;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CCombat;
import com.hollowhours.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Strength (0-100) and weapon skill, kept through death.
 * Strength comes from sport on the playground (baskets, goals, the trampoline, the swing), from running and from
 * wriggling free of ghosts. It gives more health, harder hits, a little speed and easier struggles.
 * Weapon skill (melee / guns, level 0-10) comes from hitting things with them: more damage, faster reloads, steadier aim.
 */
public final class Fitness {
    private Fitness() {}

    static final String STR = "hollowhours_strength", MEL = "hollowhours_xp_melee", GUN = "hollowhours_xp_gun";
    static final UUID HP = UUID.fromString("6f1c2a3e-1b7d-4f0a-9a51-3c2b8e7d1a01");
    static final UUID DMG = UUID.fromString("6f1c2a3e-1b7d-4f0a-9a51-3c2b8e7d1a02");
    static final UUID SPD = UUID.fromString("6f1c2a3e-1b7d-4f0a-9a51-3c2b8e7d1a03");
    public static final int MAX_LEVEL = 10;

    static CompoundTag keep(Player p) {
        return p.getPersistentData().getCompound("PlayerPersisted");
    }

    static void save(Player p, CompoundTag k) {
        p.getPersistentData().put("PlayerPersisted", k);
    }

    public static float strength(Player p) {
        return keep(p).getFloat(STR);
    }

    public static void setStrength(ServerPlayer p, float v) {
        CompoundTag k = keep(p);
        k.putFloat(STR, Math.max(0, Math.min(100, v)));
        save(p, k);
        apply(p);
        sync(p);
    }

    /** More strength; it gets harder the stronger you already are. */
    public static void addStrength(ServerPlayer p, float amount) {
        if (!HHConfig.FITNESS.get() || amount <= 0) return;
        float s = strength(p);
        if (s >= 100) return;
        float gain = amount * (1 - s / 130f);
        float n = Math.min(100, s + gain);
        CompoundTag k = keep(p);
        k.putFloat(STR, n);
        save(p, k);
        if ((int) n > (int) s) {
            Fx.actionbar(p, Component.translatable("hollowhours.fit.up", (int) n).withStyle(ChatFormatting.GOLD));
            if ((int) n % 10 == 0) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
                p.sendSystemMessage(Component.translatable("hollowhours.fit.milestone", (int) n).withStyle(ChatFormatting.GOLD));
            }
            apply(p);
            sync(p);
        }
    }

    // ------------------------------------------------------------------ weapon skill
    public static int level(int xp) {
        return Math.min(MAX_LEVEL, (int) Math.floor(Math.sqrt(xp / 12.0)));
    }

    public static int meleeLevel(Player p) {
        return level(keep(p).getInt(MEL));
    }

    public static int gunLevel(Player p) {
        return level(keep(p).getInt(GUN));
    }

    public static void addSkill(ServerPlayer p, boolean gun, int xp) {
        CompoundTag k = keep(p);
        String key = gun ? GUN : MEL;
        int before = k.getInt(key);
        k.putInt(key, before + xp);
        save(p, k);
        int lb = level(before), la = level(before + xp);
        if (la > lb) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.0f);
            p.sendSystemMessage(Component.translatable(gun ? "hollowhours.skill.gun.up" : "hollowhours.skill.melee.up", la).withStyle(ChatFormatting.AQUA));
            sync(p);
        }
    }

    // ------------------------------------------------------------------ effects
    public static void apply(ServerPlayer p) {
        float s = HHConfig.FITNESS.get() ? strength(p) / 100f : 0;
        set(p, Attributes.MAX_HEALTH, HP, s * 8.0, AttributeModifier.Operation.ADDITION);
        set(p, Attributes.ATTACK_DAMAGE, DMG, s * 2.5, AttributeModifier.Operation.ADDITION);
        set(p, Attributes.MOVEMENT_SPEED, SPD, s * 0.06, AttributeModifier.Operation.MULTIPLY_BASE);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    //#if MC>=12100
    //$$ static void set(ServerPlayer p, net.minecraft.core.Holder<Attribute> a, UUID uuid, double v, AttributeModifier.Operation op) {
    //$$     AttributeInstance in = p.getAttribute(a);
    //$$     if (in == null) return;
    //$$     var id = com.hollowhours.HollowHours.id("fitness_" + uuid.toString().substring(0, 8));
    //$$     AttributeModifier old = in.getModifier(id);
    //$$     if (old != null && Math.abs(old.amount() - v) < 1e-4) return;
    //$$     if (old != null) in.removeModifier(id);
    //$$     if (v > 0) in.addPermanentModifier(new AttributeModifier(id, v, op));
    //#else
    static void set(ServerPlayer p, Attribute a, UUID id, double v, AttributeModifier.Operation op) {
        AttributeInstance in = p.getAttribute(a);
        if (in == null) return;
        AttributeModifier old = in.getModifier(id);
        if (old != null && Math.abs(old.getAmount() - v) < 1e-4) return;
        if (old != null) in.removeModifier(id);
        if (v > 0) in.addPermanentModifier(new AttributeModifier(id, "hollowhours fitness", v, op));
    //#endif
    }

    public static void sync(ServerPlayer p) {
        HHNet.toPlayer(p, new S2CCombat(S2CCombat.STATS, strength(p), meleeLevel(p), gunLevel(p)));
    }

    /** Running about keeps you fit too (a little). */
    public static void tick(ServerPlayer p, long now) {
        if (now % 20 == 7 && p.isSprinting() && p.onGround()) addStrength(p, 0.012f);
        if (now % 20 == 7 && p.isSwimming()) addStrength(p, 0.015f);
        if (now % 200 == 3) sync(p);
    }

    public static void onJoin(ServerPlayer p) {
        apply(p);
        sync(p);
    }
}
