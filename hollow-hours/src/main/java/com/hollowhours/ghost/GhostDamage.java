package com.hollowhours.ghost;

import com.hollowhours.HollowHours;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Data-driven damage types (data/hollowhours/damage_type/*.json) with their own death messages. */
public final class GhostDamage {
    public static final ResourceKey<DamageType> HIT = ResourceKey.create(Registries.DAMAGE_TYPE, HollowHours.id("ghost_hit"));
    public static final ResourceKey<DamageType> KILL = ResourceKey.create(Registries.DAMAGE_TYPE, HollowHours.id("ghost_kill"));
    public static final ResourceKey<DamageType> CURSE = ResourceKey.create(Registries.DAMAGE_TYPE, HollowHours.id("curse"));
    public static final ResourceKey<DamageType> THIRST = ResourceKey.create(Registries.DAMAGE_TYPE, HollowHours.id("thirst"));
    public static final ResourceKey<DamageType> MADNESS = ResourceKey.create(Registries.DAMAGE_TYPE, HollowHours.id("madness"));

    private GhostDamage() {}

    private static DamageSource of(Level level, ResourceKey<DamageType> key, @Nullable Entity attacker) {
        var holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return attacker == null ? new DamageSource(holder) : new DamageSource(holder, attacker);
    }

    public static DamageSource hit(Entity ghost) {
        return of(ghost.level(), HIT, ghost);
    }

    public static DamageSource kill(Entity ghost) {
        return of(ghost.level(), KILL, ghost);
    }

    public static DamageSource curse(Level level) {
        return of(level, CURSE, null);
    }

    public static DamageSource thirst(Level level) {
        return of(level, THIRST, null);
    }

    public static DamageSource madness(Level level) {
        return of(level, MADNESS, null);
    }

    public static boolean isGhost(DamageSource src) {
        return src.is(HIT) || src.is(KILL);
    }
}
