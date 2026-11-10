package com.helperbuddy.talk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.helperbuddy.HelperBuddy;
import com.helperbuddy.config.HBConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What helpers remember about their players, in every world and on every server of this computer:
 * config/helperbuddy-memory.json next to the game (not in the world folder). Things the player asked it to
 * remember, deaths, the worlds they played in, what the helpers did for them.
 */
public final class Memory {
    private Memory() {}

    public static final class Player {
        public String name = "";
        public long firstMet, lastSeen;
        public List<String> worlds = new ArrayList<>();
        public int deaths;
        public String lastDeath = "";
        public String lastWorld = "";
        /** things the player said to remember */
        public List<String> facts = new ArrayList<>();
        /** notable things that happened ("th|en") */
        public List<String> events = new ArrayList<>();
        public Map<String, Integer> counts = new LinkedHashMap<>();
        public String nickname = "";
        /** "th" / "en": the language the player told the helpers to speak ("" = the game's) */
        public String lang = "";
    }

    static Map<String, Player> players = new LinkedHashMap<>();
    static boolean loaded, dirty;
    static Path file = Paths.get("config", "helperbuddy-memory.json");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static boolean on() {
        return HBConfig.MEMORY.get();
    }

    static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    Map<String, Player> m = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, Player>>() {}.getType());
                    if (m != null) players = m;
                }
            }
        } catch (Exception e) {
            HelperBuddy.LOG.warn("[Helper Buddy] could not read {}: {}", file, e.toString());
        }
    }

    public static void save() {
        if (!dirty) return;
        dirty = false;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(players, w);
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            HelperBuddy.LOG.warn("[Helper Buddy] could not save {}: {}", file, e.toString());
        }
    }

    public static Player of(ServerPlayer p) {
        load();
        Player m = players.computeIfAbsent(p.getUUID().toString(), k -> {
            Player n = new Player();
            n.firstMet = System.currentTimeMillis();
            dirty = true;
            return n;
        });
        if (!p.getGameProfile().getName().equals(m.name)) {
            m.name = p.getGameProfile().getName();
            dirty = true;
        }
        return m;
    }

    static String world(MinecraftServer s) {
        return s.getWorldData().getLevelName();
    }

    /** On login: what the helper remembers from before (null for a first meeting). [th, en] */
    public static String[] onLogin(ServerPlayer p) {
        if (!on()) return null;
        Player m = of(p);
        String w = world(p.server);
        String[] greet = null;
        if (m.lastSeen > 0) {
            String when = ago(System.currentTimeMillis() - m.lastSeen);
            String[] wh = when.split("\\|", 2);
            String place = !m.lastWorld.isEmpty() && !m.lastWorld.equals(w) ? m.lastWorld : null;
            String th = "กลับมาแล้วเหรอ " + m.name + "! หายไป" + wh[0] + (place != null ? " (ครั้งก่อนเจอกันในโลก \"" + place + "\")" : "")
                    + (m.deaths > 0 ? " ตายไปแล้วรวม " + m.deaths + " ครั้งนะครับ ผมจำได้หมด" : "");
            String en = "You're back, " + m.name + "! It's been " + wh[1] + (place != null ? " (last time we were in \"" + place + "\")" : "")
                    + (m.deaths > 0 ? ". " + m.deaths + " deaths so far, I remember them all" : "");
            if (!m.facts.isEmpty()) {
                String f = m.facts.get(Persona.R.nextInt(m.facts.size()));
                th += "\nผมยังจำได้นะครับว่า: " + f;
                en += "\nI still remember: " + f;
            }
            greet = new String[] {th, en};
        }
        if (!m.worlds.contains(w)) m.worlds.add(w);
        while (m.worlds.size() > 30) m.worlds.remove(0);
        m.lastWorld = w;
        m.lastSeen = System.currentTimeMillis();
        dirty = true;
        return greet;
    }

    public static void onLogout(ServerPlayer p) {
        if (!on()) return;
        of(p).lastSeen = System.currentTimeMillis();
        dirty = true;
        save();
    }

    public static void onDeath(ServerPlayer p, String cause) {
        if (!on()) return;
        Player m = of(p);
        m.deaths++;
        m.lastDeath = cause + " @ " + world(p.server) + " " + p.blockPosition().toShortString();
        event(p, "ตาย (" + cause + ")", "died (" + cause + ")");
    }

    public static void event(ServerPlayer p, String th, String en) {
        if (!on()) return;
        Player m = of(p);
        m.events.add(new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + " " + world(p.server) + ": " + th + "|" + en);
        while (m.events.size() > 40) m.events.remove(0);
        dirty = true;
    }

    public static void count(ServerPlayer p, String what, int n) {
        if (!on() || n == 0) return;
        Player m = of(p);
        m.counts.merge(what, n, Integer::sum);
        dirty = true;
    }

    public static void remember(ServerPlayer p, String fact) {
        Player m = of(p);
        m.facts.removeIf(f -> f.equalsIgnoreCase(fact));
        m.facts.add(fact);
        while (m.facts.size() > 60) m.facts.remove(0);
        dirty = true;
    }

    /** Forgets facts that contain the words (null / empty = everything). Returns how many. */
    public static int forget(ServerPlayer p, String what) {
        Player m = of(p);
        int n;
        if (what == null || what.isBlank()) {
            n = m.facts.size() + m.events.size();
            m.facts.clear();
            m.events.clear();
            m.counts.clear();
            m.deaths = 0;
            m.lastDeath = "";
        } else {
            String w = what.toLowerCase();
            int before = m.facts.size();
            m.facts.removeIf(f -> f.toLowerCase().contains(w));
            n = before - m.facts.size();
        }
        dirty = true;
        return n;
    }

    /** What it remembers, for the player to read. */
    public static String summary(ServerPlayer p, boolean th) {
        Player m = of(p);
        StringBuilder b = new StringBuilder();
        String since = new SimpleDateFormat("yyyy-MM-dd").format(new Date(m.firstMet));
        b.append(th ? "§eที่ข้าจำได้เกี่ยวกับ " + m.name + "§r (รู้จักกันตั้งแต่ " + since + ")" : "§eWhat I remember about " + m.name + "§r (we met on " + since + ")");
        if (!m.worlds.isEmpty()) b.append(th ? "\n§7โลกที่เคยไป: §f" : "\n§7Worlds: §f").append(String.join(", ", m.worlds));
        b.append(th ? "\n§7ตายไปแล้ว: §f" + m.deaths + " ครั้ง" : "\n§7Deaths: §f" + m.deaths);
        if (!m.lastDeath.isEmpty()) b.append(th ? " §7(ล่าสุด: " + m.lastDeath + ")" : " §7(last: " + m.lastDeath + ")");
        if (!m.counts.isEmpty()) {
            b.append(th ? "\n§7ที่ผู้ช่วยทำให้: §f" : "\n§7Helpers did: §f");
            List<String> c = new ArrayList<>();
            m.counts.forEach((k, v) -> c.add(countName(k, th) + " " + v));
            b.append(String.join(", ", c));
        }
        if (!m.facts.isEmpty()) {
            b.append(th ? "\n§7เรื่องที่สั่งให้จำ:" : "\n§7Things you told me:");
            for (String f : m.facts) b.append("\n §f- ").append(f);
        }
        return b.toString();
    }

    /** The same for an AI's instructions (English, short). */
    public static String forAi(ServerPlayer p) {
        if (!on()) return "";
        Player m = of(p);
        StringBuilder b = new StringBuilder("What you remember about " + m.name + " (from every world you played together): ");
        b.append("worlds ").append(m.worlds).append("; deaths ").append(m.deaths);
        if (!m.lastDeath.isEmpty()) b.append(" (last: ").append(m.lastDeath).append(")");
        if (!m.counts.isEmpty()) b.append("; helpers did ").append(m.counts);
        if (!m.facts.isEmpty()) b.append("; they asked you to remember: ").append(String.join(" / ", m.facts));
        if (!m.events.isEmpty()) {
            List<String> ev = new ArrayList<>();
            for (String e : m.events.subList(Math.max(0, m.events.size() - 8), m.events.size())) ev.add(e.contains("|") ? e.substring(e.indexOf('|') + 1) : e);
            b.append("; recent: ").append(String.join(", ", ev));
        }
        return b.append(".").toString();
    }

    static String countName(String k, boolean th) {
        return switch (k) {
            case "break" -> th ? "ขุด/ทุบบล็อก" : "blocks broken";
            case "hunt" -> th ? "ล่าสัตว์" : "animals hunted";
            case "build" -> th ? "สร้างของ" : "builds";
            case "place" -> th ? "วางบล็อก" : "blocks placed";
            case "kill" -> th ? "ฆ่ามอนสเตอร์" : "monsters killed";
            case "craft" -> th ? "คราฟต์" : "crafted";
            default -> k;
        };
    }

    /** "3 วัน|3 days" */
    static String ago(long ms) {
        long min = ms / 60000;
        if (min < 60) return Math.max(1, min) + " นาที|" + Math.max(1, min) + " minutes";
        long h = min / 60;
        if (h < 48) return h + " ชั่วโมง|" + h + " hours";
        return h / 24 + " วัน|" + h / 24 + " days";
    }

    /** (tests) the file round trip: write, read back, the same. */
    public static boolean selfTest() {
        Path keep = file;
        Map<String, Player> keepPlayers = players;
        boolean keepLoaded = loaded;
        try {
            file = Files.createTempFile("helperbuddy-memory", ".json");
            players = new LinkedHashMap<>();
            Player m = new Player();
            m.name = "Tester";
            m.facts.add("ชอบสีชมพู");
            m.deaths = 3;
            players.put("x", m);
            dirty = true;
            save();
            players = new LinkedHashMap<>();
            loaded = false;
            load();
            Player back = players.get("x");
            Files.deleteIfExists(file);
            return back != null && back.deaths == 3 && back.facts.contains("ชอบสีชมพู");
        } catch (Exception e) {
            HelperBuddy.LOG.warn("[Helper Buddy] memory test", e);
            return false;
        } finally {
            file = keep;
            players = keepPlayers;
            loaded = keepLoaded;
        }
    }
}
