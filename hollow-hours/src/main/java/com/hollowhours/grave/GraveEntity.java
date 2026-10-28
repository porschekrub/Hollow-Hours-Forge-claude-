package com.hollowhours.grave;

import com.hollowhours.config.HHConfig;
import com.hollowhours.util.Fx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/** The grave in the world. All it holds is which grave it is (the contents live in {@link Graves}). */
public class GraveEntity extends Entity {
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(GraveEntity.class, EntityDataSerializers.STRING);
    /** seconds until it crumbles */
    private static final EntityDataAccessor<Integer> LEFT = SynchedEntityData.defineId(GraveEntity.class, EntityDataSerializers.INT);

    UUID grave, owner;
    int hits;
    long lastHit;

    public GraveEntity(EntityType<? extends GraveEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    void setup(Graves.Grave g) {
        grave = g.id;
        owner = g.owner;
        entityData.set(OWNER_NAME, g.ownerName);
        entityData.set(LEFT, (int) (Graves.lifetime() / 20));
    }

    public String ownerName() {
        return entityData.get(OWNER_NAME);
    }

    public int secondsLeft() {
        return entityData.get(LEFT);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER_NAME, "");
        entityData.define(LEFT, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(30) == 0)
                level().addParticle(ParticleTypes.SOUL, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 0.4, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            return;
        }
        if (tickCount % 20 != 0) return;
        Graves.Grave g = Graves.get(level().getServer()).active(grave);
        if (g == null) {
            discard(); // opened, broken or crumbled while this chunk wasn't loaded
            return;
        }
        long left = Graves.lifetime() - (Graves.now(level().getServer()) - g.created);
        entityData.set(LEFT, (int) Math.max(0, left / 20));
    }

    boolean isOwner(Player p) {
        return owner != null && owner.equals(p.getUUID());
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(p instanceof ServerPlayer sp)) return InteractionResult.PASS;
        Graves.Grave g = Graves.get(sp.server).active(grave);
        if (g == null) {
            discard();
            return InteractionResult.CONSUME;
        }
        if (!isOwner(sp)) {
            Fx.actionbar(sp, Component.translatable("hollowhours.grave.notyours", g.ownerName));
            return InteractionResult.CONSUME;
        }
        Graves.open(sp, this, g);
        return InteractionResult.CONSUME;
    }

    /** Only the owner's hits count: three in a row and it crumbles. Nothing else can hurt it. */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || !(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p) return false;
        Graves.Grave g = Graves.get(p.server).active(grave);
        if (g == null) {
            discard();
            return false;
        }
        if (!isOwner(p)) {
            Fx.actionbar(p, Component.translatable("hollowhours.grave.notyours", g.ownerName));
            return false;
        }
        long now = level().getGameTime();
        if (now - lastHit > 100) hits = 0;
        lastHit = now;
        hits++;
        int need = HHConfig.GRAVE_HITS.get();
        ((ServerLevel) level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()), getX(), getY() + 0.6, getZ(), 8, 0.25, 0.3, 0.25, 0.05);
        level().playSound(null, blockPosition(), SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1f, 0.7f + hits * 0.1f);
        if (hits >= need) Graves.smash(p, this, g);
        else Fx.actionbar(p, Component.translatable("hollowhours.grave.hit", hits, need));
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
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        if (t.hasUUID("Grave")) grave = t.getUUID("Grave");
        if (t.hasUUID("Owner")) owner = t.getUUID("Owner");
        entityData.set(OWNER_NAME, t.getString("OwnerName"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        if (grave != null) t.putUUID("Grave", grave);
        if (owner != null) t.putUUID("Owner", owner);
        t.putString("OwnerName", ownerName());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
