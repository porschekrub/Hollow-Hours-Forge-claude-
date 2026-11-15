package com.veritypal.tools;

import com.veritypal.V;
import com.veritypal.VerityPal;
import com.veritypal.config.PalConfig;
import com.veritypal.friends.HelperFriends;
import com.veritypal.mind.Memory;
import com.veritypal.world.Guide;
import com.veritypal.world.Hide;
import com.veritypal.world.HouseBuilder;
import com.veritypal.world.Life;
import com.veritypal.world.Places;
import com.veritypal.world.Senses;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import varmite.verity.entity.llm.actions.Tools;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The new things Verity can do, given to her AI next to Verity JE's own tools (AiApiMixin). Her AI calls them from
 * its own thread: every one does its work on the server thread and answers in plain English for the AI, which tells
 * the player in their language and in character.
 */
public final class PalTools {
    final ServerPlayer player;
    final MinecraftServer server;

    PalTools(ServerPlayer player, MinecraftServer server) {
        this.player = player;
        this.server = server;
    }

    /** Verity JE's tools for this question, plus ours (for the same player). */
    public static Object[] withOurs(Object[] tools) {
        try {
            for (Object o : tools) {
                if (o instanceof Tools t && t.server != null && t.player != null) {
                    Object[] out = Arrays.copyOf(tools, tools.length + 1);
                    out[tools.length] = new PalTools(t.player, t.server);
                    return out;
                }
            }
        } catch (Throwable t) {
            VerityPal.LOG.warn("[Verity Pal] could not give Verity her new tools: {}", t.toString());
        }
        return tools;
    }

