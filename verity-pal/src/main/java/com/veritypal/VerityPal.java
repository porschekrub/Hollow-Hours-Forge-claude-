package com.veritypal;

import com.veritypal.config.PalClientConfig;
import com.veritypal.config.PalConfig;
import com.veritypal.friends.HelperFriends;
import com.veritypal.mind.Memory;
import com.veritypal.mind.Mood;
import com.veritypal.talk.Lang;
import com.veritypal.world.Guide;
import com.veritypal.world.Hide;
import com.veritypal.world.HouseBuilder;
import com.veritypal.world.Life;
import com.veritypal.world.Watchers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Verity Pal: an add-on for Verity JE. It changes nothing in Verity JE's files; it hooks into the game while it runs:
 * her horror story is off until it is turned on, and she gets Thai, a mood, a memory of you in every world and many
 * new things she can do (see the tools in PalTools). Settings: Mods > Verity Pal > Config.
 */
@Mod(VerityPal.MODID)
public class VerityPal {
    public static final String MODID = "veritypal";
    public static final Logger LOG = LoggerFactory.getLogger("VerityPal");

    @SuppressWarnings("removal")
    public VerityPal() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PalConfig.SPEC, "veritypal-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PalClientConfig.SPEC, "veritypal-client.toml");
        modBus.addListener(this::setup);
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener(this::serverTick);
        bus.addListener(this::started);
        bus.addListener(EventPriority.LOWEST, this::stopping);
        Life.register(bus);
        Hide.register(bus);
        Lang.register(bus);
        Memory.register(bus);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.veritypal.client.ClientSetup.init(modBus));
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            V.check();
            com.veritypal.story.StoryGate.init();
        });
    }

    private void started(ServerStartedEvent event) {
        Memory.load();
        Mood.reset();
        if (SmokeTest.ON) SmokeTest.start(event.getServer());
    }

    private void stopping(ServerStoppingEvent event) {
        Guide.stopAll();
        Hide.comeOutAll(event.getServer(), false);
        HouseBuilder.finishAll();
        Memory.save();
        LATER.clear();
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        // things asked for "in a moment" (never inside a tick of the world itself)
        if (!LATER.isEmpty()) {
            List<Later> due = new ArrayList<>();
            for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
                Later l = it.next();
                if (--l.ticks <= 0) {
                    due.add(l);
                    it.remove();
                }
            }
            for (Later l : due) {
                try {
                    l.run.run();
                } catch (Exception e) {
                    LOG.warn("[Verity Pal] a scheduled step failed", e);
                }
            }
        }
        try {
            Guide.tick(server);
            Hide.tick(server);
            HouseBuilder.tick(server);
            Life.tick(server);
            Watchers.tick(server);
            Mood.tick(server);
            HelperFriends.tick(server);
            Memory.tick();
            if (SmokeTest.ON) SmokeTest.tick(server);
        } catch (Exception e) {
            if (WARNED++ < 20) LOG.warn("[Verity Pal] something went wrong this tick", e);
        }
    }

    static int WARNED;

    static final class Later {
        int ticks;
        final Runnable run;

        Later(int ticks, Runnable run) {
            this.ticks = ticks;
            this.run = run;
        }
    }

    static final List<Later> LATER = new ArrayList<>();

    /** Runs on the server thread after that many ticks. */
    public static void later(int ticks, Runnable run) {
        LATER.add(new Later(ticks, run));
    }

    public static ResourceLocation rl(String id) {
        return new ResourceLocation(id);
    }

    public static ResourceLocation rl(String ns, String path) {
        return new ResourceLocation(ns, path);
    }
}
