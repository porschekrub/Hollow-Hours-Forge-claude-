package com.helperbuddy.talk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.helperbuddy.HBEvents;
import com.helperbuddy.Helpers;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers near each other talk to each other now and then (made up by the AI when there is one). */
public final class Banter {
    private Banter() {}

    static final Map<UUID, Long> LAST = new HashMap<>();

    /** {A line, B line, A line ...}: each line [th, en]; "{A}" / "{B}" are the helpers' names, "{P}" the player's. */
    static final String[][][] SASSY = {
            {{"เฮ้ย {B} วันนี้เอ็งขุดได้กี่ก้อน", "Hey {B}, how many blocks did you mine today?"}, {"เยอะกว่าเอ็งแน่นอน", "More than you, obviously"},
                    {"ฝันไปเถอะ", "Keep dreaming"}},
            {{"{B} เอ็งว่า {P} จะตายกี่รอบวันนี้", "{B}, how many times do you think {P} dies today?"}, {"ข้าพนันสามรอบ", "I bet three"},
                    {"ข้าว่าห้า ดูทรงแล้ว", "Five. Look at them"}},
            {{"ทำไมเจ้านายเราสั่งงานเยอะจัง", "Why does the boss give us so much work?"}, {"เพราะขี้เกียจไง", "Because they're lazy, duh"}, {"จริง", "True"}},
            {{"{B} ผีมีจริงไหมวะ", "{B}, are ghosts real?"}, {"มีจริงสิ แต่ข้าวิ่งเร็วกว่าเอ็ง", "Sure, but I run faster than you"}, {"ใจร้าย", "Rude"}},
            {{"เห็นบ้านที่ {P} สร้างไหม", "Seen the house {P} built?"}, {"กล่องไม้สี่เหลี่ยมนั่นเหรอ", "That wooden box?"}, {"นั่นแหละ ไม่ต้องบอกใคร", "Yep. Don't tell anyone"}},
            {{"ถ้าข้าลาออก เอ็งจะทำงานแทนไหม", "If I quit, would you do my job?"}, {"ไม่ ข้าจะลาออกด้วย", "No, I'd quit too"}},
    };
    static final String[][][] RUDE = {
            {{"{B} มึงเห็น {P} ขุดหลุมเมื่อกี้ไหม", "{B}, did you see {P} dig that hole?"}, {"เห็น โง่ฉิบหาย", "Yeah, dumbest thing ever"}, {"เออ ไอ้ควายจริง", "A real idiot"}},
            {{"เบื่อทำงานให้ไอ้ {P} ว่ะ", "I'm sick of working for {P}"}, {"กูก็เหมือนกัน แต่มันให้ข้าวกิน", "Me too, but they feed us"}},
            {{"มึงว่า {P} จะรอดคืนนี้ไหม", "Think {P} survives tonight?"}, {"ไม่รอดหรอก ไอ้ห่านั่น", "No chance, that loser"}, {"55555", "LMAO"}},
            {{"{B} มึงหลบไปดิ เกะกะ", "{B}, move, you're in the way"}, {"มึงนั่นแหละหลบ", "You move"}, {"ไอ้เวร", "Jerk"}},
    };
    static final String[][][] POLITE = {
            {{"{B} วันนี้เหนื่อยไหมครับ", "{B}, tired today?"}, {"นิดหน่อยครับ แต่สนุกดี", "A little, but it's fun"}},
            {{"{P} เก่งขึ้นเยอะเลยนะครับ", "{P} is getting really good"}, {"ใช่ครับ ผมภูมิใจมาก", "Yes, I'm so proud"}},
            {{"ช่วยกันทำงานนะครับ {B}", "Let's work together, {B}"}, {"ได้เลยครับ!", "Sure thing!"}},
    };