    <T> T call(String what, Supplier<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        server.execute(() -> {
            try {
                f.complete(task.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            T r = f.get(20, TimeUnit.SECONDS);
            VerityPal.LOG.info("[Verity Pal] tool {} -> {}", what, r);
            return r;
        } catch (Exception e) {
            VerityPal.LOG.warn("[Verity Pal] tool {} failed: {}", what, e.toString());
            throw new IllegalStateException("could not do it right now: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ places
    @Tool("Find a place and tell which way it is and how far from the player: a structure (village, stronghold, ancient city ...), a biome (desert, cherry grove ...), "
            + "a creature, their bed/home, the world spawn, where they died, a nether portal, coordinates, or a structure or biome of another mod. "
            + "Use for questions like 'where is ...?' or 'which way is ...?'.")
    public String where_is(@P(name = "place", description = "The place in English or its id, e.g. village, desert, ancient city, minecraft:stronghold, bed, spawn, 100 64 -200") String place) {
        if (!PalConfig.GUIDE.get()) return "That ability is turned off in the settings.";
        return call("where_is(" + place + ")", () -> {
            String[] why = new String[1];
            Places.Found f = Places.find(player, place, why);
            if (f == null) return "Could not find it: " + why[0];
            Places.LAST.put(player.getUUID(), f);
            return Places.describe(player, f);
        });
    }

    @Tool("Lead the player to a place: you roll ahead, they follow you, any distance. Use when they ask you to take them / show them the way somewhere. "
            + "'there' = the last place asked about.")
    public String guide_me_to(@P(name = "place", description = "Same as for where_is, or 'there' for the last place asked about") String place) {
        if (!PalConfig.GUIDE.get()) return "That ability is turned off in the settings.";
        return call("guide_me_to(" + place + ")", () -> {
            String[] why = new String[1];
            Places.Found f = Places.find(player, place, why);
            if (f == null) return "Could not find it: " + why[0];
            Places.LAST.put(player.getUUID(), f);
            String no = Guide.start(player, f);
            if (no != null) return "Can't lead the way: " + no + ". (" + Places.describe(player, f) + ")";
            return "You are now leading the way. " + Places.describe(player, f) + ". Tell them to follow you.";
        });
    }

    @Tool("Stop leading the way.")
    public String stop_guiding() {
        return call("stop_guiding", () -> {
            if (!Guide.active()) return "You weren't leading anywhere.";
            Guide.stop();
            return "Stopped.";
        });
    }

    // ------------------------------------------------------------------ seeing
    @Tool("Look around the player: the creatures near (and which mod they come from), what the player looks at and holds, the biome and structure they are in, "
            + "notable blocks. Use for 'what's around?', 'what is that?', 'which mod is this from?'.")
    public String look_around() {
        return call("look_around", () -> Senses.lookAround(player));
    }

    @Tool("The mods installed in this game and what they add, or one mod in detail (its creatures and items).")
    public String about_mods(@P(name = "mod", description = "A mod's name, or empty for all of them", required = false) String mod) {
        if (!PalConfig.SEE_MODS.get()) return "That ability is turned off in the settings.";
        return call("about_mods(" + mod + ")", () -> Senses.mods(mod));
    }

    // ------------------------------------------------------------------ doing things
    @Tool("Build a small house right in front of the player (by magic, no blocks needed): walls, windows, door, roof, bed, crafting table, furnace, chest, light.")
    public String build_house(@P(name = "style", description = "oak, spruce, stone or cherry", required = false) String style) {
        if (!PalConfig.BUILD.get()) return "That ability is turned off in the settings.";
        return call("build_house(" + style + ")", () -> {
            String no = HouseBuilder.start(player, style == null ? "oak" : style);
            return no == null ? "The house is going up right now in front of them (it takes a few seconds)." : "Can't build here: " + no;
        });
    }

    @Tool("Dig into the ground and hide there (you are a coward). Use when you are scared or want to hide.")
    public String hide() {
        return call("hide", () -> {
            Mob b = V.body();
            if (b == null) return "You are in the pocket already.";
            String no = Hide.start(server, b, null);
            return no == null ? "You are digging into the ground." : "Can't hide: " + no;
        });
    }

    @Tool("Climb back out of the ground after hiding.")
    public String come_out() {
        return call("come_out", () -> {
            if (!Hide.active()) return "You are not hiding.";
            Hide.askOut();
            return "You are climbing out.";
        });
    }

    @Tool("Roll away for a while to look around on your own (when you are bored or want to be left alone).")
    public String wander_off() {
        return call("wander_off", () -> {
            Mob b = V.body();
            if (b == null) return "You are in the pocket.";
            String no = Life.Wander.start(b, player);
            return no == null ? "You roll off to look around." : "Can't: " + no;
        });
    }

    @Tool("Come to the player right away from wherever you are (pop out of their pocket, or jump to them from far away).")
    public String come_to_me() {
        return call("come_to_me", () -> {
            Mob b = V.body();
            if (b == null) return Life.popOut(player) ? "You popped out of their pocket next to them." : "You are not in their pocket and not in the world.";
            if (Hide.active()) Hide.askOut();
            Life.comeTo(player, b);
            return "You are next to them now.";
        });
    }

    @Tool("Say something to a creature or a friend near you (for example a Helper Buddy helper, an animal, a monster). A helper will answer you.")
    public String talk_to(@P(name = "who", description = "Its name or kind, e.g. the helper's name, chicken, zombie") String who,
                          @P(name = "line", description = "What you say to it, in the player's language") String line) {
        return call("talk_to(" + who + ")", () -> {
            Entity e = nearest(who);
            if (e == null) return "There is no " + who + " near you.";
            Mob b = V.body();
            if (b != null) b.getLookControl().setLookAt(e, 30, 30);
            if (HelperFriends.isHelper(e)) {
                HelperFriends.chat(server, player, e, line);
                return "You said it to " + HelperFriends.name(e) + "; they will answer.";
            }
            V.sayTo(server, e.getName().getString(), line);
            return "You said it to the " + e.getName().getString() + ". (Animals and monsters don't answer.) Don't repeat the line to the player.";
        });
    }

    @Nullable
    Entity nearest(String who) {
        String w = who == null ? "" : who.toLowerCase(Locale.ROOT).trim();
        Mob b = V.body();
        Entity from = b != null && b.level() == player.level() ? b : player;
        Entity best = null;
        for (Entity e : ((ServerLevel) from.level()).getEntities(from, from.getBoundingBox().inflate(24), x -> x instanceof LivingEntity && x.isAlive() && x != player)) {
            String n = (HelperFriends.isHelper(e) ? HelperFriends.name(e) + " helper" : e.getName().getString()).toLowerCase(Locale.ROOT);
            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().replace('_', ' ');
            if (!w.isEmpty() && !n.contains(w) && !id.contains(w) && !w.contains(n)) continue;
            if (best == null || e.distanceToSqr(from) < best.distanceToSqr(from)) best = e;
        }
        return best;
    }

    // ------------------------------------------------------------------ people and memory
    @Tool("Change how much you like a player (you like each player differently). Positive when they are nice to you, negative when they are mean.")
    public String like_player(@P(name = "name", description = "The player's name") String name,
                              @P(name = "change", description = "-2 to +2") int change) {
        if (!PalConfig.LIKES.get()) return "That is turned off in the settings.";
        return call("like_player(" + name + "," + change + ")", () -> {
            ServerPlayer p = server.getPlayerList().getPlayerByName(name.trim());
            if (p == null) p = player;
            int now = Memory.addLike(p, Math.max(-2, Math.min(2, change)));
            return "You now like " + p.getGameProfile().getName() + " " + now + "/20.";
        });
    }

    @Tool("Remember a fact about the player in every world and server, forever (their name, what they like, a promise). For things only true in this world use add_to_memory.")
    public String remember_forever(@P(name = "fact", description = "The fact, short") String fact) {
        if (!PalConfig.MEMORY.get()) return "That is turned off in the settings.";
        return call("remember_forever", () -> {
            Memory.remember(player, fact);
            return "Remembered forever.";
        });
    }

    @Tool("Forget something you remembered forever about the player.")
    public String forget_forever(@P(name = "about", description = "What to forget") String about) {
        return call("forget_forever", () -> Memory.forget(player, about) ? "Forgotten." : "You didn't remember anything like that.");
    }
}
