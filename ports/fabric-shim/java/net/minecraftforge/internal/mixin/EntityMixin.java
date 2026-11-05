package net.minecraftforge.internal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.internal.Drops;
import net.minecraftforge.internal.Persist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Forge's persistent entity data (saved under the mod's own key) and drop capturing. */
@Mixin(Entity.class)
public abstract class EntityMixin implements Persist.Holder, Drops.Holder {
    @Unique
    private static final String SHIM_KEY = "shim$ForgeData";
    @Unique
    private CompoundTag shim$data;
    @Unique
    private List<ItemEntity> shim$drops;

    @Override
    public CompoundTag shim$persistentData() {
        if (shim$data == null) shim$data = new CompoundTag();
        return shim$data;
    }

    @Override
    public List<ItemEntity> shim$captured() {
        return shim$drops;
    }

    @Override
    public void shim$capture(List<ItemEntity> list) {
        shim$drops = list;
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void shim$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (shim$data != null && !shim$data.isEmpty()) tag.put(SHIM_KEY, shim$data);
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void shim$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(SHIM_KEY, Tag.TAG_COMPOUND)) shim$data = tag.getCompound(SHIM_KEY);
    }

    @WrapOperation(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean shim$captureDrop(Level level, Entity item, Operation<Boolean> original) {
        if (Drops.caught((Entity) (Object) this, item)) return true;
        return original.call(level, item);
    }
}
