package com.veritypal.mixin;

import com.veritypal.talk.PromptPatch;
import com.veritypal.tools.PalTools;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import varmite.verity.entity.llm.AiAPI;
import varmite.verity.entity.verity.VerityEntity;

/** Verity JE's AI: her prompt goes through PromptPatch, and she gets Verity Pal's tools next to her own. */
@Mixin(value = AiAPI.class, remap = false)
public abstract class AiApiMixin {
    @Inject(method = "getSystemPrompt", at = @At("RETURN"), cancellable = true, require = 0)
    private static void veritypal$prompt(ServerPlayer player, VerityEntity verity, ServerLevel level, long day, float karma, boolean demon,
                                         CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(PromptPatch.patch(cir.getReturnValue(), player, level, karma, demon));
    }

    @ModifyArg(method = "ask", at = @At(value = "INVOKE", target = "Ldev/langchain4j/service/AiServices;tools([Ljava/lang/Object;)Ldev/langchain4j/service/AiServices;"),
            index = 0, require = 0)
    private static Object[] veritypal$tools(Object[] tools) {
        return PalTools.withOurs(tools);
    }
}
