package com.helperbuddy.net;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.Helpers;
import com.helperbuddy.entity.HelperEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class HBNet {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HelperBuddy.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private HBNet() {}

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(S2COpen.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(S2COpen::encode).decoder(S2COpen::decode).consumerMainThread(S2COpen::handle).add();
        CHANNEL.messageBuilder(C2SAction.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(C2SAction::encode).decoder(C2SAction::decode).consumerMainThread(C2SAction::handle).add();
        CHANNEL.messageBuilder(C2STalk.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(C2STalk::encode).decoder(C2STalk::decode).consumerMainThread(C2STalk::handle).add();
    }

    public static void toPlayer(ServerPlayer p, Object msg) {
        if (p == null || p.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
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
