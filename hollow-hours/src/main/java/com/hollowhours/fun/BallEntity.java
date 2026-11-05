package com.hollowhours.fun;

import com.hollowhours.registry.HHBlocks;
import com.hollowhours.registry.HHItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A ball with real-ish physics: gravity, air drag, bouncing off the ground and walls (a basketball bounces higher
 * than a football), rolling friction and spin. Throw it (right-click with it), kick it (hit it), dribble it (walk into
 * it), pick it up (right-click it). Through a hoop or into a goal: score.
 */
public class BallEntity extends Entity {
    public static final int BASKETBALL = 0, SOCCER = 1;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BallEntity.class, EntityDataSerializers.INT);

    /** client: rolling angle for the renderer */
    public float roll, rollO, rollYaw;
    @Nullable
    UUID toucher;
    int scoreCooldown;
    @Nullable
    BlockPos inGoal;

    public BallEntity(EntityType<? extends BallEntity> type, Level level) {
        super(type, level);
    }

    public int kind() {
        return entityData.get(KIND);
    }

    public void setKind(int k) {
        entityData.set(KIND, k);
    }

    public ItemStack item() {
        return new ItemStack(kind() == SOCCER ? HHItems.SOCCER_BALL.get() : HHItems.BASKETBALL.get());
    }

    /** how much speed survives a bounce */
    double restitution() {
        return kind() == SOCCER ? 0.55 : 0.78;
    }

    @Override
    //#if MC>=12100
    //$$ protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {
    //#else
    protected void defineSynchedData() {
    //#endif
        entityData.define(KIND, BASKETBALL);
    }

    public void touchedBy(Entity e) {
        if (e instanceof Player) toucher = e.getUUID();
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        double prevY = getY();
        if (!isNoGravity()) v = v.add(0, -0.045, 0);
        if (isInWater()) v = v.multiply(0.8, 0.8, 0.8).add(0, 0.07, 0);
        move(MoverType.SELF, v);
        Vec3 after = getDeltaMovement();
        double vx = v.x, vy = v.y, vz = v.z;
        boolean bounced = false;
        if (horizontalCollision) {
            if (Math.abs(after.x) < 1e-6 && Math.abs(vx) > 1e-4) {
                vx = -vx * restitution();
                bounced = true;
            }
            if (Math.abs(after.z) < 1e-6 && Math.abs(vz) > 1e-4) {
                vz = -vz * restitution();
                bounced = true;
            }
        }
        if (verticalCollision) {
            if (vy < -0.1) {
                vy = -vy * restitution();
                bounced = true;
            } else if (vy < 0) vy = 0;
            else if (vy > 0) vy = -vy * 0.3;
        }
        if (onGround()) {
            // rolling: a football rolls a long way, a basketball less
            double f = kind() == SOCCER ? 0.965 : 0.93;
            vx *= f;
            vz *= f;
        } else {
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
        }
        if (Math.abs(vx) < 0.003) vx = 0;
        if (Math.abs(vz) < 0.003) vz = 0;
        setDeltaMovement(vx, vy, vz);
        if (bounced && (Math.abs(v.y) > 0.15 || Math.abs(vx) + Math.abs(vz) > 0.2)) {
            float vol = (float) Math.min(1, v.length());
            playSound(kind() == SOCCER ? SoundEvents.WOOL_HIT : SoundEvents.BASALT_HIT, vol, kind() == SOCCER ? 0.8f : 1.5f);
        }
        if (level().isClientSide) {
            // spin with the distance it rolls
            rollO = roll;
            double d = Math.sqrt(vx * vx + vz * vz);
            if (d > 0.002) {
                roll += (float) (d / 0.25);
                rollYaw = (float) Mth.atan2(vz, vx);
            }
            return;
        }
        if (scoreCooldown > 0) scoreCooldown--;
        checkScore(prevY);
        if (getY() < level().getMinBuildHeight() - 32) discard();
    }

    /** Through a hoop's rim going down, or into a goal. */
    void checkScore(double prevY) {
        ServerLevel level = (ServerLevel) level();
        BlockPos at = blockPosition();
        if (kind() == BASKETBALL) {
            if (getDeltaMovement().y >= 0 || scoreCooldown > 0) return;
            for (BlockPos b : BlockPos.betweenClosed(at.offset(-1, -2, -1), at.offset(1, 0, 1))) {
                BlockState s = level.getBlockState(b);
                if (!s.is(HHBlocks.BASKETBALL_HOOP.get())) continue;
                Direction f = s.getValue(FunBlock.FACING);
                Vec3 rim = new Vec3(b.getX() + 0.5 + f.getStepX() * 0.25, b.getY() + 1.5, b.getZ() + 0.5 + f.getStepZ() * 0.25);
                double cy = getY() + 0.25, py = prevY + 0.25;
                double dx = getX() - rim.x, dz = getZ() - rim.z;
                if (py >= rim.y && cy < rim.y && dx * dx + dz * dz < 0.3 * 0.3) {
                    scoreCooldown = 20;
                    // the net slows it down
                    setDeltaMovement(getDeltaMovement().multiply(0.3, 0.5, 0.3));
                    FunBlocks.score(level, rim, scorer(), true);
                    return;
                }
            }
        } else {
            BlockState s = level.getBlockState(at);
            if (s.is(HHBlocks.SOCCER_GOAL.get())) {
                if (!at.equals(inGoal) && scoreCooldown <= 0) {
                    inGoal = at.immutable();
                    scoreCooldown = 40;
                    FunBlocks.score(level, position().add(0, 0.5, 0), scorer(), false);
                }
            } else inGoal = null;
        }
    }

    @Nullable
    LivingEntity scorer() {
        return toucher == null ? null : level().getPlayerByUUID(toucher);
    }

    /** Hit it: a kick (or a punch) in the direction you look. */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (isInvulnerableTo(src)) return false;
        Entity by = src.getEntity();
        if (by == null) return false;
        if (!level().isClientSide) {
            Vec3 look = Vec3.directionFromRotation(Math.min(0, by.getXRot()) - 12, by.getYRot());
            double power = kind() == SOCCER ? 1.0 : 0.7;
            setDeltaMovement(look.scale(power).add(0, 0.12, 0));
            hurtMarked = true;
            touchedBy(by);
            if (by instanceof net.minecraft.server.level.ServerPlayer sp) com.hollowhours.combat.Fitness.addStrength(sp, 0.05f);
            playSound(kind() == SOCCER ? SoundEvents.WOOL_HIT : SoundEvents.BASALT_HIT, 1f, kind() == SOCCER ? 0.6f : 1.2f);
        }
        return true;
    }

    /** Walk into it: you dribble it along. */
    @Override
    public void push(Entity e) {
        if (e instanceof BallEntity || e.isPassengerOfSameVehicle(this)) return;
        Vec3 d = position().subtract(e.position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-4) return;
        Vec3 ev = e.getDeltaMovement().multiply(1, 0, 1);
        double speed = Math.max(0.12, ev.length() * 1.7);
        Vec3 v = d.normalize().scale(speed);
        Vec3 now = getDeltaMovement();
        setDeltaMovement(v.x, Math.max(now.y, onGround() ? 0.06 : now.y), v.z);
        hurtMarked = true;
        touchedBy(e);
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /** Right-click: pick it up. */
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (!level().isClientSide) {
            ItemStack it = item();
            if (!p.getInventory().add(it)) p.drop(it, false);
            playSound(SoundEvents.ITEM_PICKUP, 0.4f, 1.3f);
            discard();
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public ItemStack getPickResult() {
        return item();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        setKind(t.getInt("Kind"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("Kind", kind());
    }

    //#if MC<12100
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    //#endif
}
