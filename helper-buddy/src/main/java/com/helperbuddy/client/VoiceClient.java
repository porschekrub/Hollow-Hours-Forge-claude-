package com.helperbuddy.client;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.talk.VoiceTts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Plays a helper's voice: the babble (short syllables, each helper with its own pitch, sassy ones quicker and
 * jumpier, rude ones lower), or a real voice clip from the server (OpenAL, at the helper).
 */
@Mod.EventBusSubscriber(modid = HelperBuddy.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VoiceClient {
    private VoiceClient() {}

    record Syl(long at, int entity, int k, float pitch) {}

    static final List<Syl> QUEUE = new ArrayList<>();
    static final Map<Integer, Long> BUSY_UNTIL = new HashMap<>();
    static long tick;
    /** syllables played (the client smoke test checks there were some) */
    public static int played;
    static final RandomSource R = RandomSource.create();

    static float volume() {
        return (float) (double) HBConfig.VOICE_VOLUME.get();
    }

    /** How many syllables a line has (Thai: about one per two letters). */
    static int syllables(String text) {
        int n = 0;
        boolean inWord = false;
        int thai = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x0E00 && c <= 0x0E7F) {
                thai++;
                continue;
            }
            boolean letter = Character.isLetterOrDigit(c);
            if (letter && !inWord) n++;
            inWord = letter;
        }
        return Math.max(2, Math.min(32, n + thai / 3));
    }

    public static void babble(int entity, String text, int seed, int style) {
        if (volume() <= 0) return;
        // someone already speaking with a real voice: no babble on top
        Long until = BUSY_UNTIL.get(entity);
        if (until != null && until > tick) return;
        float base = 0.95f + (Math.floorMod(seed, 97) / 97f) * 0.45f;
        if (style == 2) base *= 0.85f;
        int n = syllables(text);
        float gap = style == 0 ? 2.6f : 2.0f;
        float jump = style == 0 ? 0.06f : 0.14f;
        long start = Math.max(tick + 1, lastAt(entity) + 3);
        float t = 0;
        int last = -1;
        for (int i = 0; i < n; i++) {
            int k;
            do k = R.nextInt(12);
            while (k == last);
            last = k;
            float pitch = base * (1 + (R.nextFloat() * 2 - 1) * jump);
            // a question goes up at the end, a shout (!) gets louder and higher
            if (i >= n - 2 && text.endsWith("?")) pitch *= 1.12f;
            if (text.endsWith("!") && i == n - 1) pitch *= 1.08f;
            QUEUE.add(new Syl(start + (long) t, entity, k, Math.max(0.5f, Math.min(2f, pitch))));
            t += gap + (R.nextFloat() < 0.15f ? 2 : 0);
        }
    }

    static long lastAt(int entity) {
        long m = 0;
        for (Syl s : QUEUE) if (s.entity == entity) m = Math.max(m, s.at);
        return m;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tick++;
        Minecraft mc = Minecraft.getInstance();
        VoiceInput.tick(mc);
        if (mc.level == null) {
            QUEUE.clear();
            ClipPlayer.stopAll();
            return;
        }
        if (!QUEUE.isEmpty()) {
            Iterator<Syl> it = QUEUE.iterator();
            while (it.hasNext()) {
                Syl s = it.next();
                if (s.at > tick) continue;
                it.remove();
                Entity ent = mc.level.getEntity(s.entity);
                if (ent == null) continue;
                mc.getSoundManager().play(new SimpleSoundInstance(HelperBuddy.id("voice.s" + s.k), SoundSource.NEUTRAL, 0.55f * volume(), s.pitch, R, false, 0,
                        SoundInstance.Attenuation.LINEAR, ent.getX(), ent.getEyeY(), ent.getZ(), false));
                played++;
            }
        }
        ClipPlayer.tick(mc);
    }

    // ------------------------------------------------------------------ real voice clips
    static final Map<Integer, ByteArrayOutputStream> PARTS = new HashMap<>();

    public static void audio(int entity, int clip, int part, boolean last, int rate, byte[] data) {
        ByteArrayOutputStream b = PARTS.computeIfAbsent(clip, k -> new ByteArrayOutputStream());
        b.writeBytes(data);
        if (!last) return;
        PARTS.remove(clip);
        byte[] ulaw = b.toByteArray();
        short[] pcm = new short[ulaw.length];
        for (int i = 0; i < ulaw.length; i++) pcm[i] = VoiceTts.fromUlaw(ulaw[i]);
        // the babble of this helper stops while it really talks
        QUEUE.removeIf(s -> s.entity == entity);
        BUSY_UNTIL.put(entity, tick + (long) (pcm.length * 20L / Math.max(1, rate)) + 5);
        ClipPlayer.play(entity, pcm, rate, volume());
    }

    /** OpenAL playback of a clip at an entity (Minecraft's own sound system only plays files). */
    static final class ClipPlayer {
        record Clip(int source, int buffer, int entity) {}

        static final List<Clip> PLAYING = new ArrayList<>();
        static boolean broken;

        static void play(int entity, short[] pcm, int rate, float vol) {
            if (broken) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                float master = mc.options.getSoundSourceVolume(SoundSource.MASTER) * mc.options.getSoundSourceVolume(SoundSource.VOICE);
                int buf = org.lwjgl.openal.AL10.alGenBuffers();
                java.nio.ShortBuffer sb = org.lwjgl.BufferUtils.createShortBuffer(pcm.length);
                sb.put(pcm).flip();
                org.lwjgl.openal.AL10.alBufferData(buf, org.lwjgl.openal.AL10.AL_FORMAT_MONO16, sb, rate);
                int src = org.lwjgl.openal.AL10.alGenSources();
                org.lwjgl.openal.AL10.alSourcei(src, org.lwjgl.openal.AL10.AL_BUFFER, buf);
                org.lwjgl.openal.AL10.alSourcef(src, org.lwjgl.openal.AL10.AL_GAIN, Math.min(2f, vol * master * 1.4f));
                // linear fall-off like Minecraft's own sounds
                org.lwjgl.openal.AL10.alSourcei(src, 0xD000, 0xD003);
                org.lwjgl.openal.AL10.alSourcef(src, org.lwjgl.openal.AL10.AL_MAX_DISTANCE, 28f);
                org.lwjgl.openal.AL10.alSourcef(src, org.lwjgl.openal.AL10.AL_ROLLOFF_FACTOR, 1f);
                org.lwjgl.openal.AL10.alSourcef(src, org.lwjgl.openal.AL10.AL_REFERENCE_DISTANCE, 0f);
                place(mc, src, entity);
                org.lwjgl.openal.AL10.alSourcePlay(src);
                PLAYING.add(new Clip(src, buf, entity));
            } catch (Throwable t) {
                broken = true;
                HelperBuddy.LOG.warn("[Helper Buddy] can't play the helper's voice: {}", t.toString());
            }
        }

        static void place(Minecraft mc, int src, int entity) {
            Entity e = mc.level == null ? null : mc.level.getEntity(entity);
            if (e != null) org.lwjgl.openal.AL10.alSource3f(src, org.lwjgl.openal.AL10.AL_POSITION, (float) e.getX(), (float) e.getEyeY(), (float) e.getZ());
        }

        static void tick(Minecraft mc) {
            if (PLAYING.isEmpty() || broken) return;
            try {
                Iterator<Clip> it = PLAYING.iterator();
                while (it.hasNext()) {
                    Clip c = it.next();
                    if (org.lwjgl.openal.AL10.alGetSourcei(c.source, org.lwjgl.openal.AL10.AL_SOURCE_STATE) != org.lwjgl.openal.AL10.AL_PLAYING) {
                        org.lwjgl.openal.AL10.alDeleteSources(c.source);
                        org.lwjgl.openal.AL10.alDeleteBuffers(c.buffer);
                        it.remove();
                    } else place(mc, c.source, c.entity);
                }
            } catch (Throwable t) {
                broken = true;
            }
        }

        static void stopAll() {
            if (PLAYING.isEmpty()) return;
            try {
                for (Clip c : PLAYING) {
                    org.lwjgl.openal.AL10.alSourceStop(c.source);
                    org.lwjgl.openal.AL10.alDeleteSources(c.source);
                    org.lwjgl.openal.AL10.alDeleteBuffers(c.buffer);
                }
            } catch (Throwable ignored) {
            }
            PLAYING.clear();
        }
    }
}
