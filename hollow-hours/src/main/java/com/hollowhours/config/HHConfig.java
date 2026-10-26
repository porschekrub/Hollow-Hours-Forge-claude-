package com.hollowhours.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side rules (config/hollowhours-common.toml). Times are in seconds unless stated otherwise. */
public final class HHConfig {
    public static final ForgeConfigSpec SPEC;

    // ---- spawning
    public static final ForgeConfigSpec.IntValue FIRST_SPAWN_MIN, FIRST_SPAWN_MAX, GAP_MIN, GAP_MAX, RESPAWN_MIN, RESPAWN_MAX;
    public static final ForgeConfigSpec.DoubleValue QUICK_CHANCE, FALSE_ALARM_CHANCE, HOUSE_EVENT_CHANCE, GLIMPSE_CHANCE;
    public static final ForgeConfigSpec.IntValue MAX_ALIVE, CHASE_MAX_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ONLY_AT_NIGHT, GHOSTS_IN_NETHER, FAKE_MESSAGES, STALKERS_ENABLED, EVENTS_ENABLED;

    // ---- ghost strength
    public static final ForgeConfigSpec.DoubleValue SPEED_MULT, DAMAGE, RAGE_SPEED, RAGE_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue BREAK_DOORS, BREAK_WALLS;

    // ---- kill cinematic / betrayal
    public static final ForgeConfigSpec.EnumValue<KillMode> KILL_MODE;
    public static final ForgeConfigSpec.DoubleValue BETRAYAL_CHANCE, BETRAYAL_LIE_CHANCE;
    public static final ForgeConfigSpec.IntValue BETRAYAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue BETRAYAL_ACROSS_DIMENSIONS;

    // ---- talk / ask
    public static final ForgeConfigSpec.DoubleValue TALK_SUCCESS_CHANCE, TALK_RAGE_CHANCE, ASK_GIVE_CHANCE;
    public static final ForgeConfigSpec.IntValue ASK_LIMIT;

    // ---- AI (optional)
    public static final ForgeConfigSpec.BooleanValue AI_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER, AI_KEY, AI_MODEL, AI_URL;
    public static final ForgeConfigSpec.IntValue AI_TIMEOUT;

    public enum KillMode { LETHAL_HIT, ON_CATCH }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("When and how often the ghosts come").push("spawning");
        FIRST_SPAWN_MIN = b.comment("First ghost: earliest seconds after joining").defineInRange("firstSpawnMin", 180, 10, 36000);
        FIRST_SPAWN_MAX = b.defineInRange("firstSpawnMax", 300, 10, 36000);
        GAP_MIN = b.comment("Seconds between hunts (random between min and max)").defineInRange("gapMin", 420, 30, 36000);
        GAP_MAX = b.defineInRange("gapMax", 720, 30, 36000);
        QUICK_CHANCE = b.comment("Chance that the next hunt comes early (4-6 minutes)").defineInRange("quickChance", 0.15, 0, 1);
        RESPAWN_MIN = b.comment("The same ghost can come back after this many seconds").defineInRange("respawnMin", 900, 30, 72000);
        RESPAWN_MAX = b.defineInRange("respawnMax", 1500, 30, 72000);
        FALSE_ALARM_CHANCE = b.comment("Omen sounds play but no ghost comes").defineInRange("falseAlarmChance", 0.12, 0, 1);
        HOUSE_EVENT_CHANCE = b.comment("Player indoors: chance the ghost comes to a door/window instead of appearing outside").defineInRange("houseEventChance", 0.8, 0, 1);
        GLIMPSE_CHANCE = b.comment("Chance a hunt is replaced by a stalker apparition").defineInRange("glimpseChance", 0.10, 0, 1);
        MAX_ALIVE = b.comment("Hunting ghosts at the same time").defineInRange("maxAlive", 1, 1, 5);
        CHASE_MAX_SECONDS = b.comment("A chase lasts at most this long before the ghost vanishes").defineInRange("chaseMaxSeconds", 90, 10, 3600);
        ONLY_AT_NIGHT = b.comment("Only hunt at night (default false: they come any time, just more often at night)").define("onlyAtNight", false);
        GHOSTS_IN_NETHER = b.comment("Allow hunts in the Nether and the End").define("huntInOtherDimensions", false);
        FAKE_MESSAGES = b.comment("Fake 'joined the game' / chat messages").define("fakeMessages", true);
        STALKERS_ENABLED = b.comment("The three stalker apparitions").define("stalkers", true);
        EVENTS_ENABLED = b.comment("Random scary events (footsteps, blackout, crying ...)").define("randomEvents", true);
        b.pop();

