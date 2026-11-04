package com.hollowhours.client.recipes;

import com.hollowhours.HollowHours;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/** K opens the recipe book; in any inventory, hovering an item and pressing R (recipes) or U (uses) opens it there. */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RecipeEvents {
    private RecipeEvents() {}

    private static Boolean jei;

    /** JEI already uses R / U in inventories: leave those keys to it when it is installed. */
    static boolean jei() {
        if (jei == null) jei = ModList.get().isLoaded("jei");
        return jei;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (RecipeKeys.OPEN.consumeClick())
            if (mc.screen == null && mc.player != null) mc.setScreen(new RecipeScreen(null, ItemStack.EMPTY, false));
    }

    @SubscribeEvent
    public static void key(ScreenEvent.KeyPressed.Pre e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> s) || typing(s)) return;
        int key = e.getKeyCode();
        boolean open = RecipeKeys.OPEN.matches(key, e.getScanCode());
        boolean r = !jei() && key == GLFW.GLFW_KEY_R, u = !jei() && key == GLFW.GLFW_KEY_U;
        if (!open && !r && !u) return;
        Slot slot = s.getSlotUnderMouse();
        ItemStack st = slot != null && slot.hasItem() ? slot.getItem() : ItemStack.EMPTY;
        if (st.isEmpty() && !open) return;
        Minecraft.getInstance().setScreen(new RecipeScreen(s, st, u));
        e.setCanceled(true);
    }

    /** A text box of the screen is being typed in (creative search, anvil name...). */
    static boolean typing(Screen s) {
        if (s.getFocused() instanceof EditBox eb && eb.isFocused()) return true;
        for (Class<?> c = s.getClass(); c != null && c != Screen.class; c = c.getSuperclass())
            for (Field f : c.getDeclaredFields()) {
                if (!EditBox.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    if (f.get(s) instanceof EditBox eb && eb.isVisible() && eb.isFocused()) return true;
                } catch (Throwable ignored) {
                }
            }
        return false;
    }

    @SubscribeEvent
    public static void recipes(RecipesUpdatedEvent e) {
        RecipeIndex.clear();
    }

    @SubscribeEvent
    public static void tags(TagsUpdatedEvent e) {
        RecipeIndex.clear();
    }

    @SubscribeEvent
    public static void out(ClientPlayerNetworkEvent.LoggingOut e) {
        RecipeIndex.clear();
    }
}
