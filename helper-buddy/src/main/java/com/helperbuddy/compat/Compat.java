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

    /** An enchantment's level on a stack (1.21: enchantments are looked up by key). */
    //#if MC>=12100
    //$$ public static int enchant(ItemStack s, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> e) {
    //$$     var reg = registries().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
    //$$     return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(reg.getOrThrow(e), s);
    //$$ }
    //#else
    public static int enchant(ItemStack s, net.minecraft.world.item.enchantment.Enchantment e) {
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(e, s);
    }
    //#endif

    /** Moves an entity to another dimension, at a spot. */
    public static net.minecraft.world.entity.Entity toDimension(net.minecraft.world.entity.Entity e, net.minecraft.server.level.ServerLevel to, net.minecraft.world.phys.Vec3 at) {
        //#if MC>=12100
        //$$ return e.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(to, at, net.minecraft.world.phys.Vec3.ZERO, e.getYRot(), e.getXRot(),
        //$$         net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
        //#elif FABRIC
        //$$ return net.fabricmc.fabric.api.dimension.v1.FabricDimensions.teleport(e, to,
        //$$         new net.minecraft.world.level.portal.PortalInfo(at, net.minecraft.world.phys.Vec3.ZERO, e.getYRot(), e.getXRot()));
        //#else
        return e.changeDimension(to, new net.minecraftforge.common.util.ITeleporter() {
            @Override
            public net.minecraft.world.entity.Entity placeEntity(net.minecraft.world.entity.Entity en, net.minecraft.server.level.ServerLevel cur,
                                                                  net.minecraft.server.level.ServerLevel dest, float yaw,
                                                                  java.util.function.Function<Boolean, net.minecraft.world.entity.Entity> repos) {
                net.minecraft.world.entity.Entity n = repos.apply(false);
                n.moveTo(at.x, at.y, at.z, n.getYRot(), n.getXRot());
                return n;
            }

            @Override
            public boolean playTeleportSound(net.minecraft.server.level.ServerPlayer pl, net.minecraft.server.level.ServerLevel s, net.minecraft.server.level.ServerLevel d) {
                return false;
            }
        });
        //#endif
    }

    public static void ignite(net.minecraft.world.entity.Entity e, int seconds) {
        //#if MC>=12100
        //$$ e.igniteForSeconds(seconds);
        //#else
        e.setSecondsOnFire(seconds);
        //#endif
    }

    /** Hunger points a food gives (0: not food). */
    public static int nutrition(ItemStack s) {
        //#if MC>=12100
        //$$ var f = s.get(net.minecraft.core.component.DataComponents.FOOD);
        //$$ return f == null ? 0 : f.nutrition();
        //#elif FABRIC
        //$$ var f = s.getItem().getFoodProperties();
        //$$ return f == null ? 0 : f.getNutrition();
        //#else
        var f = s.getFoodProperties(null);
        return f == null ? 0 : f.getNutrition();
        //#endif
    }

    public static boolean isFood(ItemStack s) {
        return nutrition(s) > 0 || s.is(net.minecraft.world.item.Items.ROTTEN_FLESH);
    }

    /** What a furnace makes of one of these (empty: nothing). */
    public static ItemStack smelt(net.minecraft.world.level.Level level, ItemStack in) {
        var rm = level.getRecipeManager();
        //#if MC>=12100
        //$$ return rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, new net.minecraft.world.item.crafting.SingleRecipeInput(in.copyWithCount(1)), level)
        //$$         .map(r -> r.value().getResultItem(level.registryAccess()).copy()).orElse(ItemStack.EMPTY);
        //#else
        return rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, new net.minecraft.world.SimpleContainer(in.copyWithCount(1)), level)
                .map(r -> r.getResultItem(level.registryAccess()).copy()).orElse(ItemStack.EMPTY);
        //#endif
    }

    /** Furnace fuel time in ticks. */
    public static int burnTime(ItemStack s) {
        //#if NEOFORGE
        //$$ return s.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);
        //#elif FABRIC
        //$$ Integer t = net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.get(s.getItem());
        //$$ return t == null ? 0 : t;
        //#else
        return net.minecraftforge.common.ForgeHooks.getBurnTime(s, net.minecraft.world.item.crafting.RecipeType.SMELTING);
        //#endif
    }

    /** Sum of the main-hand modifiers of one attribute (attack damage, speed). */
    public static double mainHand(ItemStack s, Object attribute) {
        double[] sum = {0};
        //#if MC>=12100
        //$$ s.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
        //$$         .forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (a, m) -> { if (a.equals(attribute)) sum[0] += m.amount(); });
        //#else
        for (var m : s.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get((net.minecraft.world.entity.ai.attributes.Attribute) attribute))
            sum[0] += m.getAmount();
        //#endif
        return sum[0];
    }

    public static boolean sameItemSameData(ItemStack a, ItemStack b) {
        //#if MC>=12100
        //$$ return ItemStack.isSameItemSameComponents(a, b);
        //#else
        return ItemStack.isSameItemSameTags(a, b);
        //#endif
    }

    /** A tool tier as 1.20's mining level: wood/gold 0, stone 1, iron 2, diamond 3, netherite 4. */
    public static int tierLevel(net.minecraft.world.item.Tier t) {
        //#if MC>=12100
        //$$ if (t instanceof net.minecraft.world.item.Tiers v) return switch (v) {
        //$$     case WOOD, GOLD -> 0;
        //$$     case STONE -> 1;
        //$$     case IRON -> 2;
        //$$     case DIAMOND -> 3;
        //$$     case NETHERITE -> 4;
        //$$ };
        //$$ return 2;
        //#else
        return t.getLevel();
        //#endif
    }

    public static boolean hasCustomName(ItemStack s) {
        //#if MC>=12100
        //$$ return s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        //#else
        return s.hasCustomHoverName();
        //#endif
    }

    // ------------------------------------------------------------------ fishing
    /** What one catch brings up (the vanilla fishing loot, luck of the sea counts). */
    public static java.util.List<ItemStack> fishLoot(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 at, ItemStack rod, float luck) {
        //#if MC>=12100
        //$$ var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.FISHING);
        //#else
        var table = level.getServer().getLootData().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.FISHING);
        //#endif
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, at)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, rod).withLuck(luck)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.FISHING);
        return table.getRandomItems(params);
    }

    /** Luck of the Sea, Lure. */
    public static int fishLuck(ItemStack rod) {
        //#if MC>=12100
        //$$ return enchant(rod, net.minecraft.world.item.enchantment.Enchantments.LUCK_OF_THE_SEA);
        //#else
        return enchant(rod, net.minecraft.world.item.enchantment.Enchantments.FISHING_LUCK);
        //#endif
    }

    public static int fishLure(ItemStack rod) {
        //#if MC>=12100
        //$$ return enchant(rod, net.minecraft.world.item.enchantment.Enchantments.LURE);
        //#else
        return enchant(rod, net.minecraft.world.item.enchantment.Enchantments.FISHING_SPEED);
        //#endif
    }

    // ------------------------------------------------------------------ enchanting
    /** Enchants a copy like an enchanting table would at this level (a book becomes an enchanted book). */
    public static ItemStack enchantAt(net.minecraft.server.level.ServerLevel level, net.minecraft.util.RandomSource r, ItemStack st, int lvl) {
        //#if MC>=12100
        //$$ var tag = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getTag(net.minecraft.tags.EnchantmentTags.IN_ENCHANTING_TABLE);
        //$$ return net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(r, st.copy(), lvl, level.registryAccess(), tag);
        //#else
        return net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(r, st.copy(), lvl, false);
        //#endif
    }

    // ------------------------------------------------------------------ potions
    /** "minecraft:swiftness" of a potion item ("" when it has none). */
    public static String potionId(ItemStack s) {
        //#if MC>=12100
        //$$ var c = s.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        //$$ return c == null ? "" : c.potion().flatMap(h -> h.unwrapKey()).map(k -> k.location().toString()).orElse("");
        //#else
        var p = net.minecraft.world.item.alchemy.PotionUtils.getPotion(s);
        return p == net.minecraft.world.item.alchemy.Potions.EMPTY ? "" : net.minecraft.core.registries.BuiltInRegistries.POTION.getKey(p).toString();
        //#endif
    }

    /** A potion item (potion / splash / lingering) of a potion type; empty when there is no such potion. */
    public static ItemStack potion(net.minecraft.world.item.Item item, String potionId) {
        //#if MC>=12100
        //$$ var h = net.minecraft.core.registries.BuiltInRegistries.POTION.getHolder(com.helperbuddy.HelperBuddy.rl(potionId));
        //$$ return h.map(p -> net.minecraft.world.item.alchemy.PotionContents.createItemStack(item, p)).orElse(ItemStack.EMPTY);
        //#else
        var rl = com.helperbuddy.HelperBuddy.rl(potionId);
        if (!net.minecraft.core.registries.BuiltInRegistries.POTION.containsKey(rl)) return ItemStack.EMPTY;
        return net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(item), net.minecraft.core.registries.BuiltInRegistries.POTION.get(rl));
        //#endif
    }

    /** What brewing this ingredient into this potion makes (empty: nothing). */
    public static ItemStack brew(net.minecraft.world.level.Level level, ItemStack ingredient, ItemStack potion) {
        //#if MC>=12100
        //$$ var pb = level.potionBrewing();
        //$$ return pb.hasMix(potion, ingredient) ? pb.mix(ingredient, potion) : ItemStack.EMPTY;
        //#else
        return net.minecraft.world.item.alchemy.PotionBrewing.hasMix(potion, ingredient) ? net.minecraft.world.item.alchemy.PotionBrewing.mix(ingredient, potion) : ItemStack.EMPTY;
        //#endif
    }

    public static boolean brewIngredient(net.minecraft.world.level.Level level, ItemStack s) {
        //#if MC>=12100
        //$$ return level.potionBrewing().isIngredient(s);
        //#else
        return net.minecraft.world.item.alchemy.PotionBrewing.isIngredient(s);
        //#endif
    }

    /** Every recipe that makes this item. */
    public static java.util.List<net.minecraft.world.item.crafting.Recipe<?>> recipesFor(net.minecraft.server.MinecraftServer server, net.minecraft.world.item.Item item) {
        java.util.List<net.minecraft.world.item.crafting.Recipe<?>> out = new java.util.ArrayList<>();
        //#if MC>=12100
        //$$ for (var holder : server.getRecipeManager().getRecipes()) {
        //$$     var r = holder.value();
        //#else
        for (var r : server.getRecipeManager().getRecipes()) {
        //#endif
            try {
                if (r.getResultItem(server.registryAccess()).is(item)) out.add(r);
            } catch (Exception ignored) {
                // a mod's recipe that cannot tell its result without a grid
            }
        }
        return out;
    }
}
