package com.helperbuddy.client.recipes;

import com.helperbuddy.HelperBuddy;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** The key that opens the recipe book (K by default, changeable in Controls). */
@Mod.EventBusSubscriber(modid = HelperBuddy.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RecipeKeys {
    private RecipeKeys() {}

    public static final KeyMapping OPEN = new KeyMapping("key.helperbuddy.recipes", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.helperbuddy");

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(OPEN);
    }
}
