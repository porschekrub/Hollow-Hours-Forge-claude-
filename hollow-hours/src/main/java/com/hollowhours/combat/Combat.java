package com.hollowhours.combat;

import com.hollowhours.entity.GhostEntity;
import com.hollowhours.ghost.KillSequence;
import com.hollowhours.net.C2SCombat;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CCombat;
import com.hollowhours.util.Fx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Player-side fighting: dodging, guarding against mobs, heavy blows, reload requests, the struggle key; event wiring. */
public final class Combat {
    private Combat() {}

    static final Map<UUID, Long> DODGE_UNTIL = new HashMap<>(), DODGE_READY = new HashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(Combat::attack);
        bus.addListener(Combat::hurt);
        bus.addListener(Combat::tick);
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Fitness.onJoin(p);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Fitness.onJoin(p);
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Fitness.onJoin(p);
        });
    }

    /** A moment where ghost swings miss (after breaking free). */
    public static void grantDodge(Player p, int ticks) {
        DODGE_UNTIL.put(p.getUUID(), p.level().getGameTime() + ticks);
    }

    public static boolean dodging(Player p) {
        return p.level().getGameTime() < DODGE_UNTIL.getOrDefault(p.getUUID(), 0L);
    }

    public static void input(ServerPlayer p, C2SCombat m) {
        switch (m.type()) {
            case C2SCombat.STRUGGLE -> {
                if (!com.hollowhours.endgame.WeepingEntity.struggle(p)) KillSequence.struggle(p);
            }
            case C2SCombat.RELOAD -> {
                if (p.getMainHandItem().getItem() instanceof GunItem g) g.startReload(p, p.getMainHandItem());
            }
            case C2SCombat.DODGE -> dodge(p, m.a(), m.b());
            default -> {}
        }
    }

    static void dodge(ServerPlayer p, float fwd, float strafe) {
        long now = p.level().getGameTime();
        if (now < DODGE_READY.getOrDefault(p.getUUID(), 0L) || KillSequence.isVictim(p) || p.isPassenger() || !p.isAlive()) return;
        if (!com.hollowhours.ghost.Sprint.spend(p, 0.16)) {
            Fx.sound(p.level(), "ghost.pant", p.position(), 0.6f, 1f, 8);
            return;
        }
        DODGE_READY.put(p.getUUID(), now + 22);
        DODGE_UNTIL.put(p.getUUID(), now + 9);
        if (Math.abs(fwd) < 0.01 && Math.abs(strafe) < 0.01) fwd = -1; // no direction: hop back
        float yaw = p.getYRot() * ((float) Math.PI / 180f);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double len = Math.max(1, Math.hypot(fwd, strafe));
        double x = (strafe * cos - fwd * sin) / len, z = (fwd * cos + strafe * sin) / len;
        double sp = 0.95 * (1 + 0.15 * Fitness.strength(p) / 100);
        HHNet.toPlayer(p, new S2CCombat(S2CCombat.DODGE, (float) (x * sp), (float) (z * sp), 0));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.hollowhours.registry.HHSounds.get("ghost.dash"), SoundSource.PLAYERS, 0.7f, 1.3f);
        Fitness.addStrength(p, 0.02f);
    }

    static void attack(AttackEntityEvent e) {
        Player p = e.getEntity();
        if (p.level().isClientSide) return;
        GhostCombat.rememberSwing(p, p.getAttackStrengthScale(0.5f));
    }

    /** Guarding with a melee weapon also blocks mobs; a sprinting full-power blow hits harder. */
    static void hurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        if (victim instanceof ServerPlayer p && MeleeWeapon.guarding(p) && e.getSource().getEntity() instanceof LivingEntity att && !(att instanceof GhostEntity)) {
            Vec3 to = att.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            if (to.dot(p.getViewVector(1f).multiply(1, 0, 1).normalize()) > 0.2) {
                e.setAmount(e.getAmount() * 0.4f);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1f);
                p.getUseItem().hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(p.getUsedItemHand()));
            }
        }
        if (e.getSource().getDirectEntity() instanceof ServerPlayer p && p.getMainHandItem().getItem() instanceof MeleeWeapon) {
            float[] sw = GhostCombat.SWING.get(p.getUUID());
            if (sw != null && p.level().getGameTime() - (long) sw[0] <= 1 && sw[2] > 0 && sw[1] > 0.9f) e.setAmount(e.getAmount() * 1.3f);
            e.setAmount(e.getAmount() * (1 + 0.05f * Fitness.meleeLevel(p)));
            Fitness.addSkill(p, false, 1);
        }
    }

    static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Fitness.tick(p, p.level().getGameTime());
    }
}
