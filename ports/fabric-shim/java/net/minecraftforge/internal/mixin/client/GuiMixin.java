package net.minecraftforge.internal.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.internal.ClientHooks;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge's HUD events: RenderGuiEvent around the whole HUD, RenderGuiOverlayEvent.Pre for each vanilla part. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"))
    //#if MC>=12100
    //$$ private void shim$pre(GuiGraphics g, net.minecraft.client.DeltaTracker dt, CallbackInfo ci) {
    //$$     Hooks.post(new RenderGuiEvent.Pre(g, dt.getGameTimeDeltaPartialTick(false)));
    //#else
    private void shim$pre(GuiGraphics g, float partial, CallbackInfo ci) {
        Hooks.post(new RenderGuiEvent.Pre(g, partial));
    //#endif
    }

    @Inject(method = "render", at = @At("TAIL"))
    //#if MC>=12100
    //$$ private void shim$post(GuiGraphics g, net.minecraft.client.DeltaTracker dt, CallbackInfo ci) {
    //$$     Hooks.post(new RenderGuiEvent.Post(g, dt.getGameTimeDeltaPartialTick(false)));
    //#else
    private void shim$post(GuiGraphics g, float partial, CallbackInfo ci) {
        Hooks.post(new RenderGuiEvent.Post(g, partial));
    //#endif
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void shim$crosshair(CallbackInfo ci) {
        if (ClientHooks.hudHidden("crosshair")) ci.cancel();
    }

    //#if MC>=12100
    //$$ @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    //#else
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    //#endif
    private void shim$hotbar(CallbackInfo ci) {
        if (ClientHooks.hudHidden("hotbar")) ci.cancel();
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void shim$health(CallbackInfo ci) {
        if (ClientHooks.hudHidden("player_health") | ClientHooks.hudHidden("food_level") | ClientHooks.hudHidden("armor_level")) ci.cancel();
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void shim$vehicle(CallbackInfo ci) {
        if (ClientHooks.hudHidden("mount_health")) ci.cancel();
    }

    @Inject(method = "renderJumpMeter", at = @At("HEAD"), cancellable = true)
    private void shim$jump(CallbackInfo ci) {
        if (ClientHooks.hudHidden("jump_bar")) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void shim$xp(CallbackInfo ci) {
        if (ClientHooks.hudHidden("experience_bar")) ci.cancel();
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void shim$itemName(CallbackInfo ci) {
        if (ClientHooks.hudHidden("item_name")) ci.cancel();
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void shim$effects(CallbackInfo ci) {
        if (ClientHooks.hudHidden("potion_icons")) ci.cancel();
    }
}
