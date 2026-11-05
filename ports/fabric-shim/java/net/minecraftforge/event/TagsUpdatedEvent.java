package net.minecraftforge.event;

import net.minecraft.core.RegistryAccess;
import net.minecraftforge.eventbus.api.Event;

public class TagsUpdatedEvent extends Event {
    public enum UpdateCause { SERVER_DATA_LOAD, CLIENT_PACKET_RECEIVED }

    private final RegistryAccess registryAccess;
    private final UpdateCause cause;

    public TagsUpdatedEvent(RegistryAccess registryAccess, boolean client) {
        this.registryAccess = registryAccess;
        this.cause = client ? UpdateCause.CLIENT_PACKET_RECEIVED : UpdateCause.SERVER_DATA_LOAD;
    }

    public RegistryAccess getRegistryAccess() {
        return registryAccess;
    }

    public UpdateCause getUpdateCause() {
        return cause;
    }
}
