package com.hollowhours.combat;

import com.hollowhours.HollowHours;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Three soldier armour sets. Every piece protects like normal armour; wearing pieces of one set also makes ghost hits
 * softer and struggles in a ghost's grip easier (a full set gives the whole bonus).
 */
public final class MilitaryArmor {
    private MilitaryArmor() {}

    public enum Mat
    //#if MC<12100
            implements ArmorMaterial
    //#endif
    {
        TACTICAL("tactical", 22, new int[]{2, 6, 5, 2}, 12, 1.0f, 0.0f, 0.15f, 0.10f, () -> Ingredient.of(Items.IRON_INGOT)),
        HEAVY("heavy", 33, new int[]{3, 8, 6, 3}, 10, 2.5f, 0.10f, 0.25f, 0.20f, () -> Ingredient.of(Items.IRON_INGOT)),
        HUNTER("hunter", 38, new int[]{3, 8, 6, 3}, 15, 3.5f, 0.15f, 0.40f, 0.30f, () -> Ingredient.of(Items.AMETHYST_SHARD));

        final String name;
        final int dur;
        final int[] def; // helmet, chestplate, leggings, boots
        final int ench;
        final float tough, kb;
        public final float struggle, ghostCut;
        final Supplier<Ingredient> repair;

        Mat(String name, int dur, int[] def, int ench, float tough, float kb, float struggle, float ghostCut, Supplier<Ingredient> repair) {
            this.name = name;
            this.dur = dur;
            this.def = def;
            this.ench = ench;
            this.tough = tough;
            this.kb = kb;
            this.struggle = struggle;
            this.ghostCut = ghostCut;
            this.repair = repair;
        }

        static int idx(ArmorItem.Type t) {
            return switch (t) {
                case HELMET -> 0;
                case LEGGINGS -> 2;
                case BOOTS -> 3;
                default -> 1;
            };
        }

        //#if MC>=12100
        //$$ private net.minecraft.core.Holder<ArmorMaterial> holder;
        //$$
        //$$ /** The armour material (Minecraft 1.21 keeps materials apart from the items). */
        //$$ public net.minecraft.core.Holder<ArmorMaterial> holder() {
        //$$     if (holder == null) {
        //$$         java.util.EnumMap<ArmorItem.Type, Integer> d = new java.util.EnumMap<>(ArmorItem.Type.class);
        //$$         for (ArmorItem.Type t : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS})
        //$$             d.put(t, def[idx(t)]);
        //$$         d.put(ArmorItem.Type.BODY, def[1]);
        //$$         holder = net.minecraft.core.Holder.direct(new ArmorMaterial(d, ench,
        //$$                 this == TACTICAL ? SoundEvents.ARMOR_EQUIP_LEATHER : SoundEvents.ARMOR_EQUIP_IRON, repair,
        //$$                 List.of(new ArmorMaterial.Layer(HollowHours.id(name))), tough, kb));
        //$$     }
        //$$     return holder;
        //$$ }
        //#else
        @Override
        public int getDurabilityForType(ArmorItem.Type t) {
            return new int[]{11, 16, 15, 13}[idx(t)] * dur;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type t) {
            return def[idx(t)];
        }

        @Override
        public int getEnchantmentValue() {
            return ench;
        }

        @Override
        public SoundEvent getEquipSound() {
            return this == TACTICAL ? SoundEvents.ARMOR_EQUIP_LEATHER : SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return repair.get();
        }

        @Override
        public String getName() {
            return HollowHours.MODID + ":" + name;
        }

        @Override
        public float getToughness() {
            return tough;
        }

        @Override
        public float getKnockbackResistance() {
            return kb;
        }
        //#endif
    }

    public static class Piece extends ArmorItem {
        public final Mat mat;

        public Piece(Mat mat, Type type, Properties p) {
            //#if MC>=12100
            //$$ super(mat.holder(), type, p.durability(type.getDurability(mat.dur)));
            //#else
            super(mat, type, p);
            //#endif
            this.mat = mat;
        }

        @Override
        //#if MC>=12100
        //$$ public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
        //#else
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        //#endif
            tip.add(Component.translatable("hollowhours.armor.set." + mat.name).withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("hollowhours.armor.bonus", Math.round(mat.ghostCut * 100), Math.round(mat.struggle * 100)).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Pieces worn, as a fraction of a full set, per bonus: ghost damage cut and struggle bonus (both 0..). */
    public static float ghostCut(Player p) {
        float v = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (p.getItemBySlot(s).getItem() instanceof Piece pc) v += pc.mat.ghostCut / 4f;
        return v;
    }

    public static float struggle(Player p) {
        float v = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (p.getItemBySlot(s).getItem() instanceof Piece pc) v += pc.mat.struggle / 4f;
        return v;
    }
}