    /** Once a second. */
    public static void tick(MinecraftServer server) {
        if (!HBConfig.BANTER.get() || server.getTickCount() % 20 != 13) return;
        long now = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Long last = LAST.get(p.getUUID());
            if (last != null && now - last < 20 * 60 * 4) continue;
            if (Persona.R.nextInt(90) != 0) continue;
            List<HelperEntity> near = new ArrayList<>();
            for (HelperEntity h : p.level().getEntitiesOfClass(HelperEntity.class, p.getBoundingBox().inflate(16))) if (!h.isKO()) near.add(h);
            if (near.size() < 2) continue;
            java.util.Collections.shuffle(near, Persona.R);
            HelperEntity a = near.get(0), b = near.get(1);
            if (a.distanceTo(b) > 12) continue;
            LAST.put(p.getUUID(), now);
            start(p, a, b);
        }
    }

    public static void start(ServerPlayer p, HelperEntity a, HelperEntity b) {
        if (AiBridge.enabled() && HBConfig.AI_FOR_CHAT.get()) {
            ai(p, a, b);
            return;
        }
        Persona.Style st = Persona.of(a);
        String[][][] set = st == Persona.Style.RUDE ? RUDE : st == Persona.Style.SASSY ? SASSY : POLITE;
        String[][] conv = set[Persona.R.nextInt(set.length)];
        List<String[]> lines = new ArrayList<>();
        for (String[] l : conv) lines.add(l);
        play(p.server, a.getUUID(), b.getUUID(), lines, p.getGameProfile().getName());
    }

    static void ai(ServerPlayer p, HelperEntity a, HelperEntity b) {
        MinecraftServer server = p.server;
        UUID au = a.getUUID(), bu = b.getUUID();
        String who = p.getGameProfile().getName();
        String sys = "Write a very short funny conversation (3 or 4 lines) between two Minecraft helpers, " + a.helperName() + " (" + personaShort(a) + ") and "
                + b.helperName() + " (" + personaShort(b) + "), who work for the player " + who + ". They tease each other and the player, about what is going on: "
                + Knowledge.forAi(p, a) + " Reply with JSON only: {\"lines\":[{\"who\":\"A\",\"th\":\"Thai line\",\"en\":\"English line\"}, ...]} where who is A ("
                + a.helperName() + ") or B (" + b.helperName() + "). Each line under 15 words.";
        AiBridge.send(sys, List.of(new AiBridge.Msg("user", "Go")), 900, 0.9).thenAccept(res -> server.execute(() -> {
            JsonObject o = AiBridge.jsonIn(res);
            List<String[]> lines = new ArrayList<>();
            List<Boolean> byA = new ArrayList<>();
            if (o != null && o.has("lines") && o.get("lines").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("lines")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject l = e.getAsJsonObject();
                    String th = l.has("th") ? AiBridge.clean(l.get("th").getAsString()) : null, en = l.has("en") ? AiBridge.clean(l.get("en").getAsString()) : null;
                    if (th == null || en == null) continue;
                    lines.add(new String[] {th, en});
                    byA.add(!l.has("who") || !l.get("who").getAsString().equalsIgnoreCase("B"));
                }
            }
            if (lines.isEmpty()) {
                HelperEntity ha = Helpers.byUuid(server, au), hb = Helpers.byUuid(server, bu);
                ServerPlayer pl = server.getPlayerList().getPlayer(p.getUUID());
                if (ha != null && hb != null && pl != null) {
                    String[][][] set = SASSY;
                    String[][] conv = set[Persona.R.nextInt(set.length)];
                    List<String[]> l = new ArrayList<>(List.of(conv));
                    play(server, au, bu, l, who);
                }
                return;
            }
            playWho(server, au, bu, lines, byA, who);
        }));
    }

    static String personaShort(HelperEntity h) {
        return switch (Persona.of(h)) {
            case POLITE -> "polite and kind";
            case SASSY -> "sassy, cheeky, teases everyone";
            case RUDE -> "rude, swears a lot (Thai กู/มึง)";
        };
    }

    static void play(MinecraftServer server, UUID a, UUID b, List<String[]> lines, String player) {
        List<Boolean> byA = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) byA.add(i % 2 == 0);
        playWho(server, a, b, lines, byA, player);
    }

    static void playWho(MinecraftServer server, UUID a, UUID b, List<String[]> lines, List<Boolean> byA, String player) {
        for (int i = 0; i < lines.size(); i++) {
            String[] l = lines.get(i);
            boolean fromA = byA.get(i);
            HBEvents.later(i * 50 + 10, () -> {
                HelperEntity sa = Helpers.byUuid(server, fromA ? a : b), sb = Helpers.byUuid(server, fromA ? b : a);
                if (sa == null || sb == null) return;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.level() != sa.level() || p.distanceTo(sa) > 24) continue;
                    String text = Say.t(p, l[0], l[1]).replace("{A}", sa.helperName()).replace("{B}", sb.helperName()).replace("{P}", player);
                    p.sendSystemMessage(Component.literal("§6[" + sa.helperName() + " → " + sb.helperName() + "]§r " + text));
                    Voice.speak(p, sa, text);
                }
                sa.getLookControl().setLookAt(sb, 30f, 30f);
            });
        }
    }
}
