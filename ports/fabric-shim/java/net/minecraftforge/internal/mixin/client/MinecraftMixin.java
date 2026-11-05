package net.minecraftforge.internal.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public Screen screen;
    @Shadow
    public LocalPlayer player;

    @WrapMethod(method = "setScreen")
    private void shim$opening(Screen next, Operation<Void> original) {
        ScreenEvent.Opening e = new ScreenEvent.Opening(screen, next);
        if (next != null && Hooks.post(e)) return;
        original.call(next == null ? null : e.getNewScreen());
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void shim$attack(CallbackInfoReturnable<Boolean> cir) {
        if (player == null) return;
        InputEvent.InteractionKeyMappingTriggered e = InputEvent.InteractionKeyMappingTriggered.attack();
        if (Hooks.post(e)) {
            if (e.shouldSwingHand()) player.swing(InteractionHand.MAIN_HAND);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void shim$keepAttacking(boolean leftClick, CallbackInfo ci) {
        if (!leftClick || player == null || player.isUsingItem()) return;
        if (Hooks.post(InputEvent.InteractionKeyMappingTriggered.attack())) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void shim$use(CallbackInfo ci) {
        if (player == null) return;
        InputEvent.InteractionKeyMappingTriggered e = InputEvent.InteractionKeyMappingTriggered.use(InteractionHand.MAIN_HAND);
        if (Hooks.post(e)) {
            if (e.shouldSwingHand()) player.swing(InteractionHand.MAIN_HAND);
            ci.cancel();
        }
    }
}
