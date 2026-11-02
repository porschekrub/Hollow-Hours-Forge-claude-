package com.hollowhours.phone;

import com.hollowhours.grave.Graves;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CPhoneInfo;
import com.hollowhours.rescue.HelicopterEntity;
import com.hollowhours.rescue.Rescue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Sends each phone what its rescue and map apps show (every 2 seconds). */
public final class PhoneInfo {
    private PhoneInfo() {}

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p);
    }

    static List<String> dead(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) if (!o.isAlive()) out.add("p:" + o.getUUID());
        return out;
    }

    public static void send(ServerPlayer p) {
        MinecraftServer server = p.server;
        List<S2CPhoneInfo.Target> t = new ArrayList<>();
        // home: your bed (or where you'd wake up)
        BlockPos home = p.getRespawnPosition();
        String homeDim = p.getRespawnDimension().location().toString();
        if (home == null) {
            if (Rescue.inHaven(p)) {
                ServerLevel haven = server.getLevel(Rescue.HAVEN);
                home = haven == null ? null : Rescue.havenSpot(haven);
                homeDim = Rescue.HAVEN.location().toString();
            } else {
                home = server.overworld().getSharedSpawnPos();
                homeDim = Level.OVERWORLD.location().toString();
            }
        }
        if (home != null) t.add(new S2CPhoneInfo.Target("home", home.getX(), home.getY(), home.getZ(), homeDim));
        // the helicopter that came for you
        int kind = Rescue.phase(p) == Rescue.WAITING ? HelicopterEntity.PICKUP : Rescue.phase(p) == Rescue.IN_HAVEN ? HelicopterEntity.EXTRACT : -1;
        HelicopterEntity h = kind < 0 ? null : HelicopterEntity.nearest(p, kind);
        int left = -1;
        if (h != null && !h.isLeaving()) {
            t.add(new S2CPhoneInfo.Target("heli", h.getBlockX(), h.getBlockY(), h.getBlockZ(), h.level().dimension().location().toString()));
            if (kind == HelicopterEntity.PICKUP) left = h.ticksLeft();
        }
        for (Graves.Grave g : Graves.get(server).standing(p.getUUID()))
            t.add(new S2CPhoneInfo.Target("grave", g.pos.getX(), g.pos.getY(), g.pos.getZ(), g.dim.location().toString()));
        HHNet.toPlayer(p, new S2CPhoneInfo(Rescue.nextArrival(server), left, Rescue.phase(p), Rescue.missed(p), Rescue.invaded(p),
                (float) Rescue.harshness(p, false), (float) Rescue.harshness(p, true), t, dead(server)));
    }
}
