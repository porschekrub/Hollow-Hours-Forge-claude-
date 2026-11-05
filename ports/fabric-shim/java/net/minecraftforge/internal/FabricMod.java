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
        make.get();
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
