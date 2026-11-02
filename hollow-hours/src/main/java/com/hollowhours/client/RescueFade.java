package com.hollowhours.client;

import com.hollowhours.rescue.HelicopterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Aboard a helicopter that's lifting off: the screen fades to black just before you're flown on. */
public final class RescueFade {
    private RescueFade() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        var p = Minecraft.getInstance().player;
        if (p == null || !(p.getVehicle() instanceof HelicopterEntity heli) || !heli.isLeaving()) return;
        float k = Mth.clamp((heli.stateTicks + partial - (HelicopterEntity.TRANSFER_AT - 45)) / 40f, 0, 1);
        if (k <= 0) return;
        g.fill(0, 0, w, h, (Math.round(k * 255) << 24));
    }
}
