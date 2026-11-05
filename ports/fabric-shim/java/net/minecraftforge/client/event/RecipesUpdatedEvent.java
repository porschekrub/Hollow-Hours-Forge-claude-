package net.minecraftforge.client.event;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.eventbus.api.Event;

public class RecipesUpdatedEvent extends Event {
    private final RecipeManager manager;

    public RecipesUpdatedEvent(RecipeManager manager) {
        this.manager = manager;
    }

    public RecipeManager getRecipeManager() {
        return manager;
    }
}
