package com.veritypal.mixin;

import com.veritypal.story.StoryGate;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With the story off, a few of Verity JE's settings read differently (StoryGate). */
@Mixin(value = ForgeConfigSpec.ConfigValue.class, remap = false)
public abstract class ConfigValueMixin<T> {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void veritypal$story(CallbackInfoReturnable<T> cir) {
        Object o = StoryGate.override(this);
        if (o != null) {
            @SuppressWarnings("unchecked") T t = (T) o;
            cir.setReturnValue(t);
        }
    }
}
