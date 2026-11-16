package com.helperbuddy.talk;

/**
 * The voices of the mod's own voice (made on the player's computer). English has eight speakers; Thai has one, so its
 * voices differ in pitch. Each helper has one (picked in its menu, or one of the first eight by its id).
 */
public final class VoiceCatalog {
    private VoiceCatalog() {}

    /** sid: the English speaker; pitch: playback higher / lower (also a little faster / slower); speed: talking speed */
    public record V(String id, String name, int sid, float pitch, float speed) {}

    public static final V[] ALL = {
            new V("a", "Voice 1", 0, 1f, 1f), new V("b", "Voice 2", 1, 1f, 1f), new V("c", "Voice 3", 2, 1f, 1f), new V("d", "Voice 4", 3, 1f, 1f),
            new V("e", "Voice 5", 4, 1f, 1f), new V("f", "Voice 6", 5, 1f, 1f), new V("g", "Voice 7", 6, 1f, 1f), new V("h", "Voice 8", 7, 1f, 1f),
            new V("tiny", "Tiny", 2, 1.32f, 1.05f), new V("kid", "Kid", 6, 1.18f, 1.05f), new V("deep", "Deep", 1, 0.84f, 0.95f),
            new V("giant", "Giant", 5, 0.74f, 0.9f), new V("fast", "Fast talker", 3, 1.05f, 1.3f), new V("calm", "Calm", 4, 0.96f, 0.85f),
    };

    /** seed: the helper's id hash (the voice when none was picked) */
    public static V of(String id, int seed) {
        if (id != null && !id.isEmpty()) for (V v : ALL) if (v.id().equals(id)) return v;
        return ALL[Math.floorMod(seed, 8)];
    }

    public static String name(String id, int seed) {
        return (id == null || id.isEmpty() ? "auto: " : "") + of(id, seed).name();
    }

    public static String next(String id, int step) {
        int i = -1;
        for (int k = 0; k < ALL.length; k++) if (ALL[k].id().equals(id)) i = k;
        // "" (one by its id) comes before the first
        int n = Math.floorMod(i + 1 + step, ALL.length + 1);
        return n == 0 ? "" : ALL[n - 1].id();
    }
}
