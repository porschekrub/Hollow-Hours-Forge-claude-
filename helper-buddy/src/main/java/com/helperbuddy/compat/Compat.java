package com.helperbuddy.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.function.Function;
import java.util.function.Supplier;

/** Small differences between the Minecraft versions, behind one name. */
public final class Compat {
    private Compat() {}

    /** A level's saved data, loaded (or made) once. */
    public static <T extends SavedData> T saved(DimensionDataStorage storage, Function<CompoundTag, T> load, Supplier<T> make, String name) {
        //#if MC>=12100
        //$$ return storage.computeIfAbsent(new SavedData.Factory<>(make, (tag, registries) -> load.apply(tag), null), name);
        //#else
        return storage.computeIfAbsent(load, make, name);
        //#endif
    }

    //#if MC>=12100
    //$$ /** The registries items are saved with: the running server's, else the client's world. */
    //$$ public static net.minecraft.core.HolderLookup.Provider registries() {
    //$$     var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    //$$     if (server != null) return server.registryAccess();
    //$$     return ClientCompat.registries();
    //$$ }
    //#endif

    /** An item stack as NBT (an empty stack: an empty tag). */
    public static CompoundTag saveStack(ItemStack s) {
        //#if MC>=12100
        //$$ return s.isEmpty() ? new CompoundTag() : (CompoundTag) s.save(registries());
        //#else
        return Compat.saveStack(s);
        //#endif
    }

    public static ItemStack loadStack(CompoundTag t) {
        //#if MC>=12100
        //$$ return ItemStack.parseOptional(registries(), t);
        //#else
        return Compat.loadStack(t);
        //#endif
    }

    /** Wears an item held in a hand (it breaks with the usual sound at 0). */
    public static void hurtItem(ItemStack s, int amount, net.minecraft.world.entity.LivingEntity who, net.minecraft.world.InteractionHand hand) {
        if (amount <= 0) return;
        //#if MC>=12100
        //$$ s.hurtAndBreak(amount, who, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
        //#else
        s.hurtAndBreak(amount, who, e -> e.broadcastBreakEvent(hand));
        //#endif
    }

    /** Runs client-only code on the physical client (the double lambda keeps the client class unloaded on servers). */
    public static void client(Supplier<Runnable> code) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) code.get().run();
    }
}
