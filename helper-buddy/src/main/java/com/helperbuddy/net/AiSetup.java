package com.helperbuddy.net;

import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.AiBridge;
import com.helperbuddy.talk.Say;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The "AI" page of the helper's menu: which AI, which model and the API key, set in the game instead of in the
 * config file (they are written to config/helperbuddy-common.toml); the language the helper speaks and a way of
 * talking in the player's own words. The key only ever travels from the screen to the server: what goes back is
 * its last four characters.
 */
public final class AiSetup {
    private AiSetup() {}

    public static final String[] PROVIDERS = {"gemini", "openai", "anthropic", "openrouter", "groq", "ollama", "custom"};
    public static final String[] VOICES = {"auto", "ai", "cartoon", "babble", "off"};
    /** {code, name}: "" = the game's language, "auto" = whatever language the player writes in */
    public static final String[][] LANGUAGES = {{"en", "English"}, {"th", "Thai"}, {"auto", "as I type"}, {"Japanese", "Japanese"}, {"Chinese", "Chinese"}, {"Korean", "Korean"},
            {"Vietnamese", "Vietnamese"}, {"Lao", "Lao"}, {"Indonesian", "Indonesian"}, {"Malay", "Malay"}, {"Filipino", "Filipino"}, {"Burmese", "Burmese"}, {"Khmer", "Khmer"},
            {"Hindi", "Hindi"}, {"Arabic", "Arabic"}, {"Spanish", "Spanish"}, {"French", "French"}, {"German", "German"}, {"Italian", "Italian"}, {"Portuguese", "Portuguese"},
            {"Russian", "Russian"}, {"Dutch", "Dutch"}, {"Turkish", "Turkish"}, {"Polish", "Polish"}, {"Ukrainian", "Ukrainian"}};

    /** What the provider said it has (asked after every save), for the arrows next to the model box. */
    static List<String> models = new ArrayList<>();
    static String modelsOf = "";

    /** The AI belongs to the world: its host (single player, a world opened to friends) or a server operator sets it. */
    public static boolean admin(ServerPlayer p) {
        return p.server.isSingleplayerOwner(p.getGameProfile()) || p.hasPermissions(2);
    }

    static String tail(String key) {
        String k = key.trim();
        return k.isEmpty() ? "" : k.length() <= 8 ? "set" : k.substring(k.length() - 4);
    }

    public static void fill(CompoundTag t, ServerPlayer p, HelperEntity h) {
        CompoundTag a = new CompoundTag();
        a.putBoolean("admin", admin(p));
        a.putBoolean("on", HBConfig.AI_ENABLED.get());
        a.putBoolean("ready", AiBridge.enabled());
        a.putString("provider", AiBridge.providerName());
        a.putString("model", HBConfig.AI_MODEL.get().trim());
        a.putString("modelUsed", AiBridge.modelName());
        a.putString("url", HBConfig.AI_URL.get().trim());
        a.putString("keyTail", tail(HBConfig.AI_KEY.get()));
        a.putString("voice", HBConfig.VOICE.get().trim().toLowerCase(Locale.ROOT));
        ListTag l = new ListTag();
        if (modelsOf.equals(AiBridge.providerName())) for (String m : models) l.add(StringTag.valueOf(m));
        a.put("models", l);
        String lang = Say.chosen(p);
        a.putString("lang", lang.isEmpty() ? (Say.gameThai(p) ? "th" : "en") : lang);
        a.putString("style", h.styleNote());
        t.put("aiSetup", a);
    }

    static void tell(ServerPlayer p, String th, String en) {
        p.sendSystemMessage(Component.literal("§6[Helper Buddy]§r " + Say.t(p, th, en)));
    }

