package com.helperbuddy;

import com.helperbuddy.command.HBCommands;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.net.HBNet;
import com.helperbuddy.registry.HBRegistry;
import com.helperbuddy.talk.ItemWords;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
 * Helper Buddy (Java / Forge 1.20.1): a helper that mines, chops, collects, farms, cooks, crafts, fights and builds
 * houses (and much more) from plain Thai or English sentences.
 */
@Mod(HelperBuddy.MODID)
public class HelperBuddy {
    public static final String MODID = "helperbuddy";
    public static final Logger LOG = LoggerFactory.getLogger("HelperBuddy");

    @SuppressWarnings("removal")
    public HelperBuddy() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        HBRegistry.ENTITIES.register(modBus);
        HBRegistry.ITEMS.register(modBus);
        HBRegistry.TABS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::attributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HBConfig.SPEC, "helperbuddy-common.toml");
        HBEvents.register(MinecraftForge.EVENT_BUS);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            HBNet.register();
            // the item name dictionary may name any item of the game (mods included)
            ItemWords.EXISTS = id -> {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                return rl != null && BuiltInRegistries.ITEM.containsKey(rl);
            };
        });
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(HBRegistry.HELPER.get(), HelperEntity.createAttributes().build());
    }

    private void commands(RegisterCommandsEvent event) {
        HBCommands.register(event.getDispatcher());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
