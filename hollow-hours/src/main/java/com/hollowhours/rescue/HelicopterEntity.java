package com.hollowhours.rescue;

import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The rescue helicopter. It flies in from far away and sets down; right-click it to climb aboard (up to six). A pickup
 * waits five minutes, an extraction as long as it takes; once everyone near is aboard it lifts off, and when it's high
 * enough the passengers are flown on (to the haven, or home). Also used empty, flying off after dropping people.
 */
public class HelicopterEntity extends Entity implements IEntityAdditionalSpawnData {
    public static final int PICKUP = 0, EXTRACT = 1, DROP_OFF = 2;
    public static final int ARRIVING = 0, LANDED = 1, LEAVING = 2;
    /** ticks of the approach, of the lift-off, the moment passengers are flown on */
    public static final int FLY_IN = 220, TAKE_OFF = 110, TRANSFER_AT = 95;
    static final int APPROACH = 150;
    /** where the six seats are: right, up, back (blocks from the middle of the door) */
    static final double[][] SEATS = {{-0.85, 1.05, 1.75}, {0, 1.05, 1.75}, {0.85, 1.05, 1.75}, {0, 1.05, 0.44}, {-0.69, 1.05, -1.69}, {0.69, 1.05, -1.69}};

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.BYTE);
    private static final Set<HelicopterEntity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    Vec3 from = Vec3.ZERO, pad = Vec3.ZERO;
    public int stateTicks;
    long expireAt = -1, firstAboard = -1, lastBoard = -1;
    // client: the spinning rotors, smooth movement, the engine sound
    public float rotor, rotorO, tail, tailO;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot, lerpXRot;
    private boolean soundStarted;

    public HelicopterEntity(EntityType<? extends HelicopterEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    // ------------------------------------------------------------------ making one
    /** Flies in from far away and lands on pad. */
    @Nullable
    public static HelicopterEntity arrive(ServerLevel level, Vec3 pad, int kind) {
        HelicopterEntity h = HHEntities.HELICOPTER.get().create(level);
        if (h == null) return null;
        double ang = level.random.nextDouble() * Math.PI * 2;
        h.pad = pad;
        h.from = pad.add(Math.cos(ang) * 75, 48, Math.sin(ang) * 75);
        h.setKind(kind);
        h.setState(ARRIVING);
        h.moveTo(h.from.x, h.from.y, h.from.z, yawTo(h.from, pad), 0);
        level.addFreshEntity(h);
        return h;
    }

    /** Sits on pad and flies away at once (after dropping people off). */
    public static void leaveFrom(ServerLevel level, Vec3 pad) {
        HelicopterEntity h = HHEntities.HELICOPTER.get().create(level);
        if (h == null) return;
        h.pad = pad;
        h.setKind(DROP_OFF);
        h.setState(LEAVING);
        h.moveTo(pad.x, pad.y, pad.z, level.random.nextFloat() * 360, 0);
        level.addFreshEntity(h);
    }

    static float yawTo(Vec3 a, Vec3 b) {
        return (float) (Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG) - 90f;
    }

    // ------------------------------------------------------------------ the ones about
    public static List<HelicopterEntity> live(int kind) {
        List<HelicopterEntity> out = new ArrayList<>();
        for (HelicopterEntity h : LIVE) if (h.isAlive() && h.kind() == kind) out.add(h);
        return out;
    }

    @Nullable
    public static HelicopterEntity nearest(Player p, int kind) {
        HelicopterEntity best = null;
        for (HelicopterEntity h : live(kind)) {
            if (h.level() != p.level() || h.distanceTo(p) > 600) continue;
            if (best == null || h.distanceTo(p) < best.distanceTo(p)) best = h;
        }
        return best;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) LIVE.add(this);
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        LIVE.remove(this);
    }

    // ------------------------------------------------------------------ state
    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, (byte) ARRIVING);
        entityData.define(KIND, (byte) PICKUP);
    }

    public int state() {
        return entityData.get(STATE);
    }

    void setState(int s) {
        entityData.set(STATE, (byte) s);
        stateTicks = 0;
    }

    public int kind() {
        return entityData.get(KIND);
    }

    void setKind(int k) {
        entityData.set(KIND, (byte) k);
    }

    public boolean isLeaving() {
        return state() == LEAVING;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key)) stateTicks = 0;
    }

    /** Ticks until a pickup gives up and flies off. */
    public int ticksLeft() {
        if (kind() != PICKUP) return 0;
        if (state() == ARRIVING) return Rescue.pickupTicks() + FLY_IN - stateTicks;
        if (state() == LANDED) return (int) Math.max(0, expireAt - level().getGameTime());
        return 0;
    }

    // ------------------------------------------------------------------ flying
    @Override
    public void tick() {
        super.tick();
        stateTicks++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        switch (state()) {
            case ARRIVING -> flyIn();
            case LANDED -> waitOnGround();
            default -> flyOff();
        }
    }

    static double ease(double k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    private void flyIn() {
        Vec3 hover = pad.add(0, 14, 0);
        int t = stateTicks;
        Vec3 at;
        float pitch;
        if (t <= APPROACH) {
            double k = ease(t / (double) APPROACH);
            at = from.lerp(hover, k);
            pitch = 14 * Mth.sin((float) (t / (double) APPROACH) * Mth.PI);      // nose down speeding up, up again slowing down
        } else {
            at = hover.lerp(pad, ease((t - APPROACH) / (double) (FLY_IN - APPROACH)));
            pitch = 0;
        }
        setPos(at.x, at.y, at.z);
        setXRot(pitch);
        if (t >= FLY_IN) {
            setPos(pad.x, pad.y, pad.z);
            setState(LANDED);
            if (kind() == PICKUP) expireAt = level().getGameTime() + Rescue.pickupTicks();
        }
    }

    private void waitOnGround() {
        long now = level().getGameTime();
        List<ServerPlayer> aboard = aboard();
        if (!aboard.isEmpty() && firstAboard < 0) firstAboard = now;
        boolean everyone = !aboard.isEmpty() && everyoneAboard() && now - lastBoard >= 60;
        if (kind() == PICKUP) {
            if (now >= expireAt || everyone) setState(LEAVING);
        } else if (kind() == EXTRACT) {
            if (everyone || !aboard.isEmpty() && now - firstAboard >= 45 * 20) setState(LEAVING);
        } else setState(LEAVING);
    }

    private void flyOff() {
        int t = stateTicks;
        double rise = t < 20 ? 0 : Math.min(0.6, (t - 20) * 0.02);
        double fwd = t < 45 ? 0 : Math.min(0.9, (t - 45) * 0.03);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        setPos(getX() - Mth.sin(yaw) * fwd, getY() + rise, getZ() + Mth.cos(yaw) * fwd);
        setXRot(t < 45 ? 0 : Math.min(15, (t - 45) * 0.5f));
        List<ServerPlayer> aboard = aboard();
        // (moved between worlds after the level's tick, not in the middle of it)
        if (t == TRANSFER_AT && !aboard.isEmpty()) {
            if (kind() == PICKUP) com.hollowhours.util.Later.run(1, () -> Rescue.toHaven(aboard));
            else if (kind() == EXTRACT) com.hollowhours.util.Later.run(1, () -> Rescue.home(aboard));
        }
        if (t >= 300) discard();
    }

    List<ServerPlayer> aboard() {
        List<ServerPlayer> out = new ArrayList<>();
        for (Entity e : getPassengers()) if (e instanceof ServerPlayer p) out.add(p);
        return out;
    }

    /** everyone this helicopter came for, near enough to make it, is aboard */
    boolean everyoneAboard() {
        for (Player p : level().players()) {
            if (!(p instanceof ServerPlayer sp) || !sp.isAlive() || sp.isSpectator() || sp.distanceTo(this) > 160 || !mayBoard(sp)) continue;
            if (!(sp.getVehicle() instanceof HelicopterEntity)) return false;
        }
        return true;
    }

    boolean mayBoard(Player p) {
        if (kind() == PICKUP) return Rescue.phase(p) == Rescue.WAITING;
        if (kind() == EXTRACT) return Rescue.phase(p) == Rescue.IN_HAVEN;
        return false;
    }

    // ------------------------------------------------------------------ getting on
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (p.isSecondaryUseActive() || state() != LANDED || p.getVehicle() == this) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!mayBoard(p)) {
            if (p instanceof ServerPlayer sp) Fx.actionbarKey(sp, "hollowhours.rescue.board.wrong");
            return InteractionResult.CONSUME;
        }
        if (getPassengers().size() >= SEATS.length) return InteractionResult.CONSUME;
        return p.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    protected void addPassenger(Entity e) {
        super.addPassenger(e);
        lastBoard = level().getGameTime();
        e.setYRot(getYRot());
        if (!level().isClientSide) level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().size() < SEATS.length && e instanceof Player;
    }

    /** a point of the helicopter: right, up, back in blocks from the middle of the door */
    public Vec3 local(double right, double up, double back) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        return new Vec3(getX() + rx * right - fx * back, getY() + up, getZ() + rz * right - fz * back);
    }

    @Override
    protected void positionRider(Entity rider, MoveFunction move) {
        int i = getPassengers().indexOf(rider);
        if (i < 0) return;
        double[] s = SEATS[i];
        Vec3 v = local(s[0], s[1], s[2]);
        move.accept(rider, v.x, v.y + rider.getMyRidingOffset(), v.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        int i = Math.max(0, getPassengers().indexOf(rider));
        Vec3 v = local(SEATS[i][0] < 0 ? -2.6 : 2.6, 0, 0.5);
        double ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(v.x), Mth.floor(v.z));
        return new Vec3(v.x, Math.max(ground, getY()), v.z);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(7, 2, 7);
    }

    // ------------------------------------------------------------------ client
    private void clientTick() {
        if (lerpSteps > 0) {
            double x = getX() + (lerpX - getX()) / lerpSteps, y = getY() + (lerpY - getY()) / lerpSteps, z = getZ() + (lerpZ - getZ()) / lerpSteps;
            setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
            setXRot(getXRot() + (float) (lerpXRot - getXRot()) / lerpSteps);
            lerpSteps--;
            setPos(x, y, z);
        }
        float speed = state() == LANDED ? 34 : 47;
        rotorO = rotor;
        rotor += speed;
        tailO = tail;
        tail += 72;
        if (!soundStarted) {
            soundStarted = true;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.hollowhours.client.HelicopterSound.start(this));
        }
        // the downwash whips up dust under it
        double ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
        double h = getY() - ground;
        if (h < 9) {
            int n = h < 3 ? 4 : 2;
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 2.5 + random.nextDouble() * 3;
                level().addParticle(ParticleTypes.POOF, getX() + Math.cos(a) * r, ground + 0.2, getZ() + Math.sin(a) * r,
                        Math.cos(a) * 0.25, 0.02, Math.sin(a) * 0.25);
            }
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = 3;
    }

    // ------------------------------------------------------------------ saving / spawning
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeVarInt(stateTicks);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        stateTicks = buf.readVarInt();
    }
}
