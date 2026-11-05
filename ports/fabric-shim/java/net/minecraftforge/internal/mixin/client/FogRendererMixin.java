package net.minecraftforge.internal.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V"))
    private static void shim$fogColor(Camera camera, float partial, ClientLevel level, int renderDistance, float darken, CallbackInfo ci) {
        ViewportEvent.ComputeFogColor e = new ViewportEvent.ComputeFogColor(camera, partial, fogRed, fogGreen, fogBlue);
        Hooks.post(e);
        fogRed = e.getRed();
        fogGreen = e.getGreen();
        fogBlue = e.getBlue();
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void shim$fog(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thick, float partial, CallbackInfo ci) {
        ViewportEvent.RenderFog e = new ViewportEvent.RenderFog(mode, camera.getFluidInCamera(), camera, partial,
                RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogShape());
        if (Hooks.post(e)) {
            RenderSystem.setShaderFogStart(e.getNearPlaneDistance());
            RenderSystem.setShaderFogEnd(e.getFarPlaneDistance());
            RenderSystem.setShaderFogShape(e.getFogShape());
        }
    }
}
