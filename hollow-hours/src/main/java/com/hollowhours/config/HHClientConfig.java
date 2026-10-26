package com.hollowhours.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Per-player visual/audio comfort settings (config/hollowhours-client.toml). */
public final class HHClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue JUMPSCARE_INTENSITY, SHAKE, JUMPSCARE_VOLUME;
    public static final ForgeConfigSpec.BooleanValue REDUCE_FLASHING, FACE_CLOSEUPS, DEATH_SCREEN_FX;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("horror");
        JUMPSCARE_INTENSITY = b.comment("0 = gentle ... 1 = full hardcore").defineInRange("jumpscareIntensity", 1.0, 0, 1);
        SHAKE = b.comment("Camera shake strength").defineInRange("cameraShake", 1.0, 0, 2);
        JUMPSCARE_VOLUME = b.comment("Extra volume for jumpscare screams").defineInRange("jumpscareVolume", 1.0, 0, 1);
        REDUCE_FLASHING = b.comment("Photosensitivity: no rapid red/black flashes").define("reduceFlashing", false);
        FACE_CLOSEUPS = b.comment("Show the full-screen ghost face close-ups").define("faceCloseups", true);
        DEATH_SCREEN_FX = b.comment("Glitchy ghost face on the death screen").define("deathScreenFx", true);
        b.pop();
        SPEC = b.build();
    }

    private HHClientConfig() {}
}
