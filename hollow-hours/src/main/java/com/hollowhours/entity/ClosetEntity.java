package com.hollowhours.entity;

import com.hollowhours.compat.Compat;
import com.hollowhours.ghost.Hiding;
import com.hollowhours.registry.HHItems;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A wardrobe you can hide in (right-click). Hit it with nobody inside to pick it back up.
 * Door: 0 shut, 1 open, 2 yanked open by a ghost, 3 rattling.
 */
public class ClosetEntity extends Entity implements GeoEntity {
    public static final int DOOR_SHUT = 0, DOOR_OPEN = 1, DOOR_YANK = 2, DOOR_RATTLE = 3;
    private static final EntityDataAccessor<Integer> DOOR = SynchedEntityData.defineId(ClosetEntity.class, EntityDataSerializers.INT);
    static final float SEAT_BACK = 0.18f, YAW_LOCK = 55f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int pulseId;

    public ClosetEntity(EntityType<? extends ClosetEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    //#if MC>=12100
    //$$ protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {
    //#else
    protected void defineSynchedData() {
    //#endif
        entityData.define(DOOR, DOOR_SHUT);
    }

    public int door() {
        return entityData.get(DOOR);
    }

    public void setDoor(int v) {
        pulseId++;
        entityData.set(DOOR, v);
    }

    /** Opens/sets the door for a moment, then shuts it again. */
    public void pulseDoor(int v, int ticks) {
        setDoor(v);
        int id = pulseId;
        Later.run(ticks, () -> {
            if (isAlive() && pulseId == id) entityData.set(DOOR, DOOR_SHUT);
        });
    }

    public Vec3 front() {
        return Vec3.directionFromRotation(0, getYRot());
    }

    // ------------------------------------------------------------------ physics
    @Override
    public void tick() {
        super.tick();
        if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0, v.y, 0);
        move(MoverType.SELF, getDeltaMovement());
        if (onGround()) setDeltaMovement(Vec3.ZERO);
        else setDeltaMovement(getDeltaMovement().multiply(0.98, 0.98, 0.98));
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return other != getFirstPassenger() && (other.canBeCollidedWith() || other.isPushable()) && !isPassengerOfSameVehicle(other);
    }

    // ------------------------------------------------------------------ hiding
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (p.isSecondaryUseActive()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (p instanceof ServerPlayer sp && !isVehicle()) Hiding.enterCloset(sp, this);
        return InteractionResult.CONSUME;
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
        return 0.05;
    }

    @Override
    protected void positionRider(Entity rider, MoveFunction move) {
        if (!hasPassenger(rider)) return;
        Vec3 f = front();
        move.accept(rider, getX() - f.x * SEAT_BACK, getY() + getPassengersRidingOffset() + Compat.ridingOffset(rider), getZ() - f.z * SEAT_BACK);
    }

    @Override
    public void onPassengerTurned(Entity rider) {
        clampRotation(rider);
    }

    protected void clampRotation(Entity rider) {
        rider.setYBodyRot(getYRot());
        float d = Mth.wrapDegrees(rider.getYRot() - getYRot());
        float c = Mth.clamp(d, -YAW_LOCK, YAW_LOCK);
        rider.yRotO += c - d;
        rider.setYRot(rider.getYRot() + c - d);
        rider.setYHeadRot(rider.getYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        Vec3 f = front();
        return position().add(f.x * 1.0, 0, f.z * 1.0);
    }

    // ------------------------------------------------------------------ picking it up
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            discard();
            return true;
        }
        if (!(src.getEntity() instanceof Player p) || isVehicle()) return false;
        ServerLevel level = (ServerLevel) level();
        if (Hiding.occupantOf(level, "c:" + getUUID()) != null) return false;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_PLANKS.defaultBlockState()), getX(), getY() + 1, getZ(), 24, 0.35, 0.6, 0.35, 0.1);
        Fx.sound(level, "dig.wood", position(), 1f, 0.8f, 16);
        if (!p.getAbilities().instabuild) spawnAtLocation(new ItemStack(HHItems.CLOSET.get()), 0.5f);
        discard();
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(HHItems.CLOSET.get());
    }

    // ------------------------------------------------------------------ save
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

    // ------------------------------------------------------------------ animation
    static final RawAnimation SHUT = RawAnimation.begin().thenPlayAndHold("animation.ghost_closet.shut");
    static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("animation.ghost_closet.open");
    static final RawAnimation YANK = RawAnimation.begin().thenPlayAndHold("animation.ghost_closet.yank");
    static final RawAnimation RATTLE = RawAnimation.begin().thenLoop("animation.ghost_closet.rattle");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(new AnimationController<>(this, "doors", 6, state -> {
            RawAnimation a = switch (door()) {
                case DOOR_OPEN -> OPEN;
                case DOOR_YANK -> YANK;
                case DOOR_RATTLE -> RATTLE;
                default -> SHUT;
            };
            return state.setAndContinue(a);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
