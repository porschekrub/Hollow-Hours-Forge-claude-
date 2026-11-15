package com.veritypal.story;

import com.veritypal.VerityPal;
import com.veritypal.config.PalConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import varmite.verity.VerityConfig;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Verity JE's horror story, on or off. With the story off a few of Verity JE's own settings read differently while
 * the game runs (her files are not changed): no countdown of days (so no demon day, no animals and villagers
 * vanishing, no creepy faces), no killing of animals, no extreme darkness, no kicking out, no intro video.
 * Her AI also can't schedule turning into the demon (StoryMixins) and her prompt loses the story (PromptPatch).
 */
public final class StoryGate {
    private StoryGate() {}

    /** Days before Verity JE's demon day while the story is off: never. */
    public static final int NEVER = 1_000_000;

    static final Map<Object, Supplier<Object>> OFF = new IdentityHashMap<>();

    public static void init() {
        try {
            put(VerityConfig.DAY_COUNT, () -> NEVER);
            put(VerityConfig.KILL_WILDLIFE, () -> false);
            put(VerityConfig.KILL_ENTITIES, () -> false);
            put(VerityConfig.KILL_VILLAGERS, () -> false);
            put(VerityConfig.CAN_CRASH, () -> false);
            put(VerityConfig.PLAY_VIDEO, () -> false);
            put(VerityConfig.TRUE_DARKNESS, () -> PalConfig.on(PalConfig.STORY_DARKNESS) ? null : false);
            VerityPal.LOG.info("[Verity Pal] story switch ready ({} of Verity JE's settings follow it); the story is {}", OFF.size(), PalConfig.story() ? "ON" : "off");
        } catch (Throwable t) {
            VerityPal.LOG.error("[Verity Pal] could not find Verity JE's story settings: the story can't be turned off with this version of Verity JE", t);
        }
    }

    static void put(@Nullable ForgeConfigSpec.ConfigValue<?> v, Supplier<Object> off) {
        if (v != null) OFF.put(v, off);
    }

    /** What one of Verity JE's settings reads right now (null: its own value). Called for every config value of every mod: cheap. */
    @Nullable
    public static Object override(Object configValue) {
        if (OFF.isEmpty()) return null;
        Supplier<Object> s = OFF.get(configValue);
        if (s == null || PalConfig.story()) return null;
        return s.get();
    }

    public static boolean off() {
        return !PalConfig.story();
    }
}
