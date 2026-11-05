package net.minecraftforge.client.gui.overlay;

import net.minecraft.client.gui.GuiGraphics;

@FunctionalInterface
public interface IGuiOverlay {
    void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight);
}
