package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Opens the ghost guide with live status. awake[i] = distance to ghost i+1 (-1 asleep, -2 other dimension).
 * page: which page to open (0 menu, 1 talk, 2 ask).
 */
public record S2CGuide(int page, float soul, float[] awake, int flashTicks, int flashFull, boolean flashOn, boolean hasFlash, int batteries,
                       int asksLeft, int askLimit, int preferGhost) {
    public static void encode(S2CGuide m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.page);
        buf.writeFloat(m.soul);
        buf.writeVarInt(m.awake.length);
        for (float f : m.awake) buf.writeFloat(f);
        buf.writeVarInt(m.flashTicks);
        buf.writeVarInt(m.flashFull);
        buf.writeBoolean(m.flashOn);
        buf.writeBoolean(m.hasFlash);
        buf.writeVarInt(m.batteries);
        buf.writeVarInt(m.asksLeft);
        buf.writeVarInt(m.askLimit);
        buf.writeVarInt(m.preferGhost);
    }

    public static S2CGuide decode(FriendlyByteBuf buf) {
        int page = buf.readVarInt();
        float soul = buf.readFloat();
        int n = Math.min(16, buf.readVarInt());
        float[] aw = new float[n];
        for (int i = 0; i < n; i++) aw[i] = buf.readFloat();
        return new S2CGuide(page, soul, aw, buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CGuide m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.guide(m));
    }
}
