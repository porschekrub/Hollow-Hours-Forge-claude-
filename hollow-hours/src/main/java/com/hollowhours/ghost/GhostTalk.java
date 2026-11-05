package com.hollowhours.ghost;

import com.hollowhours.compat.Compat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hollowhours.HollowHours;
import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CSpeech;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Talking to ghosts (port of talk.js + topics.js). Type anything in the guide's talk page (Thai or English).
 * The game decides what happens (only appeals and real offers can make it leave, 5% + offer bonus; failed tries may
 * enrage it); the reply comes from the built-in lines, or from a language model when one is configured.
 */
public final class GhostTalk {
    private GhostTalk() {}

    static final int COOLDOWN = 40;
    static final Pattern THAI = Pattern.compile("[\\u0E00-\\u0E7F]");

    // ------------------------------------------------------------------ data
    static final class Data {
        final Map<String, Map<Integer, Map<String, List<String>>>> lines = new HashMap<>();
        final Map<String, Map<Integer, Map<String, List<String>>>> topics = new HashMap<>();
        final Map<String, List<String>> topicKw = new LinkedHashMap<>();
        final List<String> topicOrder = new ArrayList<>();
        final Map<String, List<String>> intentKw = new LinkedHashMap<>();
        final Map<Integer, String> persona = new HashMap<>();
        final Map<String, Map<Integer, String>> names = new HashMap<>();
    }

    private static Data DATA;

    static Data data() {
        if (DATA == null) DATA = load();
        return DATA;
    }

    static List<String> strs(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        return out;
    }

