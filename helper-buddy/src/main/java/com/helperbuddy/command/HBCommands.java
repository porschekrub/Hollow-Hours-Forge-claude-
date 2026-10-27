package com.helperbuddy.command;

import com.helperbuddy.Helpers;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.item.CommandBookItem;
import com.helperbuddy.net.HBNet;
import com.helperbuddy.talk.Say;
import com.helperbuddy.talk.Talk;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** /helper <anything you would say> (also /hb), /helper list, /helper book. */
public final class HBCommands {
    private HBCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String root : new String[] {"helper", "hb"}) {
            LiteralArgumentBuilder<CommandSourceStack> b = Commands.literal(root)
                    .executes(c -> {
                        Talk.help(c.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(Commands.literal("list").executes(c -> list(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("book").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        CommandBookItem.give(p);
                        HBNet.openBook(p);
                        return 1;
                    }))
                    .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                        Talk.command(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "text"));
                        return 1;
                    }));
            d.register(b);
        }
    }

    static int list(ServerPlayer p) {
        List<HelperEntity> mine = Helpers.owned(p);
        if (mine.isEmpty()) {
            p.sendSystemMessage(Component.literal(Say.t(p, "§6[ผู้ช่วย]§r คุณยังไม่มีผู้ช่วย (หรืออยู่ในพื้นที่ที่ยังไม่โหลด)", "§6[Helper]§r You have no helpers (or they are in unloaded areas)")));
            return 0;
        }
        StringBuilder sb = new StringBuilder(Say.t(p, "§6ผู้ช่วยของคุณ:", "§6Your helpers:"));
        for (HelperEntity h : mine)
            sb.append("\n§e").append(h.helperName()).append("§r - ").append(h.mode().label(Say.thai(p))).append(" §7(").append(h.blockPosition().toShortString()).append(", ")
                    .append(h.level().dimension().location().getPath()).append(") ").append((int) Math.ceil(h.shownHp())).append("/").append(h.maxHp());
        p.sendSystemMessage(Component.literal(sb.toString()));
        return mine.size();
    }
}
