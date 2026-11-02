package com.hollowhours.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Per-player visual/audio comfort settings (config/hollowhours-client.toml). */
public final class HHClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue JUMPSCARE_INTENSITY, SHAKE, JUMPSCARE_VOLUME;
    public static final ForgeConfigSpec.BooleanValue REDUCE_FLASHING, FACE_CLOSEUPS, DEATH_SCREEN_FX, SHADER, PLAYER_ANIMS, BLOOD, ITEM_PHYSICS, FIRST_PERSON_ARMS, FIRST_PERSON_BODY, SKIP_EXPERIMENTAL_WARNING;
    public static final ForgeConfigSpec.DoubleValue SHADER_STRENGTH, CAMERA_SWAY;
    public static final ForgeConfigSpec.IntValue SETTINGS_VERSION;
    /** settings changed for everyone, once (see {@link #onLoad}) */
    static final int CURRENT = 2;

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
        b.push("look");
        SHADER = b.comment("Built-in horror shader: bleak colours, vignette, film grain, red blood moon (turn off if you use a shader pack)").define("shader", true);
        SHADER_STRENGTH = b.comment("How strong the shader is").defineInRange("shaderStrength", 1.0, 0, 1.5);
        PLAYER_ANIMS = b.comment("Scared player animations (hunched walk, panicked run, shaking hands ...)").define("playerAnimations", true);
        CAMERA_SWAY = b.comment("First-person breathing / panic sway").defineInRange("cameraSway", 1.0, 0, 2);
        BLOOD = b.comment("Show blood (sprays, splats on the ground, drips)").define("blood", true);
        FIRST_PERSON_ARMS = b.comment("First person: your arm holds what's in your hand (you see it swing, eat, drink)").define("firstPersonArms", false);
        FIRST_PERSON_BODY = b.comment("First person: look down and see your own body and legs (with the same animations as in third person)").define("firstPersonBody", true);
        ITEM_PHYSICS = b.comment("Dropped items lie on the ground (no floating / spinning)").define("itemPhysics", true);
        SKIP_EXPERIMENTAL_WARNING = b.comment("Skip Minecraft's 'Worlds using Experimental Settings' warning (it shows because of the haven, the mod's extra world)")
                .define("skipExperimentalWarning", true);
        SETTINGS_VERSION = b.comment("(don't change: lets an update adjust settings once)").defineInRange("settingsVersion", 0, 0, 1000);
        b.pop();
        SPEC = b.build();
    }

    private HHClientConfig() {}

    /** Once per update that needs it: 2 = no first-person arms (the body when looking down stays). */
    public static void onLoad(net.minecraftforge.fml.event.config.ModConfigEvent.Loading e) {
        if (e.getConfig().getSpec() != SPEC || SETTINGS_VERSION.get() >= CURRENT) return;
        if (SETTINGS_VERSION.get() < 2) FIRST_PERSON_ARMS.set(false);
        SETTINGS_VERSION.set(CURRENT);
        SPEC.save();
    }
}
