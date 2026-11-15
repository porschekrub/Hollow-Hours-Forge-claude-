package com.veritypal.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.veritypal.VerityPal;
import com.veritypal.config.PalClientConfig;
import varmite.verity.VerityConfig;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/** Google Gemini, for Verity's Thai voice and ears (the same requests Helper Buddy uses). */
public final class Gemini {
    private Gemini() {}

    static HttpClient client;
    /** The tests point it at a local fake server. */
    static final String BASE = System.getProperty("veritypal.geminibase", "https://generativelanguage.googleapis.com/v1beta/models/");
    public static volatile String lastError = "";

    static HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client;
    }

    /** The key: Verity Pal's own setting, else Verity JE's AI key when her AI is Gemini. */
    public static String key() {
        String k = PalClientConfig.ready() ? PalClientConfig.GEMINI_KEY.get().trim() : "";
        if (!k.isEmpty()) return k;
        try {
            if (VerityConfig.AI_PROVIDER.get().name().equals("GEMINI")) {
                String v = VerityConfig.API_KEY.get();
                if (v != null) return v.trim();
            }
        } catch (Throwable ignored) {
        }
        return System.getProperty("veritypal.geminikey", "");
    }

    /** The raw answer of generateContent, or null (lastError says why). */
    @Nullable
    static String post(String model, JsonObject body, int timeoutSeconds) {
        try {
            HttpRequest rq = HttpRequest.newBuilder(URI.create(BASE + model.replaceFirst("^models/", "") + ":generateContent"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("content-type", "application/json")
                    .header("x-goog-api-key", key())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            HttpResponse<String> r = client().send(rq, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) {
                lastError = r.statusCode() + " " + trim(r.body(), 200);
                VerityPal.LOG.warn("[Verity Pal] Gemini ({}) said {}", model, lastError);
                return null;
            }
            lastError = "";
            return r.body();
        } catch (Exception e) {
            lastError = e.toString();
            VerityPal.LOG.warn("[Verity Pal] Gemini ({}) could not be reached: {}", model, e.toString());
            return null;
        }
    }

    static String trim(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
    }

    static JsonObject text(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("text", t);
        return o;
    }

    /** The first sound in an answer: [rate, pcm16le...] as {rate} + bytes, or null. */
    @Nullable
    static Object[] audioIn(String response) {
        JsonObject o = JsonParser.parseString(response).getAsJsonObject();
        JsonArray cands = o.getAsJsonArray("candidates");
        if (cands == null) return null;
        for (JsonElement cand : cands) {
            JsonObject content = cand.getAsJsonObject().getAsJsonObject("content");
            if (content == null || !content.has("parts")) continue;
            for (JsonElement pe : content.getAsJsonArray("parts")) {
                JsonObject po = pe.getAsJsonObject();
                if (!po.has("inlineData")) continue;
                JsonObject d = po.getAsJsonObject("inlineData");
                int rate = 24000;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("rate=(\\d+)").matcher(d.has("mimeType") ? d.get("mimeType").getAsString() : "");
                if (m.find()) rate = Integer.parseInt(m.group(1));
                byte[] raw = Base64.getDecoder().decode(d.get("data").getAsString());
                int[] wavRate = new int[1];
                raw = stripWav(raw, wavRate);
                if (wavRate[0] > 0) rate = wavRate[0];
                return new Object[] {rate, raw};
            }
        }
        return null;
    }

    /** The words in an answer ("" when there are none). */
    static String textIn(String response) {
        JsonObject o = JsonParser.parseString(response).getAsJsonObject();
        StringBuilder sb = new StringBuilder();
        JsonArray cand = o.getAsJsonArray("candidates");
        if (cand == null || cand.isEmpty()) return "";
        JsonObject content = cand.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null || !content.has("parts")) return "";
        for (JsonElement p : content.getAsJsonArray("parts")) {
            JsonObject po = p.getAsJsonObject();
            if (po.has("thought") && po.get("thought").getAsBoolean()) continue;
            if (po.has("text")) sb.append(po.get("text").getAsString());
        }
        return sb.toString().replaceAll("[\"“”]", "").replaceAll("\\s+", " ").trim();
    }

    /** A WAV file -> its PCM samples (rate in rate[0]); anything else is returned as it is. */
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
}
