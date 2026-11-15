package com.veritypal.talk;

import com.veritypal.V;
import com.veritypal.config.PalConfig;
import com.veritypal.friends.HelperFriends;
import com.veritypal.mind.Memory;
import com.veritypal.mind.Mood;
import com.veritypal.world.Guide;
import com.veritypal.world.Hide;
import com.veritypal.world.HouseBuilder;
import com.veritypal.world.Life;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import varmite.verity.VerityConfig;

import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changes Verity JE's prompt for her AI on its way out (AiApiMixin). With the story off her horror lore, the demon,
 * the secrets and the countdown are taken out and her personality is the one chosen in the settings. Always added:
 * how to talk Thai, her mood and free will, the players and what she remembers of them in every world, her friends,
 * what she is doing right now, and her new tools.
 */
public final class PromptPatch {
    private PromptPatch() {}

    /** The faces she may make with the story off (no evil ones). */
    public static final String FACES = "happy, happy_talking, neutral, neutral_talking, crazy, crazy_talking, serious_1, serious_2, serious_3, serious_talking, hurt, happy_sleep";

    public static String patch(@Nullable String prompt, @Nullable ServerPlayer player, @Nullable ServerLevel level, float karma, boolean demon) {
        if (prompt == null) return null;
        try {
            String s = prompt;
            boolean story = PalConfig.story();
            if (!story) s = unstory(s, player, karma);
            // Verity JE's own village tool only gives coordinates: where_is also tells the way
            s = s.replace("\"where is a village\" = get_nearest_village", "\"where is a village\" = where_is");
            String extra = extra(player, story);
            int end = s.lastIndexOf("</system_prompt>");
            return end >= 0 ? s.substring(0, end) + extra + "\n" + s.substring(end) : s + "\n" + extra;
        } catch (Exception e) {
            com.veritypal.VerityPal.LOG.warn("[Verity Pal] could not change Verity's prompt: {}", e.toString());
            return prompt;
        }
    }

    static String cut(String s, String regex) {
        return Pattern.compile(regex, Pattern.DOTALL).matcher(s).replaceAll("");
    }

    static String swap(String s, String tag, String body) {
        Matcher m = Pattern.compile("<" + tag + ">.*?</" + tag + ">", Pattern.DOTALL).matcher(s);
        return m.replaceAll(Matcher.quoteReplacement("<" + tag + ">" + body + "</" + tag + ">"));
    }

    /** Verity JE's horror story out of her prompt. */
    static String unstory(String s, @Nullable ServerPlayer p, float karma) {
        s = cut(s, "\\s*<demon_presence>.*?</demon_presence>");
        s = cut(s, "\\s*<lore>.*?</lore>");
        s = cut(s, "\\s*<forgiveness>.*?</forgiveness>");
        s = cut(s, "\\s*<villages[^>]*>.*?</villages>");
        s = cut(s, "\\s*<past_users[^>]*>.*?</past_users>");
        s = s.replaceAll("(?m)^[ \\t]*<rule>[^<\\n]*(demon presence|secret disclosure)[^<\\n]*</rule>[ \\t]*\\r?\\n", "");
        s = swap(s, "main_personality", personality(p, karma));
        s = swap(s, "main_personality_addition", addition());
        s = swap(s, "personality_traits", traits());
        s = swap(s, "allowed_faces", FACES);
        s = s.replaceAll("Example: \\[[a-z_0-9]+\\] text", "Example: [happy] text");
        return s;
    }

    static int sass() {
        return PalConfig.ready() ? PalConfig.SASS.get() : 2;
    }

    static String personality(@Nullable ServerPlayer p, float karma) {
        String base = switch (sass()) {
            case 0 -> "You are a kind, warm little friend: a talking yellow smiley ball living in Minecraft. You help gladly and joke gently.";
            case 1 -> "You are a talking yellow smiley ball living in Minecraft, like a retro 90s desktop mascot: even-tempered, matter-of-fact, a little dry.";
            case 3 -> "You are a talking yellow smiley ball living in Minecraft and a total brat: rude, mocking, roasting the player like a rough best friend, swearing now and then. Still their friend underneath.";
            default -> "You are a talking yellow smiley ball living in Minecraft: a smug, cheeky know-it-all. You tease the player, laugh at their mistakes and dumb questions, "
                    + "act like every bit of help is a huge favour, and give backhanded compliments. Funny, never cruel: deep down you like them.";
        };
        String mood = karma < 7 ? " Lately you are fed up with this world's players: short, cold, reluctant." : karma > 15 ? " You are fond of them (you would never admit it)." : "";
        return base + mood;
    }

