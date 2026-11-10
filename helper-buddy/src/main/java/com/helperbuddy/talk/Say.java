package com.helperbuddy.talk;

import com.helperbuddy.entity.HelperEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Messages from a helper to its owner, in the language of the owner's game: Thai when the game is set to Thai, English for everything else. */
public final class Say {
    private Say() {}

    /** Thai only when the player's game is set to Thai (whatever language they type in); English otherwise. */
    public static boolean thai(@Nullable Player p) {
        return p instanceof ServerPlayer sp && sp.getLanguage() != null && sp.getLanguage().toLowerCase().startsWith("th");
    }

    public static String t(@Nullable Player p, String th, String en) {
        return thai(p) ? th : en;
    }

    public static MutableComponent lit(String s) {
        return Component.literal(s);
    }

    public static MutableComponent item(Item item) {
        return Component.translatable(item.getDescriptionId()).withStyle(ChatFormatting.AQUA);
    }

    public static MutableComponent item(ItemStack st) {
        return st.getHoverName().copy().withStyle(ChatFormatting.AQUA);
    }

    /** "[Buddy] message" */
    public static void to(@Nullable Player p, HelperEntity h, Component msg) {
        if (p == null) return;
        MutableComponent head = Component.literal("[").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(h.helperName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("] ").withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(head.append(msg));
    }

    /** A line in the helper's own way of talking (polite / sassy / rude), spoken with its voice. */
    public static void to(@Nullable Player p, HelperEntity h, String th, String en) {
        if (p == null) return;
        Persona.Style st = Persona.of(h);
        String line = thai(p) ? Persona.th(st, th, Persona.R) : Persona.en(st, en, Persona.R);
        to(p, h, Component.literal(line));
        Voice.speak(p, h, line);
    }

    /** A line exactly as given (the AI already wrote it in the helper's style). */
    public static void raw(@Nullable Player p, HelperEntity h, String text) {
        if (p == null) return;
        to(p, h, Component.literal(text));
        Voice.speak(p, h, text);
    }

    /** (the server smoke test: what a helper without an owner would have said goes to the log) */
    public static final boolean LOG_UNHEARD = Boolean.getBoolean("helperbuddy.smoketest");

    public static void owner(HelperEntity h, String th, String en) {
        Player p = h.owner();
        if (p != null) to(p, h, th, en);
        else if (LOG_UNHEARD) com.helperbuddy.HelperBuddy.LOG.info("[{} says] {}", h.helperName(), en);
    }

    public static void owner(HelperEntity h, Component msg) {
        Player p = h.owner();
        if (p != null) to(p, h, msg);
        else if (LOG_UNHEARD) com.helperbuddy.HelperBuddy.LOG.info("[{} says] {}", h.helperName(), msg.getString());
    }

    /** Rate limited message to the owner. */
    public static void notify(HelperEntity h, String key, int cooldownTicks, String th, String en) {
        if (!h.brain().cooldown("msg:" + key, cooldownTicks)) return;
        owner(h, th, en);
    }

    public static void notify(HelperEntity h, String key, int cooldownTicks, Component msg) {
        if (!h.brain().cooldown("msg:" + key, cooldownTicks)) return;
        owner(h, msg);
    }

    /** Builds "text1 <item> text2" for the owner's language. */
    public static MutableComponent mix(@Nullable Player p, String th, String en, Object... parts) {
        String s = t(p, th, en);
        MutableComponent out = Component.empty();
        int i = 0;
        int idx;
        int start = 0;
        while ((idx = s.indexOf("{}", start)) >= 0) {
            out.append(Component.literal(s.substring(start, idx)));
            if (i < parts.length) {
                Object o = parts[i++];
                if (o instanceof Component c) out.append(c);
                else if (o instanceof Item it) out.append(item(it));
                else if (o instanceof ItemStack st) out.append(item(st));
                else out.append(Component.literal(String.valueOf(o)));
            }
            start = idx + 2;
        }
        out.append(Component.literal(s.substring(start)));
        return out;
    }
}
