package com.hollowhours.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The mod's HUD layers, the same on every loader: each loader's ClientSetup registers them above the vanilla layer named
 * here (null = above everything), and the survival bars ask here where the next free row is.
 */
public final class Hud {
    private Hud() {}

    public interface Draw {
        void render(GuiGraphics g, float partial);
    }

    public enum Anchor { CHAT, FOOD, ARMOR, CROSSHAIR, BOSS, TOP }

    public record Layer(String name, Anchor anchor, Draw draw) {}

    /** In drawing order (as registered). */
    public static final List<Layer> LAYERS = List.of(
            new Layer("horror_fx", Anchor.CHAT, ClientFx::render),
            new Layer("jumpscare", Anchor.TOP, Jumpscare::render),
            new Layer("thirst", Anchor.FOOD, SurvivalHud::thirst),
            new Layer("sanity", Anchor.ARMOR, SurvivalHud::sanity),
            new Layer("phone_alert", Anchor.TOP, PhoneAlert::render),
            new Layer("phone_notice", Anchor.TOP, PhoneNotice::render),
            new Layer("rescue_fade", Anchor.TOP, RescueFade::render),
            new Layer("ending", Anchor.TOP, (g, partial) -> EndingClient.render(g, partial, g.guiWidth(), g.guiHeight())),
            new Layer("combat", Anchor.CROSSHAIR, (g, partial) -> CombatClient.renderHud(g, partial, g.guiWidth(), g.guiHeight())),
            new Layer("phone_nav", Anchor.BOSS, NavOverlay::render));

    /** The vanilla layer to draw above. */
    public static ResourceLocation vanilla(Anchor a) {
        //#if NEOFORGE
        //$$ return switch (a) {
        //$$     case CHAT -> net.neoforged.neoforge.client.gui.VanillaGuiLayers.CHAT;
        //$$     case FOOD -> net.neoforged.neoforge.client.gui.VanillaGuiLayers.FOOD_LEVEL;
        //$$     case ARMOR -> net.neoforged.neoforge.client.gui.VanillaGuiLayers.ARMOR_LEVEL;
        //$$     case CROSSHAIR -> net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR;
        //$$     case BOSS -> net.neoforged.neoforge.client.gui.VanillaGuiLayers.BOSS_OVERLAY;
        //$$     case TOP -> null;
        //$$ };
        //#elif MC>=12100
        //$$ return switch (a) {
        //$$     case CHAT -> net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.CHAT_OVERLAY;
        //$$     case FOOD, ARMOR -> net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.HOTBAR;
        //$$     case CROSSHAIR -> net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.CROSSHAIR;
        //$$     case BOSS -> net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.BOSS_OVERLAY;
        //$$     case TOP -> null;
        //$$ };
        //#else
        return switch (a) {
            case CHAT -> net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CHAT_PANEL.id();
            case FOOD -> net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.FOOD_LEVEL.id();
            case ARMOR -> net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.ARMOR_LEVEL.id();
            case CROSSHAIR -> net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id();
            case BOSS -> net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id();
            case TOP -> null;
        };
        //#endif
    }

    /** Vanilla HUD parts hidden while a ghost holds you (the kill cinematic). */
    public static boolean hiddenInKill(ResourceLocation id) {
        String p = id.getNamespace().equals("minecraft") ? id.getPath() : "";
        return switch (p) {
            // 1.20.1 Forge names, then 1.21 names
            case "hotbar", "crosshair", "player_health", "food_level", "armor_level", "experience_bar", "air_level", "mount_health",
                    "jump_bar", "item_name", "record_overlay",
                    "vehicle_health", "jump_meter", "selected_item_name", "overlay_message", "experience_level" -> true;
            default -> false;
        };
    }

    /** Survival bars are drawn (not creative / spectator). */
    public static boolean survival() {
        var gm = Minecraft.getInstance().gameMode;
        return gm != null && gm.canHurtPlayer();
    }

    // the next free row above the hotbar on the right (food side) and the left (health side)
    //#if MC>=12100 && !NEOFORGE
    //$$ private static int extraRight, extraLeft;
    //$$ private static long frame = -1;
    //$$ private static void sync() {
    //$$     long f = net.minecraft.Util.getMillis() / 16;
    //$$     if (f != frame) { frame = f; extraRight = 0; extraLeft = 0; }
    //$$ }
    //$$ public static int rightHeight() {
    //$$     sync();
    //$$     var p = Minecraft.getInstance().player;
    //$$     int h = 49;
    //$$     if (p != null && (p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) || p.getAirSupply() < p.getMaxAirSupply())) h += 10;
    //$$     return h + extraRight;
    //$$ }
    //$$ public static void addRight(int n) { sync(); extraRight += n; }
    //$$ public static int leftHeight() {
    //$$     sync();
    //$$     var p = Minecraft.getInstance().player;
    //$$     int h = 39;
    //$$     if (p != null) {
    //$$         float hp = p.getMaxHealth() + p.getAbsorptionAmount();
    //$$         int rows = net.minecraft.util.Mth.ceil(hp / 2f / 10f);
    //$$         h += Math.max(10 - (rows - 2), 3) * Math.max(0, rows - 1) + 10;
    //$$         if (p.getArmorValue() > 0) h += 10;
    //$$     }
    //$$     return h + extraLeft;
    //$$ }
    //$$ public static void addLeft(int n) { sync(); extraLeft += n; }
    //#elif MC>=12100
    //$$ public static int rightHeight() { return Minecraft.getInstance().gui.rightHeight; }
    //$$ public static void addRight(int n) { Minecraft.getInstance().gui.rightHeight += n; }
    //$$ public static int leftHeight() { return Minecraft.getInstance().gui.leftHeight; }
    //$$ public static void addLeft(int n) { Minecraft.getInstance().gui.leftHeight += n; }
    //#else
    private static net.minecraftforge.client.gui.overlay.ForgeGui gui() {
        return (net.minecraftforge.client.gui.overlay.ForgeGui) Minecraft.getInstance().gui;
    }

    public static int rightHeight() {
        return gui().rightHeight;
    }

    public static void addRight(int n) {
        gui().rightHeight += n;
    }

    public static int leftHeight() {
        return gui().leftHeight;
    }

    public static void addLeft(int n) {
        gui().leftHeight += n;
    }
    //#endif
}
