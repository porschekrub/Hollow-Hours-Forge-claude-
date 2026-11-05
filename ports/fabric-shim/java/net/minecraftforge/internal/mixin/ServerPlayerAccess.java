package net.minecraftforge.internal.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayer.class)
public interface ServerPlayerAccess {
    //#if MC<12100
    @Accessor("language")
    String shim$language();
    //#endif
}
