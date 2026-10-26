package com.hollowhours.ghost;

import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Soul 0-100 (port of soul.js): cursed items slowly drain it. Below 70 you take more damage and get hungry,
 * below 40 you slow down and weaken, below 15 your gear rots away. At 0... it comes to collect the rest.
 * Regenerates slowly; golden apples, sleeping through the night and respawning restore it.
 */
public final class Soul {
    private Soul() {}

    static final String KEY = "hollowhours_soul";
    static final double MAX = 100;
    private static final Map<UUID, Long> SLEPT = new HashMap<>();
    private static long lastTime;
    private static int k;

    static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static float get(Player p) {
        CompoundTag d = data(p);
        return d.contains(KEY) ? d.getFloat(KEY) : (float) MAX;
    }

    static float set(Player p, double v) {
        float f = (float) Math.max(0, Math.min(MAX, v));
        data(p).putFloat(KEY, f);
        return f;
    }

    public static int tier(double v) {
        return v < 15 ? 3 : v < 40 ? 2 : v < 70 ? 1 : 0;
    }

    public static double damageMult(Player p) {
        return new double[]{1, 1.25, 1.6, 2.0}[tier(get(p))];
    }

    static void hud(ServerPlayer p, double v, String prefixKey) {
        int n = (int) Math.round(v / 10);
        String col = v < 15 ? "§4" : v < 40 ? "§5" : v < 70 ? "§d" : "§b";
        String bar = col + "▮".repeat(n) + "§8" + "▮".repeat(10 - n);
        Component c = Component.translatable("hollowhours.soul.bar", bar, Math.round(v));
        if (prefixKey != null) c = Component.translatable(prefixKey).append("  ").append(c);
        Fx.actionbar(p, c);
    }

    public static float drain(ServerPlayer p, double amount, boolean quiet) {
        float before = get(p);
        float v = set(p, before - amount);
        int t0 = tier(before), t1 = tier(v);
        if (!quiet) com.hollowhours.util.Later.run(50, () -> hud(p, v, "hollowhours.soul.drained"));
        if (t1 > t0) {
            p.sendSystemMessage(Component.translatable("hollowhours.soul.tier." + t1));
            Fx.playSelf(p, "mob.warden.heartbeat", 1f, 0.6f);
            Fx.shake(p, 0.4f, 16);
        }
        if (v <= 0 && before > 0) {
            Fx.title(p, Component.translatable("hollowhours.soul.gone.title"), Component.translatable("hollowhours.soul.gone.sub"), 5, 50, 20);
            Director.summonGhost(p);
        }
        return v;
    }

    public static float heal(ServerPlayer p, double amount, String whyKey) {
        float v = set(p, get(p) + amount);
        if (whyKey != null) hud(p, v, whyKey);
        return v;
    }

    /** Every second. */
    public static void tick(net.minecraft.server.MinecraftServer server, long now) {
        k++;
        long tod = server.overworld().getDayTime() % 24000L;
        boolean morning = lastTime > 20000 && tod < 2000;
        lastTime = tod;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            double v = get(p);
            if (p.isSleeping()) SLEPT.put(p.getUUID(), now);
            if (morning && now - SLEPT.getOrDefault(p.getUUID(), -100000L) < 400) {
                v = heal(p, 35, "hollowhours.soul.slept");
                SLEPT.remove(p.getUUID());
            }
            if (k % 30 == 0) {
                int n = Curses.countCursed(p);
                double delta = 1 - Math.min(4, n * 0.6);
                if (delta > 0 && v < MAX) v = set(p, v + delta);
                else if (delta < 0) v = drain(p, -delta, true);
            }
            int t = tier(v);
            if (t >= 1 && k % 20 == 0) Fx.effect(p, MobEffects.HUNGER, t == 1 ? 80 : 60, t - 1);
            if (t >= 2) {
                Fx.effect(p, MobEffects.MOVEMENT_SLOWDOWN, 50, t - 2);
                Fx.effect(p, MobEffects.HUNGER, 50, t - 1);
                Fx.effect(p, MobEffects.WEAKNESS, 50, t - 2);
            }
            if (t >= 3) {
                Fx.effect(p, MobEffects.DIG_SLOWDOWN, 50, 0);
                if (k % 15 == 0) rotGear(p);
            }
            if (t >= 1 && k % 60 == 0) hud(p, v, null);
        }
    }

    static void rotGear(ServerPlayer p) {
        EquipmentSlot[] armor = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        EquipmentSlot[] order = {EquipmentSlot.MAINHAND, armor[R.randInt(0, 3)]};
        boolean hit = false;
        for (EquipmentSlot s : order) {
            ItemStack it = p.getItemBySlot(s);
            if (it.isEmpty() || !it.isDamageableItem()) continue;
            int nd = it.getDamageValue() + Math.max(1, (int) Math.ceil(it.getMaxDamage() * 0.06));
            if (nd >= it.getMaxDamage()) {
                p.setItemSlot(s, ItemStack.EMPTY);
                Fx.playSelf(p, "random.break", 1f, 0.6f);
                p.sendSystemMessage(Component.translatable("hollowhours.soul.rot.broke"));
            } else it.setDamageValue(nd);
            hit = true;
        }
        if (hit) Fx.actionbar(p, Component.translatable("hollowhours.soul.rot"));
    }

    public static void onRespawn(ServerPlayer p) {
        if (get(p) < 40) set(p, 40);
    }
}
