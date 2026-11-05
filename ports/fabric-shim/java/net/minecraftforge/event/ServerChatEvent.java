package net.minecraftforge.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** A chat message from a player, before it is sent to everyone (cancelled: it is not sent). */
public class ServerChatEvent extends Event {
    private final ServerPlayer player;
    private final String rawText;
    private Component message;

    public ServerChatEvent(ServerPlayer player, String rawText, Component message) {
        this.player = player;
        this.rawText = rawText;
        this.message = message;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getUsername() {
        return player.getGameProfile().getName();
    }

    public String getRawText() {
        return rawText;
    }

    public Component getMessage() {
        return message;
    }

    public void setMessage(Component message) {
        this.message = message;
    }
}
