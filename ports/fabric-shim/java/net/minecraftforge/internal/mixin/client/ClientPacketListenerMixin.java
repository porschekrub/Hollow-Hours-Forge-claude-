package net.minecraftforge.internal.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void shim$recipes(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        Hooks.post(new RecipesUpdatedEvent(((ClientPacketListener) (Object) this).getRecipeManager()));
    }
}
