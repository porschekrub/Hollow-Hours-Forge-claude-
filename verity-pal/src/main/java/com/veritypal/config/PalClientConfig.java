package com.veritypal.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/veritypal-client.toml: Verity's Thai voice and ears (on this computer). */
public final class PalClientConfig {
    private PalClientConfig() {}

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> THAI_VOICE;
    public static final ForgeConfigSpec.ConfigValue<String> GEMINI_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> VOICE_NAME;
    public static final ForgeConfigSpec.ConfigValue<String> VOICE_STYLE;
    public static final ForgeConfigSpec.DoubleValue VOICE_PITCH;
    public static final ForgeConfigSpec.DoubleValue VOICE_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<String> SPEECH_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> EARS;
    public static final ForgeConfigSpec.ConfigValue<String> EARS_MODEL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Verity's voice. Verity JE's own voices only speak English: Thai is read by Google Gemini (a free key from aistudio.google.com)").push("voice");
        THAI_VOICE = b.comment("auto = Gemini reads the lines that are in Thai (English ones keep Verity JE's voice), always = every line, off = never")
                .define("thaiVoice", "auto");
        GEMINI_KEY = b.comment("Gemini API key. Empty: the key in Verity JE's AI settings is used when Verity's AI is Gemini").define("geminiKey", "");
        VOICE_NAME = b.comment("Gemini voice: Puck, Kore, Charon, Fenrir, Leda, Orus, Zephyr, Aoede ...").define("voiceName", "Puck");
        VOICE_STYLE = b.comment("How the voice should sound (said to the speech AI)").define("voiceStyle", "a cheeky, smug cartoon mascot, playful and a bit sarcastic");
        VOICE_PITCH = b.comment("Higher and faster like a cartoon (1 = as the AI says it)").defineInRange("cartoonPitch", 1.12, 0.8, 1.6);
        VOICE_VOLUME = b.comment("Loudness of the Gemini voice").defineInRange("volume", 1.0, 0.0, 2.0);
        SPEECH_MODEL = b.comment("Gemini speech model (empty: the newest that works is found by itself)").define("speechModel", "");
        b.pop();

        b.comment("Verity's ears (the microphone key of Verity JE)").push("listen");
        EARS = b.comment("auto = when Verity JE's own speech-to-text is the built-in one (English only) and there is a Gemini key, Gemini writes down",
                "what you say, in any language (Thai too). on = always Gemini, off = never").define("geminiEars", "auto");
        EARS_MODEL = b.comment("Gemini model that writes down what you say").define("earsModel", "gemini-flash-lite-latest");
        b.pop();
        SPEC = b.build();
    }

    public static boolean ready() {
        return SPEC.isLoaded();
    }
}
