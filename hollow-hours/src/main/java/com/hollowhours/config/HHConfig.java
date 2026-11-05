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
    public static final ForgeConfigSpec.DoubleValue BLOOD_MOON_CHANCE, BLOOD_SPAWN_MULT, BLOOD_DAMAGE_MULT, BLOOD_SPEED_MULT, BLOOD_DOUBLE_CHANCE, FOG_CHANCE;
    public static final ForgeConfigSpec.BooleanValue FOG_ENABLED;
    public static final ForgeConfigSpec.IntValue START_NIGHT, CONTACTS_LOST_NIGHT;
    public static final ForgeConfigSpec.BooleanValue PHONE_ALERT, GIVE_PHONE, THIRST, SANITY, BLOOD, TREE_FELLING, EXPLOSION_DEBRIS;
    public static final ForgeConfigSpec.IntValue VEIN_MINING, GRAVE_MINUTES, GRAVE_HITS;
    public static final ForgeConfigSpec.DoubleValue THIRST_RATE, PHONE_DRAIN;
    public static final ForgeConfigSpec.BooleanValue GRAVES, RESCUE;
    public static final ForgeConfigSpec.BooleanValue KNOCKOUT, STRUGGLE, FITNESS;
    public static final ForgeConfigSpec.IntValue KO_SECONDS;
    public static final ForgeConfigSpec.DoubleValue STRUGGLE_SECONDS, GHOST_TOUGHNESS;
    public static final ForgeConfigSpec.IntValue RESCUE_DAY, RESCUE_MIN_DAYS, RESCUE_MAX_DAYS, RESCUE_PICKUP_MINUTES, INVASION_MIN_DAYS, INVASION_MAX_DAYS;
    public static final ForgeConfigSpec.DoubleValue MISSED_GHOST_MULT, MISSED_BLOOD_MULT, RETURN_GHOST_MULT, RETURN_BLOOD_MULT;
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

        b.comment("How the story begins").push("story");
        START_NIGHT = b.comment("The haunting starts on this night (1 = the very first night). Until then the world is perfectly normal.",
                "On that day an emergency alert comes through everyone's phone.").defineInRange("startNight", 3, 1, 1000);
        PHONE_ALERT = b.comment("The emergency alert (voice + subtitles) on the day the haunting starts").define("phoneAlert", true);
        GIVE_PHONE = b.comment("Every player starts with a phone (random battery; craft a charger)").define("givePhone", true);
        CONTACTS_LOST_NIGHT = b.comment("The night Mom, Dad and your best friend send their last message (then they can't be reached)").defineInRange("contactsLostNight", 15, 1, 1000);
        b.pop();

        b.comment("Survival").push("survival");
        THIRST = b.comment("Thirst: drink water (bottles, or sneak + right-click water with an empty hand)").define("thirst", true);
        THIRST_RATE = b.comment("How fast you get thirsty (1 = 10% faster than the hunger bar)").defineInRange("thirstRate", 1.0, 0.1, 5.0);
        PHONE_DRAIN = b.comment("How fast the phone battery drains (1 = about 30 minutes of screen time, 12 minutes of games per charge)").defineInRange("phoneDrain", 1.0, 0.1, 5.0);
        SANITY = b.comment("Sanity: drains in the dark, near ghosts and when they jump at you; play, sleep and stay with friends to keep it").define("sanity", true);
        BLOOD = b.comment("Blood: hits make every creature bleed in its own colour, badly hurt creatures drip").define("blood", true);
        b.pop();

        b.comment("Physics").push("physics");
        TREE_FELLING = b.comment("Chop one log and the whole tree falls over (sneak to break a single log)").define("treeFelling", true);
        VEIN_MINING = b.comment("Mine one ore and up to this many touching ores of the same kind break with it (sneak = just one; 1 = off)")
                .defineInRange("veinMiningMax", 64, 1, 512);
        EXPLOSION_DEBRIS = b.comment("Explosions throw blocks through the air (they land as rubble)").define("explosionDebris", true);
        b.pop();

        b.comment("Graves: your items and experience wait where you died (not with keepInventory)").push("graves");
        GRAVES = b.comment("Make a grave when a player dies").define("enabled", true);
        GRAVE_MINUTES = b.comment("Minutes before a grave crumbles (its items can then only come back with a Grave Restorer)").defineInRange("minutes", 15, 1, 600);
        GRAVE_HITS = b.comment("Hits from its owner that break a grave").defineInRange("hitsToBreak", 3, 1, 20);
        b.pop();

        b.comment("The rescue: a helicopter to the haven (a land with no ghosts), the ghosts finding it, the flight back").push("rescue");
        RESCUE = b.comment("The rescue story").define("enabled", true);
        RESCUE_DAY = b.comment("The day the message comes: help is on its way").defineInRange("messageDay", 7, 1, 1000);
        RESCUE_MIN_DAYS = b.comment("Fewest days until the helicopter comes").defineInRange("minDays", 35, 1, 1000);
        RESCUE_MAX_DAYS = b.comment("Most days until the helicopter comes").defineInRange("maxDays", 100, 1, 1000);
        RESCUE_PICKUP_MINUTES = b.comment("Minutes the helicopter waits for you").defineInRange("pickupMinutes", 5, 1, 60);
        INVASION_MIN_DAYS = b.comment("Fewest days in the haven before the ghosts find it").defineInRange("invasionMinDays", 5, 1, 1000);
        INVASION_MAX_DAYS = b.comment("Most days in the haven before the ghosts find it").defineInRange("invasionMaxDays", 10, 1, 1000);
        MISSED_GHOST_MULT = b.comment("Missed the helicopter: ghosts this many times as cruel (damage, how often they come)").defineInRange("missedGhostMultiplier", 2.0, 1, 20);
        MISSED_BLOOD_MULT = b.comment("Missed the helicopter: blood moon ghosts this many times as cruel").defineInRange("missedBloodMoonMultiplier", 5.0, 1, 20);
        RETURN_GHOST_MULT = b.comment("Back from the haven: ghosts this many times as cruel").defineInRange("returnGhostMultiplier", 3.0, 1, 20);
        RETURN_BLOOD_MULT = b.comment("Back from the haven: blood moon ghosts this many times as cruel").defineInRange("returnBloodMoonMultiplier", 6.0, 1, 20);
        b.pop();

        b.comment("Blood moon nights and rolling fog").push("atmosphere");
        BLOOD_MOON_CHANCE = b.comment("Chance that a night is a blood moon").defineInRange("bloodMoonChance", 0.10, 0, 1);
        BLOOD_SPAWN_MULT = b.comment("Blood moon: ghosts come this many times more often").defineInRange("bloodMoonSpawnMultiplier", 3.0, 1, 10);
        BLOOD_DAMAGE_MULT = b.comment("Blood moon: ghost damage multiplier").defineInRange("bloodMoonDamageMultiplier", 3.0, 1, 10);
        BLOOD_SPEED_MULT = b.comment("Blood moon: ghost speed multiplier").defineInRange("bloodMoonSpeedMultiplier", 1.25, 1, 3);
        BLOOD_DOUBLE_CHANCE = b.comment("Blood moon: chance that two ghosts come at once").defineInRange("bloodMoonDoubleChance", 0.5, 0, 1);
        FOG_ENABLED = b.comment("Thick fog rolls in now and then").define("fog", true);
        FOG_CHANCE = b.comment("Chance per minute that fog rolls in (doubled at night)").defineInRange("fogChancePerMinute", 0.05, 0, 1);
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

        b.comment("Optional real AI for ghost conversations: the ghost understands any sentence (questions, begging, offers of any item,",
                "asking it for an item) and answers in its own words. Without a key the built-in Thai/English brain is used.",
                "providers: gemini (free key: aistudio.google.com), openai, anthropic, openrouter, groq, ollama (local), custom (OpenAI-compatible url)")
                .push("ai");
        AI_ENABLED = b.define("enabled", false);
        AI_PROVIDER = b.define("provider", "gemini");
        AI_KEY = b.comment("API key (keep it secret!)").define("apiKey", "");
        AI_MODEL = b.comment("Leave empty for the provider default").define("model", "");
        AI_URL = b.comment("Only for provider=custom or ollama: base url, e.g. http://localhost:11434").define("url", "");
        AI_TIMEOUT = b.defineInRange("timeoutSeconds", 20, 3, 120);
        b.pop();

        b.comment("Fighting back: weapons and armour, knocking ghosts out, wriggling free of their grip, getting stronger").push("combat");
        KNOCKOUT = b.comment("Ghosts can be knocked out by hitting them (weapons, guns, grenades); they wake up again later").define("knockOut", true);
        KO_SECONDS = b.comment("How long a knocked-out ghost stays down").defineInRange("knockOutSeconds", 70, 5, 3600);
        GHOST_TOUGHNESS = b.comment("How much hitting it takes to knock a ghost out (1 = normal)").defineInRange("ghostToughness", 1.0, 0.1, 20);
        STRUGGLE = b.comment("When a ghost grabs you: mash a key to break free (easier when you are stronger / better armoured)").define("struggle", true);
        STRUGGLE_SECONDS = b.comment("Time you get to break free").defineInRange("struggleSeconds", 5.0, 1, 20);
        FITNESS = b.comment("Strength: playing sports on the playground makes you stronger (more health, harder hits, easier struggles)").define("fitness", true);
        b.pop();

        SPEC = b.build();
    }

    private HHConfig() {}
}
