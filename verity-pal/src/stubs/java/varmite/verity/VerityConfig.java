package varmite.verity;

import net.minecraftforge.common.ForgeConfigSpec;
import varmite.verity.types.AiProvider;
import varmite.verity.types.STTProvider;
import varmite.verity.types.TTSProvider;

/** Stand-in (see README.md): the Verity JE settings Verity Pal reads. */
public class VerityConfig {
    public static ForgeConfigSpec SPEC;
    public static ForgeConfigSpec.BooleanValue CAN_CRASH;
    public static ForgeConfigSpec.BooleanValue REQUIRE_VERITY;
    public static ForgeConfigSpec.BooleanValue PLAY_VIDEO;
    public static ForgeConfigSpec.BooleanValue TRUE_DARKNESS;
    public static ForgeConfigSpec.BooleanValue KILL_WILDLIFE;
    public static ForgeConfigSpec.BooleanValue KILL_VILLAGERS;
    public static ForgeConfigSpec.BooleanValue KILL_ENTITIES;
    public static ForgeConfigSpec.BooleanValue IMMERSIVE_MODE;
    public static ForgeConfigSpec.BooleanValue USE_TTS;
    public static ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static ForgeConfigSpec.ConfigValue<String> AI_ENDPOINT;
    public static ForgeConfigSpec.ConfigValue<Integer> DAY_COUNT;
    public static ForgeConfigSpec.ConfigValue<String> VERITY_CUSTOM_NAME;
    public static ForgeConfigSpec.ConfigValue<String> PERSONALITY;
    public static ForgeConfigSpec.ConfigValue<String> GROQ_KEY;
    public static ForgeConfigSpec.EnumValue<AiProvider> AI_PROVIDER;
    public static ForgeConfigSpec.EnumValue<TTSProvider> TTS_PROVIDER;
    public static ForgeConfigSpec.EnumValue<STTProvider> STT_PROVIDER;
}