    static String clip(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[\\r\\n\\t§]", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** "Save and test": writes the settings to the config file, then asks the AI something and lists its models. */
    public static void save(ServerPlayer p, HelperEntity h, CompoundTag a) {
        if (!admin(p)) {
            tell(p, "§cเฉพาะเจ้าของโลก (คนเปิดโลก) หรือแอดมินเซิร์ฟเวอร์เท่านั้นที่ตั้งค่า AI ได้", "§cOnly the host of this world (or a server operator) can change the AI settings");
            return;
        }
        String provider = a.getString("provider").trim().toLowerCase(Locale.ROOT);
        if (!java.util.Arrays.asList(PROVIDERS).contains(provider)) provider = "gemini";
        boolean changed = !provider.equals(AiBridge.providerName());
        HBConfig.AI_PROVIDER.set(provider);
        HBConfig.AI_MODEL.set(clip(a.getString("model"), 120));
        HBConfig.AI_URL.set(provider.equals("ollama") || provider.equals("custom") ? clip(a.getString("url"), 300) : "");
        // an empty key box keeps the key that is there (unless the AI was switched to another provider: its key is another one)
        String key = clip(a.getString("key"), 400).replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
        if (!key.isEmpty()) HBConfig.AI_KEY.set(key);
        else if (changed) HBConfig.AI_KEY.set("");
        HBConfig.AI_ENABLED.set(a.getBoolean("on"));
        HBConfig.SPEC.save();
        com.helperbuddy.talk.Talk.aiChanged();
        if (!a.getBoolean("on")) {
            tell(p, "บันทึกแล้ว §7AI ปิดอยู่ (ผู้ช่วยใช้สมองในตัว เข้าใจคำสั่งไทย/อังกฤษ)", "Saved. §7The AI is off (the helper uses its built-in brain: orders in English and Thai)");
            Menus.refresh(p, h);
            return;
        }
        if (!AiBridge.enabled()) {
            tell(p, "บันทึกแล้ว แต่ §eยังไม่ได้ใส่ API key§r ใส่ key ในช่องแล้วกดบันทึกอีกครั้ง", "Saved, but §ethere is no API key yet§r: type it in the box and save again");
            Menus.refresh(p, h);
            return;
        }
        tell(p, "บันทึกแล้ว §7กำลังทดสอบ AI...", "Saved. §7Testing the AI...");
        test(p, h.getUUID());
    }

    static void test(ServerPlayer p, UUID helper) {
        MinecraftServer server = p.server;
        UUID pid = p.getUUID();
        String provider = AiBridge.providerName(), secret = HBConfig.AI_KEY.get().trim();
        AiBridge.chat("This is a connection test. Answer with the single word OK.", List.of(new AiBridge.Msg("user", "ping")), 30, 0).thenAccept(res ->
                AiBridge.listModels().thenAccept(list -> server.execute(() -> {
                    if (!list.isEmpty()) {
                        models = list;
                        modelsOf = provider;
                    }
                    ServerPlayer pl = server.getPlayerList().getPlayer(pid);
                    if (pl == null) return;
                    if (res != null) {
                        tell(pl, "§a✔ AI ใช้งานได้แล้ว§r (" + provider + " · " + AiBridge.modelName() + ")" + (list.isEmpty() ? "" : " §7มีโมเดลให้เลือก " + list.size() + " ตัว กดลูกศรข้างช่อง Model"),
                                "§a✔ The AI works§r (" + provider + " · " + AiBridge.modelName() + ")" + (list.isEmpty() ? "" : " §7" + list.size() + " models to pick from: the arrows next to the Model box"));
                    } else {
                        String err = AiBridge.lastError == null ? "" : AiBridge.lastError;
                        if (!secret.isEmpty()) err = err.replace(secret, "***");
                        String code = err.length() >= 3 ? err.substring(0, 3) : "";
                        String whyTh = switch (code) {
                            case "400", "401", "403" -> "API key ไม่ถูกต้องหรือไม่มีสิทธิ์ใช้โมเดลนี้ (เช็กว่า key ตรงกับเจ้าที่เลือก)";
                            case "404" -> "ไม่มีโมเดลชื่อนี้ (ลบชื่อในช่อง Model ให้ว่างเพื่อใช้ค่าเริ่มต้น หรือกดลูกศรเลือก)";
                            case "429" -> "โควตาหมดชั่วคราว (รอสักพักหรือเปลี่ยนโมเดล)";
                            default -> "ต่อไม่ติดหรือหมดเวลา";
                        };
                        String whyEn = switch (code) {
                            case "400", "401", "403" -> "the API key was refused, or it may not use this model (is it the key of the provider you picked?)";
                            case "404" -> "there is no model by that name (empty the Model box for the default, or pick one with the arrows)";
                            case "429" -> "out of quota for now (wait a little, or pick another model)";
                            default -> "no connection, or it took too long";
                        };
                        tell(pl, "§c✘ AI ยังไม่ตอบ:§r " + whyTh + " §8" + clip(err, 90), "§c✘ The AI did not answer:§r " + whyEn + " §8" + clip(err, 90));
                    }
                    HelperEntity hh = com.helperbuddy.Helpers.byUuid(server, helper);
                    if (hh != null) Menus.refresh(pl, hh);
                })));
    }

    public static void voice(ServerPlayer p, HelperEntity h) {
        if (!admin(p)) {
            tell(p, "§cเฉพาะเจ้าของโลกหรือแอดมินเท่านั้นที่เปลี่ยนเสียงได้", "§cOnly the host of this world (or a server operator) can change the voice");
            return;
        }
        String cur = HBConfig.VOICE.get().trim().toLowerCase(Locale.ROOT);
        int i = java.util.Arrays.asList(VOICES).indexOf(cur);
        HBConfig.VOICE.set(VOICES[(i + 1) % VOICES.length]);
        HBConfig.SPEC.save();
    }

    public static void language(ServerPlayer p, HelperEntity h, String code) {
        for (String[] l : LANGUAGES) if (l[0].equals(code)) {
            Say.setSpeak(p, code);
            return;
        }
    }

    public static void style(ServerPlayer p, HelperEntity h, String text) {
        h.setStyleNote(clip(text, 200));
        if (h.styleNote().isEmpty()) tell(p, h.helperName() + " กลับไปพูดแบบปกติแล้ว", h.helperName() + " talks its normal way again");
        else if (AiBridge.enabled()) tell(p, h.helperName() + " จะพูดแบบนี้: §f" + h.styleNote(), h.helperName() + " will talk like this: §f" + h.styleNote());
        else tell(p, "บันทึกแล้ว §eแต่ลักษณะการพูดที่พิมพ์เองใช้ได้เมื่อเปิด AI§r (ตอนนี้ปิดอยู่ ผู้ช่วยจะใช้แบบที่ใกล้ที่สุด)",
                    "Saved, §ebut a way of talking in your own words needs the AI§r (it is off: the helper uses the closest of its own styles)");
    }
}
