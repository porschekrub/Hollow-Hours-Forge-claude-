package com.helperbuddy.registry;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.item.CommandBookItem;
import com.helperbuddy.item.ContractItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class HBRegistry {
    private HBRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HelperBuddy.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, HelperBuddy.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HelperBuddy.MODID);

    public static final Supplier<EntityType<HelperEntity>> HELPER = ENTITIES.register("helper",
            () -> EntityType.Builder.<HelperEntity>of(HelperEntity::new, MobCategory.MISC).sized(0.6f, 1.75f).clientTrackingRange(10).updateInterval(2)
                    .build(HelperBuddy.MODID + ":helper"));

    public static final Supplier<Item> CONTRACT = ITEMS.register("helper_contract", () -> new ContractItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final Supplier<Item> COMMAND_BOOK = ITEMS.register("command_book", () -> new CommandBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Supplier<Item> SPAWN_EGG = ITEMS.register("helper_spawn_egg", () -> new ForgeSpawnEggItem(HELPER, 0x3A7BD5, 0xF5C542, new Item.Properties()));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.helperbuddy"))
            .icon(() -> new ItemStack(CONTRACT.get()))
            .displayItems((params, out) -> {
                out.accept(CONTRACT.get());
                out.accept(COMMAND_BOOK.get());
                out.accept(SPAWN_EGG.get());
            })
            .build());
}
