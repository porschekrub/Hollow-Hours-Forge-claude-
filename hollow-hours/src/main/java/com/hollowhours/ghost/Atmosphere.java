package com.hollowhours.ghost;

import com.hollowhours.compat.Compat;
import com.hollowhours.config.HHConfig;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CAtmos;
import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The haunting's weather (server side, saved with the world, sent to every player):
 * - Blood moon: at dusk each night there is a 10% chance (config) the moon rises red. Until dawn the ghosts come
 *   3x as often, hit 3x as hard, run a bit faster, break in easier, and a hunt may bring two ghosts at once.
 * - Fog: now and then thick fog rolls in for a few minutes (more likely at night, always a little on a blood moon).
 */
public final class Atmosphere {
    private Atmosphere() {}

    public static final class Data extends SavedData {
        boolean blood;
        long rolledNight = -1;
        long fogUntil, nextFogCheck;
        float fogStrength;

        static Data load(CompoundTag t) {
            Data d = new Data();
            d.blood = t.getBoolean("blood");
            d.rolledNight = t.getLong("rolledNight");
            d.fogUntil = t.getLong("fogUntil");
            d.nextFogCheck = t.getLong("nextFogCheck");
            d.fogStrength = t.getFloat("fogStrength");
            return d;
        }

        @Override
        //#if MC>=12100
        //$$ public CompoundTag save(CompoundTag t, net.minecraft.core.HolderLookup.Provider registries) {
        //#else
        public CompoundTag save(CompoundTag t) {
        //#endif
            t.putBoolean("blood", blood);
            t.putLong("rolledNight", rolledNight);
            t.putLong("fogUntil", fogUntil);
            t.putLong("nextFogCheck", nextFogCheck);
            t.putFloat("fogStrength", fogStrength);
            return t;
        }
    }

    /** Commands / tests: 1 = force the next (or current) night to be a blood moon, -1 = never, 0 = random. */
    public static int forceBlood;
    private static boolean bloodNow;
    private static float fogSent = -1;
    private static boolean bloodSent, hauntSent;

    static Data data(MinecraftServer server) {
        return Compat.saved(server.overworld().getDataStorage(), Data::load, Data::new, "hollowhours_atmosphere");
    }

    /** Is a blood moon up right now (overworld night)? */
    public static boolean bloodMoon() {
        return bloodNow;
    }

    public static boolean bloodMoonFor(Level level) {
        return bloodNow && level.dimension() == Level.OVERWORLD;
    }

    static boolean nightTime(long dayTime) {
        long t = dayTime % 24000L;
        return t >= 12500 && t < 23400;
    }

    /** Every 20 ticks. */
    public static void tick(MinecraftServer server, long now) {
        ServerLevel over = server.overworld();
        Data d = data(server);
        long dayTime = over.getDayTime();
        boolean night = nightTime(dayTime);
        long nightNo = dayTime / 24000L;
        // ---- blood moon: rolled once per night at dusk, ends at dawn
        // the CI screenshot tour only gets a blood moon / fog when it asks for one; nothing before the story starts
        boolean random = forceBlood == 0 && !GhostEvents.CLIENT_SMOKE && Story.activeCached();
        if (night && d.rolledNight != nightNo) {
            d.rolledNight = nightNo;
            d.blood = forceBlood > 0 || random && R.chance(HHConfig.BLOOD_MOON_CHANCE.get());
            d.setDirty();
            if (d.blood) announceBlood(server);
        } else if (!night && d.blood) {
            d.blood = false;
            d.setDirty();
            for (ServerPlayer p : over.players()) {
                p.sendSystemMessage(Component.translatable("hollowhours.bloodmoon.end").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        if (forceBlood > 0 && night && !d.blood) {
            d.blood = true;
            d.setDirty();
            announceBlood(server);
        }
        if ((forceBlood < 0 || GhostEvents.CLIENT_SMOKE && forceBlood == 0) && d.blood) {
            d.blood = false;
            d.setDirty();
        }
        bloodNow = d.blood;
        // ---- fog
        if (!HHConfig.FOG_ENABLED.get()) d.fogUntil = 0;
        else if (now >= d.fogUntil && now >= d.nextFogCheck && !GhostEvents.CLIENT_SMOKE && Story.activeCached()) {
            d.nextFogCheck = now + 60 * 20;
            double chance = HHConfig.FOG_CHANCE.get() * (night ? 2 : 1) * (over.isRaining() ? 1.5 : 1);
            if (R.chance(chance)) startFog(server, (long) R.rand(120 * 20, 360 * 20), (float) R.rand(0.55, 1.0));
        }
        if (d.fogUntil > 0 && now >= d.fogUntil) {
            d.fogUntil = 0;
            d.setDirty();
        }
        sync(server, false);
    }

    static void announceBlood(MinecraftServer server) {
        for (ServerPlayer p : server.overworld().players()) {
            Fx.title(p, Component.translatable("hollowhours.bloodmoon.title"), Component.translatable("hollowhours.bloodmoon.sub"), 20, 80, 30);
            Fx.playSelf(p, "ghost.scare.bell", 1.6f, 0.5f);
            Fx.playSelf(p, "ghost.horror.choir", 1.2f, 0.7f);
            p.sendSystemMessage(Component.translatable("hollowhours.bloodmoon.chat").withStyle(ChatFormatting.DARK_RED));
        }
        Director.bloodMoonRose(server);
    }

    public static void startFog(MinecraftServer server, long ticks, float strength) {
        Data d = data(server);
        long now = server.overworld().getGameTime();
        d.fogUntil = now + ticks;
        d.fogStrength = strength;
        d.setDirty();
        sync(server, false);
    }

    public static void stopFog(MinecraftServer server) {
        Data d = data(server);
        d.fogUntil = 0;
        d.setDirty();
        sync(server, false);
    }

    static float fogNow(MinecraftServer server) {
        Data d = data(server);
        float f = d.fogUntil > 0 && HHConfig.FOG_ENABLED.get() ? d.fogStrength : 0;
        if (d.blood) f = Math.max(f, 0.35f);
        return f;
    }

    static void sync(MinecraftServer server, boolean force) {
        float fog = fogNow(server);
        boolean haunt = Story.activeCached();
        if (!force && fog == fogSent && bloodNow == bloodSent && haunt == hauntSent) return;
        fogSent = fog;
        bloodSent = bloodNow;
        hauntSent = haunt;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p);
    }

    /** On login / respawn / changing dimension. */
    public static void send(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        boolean over = p.level().dimension() == Level.OVERWORLD;
        // in the haven nothing haunts you (until they find it)
        boolean calm = com.hollowhours.rescue.Rescue.inHaven(p) && !com.hollowhours.rescue.Rescue.invaded(p);
        HHNet.toPlayer(p, new S2CAtmos(over && bloodNow, over ? fogNow(server) : 0, Story.activeCached() && !calm));
    }

    public static void reset() {
        bloodNow = false;
        bloodSent = false;
        fogSent = -1;
        forceBlood = 0;
    }
}
