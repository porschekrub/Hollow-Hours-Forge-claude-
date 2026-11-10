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
 * A real voice for the helper's answers: Gemini's speech model reads the line (in the helper's mood), the server
 * sends it to the player as 8-bit mu-law pieces, the client plays it at the helper. Falls back to the babble.
 */
public final class VoiceTts {
    private VoiceTts() {}

    static final String[] VOICES = {"Puck", "Fenrir", "Charon", "Kore", "Leda", "Orus", "Zephyr", "Aoede"};
    static final Set<UUID> BUSY = new HashSet<>();
    /** recent clips (text + voice -> mu-law), so a repeated line costs nothing */
    static final Map<String, byte[]> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > 40;
        }
    };
    static final Map<String, Integer> RATE = new java.util.HashMap<>();
    static long pausedUntil;
    static int clipIds;
    /** speech models to try when the configured one is gone (newest first) */
    static final String[] FALLBACK = {"gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts", "gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"};
    /** the model that works (found by trying) */
    static String model;
    static int modelTry;

    static String model() {
        if (model != null) return model;
        String m = HBConfig.VOICE_MODEL.get().trim().replaceFirst("^models/", "");
        if (modelTry == 0 && !m.isEmpty()) return m;
        int i = Math.max(0, modelTry - (m.isEmpty() ? 0 : 1));
        return i < FALLBACK.length ? FALLBACK[i] : null;
    }

    static String voiceFor(HelperEntity h) {
        String v = HBConfig.VOICE_NAME.get().trim();
        if (!v.isEmpty()) return v;
        return VOICES[Math.floorMod(h.getUUID().hashCode(), VOICES.length)];
    }

    /** How much faster and higher a helper's cartoon voice is played (its own, 1.28 - 1.46). */
    static float cartoonPitch(HelperEntity h) {
        return 1.28f + Math.floorMod(h.getUUID().hashCode() >> 4, 7) * 0.03f;
    }

    static String how(int style, boolean cartoon) {
        if (cartoon) return switch (style) {
            case 0 -> "Say this like a happy, bouncy cartoon character, a bit slowly and clearly";
            case 2 -> "Say this like a grumpy, mocking cartoon character, a bit slowly and clearly";
            default -> "Say this like a cheeky, playful cartoon character, a bit slowly and clearly";
        };
        return switch (style) {
            case 0 -> "Say this in a friendly, cheerful voice";
            case 2 -> "Say this in an annoyed, rude, mocking voice";
            default -> "Say this in a cheeky, sarcastic, playful voice";
        };
    }

    /** @return false when the real voice can't be made now (the caller babbles instead) */
    static boolean speak(ServerPlayer p, HelperEntity h, String text, int style, boolean cartoon) {
        if (System.currentTimeMillis() < pausedUntil || BUSY.contains(h.getUUID())) return false;
        String voice = voiceFor(h);
        String key = voice + "|" + style + "|" + cartoon + "|" + text;
        float pitch = cartoon ? cartoonPitch(h) : 1f;
        byte[] cached;
        synchronized (CACHE) {
            cached = CACHE.get(key);
        }
        if (cached != null) {
            send(p, h, cached, Math.round(RATE.getOrDefault(key, 24000) * pitch));
            return true;
        }
        BUSY.add(h.getUUID());
        MinecraftServer server = p.server;
        UUID pid = p.getUUID(), hid = h.getUUID();
        try {
            JsonObject body = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject c = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", how(style, cartoon) + ": " + text);
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
            String model = model();
            if (model == null) {
                BUSY.remove(hid);
                return false;
            }
            HttpRequest rq = HttpRequest.newBuilder(URI.create(AiBridge.geminiBase + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(Math.max(10, HBConfig.AI_TIMEOUT.get())))
                    .header("content-type", "application/json").header("x-goog-api-key", HBConfig.AI_KEY.get().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            AiBridge.client().sendAsync(rq, HttpResponse.BodyHandlers.ofString()).whenComplete((r, err) -> {
                byte[] ulaw = null;
                int rate = 24000;
                if (err != null) HelperBuddy.LOG.warn("[Helper Buddy] voice request failed: {}", err.toString());
                else if (r.statusCode() / 100 != 2) {
                    HelperBuddy.LOG.warn("[Helper Buddy] voice request failed ({}, {}): {}", r.statusCode(), model, AiBridge.trim(r.body(), 300));
                    // that model is gone: the next one next time; out of quota or not allowed: babble for a while
                    if ((r.statusCode() == 404 || r.statusCode() == 400) && VoiceTts.model == null) modelTry++;
                    else if (r.statusCode() == 429 || r.statusCode() == 403) pausedUntil = System.currentTimeMillis() + 10 * 60_000L;
                } else {
                    VoiceTts.model = model;
                    try {
                        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                        for (JsonElement cand : o.getAsJsonArray("candidates")) {
                            for (JsonElement pe : cand.getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts")) {
                                JsonObject po = pe.getAsJsonObject();
                                if (!po.has("inlineData")) continue;
                                JsonObject d = po.getAsJsonObject("inlineData");
                                Matcher m = Pattern.compile("rate=(\\d+)").matcher(d.has("mimeType") ? d.get("mimeType").getAsString() : "");
                                if (m.find()) rate = Integer.parseInt(m.group(1));
                                byte[] raw = Base64.getDecoder().decode(d.get("data").getAsString());
                                int[] wavRate = new int[1];
                                raw = stripWav(raw, wavRate);
                                if (wavRate[0] > 0) rate = wavRate[0];
                                ulaw = toUlaw(raw);
                                break;
                            }
                            if (ulaw != null) break;
                        }
                    } catch (Exception e) {
                        HelperBuddy.LOG.warn("[Helper Buddy] voice answer could not be read: {}", e.toString());
                    }
                }
                byte[] clip = ulaw;
                int clipRate = rate;
                server.execute(() -> {
                    BUSY.remove(hid);
                    ServerPlayer pl = server.getPlayerList().getPlayer(pid);
                    HelperEntity hh = Helpers.byUuid(server, hid);
                    if (pl == null || hh == null) return;
                    if (clip == null) {
                        Voice.babble(pl, hh, text, style);
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
        } catch (Exception e) {
            BUSY.remove(hid);
            HelperBuddy.LOG.warn("[Helper Buddy] voice request could not be sent: {}", e.toString());
            return false;
        }
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
