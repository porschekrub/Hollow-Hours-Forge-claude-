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
 * The helper's voice (config voiceSource): its own voice made on the player's computer ("mod", the default: free, no
 * key), a real voice from an AI service for its answers ("ai", "cartoon": everything else, and whatever the service
 * can't say, in its own voice), cartoon babble ("babble"), or nothing ("off").
 */
public final class Voice {
    private Voice() {}

    /** Lines said within a few seconds of the player talking are answers (those get the real voice). */
    static final Map<UUID, Long> REPLY_UNTIL = new HashMap<>();

    public static void replying(Player p) {
        REPLY_UNTIL.put(p.getUUID(), System.currentTimeMillis() + 15000);
    }

    static String mode() {
        String m = HBConfig.VOICE.get().trim().toLowerCase(Locale.ROOT);
        return m.equals("auto") ? "mod" : m;
    }

    public static String source() {
        return mode();
    }

    static boolean cartoon() {
        return mode().equals("cartoon");
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
        if (m.equals("babble")) {
            babble(sp, h, text, style);
            return;
        }
        if (m.equals("ai") || m.equals("cartoon")) {
            // the AI voice for answers to what the player said (a service's speech quota is small); the rest in its own voice
            boolean reply = REPLY_UNTIL.getOrDefault(sp.getUUID(), 0L) > System.currentTimeMillis();
            if ((reply || cartoon()) && VoiceTts.speak(sp, h, text, style, cartoon())) return;
        }
        local(sp, h, text, style, cartoon());
    }

    /** The mod's own voice: the player's computer makes it (babbling when it can't). */
    public static void local(ServerPlayer p, HelperEntity h, String text, int style, boolean cartoon) {
        HBNet.toPlayer(p, new S2CSpeak(h.getId(), text, h.getUUID().hashCode(), style, 1, h.modVoice(), cartoon));
    }

    /** The babble (also when the real voice could not be made). */
    static void babble(ServerPlayer p, HelperEntity h, String text, int style) {
        HBNet.toPlayer(p, new S2CSpeak(h.getId(), text, h.getUUID().hashCode(), style));
    }
}
