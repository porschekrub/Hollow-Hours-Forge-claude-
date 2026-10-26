package com.hollowhours.entity;

import com.hollowhours.config.HHConfig;
import com.hollowhours.ghost.GhostBrain;
import com.hollowhours.ghost.KillTracks;
import com.hollowhours.registry.HHEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * One of the five ghosts. All behaviour lives in {@link GhostBrain} (server side); this class holds the synced
 * state the client needs to animate it, its size (standing / crawling) and how it holds a victim during a kill.
 */
public class GhostEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RAGE = SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CRAWL = SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.BOOLEAN);
    /** index into KillTracks for grab / scream / kill animations (-1 = none) */
    private static final EntityDataAccessor<Integer> TRACK = SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.INT);
    /** bumps every time an animation must restart from the beginning */
    private static final EntityDataAccessor<Integer> SEQ = SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.INT);

    public final GhostKind kind;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private GhostBrain brain;
    /** tickCount when the current track started (each side keeps its own clock) */
    public int trackStart;
    public int lastSeq = -1;
    /** visual twitch seed for the client (random offsets in the model) */
    public final float seed = (float) Math.random() * 1000f;

    public GhostEntity(EntityType<? extends GhostEntity> type, Level level) {
        super(type, level);
        this.kind = HHEntities.kindOf(type);
        this.xpReward = 0;
        this.setMaxUpStep(1.1f);
        this.setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(false);
            nav.setCanPassDoors(true);
            nav.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ANIM, GhostAnim.SPAWN.ordinal());
        this.entityData.define(RAGE, false);
        this.entityData.define(CRAWL, false);
        this.entityData.define(TRACK, -1);
        this.entityData.define(SEQ, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation nav = new GroundPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanPassDoors(true);
        nav.setCanFloat(true);
        return nav;
    }

    // ------------------------------------------------------------------ synced state
    public GhostAnim anim() {
        return GhostAnim.of(entityData.get(ANIM));
    }

    public void setAnim(GhostAnim a) {
        if (anim() != a) entityData.set(ANIM, a.ordinal());
    }

    /** Start (or restart) a one-shot animation. */
    public void playAnim(GhostAnim a) {
        entityData.set(ANIM, a.ordinal());
        entityData.set(SEQ, entityData.get(SEQ) + 1);
    }

    public int seq() {
        return entityData.get(SEQ);
    }

    public boolean isRaged() {
        return entityData.get(RAGE);
    }

    public void setRaged(boolean r) {
        entityData.set(RAGE, r);
    }

    public boolean isCrawling() {
        return entityData.get(CRAWL);
    }

    public void setCrawling(boolean c) {
        if (isCrawling() != c) {
            entityData.set(CRAWL, c);
            refreshDimensions();
        }
    }

    public int trackIndex() {
        return entityData.get(TRACK);
    }

    @Nullable
    public KillTracks.Track track() {
        return KillTracks.get(trackIndex());
    }

    public void setTrack(int index) {
        entityData.set(TRACK, index);
        trackStart = tickCount;
        entityData.set(SEQ, entityData.get(SEQ) + 1);
    }

    /** Seconds since the current track started (smooth on the client). */
    public float trackTime(float partial) {
        return (tickCount - trackStart + partial) / 20f;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (CRAWL.equals(key)) refreshDimensions();
        if (TRACK.equals(key)) trackStart = tickCount;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (kind == null) return super.getDimensions(pose);
        return isCrawling() ? EntityDimensions.scalable(GhostKind.CRAWL_W, GhostKind.CRAWL_H) : EntityDimensions.scalable(kind.width, kind.height);
    }

    public float standHeight() {
        return kind.height;
    }

    /** Eye position for line of sight (low when crawling). */
    public Vec3 eye() {
        double h = isCrawling() || anim().low() ? 0.9 : Math.min(kind.height * 0.8, 3.4);
        return new Vec3(getX(), getY() + h, getZ());
    }

    // ------------------------------------------------------------------ brain
    public GhostBrain brain() {
        if (brain == null) brain = new GhostBrain(this);
        return brain;
    }

    @Nullable
    public GhostBrain brainIfPresent() {
        return brain;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        brain().tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isVehicle()) {
            // the victim is held: keep the body still and facing them
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        }
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) com.hollowhours.ghost.Director.LIVE.add(this);
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!level().isClientSide) {
            com.hollowhours.ghost.Director.LIVE.remove(this);
            com.hollowhours.ghost.KillSequence.onGhostRemoved(this);
        }
    }

    // ------------------------------------------------------------------ cannot be hurt, pushed, saved or despawned
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!level().isClientSide && source.getEntity() instanceof Player p) brain().onHitBy(p);
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity other) {}

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // ghosts never survive a world reload (the director brings new ones)
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // footsteps are played by the brain (scarier, pitched down)
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public boolean onClimbable() {
        return brain != null && brain.climbing;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return brain().meleeHit(target);
    }

    public float attackDamage() {
        float d = (float) (double) HHConfig.DAMAGE.get();
        if (isRaged()) d *= (float) (double) HHConfig.RAGE_DAMAGE.get();
        return d;
    }

    // ------------------------------------------------------------------ holding a victim (kill cinematic)
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    public boolean canRiderInteract() {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        KillTracks.Track tr = track();
        if (tr == null) {
            move.accept(passenger, getX(), getY() + 1.0, getZ());
            return;
        }
        Vec3 off = tr.victimAt(trackTime(0));
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);   // forward
        double lx = fz, lz = -fx;                         // left
        double x = getX() + fx * off.x + lx * off.z;
        double z = getZ() + fz * off.x + lz * off.z;
        double y = getY() + off.y;
        move.accept(passenger, x, y, z);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 1.0;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(getX() - Mth.sin(yaw) * 1.6, getY(), getZ() + Mth.cos(yaw) * 1.6);
    }

    // ------------------------------------------------------------------ GeckoLib
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ghost.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.ghost.walk");
    private static final RawAnimation SPAWN = RawAnimation.begin().thenPlay("animation.ghost.spawn").thenLoop("animation.ghost.idle");
    private static final RawAnimation SNAP = RawAnimation.begin().thenPlayAndHold("animation.ghost.snap");
    private static final RawAnimation CRAWL_ANIM = RawAnimation.begin().thenLoop("animation.ghost.crawl");
    private static final RawAnimation STUCK = RawAnimation.begin().thenLoop("animation.ghost.stuck");
    private static final RawAnimation BASH = RawAnimation.begin().thenLoop("animation.ghost.bash");
    private static final RawAnimation KNOCK = RawAnimation.begin().thenLoop("animation.ghost.knock");
    private static final RawAnimation SEARCH = RawAnimation.begin().thenLoop("animation.ghost.search");
    private static final RawAnimation INSPECT = RawAnimation.begin().thenLoop("animation.ghost.inspect");
    private static final RawAnimation PEEK = RawAnimation.begin().thenPlayAndHold("animation.ghost.peek");
    private static final RawAnimation CLIMB = RawAnimation.begin().thenLoop("animation.ghost.climb");
    private static final RawAnimation FAREWELL = RawAnimation.begin().thenPlayAndHold("animation.ghost.farewell");
    private static final RawAnimation YANK = RawAnimation.begin().thenPlayAndHold("animation.ghost.yank");
    private static final RawAnimation LUNGE = RawAnimation.begin().thenPlayAndHold("animation.ghost.lunge");
    private static final RawAnimation LAUGH = RawAnimation.begin().thenLoop("animation.ghost.laugh");
    private RawAnimation run, runT, trackAnim;
    private int trackAnimFor = -2;

    private RawAnimation runAnim() {
        if (run == null) run = RawAnimation.begin().thenLoop("animation.ghost.run_g" + kind.n);
        return run;
    }

    private RawAnimation runTAnim() {
        if (runT == null) runT = RawAnimation.begin().thenLoop("animation.ghost.run_g" + kind.n + "_t");
        return runT;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::predicate));
    }

    private PlayState predicate(AnimationState<GhostEntity> state) {
        if (seq() != lastSeq) {
            lastSeq = seq();
            state.resetCurrentAnimation();
        }
        GhostAnim a = anim();
        boolean moving = state.isMoving() || getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
        RawAnimation pick = switch (a) {
            case SPAWN -> SPAWN;
            case IDLE, STALK -> moving ? WALK : IDLE;
            case SNAP -> SNAP;
            case CHASE -> runAnim();
            case FOLLOW_RUN -> runTAnim();
            case CRAWL_CHASE, SQUEEZE, CRAWL_SEARCH, FOLLOW_CRAWL -> CRAWL_ANIM;
            case BASH -> BASH;
            case STUCK -> STUCK;
            case KNOCK -> KNOCK;
            case SEARCH -> moving ? SEARCH : INSPECT;
            case INSPECT -> INSPECT;
            case PEEK -> PEEK;
            case CLIMB -> CLIMB;
            case FAREWELL -> FAREWELL;
            case YANK -> YANK;
            case LUNGE -> LUNGE;
            case LAUGH -> LAUGH;
            case GRAB, HOLD, SCREAM, KILL -> trackAnimation();
        };
        if (pick == null) pick = IDLE;
        return state.setAndContinue(pick);
    }

    private RawAnimation trackAnimation() {
        int idx = trackIndex();
        if (idx != trackAnimFor || trackAnim == null) {
            trackAnimFor = idx;
            KillTracks.Track tr = KillTracks.get(idx);
            trackAnim = tr == null ? IDLE : (tr.name.equals("hold") ? RawAnimation.begin().thenLoop("animation.ghost.hold")
                    : RawAnimation.begin().thenPlayAndHold("animation.ghost." + tr.name));
        }
        return trackAnim;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
