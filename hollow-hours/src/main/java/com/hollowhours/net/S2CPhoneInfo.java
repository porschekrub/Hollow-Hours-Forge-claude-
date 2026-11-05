package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * What the phone's rescue and map apps show: when the helicopter comes (world day time, -1 none planned), how long the
 * one that's here still waits (ticks, -1 none), your part of the story, how cruel the ghosts are for you, and the
 * places you can navigate to (home, the helicopter, your graves); which players are dead right now (no signal).
 */
public record S2CPhoneInfo(long arriveAt, int pickupLeft, int phase, int missed, boolean invaded, float ghostMult, float bloodMult, List<Target> targets,
                           List<String> dead) {
    public record Target(String kind, int x, int y, int z, String dim) {}

    public static void encode(S2CPhoneInfo m, FriendlyByteBuf buf) {
        buf.writeLong(m.arriveAt);
        buf.writeVarInt(m.pickupLeft + 1);
        buf.writeVarInt(m.phase);
        buf.writeVarInt(m.missed);
        buf.writeBoolean(m.invaded);
        buf.writeFloat(m.ghostMult);
        buf.writeFloat(m.bloodMult);
        buf.writeVarInt(m.targets.size());
        for (Target t : m.targets) {
            buf.writeUtf(t.kind);
            buf.writeInt(t.x);
            buf.writeInt(t.y);
            buf.writeInt(t.z);
            buf.writeUtf(t.dim);
        }
        buf.writeVarInt(m.dead.size());
        for (String d : m.dead) buf.writeUtf(d);
    }

    public static S2CPhoneInfo decode(FriendlyByteBuf buf) {
        long at = buf.readLong();
        int left = buf.readVarInt() - 1, phase = buf.readVarInt(), missed = buf.readVarInt();
        boolean inv = buf.readBoolean();
        float g = buf.readFloat(), b = buf.readFloat();
        int n = Math.min(32, buf.readVarInt());
        List<Target> t = new ArrayList<>();
        for (int i = 0; i < n; i++) t.add(new Target(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf()));
        int nd = Math.min(64, buf.readVarInt());
        List<String> dead = new ArrayList<>();
        for (int i = 0; i < nd; i++) dead.add(buf.readUtf());
        return new S2CPhoneInfo(at, left, phase, missed, inv, g, b, t, dead);
    }

    public static void handle(S2CPhoneInfo m) {
        ClientPackets.phoneInfo(m);
    }
}
