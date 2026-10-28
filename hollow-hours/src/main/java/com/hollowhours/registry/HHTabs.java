package com.hollowhours.registry;

import com.hollowhours.HollowHours;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class HHTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HollowHours.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hollowhours"))
            .icon(() -> new ItemStack(HHItems.GHOST_GUIDE.get()))
            .displayItems((params, out) -> {
                out.accept(HHItems.GHOST_GUIDE.get());
                out.accept(HHItems.FLASHLIGHT.get());
                out.accept(HHItems.BATTERY.get());
                out.accept(HHItems.BOARD_WOOD.get());
                out.accept(HHItems.BOARD_STONE.get());
                out.accept(HHItems.BOARD_IRON.get());
                out.accept(HHItems.REPAIR_WOOD.get());
                out.accept(HHItems.REPAIR_IRON.get());
                out.accept(HHItems.REPAIR_STONE.get());
                out.accept(HHItems.REPAIR_GLASS.get());
                out.accept(HHItems.CLOSET.get());
                out.accept(HHItems.PHONE.get());
                out.accept(HHItems.CHARGER.get());
                HHBlocks.ALL.forEach(b -> out.accept(b.get()));
                out.accept(HHItems.BASKETBALL.get());
                out.accept(HHItems.SOCCER_BALL.get());
                HHItems.EGGS.forEach(e -> out.accept(e.get()));
            })
            .build());

    private HHTabs() {}
}
