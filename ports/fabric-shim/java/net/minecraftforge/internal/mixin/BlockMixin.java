package net.minecraftforge.internal.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.internal.BlockXp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void shim$catchXp(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
        if (BlockXp.caught(amount)) ci.cancel();
    }
}
