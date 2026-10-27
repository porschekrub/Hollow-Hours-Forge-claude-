package com.helperbuddy.talk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.helperbuddy.HelperBuddy;
import com.helperbuddy.config.HBConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Optional real AI (off by default; set it up in config/helperbuddy-common.toml with a key). Providers: gemini (free key
 * from Google AI Studio), openai, anthropic (Claude), openrouter, groq, ollama (local, no key), custom (any
 * OpenAI-compatible URL).
 * Without it the built-in Thai/English brain does everything.
 */
public final class AiBridge {
    private AiBridge() {}

    public record Msg(String role, String text) {}

    private static HttpClient client;

    static HttpClient client() {
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client;
    }

    public static boolean enabled() {
        if (!HBConfig.AI_ENABLED.get()) return false;
        String p = provider();
        return p.equals("ollama") || p.equals("custom") || !HBConfig.AI_KEY.get().isBlank();
    }

    static String provider() {
        String p = HBConfig.AI_PROVIDER.get().trim().toLowerCase(Locale.ROOT);
        if (p.equals("claude")) return "anthropic";
        if (p.equals("google")) return "gemini";
        if (p.equals("chatgpt") || p.equals("gpt")) return "openai";
        return p;
    }

    static String model() {
        String m = HBConfig.AI_MODEL.get().trim();
        if (!m.isEmpty()) return m;
        return switch (provider()) {
            case "anthropic" -> "claude-haiku-5-5";
            case "openai" -> "gpt-4o-mini";
            case "ollama" -> "llama3.1";
            case "openrouter" -> "openrouter/auto";
            case "groq" -> "llama-3.3-70b-versatile";
            default -> "gemini-flash-latest";
        };
    }

    static String url() {
        String u = HBConfig.AI_URL.get().trim();
        if (!u.isEmpty()) {
            // "http://localhost:11434" is fine too: the chat endpoint is added
            if (!provider().equals("anthropic") && !u.endsWith("completions")) {
                u = u.replaceAll("/+$", "");
                u += u.endsWith("/v1") || u.endsWith("/openai") ? "/chat/completions" : "/v1/chat/completions";
            }
            return u;
        }
        return switch (provider()) {
            case "anthropic" -> "https://api.anthropic.com/v1/messages";
            case "openai" -> "https://api.openai.com/v1/chat/completions";
            case "ollama" -> "http://localhost:11434/v1/chat/completions";
            case "openrouter" -> "https://openrouter.ai/api/v1/chat/completions";
            case "groq" -> "https://api.groq.com/openai/v1/chat/completions";
            default -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
        };
    }

    /** Short spoken reply (cleaned, one line). Completes with null on failure. */
    public static CompletableFuture<String> chat(String system, List<Msg> msgs, int maxTokens, double temperature) {
        return send(system, msgs, maxTokens, temperature).thenApply(AiBridge::clean);
    }

