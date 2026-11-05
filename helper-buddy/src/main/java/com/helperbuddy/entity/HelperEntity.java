package com.helperbuddy.entity;

import com.helperbuddy.compat.Compat;
import com.helperbuddy.net.Menus;
import com.helperbuddy.HelperBuddy;
import com.helperbuddy.brain.Bag;
import com.helperbuddy.brain.Brain;
import com.helperbuddy.brain.Care;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.net.HBNet;
import com.helperbuddy.talk.Say;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Helper Buddy: a small worker that follows its owner, mines, chops, gathers, farms, cooks, crafts, fights and builds.
 * It never dies: at 0 health it is knocked out until it is fed back to 10%.
 */
public class HelperEntity extends PathfinderMob implements GeoEntity {
    // ---- synced
    private static final EntityDataAccessor<String> MODE = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> STATUS = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> HNAME = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> KO = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SITTING = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WORKING = SynchedEntityData.defineId(HelperEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int MAX_LEVEL = 4;
    public static final int[] LEVEL_XP = {0, 150, 450, 1000};
    public static final int HP_PER_LEVEL = 250;
    public static final String DEFAULT_NAME_TH = "น้องช่าง";
    public static final String DEFAULT_NAME_EN = "Buddy";
    public static final int SKINS = 6;

    @Nullable
    private UUID ownerId;
    private String ownerName = "";
    public final Settings settings = new Settings();
    public final SimpleContainer bag = new SimpleContainer(36);
    private float xp;
    @Nullable
    private BlockPos home;
    /** Saved jobs (build, mine, plan, gather ids ...), owned by the brain modules. */
    public CompoundTag jobs = new CompoundTag();
    @Nullable
    private Brain brain;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HelperEntity(EntityType<? extends HelperEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        //#if MC>=12100
        //$$ java.util.Objects.requireNonNull(getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT)).setBaseValue(1.0);
        //#else
        setMaxUpStep(1.0f);
        //#endif
        setPathfindingMalus(BlockPathTypes.WATER, 4f);
        setPathfindingMalus(BlockPathTypes.LAVA, -1f);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16f);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1f);
        setPathfindingMalus(BlockPathTypes.POWDER_SNOW, -1f);
        setPathfindingMalus(BlockPathTypes.DANGER_POWDER_SNOW, 16f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, HP_PER_LEVEL).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.ARMOR, 4);
    }

    @Override
    //#if MC>=12100
    //$$ protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {
    //$$     super.defineSynchedData(entityData);
    //#else
    protected void defineSynchedData() {
        super.defineSynchedData();
    //#endif
        entityData.define(MODE, Mode.FOLLOW.name());
        entityData.define(STATUS, "");
        entityData.define(HNAME, "");
        entityData.define(KO, false);
        entityData.define(LEVEL, 1);
        entityData.define(SKIN, 0);
        entityData.define(SITTING, false);
        entityData.define(WORKING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation nav = new GroundPathNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        nav.setCanFloat(true);
        return nav;
    }

    // ------------------------------------------------------------------ owner
    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(Player p) {
        ownerId = p.getUUID();
        ownerName = p.getGameProfile().getName();
    }

    public boolean isOwner(Player p) {
        return ownerId != null && ownerId.equals(p.getUUID());
    }

    /** The owner when online (any dimension). */
    @Nullable
    public ServerPlayer owner() {
        if (ownerId == null || level().isClientSide || level().getServer() == null) return null;
        return level().getServer().getPlayerList().getPlayer(ownerId);
    }

    // ------------------------------------------------------------------ name, look, mode
    public String helperName() {
        String n = entityData.get(HNAME);
        if (!n.isEmpty()) return n;
        return Say.thai(owner()) ? DEFAULT_NAME_TH : DEFAULT_NAME_EN;
    }

    public void setHelperName(String n) {
        String s = n.replaceAll("§.", "").trim();
        if (s.length() > 24) s = s.substring(0, 24);
        entityData.set(HNAME, s);
    }

    public String rawName() {
        return entityData.get(HNAME);
    }

    public int skin() {
        return Mth.clamp(entityData.get(SKIN), 0, SKINS - 1);
    }

    public void setSkin(int s) {
        entityData.set(SKIN, Math.floorMod(s, SKINS));
    }

    public Mode mode() {
        return Mode.of(entityData.get(MODE));
    }

    public void setMode(Mode m) {
        entityData.set(MODE, m.name());
        entityData.set(SITTING, false);
    }

    public String status() {
        return entityData.get(STATUS);
    }

    public void setStatus(String s) {
        if (!s.equals(entityData.get(STATUS))) entityData.set(STATUS, s);
    }

    public boolean isSitting() {
        return entityData.get(SITTING);
    }

    public void setSitting(boolean s) {
        entityData.set(SITTING, s);
    }

    public boolean isWorking() {
        return entityData.get(WORKING);
    }

    public void setWorking(boolean w) {
        if (w != entityData.get(WORKING)) entityData.set(WORKING, w);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    public void setHome(@Nullable BlockPos p) {
        home = p == null ? null : p.immutable();
    }

    public Brain brain() {
        if (brain == null) brain = new Brain(this);
        return brain;
    }

    // ------------------------------------------------------------------ level, health, knock-out
    public int helperLevel() {
        return entityData.get(LEVEL);
    }

    public float xp() {
        return xp;
    }

    public static int levelFromXp(float xp) {
        int l = 1;
        for (int i = 1; i < LEVEL_XP.length; i++) if (xp >= LEVEL_XP[i]) l = i + 1;
        return Math.min(MAX_LEVEL, l);
    }

    public int maxHp() {
        return helperLevel() * HP_PER_LEVEL;
    }

    public boolean isKO() {
        return entityData.get(KO);
    }

    void applyLevelStats() {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getBaseValue() != maxHp()) hp.setBaseValue(maxHp());
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue((3 + helperLevel()) * HBConfig.DAMAGE_MULT.get());
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(2 + helperLevel() * 2);
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }

    /** Experience from work and fights; a new level heals completely. */
    public void addXp(float amount) {
        if (amount <= 0 || level().isClientSide) return;
        int before = helperLevel();
        xp = Math.min(LEVEL_XP[LEVEL_XP.length - 1], xp + amount);
        int after = levelFromXp(xp);
        if (after <= before) return;
        entityData.set(LEVEL, after);
        applyLevelStats();
        if (!isKO()) setHealth(getMaxHealth());
        level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, 1.1f);
        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1, getZ(), 12, 0.6, 0.8, 0.6, 0);
        Player o = owner();
        Say.owner(this, Component.literal(Say.t(o, "§6§lเลเวลอัป!§r ตอนนี้ผม §bเลเวล " + after + "§r แล้ว เลือดสูงสุด §a" + maxHp(),
                "§6§lLevel up!§r I'm §blevel " + after + "§r now, max health §a" + maxHp())));
    }

    /** Health as shown to the player (0 while knocked out). */
    public float shownHp() {
        return isKO() ? 0 : getHealth();
    }

    public void knockOut() {
        entityData.set(KO, true);
        setHealth(1f);
        getNavigation().stop();
        setTarget(null);
        brain().onKnockOut();
        level().playSound(null, blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.NEUTRAL, 1f, 0.7f);
        ServerPlayer o = owner();
        Say.owner(this, Component.literal(Say.t(o,
                "§c§lผมสลบแล้ว...§r เอาอาหารอะไรก็ได้มาให้กินหน่อย (ยกเว้นเนื้อเน่า) ต้องได้เลือดอย่างน้อย 10% (" + (int) Math.ceil(maxHp() * 0.1) + ") ถึงจะลุกได้\n§7(ถืออาหารแล้วคลิกขวาที่ตัวผม หรือส่งผ่านหนังสือสั่งงาน)",
                "§c§lI'm knocked out...§r feed me anything (but rotten flesh) until I have 10% health (" + (int) Math.ceil(maxHp() * 0.1) + ") to get up\n§7(hold food and right-click me, or use the command book)")));
    }

    public void wake() {
        entityData.set(KO, false);
        level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 1.4f);
        Say.owner(this, "§aผมฟื้นแล้ว! พร้อมลุยต่อครับ", "§aI'm back on my feet! Ready to go");
    }

    /** Heals (food, potions...). Wakes up from a knock-out at 10%. */
    public void healHp(float amount) {
        float to = Math.min(getMaxHealth(), (isKO() ? 0 : getHealth()) + amount);
        if (isKO()) {
            koHp = to;
            if (koHp >= maxHp() * 0.1f) {
                setHealth(Math.max(1f, koHp));
                koHp = 0;
                wake();
            }
            return;
        }
        setHealth(to);
    }

    /** Health gathered while knocked out (the real health stays at 1 so the entity cannot die). */
    public float koHp;

    @Override
    public void die(DamageSource src) {
        if (level().isClientSide) return;
        // only /kill and the void (after a failed rescue) really remove it
        if (src.is(DamageTypes.GENERIC_KILL)) {
            super.die(src);
            return;
        }
        if (!isKO()) knockOut();
        else setHealth(1f);
        this.dead = false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource src) {
        if (src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_DROWNING) || src.is(DamageTypes.IN_WALL)
                || src.is(DamageTypes.CRAMMING) || src.is(DamageTypes.SWEET_BERRY_BUSH) || src.is(DamageTypes.CACTUS) || src.is(DamageTypes.FREEZE))
            return true;
        if (src.getEntity() instanceof HelperEntity) return true;
        if (src.getEntity() instanceof Player p && (isOwner(p) || p.isCreative() && !p.isShiftKeyDown())) return true;
        return super.isInvulnerableTo(src);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return false;
        if (src.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            rescueFromVoid();
            return false;
        }
        if (isKO() && !src.is(DamageTypes.GENERIC_KILL)) return false;
        if (src.getEntity() instanceof Player p && isOwner(p)) {
            // the owner tapping it: a little hop, no damage
            if (onGround()) setDeltaMovement(getDeltaMovement().add(0, 0.25, 0));
            return false;
        }
        boolean r = super.hurt(src, amount);
        if (r && !isKO()) brain().combat().onHurt(src);
        return r;
    }

    void rescueFromVoid() {
        ServerPlayer o = owner();
        if (o != null) brain().mover().bringTo(o);
        else if (level() instanceof ServerLevel sl) {
            BlockPos s = sl.getSharedSpawnPos();
            teleportTo(s.getX() + 0.5, sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, s.getX(), s.getZ()) + 1, s.getZ() + 0.5);
        }
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    //#if MC>=12100 && !FABRIC
    //$$ @Override
    //$$ public boolean canDrownInFluidType(net.minecraftforge.fluids.FluidType type) {
    //$$     return false;
    //$$ }
    //#elif MC>=12100
    //$$ // Fabric 1.21: the entity type is in the tag minecraft:can_breathe_under_water (ports/fabric-common)
    //#else
    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }
    //#endif

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    //#if MC>=12100
    //$$ public boolean canBeLeashed() {
    //#else
    public boolean canBeLeashed(Player p) {
    //#endif
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public float getVoicePitch() {
        return 1.35f;
    }

    // ------------------------------------------------------------------ tick
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        try {
            brain().tick();
        } catch (Exception e) {
            if (tickCount % 200 == 0) HelperBuddy.LOG.warn("[Helper Buddy] tick error", e);
        }
    }

    @Override
    public void aiStep() {
        if (isKO()) {
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            if (!level().isClientSide && tickCount % 40 == 0 && level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 0.6, getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
        super.aiStep();
        updateSwingTime();
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isKO();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0) applyLevelStats();
    }

    // ------------------------------------------------------------------ interaction
    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        ItemStack held = p.getItemInHand(hand);
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) p;
        if (ownerId == null) {
            setOwner(p);
            Say.to(p, this, "สวัสดีครับ! ตั้งแต่นี้ผมเป็นผู้ช่วยของคุณแล้ว", "Hi! I'm your helper from now on");
        }
        if (!isOwner(p)) {
            Say.to(p, this, "ผมเป็นผู้ช่วยของ " + ownerName + " นะครับ", "I'm " + ownerName + "'s helper");
            return InteractionResult.CONSUME;
        }
        if (held.is(Items.NAME_TAG) && Compat.hasCustomName(held)) {
            setHelperName(held.getHoverName().getString());
            if (!p.getAbilities().instabuild) held.shrink(1);
            Say.to(p, this, "จากนี้ไปเรียกผมว่า §e" + helperName() + "§r นะครับ", "From now on call me §e" + helperName());
            return InteractionResult.CONSUME;
        }
        if (Care.foodValue(held) != 0 && (isKO() || getHealth() < getMaxHealth() - 0.5f || Care.foodValue(held) < 0)) {
            Care.feed(sp, this, held);
            return InteractionResult.CONSUME;
        }
        if (p.isShiftKeyDown()) {
            openBag(sp);
            return InteractionResult.CONSUME;
        }
        Menus.openMenu(sp, this);
        return InteractionResult.CONSUME;
    }

    public void openBag(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x4, id, inv, bag, 4),
                Component.literal(helperName()).append(Component.literal(Say.t(p, " - กระเป๋า", " - bag")).withStyle(ChatFormatting.GRAY))));
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !isKO();
    }

    @Override
    //#if MC>=12100
    //$$ protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource src, boolean hit) {
    //#else
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
    //#endif
        Bag.dropAll(this);
    }

    // ------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        if (ownerId != null) t.putUUID("Owner", ownerId);
        t.putString("OwnerName", ownerName);
        t.putString("HName", rawName());
        t.putString("Mode", mode().name());
        t.put("Settings", settings.save());
        t.putFloat("Xp", xp);
        t.putBoolean("KO", isKO());
        t.putFloat("KoHp", koHp);
        t.putInt("Skin", skin());
        t.putBoolean("Sitting", isSitting());
        if (home != null) t.putLong("Home", home.asLong());
        //#if MC>=12100
        //$$ t.put("Bag", bag.createTag(registryAccess()));
        //#else
        t.put("Bag", bag.createTag());
        //#endif
        if (brain != null) brain.save(jobs);
        t.put("Jobs", jobs.copy());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        if (t.hasUUID("Owner")) ownerId = t.getUUID("Owner");
        ownerName = t.getString("OwnerName");
        setHelperName(t.getString("HName"));
        entityData.set(MODE, Mode.of(t.getString("Mode")).name());
        settings.load(t.getCompound("Settings"));
        xp = t.getFloat("Xp");
        entityData.set(LEVEL, levelFromXp(xp));
        entityData.set(KO, t.getBoolean("KO"));
        koHp = t.getFloat("KoHp");
        entityData.set(SKIN, t.getInt("Skin"));
        entityData.set(SITTING, t.getBoolean("Sitting"));
        home = t.contains("Home") ? BlockPos.of(t.getLong("Home")) : null;
        //#if MC>=12100
        //$$ bag.fromTag(t.getList("Bag", 10), registryAccess());
        //#else
        bag.fromTag(t.getList("Bag", 10));
        //#endif
        jobs = t.getCompound("Jobs").copy();
        brain = null;
        applyLevelStats();
    }

    // ------------------------------------------------------------------ GeckoLib
    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.helper.idle");
    static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.helper.walk");
    static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.helper.run");
    static final RawAnimation KO_ANIM = RawAnimation.begin().thenLoop("animation.helper.ko");
    static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.helper.sit");
    static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.helper.swim");
    static final RawAnimation WORK = RawAnimation.begin().thenLoop("animation.helper.work");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(new AnimationController<>(this, "body", 4, s -> {
            if (isKO()) return s.setAndContinue(KO_ANIM);
            if (isSitting()) return s.setAndContinue(SIT);
            if (isInWater()) return s.setAndContinue(SWIM);
            double sp = s.getLimbSwingAmount();
            if (sp > 0.55) return s.setAndContinue(RUN);
            if (sp > 0.05) return s.setAndContinue(WALK);
            return s.setAndContinue(IDLE);
        }));
        reg.add(new AnimationController<>(this, "arms", 2, s -> {
            if (isKO()) return PlayState.STOP;
            if (isWorking()) return s.setAndContinue(WORK);
            return PlayState.STOP;
        }));
        reg.add(new AnimationController<>(this, "action", 1, s -> PlayState.STOP)
                .triggerableAnim("swing", RawAnimation.begin().thenPlay("animation.helper.swing"))
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("animation.helper.attack"))
                .triggerableAnim("place", RawAnimation.begin().thenPlay("animation.helper.place"))
                .triggerableAnim("eat", RawAnimation.begin().thenPlay("animation.helper.eat"))
                .triggerableAnim("wave", RawAnimation.begin().thenPlay("animation.helper.wave"))
                .triggerableAnim("cheer", RawAnimation.begin().thenPlay("animation.helper.cheer"))
                .triggerableAnim("shoot", RawAnimation.begin().thenPlay("animation.helper.shoot")));
    }

    public void act(String anim) {
        if (!level().isClientSide) triggerAnim("action", anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ misc
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean r = super.doHurtTarget(target);
        act("attack");
        swing(InteractionHand.MAIN_HAND);
        return r;
    }

    @Override
    protected void dropEquipment() {
        // keeps its tools: nothing is dropped
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    public ItemStack tool() {
        return getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public void setTool(ItemStack st) {
        setItemSlot(EquipmentSlot.MAINHAND, st);
        setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof HelperEntity h && ownerId != null && ownerId.equals(h.ownerId)) return true;
        if (other instanceof Player p && isOwner(p)) return true;
        return super.isAlliedTo(other);
    }

    public static void initNew(HelperEntity h, @Nullable Player owner) {
        if (owner != null) h.setOwner(owner);
        h.setSkin(h.getRandom().nextInt(SKINS));
        h.applyLevelStats();
        h.setHealth(h.getMaxHealth());
        h.setMode(Mode.FOLLOW);
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Override
    public Component getName() {
        return Component.literal(helperName());
    }

    //#if MC<12100
    @Override
    public double getMyRidingOffset() {
        return 0.1;
    }
    //#endif

    /** Owner + a list tag helper for the bag (used by saves of old versions). */
    public static ListTag emptyList() {
        return new ListTag();
    }
}
