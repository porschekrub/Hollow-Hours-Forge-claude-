package com.hollowhours;

import com.hollowhours.command.HHCommands;
import com.hollowhours.config.HHClientConfig;
import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.StalkerEntity;
import com.hollowhours.ghost.GhostEvents;
import com.hollowhours.net.HHNet;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.registry.HHItems;
import com.hollowhours.registry.HHSounds;
import com.hollowhours.registry.HHTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hollow Hours (Java / Forge 1.20.1).
 * Five ghosts hunt the players. They stalk, chase, search hiding spots, smash doors and windows,
 * talk back, hand out cursed items... and when they catch you, they make sure you see it.
 */
@Mod(HollowHours.MODID)
public class HollowHours {
    public static final String MODID = "hollowhours";
    public static final Logger LOG = LoggerFactory.getLogger("HollowHours");

    @SuppressWarnings("removal")
    public HollowHours() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        HHSounds.SOUNDS.register(modBus);
        HHEntities.ENTITIES.register(modBus);
        HHItems.ITEMS.register(modBus);
        HHTabs.TABS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::attributes);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HHConfig.SPEC, "hollowhours-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HHClientConfig.SPEC, "hollowhours-client.toml");

        GhostEvents.register(MinecraftForge.EVENT_BUS);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(HHNet::register);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        AttributeSupplier ghost = GhostEntity.createAttributes().build();
        HHEntities.GHOSTS.forEach(t -> event.put(t.get(), ghost));
        AttributeSupplier stalker = StalkerEntity.createAttributes().build();
        HHEntities.STALKERS.forEach(t -> event.put(t.get(), stalker));
    }

    private void commands(RegisterCommandsEvent event) {
        HHCommands.register(event.getDispatcher());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
