package net.minecraftforge.internal.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.internal.Drops;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    protected int lastHurtByPlayerTime;
    @Shadow
    protected Player lastHurtByPlayer;

    //#if MC>=12100
    //$$ /** canBreatheUnderwater is final in 1.21: the mods' entities answer with a plain method. */
    //$$ @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "canBreatheUnderwater", at = @At("RETURN"))
    //$$ private boolean shim$breathe(boolean can) {
    //$$     return can || net.minecraftforge.internal.Ext.bool(this, "breathesUnderwater", false);
    //$$ }
    //#endif

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void shim$tick(CallbackInfo ci) {
        if (Hooks.post(new LivingEvent.LivingTickEvent((LivingEntity) (Object) this))) ci.cancel();
    }

    /** Forge's LivingHurtEvent: before armour; a changed amount, or none at all. */
    @WrapMethod(method = "actuallyHurt")
    private void shim$hurt(DamageSource source, float amount, Operation<Void> original) {
        LivingHurtEvent e = new LivingHurtEvent((LivingEntity) (Object) this, source, amount);
        if (Hooks.post(e) || e.getAmount() <= 0) return;
        original.call(source, e.getAmount());
    }

    @ModifyArg(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;F)V"), index = 1)
    private float shim$damage(DamageSource source, float amount) {
        LivingDamageEvent e = new LivingDamageEvent((LivingEntity) (Object) this, source, amount);
        Hooks.post(e);
        return e.getAmount();
    }

    @WrapOperation(method = "completeUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack shim$useFinish(ItemStack stack, Level level, LivingEntity self, Operation<ItemStack> original) {
        ItemStack copy = stack.copy();
        int left = self.getUseItemRemainingTicks();
        ItemStack result = original.call(stack, level, self);
        LivingEntityUseItemEvent.Finish e = new LivingEntityUseItemEvent.Finish(self, copy, left, result);
        Hooks.post(e);
        return e.getResultStack();
    }

    @WrapOperation(method = "releaseUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;releaseUsing(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V"))
    private void shim$useStop(ItemStack stack, Level level, LivingEntity self, int left, Operation<Void> original) {
        if (Hooks.post(new LivingEntityUseItemEvent.Stop(self, stack, left))) return;
        original.call(stack, level, self, left);
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void shim$die(DamageSource source, CallbackInfo ci) {
        if (Hooks.post(new LivingDeathEvent((LivingEntity) (Object) this, source))) ci.cancel();
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void shim$dropsStart(CallbackInfo ci) {
        ((Drops.Holder) this).shim$capture(new ArrayList<>());
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    //#if MC>=12100
    //$$ private void shim$dropsEnd(net.minecraft.server.level.ServerLevel level, DamageSource source, CallbackInfo ci) {
    //#else
    private void shim$dropsEnd(DamageSource source, CallbackInfo ci) {
    //#endif
        LivingEntity self = (LivingEntity) (Object) this;
        List<ItemEntity> drops = ((Drops.Holder) this).shim$captured();
        ((Drops.Holder) this).shim$capture(null);
        if (drops == null) return;
        //#if MC>=12100
        //$$ int looting = 0;
        //#else
        int looting = source.getEntity() instanceof LivingEntity le ? net.minecraft.world.item.enchantment.EnchantmentHelper.getMobLooting(le) : 0;
        //#endif
        if (!Hooks.post(new LivingDropsEvent(self, source, drops, looting, lastHurtByPlayerTime > 0)))
            for (ItemEntity ie : drops) self.level().addFreshEntity(ie);
    }

    @ModifyArg(method = "dropExperience", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"), index = 2)
    private int shim$xp(int xp) {
        LivingExperienceDropEvent e = new LivingExperienceDropEvent((LivingEntity) (Object) this, lastHurtByPlayer, xp);
        return Hooks.post(e) ? 0 : e.getDroppedExperience();
    }
}
