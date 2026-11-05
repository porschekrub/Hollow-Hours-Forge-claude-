package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.hollowhours.net.S2CEnding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** The rift: everything goes white, a boom, then the credits roll. */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EndingClient {
    private EndingClient() {}

    static int flash = -1;
    static List<String> names = new ArrayList<>();
    static final int WHITE_IN = 6, HOLD = 34;

    public static void packet(S2CEnding m) {
        names = new ArrayList<>(m.names());
        if (m.type() == S2CEnding.CREDITS) {
            open();
            return;
        }
        flash = 0;
        HorrorMusic.set("");
        ClientFx.addShake(1.4f, 30);
    }

    static void open() {
        flash = -1;
        Minecraft.getInstance().setScreen(new CreditsScreen(names));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || flash < 0) return;
        flash++;
        if (flash >= WHITE_IN + HOLD) open();
    }

    /** White over everything while the rift takes you. */
    public static void render(GuiGraphics g, float partial, int w, int h) {
        if (flash < 0) return;
        float t = flash + partial;
        float a = Math.min(1f, t / WHITE_IN);
        int alpha = Math.round(255 * a);
        g.fill(0, 0, w, h, (alpha << 24) | 0xFFFFFF);
    }
}
