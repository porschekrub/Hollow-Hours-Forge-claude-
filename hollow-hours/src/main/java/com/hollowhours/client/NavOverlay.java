package com.hollowhours.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Navigating with the phone's map: an arrow and the distance at the top of the screen; it stops when you get there. */
public final class NavOverlay {
    private NavOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        ClientPhone.Nav nav = ClientPhone.nav;
        Minecraft mc = Minecraft.getInstance();
        if (nav == null || mc.player == null || mc.level == null || mc.options.hideGui) return;
        Font f = mc.font;
        int cx = w / 2, y = 22;
        if (!nav.dim().equals(mc.level.dimension().location().toString())) {
            Component c = Component.translatable("hollowhours.map.nav.elsewhere", nav.label());
            g.drawCenteredString(f, c, cx, y + 6, 0xFFB0C0D8);
            return;
        }
        double dx = nav.x() + 0.5 - mc.player.getX(), dz = nav.z() + 0.5 - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 3) {
            ClientPhone.navigate(null);
            mc.player.displayClientMessage(Component.translatable("hollowhours.map.arrived", nav.label()), true);
            return;
        }
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float rel = Mth.wrapDegrees(want - mc.player.getViewYRot(partial));
        g.fill(cx - 60, y - 2, cx + 60, y + 24, 0x70000000);
        g.pose().pushPose();
        g.pose().translate(cx, y + 9, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
        g.pose().scale(1.6f, 1.6f, 1);
        g.drawString(f, "▲", -f.width("▲") / 2, -4, 0xFF7CE0FF, false);
        g.pose().popPose();
        String label = f.plainSubstrByWidth(nav.label(), 80);
        g.drawString(f, label, cx + 12, y + 1, 0xFFFFFFFF, false);
        g.drawString(f, Math.round(dist) + " m", cx + 12, y + 12, 0xFFB0C0D8, false);
    }
}
