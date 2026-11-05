package com.hollowhours.client;

import com.hollowhours.HollowHours;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The main menu: the panorama behind it is a haunted clearing with the five standing round you (it replaces the
 * vanilla panorama textures); under the Minecraft logo, "HOLLOW HOURS" in red that now and then flickers.
 */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TitleDecor {
    private TitleDecor() {}

    static final long START = System.currentTimeMillis();

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof TitleScreen s)) return;
        GuiGraphics g = e.getGuiGraphics();
        Font f = Minecraft.getInstance().font;
        float t = (System.currentTimeMillis() - START) / 1000f;
        float in = Mth.clamp((t - 0.6f) / 1.5f, 0, 1);
        if (in <= 0) return;
        // a flicker every few seconds, like a dying bulb
        float flick = 1f;
        float ph = t % 5.3f;
        if (ph > 4.6f && ph < 4.75f || ph > 4.85f && ph < 4.9f) flick = 0.25f;
        int alpha = (int) (255 * in * flick);
        if (alpha < 6) return;
        Component title = Component.literal("HOLLOW HOURS");
        float scale = 1.6f;
        int w = f.width(title);
        int y = Math.min(84, s.height / 4 + 48 - 22);
        g.pose().pushPose();
        g.pose().translate(s.width / 2f, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(f, title, -w / 2 + 1, 1, (alpha << 24) | 0x1A0000, false);
        g.drawString(f, title, -w / 2, 0, (alpha << 24) | 0xC0201A, false);
        g.pose().popPose();
    }
}
