package net.minecraftforge.internal;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.function.Supplier;

/** Starts a Forge-style mod on Fabric: the mod's constructor, its event subscribers, then the setup events. */
public final class FabricMod {
    private FabricMod() {}

    public static IEventBus modBus() {
        return FMLJavaModLoadingContext.get().getModEventBus();
    }

    /** From the mod's ModInitializer. */
    public static void common(String modid, Supplier<Object> make) {
        ModLoadingContext.MOD_ID = modid;
        Hooks.init();
        // Forge Config API Port's config events -> the mod bus. It loads common / client configs as soon as they are
        // registered (in the mod's constructor): those events wait until the constructor is done, as on Forge.
        java.util.List<net.minecraftforge.eventbus.api.Event> early = new java.util.ArrayList<>();
        boolean[] constructing = {true};
        java.util.function.Consumer<net.minecraftforge.eventbus.api.Event> configEvent = e -> {
            if (constructing[0]) early.add(e);
            else modBus().post(e);
        };
        //#if MC>=12100
        //$$ fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeModConfigEvents.loading(modid).register(c -> configEvent.accept(new net.minecraftforge.fml.event.config.ModConfigEvent.Loading(c)));
        //$$ fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeModConfigEvents.reloading(modid).register(c -> configEvent.accept(new net.minecraftforge.fml.event.config.ModConfigEvent.Reloading(c)));
        //#else
        fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents.loading(modid).register(c -> configEvent.accept(new net.minecraftforge.fml.event.config.ModConfigEvent.Loading(c)));
        fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents.reloading(modid).register(c -> configEvent.accept(new net.minecraftforge.fml.event.config.ModConfigEvent.Reloading(c)));
        //#endif
        make.get();
        constructing[0] = false;
        for (var e : early) modBus().post(e);
        SpawnData.register();
        subscribers(false);
        modBus().post(new EntityAttributeCreationEvent());
        modBus().post(new FMLCommonSetupEvent());
    }

    static void subscribers(boolean client) {
        for (String s : Subscribers.ALL) {
            String[] p = s.split(":", 3);
            if (p[1].equals("CLIENT") != client) continue;
            try {
                Class<?> c = Class.forName(p[2]);
                (p[0].equals("MOD") ? modBus() : MinecraftForge.EVENT_BUS).register(c);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("event subscriber missing: " + p[2], e);
            }
        }
    }
}
