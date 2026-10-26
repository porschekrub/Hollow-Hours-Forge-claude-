package com.hollowhours.net;

import com.hollowhours.HollowHours;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class HHNet {
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HollowHours.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private HHNet() {}

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(S2CScreenFx.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CScreenFx::encode).decoder(S2CScreenFx::decode).consumerMainThread(S2CScreenFx::handle).add();
        CHANNEL.messageBuilder(S2CJumpscare.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CJumpscare::encode).decoder(S2CJumpscare::decode).consumerMainThread(S2CJumpscare::handle).add();
        CHANNEL.messageBuilder(S2CKill.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CKill::encode).decoder(S2CKill::decode).consumerMainThread(S2CKill::handle).add();
        CHANNEL.messageBuilder(S2CBetrayal.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBetrayal::encode).decoder(S2CBetrayal::decode).consumerMainThread(S2CBetrayal::handle).add();
        CHANNEL.messageBuilder(S2CSpeech.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSpeech::encode).decoder(S2CSpeech::decode).consumerMainThread(S2CSpeech::handle).add();
        CHANNEL.messageBuilder(S2CMusic.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMusic::encode).decoder(S2CMusic::decode).consumerMainThread(S2CMusic::handle).add();
        CHANNEL.messageBuilder(S2CHud.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CHud::encode).decoder(S2CHud::decode).consumerMainThread(S2CHud::handle).add();
        CHANNEL.messageBuilder(S2CGuide.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CGuide::encode).decoder(S2CGuide::decode).consumerMainThread(S2CGuide::handle).add();
        CHANNEL.messageBuilder(C2SBetrayChoice.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBetrayChoice::encode).decoder(C2SBetrayChoice::decode).consumerMainThread(C2SBetrayChoice::handle).add();
        CHANNEL.messageBuilder(C2STalk.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2STalk::encode).decoder(C2STalk::decode).consumerMainThread(C2STalk::handle).add();
        CHANNEL.messageBuilder(C2SGuideRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SGuideRequest::encode).decoder(C2SGuideRequest::decode).consumerMainThread(C2SGuideRequest::handle).add();
    }

    public static void toPlayer(ServerPlayer p, Object msg) {
        if (p == null || p.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
