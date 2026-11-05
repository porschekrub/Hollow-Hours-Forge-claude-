package net.minecraftforge.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.eventbus.api.Event;

public class ClientPlayerNetworkEvent extends Event {
    public LocalPlayer getPlayer() {
        return Minecraft.getInstance().player;
    }

    public static class LoggingIn extends ClientPlayerNetworkEvent {}

    public static class LoggingOut extends ClientPlayerNetworkEvent {}
}
