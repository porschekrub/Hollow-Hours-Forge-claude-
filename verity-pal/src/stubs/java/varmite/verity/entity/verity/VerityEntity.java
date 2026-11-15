package varmite.verity.entity.verity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stand-in (see README.md): Verity's body. Only Verity's own methods are listed. */
public class VerityEntity extends PathfinderMob {
    public static EntityDataAccessor<Boolean> IS_TALKING;
    public static List<String> VALID_VARIANTS;
    public volatile boolean clientIsTalking;

    public VerityEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public Optional<UUID> getOwnerUUID() {
        throw new AssertionError();
    }

    public void setOwnerUUID(@Nullable UUID uuid) {}

    public void startTalking(int ticks) {}

    public void stopTalking() {}

    public boolean isTalking() {
        throw new AssertionError();
    }

    public String getVariant() {
        throw new AssertionError();
    }

    public void setVariant(String variant) {}

    private boolean transformIntoDemon(Player player, boolean provoked) {
        throw new AssertionError();
    }
}
