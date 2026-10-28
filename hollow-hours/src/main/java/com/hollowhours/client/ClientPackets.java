package com.hollowhours.client;

import com.hollowhours.net.S2CBetrayal;
import com.hollowhours.net.S2CGuide;
import com.hollowhours.net.S2CHud;
import com.hollowhours.net.S2CJumpscare;
import com.hollowhours.net.S2CKill;
import com.hollowhours.net.S2CMusic;
import com.hollowhours.net.S2CScreenFx;
import com.hollowhours.net.S2CSpeech;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client handlers for every server packet (only ever loaded on the client). */
public final class ClientPackets {
    private ClientPackets() {}

    public static void screenFx(S2CScreenFx m) {
        ClientFx.apply(m);
    }

    public static void jumpscare(S2CJumpscare m) {
        Jumpscare.start(m.who(), m.strength(), m.variant());
    }

    public static void kill(S2CKill m) {
        KillCam.packet(m);
    }

    public static void betrayal(S2CBetrayal m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.open()) mc.setScreen(new BetrayalScreen(m));
        else if (mc.screen instanceof BetrayalScreen) mc.setScreen(null);
    }

    public static void speech(S2CSpeech m) {
        ClientFx.speech(m.kind(), m.text(), m.style());
        if (m.style() != 3 || !m.text().getString().equals("...")) {
            Component who = m.kind() >= 1 && m.kind() <= 5 ? Component.translatable("entity.hollowhours.ghost_" + m.kind()) : Component.literal("?");
            GuideScreen.logSpeech(Component.empty().append(who.copy().withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY)).append(m.text().copy().withStyle(ChatFormatting.DARK_RED)));
        }
    }

    public static void music(S2CMusic m) {
        HorrorMusic.set(m.key());
    }

    public static void atmos(com.hollowhours.net.S2CAtmos m) {
        Atmos.set(m.bloodMoon(), m.fog());
        Atmos.haunted = m.haunted();
    }

    public static void stats(com.hollowhours.net.S2CStats m) {
        SurvivalHud.thirst = m.thirst();
        SurvivalHud.sanity = m.sanity();
    }

    public static void blood(com.hollowhours.net.S2CBlood m) {
        BloodParticle.spawn(m);
    }

    public static void phoneAlert(com.hollowhours.net.S2CPhoneAlert m) {
        PhoneAlert.start(m.phone());
    }

    public static void hud(S2CHud m) {
        ClientFx.hud(m);
    }

    public static void guide(S2CGuide m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GuideScreen gs) gs.refresh(m);
        else mc.setScreen(new GuideScreen(m));
    }
}
