package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.item.PhoneItem;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CPhoneAlert;
import com.hollowhours.registry.HHItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The story's timeline. The first nights are completely normal: no ghosts, no omens, no fog, cheerful players.
 * On the day the haunting begins (day 3 by default) an emergency alert comes through everyone's phone: something
 * escaped, stay inside, lock the doors and windows, don't let it see you. From that night on the ghosts hunt.
 */
public final class Story {
    private Story() {}

    public static final class Data extends SavedData {
        /** 1 = started by command, -1 = held back by command, 0 = follow the calendar */
        int forced;

        static Data load(CompoundTag t) {
            Data d = new Data();
            d.forced = t.getInt("forced");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putInt("forced", forced);
            return t;
        }
    }

    static final String ALERTED = "hollowhours_alerted", PHONE_GIVEN = "hollowhours_phone_given", FLASH_GIVEN = "hollowhours_flashlight_given";
    private static boolean active;

    static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "hollowhours_story");
    }

    /** Day number of the world (1 = the first day). */
    public static long day(ServerLevel over) {
        return over.getDayTime() / 24000L + 1;
    }

    static boolean calendar(ServerLevel over) {
        long day = day(over), tod = over.getDayTime() % 24000L;
        int n = HHConfig.START_NIGHT.get();
        return day > n || day == n && tod >= 12500;
    }

    /** Is the haunting on? (cached each second by {@link #tick}, computed on first use) */
    public static boolean active(MinecraftServer server) {
        if (server == null) return active;
        Data d = data(server);
        if (d.forced != 0) return d.forced > 0;
        return calendar(server.overworld());
    }

    public static boolean activeCached() {
        return active;
    }

    /** Commands / tests: 1 start now, -1 hold back, 0 follow the calendar. */
    public static void force(MinecraftServer server, int mode) {
        Data d = data(server);
        d.forced = mode;
        d.setDirty();
        tick(server, server.overworld().getGameTime());
    }

    /** The alert comes on the day the haunting starts (from 8 in the morning), or right away if it already started. */
    static boolean alertDue(MinecraftServer server) {
        ServerLevel over = server.overworld();
        if (active(server)) return true;
        long day = day(over), tod = over.getDayTime() % 24000L;
        int n = HHConfig.START_NIGHT.get();
        return data(server).forced == 0 && (day > n || day == n && tod >= 2000 && tod < 12500);
    }

    /** Every 20 ticks. */
    public static void tick(MinecraftServer server, long now) {
        boolean was = active;
        active = active(server);
        if (was != active) Atmosphere.sync(server, true);
        if (!HHConfig.PHONE_ALERT.get() || GhostEvents.CLIENT_SMOKE) return;
        boolean due = alertDue(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!due || p.tickCount < 120 || persisted(p).getBoolean(ALERTED)) continue;
            sendAlert(p);
        }
    }

    public static void sendAlert(ServerPlayer p) {
        persisted(p).putBoolean(ALERTED, true);
        boolean phone = PhoneItem.find(p, true) != null;
        HHNet.toPlayer(p, new S2CPhoneAlert(phone));
        if (!phone) p.sendSystemMessage(Component.translatable("hollowhours.alert.chat").withStyle(ChatFormatting.RED));
        PhoneItem.addMessage(p, "hollowhours.phone.msg.alert");
    }

    /** First join: a phone with a random battery. */
    public static void onLogin(ServerPlayer p) {
        CompoundTag d = persisted(p);
        if (!HHConfig.GIVE_PHONE.get()) return;
        if (!d.getBoolean(PHONE_GIVEN)) {
            d.putBoolean(PHONE_GIVEN, true);
            give(p, PhoneItem.fresh(p.getRandom()));
        }
        // a flashlight with a fresh battery in it, and one spare battery
        if (!d.getBoolean(FLASH_GIVEN)) {
            d.putBoolean(FLASH_GIVEN, true);
            ItemStack flash = new ItemStack(HHItems.FLASHLIGHT.get());
            Flashlight.stamp(flash, Flashlight.FULL, false);
            give(p, flash);
            give(p, new ItemStack(HHItems.BATTERY.get()));
        }
    }

    static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    public static boolean alerted(Player p) {
        return persisted(p).getBoolean(ALERTED);
    }

    public static void resetAlert(ServerPlayer p) {
        persisted(p).remove(ALERTED);
    }

    static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    static boolean isPhone(ItemStack s) {
        return s.is(HHItems.PHONE.get());
    }
}
