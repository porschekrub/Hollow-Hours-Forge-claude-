package net.minecraftforge.client.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;

public class ModelEvent extends Event {
    /** Extra models to load (the shim hands them to Fabric's model loading plugin). */
    public static class RegisterAdditional extends ModelEvent {
        public final List<ResourceLocation> models = new ArrayList<>();

        public void register(ResourceLocation model) {
            models.add(model);
        }
    }
}
