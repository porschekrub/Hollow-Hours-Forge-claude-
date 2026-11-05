package net.minecraftforge.client.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;

/** Fabric shim: the overlays are drawn in order after the vanilla HUD (Fabric's HudRenderCallback). */
public class RegisterGuiOverlaysEvent extends Event {
    public final List<IGuiOverlay> overlays = new ArrayList<>();

    public void registerAboveAll(String id, IGuiOverlay overlay) {
        overlays.add(overlay);
    }

    public void registerAbove(ResourceLocation other, String id, IGuiOverlay overlay) {
        overlays.add(overlay);
    }

    public void registerBelow(ResourceLocation other, String id, IGuiOverlay overlay) {
        overlays.add(overlay);
    }

    public void registerBelowAll(String id, IGuiOverlay overlay) {
        overlays.add(0, overlay);
    }
}
