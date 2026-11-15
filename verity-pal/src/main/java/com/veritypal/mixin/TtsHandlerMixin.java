package com.veritypal.mixin;

import com.veritypal.client.ThaiEars;
import com.veritypal.client.ThaiVoice;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import varmite.verity.client.audio.TTSHandler;
import varmite.verity.entity.verity.VerityEntity;

import javax.sound.sampled.AudioFormat;

/** Verity JE's voice and ears (client): Thai lines are read by Gemini, and Gemini can write down what you say in any language. */
@Mixin(value = TTSHandler.class, remap = false)
public abstract class TtsHandlerMixin {
    @Inject(method = "playTTS", at = @At("HEAD"), cancellable = true, require = 0)
    private static void veritypal$voice(String text, Player player, VerityEntity verity, CallbackInfo ci) {
        if (ThaiVoice.take(text, player, verity)) ci.cancel();
    }

    @Inject(method = "transcribeAudio", at = @At("HEAD"), cancellable = true, require = 0)
    private static void veritypal$ears(byte[] pcm, AudioFormat format, CallbackInfoReturnable<String> cir) {
        String heard = ThaiEars.hear(pcm, format);
        if (heard != null) cir.setReturnValue(heard);
    }
}
