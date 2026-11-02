package com.hollowhours.phone;

import com.hollowhours.config.HHConfig;
import com.hollowhours.ghost.AiBridge;
import com.hollowhours.ghost.GhostTalk;
import com.hollowhours.ghost.Story;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CChat;
import com.hollowhours.net.S2CNotice;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The phone's chats. Contacts: Mom, Dad and your best friend (they answer - with the AI if it's on, otherwise in their
 * own words), and every real player (messages and locations go straight to their phone). The story comes through here
 * too: after the emergency alert they get worried, around day 8 it gets bad where they are, and on night 15 each of
 * them sends one last message. After that nothing gets through to them.
 *
 * A chat line is "who|type|payload": who = me / them / sys; type t = text, k = translation key (the game's language),
 * l = a location "x,y,z,dimension", x = not delivered.
 */
public final class PhoneChat {
    private PhoneChat() {}

    public static final String[] NPCS = {"mom", "dad", "friend"};
    static final String TAG = "hollowhours_chats", STAGE = "hollowhours_chat_stage";
    static final int KEEP = 40;
    /** reply pools: each has this many lines in the language files */
    static final int LINES = 3;

    public static boolean isNpc(String contact) {
        for (String n : NPCS) if (n.equals(contact)) return true;
        return false;
    }

    static CompoundTag chats(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag d = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!d.contains(TAG)) d.put(TAG, new CompoundTag());
        return d.getCompound(TAG);
    }

    static CompoundTag persisted(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static List<String> log(Player p, String contact) {
        List<String> out = new ArrayList<>();
        for (Tag t : chats(p).getList(contact, Tag.TAG_STRING)) out.add(t.getAsString());
        return out;
    }

    /** Adds a line to a chat and sends the chat to the phone. */
    public static void add(ServerPlayer p, String contact, String line) {
        CompoundTag c = chats(p);
        ListTag l = c.getList(contact, Tag.TAG_STRING);
        l.add(StringTag.valueOf(line));
        while (l.size() > KEEP) l.remove(0);
        c.put(contact, l);
        sync(p, contact);
    }

    static void sync(ServerPlayer p, String contact) {
        HHNet.toPlayer(p, new S2CChat(contact, name(p.getServer(), contact), log(p, contact)));
    }

    /** All chats to the phone (logging in); the first time, the messages already waiting on a new phone. */
    public static void syncAll(ServerPlayer p) {
        if (chats(p).isEmpty())
            for (String[] first : new String[][] {{"mom", "hollowhours.phone.msg.mom"}, {"friend", "hollowhours.phone.msg.friend"}, {"dad", "hollowhours.chat.dad.hello"}}) {
                ListTag l = new ListTag();
                l.add(StringTag.valueOf("them|k|" + first[1]));
                chats(p).put(first[0], l);
            }
        for (String k : chats(p).getAllKeys()) sync(p, k);
    }

    /** CI: no chats, the story messages not sent yet */
    public static void reset(ServerPlayer p) {
        persisted(p).remove(TAG);
        persisted(p).remove(STAGE);
        syncAll(p);
    }

    static String name(MinecraftServer server, String contact) {
        if (!contact.startsWith("p:")) return "";
        try {
            ServerPlayer o = server.getPlayerList().getPlayer(UUID.fromString(contact.substring(2)));
            if (o != null) return o.getGameProfile().getName();
        } catch (IllegalArgumentException ignored) {}
        return "?";
    }

    // ------------------------------------------------------------------ the story
    /** Mom, Dad and your friend are gone for this player (their last messages came) */
    public static boolean lost(ServerPlayer p) {
        return persisted(p).getInt(STAGE) >= 3 || lost(p.server.overworld());
    }

    /** night N (default 15): Mom, Dad and your friend are gone */
    public static boolean lost(ServerLevel over) {
        long day = Story.day(over), tod = over.getDayTime() % 24000L;
        int n = HHConfig.CONTACTS_LOST_NIGHT.get();
        return day > n || day == n && tod >= 13000;
    }

    /** Every second: the messages that come on their own (worried after the alert, worse by day 8, the last one). */
    public static void tick(MinecraftServer server) {
        if (com.hollowhours.ghost.GhostEvents.clientSmoke()) return;
        ServerLevel over = server.overworld();
        long day = Story.day(over);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.tickCount < 200) continue;
            int stage = persisted(p).getInt(STAGE);
            int want = lost(over) ? 3 : day >= 8 && Story.activeCached() ? 2 : Story.activeCached() || Story.alerted(p) ? 1 : 0;
            if (want > stage) {
                persisted(p).putInt(STAGE, want);
                storyMessages(p, want);
            }
        }
    }

    /** Each of them writes, a few seconds apart. */
    public static void storyMessages(ServerPlayer p, int stage) {
        String part = stage == 1 ? "story1" : stage == 2 ? "story2" : "final";
        persisted(p).putInt(STAGE, Math.max(stage, persisted(p).getInt(STAGE)));
        int delay = 40;
        for (String n : NPCS) {
            Later.run(delay, () -> {
                if (p.hasDisconnected()) return;
                add(p, n, "them|k|hollowhours.chat." + n + "." + part);
                HHNet.toPlayer(p, new S2CNotice("hollowhours.chat.notice." + n, new String[0], stage == 3));
                Fx.playSelf(p, "phone.notify", 0.8f, stage == 3 ? 0.8f : 1.1f);
            });
            delay += stage == 3 ? 120 : 80;
        }
    }

    // ------------------------------------------------------------------ sending
    public static final int TEXT = 0, LOCATION = 1;

    /** From the phone: a message (or your location) to a contact. */
    public static void send(ServerPlayer p, String contact, int kind, String text) {
        text = text.replace('|', '/').strip();
        if (text.length() > 160) text = text.substring(0, 160);
        String payload;
        String type;
        if (kind == LOCATION) {
            type = "l";
            payload = p.getBlockX() + "," + p.getBlockY() + "," + p.getBlockZ() + "," + p.level().dimension().location();
        } else {
            if (text.isEmpty()) return;
            type = "t";
            payload = text;
        }
        if (isNpc(contact)) {
            boolean gone = lost(p);
            add(p, contact, "me|" + (gone ? "x" : type) + "|" + payload);
            if (!gone) reply(p, contact, kind == LOCATION ? "where" : text);
            return;
        }
        if (!contact.startsWith("p:")) return;
        ServerPlayer o = null;
        try {
            o = p.server.getPlayerList().getPlayer(UUID.fromString(contact.substring(2)));
        } catch (IllegalArgumentException ignored) {}
        if (o == null || o == p) {
            add(p, contact, "me|x|" + payload);
            return;
        }
        add(p, contact, "me|" + type + "|" + payload);
        String back = "p:" + p.getUUID();
        add(o, back, "them|" + type + "|" + payload);
        String[] args = {p.getGameProfile().getName(), kind == LOCATION ? "◉" : text};
        HHNet.toPlayer(o, new S2CNotice(kind == LOCATION ? "hollowhours.chat.notice.location" : "hollowhours.chat.notice.player", args, false));
        Fx.playSelf(o, "phone.notify", 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------ Mom, Dad and your friend answer
    static void reply(ServerPlayer p, String who, String text) {
        String lang = GhostTalk.lang(p);
        boolean danger = Story.activeCached() || Story.alerted(p);
        int delay = R.randInt(50, 110);
        if (AiBridge.enabled()) {
            AiBridge.chat(system(p, who, lang, danger), history(p, who), 90, 0.9).thenAccept(r -> p.server.execute(() -> {
                if (r == null || r.isBlank()) Later.run(10, () -> scripted(p, who, text, danger));
                else Later.run(Math.max(10, delay - 40), () -> {
                    if (!p.hasDisconnected()) answer(p, who, "them|t|" + r.replace('|', '/'));
                });
            }));
            return;
        }
        Later.run(delay, () -> scripted(p, who, text, danger));
    }

    static void scripted(ServerPlayer p, String who, String text, boolean danger) {
        if (p.hasDisconnected()) return;
        String cat = category(text);
        answer(p, who, "them|k|hollowhours.chat." + who + "." + (danger ? "danger" : "calm") + "." + cat + "." + R.randInt(0, LINES - 1));
    }

    static void answer(ServerPlayer p, String who, String line) {
        if (lost(p)) return;
        add(p, who, line);
        HHNet.toPlayer(p, new S2CNotice("hollowhours.chat.notice." + who, new String[0], false));
        Fx.playSelf(p, "phone.notify", 0.6f, 1.2f);
    }

    /** what the message is about (Thai or English) */
    public static String category(String text) {
        String t = text.toLowerCase();
        if (t.equals("where")) return "where";
        if (has(t, "help", "scared", "afraid", "ghost", "monster", "save", "ช่วย", "กลัว", "ผี", "ตาย", "หนี", "น่ากลัว")) return "scared";
        if (has(t, "love", "miss", "รัก", "คิดถึง", "ขอบคุณ", "thank")) return "love";
        if (has(t, "where", "home", "come", "อยู่ไหน", "ที่ไหน", "บ้าน", "มาหา", "มารับ")) return "where";
        if (has(t, "hi", "hello", "hey", "yo", "สวัสดี", "หวัดดี", "ดี", "ฮัลโหล")) return "greet";
        return "other";
    }

    static boolean has(String t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }

    static String system(ServerPlayer p, String who, String lang, boolean danger) {
        String role = switch (who) {
            case "mom" -> "the player's mom: warm, worried, a little old-fashioned with phones";
            case "dad" -> "the player's dad: calm, practical, protective, short sentences";
            default -> "the player's best friend: a teenager, jokes a lot, uses slang";
        };
        String world = danger
                ? "Strange creatures (ghosts) have escaped and are hunting people. Everyone was told to stay inside, lock up, and not let them see you. You are scared too, hiding where you are, far from the player. Day "
                + Story.day(p.server.overworld()) + "."
                : "Everything is normal; it's a peaceful week in a Minecraft-like world. Day " + Story.day(p.server.overworld()) + ".";
        return "You are " + role + ". You are texting with them on the phone. " + world
                + " Reply like a real text message: one or two short sentences, no quotes, no emojis that are not on a basic phone. Reply in "
                + (lang.equals("th") ? "Thai (casual, the way Thai family/friends text)" : "English") + ".";
    }

    static List<AiBridge.Msg> history(ServerPlayer p, String who) {
        List<AiBridge.Msg> out = new ArrayList<>();
        List<String> log = log(p, who);
        for (String line : log.subList(Math.max(0, log.size() - 10), log.size())) {
            String[] s = line.split("\\|", 3);
            if (s.length < 3 || s[1].equals("x")) continue;
            String text = s[1].equals("l") ? "(sent their location)" : s[1].equals("k") ? "(earlier message)" : s[2];
            out.add(new AiBridge.Msg(s[0].equals("me") ? "user" : "assistant", text));
        }
        if (out.isEmpty() || !out.get(out.size() - 1).role().equals("user")) out.add(new AiBridge.Msg("user", "..."));
        return out;
    }

}
