package com.helperbuddy.client;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.talk.VoiceCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The helper's own voice, made on the player's computer with sherpa-onnx: free, no key, no internet. English lines
 * are read by KittenTTS (in the mod's jar, eight speakers); Thai lines by Meta's MMS-TTS Thai model, downloaded once the
 * first time a helper speaks Thai (it may not be shipped inside the mod: its licence is CC-BY-NC 4.0). Lines in other
 * languages, and everything while a model is not ready, are babbled.
 */
public final class LocalVoice {
    private LocalVoice() {}

    static final String KITTEN = "kitten-nano-en-v0_8-int8";
    static final String THAI_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mms-tha.tar.bz2";
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HelperBuddy-Voice");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    static final AtomicInteger PENDING = new AtomicInteger();
    /** this computer can't run it (no native library for it, or it failed): babble from now on */
    static volatile boolean broken;
    static volatile boolean thaiDownloading, thaiFailed, told;
    /** (tests) lines made, and the last one */
    public static volatile int made;
    public static volatile float lastSeconds, lastRms;
    public static volatile long lastMillis;

    static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("helperbuddy").resolve("voices");
    }

    static boolean thai(String s) {
        int th = 0, latin = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '฀' && c <= '๿') th++;
            else if (c < 128 && Character.isLetter(c)) latin++;
        }
        return th > 0 && th >= latin;
    }

    static boolean english(String s) {
        int latin = 0, other = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 128) {
                if (Character.isLetter(c)) latin++;
            } else if (Character.isLetter(c)) other++;
        }
        return latin > 0 && latin >= other * 2;
    }

    static String clean(String s) {
        String t = s.replaceAll("§.", "").replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    /**
     * Speaks a line at the helper. False when it can't (another language, no model yet, not possible on this
     * computer): the caller babbles instead.
     */
    public static boolean say(int entity, String raw, String voiceId, boolean cartoon, int seed) {
        if (broken) return false;
        String text = clean(raw);
        if (text.isEmpty()) return true;
        boolean th = thai(text);
        if (!th && !english(text)) return false;
        if (th && !thaiReady()) {
            startThaiDownload();
            return false;
        }
        if (PENDING.get() >= 4) return true; // it is behind: this line is skipped rather than said late
        PENDING.incrementAndGet();
        VoiceCatalog.V v = VoiceCatalog.of(voiceId, seed);
        WORKER.execute(() -> {
            try {
                Engine.Out out = Engine.make(text, th, v);
                if (out == null) return;
                int rate = Math.round(out.rate() * v.pitch() * (cartoon ? 1.3f : 1f));
                Minecraft.getInstance().execute(() -> VoiceClient.local(entity, out.pcm(), rate));
            } catch (Throwable t) {
                failed(t);
            } finally {
                PENDING.decrementAndGet();
            }
        });
        return true;
    }

    /** Makes a line right now (tests): seconds of speech, 0 when it could not. */
    public static float makeNow(String text, String voiceId) {
        try {
            return WORKER.submit(() -> {
                Engine.Out out = Engine.make(clean(text), thai(text), VoiceCatalog.of(voiceId, 0));
                return out == null ? 0f : out.pcm().length / (float) out.rate();
            }).get();
        } catch (Exception e) {
            HelperBuddy.LOG.warn("[Helper Buddy] voice test failed: {}", e.toString());
            return 0f;
        }
    }

    static void failed(Throwable t) {
        HelperBuddy.LOG.warn("[Helper Buddy] the helper's own voice does not work on this computer, babbling instead: {}", t.toString());
        if (t instanceof LinkageError || t instanceof UnsupportedOperationException) broken = true;
        if (!told) {
            told = true;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("§8[Helper Buddy] The helper's own voice could not start on this computer ("
                        + t.getClass().getSimpleName() + "), so it babbles. Details in logs/latest.log"), false);
            });
        }
    }

    static void note(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("§8[Helper Buddy] " + text), false);
        });
    }

    // ------------------------------------------------------------------ the voice files
    /** The English voice: unpacked from the jar the first time. */
    static Path english() throws Exception {
        Path to = dir().resolve("kitten-en");
        Path done = to.resolve("ready.txt");
        if (Files.exists(done) && Files.readString(done).trim().equals(KITTEN)) return to;
        String base = "/helperbuddy/voices/kitten-en/";
        String list;
        try (InputStream in = LocalVoice.class.getResourceAsStream(base + "files.txt")) {
            if (in == null) throw new UnsupportedOperationException("the English voice is missing from the jar");
            list = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String rel : list.split("\n")) {
            if (rel.isBlank()) continue;
            Path f = to.resolve(rel.trim());
            Files.createDirectories(f.getParent());
            try (InputStream in = LocalVoice.class.getResourceAsStream(base + rel.trim())) {
                if (in == null) throw new java.io.FileNotFoundException(rel);
                Files.copy(in, f, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.writeString(done, KITTEN);
        return to;
    }

    static Path thaiDir() {
        return dir().resolve("mms-tha");
    }

    public static boolean thaiReady() {
        return Files.exists(thaiDir().resolve("ready.txt"));
    }

    /** The Thai voice: downloaded once (108 MB) from the sherpa-onnx project, in the background. */
    public static void startThaiDownload() {
        if (thaiDownloading || thaiFailed || thaiReady() || !HBConfig.THAI_VOICE_DOWNLOAD.get()) return;
        thaiDownloading = true;
        note("Downloading the helper's Thai voice (108 MB, only this once). It babbles in Thai until then. / กำลังโหลดเสียงพูดไทยของผู้ช่วย (108 MB ครั้งเดียว)");
        Thread t = new Thread(() -> {
            Path tmp = dir().resolve("mms-tha.download");
            try {
                Files.createDirectories(dir());
                java.net.http.HttpClient c = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                        .connectTimeout(java.time.Duration.ofSeconds(20)).build();
                var rs = c.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(THAI_URL)).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofFile(tmp));
                if (rs.statusCode() / 100 != 2) throw new java.io.IOException("HTTP " + rs.statusCode());
                Path to = thaiDir();
                Files.createDirectories(to);
                int got = 0;
                try (InputStream raw = Files.newInputStream(tmp);
                     var bz = new org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(new java.io.BufferedInputStream(raw));
                     var tar = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bz)) {
                    org.apache.commons.compress.archivers.tar.TarArchiveEntry e;
                    while ((e = tar.getNextTarEntry()) != null) {
                        String name = e.getName().replaceFirst("^.*/", "");
                        if (e.isDirectory() || !(name.equals("model.onnx") || name.equals("tokens.txt") || name.equals("README.md"))) continue;
                        try (OutputStream o = Files.newOutputStream(to.resolve(name))) {
                            tar.transferTo(o);
                        }
                        if (!name.equals("README.md")) got++;
                    }
                }
                if (got < 2) throw new java.io.IOException("the download did not hold the Thai voice");
                Files.writeString(to.resolve("LICENSE.txt"), "Meta MMS-TTS Thai (facebook/mms-tts-tha), CC-BY-NC 4.0, converted for sherpa-onnx by the k2-fsa project.\n");
                Files.writeString(to.resolve("ready.txt"), "mms-tha");
                note("§aThe Thai voice is ready. / เสียงพูดไทยพร้อมแล้ว");
            } catch (Exception e) {
                thaiFailed = true;
                HelperBuddy.LOG.warn("[Helper Buddy] the Thai voice could not be downloaded: {}", e.toString());
                note("§cThe Thai voice could not be downloaded§8 (" + e.getMessage() + "): it babbles in Thai. It tries again next time the game starts. / โหลดเสียงไทยไม่สำเร็จ จะลองใหม่ตอนเปิดเกมครั้งหน้า");
            } finally {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                }
                thaiDownloading = false;
            }
        }, "HelperBuddy-ThaiVoice");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ the speech engine
    static final class Engine {
        record Out(short[] pcm, int rate) {}

        //#if FORGE && MC<12100
        static com.k2fsa.sherpa.onnx.OfflineTts en, th;
        static Set<Integer> thaiChars;

        static com.k2fsa.sherpa.onnx.OfflineTts english() throws Exception {
            if (en == null) {
                Path d = LocalVoice.english();
                var kitten = com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig.builder().setModel(d.resolve("model.int8.onnx").toString())
                        .setVoices(d.resolve("voices.bin").toString()).setTokens(d.resolve("tokens.txt").toString()).setDataDir(d.resolve("espeak-ng-data").toString()).build();
                var model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig.builder().setKitten(kitten).setNumThreads(2).setDebug(false).build();
                en = new com.k2fsa.sherpa.onnx.OfflineTts(com.k2fsa.sherpa.onnx.OfflineTtsConfig.builder().setModel(model).build());
            }
            return en;
        }

        static com.k2fsa.sherpa.onnx.OfflineTts thai() throws Exception {
            if (th == null) {
                Path d = thaiDir();
                Set<Integer> ok = new HashSet<>();
                for (String line : Files.readAllLines(d.resolve("tokens.txt"), StandardCharsets.UTF_8)) {
                    int sp = line.lastIndexOf(' ');
                    String tok = sp <= 0 ? line : line.substring(0, sp);
                    if (tok.isEmpty() && line.startsWith(" ")) tok = " ";
                    if (tok.codePointCount(0, tok.length()) == 1) ok.add(tok.codePointAt(0));
                }
                ok.add((int) ' ');
                thaiChars = ok;
                var vits = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig.builder().setModel(d.resolve("model.onnx").toString()).setTokens(d.resolve("tokens.txt").toString()).build();
                var model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig.builder().setVits(vits).setNumThreads(2).setDebug(false).build();
                th = new com.k2fsa.sherpa.onnx.OfflineTts(com.k2fsa.sherpa.onnx.OfflineTtsConfig.builder().setModel(model).build());
            }
            return th;
        }

        static Out make(String text, boolean thai, VoiceCatalog.V v) throws Exception {
            long t0 = System.currentTimeMillis();
            com.k2fsa.sherpa.onnx.GeneratedAudio g;
            if (thai) {
                var tts = thai();
                StringBuilder sb = new StringBuilder();
                String t = ThaiText.numbers(text);
                for (int i = 0; i < t.length(); ) {
                    int cp = t.codePointAt(i);
                    sb.append(thaiChars.contains(cp) ? new String(Character.toChars(cp)) : " ");
                    i += Character.charCount(cp);
                }
                String said = sb.toString().replaceAll("\\s+", " ").trim();
                if (said.isEmpty()) return null;
                g = tts.generate(said, 0, v.speed());
            } else {
                var tts = english();
                String said = text.replaceAll("[^\\x20-\\x7E]", " ").replaceAll("\\s+", " ").trim();
                if (said.isEmpty()) return null;
                g = tts.generate(said, Math.floorMod(v.sid(), Math.max(1, tts.getNumSpeakers())), v.speed());
            }
            float[] s = g.getSamples();
            if (s == null || s.length == 0) return null;
            float peak = 0;
            double sum = 0;
            for (float f : s) {
                peak = Math.max(peak, Math.abs(f));
                sum += f * f;
            }
            float gain = peak > 0 && peak < 0.6f ? 0.85f / peak : 1f;
            short[] pcm = new short[s.length];
            for (int i = 0; i < s.length; i++) pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(s[i] * gain * 32767)));
            made++;
            lastSeconds = s.length / (float) g.getSampleRate();
            lastRms = (float) Math.sqrt(sum / s.length) * gain;
            lastMillis = System.currentTimeMillis() - t0;
            return new Out(pcm, g.getSampleRate());
        }
        //#else
        //$$ static Out make(String text, boolean thai, VoiceCatalog.V v) {
        //$$     throw new UnsupportedOperationException("the mod's own voice is only in the Forge 1.20.1 build for now");
        //$$ }
        //#endif
    }
}
