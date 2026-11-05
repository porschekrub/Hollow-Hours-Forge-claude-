package net.minecraftforge.client.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.eventbus.api.Event;

/** One vanilla HUD part about to be drawn (Pre cancelled: it is not). */
public class RenderGuiOverlayEvent extends Event {
    private final GuiGraphics g;
    private final float partialTick;
    private final NamedGuiOverlay overlay;

    public RenderGuiOverlayEvent(GuiGraphics g, float partialTick, NamedGuiOverlay overlay) {
        this.g = g;
        this.partialTick = partialTick;
        this.overlay = overlay;
    }

    public GuiGraphics getGuiGraphics() {
        return g;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public NamedGuiOverlay getOverlay() {
        return overlay;
    }

    public static class Pre extends RenderGuiOverlayEvent {
        public Pre(GuiGraphics g, float partialTick, NamedGuiOverlay overlay) {
            super(g, partialTick, overlay);
        }
    }

    public static class Post extends RenderGuiOverlayEvent {
        public Post(GuiGraphics g, float partialTick, NamedGuiOverlay overlay) {
            super(g, partialTick, overlay);
        }
    }
}
