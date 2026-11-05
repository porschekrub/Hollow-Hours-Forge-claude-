package com.helperbuddy.net;

import com.helperbuddy.Helpers;
import com.helperbuddy.entity.HelperEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

/** The helper's screens, opened from the server. */
public final class Menus {
    private Menus() {}

    private static void toPlayer(ServerPlayer p, Object msg) {
        HBNet.toPlayer(p, msg);
    }

    /** Opens (or refreshes) the helper's menu on the player's screen. */
    public static void openMenu(ServerPlayer p, HelperEntity h) {
        toPlayer(p, new S2COpen("menu", Snapshot.of(h, p)));
    }

    public static void refresh(ServerPlayer p, HelperEntity h) {
        toPlayer(p, new S2COpen("refresh", Snapshot.of(h, p)));
    }

    /** The command book: every helper the player owns. */
    public static void openBook(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (HelperEntity h : Helpers.owned(p)) l.add(Snapshot.of(h, p));
        t.put("helpers", l);
        t.putBoolean("thai", com.helperbuddy.talk.Say.thai(p));
        toPlayer(p, new S2COpen("book", t));
    }
}
