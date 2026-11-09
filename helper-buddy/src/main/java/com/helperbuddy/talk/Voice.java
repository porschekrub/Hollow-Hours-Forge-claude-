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
 * The helper's voice. Every line it says is spoken: by default with a cartoon babble of its own (made on the
 * client from short syllables, pitched per helper), or with a real voice from the AI (Gemini speech) for its answers
 * to what you say, when a Gemini key is set (see VoiceTts). Config [voice].
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

    /** A real voice is possible (Gemini with a key). */
    static boolean aiVoice() {
        String m = mode();
        if (!m.equals("ai") && !m.equals("auto")) return false;
        return AiBridge.provider().equals("gemini") && !HBConfig.AI_KEY.get().isBlank() && HBConfig.AI_URL.get().isBlank();
    }

    /** What is said, without colours and with no line breaks. */
    static String clean(String s) {
        String t = s.replaceAll("§.", "").replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    public static void speak(Player p, HelperEntity h, String line) {
        if (!(p instanceof ServerPlayer sp) || sp.connection == null || mode().equals("off")) return;
        String text = clean(line);
        if (text.isEmpty() || text.startsWith("(")) return; // "(thinking...)"
        int style = Persona.of(h).ordinal();
        boolean reply = REPLY_UNTIL.getOrDefault(sp.getUUID(), 0L) > System.currentTimeMillis();
        if (reply && aiVoice() && VoiceTts.speak(sp, h, text, style)) return;
        HBNet.toPlayer(sp, new S2CSpeak(h.getId(), text, h.getUUID().hashCode(), style));
    }

    /** The babble (also when the real voice could not be made). */
    static void babble(ServerPlayer p, HelperEntity h, String text, int style) {
        HBNet.toPlayer(p, new S2CSpeak(h.getId(), text, h.getUUID().hashCode(), style));
    }
}
