package net.minecraftforge.client.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.eventbus.api.Event;

/** The whole HUD (Pre: before anything of it is drawn). */
public class RenderGuiEvent extends Event {
    private final GuiGraphics g;
    private final float partialTick;

    public RenderGuiEvent(GuiGraphics g, float partialTick) {
        this.g = g;
        this.partialTick = partialTick;
    }

    public GuiGraphics getGuiGraphics() {
        return g;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public com.mojang.blaze3d.platform.Window getWindow() {
        return net.minecraft.client.Minecraft.getInstance().getWindow();
    }

    public static class Pre extends RenderGuiEvent {
        public Pre(GuiGraphics g, float partialTick) {
            super(g, partialTick);
        }
    }

    public static class Post extends RenderGuiEvent {
        public Post(GuiGraphics g, float partialTick) {
            super(g, partialTick);
        }
    }
}
