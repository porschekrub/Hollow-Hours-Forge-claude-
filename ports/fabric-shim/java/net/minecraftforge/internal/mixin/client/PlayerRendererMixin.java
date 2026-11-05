package net.minecraftforge.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void shim$pre(AbstractClientPlayer p, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (Hooks.post(new RenderPlayerEvent.Pre(p, (PlayerRenderer) (Object) this, partial, ps, buffers, light))) ci.cancel();
    }

    /** IClientItemExtensions#getArmPose (guns are held like a crossbow). */
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void shim$armPose(AbstractClientPlayer p, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        ItemStack s = p.getItemInHand(hand);
        if (s.isEmpty()) return;
        IClientItemExtensions ext = IClientItemExtensions.of(s);
        if (ext == IClientItemExtensions.DEFAULT) return;
        HumanoidModel.ArmPose pose = ext.getArmPose(p, hand, s);
        if (pose != null) cir.setReturnValue(pose);
    }
}
