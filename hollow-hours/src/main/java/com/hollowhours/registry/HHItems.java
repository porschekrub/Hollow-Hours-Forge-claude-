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
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;

import java.util.ArrayList;
import java.util.List;

public final class HHItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, HollowHours.MODID);

    public static final Supplier<Item> GHOST_GUIDE = ITEMS.register("ghost_guide",
            () -> new GhostGuideItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final Supplier<Item> FLASHLIGHT = ITEMS.register("flashlight",
            () -> new FlashlightItem(new Item.Properties().stacksTo(1).durability(FlashlightItem.DUR_SPAN)));
    public static final Supplier<Item> BATTERY = ITEMS.register("battery", () -> new BatteryItem(new Item.Properties().stacksTo(16)));
    public static final Supplier<Item> BOARD_WOOD = ITEMS.register("board_wood", () -> new BoardItem(new Item.Properties(), 0));
    public static final Supplier<Item> BOARD_STONE = ITEMS.register("board_stone", () -> new BoardItem(new Item.Properties(), 1));
    public static final Supplier<Item> BOARD_IRON = ITEMS.register("board_iron", () -> new BoardItem(new Item.Properties(), 2));
    public static final Supplier<Item> REPAIR_WOOD = ITEMS.register("repair_wood", () -> new RepairKitItem(new Item.Properties().stacksTo(16), "wood"));
    public static final Supplier<Item> REPAIR_IRON = ITEMS.register("repair_iron", () -> new RepairKitItem(new Item.Properties().stacksTo(16), "iron"));
    public static final Supplier<Item> REPAIR_STONE = ITEMS.register("repair_stone", () -> new RepairKitItem(new Item.Properties().stacksTo(16), "stone"));
    public static final Supplier<Item> REPAIR_GLASS = ITEMS.register("repair_glass", () -> new RepairKitItem(new Item.Properties().stacksTo(16), "glass"));
    public static final Supplier<Item> CLOSET = ITEMS.register("closet_item", () -> new ClosetItem(new Item.Properties().stacksTo(4)));
    public static final Supplier<Item> PHONE = ITEMS.register("phone", () -> new com.hollowhours.item.PhoneItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<Item> CHARGER = ITEMS.register("charger", () -> new com.hollowhours.item.ChargerItem(new Item.Properties().durability(3)));
    public static final Supplier<Item> CANTEEN = ITEMS.register("canteen", () -> new com.hollowhours.item.CanteenItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<Item> GRAVE_COMPASS = ITEMS.register("grave_compass", () -> new com.hollowhours.grave.GraveCompassItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<Item> RESTORER = ITEMS.register("restorer", () -> new com.hollowhours.grave.RestorerItem(new Item.Properties().durability(3).rarity(Rarity.RARE)));
    public static final Supplier<Item> BASKETBALL = ITEMS.register("basketball", () -> new com.hollowhours.fun.BallItem(new Item.Properties().stacksTo(4), com.hollowhours.fun.BallEntity.BASKETBALL));
    public static final Supplier<Item> SOCCER_BALL = ITEMS.register("soccer_ball", () -> new com.hollowhours.fun.BallItem(new Item.Properties().stacksTo(4), com.hollowhours.fun.BallEntity.SOCCER));

    // ------------------------------------------------------------------ fighting back (2.11)
    public static final Supplier<Item> PISTOL_AMMO = ITEMS.register("pistol_ammo", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> RIFLE_AMMO = ITEMS.register("rifle_ammo", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> SHOTGUN_SHELL = ITEMS.register("shotgun_shell", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> SNIPER_AMMO = ITEMS.register("sniper_ammo", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> PISTOL = ITEMS.register("pistol", () -> new com.hollowhours.combat.GunItem(
            new com.hollowhours.combat.GunItem.Spec("pistol", 12, 7, 9, 6, false, 1, 1.4f, 48, 30, 3f, 0.75f, () -> PISTOL_AMMO.get()), new Item.Properties()));
    public static final Supplier<Item> RIFLE = ITEMS.register("rifle", () -> new com.hollowhours.combat.GunItem(
            new com.hollowhours.combat.GunItem.Spec("rifle", 30, 5, 6, 3, true, 1, 2.4f, 64, 45, 1.6f, 0.7f, () -> RIFLE_AMMO.get()), new Item.Properties()));
    public static final Supplier<Item> SHOTGUN = ITEMS.register("shotgun", () -> new com.hollowhours.combat.GunItem(
            new com.hollowhours.combat.GunItem.Spec("shotgun", 6, 4, 3.5f, 18, false, 8, 7f, 24, 50, 7f, 0.8f, () -> SHOTGUN_SHELL.get()), new Item.Properties()));
    public static final Supplier<Item> SNIPER = ITEMS.register("sniper", () -> new com.hollowhours.combat.GunItem(
            new com.hollowhours.combat.GunItem.Spec("sniper", 5, 24, 30, 30, false, 1, 0.15f, 128, 60, 9f, 0.25f, () -> SNIPER_AMMO.get()), new Item.Properties().rarity(Rarity.RARE)));
    public static final Supplier<Item> GRENADE = ITEMS.register("grenade", () -> new com.hollowhours.combat.Grenade.GrenadeItem(new Item.Properties()));
    public static final Supplier<Item> COMBAT_KNIFE = ITEMS.register("combat_knife", () -> new com.hollowhours.combat.MeleeWeapon(700, 4, -1.6f, 1.0f, 0, false, new Item.Properties()));
    public static final Supplier<Item> KATANA = ITEMS.register("katana", () -> new com.hollowhours.combat.MeleeWeapon(1300, 7, -2.2f, 1.3f, 0, false, new Item.Properties()));
    public static final Supplier<Item> BATTLE_AXE = ITEMS.register("battle_axe", () -> new com.hollowhours.combat.MeleeWeapon(1100, 9, -3.1f, 1.7f, 0, false, new Item.Properties()));
    public static final Supplier<Item> STUN_BATON = ITEMS.register("stun_baton", () -> new com.hollowhours.combat.MeleeWeapon(900, 3, -2.0f, 2.6f, 0, true, new Item.Properties()));
    public static final Supplier<Item> SPEAR = ITEMS.register("spear", () -> new com.hollowhours.combat.MeleeWeapon(1000, 5, -2.7f, 1.3f, 1.5f, false, new Item.Properties()));
    public static final List<Supplier<Item>> ARMOR = new ArrayList<>();

    static {
        for (com.hollowhours.combat.MilitaryArmor.Mat m : com.hollowhours.combat.MilitaryArmor.Mat.values())
            for (net.minecraft.world.item.ArmorItem.Type t : new net.minecraft.world.item.ArmorItem.Type[]{net.minecraft.world.item.ArmorItem.Type.HELMET,
                    net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS})
                ARMOR.add(ITEMS.register(m.name().toLowerCase(java.util.Locale.ROOT) + "_" + t.getName(),
                        () -> new com.hollowhours.combat.MilitaryArmor.Piece(m, t, new Item.Properties())));
    }

    public static final List<Supplier<Item>> COMBAT = List.of(COMBAT_KNIFE, KATANA, BATTLE_AXE, STUN_BATON, SPEAR, PISTOL, RIFLE, SHOTGUN, SNIPER,
            GRENADE, PISTOL_AMMO, RIFLE_AMMO, SHOTGUN_SHELL, SNIPER_AMMO);

    public static final List<Supplier<Item>> EGGS = new ArrayList<>();

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
