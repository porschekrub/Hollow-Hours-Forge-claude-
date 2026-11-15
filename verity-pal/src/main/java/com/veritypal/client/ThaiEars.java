package com.veritypal.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.veritypal.VerityPal;
import com.veritypal.config.PalClientConfig;
import varmite.verity.VerityConfig;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;

/**
 * Verity's ears in any language: Verity JE's built-in speech-to-text only knows English, so (when set) what is said into
 * the microphone is written down by Gemini instead, Thai included. Her name said in Thai becomes her name.
 */
public final class ThaiEars {
    private ThaiEars() {}

    static String mode() {
        return PalClientConfig.ready() ? PalClientConfig.EARS.get().trim().toLowerCase(Locale.ROOT) : "auto";
    }

    static boolean ours() {
        String m = mode();
        if (m.equals("off") || Gemini.key().isEmpty()) return false;
        if (m.equals("on") || m.equals("always")) return true;
        try {
            return VerityConfig.STT_PROVIDER.get().name().equals("NATIVE");
        } catch (Throwable t) {
            return false;
        }
    }

    /** What was said, "" for nothing, or null when Verity JE should listen as usual. Runs on Verity JE's own thread. */
    @Nullable
    public static String hear(byte[] pcm, AudioFormat format) {
        if (pcm == null || pcm.length == 0 || !ours()) return null;
        try {
            ByteArrayOutputStream wav = new ByteArrayOutputStream();
            try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / format.getFrameSize())) {
                AudioSystem.write(in, AudioFileFormat.Type.WAVE, wav);
            }
            JsonObject audio = new JsonObject(), inline = new JsonObject();
            inline.addProperty("mime_type", "audio/wav");
            inline.addProperty("data", Base64.getEncoder().encodeToString(wav.toByteArray()));
            audio.add("inline_data", inline);
            JsonArray ps = new JsonArray();
            ps.add(audio);
            ps.add(Gemini.text("A Minecraft player is talking to their companion, a talking yellow ball called " + com.veritypal.V.name()
                    + " (in Thai often เวริตี้), in any language (often Thai or English). Write down exactly what they say, in the language they speak and in its own script "
                    + "(Thai in Thai script). Only those words: no quotes, no notes, no translation. If nobody speaks, reply with nothing."));
            JsonObject c = new JsonObject();
            c.addProperty("role", "user");
            c.add("parts", ps);
            JsonArray contents = new JsonArray();
            contents.add(c);
            JsonObject body = new JsonObject();
            body.add("contents", contents);
            JsonObject gen = new JsonObject();
            gen.addProperty("maxOutputTokens", 2048);
            gen.addProperty("temperature", 0);
            body.add("generationConfig", gen);
            String model = PalClientConfig.ready() ? PalClientConfig.EARS_MODEL.get().trim() : "gemini-flash-lite-latest";
            String r = Gemini.post(model.isEmpty() ? "gemini-flash-lite-latest" : model, body, 30);
            if (r == null) return null;
            String said = Gemini.textIn(r);
            if (said.isBlank()) return "";
            said = said.replaceAll("เวอ?ร?ริตี้?|วีริตี้", com.veritypal.V.name());
            VerityPal.LOG.info("[Verity Pal] heard: {}", said);
            return said;
        } catch (Exception e) {
            VerityPal.LOG.warn("[Verity Pal] could not listen with Gemini: {}", e.toString());
            return null;
        }
    }
}
