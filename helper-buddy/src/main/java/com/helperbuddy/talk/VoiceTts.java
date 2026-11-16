package com.helperbuddy.talk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.helperbuddy.HelperBuddy;
import com.helperbuddy.Helpers;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.net.HBNet;
import com.helperbuddy.net.S2CVoice;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A real voice from an AI service for the helper's answers: Gemini (its own speech API), or any service with an
 * OpenAI-style /audio/speech (OpenAI, OpenRouter, Groq, Ollama, a custom URL). The server sends the clip to the player
 * as 8-bit mu-law pieces and the client plays it at the helper. When a line can't be made this way (another language
 * than the service speaks, no quota, no key ...) the helper says it in the mod's own voice instead.
 */
public final class VoiceTts {
    private VoiceTts() {}

    static final String[] VOICES = {"Puck", "Fenrir", "Charon", "Kore", "Leda", "Orus", "Zephyr", "Aoede"};
    static final String[] OPENAI_VOICES = {"marin", "cedar", "coral", "sage", "ash", "verse", "ballad", "nova"};
    static final String[] GROQ_VOICES = {"troy", "hannah", "austin", "diana", "daniel", "autumn"};
    static final Set<UUID> BUSY = new HashSet<>();
    /** recent clips (text + voice -> mu-law), so a repeated line costs nothing */
    static final Map<String, byte[]> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > 120;
        }
    };
    static final Map<String, Integer> RATE = new java.util.HashMap<>();
    static long pausedUntil;
    static int clipIds;
    /** Gemini speech models to try (newest first) when none is set */
    static final String[] FALLBACK = {"gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts", "gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"};
    /** the Gemini model that works (found by trying) */
    static String model;
    static int modelTry;
    /** (the setup screen) how the last try went */
    public static volatile String lastResult = "";

    /** New settings: the speech model is looked for again, a pause after a refused key is over. */
    public static void reset() {
        model = null;
        modelTry = 0;
        pausedUntil = 0;
        toldAt = 0;
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    public static final String[] PROVIDERS = {"same", "gemini", "openai", "openrouter", "groq", "ollama", "custom"};

    /** The service that speaks, or null (none set up that can speak). */
    @javax.annotation.Nullable
    public static String provider() {
        String p = HBConfig.VOICE_PROVIDER.get().trim().toLowerCase(java.util.Locale.ROOT);
        if (p.isEmpty() || p.equals("same")) p = AiBridge.provider();
        if (p.equals("google")) p = "gemini";
        if (p.equals("chatgpt")) p = "openai";
        return switch (p) {
            case "gemini", "openai", "openrouter", "groq", "ollama", "custom" -> p;
            default -> null; // Claude, Mistral ...: no speech
        };
    }

    static String key(String prov) {
        String k = HBConfig.VOICE_KEY.get().trim();
        if (!k.isEmpty()) return k;
        return prov.equals(AiBridge.provider()) ? HBConfig.AI_KEY.get().trim() : "";
    }

    /** A voice service is set up (with a key where it needs one). */
    public static boolean available() {
        String p = provider();
        if (p == null) return false;
        return p.equals("ollama") || p.equals("custom") || !key(p).isEmpty();
    }

    /** Groq's and Ollama's voices speak English (and a few European languages), not Thai. */
    static boolean speaks(String prov, String text) {
        if (prov.equals("groq") || prov.equals("ollama")) {
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) > 0x2FF && Character.isLetter(text.charAt(i))) return false;
        }
        return true;
    }

    static String geminiModel() {
        if (model != null) return model;
        String m = HBConfig.VOICE_MODEL.get().trim().replaceFirst("^models/", "");
        if (modelTry == 0 && !m.isEmpty()) return m;
        int i = Math.max(0, modelTry - (m.isEmpty() ? 0 : 1));
        return i < FALLBACK.length ? FALLBACK[i] : null;
    }

    static String modelFor(String prov) {
        String m = HBConfig.VOICE_MODEL.get().trim();
        if (prov.equals("gemini")) return geminiModel();
        if (!m.isEmpty()) return m;
        return switch (prov) {
            case "openai" -> "gpt-4o-mini-tts";
            case "openrouter" -> "google/gemini-3.8-flash-lite-tts";
            case "groq" -> "canopylabs/orpheus-v1-english";
            case "ollama" -> "phonon";
            default -> "tts-1";
        };
    }

    static String voiceFor(HelperEntity h, String prov) {
        String v = HBConfig.VOICE_NAME.get().trim();
        if (!v.isEmpty()) return v;
        String[] list = switch (prov) {
            case "openai", "custom" -> OPENAI_VOICES;
            case "groq" -> GROQ_VOICES;
            case "ollama" -> new String[] {"default"};
            case "openrouter" -> modelFor(prov).contains("gemini") ? VOICES : OPENAI_VOICES;
            default -> VOICES;
        };
        return list[Math.floorMod(h.getUUID().hashCode(), list.length)];
    }

    static String speechUrl(String prov) {
        String u = HBConfig.AI_URL.get().trim().replaceAll("/+$", "");
        return switch (prov) {
            case "openai" -> "https://api.openai.com/v1/audio/speech";
            case "openrouter" -> "https://openrouter.ai/api/v1/audio/speech";
            case "groq" -> "https://api.groq.com/openai/v1/audio/speech";
            case "ollama" -> (u.isEmpty() || !AiBridge.provider().equals("ollama") ? "http://127.0.0.1:11434" : u.replaceAll("/v1(/chat/completions)?$", "")) + "/v1/audio/speech";
            default -> u.endsWith("/chat/completions") ? u.replaceFirst("/chat/completions$", "/audio/speech") : (u.endsWith("/v1") ? u : u + "/v1") + "/audio/speech";
        };
    }

    /** How much faster and higher a helper's cartoon voice is played (its own, 1.28 - 1.46). */
    static float cartoonPitch(HelperEntity h) {
        return 1.28f + Math.floorMod(h.getUUID().hashCode() >> 4, 7) * 0.03f;
    }

    /** @return false when the AI voice can't take this line (the caller uses the mod's own voice) */
    public static boolean speak(ServerPlayer p, HelperEntity h, String text, int style, boolean cartoon) {
        String prov = provider();
        if (prov == null || !available() || !speaks(prov, text)) return false;
        if (System.currentTimeMillis() < pausedUntil || BUSY.contains(h.getUUID())) return false;
        String mdl = modelFor(prov);
        if (mdl == null) return false;
        String voice = voiceFor(h, prov);
        String key = prov + "|" + mdl + "|" + voice + "|" + cartoon + "|" + text;
        float pitch = cartoon ? cartoonPitch(h) : 1f;
        byte[] cached;
        synchronized (CACHE) {
            cached = CACHE.get(key);
        }
        if (cached != null) {
            send(p, h, cached, Math.round(RATE.getOrDefault(key, 24000) * pitch));
            return true;
        }
        HttpRequest rq;
        boolean gemini = prov.equals("gemini");
        try {
            if (gemini) {
                JsonObject body = new JsonObject();
                JsonArray contents = new JsonArray();
                JsonObject c = new JsonObject();
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                // only the line itself: a "say this like ..." in front of it was sometimes read out loud too
                part.addProperty("text", text);
                parts.add(part);
                c.add("parts", parts);
                contents.add(c);
                body.add("contents", contents);
                JsonObject gen = new JsonObject();
                JsonArray mods = new JsonArray();
                mods.add("AUDIO");
                gen.add("responseModalities", mods);
                JsonObject speech = new JsonObject(), vc = new JsonObject(), pre = new JsonObject();
                pre.addProperty("voiceName", voice);
                vc.add("prebuiltVoiceConfig", pre);
                speech.add("voiceConfig", vc);
                gen.add("speechConfig", speech);
                body.add("generationConfig", gen);
                rq = HttpRequest.newBuilder(URI.create(AiBridge.geminiBase + mdl + ":generateContent"))
                        .timeout(Duration.ofSeconds(Math.max(10, HBConfig.AI_TIMEOUT.get())))
                        .header("content-type", "application/json").header("x-goog-api-key", key(prov))
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            } else {
                JsonObject body = new JsonObject();
                body.addProperty("model", mdl);
                body.addProperty("input", text);
                body.addProperty("voice", voice);
                body.addProperty("response_format", prov.equals("groq") ? "wav" : "pcm");
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(speechUrl(prov)))
                        .timeout(Duration.ofSeconds(Math.max(10, HBConfig.AI_TIMEOUT.get())))
                        .header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
                if (!key(prov).isEmpty()) rb.header("authorization", "Bearer " + key(prov));
                rq = rb.build();
            }
        } catch (Exception e) {
            HelperBuddy.LOG.warn("[Helper Buddy] voice request could not be made: {}", e.toString());
            return false;
        }
        BUSY.add(h.getUUID());
        MinecraftServer server = p.server;
        UUID pid = p.getUUID(), hid = h.getUUID();
        String secret = key(prov);
        AiBridge.client().sendAsync(rq, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((r, err) -> {
            boolean retry = false;
            String problem = null;
            byte[] ulaw = null;
            int rate = 24000;
            if (err != null) {
                HelperBuddy.LOG.warn("[Helper Buddy] voice request failed: {}", err.toString());
                problem = "net";
            } else if (r.statusCode() / 100 != 2) {
                String msg = AiBridge.trim(new String(r.body(), java.nio.charset.StandardCharsets.UTF_8), 300);
                if (!secret.isEmpty()) msg = msg.replace(secret, "***");
                HelperBuddy.LOG.warn("[Helper Buddy] voice request failed ({}, {} {}): {}", r.statusCode(), prov, mdl, msg);
                lastResult = r.statusCode() + " " + AiBridge.trim(msg, 120);
                if (gemini && (r.statusCode() == 404 || r.statusCode() == 400) && VoiceTts.model == null) {
                    modelTry++;
                    retry = geminiModel() != null;
                    if (!retry) problem = "nomodel";
                } else if (r.statusCode() == 404 || r.statusCode() == 400) problem = "nomodel";
                else if (r.statusCode() == 429 || r.statusCode() == 402) {
                    pausedUntil = System.currentTimeMillis() + 5 * 60_000L;
                    problem = "quota";
                } else if (r.statusCode() == 403 || r.statusCode() == 401) {
                    pausedUntil = System.currentTimeMillis() + 5 * 60_000L;
                    problem = "key";
                } else problem = "other";
            } else {
                try {
                    byte[] pcm;
                    if (gemini) {
                        if (VoiceTts.model == null) VoiceTts.model = mdl;
                        pcm = null;
                        JsonObject o = JsonParser.parseString(new String(r.body(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        for (JsonElement cand : o.getAsJsonArray("candidates")) {
                            for (JsonElement pe : cand.getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts")) {
                                JsonObject po = pe.getAsJsonObject();
                                if (!po.has("inlineData")) continue;
                                JsonObject d = po.getAsJsonObject("inlineData");
                                Matcher m = Pattern.compile("rate=(\\d+)").matcher(d.has("mimeType") ? d.get("mimeType").getAsString() : "");
                                if (m.find()) rate = Integer.parseInt(m.group(1));
                                pcm = Base64.getDecoder().decode(d.get("data").getAsString());
                                break;
                            }
                            if (pcm != null) break;
                        }
                    } else pcm = r.body();
                    if (pcm != null && pcm.length > 100) {
                        int[] wavRate = new int[1];
                        pcm = stripWav(pcm, wavRate);
                        if (wavRate[0] > 0) rate = wavRate[0];
                        ulaw = toUlaw(pcm);
                        lastResult = "ok";
                    } else problem = "other";
                } catch (Exception e) {
                    HelperBuddy.LOG.warn("[Helper Buddy] voice answer could not be read: {}", e.toString());
                    problem = "other";
                }
            }
            byte[] clip = ulaw;
            int clipRate = rate;
            boolean again = retry;
            String why = problem;
            server.execute(() -> {
                BUSY.remove(hid);
                ServerPlayer pl = server.getPlayerList().getPlayer(pid);
                HelperEntity hh = Helpers.byUuid(server, hid);
                if (pl == null || hh == null) return;
                if (clip == null) {
                    // that Gemini model is gone: the same line with the next one, at once
                    if (again && speak(pl, hh, text, style, cartoon)) return;
                    tell(pl, why);
                    // the line is still said, in the mod's own voice
                    Voice.local(pl, hh, text, style, cartoon);
                    return;
                }
                synchronized (CACHE) {
                    CACHE.put(key, clip);
                }
                RATE.put(key, clipRate);
                send(pl, hh, clip, Math.round(clipRate * pitch));
            });
        });
        return true;
    }

    public static long toldAt;

    /** Why the AI voice did not speak, in grey, now and then (not on every line). */
    static void tell(ServerPlayer p, @javax.annotation.Nullable String why) {
        if (why == null || System.currentTimeMillis() - toldAt < 5 * 60_000L) return;
        toldAt = System.currentTimeMillis();
        String th = switch (why) {
            case "quota" -> "โควตาเสียงพูดของ AI หมดชั่วคราว ตอนนี้ใช้เสียงของมอดแทน อีกสักพักจะลองใหม่เอง";
            case "key" -> "key นี้ใช้ทำเสียงพูดไม่ได้ (ไม่มีสิทธิ์) ใช้เสียงของมอดแทน";
            case "nomodel" -> "หาโมเดลเสียงพูดที่ใช้ได้ไม่เจอ ใช้เสียงของมอดแทน";
            case "net" -> "ต่อบริการเสียงพูดไม่ติด ใช้เสียงของมอดแทน";
            default -> "ทำเสียงพูดของ AI ไม่สำเร็จ (รายละเอียดใน logs/latest.log) ใช้เสียงของมอดแทน";
        };
        String en = switch (why) {
            case "quota" -> "The AI voice is out of quota for now: the helper uses its own voice, and tries again later";
            case "key" -> "This key may not use the AI voice: the helper uses its own voice";
            case "nomodel" -> "No speech model works with these settings: the helper uses its own voice";
            case "net" -> "The voice service could not be reached: the helper uses its own voice";
            default -> "The AI voice could not be made (details in logs/latest.log): the helper uses its own voice";
        };
        p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[voice] " + com.helperbuddy.talk.Say.t(p, th, en)));
    }

    static void send(ServerPlayer p, HelperEntity h, byte[] ulaw, int rate) {
        int clip = ++clipIds;
        int size = 30000;
        int parts = Math.max(1, (ulaw.length + size - 1) / size);
        for (int i = 0; i < parts; i++) {
            int from = i * size, to = Math.min(ulaw.length, from + size);
            HBNet.toPlayer(p, new S2CVoice(h.getId(), clip, i, i == parts - 1, rate, java.util.Arrays.copyOfRange(ulaw, from, to)));
        }
    }

    /** A WAV file (RIFF) -> its PCM samples (rate in rate[0]); anything else is returned as it is. */
    static byte[] stripWav(byte[] b, int[] rate) {
        if (b.length < 44 || b[0] != 'R' || b[1] != 'I' || b[2] != 'F' || b[3] != 'F') return b;
        rate[0] = (b[24] & 0xFF) | (b[25] & 0xFF) << 8 | (b[26] & 0xFF) << 16 | (b[27] & 0xFF) << 24;
        int i = 12;
        while (i + 8 <= b.length) {
            int len = (b[i + 4] & 0xFF) | (b[i + 5] & 0xFF) << 8 | (b[i + 6] & 0xFF) << 16 | (b[i + 7] & 0xFF) << 24;
            if (b[i] == 'd' && b[i + 1] == 'a' && b[i + 2] == 't' && b[i + 3] == 'a')
                return java.util.Arrays.copyOfRange(b, i + 8, Math.min(b.length, i + 8 + Math.max(0, len)));
            i += 8 + Math.max(0, len) + (len & 1);
        }
        return java.util.Arrays.copyOfRange(b, 44, b.length);
    }

    /** 16-bit little-endian PCM -> 8-bit mu-law (G.711). */
    public static byte[] toUlaw(byte[] pcm) {
        byte[] out = new byte[pcm.length / 2];
        for (int i = 0; i < out.length; i++) {
            int s = (short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8));
            out[i] = ulaw(s);
        }
        return out;
    }

    static byte ulaw(int sample) {
        final int BIAS = 0x84, CLIP = 32635;
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) sample = -sample;
        if (sample > CLIP) sample = CLIP;
        sample += BIAS;
        int exp = 7;
        for (int mask = 0x4000; (sample & mask) == 0 && exp > 0; exp--, mask >>= 1) ;
        int mant = (sample >> (exp + 3)) & 0x0F;
        return (byte) ~(sign | (exp << 4) | mant);
    }

    /** 8-bit mu-law -> 16-bit sample. */
    public static short fromUlaw(byte b) {
        int u = ~b & 0xFF;
        int sign = u & 0x80, exp = (u >> 4) & 0x07, mant = u & 0x0F;
        int s = ((mant << 3) + 0x84) << exp;
        s -= 0x84;
        return (short) (sign != 0 ? -s : s);
    }
}
