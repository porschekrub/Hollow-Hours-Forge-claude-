package com.veritypal.mixin;

import com.veritypal.story.StoryGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import varmite.verity.entity.llm.actions.Tools;

/** With the story off her AI can't schedule turning into the demon. */
@Mixin(value = Tools.class, remap = false)
public abstract class StoryToolsMixin {
    @Inject(method = "transform_next_day", at = @At("HEAD"), cancellable = true, require = 0)
    private void veritypal$noDemon(CallbackInfoReturnable<String> cir) {
        if (StoryGate.off()) cir.setReturnValue("You can't do that: you are only a friendly helper now.");
    }
}
