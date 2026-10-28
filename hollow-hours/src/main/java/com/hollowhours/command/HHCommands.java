package com.hollowhours.command;

import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.ghost.Curses;
import com.hollowhours.ghost.Director;
import com.hollowhours.ghost.GhostTalk;
import com.hollowhours.ghost.GhostVisit;
import com.hollowhours.ghost.Jumpscares;
import com.hollowhours.ghost.KillSequence;
import com.hollowhours.ghost.Soul;
import com.hollowhours.ghost.Stalkers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiFunction;

/**
 * /hollow ... test and admin commands (op level 2). Ghost numbers: 1 Wail, 2 Widow, 3 Ash, 4 Void, 5 Stag (0/none = random).
 */
public final class HHCommands {
    private HHCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("hollow").requires(s -> s.hasPermission(2));
        root.then(ghostCmd("knock", (p, k) -> GhostVisit.startKnock(p, k, false) ? "ok" : "no door or window with open space outside within 10 blocks"));
        root.then(ghostCmd("breakin", (p, k) -> GhostVisit.startBreakIn(p, k) ? "ok" : "no door or window with open space outside within 10 blocks"));
        root.then(ghostCmd("hunt", HHCommands::hunt));
        root.then(ghostCmd("summon", (p, k) -> {
            Vec3 spot = Director.findSpot(p, true, 22, 30);
            if (spot == null) return "no spot";
            return Director.spawnGhost(p.serverLevel(), k, spot) != null ? "ok" : "failed";
        }));
        root.then(ghostCmd("kill", HHCommands::killTest));
        root.then(ghostCmd("jumpscare", (p, k) -> {
            Jumpscares.scare(p, k.n, Jumpscares.STRONG);
            return "ok";
        }));
        root.then(Commands.literal("power").then(Commands.argument("power", IntegerArgumentType.integer(0, 3)).executes(c -> {
            Director.forcedPower = IntegerArgumentType.getInteger(c, "power");
            return reply(c, "next ghost power: " + (Director.forcedPower == 0 ? "random" : Director.forcedPower));
        })));
        root.then(Commands.literal("stalker")
                .executes(c -> reply(c, Stalkers.test(c.getSource().getPlayerOrException(), 1 + (int) (Math.random() * 3), null)))
                .then(Commands.argument("type", IntegerArgumentType.integer(1, 3))
                        .executes(c -> reply(c, Stalkers.test(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "type"), null)))
                        .then(Commands.argument("event", StringArgumentType.word())
                                .executes(c -> reply(c, Stalkers.test(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "type"),
                                        StringArgumentType.getString(c, "event")))))));
        root.then(Commands.literal("soul")
                .executes(c -> reply(c, "soul: " + Math.round(Soul.get(c.getSource().getPlayerOrException()))))
                .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    float v = FloatArgumentType.getFloat(c, "value");
                    float cur = Soul.get(p);
                    if (v < cur) Soul.drain(p, cur - v, false);
                    else Soul.heal(p, v - cur, "hollowhours.soul.healed");
                    return reply(c, "soul: " + Math.round(Soul.get(p)));
                })));
        root.then(Commands.literal("talk").then(Commands.argument("ghost", IntegerArgumentType.integer(0, 5))
                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                    GhostTalk.talk(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "ghost"), StringArgumentType.getString(c, "text"), null);
                    return 1;
                }))));
        root.then(Commands.literal("ask").then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
            Curses.askGhost(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "text"));
            return 1;
        })));
        root.then(Commands.literal("story")
                .then(Commands.literal("start").executes(c -> {
                    com.hollowhours.ghost.Story.force(c.getSource().getServer(), 1);
                    return reply(c, "the haunting has started");
                }))
                .then(Commands.literal("hold").executes(c -> {
                    com.hollowhours.ghost.Story.force(c.getSource().getServer(), -1);
                    return reply(c, "the haunting is held back (normal world)");
                }))
                .then(Commands.literal("calendar").executes(c -> {
                    com.hollowhours.ghost.Story.force(c.getSource().getServer(), 0);
                    return reply(c, "the haunting follows the calendar again (starts on night " + com.hollowhours.config.HHConfig.START_NIGHT.get() + ")");
                }))
                .then(Commands.literal("alert").executes(c -> {
                    com.hollowhours.ghost.Story.sendAlert(c.getSource().getPlayerOrException());
                    return reply(c, "alert sent");
                })));
        root.then(Commands.literal("thirst").then(Commands.argument("value", FloatArgumentType.floatArg(0, 20)).executes(c -> {
            com.hollowhours.ghost.Thirst.set(c.getSource().getPlayerOrException(), FloatArgumentType.getFloat(c, "value"));
            return reply(c, "thirst set");
        })));
        root.then(Commands.literal("sanity").then(Commands.argument("value", FloatArgumentType.floatArg(0, 100)).executes(c -> {
            com.hollowhours.ghost.Sanity.set(c.getSource().getPlayerOrException(), FloatArgumentType.getFloat(c, "value"));
            return reply(c, "sanity set");
        })));
        root.then(Commands.literal("bloodmoon")
                .then(Commands.literal("on").executes(c -> {
                    com.hollowhours.ghost.Atmosphere.forceBlood = 1;
                    return reply(c, "blood moon forced on (it rises when night falls)");
                }))
                .then(Commands.literal("off").executes(c -> {
                    com.hollowhours.ghost.Atmosphere.forceBlood = -1;
                    return reply(c, "blood moon off");
                }))
                .then(Commands.literal("random").executes(c -> {
                    com.hollowhours.ghost.Atmosphere.forceBlood = 0;
                    return reply(c, "blood moon: random again");
                })));
        root.then(Commands.literal("fog")
                .then(Commands.literal("on").executes(c -> {
                    com.hollowhours.ghost.Atmosphere.startFog(c.getSource().getServer(), 5 * 60 * 20, 1f);
                    return reply(c, "fog for 5 minutes");
                }))
                .then(Commands.literal("off").executes(c -> {
                    com.hollowhours.ghost.Atmosphere.stopFog(c.getSource().getServer());
                    return reply(c, "fog cleared");
                })));
        root.then(Commands.literal("clear").executes(c -> {
            int n = 0;
            for (GhostEntity g : Director.allGhosts(c.getSource().getServer())) {
                g.discard();
                n++;
            }
            return reply(c, "removed " + n + " ghost(s)");
        }));
        d.register(root);
    }

    static LiteralArgumentBuilder<CommandSourceStack> ghostCmd(String name, BiFunction<ServerPlayer, GhostKind, String> f) {
        return Commands.literal(name)
                .executes(c -> run(c, f, 0))
                .then(Commands.argument("ghost", IntegerArgumentType.integer(0, 5)).executes(c -> run(c, f, IntegerArgumentType.getInteger(c, "ghost"))));
    }

    static int run(CommandContext<CommandSourceStack> c, BiFunction<ServerPlayer, GhostKind, String> f, int n) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        GhostKind k = n >= 1 && n <= 5 ? GhostKind.byN(n) : Director.randomKind();
        return reply(c, f.apply(p, k));
    }

    static int reply(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal("§7[Hollow Hours] " + msg), false);
        return 1;
    }

    /** It appears right behind you and snaps. */
    static String hunt(ServerPlayer p, GhostKind k) {
        Vec3 spot = Director.findSpot(p, true, 10, 14);
        if (spot == null) return "no spot";
        GhostEntity g = Director.spawnGhost(p.serverLevel(), k, spot);
        if (g == null) return "failed";
        g.brain().born = p.serverLevel().getGameTime() - 200;
        com.hollowhours.util.Later.run(4, () -> {
            if (g.isAlive()) g.brain().snap(p);
        });
        return "ok";
    }

    /** Plays the whole kill cinematic on yourself (creative players are not targetable, use survival). */
    static String killTest(ServerPlayer p, GhostKind k) {
        Vec3 f = p.getViewVector(1f).multiply(1, 0, 1).normalize();
        Vec3 at = p.position().add(f.scale(1.5));
        GhostEntity g = Director.spawnGhost(p.serverLevel(), k, at);
        if (g == null) return "failed";
        g.brain().born = p.serverLevel().getGameTime() - 200;
        com.hollowhours.util.Later.run(2, () -> {
            if (g.isAlive()) KillSequence.begin(g.brain(), p);
        });
        return "ok";
    }
}
