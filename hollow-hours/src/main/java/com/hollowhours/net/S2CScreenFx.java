package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Generic screen effect: camera shake, colour fade, blood vignette pulse, static, blink. */
public record S2CScreenFx(int type, float a, int b, int c, int d, int e) {
    public static final int SHAKE = 0, FADE = 1, BLOOD = 2, STATIC = 3, DARK_PULSE = 4, GLITCH_TEXT = 5, HEARTBEAT = 6;

    public static S2CScreenFx shake(float strength, int ticks) {
        return new S2CScreenFx(SHAKE, strength, ticks, 0, 0, 0);
    }

    /** rgb colour, fade in / hold / fade out ticks */
    public static S2CScreenFx fade(int rgb, int in, int hold, int out) {
        return new S2CScreenFx(FADE, 0, rgb, in, hold, out);
    }

    public static S2CScreenFx blood(float strength, int ticks) {
        return new S2CScreenFx(BLOOD, strength, ticks, 0, 0, 0);
    }

    public static S2CScreenFx statik(float strength, int ticks) {
        return new S2CScreenFx(STATIC, strength, ticks, 0, 0, 0);
    }

    /** big glitchy title, `variant` picks one of the lines in the lang file */
    public static S2CScreenFx glitchText(int variant, int ticks) {
        return new S2CScreenFx(GLITCH_TEXT, 0, variant, ticks, 0, 0);
    }

    public static void encode(S2CScreenFx m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.type);
        buf.writeFloat(m.a);
        buf.writeInt(m.b);
        buf.writeVarInt(m.c);
        buf.writeVarInt(m.d);
        buf.writeVarInt(m.e);
    }

    public static S2CScreenFx decode(FriendlyByteBuf buf) {
        return new S2CScreenFx(buf.readVarInt(), buf.readFloat(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CScreenFx m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.screenFx(m));
    }
}
