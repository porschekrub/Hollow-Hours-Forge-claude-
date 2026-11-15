package com.veritypal.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** config/veritypal-common.toml (also on the page Mods > Verity Pal > Config). */
public final class PalConfig {
    private PalConfig() {}

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue STORY;
    public static final ForgeConfigSpec.BooleanValue STORY_DARKNESS;

    public static final ForgeConfigSpec.IntValue SASS;
    public static final ForgeConfigSpec.ConfigValue<String> LANGUAGE;
    public static final ForgeConfigSpec.DoubleValue MOODINESS;
    public static final ForgeConfigSpec.BooleanValue STUBBORN;
    public static final ForgeConfigSpec.BooleanValue CHATTY;
    public static final ForgeConfigSpec.IntValue CHAT_MINUTES;
    public static final ForgeConfigSpec.BooleanValue TEASE_ON_DEATH;
    public static final ForgeConfigSpec.BooleanValue WARN_WATCHERS;

    public static final ForgeConfigSpec.BooleanValue GUIDE;
    public static final ForgeConfigSpec.BooleanValue SEE_MODS;
    public static final ForgeConfigSpec.BooleanValue BUILD;
    public static final ForgeConfigSpec.BooleanValue HIDE;
    public static final ForgeConfigSpec.DoubleValue HIDE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue HIDE_GUESS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SCARY;
    public static final ForgeConfigSpec.BooleanValue SLEEP;
    public static final ForgeConfigSpec.BooleanValue WANDER;
    public static final ForgeConfigSpec.BooleanValue SUMMON;

    public static final ForgeConfigSpec.BooleanValue LIKES;
    public static final ForgeConfigSpec.BooleanValue MEMORY;
    public static final ForgeConfigSpec.BooleanValue FRIENDS;
    public static final ForgeConfigSpec.IntValue FRIEND_MINUTES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Verity JE's horror story").push("story");
        STORY = b.comment("Verity JE's own story: the countdown of days, her demon form, animals and villagers vanishing, the extreme darkness,",
                "the intro video and being kicked out. Off (the default): none of it happens, Verity is just your friend and helper.").define("storyMode", false);
        STORY_DARKNESS = b.comment("Keep Verity JE's extreme darkness even with the story off").define("darknessWithoutStory", false);
        b.pop();

        b.comment("How Verity talks and behaves").push("personality");
        SASS = b.comment("0 = polite and kind, 1 = dry and matter-of-fact (Verity's own way), 2 = cheeky: teases you like a smug friend,",
                "3 = rude: mocks you and swears a little (Thai: กู / มึง). Only with the story off.").defineInRange("sass", 2, 0, 3);
        LANGUAGE = b.comment("auto = answers in the language you talk to her in, thai = always Thai, english = always English").define("language", "auto");
        MOODINESS = b.comment("How often she doesn't feel like doing what you ask, or plays a small prank instead (0 = never, 1 = a lot)")
                .defineInRange("moodiness", 0.3, 0.0, 1.0);
        STUBBORN = b.comment("She may refuse to go away (or back in your pocket) when told to").define("stubborn", true);
        CHATTY = b.comment("She talks on her own now and then: comments, questions, complaints").define("chatty", true);
        CHAT_MINUTES = b.comment("About how many minutes between the things she says on her own").defineInRange("chatMinutes", 5, 1, 60);
        TEASE_ON_DEATH = b.comment("She says something (usually teasing) when you die").define("teaseOnDeath", true);
        WARN_WATCHERS = b.comment("She warns you when something stands far away staring at you").define("warnAboutWatchers", true);
        b.pop();

        b.comment("What she can do").push("abilities");
        GUIDE = b.comment("Lead you to places (\"take me to a village\"), any distance, structures and biomes of other mods too").define("guide", true);
        SEE_MODS = b.comment("She sees and knows the other mods: their creatures, blocks and items").define("seeMods", true);
        BUILD = b.comment("She can build you a small house (when she feels like it)").define("buildHouses", true);
        HIDE = b.comment("When a ghost or a scary monster comes she may dig into the ground and hide instead of helping").define("hideFromGhosts", true);
        HIDE_CHANCE = b.comment("How likely she hides (0 - 1)").defineInRange("hideChance", 0.75, 0.0, 1.0);
        HIDE_GUESS = b.comment("Also hide from big monsters of other mods with spooky names (ghost, demon, stalker ...), not only the list below").define("guessScary", true);
        SCARY = b.comment("Creatures she hides from: \"mod:creature\", or \"mod:*\" for all of a mod's creatures")
                .defineListAllowEmpty(List.of("scaryCreatures"), () -> List.of("hollowhours:*", "minecraft:warden", "minecraft:wither"), o -> o instanceof String);
        SLEEP = b.comment("When you sleep she sleeps next to your bed").define("sleepNextToYou", true);
        WANDER = b.comment("When she is bored she wanders off a little and comes back").define("wanderWhenBored", true);
        SUMMON = b.comment("Say her name in chat and she pops out of your pocket, or comes to you from far away").define("summonByName", true);
        b.pop();

        b.comment("Memory and friends").push("memory");
        LIKES = b.comment("She likes every player differently (multiplayer), and remembers who said what").define("likesPerPlayer", true);
        MEMORY = b.comment("She remembers you in every world and server on this computer (config/veritypal-memory.json)").define("rememberAcrossWorlds", true);
        FRIENDS = b.comment("With Helper Buddy: she is friends with the helpers and talks to them now and then").define("helperBuddyFriends", true);
        FRIEND_MINUTES = b.comment("About how many minutes between her chats with a helper").defineInRange("friendTalkMinutes", 10, 2, 120);
        b.pop();
        SPEC = b.build();
    }

    /** True when the config is loaded (it isn't for a moment while the game starts). */
    public static boolean ready() {
        return SPEC.isLoaded();
    }

    public static boolean story() {
        return ready() && STORY.get();
    }

    public static boolean on(ForgeConfigSpec.BooleanValue v) {
        return ready() ? v.get() : (Boolean) v.getDefault();
    }
}
