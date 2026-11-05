package com.hollowhours.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A felled tree, falling. It holds the tree's blocks (relative to the stump), tips over away from the player with
 * gravity (slow at first, then faster), and where it hits the ground the logs, leaves, saplings and apples drop.
 */
public class FallingTreeEntity extends Entity implements IEntityAdditionalSpawnData {
    public static final int FALL_TICKS = 32;

    public final List<BlockState> states = new ArrayList<>();
    public final List<BlockPos> rel = new ArrayList<>();
    /** horizontal direction it falls toward */
    public float dirX = 1, dirZ;
    @Nullable
    UUID by;
    ItemStack tool = ItemStack.EMPTY;
    private boolean landed;

    public FallingTreeEntity(EntityType<? extends FallingTreeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Rotation now (radians), easing like a real fall: θ ~ t². */
    public float angle(float partial) {
        float t = Mth.clamp((tickCount + partial) / FALL_TICKS, 0, 1);
        return Mth.HALF_PI * t * t;
    }

    /** The axis it tips over around (horizontal, through the stump): up × direction, so +angle tips it toward dir. */
    public Vector3f axis() {
        return new Vector3f(dirZ, 0, -dirX).normalize();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!landed && tickCount >= FALL_TICKS) {
            landed = true;
            land((ServerLevel) level());
        }
        if (tickCount > FALL_TICKS + 3 || states.isEmpty()) discard();
    }

    /** Where a block of the tree ends up lying. */
    BlockPos landing(BlockPos r, float ang) {
        Quaternionf q = new Quaternionf().rotationAxis(ang, axis().x, 0, axis().z);
        Vector3f v = new Vector3f(r.getX(), r.getY() + 0.5f, r.getZ()).rotate(q);
        return BlockPos.containing(getX() + v.x, getY() + v.y, getZ() + v.z);
    }

    void land(ServerLevel level) {
        ServerPlayer player = by == null ? null : (ServerPlayer) level.getPlayerByUUID(by);
        int n = states.size();
        float ang = angle(1);
        boolean thud = false;
        for (int i = 0; i < n; i++) {
            BlockState s = states.get(i);
            BlockPos at = landing(rel.get(i), ang);
            // rest on the ground below where it came down
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            double y = Math.min(at.getY(), top) + 0.3;
            BlockPos origin = blockPosition().offset(rel.get(i));
            List<ItemStack> drops = Block.getDrops(s, level, origin, null, player, tool);
            for (ItemStack d : drops) {
                ItemEntity ie = new ItemEntity(level, at.getX() + 0.5, y, at.getZ() + 0.5, d);
                ie.setDeltaMovement((random.nextDouble() - 0.5) * 0.12, 0.12 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.12);
                ie.setDefaultPickUpDelay();
                level.addFreshEntity(ie);
            }
            boolean log = s.is(net.minecraft.tags.BlockTags.LOGS);
            if (log || random.nextFloat() < 0.25f)
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), at.getX() + 0.5, y + 0.2, at.getZ() + 0.5, log ? 10 : 4, 0.35, 0.2, 0.35, 0.05);
            if (log) thud = true;
        }
        Vec3 mid = position().add(dirX * 3, 0, dirZ * 3);
        level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.6f, 0.6f);
        if (thud) {
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0f, 0.5f);
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 1.6f, 0.7f);
        }
        // a heavy trunk landing shakes the ground for anyone close
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(mid, mid).inflate(10)))
            com.hollowhours.util.Fx.shake(p, 0.25f, 8);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    //#if MC>=12100
    //$$ protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {}
    //#else
    protected void defineSynchedData() {}
    //#endif

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        // not saved: a falling tree lasts under two seconds
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    //#if MC<12100
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    //#endif

    @Override
    //#if NEOFORGE
    //$$ public void writeSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buf) {
    //#else
    public void writeSpawnData(FriendlyByteBuf buf) {
    //#endif
        buf.writeFloat(dirX);
        buf.writeFloat(dirZ);
        buf.writeVarInt(states.size());
        for (int i = 0; i < states.size(); i++) {
            buf.writeVarInt(Block.getId(states.get(i)));
            buf.writeBlockPos(rel.get(i));
        }
    }

    @Override
    //#if NEOFORGE
    //$$ public void readSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buf) {
    //#else
    public void readSpawnData(FriendlyByteBuf buf) {
    //#endif
        dirX = buf.readFloat();
        dirZ = buf.readFloat();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            BlockState s = Block.stateById(buf.readVarInt());
            states.add(s == null ? Blocks.OAK_LOG.defaultBlockState() : s);
            rel.add(buf.readBlockPos());
        }
    }
}
