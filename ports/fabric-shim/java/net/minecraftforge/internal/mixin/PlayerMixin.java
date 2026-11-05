package net.minecraftforge.internal.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.internal.Hooks;
import net.minecraftforge.internal.Poses;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin implements Poses.Holder {
    @Unique
    private Pose shim$pose;

    @Override
    public Pose shim$forcedPose() {
        return shim$pose;
    }

    @Override
    public void shim$forcePose(Pose pose) {
        shim$pose = pose;
    }

    /** Forge's forced pose wins over the one the player would take. */
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void shim$pose(CallbackInfo ci) {
        if (shim$pose != null) {
            ((Player) (Object) this).setPose(shim$pose);
            ci.cancel();
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void shim$attack(Entity target, CallbackInfo ci) {
        if (Hooks.post(new AttackEntityEvent((Player) (Object) this, target))) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void shim$tickStart(CallbackInfo ci) {
        Hooks.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, (Player) (Object) this));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void shim$tickEnd(CallbackInfo ci) {
        Hooks.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, (Player) (Object) this));
    }

    @WrapMethod(method = "actuallyHurt")
    private void shim$hurt(DamageSource source, float amount, Operation<Void> original) {
        LivingHurtEvent e = new LivingHurtEvent((Player) (Object) this, source, amount);
        if (Hooks.post(e) || e.getAmount() <= 0) return;
        original.call(source, e.getAmount());
    }

    @ModifyArg(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/CombatTracker;recordDamage(Lnet/minecraft/world/damagesource/DamageSource;F)V"), index = 1)
    private float shim$damage(DamageSource source, float amount) {
        LivingDamageEvent e = new LivingDamageEvent((Player) (Object) this, source, amount);
        Hooks.post(e);
        return e.getAmount();
    }
}
