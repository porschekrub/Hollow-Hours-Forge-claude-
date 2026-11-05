package net.minecraftforge.internal.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccess {
    @Invoker("setRotation")
    void shim$setRotation(float yRot, float xRot);
}
