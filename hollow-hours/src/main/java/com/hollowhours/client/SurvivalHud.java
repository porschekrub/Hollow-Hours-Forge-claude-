package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Thirst: a row of water drops above the food bar. Sanity: an eye and a bar above the armour, shown once the story
 * has started; it trembles and bleeds red when your mind is going.
 */
public final class SurvivalHud {
    private SurvivalHud() {}

    static final ResourceLocation ICONS = HollowHours.id("textures/gui/survival.png");
    /** from the server; negative = that system is off */
    public static float thirst = -1, sanity = -1;

    public static void thirst(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (thirst < 0 || mc.options.hideGui || !gui.shouldDrawSurvivalElements() || KillCam.active()) return;
        int right = w / 2 + 91, top = h - gui.rightHeight;
        gui.rightHeight += 10;
        int v = Math.round(thirst);
        RenderSystem.enableBlend();
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9, y = top;
            if (thirst < 4 && (ClientFx.ticks + i * 3) % 12 < 2) y += mc.level == null ? 0 : mc.level.random.nextInt(3) - 1;
            g.blit(ICONS, x, y, 18, 0, 9, 9, 64, 32);
            if (i * 2 + 1 < v) g.blit(ICONS, x, y, 0, 0, 9, 9, 64, 32);
            else if (i * 2 + 1 == v) g.blit(ICONS, x, y, 9, 0, 9, 9, 64, 32);
        }
        RenderSystem.disableBlend();
    }

    public static void sanity(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (sanity < 0 || mc.options.hideGui || !gui.shouldDrawSurvivalElements() || KillCam.active()) return;
        if (!Atmos.haunted && sanity >= 99.5f) return;
        int left = w / 2 - 91, top = h - gui.leftHeight;
        gui.leftHeight += 10;
        float k = Mth.clamp(sanity / 100f, 0, 1);
        int jx = 0, jy = 0;
        if (k < 0.35f && mc.level != null) {
            float amt = (0.35f - k) / 0.35f;
            if (mc.level.random.nextFloat() < 0.3f * amt) {
                jx = mc.level.random.nextInt(3) - 1;
                jy = mc.level.random.nextInt(3) - 1;
            }
        }
        RenderSystem.enableBlend();
        // eye: open, half shut, bloodshot
        int u = k > 0.6f ? 0 : k > 0.3f ? 9 : 18;
        g.blit(ICONS, left + jx, top + jy, u, 9, 9, 9, 64, 32);
        int bx = left + 11, bw = 70;
        g.fill(bx - 1 + jx, top + 2 + jy, bx + bw + 1 + jx, top + 7 + jy, 0xAA000000);
        int col = k > 0.6f ? 0xFF8FD8EA : k > 0.35f ? 0xFFB592E6 : k > 0.15f ? 0xFFD0507A : 0xFF9A0F1E;
        g.fill(bx + jx, top + 3 + jy, bx + jx + Math.round(bw * k), top + 6 + jy, col);
        RenderSystem.disableBlend();
    }
}