    /** Raw reply text (for JSON answers). Completes with null on failure; never throws. */
    public static CompletableFuture<String> send(String system, List<Msg> msgs, int maxTokens, double temperature) {
        if (provider().equals("gemini") && HBConfig.AI_URL.get().isBlank()) return sendGemini(system, msgs, maxTokens, temperature);
        try {
            boolean anth = provider().equals("anthropic");
            JsonObject body = new JsonObject();
            body.addProperty("model", model());
            body.addProperty("max_tokens", maxTokens);
            body.addProperty("temperature", temperature);
            JsonArray arr = new JsonArray();
            if (anth) body.addProperty("system", system);
            else arr.add(msg("system", system));
            for (Msg m : msgs) arr.add(msg(m.role, m.text));
            body.add("messages", arr);
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url()))
                    .timeout(Duration.ofSeconds(Math.max(3, HBConfig.AI_TIMEOUT.get())))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            String key = HBConfig.AI_KEY.get().trim();
            if (anth) rb.header("x-api-key", key).header("anthropic-version", "2023-06-01");
            else if (!key.isEmpty()) rb.header("authorization", "Bearer " + key);
            return client().sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
                if (r.statusCode() / 100 != 2) {
                    HelperBuddy.LOG.warn("[Helper Buddy] AI request failed ({}): {}", r.statusCode(), trim(r.body(), 300));
                    return (String) null;
                }
                return parse(r.body(), anth);
            }).exceptionally(e -> {
                HelperBuddy.LOG.warn("[Helper Buddy] AI request failed: {}", e.toString());
                return null;
            });
        } catch (Exception e) {
            HelperBuddy.LOG.warn("[Helper Buddy] AI request could not be sent: {}", e.toString());
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Gemini's own endpoint (base, model is appended); tests point it at a local fake server. */
    static String geminiBase = "https://generativelanguage.googleapis.com/v1beta/models/";

    /**
     * Gemini through its native API with the key in the x-goog-api-key header. Google's newer "AQ." keys (the only
     * kind AI Studio hands out now) are refused by its OpenAI-compatible endpoint, the old "AIza" keys work here too.
     */
    static CompletableFuture<String> sendGemini(String system, List<Msg> msgs, int maxTokens, double temperature) {
        try {
            JsonObject body = new JsonObject();
            body.add("systemInstruction", parts(system));
            JsonArray contents = new JsonArray();
            for (Msg m : msgs) {
                JsonObject c = parts(m.text);
                c.addProperty("role", m.role.equals("assistant") ? "model" : "user");
                contents.add(c);
            }
            body.add("contents", contents);
            JsonObject gen = new JsonObject();
            // thinking models count their thoughts in this budget: leave room so the answer is not cut off
            gen.addProperty("maxOutputTokens", Math.max(maxTokens, 2048));
            gen.addProperty("temperature", temperature);
            body.add("generationConfig", gen);
            String model = model().replaceFirst("^models/", "");
            HttpRequest rq = HttpRequest.newBuilder(URI.create(geminiBase + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(Math.max(3, HBConfig.AI_TIMEOUT.get())))
                    .header("content-type", "application/json")
                    .header("x-goog-api-key", HBConfig.AI_KEY.get().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            return client().sendAsync(rq, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
                if (r.statusCode() / 100 != 2) {
                    HelperBuddy.LOG.warn("[Helper Buddy] {}", "AI request failed (" + r.statusCode() + "): " + trim(r.body(), 300));
                    return (String) null;
                }
                try {
                    JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                    StringBuilder sb = new StringBuilder();
                    JsonObject content = o.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content");
                    for (JsonElement p : content.getAsJsonArray("parts")) {
                        JsonObject po = p.getAsJsonObject();
                        if (po.has("thought") && po.get("thought").getAsBoolean()) continue;
                        if (po.has("text")) sb.append(po.get("text").getAsString());
                    }
                    return sb.length() == 0 ? null : sb.toString();
                } catch (Exception e) {
                    HelperBuddy.LOG.warn("[Helper Buddy] {}", "AI reply could not be read: " + trim(r.body(), 300));
                    return null;
                }
            }).exceptionally(e -> {
                HelperBuddy.LOG.warn("[Helper Buddy] {}", "AI request failed: " + e);
                return null;
            });
        } catch (Exception e) {
            HelperBuddy.LOG.warn("[Helper Buddy] {}", "AI request could not be sent: " + e);
            return CompletableFuture.completedFuture(null);
        }
    }

    static JsonObject parts(String text) {
        JsonObject t = new JsonObject();
        t.addProperty("text", text);
        JsonArray a = new JsonArray();
        a.add(t);
        JsonObject o = new JsonObject();
        o.add("parts", a);
        return o;
    }

    static JsonObject msg(String role, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", text);
        return o;
    }

    static String parse(String body, boolean anth) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            if (anth) {
                StringBuilder sb = new StringBuilder();
                for (JsonElement c : o.getAsJsonArray("content")) {
                    JsonObject co = c.getAsJsonObject();
                    if (co.has("text")) sb.append(co.get("text").getAsString());
                }
                return sb.toString();
            }
            JsonObject m = o.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            return m.get("content").getAsString();
        } catch (Exception e) {
            HelperBuddy.LOG.warn("[Helper Buddy] AI reply could not be read: {}", trim(body, 300));
            return null;
        }
    }

    /** Strips quotes, markdown and thinking tags; keeps it short and printable. */
    static String clean(String s) {
        if (s == null) return null;
        s = s.replaceAll("(?s)<think>.*?</think>", "").replace("**", "").replace("§", "").trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) s = s.substring(1, s.length() - 1).trim();
        s = s.replaceAll("\\s+", " ");
        if (s.isEmpty()) return null;
        return trim(s, 300);
    }

    /** The JSON object inside a reply (models like to wrap it in ``` fences or prose). */
    public static JsonObject jsonIn(String s) {
        if (s == null) return null;
        s = s.replaceAll("(?s)<think>.*?</think>", "");
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try {
            return JsonParser.parseString(s.substring(a, b + 1)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    static String trim(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
    }
}
