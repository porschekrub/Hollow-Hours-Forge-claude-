package com.hollowhours.endgame;

import com.hollowhours.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Where it sank, a tear in the world full of white light. Step in. */
public class RiftEntity extends Entity {
    public RiftEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    //#if MC>=12100
    //$$ protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {}
    //#else
    protected void defineSynchedData() {}
    //#endif

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel sl = (ServerLevel) level();
        Endgame.Data d = Endgame.data(sl.getServer());
        if (d.stage < Endgame.RIFT || sl.getGameTime() > d.riftUntil && tickCount > 200) {
            discard();
            return;
        }
        if (tickCount % 2 == 0) {
            double a = tickCount * 0.21;
            for (int i = 0; i < 3; i++) {
                double b = a + i * Math.PI * 2 / 3;
                sl.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(b) * 1.6, getY() + 1.2 + Math.sin(tickCount * 0.1 + i) * 0.8,
                        getZ() + Math.sin(b) * 1.6, 1, 0, 0, 0, 0.01);
            }
        }
        if (tickCount % 10 == 0) sl.sendParticles(ParticleTypes.GLOW, getX(), getY() + 1, getZ(), 6, 0.8, 1.2, 0.8, 0.02);
        if (tickCount % 60 == 1) Fx.sound(level(), "rift.hum", position(), 2.4f, 1f, 32);
        // pulled in when close
        for (ServerPlayer p : sl.players()) {
            Vec3 c = position().add(0, 1, 0);
            double dist = p.position().add(0, 1, 0).distanceTo(c);
            if (dist < 1.6) Endgame.enterRift(p);
            else if (dist < 5 && !p.isShiftKeyDown()) {
                Vec3 pull = c.subtract(p.position().add(0, 1, 0)).normalize().scale(0.035);
                p.setDeltaMovement(p.getDeltaMovement().add(pull));
                p.hurtMarked = true;
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    //#if MC<12100
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
    //#endif
}
