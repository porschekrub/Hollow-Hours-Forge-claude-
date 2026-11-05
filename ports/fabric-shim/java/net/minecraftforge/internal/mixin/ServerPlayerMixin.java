package net.minecraftforge.internal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraftforge.internal.Drops;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void shim$die(DamageSource source, CallbackInfo ci) {
        if (Hooks.post(new LivingDeathEvent((ServerPlayer) (Object) this, source))) ci.cancel();
    }

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    //#if MC>=12100
    //$$ private void shim$travel(net.minecraft.world.level.portal.DimensionTransition to, CallbackInfoReturnable<Entity> cir) {
    //$$     if (Hooks.post(new EntityTravelToDimensionEvent((ServerPlayer) (Object) this, to.newLevel().dimension()))) cir.setReturnValue(null);
    //#else
    private void shim$travel(net.minecraft.server.level.ServerLevel to, CallbackInfoReturnable<Entity> cir) {
        if (Hooks.post(new EntityTravelToDimensionEvent((ServerPlayer) (Object) this, to.dimension()))) cir.setReturnValue(null);
    //#endif
    }

    /** A dying player's inventory is collected for LivingDropsEvent too. */
    @WrapOperation(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean shim$captureDrop(Level level, Entity item, Operation<Boolean> original) {
        if (Drops.caught((Entity) (Object) this, item)) return true;
        return original.call(level, item);
    }
}
