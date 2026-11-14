package com.helperbuddy.talk;

import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.net.HBNet;
import com.helperbuddy.net.S2CSpeak;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The helper's voice. With a Gemini key every line it says is read aloud by a real voice from the AI (see VoiceTts);
 * without one it babbles like a cartoon (made on the client from short syllables, pitched per helper). Config [voice].
 */
public final class Voice {
    private Voice() {}

    /** Lines said within a few seconds of the player talking are answers (those get the real voice). */
    static final Map<UUID, Long> REPLY_UNTIL = new HashMap<>();

    public static void replying(Player p) {
        REPLY_UNTIL.put(p.getUUID(), System.currentTimeMillis() + 15000);
    }

    static String mode() {
        return HBConfig.VOICE.get().trim().toLowerCase(Locale.ROOT);
    }

    /** Real words in a cartoon voice: the AI voice, sped up and pitched up per helper (voiceMode = cartoon). */
    static boolean cartoon() {
        return mode().equals("cartoon");
    }

    /** A real voice is possible (Gemini with a key). */
    static boolean aiVoice() {
        String m = mode();
        if (!m.equals("ai") && !m.equals("auto") && !m.equals("cartoon")) return false;
        return AiBridge.provider().equals("gemini") && !HBConfig.AI_KEY.get().isBlank() && HBConfig.AI_URL.get().isBlank();
    }

    /** What is said aloud: the first line only, without colours, cut at a pause when it is long. */
    static String clean(String s) {
        String t = s.replaceAll("§.", "").trim();
        int nl = t.indexOf('\n');
        if (nl > 0) t = t.substring(0, nl);
        t = t.replaceAll("\\s+", " ").trim();
        if (t.length() > 150) {
            int cut = Math.max(t.lastIndexOf(' ', 150), Math.max(t.lastIndexOf('.', 150), t.lastIndexOf(',', 150)));
            t = t.substring(0, cut > 60 ? cut : 150);
        }
        return t;
    }

    public static void speak(Player p, HelperEntity h, String line) {
        String m = mode();
        if (!(p instanceof ServerPlayer sp) || sp.connection == null || m.equals("off")) return;
        String text = clean(line);
        if (text.isEmpty() || text.startsWith("(")) return; // "(thinking...)"
        int style = Persona.of(h).ordinal();
        if (aiVoice()) {
            // the real voice or nothing: when it can't be made right now (busy with the line before, no quota) it stays quiet
            VoiceTts.speak(sp, h, text, style, cartoon());
            return;
        }
        if (m.equals("ai") || m.equals("cartoon")) return;
        HBNet.toPlayer(sp, new S2CSpeak(h.getId(), text, h.getUUID().hashCode(), style));
    }

    /** The babble (also when the real voice could not be made). */
    static void babble(ServerPlayer p, HelperEntity h, String text, int style) {
        HBNet.toPlayer(p, new S2CSpeak(h.getId(), text, h.getUUID().hashCode(), style));
    }
}
