package com.hollowhours.fun;

import com.hollowhours.compat.Compat;
import com.hollowhours.registry.HHBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/**
 * The seat of a swing, hanging from the top beam on two ropes. Sit on it and it swings: a pendulum that goes higher
 * and higher (up to about 55 degrees) while you ride it. Swinging calms you down.
 */
public class SwingSeatEntity extends Entity implements IEntityAdditionalSpawnData {
    /** rope length (blocks) and where the beam is above the swing block's floor */
    public static final double ROPE = 1.45, BEAM = 1.85;
    /** one full swing every ~2.6 s, like a real swing this size */
    static final float OMEGA = Mth.TWO_PI / 52f;
    static final float MAX_AMP = 0.95f;

    private static final EntityDataAccessor<Float> AMP = SynchedEntityData.defineId(SwingSeatEntity.class, EntityDataSerializers.FLOAT);

    BlockPos swing = BlockPos.ZERO;
    Direction facing = Direction.NORTH;
    int rideTicks;

    public SwingSeatEntity(EntityType<? extends SwingSeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    void setup(BlockPos pos, Direction facing) {
        this.swing = pos.immutable();
        this.facing = facing;
        moveTo(pos.getX() + 0.5, pos.getY() + BEAM, pos.getZ() + 0.5, facing.toYRot(), 0);
    }

    public Direction facing() {
        return facing;
    }

    public float amp() {
        return entityData.get(AMP);
    }

    /** The swing angle (radians, + = forward toward `facing`). */
    public float angle(float partial) {
        return amp() * Mth.sin((tickCount + partial) * OMEGA);
    }

    /** Where the seat is now. */
    public Vec3 seat(float partial) {
        float a = angle(partial);
        return new Vec3(getX() + facing.getStepX() * Mth.sin(a) * ROPE, getY() - Mth.cos(a) * ROPE, getZ() + facing.getStepZ() * Mth.sin(a) * ROPE);
    }

    @Override
    //#if MC>=12100
    //$$ protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {
    //#else
    protected void defineSynchedData() {
    //#endif
        entityData.define(AMP, 0.05f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Entity r = getFirstPassenger();
        boolean standing = level().getBlockState(swing).is(HHBlocks.SWING.get());
        if (tickCount > 5 && (r == null || !standing)) {
            if (r != null) r.stopRiding();
            discard();
            return;
        }
        if (r == null) return;
        rideTicks++;
        // pumping your legs: a bit higher every swing
        entityData.set(AMP, Math.min(MAX_AMP, amp() + 0.004f));
        if (rideTicks % 20 == 0 && amp() > 0.3f && r instanceof Player p) FunBlocks.reward(p, "swing", 0.8f, 18);
    }

    @Override
    public void remove(RemovalReason why) {
        super.remove(why);
        if (level().isClientSide || !why.shouldDestroy()) return;
        BlockState s = level().getBlockState(swing);
        if (s.is(HHBlocks.SWING.get())) level().setBlock(swing, s.setValue(FunBlocks.OCCUPIED, false), 3);
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty() && e instanceof LivingEntity;
    }

    @Override
    protected void positionRider(Entity rider, MoveFunction move) {
        if (!hasPassenger(rider)) return;
        Vec3 s = seat(0);
        // sitting: the hips (0.75 above the feet) on the seat board; a player's riding offset already lowers it 0.35
        move.accept(rider, s.x, s.y - 0.34 + Compat.ridingOffset(rider), s.z);
    }

    @Override
    public void onPassengerTurned(Entity rider) {
        // look forward-ish: you can turn your head but not spin around on a swing
        float d = Mth.wrapDegrees(rider.getYRot() - facing.toYRot());
        float c = Mth.clamp(d, -80, 80);
        rider.yRotO += c - d;
        rider.setYRot(rider.getYRot() + c - d);
        rider.setYHeadRot(rider.getYRot());
    }

    @Override
    protected void addPassenger(Entity e) {
        super.addPassenger(e);
        e.setYRot(facing.toYRot());
        e.setYHeadRot(facing.toYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        return new Vec3(swing.getX() + 0.5 + facing.getStepX() * 1.2, swing.getY(), swing.getZ() + 0.5 + facing.getStepZ() * 1.2);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    //#if MC<12100
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    //#endif

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeBlockPos(swing);
        buf.writeByte(facing.get2DDataValue());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        swing = buf.readBlockPos();
        facing = Direction.from2DDataValue(buf.readByte());
    }
}