    static Data load() {
        Data d = new Data();
        try (InputStream in = GhostTalk.class.getResourceAsStream("/data/hollowhours/dialogue.json")) {
            if (in == null) throw new IllegalStateException("dialogue.json missing");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String sec : new String[]{"lines", "topics"}) {
                JsonObject s = root.getAsJsonObject(sec);
                for (String lang : s.keySet()) {
                    Map<Integer, Map<String, List<String>>> byG = new HashMap<>();
                    JsonObject lo = s.getAsJsonObject(lang);
                    for (String g : lo.keySet()) {
                        Map<String, List<String>> m = new HashMap<>();
                        JsonObject go = lo.getAsJsonObject(g);
                        for (String k : go.keySet()) {
                            JsonElement v = go.get(k);
                            if (v.isJsonObject()) for (String sub : v.getAsJsonObject().keySet()) m.put(k + "." + sub, strs(v.getAsJsonObject().get(sub)));
                            else m.put(k, strs(v));
                        }
                        byG.put(Integer.parseInt(g), m);
                    }
                    (sec.equals("lines") ? d.lines : d.topics).put(lang, byG);
                }
            }
            JsonObject kw = root.getAsJsonObject("topic_kw");
            for (String k : kw.keySet()) d.topicKw.put(k, strs(kw.get(k)));
            d.topicOrder.addAll(strs(root.get("topic_order")));
            JsonObject ik = root.getAsJsonObject("intent_kw");
            for (String k : ik.keySet()) d.intentKw.put(k, strs(ik.get(k)));
            JsonObject gh = root.getAsJsonObject("ghosts");
            for (String g : gh.keySet()) {
                JsonObject o = gh.getAsJsonObject(g);
                int n = Integer.parseInt(g);
                d.persona.put(n, o.get("persona").getAsString());
                JsonObject nm = o.getAsJsonObject("name");
                for (String lang : nm.keySet()) d.names.computeIfAbsent(lang, k -> new HashMap<>()).put(n, nm.get(lang).getAsString());
            }
        } catch (Exception e) {
            HollowHours.LOG.error("[Hollow Hours] could not load ghost dialogue", e);
        }
        return d;
    }

    // ------------------------------------------------------------------ language helpers
    public static String lang(ServerPlayer p) {
        String l = p.getLanguage();
        return l != null && l.toLowerCase().startsWith("th") ? "th" : "en";
    }

    static String lang(ServerPlayer p, @Nullable String hint) {
        if (hint != null && !hint.isBlank()) return hint.toLowerCase().startsWith("th") ? "th" : "en";
        return lang(p);
    }

    static String name(int n, String lang) {
        Map<Integer, String> m = data().names.getOrDefault(lang, data().names.get("en"));
        return m == null ? "Ghost " + n : m.getOrDefault(n, "Ghost " + n);
    }

    static List<String> pool(String lang, int n, String key) {
        Map<Integer, Map<String, List<String>>> L = data().lines.getOrDefault(lang, data().lines.get("en"));
        if (L == null) return List.of("...");
        Map<String, List<String>> G = L.getOrDefault(n, Map.of());
        List<String> out = G.get(key);
        if (out == null || out.isEmpty()) out = G.get("other");
        if ((out == null || out.isEmpty()) && !lang.equals("en")) return pool("en", n, key);
        return out == null || out.isEmpty() ? List.of("...") : out;
    }

    private static final Map<String, String> LAST_LINE = new HashMap<>();

    static String pick(List<String> lines, String memo, @Nullable String item, Map<String, String> vars) {
        List<String> p = new ArrayList<>();
        for (String l : lines) if (item != null || !l.contains("{item}")) p.add(l);
        if (p.isEmpty()) p = new ArrayList<>(lines);
        String prev = LAST_LINE.get(memo);
        if (p.size() > 1) p.removeIf(l -> l.equals(prev));
        String line = p.get(R.randInt(0, p.size() - 1));
        LAST_LINE.put(memo, line);
        if (item != null) line = line.replace("{item}", item);
        for (Map.Entry<String, String> e : vars.entrySet()) line = line.replace("{" + e.getKey() + "}", e.getValue());
        return line;
    }

    static boolean hasKw(String text, List<String> words) {
        return findKw(text, words) != null;
    }

    @Nullable
    static String findKw(String text, List<String> words) {
        for (String w : words) {
            if (THAI.matcher(w).find() || !w.matches("[a-z' ]+")) {
                if (text.contains(w)) return w;
            } else if (Pattern.compile("(^|[^a-z])" + Pattern.quote(w) + "($|[^a-z])").matcher(text).find()) return w;
        }
        return null;
    }

    // ------------------------------------------------------------------ intents
    static final Set<String> SAFE = Set.of("greet", "question", "who", "bye", "why", "want");
    static final Set<String> HOSTILE = Set.of("taunt", "threat");
    static final Set<String> APPEAL = Set.of("plead", "bargain", "kind", "fear");

    record Intent(String intent, @Nullable String item) {}

    static Intent classify(String raw) {
        String text = raw.toLowerCase().trim();
        if (text.isEmpty()) return new Intent("greet", null);
        Map<String, List<String>> C = data().intentKw;
        String item = findKw(text, C.getOrDefault("items", List.of()));
        for (String name : new String[]{"taunt", "threat"}) if (hasKw(text, C.getOrDefault(name, List.of()))) return new Intent(name, item);
        if (item != null || hasKw(text, C.getOrDefault("bargain", List.of()))) return new Intent("bargain", item);
        for (String name : new String[]{"who", "why", "want", "bye", "kind", "plead", "fear", "question", "greet"})
            if (hasKw(text, C.getOrDefault(name, List.of()))) return new Intent(name, item);
        return new Intent("other", item);
    }

    @Nullable
    static String findTopic(String text) {
        for (String t : data().topicOrder) {
            List<String> kw = data().topicKw.get(t);
            if (kw != null && hasKw(text, kw)) return t;
        }
        return null;
    }

    // ------------------------------------------------------------------ offers
    record Offer(String key, double bonus, Set<Item> items, boolean food) {}

    static final Map<String, String> OFFER_KEY = Map.ofEntries(Map.entry("diamond", "diamond"), Map.entry("เพชร", "diamond"), Map.entry("gold", "gold"),
            Map.entry("ทอง", "gold"), Map.entry("emerald", "emerald"), Map.entry("มรกต", "emerald"), Map.entry("netherite", "netherite"),
            Map.entry("นีเธอไรต์", "netherite"), Map.entry("iron", "iron"), Map.entry("เหล็ก", "iron"), Map.entry("bread", "bread"),
            Map.entry("ขนมปัง", "bread"), Map.entry("food", "food"), Map.entry("อาหาร", "food"), Map.entry("soul", "soul"), Map.entry("วิญญาณ", "soul"));

    @Nullable
    static Offer offer(String k) {
        return switch (k) {
            case "diamond" -> new Offer(k, 0.20, Set.of(Items.DIAMOND), false);
            case "gold" -> new Offer(k, 0.10, Set.of(Items.GOLD_INGOT), false);
            case "emerald" -> new Offer(k, 0.15, Set.of(Items.EMERALD), false);
            case "netherite" -> new Offer(k, 0.30, Set.of(Items.NETHERITE_INGOT), false);
            case "iron" -> new Offer(k, 0.05, Set.of(Items.IRON_INGOT), false);
            case "bread" -> new Offer(k, 0.05, Set.of(Items.BREAD), false);
            case "food" -> new Offer(k, 0.05, Set.of(), true);
            default -> null;
        };
    }

    static boolean takeItem(ServerPlayer p, Offer o) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it.isEmpty()) continue;
            if (o.food ? !Compat.isFood(it) : !o.items.contains(it.getItem())) continue;
            it.shrink(1);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ runtime state
    static final class Memory {
        String last = "";
        int hostile, appeal;
        final List<AiBridge.Msg> history = new ArrayList<>();
    }

    private static final Map<String, Long> LAST_TALK = new HashMap<>();
    private static final Map<String, Memory> MEMORY = new HashMap<>();
    private static final Map<UUID, Long> LOCK = new HashMap<>();
    private static final Map<UUID, Integer> TOKEN = new HashMap<>();

    /** Whispers etc. stay quiet while a ghost's line is on screen. */
    public static boolean talkLocked(ServerPlayer p) {
        return p.serverLevel().getGameTime() < LOCK.getOrDefault(p.getUUID(), 0L);
    }

    @Nullable
    static GhostEntity findGhost(ServerPlayer p, int n) {
        GhostEntity best = null;
        double bd = Double.MAX_VALUE;
        for (GhostEntity g : Director.allGhosts(p.server)) {
            if (n > 0 && g.kind.n != n) continue;
            double d = g.level() == p.level() ? g.position().distanceTo(p.position()) : 1e6;
            if (d < bd) {
                bd = d;
                best = g;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ showing a line
    /** Red typed text + chat line + the ghost's voice. style: 0 talk, 1 rage, 2 success, 3 whisper. */
    public static void show(ServerPlayer p, @Nullable GhostEntity g, int n, String line, int style) {
        String lang = lang(p);
        int len = line.codePointCount(0, line.length());
        int typeTicks = Math.min(60, Math.max(10, (int) Math.ceil(len * 0.9)));
        int hold = Math.min(140, 70 + len * 2);
        LOCK.put(p.getUUID(), p.serverLevel().getGameTime() + typeTicks + hold);
        TOKEN.merge(p.getUUID(), 1, Integer::sum);
        HHNet.toPlayer(p, new S2CSpeech(n, Component.literal(line), style));
        p.sendSystemMessage(Component.literal(name(n, lang)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(" : ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(line).withStyle(ChatFormatting.DARK_RED)));
        if (g != null) {
            String size = style == 1 ? "rage" : len <= 14 ? "s" : len <= 32 ? "m" : "l";
            double x = g.getX(), y = g.getY() + 1.5, z = g.getZ();
            Fx.playAt(p, "ghost.voice.g" + n + "." + size, x, y, z, 2.2f, R.pitch(0.96, 1.04));
            Later.run(30, () -> {
                if (style == 1) Fx.playAt(p, "ghost.growl", x, y, z, 2.2f, 0.5f);
                else if (style == 2) Fx.playAt(p, "ghost.creak", x, y, z, 1.4f, 0.5f);
            });
        }
    }

    /** A canned line for a situation key (ask.give, ask.refuse, ask.unknown, ask.limit ...). */
    public static void say(ServerPlayer p, GhostEntity g, String key, int style) {
        String lang = lang(p);
        int n = g.kind.n;
        String line = pick(pool(lang, n, key.replace('.', '_')), n + key + lang, null, vars(p, g, lang));
        show(p, g, n, line, style);
    }

    // ------------------------------------------------------------------ topic vars
    static final String[] NUM_TH = {"", "หนึ่ง", "สอง", "สาม", "สี่", "ห้า", "หก", "เจ็ด", "แปด", "เก้า", "สิบ", "สิบเอ็ด"};

    static String timeText(long tod, String lang) {
        int h = (int) Math.floor(((tod / 1000.0) + 6) % 24);
        if (lang.equals("en")) {
            if (h == 0) return "midnight";
            if (h == 12) return "noon";
            return (h > 12 ? h - 12 : h) + (h < 12 ? " AM" : " PM");
        }
        if (h == 0) return "เที่ยงคืน";
        if (h <= 5) return "ตี" + NUM_TH[h];
        if (h <= 11) return NUM_TH[h] + "โมงเช้า";
        if (h == 12) return "เที่ยงวัน";
        if (h <= 15) return "บ่าย" + (h == 13 ? "" : NUM_TH[h - 12]) + "โมง";
        if (h <= 18) return NUM_TH[h - 12] + "โมงเย็น";
        return NUM_TH[h - 18] + "ทุ่ม";
    }

    static String dirText(ServerPlayer p, GhostEntity g, String lang) {
        boolean en = lang.equals("en");
        double dx = g.getX() - p.getX(), dz = g.getZ() - p.getZ(), dy = g.getY() - p.getY();
        if (g.level() != p.level()) return en ? "far from" : "ไกลจาก";
        if (Math.hypot(dx, dz) < 3 && Math.abs(dy) > 3) return en ? (dy > 0 ? "above" : "below") : (dy > 0 ? "ข้างบน" : "ข้างล่าง");
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double fwd = dx * fx + dz * fz, right = dx * -fz + dz * fx;
        if (Math.abs(fwd) >= Math.abs(right)) return en ? (fwd >= 0 ? "in front of" : "behind") : (fwd >= 0 ? "ข้างหน้า" : "ข้างหลัง");
        return en ? (right >= 0 ? "to the right of" : "to the left of") : (right >= 0 ? "ทางขวา" : "ทางซ้าย");
    }

    static String doingText(GhostEntity g, String lang) {
        boolean en = lang.equals("en");
        return switch (g.brain().doingKey()) {
            case "searching" -> en ? "looking for you" : "ตามหาเธอ";
            case "chasing" -> en ? "chasing you" : "ไล่ตามเธอ";
            case "breaking" -> en ? "breaking down your door" : "พังประตูบ้านเธอ";
            case "squeezing" -> en ? "squeezing toward you" : "มุดเข้าหาเธอ";
            case "knocking" -> en ? "knocking on your door" : "เคาะประตูบ้านเธอ";
            case "killing" -> en ? "holding someone" : "จับใครบางคนอยู่";
            default -> en ? "standing and watching you" : "ยืนมองเธออยู่";
        };
    }

    static Map<String, String> vars(ServerPlayer p, GhostEntity g, String lang) {
        Map<String, String> v = new HashMap<>();
        v.put("n", p.getGameProfile().getName());
        v.put("dir", dirText(p, g, lang));
        v.put("dist", String.valueOf(g.level() == p.level() ? Math.round(g.position().distanceTo(p.position())) : 999));
        v.put("time", timeText(p.level().getDayTime() % 24000L, lang));
        v.put("doing", doingText(g, lang));
        return v;
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Nullable
    static String topicLine(ServerPlayer p, GhostEntity g, String topic, String lang) {
        int n = g.kind.n;
        Map<Integer, Map<String, List<String>>> T = data().topics.getOrDefault(lang, data().topics.get("en"));
        if (T == null) return null;
        Map<String, List<String>> G = T.getOrDefault(n, Map.of());
        String key = topic;
        if (topic.equals("see")) {
            key = Hiding.isHidden(p) ? "see.hidden" : Perception.canSee(g, p, 48) ? "see.seen" : "see.unseen";
        }
        List<String> pool = G.get(key);
        if (pool == null || pool.isEmpty()) return null;
        String line = pick(pool, "t" + n + key + lang, null, vars(p, g, lang));
        return lang.equals("en") ? cap(line) : line;
    }

    static List<String> situation(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        if (p.getHealth() <= 8) out.add("hurt");
        if (p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && p.getY() < 40) out.add("cave");
        if (p.isInWater()) out.add("water");
        if (W.isNight(p.level())) out.add("night");
        return out;
    }

    static String chooseLine(ServerPlayer p, GhostEntity g, String lang, String intent, boolean raged, Memory mem, String text, @Nullable String item) {
        int n = g.kind.n;
        Map<String, String> v = vars(p, g, lang);
        if (raged) return pick(pool(lang, n, "rage"), "rage" + n, item, v);
        if (!text.isEmpty() && text.equals(mem.last)) return pick(pool(lang, n, "repeat"), "repeat" + n, item, v);
        if (HOSTILE.contains(intent) && mem.hostile >= 2 && R.chance(0.5)) return pick(pool(lang, n, "taunt_many"), "tm" + n, item, v);
        if (APPEAL.contains(intent) && mem.appeal >= 3 && R.chance(0.5)) return pick(pool(lang, n, "plead_many"), "pm" + n, item, v);
        List<String> s = situation(p);
        if (!s.isEmpty() && R.chance(0.3)) {
            String k = s.get(R.randInt(0, s.size() - 1));
            return pick(pool(lang, n, k), k + n, item, v);
        }
        return pick(pool(lang, n, intent), intent + n, item, v);
    }

    // ------------------------------------------------------------------ talking
    /** n = 1..5 ghost to talk to, 0 = the nearest one. */
    public static void talk(ServerPlayer p, int n, String rawText, @Nullable String langHint) {
        String lang = lang(p, langHint);
        GhostEntity g = findGhost(p, n);
        if (g == null) {
            p.sendSystemMessage(Component.translatable("hollowhours.talk.noghost").withStyle(ChatFormatting.GRAY));
            return;
        }
        GhostBrain b = g.brain();
        if (b.isLeaving()) return;
        long now = p.serverLevel().getGameTime();
        String key = g.getUUID() + ":" + p.getUUID();
        if (now - LAST_TALK.getOrDefault(key, -100000L) < COOLDOWN) {
            p.sendSystemMessage(Component.translatable("hollowhours.talk.wait").withStyle(ChatFormatting.GRAY));
            return;
        }
        LAST_TALK.put(key, now);
        Memory mem = MEMORY.computeIfAbsent(key, k -> new Memory());
        String trimmed = rawText.trim();
        String text = trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
        if (AiBridge.enabled() && !text.isEmpty()) {
            // the language model reads the sentence (any wording, Thai or English); the rules below still decide what happens
            HHNet.toPlayer(p, new S2CSpeech(g.kind.n, Component.literal("..."), 3));
            understand(p, g, mem, text).thenAccept(u -> p.server.execute(() -> {
                if (p.hasDisconnected() || !g.isAlive() || g.brain().isLeaving()) return;
                if (u == null && AI_FAIL_TOLD.add(p.getUUID()))
                    p.sendSystemMessage(Component.literal(lang.equals("th")
                            ? "§7[Hollow Hours] เชื่อมต่อ AI ไม่ได้ (เช็ก apiKey ในไฟล์ config หรือดูรายละเอียดใน logs/latest.log) ผีจะใช้สมองในตัวแทน"
                            : "§7[Hollow Hours] The AI could not be reached (check apiKey in the config file, details in logs/latest.log). The ghosts use their built-in brain"));
                respond(p, g, lang, mem, text, u);
            }));
            return;
        }
        respond(p, g, lang, mem, text, null);
    }

    static final java.util.Set<UUID> AI_FAIL_TOLD = new java.util.HashSet<>();

    /** What the language model understood from one message. */
    record Understood(String intent, @Nullable String topic, @Nullable String offer, @Nullable String offerName, @Nullable String ask, int count) {}

    static final Set<String> AI_INTENTS = Set.of("greet", "bye", "who", "why", "want", "question", "kind", "plead", "fear", "bargain", "taunt", "threat",
            "ask_item", "other");

    static final String UNDERSTAND = """
            You read what a Minecraft player says to a ghost that is hunting them. Reply ONLY with one JSON object, no other text:
            {"intent":"...","topic":null,"offer":null,"offer_name":null,"ask":null,"count":1}
            intent is exactly one of:
              greet (hello), bye (goodbye), who (asks who or what it is), why (asks why it haunts or hunts them), want (asks what it wants),
              question (any other question), kind (compliments, kind words, apologising), plead (begs to be spared or left alone),
              fear (says they are scared), bargain (proposes a deal, or offers to give it something so it goes away),
              taunt (insults, mocks or swears at it), threat (threatens to hurt, kill or banish it),
              ask_item (asks the ghost to GIVE THEM an item), other.
            topic: only when they ask one of these, else null:
              killme = will you kill me, where = where are you, death = how did you die, leave = will you leave / when will you go,
              escape = how can I escape or survive, count = how many ghosts are there, family = its family, real = are you real,
              time = what time is it, know = what do you know about me, doing = what are you doing, see = can you see me,
              hungry = are you hungry / what do you eat, from = where do you come from, age = how old are you, whyhere = why are you here.
            offer: the Minecraft item id they offer to GIVE the ghost (diamond, gold_ingot, emerald, iron_sword, cooked_beef ...),
              "food" for food in general, null if they offer nothing. offer_name: that item's name in the player's language.
            ask: the Minecraft item id they ask the ghost to give THEM (diamond_pickaxe, golden_apple, oak_log ...), count: how many (default 1).
            Use the earlier conversation to understand short replies ("yes, take it", "the other one").
            The player may write Thai or English, slang or typos. Thai: ขอ.../อยากได้... = asking for an item (ask_item);
            จะให้.../เอา...ไปไหม/แลก... = offering (bargain); ขอร้อง/ปล่อยฉันไป = plead; ไอ้.../โง่ = taunt.""";

    static java.util.concurrent.CompletableFuture<Understood> understand(ServerPlayer p, GhostEntity g, Memory mem, String text) {
        StringBuilder sys = new StringBuilder(UNDERSTAND);
        if (!mem.history.isEmpty()) {
            sys.append("\nEarlier conversation:\n");
            for (int i = Math.max(0, mem.history.size() - 6); i < mem.history.size(); i++) {
                AiBridge.Msg m = mem.history.get(i);
                sys.append(m.role().equals("user") ? "Player: " : "Ghost: ").append(m.text()).append('\n');
            }
        }
        return AiBridge.send(sys.toString(), List.of(new AiBridge.Msg("user", text)), 1000, 0).thenApply(r -> {
            JsonObject o = AiBridge.jsonIn(r);
            if (o == null) return null;
            try {
                String intent = str(o, "intent");
                if (intent == null || !AI_INTENTS.contains(intent.toLowerCase())) intent = "other";
                String topic = str(o, "topic");
                if (topic != null && !data().topicOrder.contains(topic.toLowerCase())) topic = null;
                int count = o.has("count") && o.get("count").isJsonPrimitive() && o.get("count").getAsJsonPrimitive().isNumber() ? o.get("count").getAsInt() : 1;
                return new Understood(intent.toLowerCase(), topic == null ? null : topic.toLowerCase(), str(o, "offer"), str(o, "offer_name"), str(o, "ask"), count);
            } catch (Exception e) {
                return null;
            }
        });
    }

    @Nullable
    static String str(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull() || !o.get(k).isJsonPrimitive()) return null;
        String v = o.get(k).getAsString().trim();
        return v.isEmpty() || v.equalsIgnoreCase("null") || v.equalsIgnoreCase("none") ? null : v;
    }

    /** An offered item the AI named: the five valuables keep their bonus, any other real item counts a little. */
    @Nullable
    static Offer offerForId(String raw) {
        if (raw.equalsIgnoreCase("food")) return offer("food");
        String id = Curses.checkItemId(raw);
        if (id == null || id.equals("!")) return null;
        String path = id.substring(id.indexOf(':') + 1);
        String k = switch (path) {
            case "diamond" -> "diamond";
            case "gold_ingot" -> "gold";
            case "emerald" -> "emerald";
            case "netherite_ingot" -> "netherite";
            case "iron_ingot" -> "iron";
            case "bread" -> "bread";
            default -> null;
        };
        if (k != null) return offer(k);
        Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(id));
        if (it == Items.AIR) return null;
        double bonus = path.contains("netherite") ? 0.25 : path.contains("diamond") ? 0.15 : path.contains("gold") || path.contains("emerald") ? 0.08
                : Compat.isFood(it) ? 0.05 : 0.03;
        return new Offer(id, bonus, Set.of(it), false);
    }

    static void respond(ServerPlayer p, GhostEntity g, String lang, Memory mem, String text, @Nullable Understood u) {
        GhostBrain b = g.brain();
        if (u == null) {
            if (Curses.tryAsk(p, text)) return;
        } else if (u.intent().equals("ask_item")) {
            Curses.askGhost(p, g, Curses.checkItemId(u.ask()), u.count());
            return;
        }
        int gn = g.kind.n;
        boolean raged = g.isRaged();
        String norm = text.toLowerCase();

        String topic = u != null ? u.topic() : findTopic(norm);
        if (topic != null && !(raged && R.chance(0.5))) {
            String tl = topicLine(p, g, topic, lang);
            if (tl != null) {
                reply(p, g, lang, mem, text, "TOPIC:" + topic, tl, raged ? 1 : 0);
                mem.last = norm;
                return;
            }
        }
        Intent in;
        Offer aiOffer = null;
        if (u != null) {
            aiOffer = u.offer() == null ? null : offerForId(u.offer());
            String it = u.intent().equals("other") && aiOffer != null ? "bargain" : u.intent();
            in = new Intent(it, aiOffer == null ? null : aiOffer.key);
        } else in = classify(text);
        String intent = in.intent();
        Runnable done = () -> {
            mem.last = norm;
            if (HOSTILE.contains(intent)) mem.hostile++;
            else if (APPEAL.contains(intent) || intent.equals("other")) mem.appeal++;
        };
        if (SAFE.contains(intent)) {
            reply(p, g, lang, mem, text, "TALK", chooseLine(p, g, lang, intent, raged, mem, norm, in.item()), raged ? 1 : 0);
            done.run();
            return;
        }
        // ---- offering a real item: it is taken from the inventory and raises the chance ----
        double chance = HHConfig.TALK_SUCCESS_CHANCE.get();
        boolean lie = false;
        Offer offered = null;
        String itemName = in.item();
        Offer o = u != null ? (HOSTILE.contains(intent) ? null : aiOffer)
                : intent.equals("bargain") && in.item() != null ? offer(OFFER_KEY.getOrDefault(in.item(), "")) : null;
        if (o != null) {
            itemName = o.key.contains(":") && u.offerName() != null ? u.offerName() : offerName(o.key, lang);
            if (takeItem(p, o)) {
                offered = o;
                chance += o.bonus;
                p.sendSystemMessage(Component.translatable("hollowhours.talk.took", itemName).withStyle(ChatFormatting.DARK_GRAY));
            } else lie = true;
        }
        if (!HOSTILE.contains(intent) && !lie && R.chance(chance)) {
            reply(p, g, lang, mem, text, "LEAVE", pick(pool(lang, gn, "success"), "success" + gn, itemName, vars(p, g, lang)), 2);
            b.farewell(p);
            return;
        }
        String fItem = itemName;
        if (!raged && R.chance(HHConfig.TALK_RAGE_CHANCE.get())) {
            done.run();
            if (offered != null) {
                reply(p, g, lang, mem, text, "REFUSE_OFFER", pick(pool(lang, gn, "refuse"), "refuse" + gn, fItem, vars(p, g, lang)), 0);
                Later.run(60, () -> enrage(p, g, lang, fItem));
            } else enrage(p, g, lang, fItem);
            return;
        }
        String line;
        String outcome;
        if (lie) {
            line = pick(pool(lang, gn, "nothave"), "nothave" + gn, itemName, vars(p, g, lang));
            outcome = "LIE";
        } else if (offered != null) {
            line = pick(pool(lang, gn, "refuse"), "refuse" + gn, itemName, vars(p, g, lang));
            outcome = "REFUSE_OFFER";
        } else {
            line = chooseLine(p, g, lang, intent, raged, mem, norm, itemName);
            outcome = raged ? "RAGE" : "REFUSE";
        }
        reply(p, g, lang, mem, text, outcome, line, raged ? 1 : 0);
        done.run();
    }

    static String offerName(String key, String lang) {
        boolean en = lang.equals("en");
        if (key.contains(":")) return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(key)).getDescription().getString();
        return switch (key) {
            case "diamond" -> en ? "diamond" : "เพชร";
            case "gold" -> en ? "gold ingot" : "ทองคำแท่ง";
            case "emerald" -> en ? "emerald" : "มรกต";
            case "netherite" -> en ? "netherite ingot" : "นีเธอไรต์แท่ง";
            case "iron" -> en ? "iron ingot" : "เหล็กแท่ง";
            case "bread" -> en ? "bread" : "ขนมปัง";
            default -> en ? "food" : "อาหาร";
        };
    }

    static void enrage(ServerPlayer p, GhostEntity g, String lang, @Nullable String item) {
        if (!g.isAlive()) return;
        g.setRaged(true);
        Fx.shake(p, 0.8f, 24);
        Fx.title(p, Component.translatable("hollowhours.talk.angry"), null, 0, 30, 10);
        int n = g.kind.n;
        Later.run(5, () -> {
            String line = pick(pool(lang, n, "rage"), "rage" + n, item, vars(p, g, lang));
            Memory mem = MEMORY.computeIfAbsent(g.getUUID() + ":" + p.getUUID(), k -> new Memory());
            reply(p, g, lang, mem, "", "ENRAGED", line, 1);
        });
        GhostBrain b = g.brain();
        if (b.mode != GhostBrain.Mode.CHASE && b.mode != GhostBrain.Mode.KILL && GhostBrain.targetable(p) && g.level() == p.level())
            Later.run(25, () -> {
                if (g.isAlive() && !b.isLeaving()) b.startChase(p, g.level().getGameTime());
            });
    }

    /** Shows the built-in line now, or asks the language model for a line with the same outcome. */
    static void reply(ServerPlayer p, GhostEntity g, String lang, Memory mem, String said, String outcome, String fallback, int style) {
        int n = g.kind.n;
        if (!AiBridge.enabled()) {
            show(p, g, n, fallback, style);
            return;
        }
        int token = TOKEN.merge(p.getUUID(), 1, Integer::sum);
        // a short creepy pause while it "thinks"
        HHNet.toPlayer(p, new S2CSpeech(n, Component.literal("..."), 3));
        String sys = systemPrompt(p, g, lang, outcome, fallback);
        List<AiBridge.Msg> msgs = new ArrayList<>(mem.history);
        msgs.add(new AiBridge.Msg("user", said.isEmpty() ? "(silence)" : said));
        AiBridge.chat(sys, msgs, 600, 0.9).thenAccept(r -> p.server.execute(() -> {
            if (p.hasDisconnected()) return;
            String line = r == null ? fallback : r;
            if (!said.isEmpty()) {
                mem.history.add(new AiBridge.Msg("user", said));
                mem.history.add(new AiBridge.Msg("assistant", line));
                while (mem.history.size() > 12) mem.history.remove(0);
            }
            if (TOKEN.getOrDefault(p.getUUID(), 0) != token && r == null) return;
            show(p, g.isAlive() ? g : null, n, line, style);
        }));
    }

    static String systemPrompt(ServerPlayer p, GhostEntity g, String lang, String outcome, String fallback) {
        int n = g.kind.n;
        String language = lang.equals("th") ? "Thai (ภาษาไทย, casual spoken Thai, call the player เธอ)" : "English";
        String what = switch (outcome) {
            case "LEAVE" -> "You have decided to LEAVE them alone for now. Say a short eerie goodbye; you are sinking into the ground.";
            case "ENRAGED" -> "You are now ENRAGED and will hunt them immediately. Threaten them.";
            case "LIE" -> "They offered an item they do not have. Call out the lie.";
            case "REFUSE_OFFER" -> "They gave you an item to make you leave. You keep it but refuse to leave.";
            case "RAGE" -> "You are furious. You refuse everything.";
            case "REFUSE" -> "You refuse what they ask. You are not leaving.";
            default -> outcome.startsWith("TOPIC:")
                    ? "Answer their question in character. True facts you may use: " + fallback
                    : "Just answer them in character. You are not leaving.";
        };
        String hid = Hiding.isHidden(p) ? "hiding from you" : "standing";
        long dist = g.level() == p.level() ? Math.round(g.position().distanceTo(p.position())) : 999;
        return "You are " + name(n, "en") + ", a ghost in a Minecraft horror game. " + data().persona.getOrDefault(n, "") + "\n"
                + "The player is " + p.getGameProfile().getName() + ", " + dist + " blocks away, " + hid + ". You are " + doingText(g, "en") + ". "
                + "Time: " + timeText(p.level().getDayTime() % 24000L, "en") + ". Player health: " + Math.round(p.getHealth()) + "/20.\n"
                + "What happens now (decided by the game, follow it): " + what + "\n"
                + "Rules: reply ONLY with what the ghost says, in " + language + ", max 2 short sentences (under 25 words). "
                + "Stay in character, be unsettling. No quotes, no emojis except your own style, never mention AI, games or rules.";
    }

    public static void forget(ServerPlayer p) {
        LOCK.remove(p.getUUID());
        TOKEN.remove(p.getUUID());
        MEMORY.keySet().removeIf(k -> k.endsWith(":" + p.getUUID()));
        LAST_TALK.keySet().removeIf(k -> k.endsWith(":" + p.getUUID()));
    }
}
