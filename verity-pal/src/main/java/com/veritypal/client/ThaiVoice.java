package com.veritypal.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.veritypal.VerityPal;
import com.veritypal.config.PalClientConfig;
import com.veritypal.talk.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import varmite.verity.VerityConfig;
import varmite.verity.client.audio.TTSHandler;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.verity.VerityEntity;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Verity's voice in Thai: Verity JE's own voices only speak English, so the lines in Thai (or all of them) are read by
 * Gemini's speech model and played the way Verity JE plays hers (from her body, quieter when far, muffled in a pocket,
 * her mouth moving).
 */
public final class ThaiVoice {
    private ThaiVoice() {}

    static final ExecutorService ONE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VerityPal-Voice");
        t.setDaemon(true);
        return t;
    });
    /** speech models to try, newest first (the one that works is kept) */
    static final String[] MODELS = {"gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts", "gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"};
    static String model;
    static int modelTry;
    static long pausedUntil;
    static boolean hinted;
    /** (tests) clips made */
    public static volatile int made;
    static final Map<String, Object[]> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object[]> e) {
            return size() > 40;
        }
    };

    static String mode() {
        return PalClientConfig.ready() ? PalClientConfig.THAI_VOICE.get().trim().toLowerCase(Locale.ROOT) : "auto";
    }

    /** Called instead of Verity JE's voice: true when this line is ours to say. */
    public static boolean take(String text, @Nullable Player player, @Nullable VerityEntity verity) {
        if (text == null || text.isBlank()) return false;
        String m = mode();
        if (m.equals("off")) return false;
        try {
            if (!VerityConfig.USE_TTS.get()) return false;
        } catch (Throwable ignored) {
        }
        boolean thai = Lang.hasThai(text);
        if (!m.equals("always") && !thai) return false;
        if (Gemini.key().isEmpty() || System.currentTimeMillis() < pausedUntil) {
            if (thai && !hinted && Gemini.key().isEmpty()) {
                hinted = true;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.displayClientMessage(Component.translatable("veritypal.hint.thai_voice"), false);
            }
            return false;
        }
        ONE.submit(() -> play(text, player, verity));
        return true;
    }

    @Nullable
    static String model() {
        if (model != null) return model;
        String m = PalClientConfig.ready() ? PalClientConfig.SPEECH_MODEL.get().trim() : "";
        if (modelTry == 0 && !m.isEmpty()) return m;
        int i = Math.max(0, modelTry - (m.isEmpty() ? 0 : 1));
        return i < MODELS.length ? MODELS[i] : null;
    }

    /** [rate, pcm] for the line, or null. */
    @Nullable
    static Object[] fetch(String text) {
        String voice = PalClientConfig.ready() ? PalClientConfig.VOICE_NAME.get().trim() : "Puck";
        String style = PalClientConfig.ready() ? PalClientConfig.VOICE_STYLE.get().trim() : "";
        String key = voice + "|" + style + "|" + text;
        synchronized (CACHE) {
            Object[] c = CACHE.get(key);
            if (c != null) return c;
        }
        for (int attempt = 0; attempt < MODELS.length + 1; attempt++) {
            String mdl = model();
            if (mdl == null) return null;
            JsonObject body = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject c = new JsonObject();
            JsonArray parts = new JsonArray();
            parts.add(Gemini.text((style.isEmpty() ? "Say this" : "Say this like " + style) + ": " + text));
            c.add("parts", parts);
            contents.add(c);
            body.add("contents", contents);
            JsonObject gen = new JsonObject();
            JsonArray mods = new JsonArray();
            mods.add("AUDIO");
            gen.add("responseModalities", mods);
            JsonObject speech = new JsonObject(), vc = new JsonObject(), pre = new JsonObject();
            pre.addProperty("voiceName", voice.isEmpty() ? "Puck" : voice);
            vc.add("prebuiltVoiceConfig", pre);
            speech.add("voiceConfig", vc);
            gen.add("speechConfig", speech);
            body.add("generationConfig", gen);
            String r = Gemini.post(mdl, body, 40);
            if (r == null) {
                String err = Gemini.lastError;
                if ((err.startsWith("404") || err.startsWith("400")) && model == null) {
                    modelTry++;
                    continue;
                }
                if (err.startsWith("429") || err.startsWith("403")) pausedUntil = System.currentTimeMillis() + 5 * 60_000L;
                return null;
            }
            model = mdl;
            Object[] a = Gemini.audioIn(r);
            if (a != null) synchronized (CACHE) {
                CACHE.put(key, a);
            }
            return a;
        }
        return null;
    }

    static void play(String text, @Nullable Player player, @Nullable VerityEntity verity) {
        Object[] a = fetch(text);
        if (a == null) {
            VerityPal.LOG.warn("[Verity Pal] no Thai voice for this line ({})", Gemini.lastError);
            return;
        }
        made++;
        int rate = (Integer) a[0];
        byte[] pcm = ((byte[]) a[1]).clone();
        float pitch = PalClientConfig.ready() ? PalClientConfig.VOICE_PITCH.get().floatValue() : 1.12f;
        float vol = PalClientConfig.ready() ? PalClientConfig.VOICE_VOLUME.get().floatValue() : 1f;
        try {
            Minecraft mc = Minecraft.getInstance();
            vol *= mc.options.getSoundSourceVolume(SoundSource.MASTER) * mc.options.getSoundSourceVolume(SoundSource.VOICE);
        } catch (Throwable ignored) {
        }
        scale(pcm, vol);
        VerityPal.LOG.info("[Verity Pal] THAI VOICE: {} bytes at {} Hz for \"{}\"", pcm.length, rate, text);
        AudioFormat format = new AudioFormat(rate * pitch, 16, 1, true, false);
        TTSHandler.cancelCurrentSpeech = false;
        TTSHandler.resetMuffledFilter();
        talking(verity, true);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format))) {
            line.open(format);
            line.start();
            int chunk = 4096;
            for (int off = 0; off < pcm.length; off += chunk) {
                if (TTSHandler.cancelCurrentSpeech) {
                    line.flush();
                    break;
                }
                int len = Math.min(chunk, pcm.length - off);
                if (TTSHandler.isVerityMuffled(player, verity)) TTSHandler.applyMuffledFilter(pcm, off, len);
                TTSHandler.apply3DEffect(line, player, verity);
                line.write(pcm, off, len);
            }
            if (!TTSHandler.cancelCurrentSpeech) line.drain();
        } catch (Throwable t) {
            VerityPal.LOG.warn("[Verity Pal] the Thai voice could not be played: {}", t.toString());
        } finally {
            talking(verity, false);
        }
    }

    static void talking(@Nullable VerityEntity verity, boolean on) {
        Minecraft.getInstance().execute(() -> {
            if (verity != null) verity.clientIsTalking = on;
            VerityState.isClientTalking = on;
        });
    }

    static void scale(byte[] pcm, float v) {
        if (Math.abs(v - 1f) < 0.01f) return;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int o = Math.max(-32768, Math.min(32767, Math.round(s * v)));
            pcm[i] = (byte) o;
            pcm[i + 1] = (byte) (o >> 8);
        }
    }
}
