package net.minecraftforge.internal;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

/** Forge-only client methods the mods call, done on Fabric. */
public final class ClientExt {
    private ClientExt() {}

    /** An extra model (registered with ModelEvent.RegisterAdditional). */
    public static BakedModel model(ModelManager mm, ResourceLocation id) {
        return ((net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager) mm).getModel(id);
    }

    public static BakedModel model(ModelManager mm, net.minecraft.client.resources.model.ModelResourceLocation id) {
        return mm.getModel(id);
    }

    static net.minecraft.world.level.Level level() {
        return net.minecraft.client.Minecraft.getInstance().level;
    }

    public static Slot hoveredSlot(AbstractContainerScreen<?> s) {
        return ((net.minecraftforge.internal.mixin.client.AbstractContainerScreenAccess) s).shim$hoveredSlot();
    }
}
