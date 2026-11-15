package com.veritypal.talk;

import com.veritypal.config.PalConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Thai or English for each player: the setting, else the language they last talked in, else their game's language. */
public final class Lang {
    private Lang() {}

    static final Map<UUID, Boolean> SPOKE_THAI = new HashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, (ServerChatEvent e) -> note(e.getPlayer(), e.getRawText()));
    }

    public static boolean hasThai(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '฀' && c <= '๿') return true;
        }
        return false;
    }

    public static void note(@Nullable Player p, String text) {
        if (p == null || text == null) return;
        if (hasThai(text)) SPOKE_THAI.put(p.getUUID(), true);
        else if (text.matches("(?s).*[a-zA-Z]{3,}.*")) SPOKE_THAI.put(p.getUUID(), false);
    }

    /** "thai", "english" or "auto". */
    public static String setting() {
        if (!PalConfig.ready()) return "auto";
        String s = PalConfig.LANGUAGE.get().trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("th") || s.equals("ไทย")) return "thai";
        if (s.startsWith("en")) return "english";
        return "auto";
    }

    public static boolean thai(@Nullable Player p) {
        String s = setting();
        if (s.equals("thai")) return true;
        if (s.equals("english")) return false;
        if (p == null) return false;
        Boolean b = SPOKE_THAI.get(p.getUUID());
        if (b != null) return b;
        return p instanceof ServerPlayer sp && gameThai(sp);
    }

    /** The player's game is set to Thai (Minecraft tells the server the language of each client). */
    static boolean gameThai(ServerPlayer p) {
        try {
            java.lang.reflect.Field f = LANG_FIELD;
            if (f == null) return false;
            Object v = f.get(p);
            return v instanceof String s && s.toLowerCase(Locale.ROOT).startsWith("th");
        } catch (Throwable t) {
            return false;
        }
    }

    static final java.lang.reflect.Field LANG_FIELD = langField();

    static java.lang.reflect.Field langField() {
        // ServerPlayer.language (f_8892_ in the game itself)
        for (String n : new String[] {"language", "f_8892_"}) {
            try {
                java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField(n);
                f.setAccessible(true);
                if (f.getType() == String.class) return f;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static String t(@Nullable Player p, String th, String en) {
        return thai(p) ? th : en;
    }
}
