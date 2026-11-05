package net.minecraftforge.api.distmarker;

/** Fabric shim of Forge's Dist. */
public enum Dist {
    CLIENT, DEDICATED_SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isDedicatedServer() {
        return this == DEDICATED_SERVER;
    }
}
