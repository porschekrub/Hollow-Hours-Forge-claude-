package com.veritypal.mixin;

import com.veritypal.story.StoryGate;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import varmite.verity.entity.verity.VerityEntity;

/** With the story off she never turns into the demon, whatever happened before. */
@Mixin(value = VerityEntity.class, remap = false)
public abstract class StoryVerityMixin {
    @Inject(method = "transformIntoDemon", at = @At("HEAD"), cancellable = true, require = 0)
    private void veritypal$noDemon(Player player, boolean provoked, CallbackInfoReturnable<Boolean> cir) {
        if (StoryGate.off()) cir.setReturnValue(false);
    }
}
