package com.hollowhours.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Every packet of the mod, in a fixed order. Each loader's HHNet registers them on its own network API:
 * S2C handlers run on the client's main thread, C2S handlers on the server's with the sender.
 */
public final class Packets {
    /** Bump when a packet changes: client and server must have the same set. */
    public static final String VERSION = "11";

    public interface Registrar {
        <T> void s2c(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, Consumer<T> handle);

        <T> void c2s(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, BiConsumer<T, ServerPlayer> handle);
    }

    private Packets() {}

    public static void register(Registrar r) {
        r.s2c(S2CScreenFx.class, S2CScreenFx::encode, S2CScreenFx::decode, S2CScreenFx::handle);
        r.s2c(S2CJumpscare.class, S2CJumpscare::encode, S2CJumpscare::decode, S2CJumpscare::handle);
        r.s2c(S2CKill.class, S2CKill::encode, S2CKill::decode, S2CKill::handle);
        r.s2c(S2CBetrayal.class, S2CBetrayal::encode, S2CBetrayal::decode, S2CBetrayal::handle);
        r.s2c(S2CSpeech.class, S2CSpeech::encode, S2CSpeech::decode, S2CSpeech::handle);
        r.s2c(S2CMusic.class, S2CMusic::encode, S2CMusic::decode, S2CMusic::handle);
        r.s2c(S2CHud.class, S2CHud::encode, S2CHud::decode, S2CHud::handle);
        r.s2c(S2CGuide.class, S2CGuide::encode, S2CGuide::decode, S2CGuide::handle);
        r.s2c(S2CAtmos.class, S2CAtmos::encode, S2CAtmos::decode, S2CAtmos::handle);
        r.s2c(S2CBlood.class, S2CBlood::encode, S2CBlood::decode, S2CBlood::handle);
        r.s2c(S2CStats.class, S2CStats::encode, S2CStats::decode, S2CStats::handle);
        r.s2c(S2CPhoneAlert.class, S2CPhoneAlert::encode, S2CPhoneAlert::decode, S2CPhoneAlert::handle);
        r.s2c(S2CAnim.class, S2CAnim::encode, S2CAnim::decode, S2CAnim::handle);
        r.s2c(S2CNotice.class, S2CNotice::encode, S2CNotice::decode, S2CNotice::handle);
        r.s2c(S2CChat.class, S2CChat::encode, S2CChat::decode, S2CChat::handle);
        r.s2c(S2CPhoneInfo.class, S2CPhoneInfo::encode, S2CPhoneInfo::decode, S2CPhoneInfo::handle);
        r.s2c(S2CCombat.class, S2CCombat::encode, S2CCombat::decode, S2CCombat::handle);
        r.s2c(S2CEnding.class, S2CEnding::encode, S2CEnding::decode, S2CEnding::handle);
        r.c2s(C2SCombat.class, C2SCombat::encode, C2SCombat::decode, C2SCombat::handle);
        r.c2s(C2SPhone.class, C2SPhone::encode, C2SPhone::decode, C2SPhone::handle);
        r.c2s(C2SSip.class, C2SSip::encode, C2SSip::decode, C2SSip::handle);
        r.c2s(C2SBetrayChoice.class, C2SBetrayChoice::encode, C2SBetrayChoice::decode, C2SBetrayChoice::handle);
        r.c2s(C2STalk.class, C2STalk::encode, C2STalk::decode, C2STalk::handle);
        r.c2s(C2SChat.class, C2SChat::encode, C2SChat::decode, C2SChat::handle);
        r.c2s(C2SGuideRequest.class, C2SGuideRequest::encode, C2SGuideRequest::decode, C2SGuideRequest::handle);
    }
}
