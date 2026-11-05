package com.hollowhours.entity;

import com.hollowhours.ghost.Hiding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Invisible seat under a bed. The rider's eyes end up just above the floor, under the mattress.
 * The seat removes itself as soon as nobody is hiding on it.
 */
public class HideSeatEntity extends Entity {
    /** The rider stands exactly on the seat, so its eyes are 1.62 above it: placing the seat EYE_DROP below a point puts the eyes there. */
    public static final double EYE_DROP = 1.62;
    static final float YAW_LOCK = 70f;

    public HideSeatEntity(EntityType<? extends HideSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    @Override
    //#if MC>=12100
    //$$ protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {}
    //#else
    protected void defineSynchedData() {}
    //#endif

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 5) {
            Entity r = getFirstPassenger();
            if (!(r instanceof ServerPlayer p) || !Hiding.isHidden(p)) discard();
        }
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty() && e instanceof Player;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    //#if MC<12100
    @Override
    //#endif
    public double getPassengersRidingOffset() {
        return 0;
    }

    @Override
    protected void positionRider(Entity rider, MoveFunction move) {
        if (hasPassenger(rider)) move.accept(rider, getX(), getY(), getZ());
    }

    @Override
    public void onPassengerTurned(Entity rider) {
        float d = Mth.wrapDegrees(rider.getYRot() - getYRot());
        float c = Mth.clamp(d, -YAW_LOCK, YAW_LOCK);
        rider.yRotO += c - d;
        rider.setYRot(rider.getYRot() + c - d);
        rider.setYHeadRot(rider.getYRot());
        rider.setXRot(Mth.clamp(rider.getXRot(), -25f, 35f));
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    //#if MC<12100
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    //#endif
}
