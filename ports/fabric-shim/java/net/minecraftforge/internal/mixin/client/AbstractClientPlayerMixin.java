package net.minecraftforge.internal.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
    private float shim$fovModifier(float f) {
        ComputeFovModifierEvent e = new ComputeFovModifierEvent((AbstractClientPlayer) (Object) this, f);
        Hooks.post(e);
        return e.getNewFovModifier();
    }
}
