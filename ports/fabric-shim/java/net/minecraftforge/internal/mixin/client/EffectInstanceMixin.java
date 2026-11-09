package net.minecraftforge.internal.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.internal.ClientExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Post shader programs with a mod's name ("hollowhours:haunt"), which Forge and NeoForge read and vanilla does not. */
@Mixin(EffectInstance.class)
public abstract class EffectInstanceMixin {
    //#if MC>=12100
    //$$ @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    //$$ private ResourceLocation shim$config(String path, Operation<ResourceLocation> op) {
    //$$     ResourceLocation rl = ClientExt.programLocation(path);
    //$$     return rl != null ? rl : op.call(path);
    //$$ }
    //$$
    //$$ @WrapOperation(method = "getOrCreate", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    //$$ private static ResourceLocation shim$program(String path, Operation<ResourceLocation> op) {
    //$$     ResourceLocation rl = ClientExt.programLocation(path);
    //$$     return rl != null ? rl : op.call(path);
    //$$ }
    //#else
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation shim$config(String path, Operation<ResourceLocation> op) {
        ResourceLocation rl = ClientExt.programLocation(path);
        return rl != null ? rl : op.call(path);
    }

    @WrapOperation(method = "getOrCreate", at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation shim$program(String path, Operation<ResourceLocation> op) {
        ResourceLocation rl = ClientExt.programLocation(path);
        return rl != null ? rl : op.call(path);
    }
    //#endif
}
