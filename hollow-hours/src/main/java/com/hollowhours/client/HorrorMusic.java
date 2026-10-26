package com.hollowhours.client;

import com.hollowhours.registry.HHSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Horror music: "spawn" (it is near, searching) and "chase" loop and cross-fade; ambient tracks play once.
 * Vanilla music is pushed aside while a horror track plays.
 */
public final class HorrorMusic {
    private HorrorMusic() {}

    static Track current;
    static String currentKey = "";

    static final class Track extends AbstractTickableSoundInstance {
        final boolean loop;
        float target = 1f, vol = 0f;
        boolean fadingOut;

        Track(SoundEvent se, boolean loop) {
            super(se, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.loop = loop;
            this.looping = loop;
            this.delay = 0;
            this.volume = 0.001f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            float step = fadingOut ? 0.04f : 0.03f;
            if (fadingOut) vol = Math.max(0, vol - step);
            else vol = Math.min(target, vol + step);
            volume = Math.max(0.001f, vol);
            if (fadingOut && vol <= 0) stop();
        }

        void fadeOut() {
            fadingOut = true;
        }
    }

    static void set(String key) {
        if (key.equals(currentKey)) return;
        if (current != null) current.fadeOut();
        current = null;
        currentKey = key;
        if (key.isEmpty()) return;
        String name = "ghost.music." + key;
        if (!HHSounds.has(name)) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getMusicManager().stopPlaying();
        boolean loop = key.equals("spawn") || key.equals("chase");
        current = new Track(HHSounds.get(name), loop);
        mc.getSoundManager().play(current);
    }

    static void tick() {
        if (current != null && !currentKey.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.getSoundManager().isActive(current) && current.vol > 0.5f) {
                // a one-shot ambient track ended
                if (!current.loop) {
                    current = null;
                    currentKey = "";
                }
            } else mc.getMusicManager().stopPlaying();
        }
    }

    static void reset() {
        if (current != null) current.fadeOut();
        current = null;
        currentKey = "";
    }
}
