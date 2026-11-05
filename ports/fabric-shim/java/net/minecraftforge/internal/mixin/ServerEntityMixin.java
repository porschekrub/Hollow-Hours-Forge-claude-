package net.minecraftforge.internal.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.internal.SpawnData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    /** IEntityAdditionalSpawnData: the extra data follows the spawn packet (same bundle). */
    @Inject(method = "sendPairingData", at = @At("TAIL"))
    private void shim$spawnData(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> out, CallbackInfo ci) {
        if (entity instanceof IEntityAdditionalSpawnData) out.accept(SpawnData.packet(entity));
    }
}
