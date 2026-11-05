package net.minecraftforge.internal.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    private int ticks;

    /** Forge's AFTER_SKY stage (Fabric has none). */
    //#if MC>=12100
    //$$ @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", shift = At.Shift.AFTER))
    //$$ private void shim$afterSky(net.minecraft.client.DeltaTracker dt, boolean outline, Camera camera, net.minecraft.client.renderer.GameRenderer gr,
    //$$                            net.minecraft.client.renderer.LightTexture lt, Matrix4f view, Matrix4f projection, CallbackInfo ci) {
    //$$     com.mojang.blaze3d.vertex.PoseStack ps = new com.mojang.blaze3d.vertex.PoseStack();
    //$$     ps.mulPose(view);
    //$$     Hooks.post(new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_SKY, (LevelRenderer) (Object) this, ps, projection, ticks,
    //$$             dt.getGameTimeDeltaPartialTick(false), camera, null));
    //$$ }
    //#else
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", shift = At.Shift.AFTER))
    private void shim$afterSky(com.mojang.blaze3d.vertex.PoseStack ps, float partial, long nano, boolean outline, Camera camera,
                               net.minecraft.client.renderer.GameRenderer gr, net.minecraft.client.renderer.LightTexture lt, Matrix4f projection, CallbackInfo ci) {
        Hooks.post(new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_SKY, (LevelRenderer) (Object) this, ps, projection, ticks, partial, camera, null));
    }
    //#endif
}
