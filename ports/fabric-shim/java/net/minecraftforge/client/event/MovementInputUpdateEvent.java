package net.minecraftforge.client.event;

import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

public class MovementInputUpdateEvent extends PlayerEvent {
    private final Input input;

    public MovementInputUpdateEvent(Player player, Input input) {
        super(player);
        this.input = input;
    }

    public Input getInput() {
        return input;
    }
}
