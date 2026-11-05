package com.hollowhours.compat;

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
        return s.save(new CompoundTag());
        //#endif
    }

    public static ItemStack loadStack(CompoundTag t) {
        //#if MC>=12100
        //$$ return ItemStack.parseOptional(registries(), t);
        //#else
        return ItemStack.of(t);
        //#endif
    }

    /** How far a rider sits below its seat (1.20.1's riding offset; players -0.35). */
    public static double ridingOffset(net.minecraft.world.entity.Entity rider) {
        //#if MC>=12100
        //$$ return rider instanceof net.minecraft.world.entity.player.Player ? -0.35 : 0;
        //#else
        return rider.getMyRidingOffset();
        //#endif
    }

    /** Sets an entity on fire for some seconds. */
    public static void ignite(net.minecraft.world.entity.Entity e, int seconds) {
        //#if MC>=12100
        //$$ e.igniteForSeconds(seconds);
        //#else
        e.setSecondsOnFire(seconds);
        //#endif
    }

    /** Food (anything that can be eaten). */
    public static boolean isFood(ItemStack s) {
        //#if MC>=12100
        //$$ return s.has(net.minecraft.core.component.DataComponents.FOOD);
        //#else
        return s.isEdible();
        //#endif
    }

    public static boolean isFood(net.minecraft.world.item.Item item) {
        //#if MC>=12100
        //$$ return item.components().has(net.minecraft.core.component.DataComponents.FOOD);
        //#else
        return item.isEdible();
        //#endif
    }

    public static int maxStack(net.minecraft.world.item.Item item) {
        //#if MC>=12100
        //$$ return item.getDefaultMaxStackSize();
        //#else
        return item.getMaxStackSize();
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

    /** A stand-in player for tests (Forge 1.21 has no fake players: a plain server player without a connection). */
    public static net.minecraft.server.level.ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level) {
        //#if MC>=12100 && FORGE
        //$$ return new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
        //$$         new com.mojang.authlib.GameProfile(java.util.UUID.fromString("41C82C87-7AfB-4024-BA57-13D2C99CAE77"), "[Minecraft]"),
        //$$         net.minecraft.server.level.ClientInformation.createDefault());
        //#else
        return net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        //#endif
    }
}
