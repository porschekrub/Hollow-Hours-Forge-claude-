package net.minecraftforge.internal.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Camera mainCamera;

    /** Forge's ComputeCameraAngles: right after the camera is placed, before the view is turned. */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.AFTER))
    //#if MC>=12100
    //$$ private void shim$angles(net.minecraft.client.DeltaTracker dt, CallbackInfo ci) {
    //$$     float partial = dt.getGameTimeDeltaPartialTick(true);
    //#else
    private void shim$angles(float partial, long nano, com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
    //#endif
        Camera c = mainCamera;
        ViewportEvent.ComputeCameraAngles e = new ViewportEvent.ComputeCameraAngles((GameRenderer) (Object) this, c, partial, c.getYRot(), c.getXRot(), 0);
        Hooks.post(e);
        if (e.getYaw() != c.getYRot() || e.getPitch() != c.getXRot()) ((CameraAccess) c).shim$setRotation(e.getYaw(), e.getPitch());
        if (e.getRoll() != 0) {
            //#if MC>=12100
            //$$ float rad = (float) (Math.PI / 180.0);
            //$$ c.rotation().rotationYXZ((float) Math.PI - e.getYaw() * rad, -e.getPitch() * rad, -e.getRoll() * rad);
            //#else
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(e.getRoll()));
            //#endif
        }
    }

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double shim$fov(double fov, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) Camera camera,
                            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) float partial,
                            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) boolean useSetting) {
        ViewportEvent.ComputeFov e = new ViewportEvent.ComputeFov((GameRenderer) (Object) this, camera, partial, fov, useSetting);
        Hooks.post(e);
        return e.getFOV();
    }
}
