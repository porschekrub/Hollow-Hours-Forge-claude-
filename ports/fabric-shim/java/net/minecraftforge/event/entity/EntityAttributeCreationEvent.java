package net.minecraftforge.event.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.eventbus.api.Event;

/** Default attributes of the mod's entities (straight into Fabric's registry). */
public class EntityAttributeCreationEvent extends Event {
    public void put(EntityType<? extends LivingEntity> entity, AttributeSupplier map) {
        FabricDefaultAttributeRegistry.register(entity, map);
    }
}
