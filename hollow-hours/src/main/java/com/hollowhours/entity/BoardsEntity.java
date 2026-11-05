package com.hollowhours.entity;

import com.hollowhours.ghost.Breach;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * Boards nailed over a door (up to 4, each wood/stone/iron) or across a window (one cross per glass block).
 * Ghosts have to smash the boards one by one, outermost first, before they can touch the door/glass behind.
 * This entity is the record itself (saved with the chunk) and also what you see.
 */
public class BoardsEntity extends Entity implements GeoEntity {
    public static final int KIND_DOOR = 0, KIND_GLASS = 1;
    public static final int[] HP = {3, 5, 8};
    public static final int[] CLS = {1, 2, 3};
    public static final String[] MAT = {"wood", "stone", "iron"};

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BoardsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AXIS = SynchedEntityData.defineId(BoardsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIDE = SynchedEntityData.defineId(BoardsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COUNT = SynchedEntityData.defineId(BoardsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MATS = SynchedEntityData.defineId(BoardsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DMG = SynchedEntityData.defineId(BoardsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FX = SynchedEntityData.defineId(BoardsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> TARGET = SynchedEntityData.defineId(BoardsEntity.class, EntityDataSerializers.BLOCK_POS);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private double h;

    public BoardsEntity(EntityType<? extends BoardsEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    //#if MC>=12100
    //$$ protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {
    //#else
    protected void defineSynchedData() {
    //#endif
        entityData.define(KIND, KIND_DOOR);
        entityData.define(AXIS, 0);
        entityData.define(SIDE, 0);
        entityData.define(COUNT, 0);
        entityData.define(MATS, 0);
        entityData.define(DMG, 0);
        entityData.define(FX, 0);
        entityData.define(TARGET, BlockPos.ZERO);
    }

    // ------------------------------------------------------------------ accessors (also used by the renderer)
    public int kind() { return entityData.get(KIND); }
    public int axis() { return entityData.get(AXIS); }
    public int side() { return entityData.get(SIDE); }
    public int count() { return entityData.get(COUNT); }
    public int dmgStage() { return entityData.get(DMG); }
    public int fx() { return entityData.get(FX); }
    public BlockPos target() { return entityData.get(TARGET); }

    public int mat(int i) {
        return (entityData.get(MATS) >> (i * 2)) & 3;
    }

    void setMat(int i, int m) {
        int v = entityData.get(MATS) & ~(3 << (i * 2));
        entityData.set(MATS, v | ((m & 3) << (i * 2)));
    }

    public void setFx(int v) {
        entityData.set(FX, v);
    }

    // ------------------------------------------------------------------ lookup / creation
    @Nullable
    public static BoardsEntity at(Level level, BlockPos pos) {
        for (BoardsEntity e : level.getEntitiesOfClass(BoardsEntity.class, new AABB(pos).inflate(1.5))) {
            if (e.isAlive() && e.target().equals(pos)) return e;
        }
        return null;
    }

    public static BoardsEntity create(ServerLevel level, BlockPos target, int kind, int axis, int side, Vec3 at) {
        BoardsEntity e = HHEntities.BOARDS.get().create(level);
        if (e == null) throw new IllegalStateException("boards entity");
        e.entityData.set(TARGET, target.immutable());
        e.entityData.set(KIND, kind);
        e.entityData.set(AXIS, axis);
        e.entityData.set(SIDE, side);
        e.moveTo(at.x, at.y, at.z, 0, 0);
        level.addFreshEntity(e);
        return e;
    }

    public boolean addBoard(int m) {
        int n = count();
        if (n >= 4) return false;
        setMat(n, m);
        entityData.set(COUNT, n + 1);
        h = 0;
        updateStage();
        return true;
    }

    /** Takes the outermost board off; returns its material or -1. */
    public int removeOuter() {
        int n = count();
        if (n <= 0) return -1;
        int m = mat(n - 1);
        setMat(n - 1, 0);
        entityData.set(COUNT, n - 1);
        h = 0;
        updateStage();
        if (n - 1 <= 0) discard();
        return m;
    }

    public Vec3 outerLoc() {
        return new Vec3(getX(), getY() + (kind() == KIND_DOOR ? (7 * Math.max(1, count()) - 1) / 16.0 : 0.5), getZ());
    }

    void updateStage() {
        int n = count();
        int st = 0;
        if (n > 0 && h > 0) st = (int) Math.max(1, Math.min(3, Math.ceil(3 * h / HP[mat(n - 1)])));
        entityData.set(DMG, st);
    }

    public int damageLevel() {
        return dmgStage();
    }

    // ------------------------------------------------------------------ ghosts
    public int[] hitsToClear(int power, boolean rage) {
        int hits = 0, strong = 0;
        double dmg = Breach.POWER_DMG[Math.max(1, Math.min(3, power))] * (rage ? 1.5 : 1);
        int n = count();
        for (int i = 0; i < n; i++) {
            int m = mat(i);
            double hp = HP[m];
            if (i == n - 1) hp = Math.max(0.5, hp - h);
            hits += (int) Math.ceil(hp / dmg - 1e-6);
            if (CLS[m] > power) strong = 1;
        }
        return new int[]{hits, strong};
    }

    static ParticleOptions chips(int m) {
        return new BlockParticleOption(ParticleTypes.BLOCK, (m == 0 ? Blocks.SPRUCE_PLANKS : m == 1 ? Blocks.COBBLESTONE : Blocks.IRON_BLOCK).defaultBlockState());
    }

    /** One blow on the outermost board. */
    public Breach.Res hit(int power, boolean rage, Vec3 dir) {
        ServerLevel level = (ServerLevel) level();
        int n = count();
        if (n <= 0) return Breach.Res.CRACK;
        int m = mat(n - 1);
        double hp = HP[m];
        boolean canBreak = CLS[m] <= power;
        double dmg = Breach.POWER_DMG[Math.max(1, Math.min(3, power))] * (rage ? 1.5 : 1);
        double before = h;
        double nh = before + dmg;
        if (!canBreak) nh = Math.min(before + dmg * 1.6, hp * 0.74);
        Vec3 c = outerLoc();
        if (canBreak && nh >= hp - 1e-6) {
            Fx.sound(level, m == 2 ? "ghost.bang.metal" : "ghost.board.snap", c, 2.2f, R.pitch(0.85, 1.05), 56);
            level.sendParticles(chips(m), c.x, c.y, c.z, 26, 0.35, 0.3, 0.35, 0.15);
            if (m == 2) level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.4);
            level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.02);
            Fx.shakeNear(level, c, 14, 0.5f, 8);
            removeOuter();
            return Breach.Res.CRACK;
        }
        h = nh;
        updateStage();
        Fx.sound(level, m == 2 ? "ghost.bang.metal" : m == 1 ? "ghost.bang.stone" : "ghost.bang.wood", c, 2.3f, R.pitch(0.8, 1.0), 56);
        level.sendParticles(chips(m), c.x, c.y, c.z, 10, 0.3, 0.25, 0.3, 0.1);
        Fx.shakeNear(level, c, 14, power >= 3 || rage ? 0.5f : 0.3f, 6);
        return !canBreak && before >= hp * 0.74 - 1e-6 ? Breach.Res.BLOCKED : Breach.Res.CRACK;
    }

    /** A repair kit of the right material fixes the outer board. */
    public boolean repairWith(String kitFam) {
        int n = count();
        if (n <= 0 || h <= 0) return false;
        if (!MAT[mat(n - 1)].equals(kitFam)) return false;
        h = 0;
        updateStage();
        return true;
    }

    /** Falls apart (door opened by redstone, block gone). */
    public void burst(boolean fx) {
        if (fx && level() instanceof ServerLevel level) {
            Vec3 c = new Vec3(getX(), getY() + (kind() == KIND_DOOR ? 1 : 0.5), getZ());
            level.sendParticles(chips(mat(Math.max(0, count() - 1))), c.x, c.y, c.z, 30, 0.4, 0.6, 0.4, 0.15);
            Fx.sound(level, "ghost.board.snap", c, 1.6f, R.pitch(0.9, 1.1), 40);
        }
        discard();
    }

    // ------------------------------------------------------------------ upkeep
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 20 != 7) return;
        if (count() <= 0) {
            discard();
            return;
        }
        BlockPos target = target();
        BlockState s = level().getBlockState(target);
        Breach.Info info = Breach.classify(s);
        boolean ok = info != null && (kind() == KIND_DOOR ? info.kind().equals("door") && Breach.basePos(s, target).equals(target) : info.fam().equals("glass"));
        if (!ok) {
            discard();
            return;
        }
        if (kind() == KIND_DOOR && Breach.isOpen(s)) burst(true);
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
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        //#if MC>=12100
        //$$ entityData.set(TARGET, NbtUtils.readBlockPos(tag, "target").orElse(BlockPos.ZERO));
        //#else
        entityData.set(TARGET, NbtUtils.readBlockPos(tag.getCompound("target")));
        //#endif
        entityData.set(KIND, tag.getInt("kind"));
        entityData.set(AXIS, tag.getInt("axis"));
        entityData.set(SIDE, tag.getInt("side"));
        entityData.set(COUNT, tag.getInt("n"));
        entityData.set(MATS, tag.getInt("mats"));
        h = tag.getDouble("h");
        updateStage();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("target", NbtUtils.writeBlockPos(target()));
        tag.putInt("kind", kind());
        tag.putInt("axis", axis());
        tag.putInt("side", side());
        tag.putInt("n", count());
        tag.putInt("mats", entityData.get(MATS));
        tag.putDouble("h", h);
    }

    //#if MC<12100
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    //#endif

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64 * 64;
    }

    // ------------------------------------------------------------------ animation
    static final RawAnimation NAIL = RawAnimation.begin().thenPlay("animation.ghost_boards.nail");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
        reg.add(new AnimationController<>(this, "nail", 0, state -> {
            if (fx() == 1) return state.setAndContinue(NAIL);
            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
