package net.minecraftforge.client.gui.overlay;

import net.minecraft.resources.ResourceLocation;

public enum VanillaGuiOverlay {
    VIGNETTE("vignette"), SPYGLASS("spyglass"), HELMET("helmet"), FROSTBITE("frostbite"), PORTAL("portal"), HOTBAR("hotbar"),
    CROSSHAIR("crosshair"), BOSS_EVENT_PROGRESS("boss_event_progress"), PLAYER_HEALTH("player_health"), ARMOR_LEVEL("armor_level"),
    FOOD_LEVEL("food_level"), AIR_LEVEL("air_level"), MOUNT_HEALTH("mount_health"), JUMP_BAR("jump_bar"), EXPERIENCE_BAR("experience_bar"),
    ITEM_NAME("item_name"), SLEEP_FADE("sleep_fade"), POTION_ICONS("potion_icons"), DEBUG_TEXT("debug_text"), FPS_GRAPH("fps_graph"),
    RECORD_OVERLAY("record_overlay"), TITLE_TEXT("title_text"), SUBTITLES("subtitles"), SCOREBOARD("scoreboard"), CHAT_PANEL("chat_panel"),
    PLAYER_LIST("player_list");

    private final ResourceLocation id;

    VanillaGuiOverlay(String path) {
        //#if MC>=12100
        //$$ this.id = ResourceLocation.withDefaultNamespace(path);
        //#else
        this.id = new ResourceLocation("minecraft", path);
        //#endif
    }

    public ResourceLocation id() {
        return id;
    }

    public NamedGuiOverlay type() {
        return new NamedGuiOverlay(id);
    }
}
