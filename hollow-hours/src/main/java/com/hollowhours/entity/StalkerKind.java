package com.hollowhours.entity;

import java.util.Map;

/** stalker.js TYPES: the three apparitions that watch, follow and lunge (they never chase or kill). */
public enum StalkerKind {
    LANKY(1, "Lanky", 0.7f, 3.1f, 0f, 36, 1.9f, 0, new String[]{"whisper", "wetbreath", "creak", "wallknock"},
            Map.of("peek", 4, "lunge", 4, "tunnel", 4, "lure", 2, "behind", 2, "follow", 2, "streak", 1, "window", 4, "door", 3, "knock", 2), 0xCFCFC8, 0x222222),
    CHILD(2, "Pale Child", 0.5f, 1.8f, 0f, 6, 1.3f, 3, new String[]{"giggle", "musicbox", "sob", "wallknock"},
            Map.of("follow", 5, "behind", 4, "lure", 3, "peek", 1, "lunge", 1, "tunnel", 2, "streak", 1, "window", 3, "door", 2, "knock", 4), 0xE8E0D0, 0x552222),
    VEIL(3, "Veil", 0.7f, 2.9f, 0.9f, 20, 1.6f, 0, new String[]{"bell", "chains", "scream", "glass"},
            Map.of("streak", 5, "peek", 3, "behind", 2, "lunge", 3, "tunnel", 1, "lure", 1, "follow", 2, "window", 3, "door", 3, "knock", 1), 0x14141C, 0xAA1111);

    public final int n;
    public final String display;
    public final float width, height, floatY;
    /** ticks it must be stared at before it fades */
    public final int seen;
    /** rush speed (blocks per tick) */
    public final float rush;
    /** hop distance while following (0 = smooth) */
    public final int hop;
    public final String[] sounds;
    public final Map<String, Integer> weights;
    public final int eggBg, eggFg;

    StalkerKind(int n, String display, float w, float h, float fy, int seen, float rush, int hop, String[] snd, Map<String, Integer> weights, int bg, int fg) {
        this.n = n;
        this.display = display;
        this.width = w;
        this.height = h;
        this.floatY = fy;
        this.seen = seen;
        this.rush = rush;
        this.hop = hop;
        this.sounds = snd;
        this.weights = weights;
        this.eggBg = bg;
        this.eggFg = fg;
    }

    public String id() {
        return "stalker_" + n;
    }
}
