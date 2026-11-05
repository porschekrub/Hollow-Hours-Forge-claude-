package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;

/** Blood particles at a spot: a burst away from (dx, dz), or drips. rgb = the creature's blood colour. */
public record S2CBlood(double x, double y, double z, float dx, float dz, int count, int rgb, int kind, float width) {
    public static final int BURST = 0, DRIP = 1;

    public static void encode(S2CBlood m, FriendlyByteBuf buf) {
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeFloat(m.dx);
        buf.writeFloat(m.dz);
        buf.writeVarInt(m.count);
        buf.writeInt(m.rgb);
        buf.writeByte(m.kind);
        buf.writeFloat(m.width);
    }

    public static S2CBlood decode(FriendlyByteBuf buf) {
        return new S2CBlood(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readInt(), buf.readByte(),
                buf.readFloat());
    }

    public static void handle(S2CBlood m) {
        ClientPackets.blood(m);
    }
}
