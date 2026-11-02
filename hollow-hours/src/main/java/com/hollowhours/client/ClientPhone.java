package com.hollowhours.client;

import com.hollowhours.net.S2CChat;
import com.hollowhours.net.S2CPhoneInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the phone knows on this side: the chats, the rescue / map info from the server, where you're navigating to. */
public final class ClientPhone {
    private ClientPhone() {}

    public static final class Chat {
        public String name = "";
        public List<String> lines = new ArrayList<>();
        public int seen;
        public long lastSentAt;
    }

    public static final Map<String, Chat> CHATS = new LinkedHashMap<>();
    @Nullable
    public static S2CPhoneInfo info;
    /** the chat open on the phone right now (its new lines count as read) */
    @Nullable
    static String open;

    public static Chat chat(String contact) {
        return CHATS.computeIfAbsent(contact, k -> new Chat());
    }

    static void update(S2CChat m) {
        Chat c = chat(m.contact());
        if (!m.name().isEmpty()) c.name = m.name();
        c.lines = new ArrayList<>(m.lines());
        if (m.contact().equals(open)) c.seen = c.lines.size();
        else if (c.seen > c.lines.size()) c.seen = c.lines.size();
    }

    public static int unread(String contact) {
        Chat c = CHATS.get(contact);
        if (c == null) return 0;
        int n = 0;
        for (int i = Math.max(0, c.seen); i < c.lines.size(); i++) if (c.lines.get(i).startsWith("them|")) n++;
        return n;
    }

    public static int unreadAll() {
        int n = 0;
        for (String k : CHATS.keySet()) n += unread(k);
        return n;
    }

    // ------------------------------------------------------------------ navigation
    /** where the arrow points: a kind from the server info (home / heli / grave) follows it; "pin" is a fixed spot */
    public record Nav(String kind, String label, int x, int y, int z, String dim) {}

    @Nullable
    public static Nav nav;

    public static void navigate(@Nullable Nav n) {
        nav = n;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(com.hollowhours.registry.HHSounds.get("phone.click"), n == null ? 0.8f : 1.4f, 0.6f));
    }

    static void infoUpdated(S2CPhoneInfo m) {
        info = m;
        if (nav == null || nav.kind().equals("pin")) return;
        for (S2CPhoneInfo.Target t : m.targets())
            if (t.kind().equals(nav.kind())) {
                nav = new Nav(nav.kind(), nav.label(), t.x(), t.y(), t.z(), t.dim());
                return;
            }
    }

    static Component targetName(String kind) {
        return Component.translatable("hollowhours.map." + kind);
    }

    static void reset() {
        CHATS.clear();
        info = null;
        nav = null;
        open = null;
    }
}
