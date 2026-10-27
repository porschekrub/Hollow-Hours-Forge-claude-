package com.helperbuddy.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/helperbuddy-common.toml */
public final class HBConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue SEARCH_RADIUS, MAX_HELPERS_PER_PLAYER, BUILD_SPEED_DEFAULT;
    public static final ForgeConfigSpec.BooleanValue FREE_BUILD, GIVE_CONTRACT_ON_JOIN, ALLOW_PVP_FIGHT_BACK, TELEPORT_TO_OWNER;
    public static final ForgeConfigSpec.DoubleValue WORK_SPEED, DAMAGE_MULT;

    public static final ForgeConfigSpec.BooleanValue AI_ENABLED, AI_FOR_COMMANDS, AI_FOR_CHAT, AI_FOR_BUILDING;
    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER, AI_KEY, AI_MODEL, AI_URL;
    public static final ForgeConfigSpec.IntValue AI_TIMEOUT, AI_MAX_BUILD;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("How the helper works").push("helper");
        SEARCH_RADIUS = b.comment("How far (blocks) the helper looks for ores, trees, blocks and animals").defineInRange("searchRadius", 100, 16, 160);
        MAX_HELPERS_PER_PLAYER = b.comment("How many helpers one player may own").defineInRange("maxHelpersPerPlayer", 8, 1, 64);
        WORK_SPEED = b.comment("Mining / chopping speed multiplier (1 = like a player with the same tool)").defineInRange("workSpeed", 1.25, 0.25, 10.0);
        DAMAGE_MULT = b.comment("Helper attack damage multiplier").defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        BUILD_SPEED_DEFAULT = b.comment("Default building speed: 0 = 5 blocks/s, 1 = 10 blocks/s, 2 = 20 blocks/s, 3 = 40 blocks/s").defineInRange("buildSpeed", 1, 0, 3);
        FREE_BUILD = b.comment("Houses are built without using blocks (a nether portal and items for crafting always need the real items)").define("freeBuild", true);
        GIVE_CONTRACT_ON_JOIN = b.comment("Give every player one helper contract the first time they join").define("contractOnFirstJoin", true);
        ALLOW_PVP_FIGHT_BACK = b.comment("Helpers also fight back against other players (only when the server allows PvP)").define("fightBackPlayers", true);
        TELEPORT_TO_OWNER = b.comment("A following helper teleports to its owner when it falls far behind or the owner changes dimension").define("teleportToOwner", true);
        b.pop();

        b.comment("Optional real AI (understands any sentence, chats, designs custom buildings).",
                "Without it the built-in Thai/English brain is used. Providers: gemini (free key: aistudio.google.com), openai, anthropic, openrouter, groq, ollama, custom",
                "custom = any OpenAI-compatible endpoint (set url).").push("ai");
        AI_ENABLED = b.comment("Turn the AI on (needs a key, except for ollama)").define("enabled", false);
        AI_PROVIDER = b.define("provider", "gemini");
        AI_KEY = b.comment("API key (keep this file private)").define("apiKey", "");
        AI_MODEL = b.comment("Model name, empty = the provider's default").define("model", "");
        AI_URL = b.comment("Endpoint for ollama / custom (e.g. http://localhost:11434)").define("url", "");
        AI_TIMEOUT = b.comment("Seconds to wait for an answer").defineInRange("timeoutSeconds", 25, 3, 120);
        AI_FOR_COMMANDS = b.comment("Use the AI when the built-in brain does not understand an order").define("useForCommands", true);
        AI_FOR_CHAT = b.comment("Use the AI for small talk / questions").define("useForChat", true);
        AI_FOR_BUILDING = b.comment("Let the AI design things the helper has no blueprint for (\"build a dragon\")").define("useForBuilding", true);
        AI_MAX_BUILD = b.comment("Largest AI design (blocks per side)").defineInRange("maxBuildSize", 32, 8, 64);
        b.pop();
        SPEC = b.build();
    }

    private HBConfig() {}

    public static boolean aiReady() {
        if (!AI_ENABLED.get()) return false;
        String p = AI_PROVIDER.get().trim().toLowerCase();
        return p.equals("ollama") || p.equals("custom") ? !AI_URL.get().isBlank() || p.equals("ollama") : !AI_KEY.get().isBlank();
    }
}
