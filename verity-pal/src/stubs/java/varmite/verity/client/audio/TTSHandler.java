package varmite.verity.client.audio;

import net.minecraft.world.entity.player.Player;
import varmite.verity.entity.verity.VerityEntity;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;

/** Stand-in (see README.md): Verity JE's voice (client). */
public class TTSHandler {
    public static volatile boolean cancelCurrentSpeech;

    public static void apply3DEffect(SourceDataLine line, Player player, VerityEntity verity) {}

    public static boolean isVerityMuffled(Player player, VerityEntity verity) {
        throw new AssertionError();
    }

    public static void applyMuffledFilter(byte[] pcm, int offset, int length) {}

    public static void resetMuffledFilter() {}

    public static void playTTS(String text, Player player, VerityEntity verity) {}

    public static String transcribeAudio(byte[] pcm, AudioFormat format) {
        throw new AssertionError();
    }
}
