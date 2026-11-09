package com.helperbuddy.client;

import com.helperbuddy.HelperBuddy;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Hold to talk to your helpers (B by default, changeable in Controls). */
@Mod.EventBusSubscriber(modid = HelperBuddy.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VoiceKeys {
    private VoiceKeys() {}

    public static final KeyMapping TALK = new KeyMapping("key.helperbuddy.talk", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, "key.categories.helperbuddy");

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(TALK);
    }
}
