package com.veritypal.mind;

import com.veritypal.V;
import net.minecraft.server.MinecraftServer;

import java.util.Random;

/**
 * Her mood, which drifts on its own and follows what happens (being hit, praised, a ghost, the night ...). It goes into
 * her prompt: the AI decides what she does with it (help, refuse, tease, prank).
 */
public final class Mood {
    private Mood() {}

    public enum Kind {
        HAPPY("happy", "cheerful, happy to help", "happy"),
        PLAYFUL("playful", "in a teasing, mischievous mood: pranks are tempting", "crazy"),
        GRUMPY("grumpy", "annoyed and grumpy: short answers, not very willing to help", "serious_1"),
        BORED("bored", "bored out of your mind: you want something to happen", "neutral"),
        SLEEPY("sleepy", "sleepy and slow, yawning", "happy_sleep"),
        SCARED("scared", "nervous and jumpy, something feels wrong", "hurt"),
        PROUD("proud", "smug and full of yourself", "crazy");

        public final String word, how, face;

        Kind(String word, String how, String face) {
            this.word = word;
            this.how = how;
            this.face = face;
        }
    }

    public static Kind now = Kind.HAPPY;
    static long nextDrift;
    static final Random R = new Random();

    public static void reset() {
        now = Kind.HAPPY;
        nextDrift = 0;
    }

    public static void set(Kind k) {
        now = k;
        nextDrift = System.currentTimeMillis() + (4 + R.nextInt(6)) * 60_000L;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 200 != 0) return;
        long t = System.currentTimeMillis();
        if (t < nextDrift) return;
        float karma = V.karma(server);
        long day = server.overworld().getDayTime() % 24000;
        boolean night = day > 13000 && day < 23000;
        int r = R.nextInt(100);
        Kind k;
        if (night && r < 25) k = Kind.SLEEPY;
        else if (karma < 6) k = r < 55 ? Kind.GRUMPY : r < 75 ? Kind.BORED : Kind.PLAYFUL;
        else if (karma > 15) k = r < 40 ? Kind.HAPPY : r < 70 ? Kind.PLAYFUL : r < 85 ? Kind.PROUD : Kind.BORED;
        else k = r < 30 ? Kind.HAPPY : r < 55 ? Kind.PLAYFUL : r < 70 ? Kind.BORED : r < 85 ? Kind.GRUMPY : Kind.PROUD;
        set(k);
    }
}