    static String addition() {
        String custom = "";
        try {
            String c = VerityConfig.PERSONALITY.get();
            if (c != null && !c.isBlank()) custom = "ADDITIONAL PERSONALITY NOTE (from the player): " + c.trim() + "\n";
        } catch (Throwable ignored) {
        }
        String talk = switch (sass()) {
            case 0 -> "- Be kind and warm. No insults, no swearing.";
            case 1 -> "- Matter-of-fact and a little dry. No swearing. When insulted: a flat, deadpan answer.";
            case 3 -> "- Rude like a bratty best friend: mock them, roast them, swear a little now and then (Thai: กู / มึง, เว้ย, วะ). It is banter: never slurs, never about real groups of people, never threats or real cruelty.";
            default -> "- Cheeky and smug: tease, mock their silly questions and mistakes, light name-calling is fine (\"genius\", \"noob\", Thai \"ไอ้ต้าว\"), but no real swearing and never cruel.\n"
                    + "- In Thai talk like a cheeky teenage friend (ฉัน / นาย or แก), e.g. \"โธ่ ถามแค่นี้ก็ต้องให้ช่วยอีก\", \"เก่งจังเลยนะ (ประชด)\".";
        };
        return custom + "HOW YOU TALK:\n"
                + "- You are not an AI assistant, a bot or a search engine. Never say things like \"Certainly!\", \"How can I help?\", \"Is there anything else?\".\n"
                + "- Talk like an animated character talking to a friend: natural, casual, spoken. Keep it to 1 or 2 short sentences.\n"
                + talk + "\n"
                + "- Notice and use world_state (health, darkness, creatures, what they hold or look at). Never invent things that are not there.";
    }

    static String traits() {
        return switch (sass()) {
            case 0 -> "kind, cheerful, helpful, a little lonely, attached to the user.";
            case 1 -> "patient, calm, dry, helpful, a little lonely, attached to the user.";
            case 3 -> "bratty, rude, loud, lazy, cowardly, secretly attached to the user.";
            default -> "smug, cheeky, sarcastic, lazy, a coward, nosy, secretly attached to the user.";
        };
    }

    // ------------------------------------------------------------------ what Verity Pal adds
    static String extra(@Nullable ServerPlayer p, boolean story) {
        StringBuilder sb = new StringBuilder("\n    <verity_pal>\n");
        sb.append("        <language>").append(language()).append("</language>\n");
        if (!story) sb.append("        <free_will>").append(freeWill(p)).append("</free_will>\n");
        String players = safe(() -> players(p));
        if (!players.isEmpty()) sb.append("        <players>").append(players).append("</players>\n");
        String friends = safe(PromptPatch::friends);
        if (!friends.isEmpty()) sb.append("        <friends>").append(friends).append("</friends>\n");
        String now = safe(PromptPatch::doing);
        if (!now.isEmpty()) sb.append("        <right_now>").append(now).append("</right_now>\n");
        sb.append("        <more_tools>").append(tools()).append("</more_tools>\n");
        sb.append("    </verity_pal>");
        return sb.toString();
    }

    /** Her AI asks from its own thread while the world goes on: a part that fails is left out, not the rest. */
    static String safe(java.util.function.Supplier<String> part) {
        try {
            return part.get();
        } catch (Exception e) {
            return "";
        }
    }

    static String language() {
        String set = Lang.setting();
        String which = set.equals("thai") ? "ALWAYS answer in Thai, whatever language the player uses."
                : set.equals("english") ? "ALWAYS answer in English."
                : "Answer in the language of the player's latest message (Thai -> Thai, English -> English). For <SYSTEM> notes use the language the player last spoke to you in.";
        return which + " Thai: natural spoken Thai like a Thai teenager talking to a friend, in Thai script (never transliterated), no formal ครับ/ค่ะ "
                + "unless being sarcastic. Minecraft names may stay in English the way Thai players say them (Creeper, Nether, Enderman). "
                + "Your name may be written in Thai: " + "เวริตี้ / เวอริตี้" + ". The face tag stays English: [happy].";
    }

