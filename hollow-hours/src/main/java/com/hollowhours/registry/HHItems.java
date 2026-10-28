package com.hollowhours.registry;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.entity.StalkerKind;
import com.hollowhours.item.BatteryItem;
import com.hollowhours.item.BoardItem;
import com.hollowhours.item.ClosetItem;
import com.hollowhours.item.FlashlightItem;
import com.hollowhours.item.GhostGuideItem;
import com.hollowhours.item.RepairKitItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class HHItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HollowHours.MODID);

    public static final RegistryObject<Item> GHOST_GUIDE = ITEMS.register("ghost_guide",
            () -> new GhostGuideItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> FLASHLIGHT = ITEMS.register("flashlight",
            () -> new FlashlightItem(new Item.Properties().stacksTo(1).durability(FlashlightItem.DUR_SPAN)));
    public static final RegistryObject<Item> BATTERY = ITEMS.register("battery", () -> new BatteryItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> BOARD_WOOD = ITEMS.register("board_wood", () -> new BoardItem(new Item.Properties(), 0));
    public static final RegistryObject<Item> BOARD_STONE = ITEMS.register("board_stone", () -> new BoardItem(new Item.Properties(), 1));
    public static final RegistryObject<Item> BOARD_IRON = ITEMS.register("board_iron", () -> new BoardItem(new Item.Properties(), 2));
    public static final RegistryObject<Item> REPAIR_WOOD = ITEMS.register("repair_wood", () -> new RepairKitItem(new Item.Properties().stacksTo(16), "wood"));
    public static final RegistryObject<Item> REPAIR_IRON = ITEMS.register("repair_iron", () -> new RepairKitItem(new Item.Properties().stacksTo(16), "iron"));
    public static final RegistryObject<Item> REPAIR_STONE = ITEMS.register("repair_stone", () -> new RepairKitItem(new Item.Properties().stacksTo(16), "stone"));
    public static final RegistryObject<Item> REPAIR_GLASS = ITEMS.register("repair_glass", () -> new RepairKitItem(new Item.Properties().stacksTo(16), "glass"));
    public static final RegistryObject<Item> CLOSET = ITEMS.register("closet_item", () -> new ClosetItem(new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> PHONE = ITEMS.register("phone", () -> new com.hollowhours.item.PhoneItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CHARGER = ITEMS.register("charger", () -> new com.hollowhours.item.ChargerItem(new Item.Properties().durability(3)));

    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();

    static {
        for (GhostKind k : GhostKind.values()) {
            EGGS.add(ITEMS.register(k.id() + "_spawn_egg",
                    () -> new ForgeSpawnEggItem(() -> HHEntities.ghost(k), k.eggBg, k.eggFg, new Item.Properties())));
        }
        for (StalkerKind k : StalkerKind.values()) {
            EGGS.add(ITEMS.register(k.id() + "_spawn_egg",
                    () -> new ForgeSpawnEggItem(() -> HHEntities.stalker(k), k.eggBg, k.eggFg, new Item.Properties())));
        }
    }

    public static Item boardFor(int mat) {
        return switch (mat) {
            case 1 -> BOARD_STONE.get();
            case 2 -> BOARD_IRON.get();
            default -> BOARD_WOOD.get();
        };
    }

    private HHItems() {}
}
