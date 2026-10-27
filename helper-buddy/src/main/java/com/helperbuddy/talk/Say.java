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

/** Messages from a helper to its owner, in the owner's language (Thai when the game is in Thai, otherwise English). */
public final class Say {
    private Say() {}

    /** Language each player last talked in (so a Thai player with an English game still gets Thai answers). */
    static final Map<UUID, Boolean> SPOKE_THAI = new HashMap<>();

    public static void noteLanguage(Player p, String text) {
        if (Nlu.looksThai(text)) SPOKE_THAI.put(p.getUUID(), true);
        else if (text.matches(".*[a-zA-Z]{3,}.*")) SPOKE_THAI.put(p.getUUID(), false);
    }

    public static boolean thai(@Nullable Player p) {
        if (p == null) return false;
        Boolean b = SPOKE_THAI.get(p.getUUID());
        if (b != null) return b;
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

    public static void to(@Nullable Player p, HelperEntity h, String th, String en) {
        to(p, h, Component.literal(t(p, th, en)));
    }

    public static void owner(HelperEntity h, String th, String en) {
        Player p = h.owner();
        if (p != null) to(p, h, th, en);
    }

    public static void owner(HelperEntity h, Component msg) {
        Player p = h.owner();
        if (p != null) to(p, h, msg);
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
