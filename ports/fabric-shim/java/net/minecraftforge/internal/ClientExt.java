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

    /**
     * A post shader program of a mod, like Forge reads it: "shaders/program/mymod:haunt.json" is
     * mymod:shaders/program/haunt.json (vanilla only knows minecraft's). null: no mod name in it.
     */
    public static ResourceLocation programLocation(String path) {
        String dir = "shaders/program/";
        int c = path.indexOf(':');
        if (c < 0 || !path.startsWith(dir)) return null;
        String ns = path.substring(dir.length(), c), rest = dir + path.substring(c + 1);
        //#if MC>=12100
        //$$ return ResourceLocation.fromNamespaceAndPath(ns, rest);
        //#else
        return new ResourceLocation(ns, rest);
        //#endif
    }

    public static Slot hoveredSlot(AbstractContainerScreen<?> s) {
        return ((net.minecraftforge.internal.mixin.client.AbstractContainerScreenAccess) s).shim$hoveredSlot();
    }
}
