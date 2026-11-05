package net.minecraftforge.internal.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraftforge.internal.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void shim$chat(CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().screen == null && ClientHooks.hudHidden("chat_panel")) ci.cancel();
    }
}
