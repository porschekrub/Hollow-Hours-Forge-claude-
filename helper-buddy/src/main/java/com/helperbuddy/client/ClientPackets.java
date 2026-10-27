package com.helperbuddy.client;

import com.helperbuddy.client.screen.BookScreen;
import com.helperbuddy.client.screen.HelperScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public final class ClientPackets {
    private ClientPackets() {}

    public static void open(String screen, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        switch (screen) {
            case "menu" -> {
                String tab = mc.screen instanceof HelperScreen hs && hs.helperId() == data.getInt("id") ? hs.tab() : "main";
                mc.setScreen(new HelperScreen(data, tab));
            }
            case "refresh" -> {
                if (mc.screen instanceof HelperScreen hs && hs.helperId() == data.getInt("id")) hs.update(data);
            }
            case "book" -> mc.setScreen(new BookScreen(data));
            default -> {
            }
        }
    }
}
