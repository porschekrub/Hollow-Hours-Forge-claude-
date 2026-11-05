package net.minecraftforge.internal.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.internal.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    /** Forge's EntityPlaceEvent (here only told afterwards). */
    @Inject(method = "place", at = @At("RETURN"))
    private void shim$placed(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction() || ctx.getLevel().isClientSide) return;
        var pos = ctx.getClickedPos();
        Hooks.post(new BlockEvent.EntityPlaceEvent(ctx.getLevel(), pos, ctx.getLevel().getBlockState(pos),
                ctx.getLevel().getBlockState(pos.relative(ctx.getClickedFace().getOpposite())), ctx.getPlayer()));
    }
}