        b.comment("How dangerous the ghosts are").push("ghosts");
        SPEED_MULT = b.comment("Chase speed multiplier").defineInRange("speedMultiplier", 1.0, 0.2, 3.0);
        DAMAGE = b.comment("Damage per hit (2 = one heart)").defineInRange("damage", 8.0, 0, 100);
        RAGE_SPEED = b.comment("Speed multiplier while enraged").defineInRange("rageSpeed", 1.4, 1, 4);
        RAGE_DAMAGE = b.comment("Damage multiplier while enraged").defineInRange("rageDamage", 2.0, 1, 10);
        BREAK_DOORS = b.comment("Ghosts smash doors, windows, trapdoors, gates and boards").define("breakDoors", true);
        BREAK_WALLS = b.comment("Ghosts may smash through walls when they can't get through a door").define("breakWalls", true);
        b.pop();

        b.comment("What happens when a ghost catches you").push("death");
        KILL_MODE = b.comment("LETHAL_HIT: normal hits until one would kill you, then the kill cinematic.",
                "ON_CATCH: the first time it catches you it grabs you (hardcore).").defineEnum("killMode", KillMode.LETHAL_HIT);
        BETRAYAL_CHANCE = b.comment("Chance the ghost offers: 'tell me where your friend is and I might spare you' (needs another player online)")
                .defineInRange("betrayalChance", 0.25, 0, 1);
        BETRAYAL_SECONDS = b.comment("Seconds to choose").defineInRange("betrayalSeconds", 10, 3, 60);
        BETRAYAL_LIE_CHANCE = b.comment("Chance the ghost lies and kills you anyway after you sell your friend (it only said 'might')")
                .defineInRange("betrayalLieChance", 0.0, 0, 1);
        BETRAYAL_ACROSS_DIMENSIONS = b.comment("Friends in other dimensions can be sold too").define("betrayalAcrossDimensions", true);
        b.pop();

        b.comment("Talking to ghosts and asking them for items").push("talk");
        TALK_SUCCESS_CHANCE = b.comment("Base chance that negotiation makes the ghost leave").defineInRange("negotiateChance", 0.05, 0, 1);
        TALK_RAGE_CHANCE = b.comment("Chance a failed negotiation enrages it").defineInRange("rageChance", 0.25, 0, 1);
        ASK_GIVE_CHANCE = b.comment("Chance a ghost gives the item you ask for (always cursed)").defineInRange("askGiveChance", 0.5, 0, 1);
        ASK_LIMIT = b.comment("Requests per player per ghost visit").defineInRange("askLimit", 3, 0, 100);
        b.pop();

        b.comment("Optional real AI for ghost conversations. Without a key the built-in Thai/English brain is used.",
                "providers: gemini (free key: aistudio.google.com), openai, anthropic, openrouter, groq, ollama (local), custom (OpenAI-compatible url)")
                .push("ai");
        AI_ENABLED = b.define("enabled", false);
        AI_PROVIDER = b.define("provider", "gemini");
        AI_KEY = b.comment("API key (keep it secret!)").define("apiKey", "");
        AI_MODEL = b.comment("Leave empty for the provider default").define("model", "");
        AI_URL = b.comment("Only for provider=custom or ollama: base url, e.g. http://localhost:11434/v1").define("url", "");
        AI_TIMEOUT = b.defineInRange("timeoutSeconds", 20, 3, 120);
        b.pop();

        SPEC = b.build();
    }

    private HHConfig() {}
}
