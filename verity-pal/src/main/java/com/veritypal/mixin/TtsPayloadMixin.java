package com.veritypal.mixin;

import com.veritypal.friends.Heard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import varmite.verity.network.PlayTtsPayload;

/** Hears what Verity says (every line she says aloud is made into one of these on the server). */
@Mixin(value = PlayTtsPayload.class, remap = false)
public abstract class TtsPayloadMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void veritypal$heard(int entityId, String text, CallbackInfo ci) {
        Heard.spoken(text);
    }
}
