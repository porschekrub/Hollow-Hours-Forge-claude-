package com.hollowhours.entity;

import com.hollowhours.registry.HHEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The three apparitions (Lanky, Pale Child, Veil). They have no AI of their own: the stalker director moves them
 * (peeks, follows, lunges, streaks past). They can't be hurt and vanish when you stare at them.
 */
public class StalkerEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Integer> POSE_ID = SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);
    public static final int POSE_IDLE = 0, POSE_LUNGE = 1, POSE_RUN = 2;

    public final StalkerKind kind;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** set by the director; a stalker without a session removes itself */
    public boolean owned;

    public StalkerEntity(EntityType<? extends StalkerEntity> type, Level level) {
        super(type, level);
        this.kind = HHEntities.stalkerKindOf(type);
        this.setNoAi(true);
        this.setNoGravity(kind == StalkerKind.VEIL);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    //#if MC>=12100
    //$$ protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {
    //$$     super.defineSynchedData(entityData);
    //#else
    protected void defineSynchedData() {
        super.defineSynchedData();
    //#endif
        entityData.define(POSE_ID, POSE_IDLE);
    }

    public void setStalkPose(int p) {
        entityData.set(POSE_ID, p);
    }

    public int stalkPose() {
        return entityData.get(POSE_ID);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 40 && !owned) discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity other) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.stalker.idle");
    private static final RawAnimation LUNGE = RawAnimation.begin().thenLoop("animation.stalker.lunge");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.stalker.run");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> state.setAndContinue(switch (stalkPose()) {
            case POSE_LUNGE -> LUNGE;
            case POSE_RUN -> RUN;
            default -> IDLE;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
