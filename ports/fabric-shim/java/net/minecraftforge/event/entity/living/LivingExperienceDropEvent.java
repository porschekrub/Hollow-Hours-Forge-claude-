package net.minecraftforge.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class LivingExperienceDropEvent extends LivingEvent {
    private final Player attackingPlayer;
    private final int originalExperience;
    private int droppedExperience;

    public LivingExperienceDropEvent(LivingEntity entity, Player attackingPlayer, int originalExperience) {
        super(entity);
        this.attackingPlayer = attackingPlayer;
        this.originalExperience = this.droppedExperience = originalExperience;
    }

    public int getDroppedExperience() {
        return droppedExperience;
    }

    public void setDroppedExperience(int droppedExperience) {
        this.droppedExperience = droppedExperience;
    }

    public Player getAttackingPlayer() {
        return attackingPlayer;
    }

    public int getOriginalExperience() {
        return originalExperience;
    }
}
