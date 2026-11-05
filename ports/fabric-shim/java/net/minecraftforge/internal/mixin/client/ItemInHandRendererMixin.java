package net.minecraftforge.internal.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    /** Forge's RenderHandEvent, once per hand (cancelled: that hand is not drawn). */
    @WrapOperation(method = "renderHandsWithItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void shim$hand(ItemInHandRenderer self, AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                           ItemStack stack, float equip, PoseStack ps, MultiBufferSource buffers, int light, Operation<Void> original) {
        if (Hooks.post(new RenderHandEvent(hand, ps, buffers, light, partial, pitch, swing, equip, stack))) return;
        original.call(self, player, partial, pitch, hand, swing, stack, equip, ps, buffers, light);
    }
}
