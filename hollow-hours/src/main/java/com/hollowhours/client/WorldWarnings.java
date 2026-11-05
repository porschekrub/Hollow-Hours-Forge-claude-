package com.hollowhours.client;

import com.hollowhours.HollowHours;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Minecraft 1.21 calls every world with a dimension from a data pack "experimental" and asks for a backup each time the
 * world is opened (and once more when one is made). The rescue island is such a dimension, so with Hollow Hours every
 * world would ask: these two questions are answered "go on" by themselves. Nothing changes on 1.20.1.
 */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WorldWarnings {
    private WorldWarnings() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        //#if MC>=12100
        //$$ Screen s = Minecraft.getInstance().screen;
        //$$ if (s instanceof net.minecraft.client.gui.screens.BackupConfirmScreen && is(s.getTitle(), "selectWorld.backupQuestion.experimental"))
        //$$     press(s, "selectWorld.backupJoinSkipButton");
        //$$ else if (s instanceof net.minecraft.client.gui.screens.ConfirmScreen && is(s.getTitle(), "selectWorld.warning.experimental.title"))
        //$$     press(s, "gui.yes", "gui.proceed");
        //#endif
    }

    static boolean is(Component c, String key) {
        return c.getContents() instanceof TranslatableContents tc && tc.getKey().equals(key);
    }

    static void press(Screen s, String... keys) {
        for (var c : s.children())
            if (c instanceof Button b)
                for (String k : keys)
                    if (is(b.getMessage(), k)) {
                        HollowHours.LOG.info("[Hollow Hours] skipped Minecraft's \"experimental settings\" question (the rescue island is a data pack dimension)");
                        b.onPress();
                        return;
                    }
    }
}
