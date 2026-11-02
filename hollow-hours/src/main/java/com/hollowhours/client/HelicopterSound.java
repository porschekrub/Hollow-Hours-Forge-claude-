package com.hollowhours.client;

import com.hollowhours.registry.HHSounds;
import com.hollowhours.rescue.HelicopterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The rotor's chop, following the helicopter (heard from about 80 blocks away, loud when you're aboard). */
public class HelicopterSound extends AbstractTickableSoundInstance {
    final HelicopterEntity heli;

    HelicopterSound(HelicopterEntity h) {
        super(HHSounds.get("rescue.rotor"), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        heli = h;
        looping = true;
        delay = 0;
        volume = 5f;
        attenuation = Attenuation.LINEAR;
        follow();
    }

    public static void start(HelicopterEntity h) {
        Minecraft.getInstance().getSoundManager().play(new HelicopterSound(h));
    }

    void follow() {
        x = heli.getX();
        y = heli.getY() + 2;
        z = heli.getZ();
    }

    @Override
    public void tick() {
        if (heli.isRemoved()) {
            stop();
            return;
        }
        follow();
        pitch = heli.state() == HelicopterEntity.LANDED ? 0.9f : 1f;
    }
}
