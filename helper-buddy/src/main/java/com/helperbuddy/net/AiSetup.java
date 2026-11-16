package com.helperbuddy.net;

import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.AiBridge;
import com.helperbuddy.talk.Say;
import com.helperbuddy.talk.Voice;
import com.helperbuddy.talk.VoiceTts;
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
    public static final String[] VOICES = {"mod", "ai", "cartoon", "babble", "off"};
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
        CompoundTag v = new CompoundTag();
        v.putBoolean("admin", admin(p));
        v.putString("source", Voice.source());
        v.putString("provider", HBConfig.VOICE_PROVIDER.get().trim().toLowerCase(Locale.ROOT));
        String used = VoiceTts.provider();
        v.putString("used", used == null ? "" : used);
        v.putBoolean("ready", VoiceTts.available());
        v.putString("keyTail", tail(HBConfig.VOICE_KEY.get()));
        v.putString("model", HBConfig.VOICE_MODEL.get().trim());
        v.putString("name", HBConfig.VOICE_NAME.get().trim());
        v.putString("brain", AiBridge.providerName());
        v.putString("modVoice", h.modVoice());
        v.putString("modVoiceName", com.helperbuddy.talk.VoiceCatalog.name(h.modVoice(), h.getUUID().hashCode()));
        v.putString("last", VoiceTts.lastResult);
        t.put("voiceSetup", v);
        com.helperbuddy.ai.Ollama.fill(t);
    }

    // ------------------------------------------------------------------ the voice page
    static final String[] SOURCES = {"mod", "ai", "cartoon", "babble", "off"};

    public static void voiceSource(ServerPlayer p, HelperEntity h) {
        if (!admin(p)) {
            tell(p, "§cเฉพาะเจ้าของโลกหรือแอดมินเท่านั้นที่เปลี่ยนเสียงได้", "§cOnly the host of this world (or a server operator) can change where the voice comes from");
            return;
        }
        int i = java.util.Arrays.asList(SOURCES).indexOf(Voice.source());
        HBConfig.VOICE.set(SOURCES[(i + 1) % SOURCES.length]);
        HBConfig.SPEC.save();
    }

    public static void voicePick(ServerPlayer p, HelperEntity h, int step) {
        h.setModVoice(com.helperbuddy.talk.VoiceCatalog.next(h.modVoice(), step));
        voiceTest(p, h, false);
    }

    /** A sample line in this helper's voice (the AI voice too when that is what it uses). */
    public static void voiceTest(ServerPlayer p, HelperEntity h, boolean ai) {
        boolean th = Say.thai(p);
        String line = th ? "สวัสดีครับ ผมชื่อ " + h.helperName() + " มีอะไรให้ช่วยไหม" : "Hi, I'm " + h.helperName() + ". What can I do for you?";
        int style = com.helperbuddy.talk.Persona.of(h).ordinal();
        if (ai) {
            VoiceTts.toldAt = 0;
            if (!VoiceTts.available()) {
                String brain = AiBridge.providerName();
                tell(p, VoiceTts.provider() == null ? "เจ้าที่เลือก (" + brain + ") ไม่มีเสียงพูด เลือก Voice provider เป็น Gemini, OpenAI, OpenRouter, Groq หรือ Ollama" : "ยังไม่มี key สำหรับเสียง ใส่ key แล้วกดบันทึก",
                        VoiceTts.provider() == null ? "The service picked (" + brain + ") has no voice: pick Gemini, OpenAI, OpenRouter, Groq or Ollama as the voice provider" : "There is no key for the voice yet: type one and save");
                Voice.local(p, h, line, style, false);
                return;
            }
            if (VoiceTts.speak(p, h, line, style, Voice.source().equals("cartoon"))) return;
            tell(p, "AI voice พูดประโยคนี้ไม่ได้ (เช่น Groq/Ollama พูดไทยไม่ได้) ใช้เสียงของมอดแทน", "The AI voice can't say this line (Groq and Ollama speak no Thai, for one): the mod's own voice says it");
        }
        Voice.local(p, h, line, style, Voice.source().equals("cartoon"));
    }

    public static void voiceSave(ServerPlayer p, HelperEntity h, CompoundTag a) {
        if (!admin(p)) {
            tell(p, "§cเฉพาะเจ้าของโลกหรือแอดมินเท่านั้นที่ตั้งค่าเสียง AI ได้", "§cOnly the host of this world (or a server operator) can set up the AI voice");
            return;
        }
        String prov = a.getString("provider").trim().toLowerCase(Locale.ROOT);
        if (!java.util.Arrays.asList(VoiceTts.PROVIDERS).contains(prov)) prov = "same";
        boolean changed = !prov.equals(HBConfig.VOICE_PROVIDER.get().trim().toLowerCase(Locale.ROOT));
        HBConfig.VOICE_PROVIDER.set(prov);
        String key = clip(a.getString("key"), 400).replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
        if (!key.isEmpty()) HBConfig.VOICE_KEY.set(key);
        else if (changed) HBConfig.VOICE_KEY.set("");
        HBConfig.VOICE_MODEL.set(clip(a.getString("model"), 120));
        HBConfig.VOICE_NAME.set(clip(a.getString("name"), 60));
        // saving the AI voice means using it
        if (Voice.source().equals("mod") || Voice.source().equals("babble") || Voice.source().equals("off")) HBConfig.VOICE.set("ai");
        HBConfig.SPEC.save();
        VoiceTts.reset();
        tell(p, "บันทึกแล้ว §7กำลังลองเสียง AI...", "Saved. §7Trying the AI voice...");
        voiceTest(p, h, true);
    }

    // ------------------------------------------------------------------ the local AI page (Ollama)
    public static void ollama(ServerPlayer p, HelperEntity h, String what, CompoundTag a) {
        if (!what.equals("refresh") && !admin(p)) {
            tell(p, "§cเฉพาะเจ้าของโลกหรือแอดมินเท่านั้นที่ตั้งค่า Ollama ได้", "§cOnly the host of this world (or a server operator) can set up Ollama");
            return;
        }
        boolean ok = switch (what) {
            case "install" -> com.helperbuddy.ai.Ollama.install();
            case "start" -> com.helperbuddy.ai.Ollama.startServer();
            case "stop" -> {
                com.helperbuddy.ai.Ollama.stopServer();
                yield true;
            }
            case "remove" -> com.helperbuddy.ai.Ollama.remove();
            case "cancel" -> {
                com.helperbuddy.ai.Ollama.cancel();
                yield true;
            }
            case "pull" -> com.helperbuddy.ai.Ollama.pull(a.getString("model"));
            case "delete" -> com.helperbuddy.ai.Ollama.delete(a.getString("model"));
            case "use" -> {
                com.helperbuddy.ai.AiUse.ollama(a.getString("model"));
                tell(p, "ผู้ช่วยใช้ " + a.getString("model") + " คิดแล้ว", "The helpers think with " + a.getString("model") + " now");
                yield true;
            }
            default -> true;
        };
        if (!ok) tell(p, "§eรอให้งานที่ทำอยู่เสร็จก่อนนะ", "§eWait for the job that is running to finish first");
        // look again in the background, then show it
        net.minecraft.server.MinecraftServer server = p.server;
        UUID pid = p.getUUID(), hid = h.getUUID();
        Thread t = new Thread(() -> {
            com.helperbuddy.ai.Ollama.check();
            server.execute(() -> {
                ServerPlayer pl = server.getPlayerList().getPlayer(pid);
                HelperEntity hh = com.helperbuddy.Helpers.byUuid(server, hid);
                if (pl != null && hh != null) Menus.refresh(pl, hh);
            });
        }, "HelperBuddy-OllamaCheck");
        t.setDaemon(true);
        t.start();
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
