package com.hollowhours.ghost;

import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CJumpscare;
import com.hollowhours.util.R;
import com.hollowhours.util.Fx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

/**
 * Server side of the jumpscares: the client draws the face, plays the screams and shakes the camera
 * (client/JumpscareOverlay); here we only add the lingering effects (blind, dark, sick).
 *
 * who: 1-5 ghosts, 11-13 stalkers. strength: 0 flash, 1 strong, 2 death, 3 dragged out of a hiding spot.
 */
public final class Jumpscares {
    public static final int FLASH = 0, STRONG = 1, DEATH = 2, DRAG = 3;

    private Jumpscares() {}

    public static void scare(ServerPlayer p, int who, int strength) {
        if (p == null || !p.isAlive()) return;
        Sanity.shock(p, strength == FLASH ? 2 : strength == DRAG ? 10 : strength == DEATH ? 12 : 8);
        HHNet.toPlayer(p, new S2CJumpscare(who, strength, R.randInt(0, 7)));
        Fx.effect(p, MobEffects.BLINDNESS, strength >= 1 ? 14 : 8, 0);
        if (strength >= 1) {
            Fx.effect(p, MobEffects.DARKNESS, 140, 0);
            Director.dreadBoost(p);
        }
    }

    public static void dragOut(ServerPlayer p, int who, boolean bed) {
        HHNet.toPlayer(p, new S2CJumpscare(who, DRAG, bed ? 1 : 0));
        Fx.effect(p, MobEffects.DARKNESS, 160, 0);
    }

    public static void stalker(ServerPlayer p, int n, boolean strong) {
        scare(p, 10 + n, strong ? STRONG : FLASH);
    }

    /** After respawning from a ghost death: one more, smaller one. */
    public static void afterDeath(ServerPlayer p, int who) {
        HHNet.toPlayer(p, new S2CJumpscare(who, FLASH, 99));
    }
}
