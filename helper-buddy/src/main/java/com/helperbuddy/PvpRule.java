package com.helperbuddy;

import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.Say;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/** Friendly fire: players (and their helpers) can't hurt each other unless someone turns it on in a helper's settings. Saved per world, OFF by default. */
public final class PvpRule {
    private PvpRule() {}

    static final class Data extends SavedData {
        boolean on;

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putBoolean("on", on);
            return t;
        }

        static Data load(CompoundTag t) {
            Data d = new Data();
            d.on = t.getBoolean("on");
            return d;
        }
    }

    static Data data(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "helperbuddy_pvp");
    }

    public static boolean on(MinecraftServer s) {
        return s != null && data(s).on;
    }

    public static void set(MinecraftServer s, boolean on) {
        Data d = data(s);
        d.on = on;
        d.setDirty();
    }

    public static void toggle(ServerPlayer by) {
        boolean on = !on(by.server);
        set(by.server, on);
        for (ServerPlayer p : by.server.getPlayerList().getPlayers())
            p.sendSystemMessage(Component.literal(on
                    ? Say.t(p, "§6[ผู้ช่วย]§r §c" + by.getName().getString() + " เปิดการตีเพื่อนแล้ว§r ผู้เล่นตีกันได้", "§6[Helper]§r §c" + by.getName().getString() + " turned friendly fire ON§r - players can hurt each other")
                    : Say.t(p, "§6[ผู้ช่วย]§r §a" + by.getName().getString() + " ปิดการตีเพื่อนแล้ว§r ผู้เล่นตีกันไม่ได้", "§6[Helper]§r §a" + by.getName().getString() + " turned friendly fire OFF§r - players can't hurt each other")));
    }

    static boolean ownedByOther(Entity e, Player victim) {
        return e instanceof HelperEntity h && !h.isOwner(victim);
    }

    /** Cancels any hit from another player (melee, arrows, tridents, potions) or another player's helper while friendly fire is off. */
    static void attack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer victim) || on(victim.server)) return;
        Entity src = e.getSource().getEntity(), direct = e.getSource().getDirectEntity();
        boolean friend = src instanceof Player p && p != victim || direct instanceof Player p2 && p2 != victim
                || ownedByOther(src, victim) || ownedByOther(direct, victim);
        if (friend) e.setCanceled(true);
    }
}