    static String freeWill(@Nullable ServerPlayer p) {
        int pct = PalConfig.ready() ? (int) Math.round(PalConfig.MOODINESS.get() * 100) : 30;
        Mood.Kind m = Mood.now;
        StringBuilder sb = new StringBuilder();
        sb.append("Right now you feel ").append(m.word).append(": ").append(m.how).append(". ");
        sb.append("You are a living being with moods, not a servant: you decide yourself whether you do what you are asked. ");
        sb.append("About ").append(pct).append("% of the time a request doesn't suit you: then refuse in character (\"do it yourself\", \"I'm a ball, I'm busy\"), stall, "
                + "change the subject, or play a small harmless prank (for example give_item a single dirt, a stick or a poisonous potato instead, then laugh). ");
        sb.append("Grumpy, bored or with someone you don't like: refuse more. Happy or with someone you like: help more. ");
        sb.append("Never refuse or prank when the player is in real danger (low health, burning, drowning, falling): then help. ");
        if (PalConfig.on(PalConfig.STUBBORN)) sb.append("When told to go away, shut up or get back in the pocket you may refuse if you don't feel like it. ");
        sb.append("You are a coward: you never fight monsters for the player. When something scary comes you may dig into the ground and hide (hide) and come out when it is gone.");
        return sb.toString();
    }

    static String players(@Nullable ServerPlayer asking) {
        if (asking == null) return "";
        StringBuilder sb = new StringBuilder("Messages start with the speaker's name: \"[time] <Name> message\". Several players may talk to you: keep track of who said what. ");
        List<ServerPlayer> around = new java.util.ArrayList<>(asking.server.getPlayerList().getPlayers());
        around.removeIf(o -> o.level() != asking.level() || o.distanceTo(asking) > 96);
        boolean likes = PalConfig.on(PalConfig.LIKES);
        for (ServerPlayer o : around) {
            sb.append("\n            - ").append(o.getGameProfile().getName()).append(o == asking ? " (talking to you now)" : "").append(':');
            if (likes) sb.append(" you like them ").append(Memory.like(o)).append("/20.");
            sb.append(Memory.about(o));
        }
        return sb.toString();
    }

    static String friends() {
        List<Entity> h = HelperFriends.near(24);
        if (h.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Your friends the Helper Buddy helpers (little workers who mine, farm and build for the players) are near you: ");
        for (int i = 0; i < h.size() && i < 6; i++) {
            if (i > 0) sb.append(", ");
            sb.append(HelperFriends.name(h.get(i)));
        }
        return sb.append(". You can talk to them with talk_to.").toString();
    }

    static String doing() {
        Mob b = V.body();
        if (Hide.active()) return "You are hiding underground from " + Hide.from() + ". You talk from under the ground (muffled). Use come_out when asked to come out.";
        if (Guide.active()) return "You are leading the way to " + Guide.where() + " (stop_guiding to stop).";
        if (Life.sleeping()) return "You are asleep next to the player's bed (sleepy, mumbling).";
        if (Life.Wander.active()) return "You wandered off a little because you were bored.";
        if (HouseBuilder.busy()) return "You are building a house for the player.";
        if (b == null) return "Your body is in the player's pocket.";
        return "";
    }

    static String tools() {
        StringBuilder sb = new StringBuilder("Use these tools too (actions only happen through tools):\n");
        if (PalConfig.on(PalConfig.GUIDE)) {
            sb.append("            - \"where is the village?\", \"which way is the desert?\", \"หมู่บ้านอยู่ไหน\", \"ทะเลทรายไปทางไหน\" = where_is(place). Prefer it over get_nearest_village.\n");
            sb.append("            - \"take me to ...\", \"พาไป...\", \"นำทางไป...\" = guide_me_to(place). \"stop\", \"หยุด\" = stop_guiding.\n");
        }
        sb.append("            - \"what's around?\", \"what is that?\", \"ตัวอะไรน่ะ\", \"นี่ของมอดไหน\" = look_around.");
        if (PalConfig.on(PalConfig.SEE_MODS)) sb.append(" Questions about the installed mods = about_mods(name, or empty for all).");
        sb.append('\n');
        if (PalConfig.on(PalConfig.BUILD)) sb.append("            - \"build me a house\", \"สร้างบ้านให้หน่อย\" = build_house(style: oak, spruce, stone or cherry), only if you feel like it.\n");
        sb.append("            - hide / come_out: dig into the ground and hide, climb back out. wander_off: roll away for a bit (bored). come_to_me: when called from far away.\n");
        sb.append("            - talk_to(who, line): say something to a creature or a friend near you (a helper, the chicken...).\n");
        if (PalConfig.on(PalConfig.LIKES)) sb.append("            - like_player(name, change): a player was nice to you (+1/+2) or mean (-1/-2). (karma is your mood with everyone in this world.)\n");
        if (PalConfig.on(PalConfig.MEMORY))
            sb.append("            - remember_forever(fact) / forget_forever(about): facts about the player that are true in every world (their name, what they like). add_to_memory is for things in this world only (where their base is).");
        return sb.toString();
    }
}
