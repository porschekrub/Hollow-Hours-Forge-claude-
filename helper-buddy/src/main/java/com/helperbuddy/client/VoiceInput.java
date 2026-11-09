package com.helperbuddy.client;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.net.C2SVoiceIn;
import com.helperbuddy.net.HBNet;
import com.helperbuddy.talk.VoiceTts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Hold the talk key and speak: the microphone is recorded (OpenAL capture, 16 kHz) while the key is down, then sent
 * to the server, where the AI writes it down and the helpers do what was said.
 */
public final class VoiceInput {
    private VoiceInput() {}

    static final int RATE = 16000;
    static final int MAX = RATE * 12;
    static final short[] BUF = new short[MAX];
    static int n, peak, shown;
    static long dev;
    static boolean rec, broken;
    static int clip;

    static boolean thai(Minecraft mc) {
        return mc.options.languageCode != null && mc.options.languageCode.startsWith("th");
    }

    static void bar(Minecraft mc, String th, String en) {
        mc.gui.setOverlayMessage(Component.literal(thai(mc) ? th : en), false);
    }

    /** Every client tick. */
    static void tick(Minecraft mc) {
        boolean down = mc.player != null && mc.level != null && mc.screen == null && VoiceKeys.TALK.isDown();
        if (down && !rec) start(mc);
        if (!rec) return;
        pull();
        if (!down || n >= MAX) {
            stop();
            send(mc, java.util.Arrays.copyOf(BUF, n));
            return;
        }
        if (mc.level.getGameTime() % 3 == 0) {
            int level = Math.min(10, peakRecent() / 1500);
            String meter = "§a" + "|".repeat(level) + "§8" + "|".repeat(10 - level);
            int secs = n / RATE;
            bar(mc, "§c● §fกำลังฟัง... " + meter + " §7" + secs + "s (ปล่อยปุ่มเพื่อส่ง)", "§c● §fListening... " + meter + " §7" + secs + "s (let go to send)");
        }
    }

    static void start(Minecraft mc) {
        if (broken) {
            bar(mc, "§cไม่เจอไมโครโฟน (ดูว่าเสียบไมค์แล้ว และอนุญาตให้เกมใช้ไมค์)", "§cNo microphone (check it's plugged in and the game may use it)");
            return;
        }
        try {
            dev = org.lwjgl.openal.ALC11.alcCaptureOpenDevice((CharSequence) null, RATE, org.lwjgl.openal.AL10.AL_FORMAT_MONO16, RATE);
            if (dev == 0) throw new IllegalStateException("no capture device");
            org.lwjgl.openal.ALC11.alcCaptureStart(dev);
        } catch (Throwable t) {
            broken = true;
            dev = 0;
            HelperBuddy.LOG.warn("[Helper Buddy] no microphone: {}", t.toString());
            bar(mc, "§cไม่เจอไมโครโฟน (ดูว่าเสียบไมค์แล้ว และอนุญาตให้เกมใช้ไมค์)", "§cNo microphone (check it's plugged in and the game may use it)");
            return;
        }
        rec = true;
        n = 0;
        peak = 0;
        shown = 0;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.8f));
    }

    static void pull() {
        if (dev == 0) return;
        try {
            int avail = org.lwjgl.openal.ALC10.alcGetInteger(dev, org.lwjgl.openal.ALC11.ALC_CAPTURE_SAMPLES);
            if (avail <= 0) return;
            int k = Math.min(avail, MAX - n);
            if (k <= 0) return;
            short[] tmp = new short[avail];
            org.lwjgl.openal.ALC11.alcCaptureSamples(dev, tmp, avail);
            System.arraycopy(tmp, 0, BUF, n, k);
            for (int i = n; i < n + k; i++) peak = Math.max(peak, Math.abs((int) BUF[i]));
            n += k;
        } catch (Throwable t) {
            broken = true;
            stop();
        }
    }

    static int peakRecent() {
        int m = 0;
        for (int i = Math.max(0, n - RATE / 10); i < n; i++) m = Math.max(m, Math.abs((int) BUF[i]));
        return m;
    }

    static void stop() {
        rec = false;
        if (dev == 0) return;
        try {
            org.lwjgl.openal.ALC11.alcCaptureStop(dev);
            org.lwjgl.openal.ALC11.alcCaptureCloseDevice(dev);
        } catch (Throwable ignored) {
        }
        dev = 0;
    }

    /** Sends a recording to the server (louder when it was quiet). */
    public static void send(Minecraft mc, short[] pcm) {
        if (pcm.length < RATE * 3 / 10) {
            bar(mc, "§7กดปุ่มค้างไว้ แล้วพูด แล้วค่อยปล่อย", "§7Hold the key, speak, then let go");
            return;
        }
        int pk = 0;
        for (short s : pcm) pk = Math.max(pk, Math.abs((int) s));
        if (pk < 500) {
            bar(mc, "§7ไม่ได้ยินเสียงเลย (ไมค์ปิดอยู่หรือเปล่า?)", "§7Didn't hear anything (is the mic muted?)");
            return;
        }
        double gain = Math.min(8.0, 20000.0 / pk);
        byte[] le = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            int v = (int) Math.max(-32768, Math.min(32767, Math.round(pcm[i] * gain)));
            le[2 * i] = (byte) v;
            le[2 * i + 1] = (byte) (v >> 8);
        }
        byte[] ulaw = VoiceTts.toUlaw(le);
        int id = ++clip;
        int part = 0;
        for (int off = 0; off < ulaw.length || part == 0; off += 24000, part++) {
            int len = Math.min(24000, ulaw.length - off);
            HBNet.toServer(new C2SVoiceIn(id, part, off + len >= ulaw.length, RATE, java.util.Arrays.copyOfRange(ulaw, off, off + Math.max(0, len))));
            if (off + len >= ulaw.length) break;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
        bar(mc, "§7ส่งให้ผู้ช่วยฟังแล้ว...", "§7Sent to your helpers...");
    }
}
