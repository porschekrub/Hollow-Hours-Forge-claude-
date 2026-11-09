package net.minecraftforge.fml;

import net.fabricmc.loader.api.FabricLoader;

public final class ModList {
    private static final ModList INSTANCE = new ModList();

    private ModList() {}

    public static ModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public java.util.List<Info> getMods() {
        java.util.List<Info> out = new java.util.ArrayList<>();
        for (net.fabricmc.loader.api.ModContainer c : FabricLoader.getInstance().getAllMods()) out.add(new Info(c));
        return out;
    }

    public java.util.Optional<Container> getModContainerById(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(Container::new);
    }

    public record Container(net.fabricmc.loader.api.ModContainer fabric) {
        public Info getModInfo() {
            return new Info(fabric);
        }

        public String getModId() {
            return fabric.getMetadata().getId();
        }
    }

    public record Info(net.fabricmc.loader.api.ModContainer fabric) {
        public String getDisplayName() {
            return fabric.getMetadata().getName();
        }

        public String getModId() {
            return fabric.getMetadata().getId();
        }
    }
}
