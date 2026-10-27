package com.hollowhours.ghost;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hollowhours.HollowHours;
import com.hollowhours.config.HHConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Optional language model for ghost speech. Off by default; turn it on in hollowhours-common.toml with a key.
 * Providers: gemini (free key from Google AI Studio), openai, anthropic (Claude), ollama (local, no key), custom
 * (any OpenAI-compatible URL), openrouter, groq. The model understands what the player typed (GhostTalk.understand)
 * and writes the ghost's words; the game still decides WHAT happens (chances, items taken, rage).
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
        if (!HHConfig.AI_ENABLED.get()) return false;
        String p = provider();
        return p.equals("ollama") || p.equals("custom") || !HHConfig.AI_KEY.get().isBlank();
    }

    static String provider() {
        String p = HHConfig.AI_PROVIDER.get().trim().toLowerCase(Locale.ROOT);
        if (p.equals("claude")) return "anthropic";
        if (p.equals("google")) return "gemini";
        if (p.equals("chatgpt") || p.equals("gpt")) return "openai";
        return p;
    }

    static String model() {
        String m = HHConfig.AI_MODEL.get().trim();
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
        String u = HHConfig.AI_URL.get().trim();
        if (!u.isEmpty()) {
            // a base url such as http://localhost:11434 or .../v1 is fine: the chat endpoint is added
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

    /** Sends a chat; completes with the cleaned one-line reply, or null on any failure. Never throws. */
    public static CompletableFuture<String> chat(String system, List<Msg> msgs, int maxTokens, double temperature) {
        return send(system, msgs, maxTokens, temperature).thenApply(AiBridge::clean);
    }

    /** The JSON object inside a reply (models like to wrap it in ``` fences or prose); null if there is none. */
    @javax.annotation.Nullable
    public static JsonObject jsonIn(@javax.annotation.Nullable String s) {
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

    /** Sends a chat; completes with the raw reply text, or null on any failure. Never throws. */
    public static CompletableFuture<String> send(String system, List<Msg> msgs, int maxTokens, double temperature) {
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
                    .timeout(Duration.ofSeconds(Math.max(3, HHConfig.AI_TIMEOUT.get())))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            String key = HHConfig.AI_KEY.get().trim();
            if (anth) {
                rb.header("x-api-key", key).header("anthropic-version", "2023-06-01");
            } else if (!key.isEmpty()) rb.header("authorization", "Bearer " + key);
            return client().sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> {
                        if (r.statusCode() / 100 != 2) {
                            HollowHours.LOG.warn("[Hollow Hours] AI request failed ({}): {}", r.statusCode(), trim(r.body(), 300));
                            return null;
                        }
                        return parse(r.body(), anth);
                    })
                    .exceptionally(e -> {
                        HollowHours.LOG.warn("[Hollow Hours] AI request failed: {}", e.toString());
                        return null;
                    });
        } catch (Exception e) {
            HollowHours.LOG.warn("[Hollow Hours] AI request could not be sent: {}", e.toString());
            return CompletableFuture.completedFuture(null);
        }
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
            HollowHours.LOG.warn("[Hollow Hours] AI reply could not be read: {}", trim(body, 300));
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
        return trim(s, 220);
    }

    static String trim(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
    }
}
