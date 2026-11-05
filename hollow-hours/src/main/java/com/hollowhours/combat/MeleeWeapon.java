package com.hollowhours.combat;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
//#if FABRIC
//$$
//#else
import net.minecraftforge.common.ForgeMod;
//#endif

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Soldier melee weapons. Right-click and hold = guard (blocks most of a hit; guarding just as a ghost swings = a parry
 * that staggers it). Sprint + a full-power swing = a heavy blow. "poise" = how hard it knocks a ghost toward a knock-out.
 */
public class MeleeWeapon extends SwordItem {
    static final UUID REACH = UUID.fromString("6f1c2a3e-1b7d-4f0a-9a51-3c2b8e7d1a10");
    public final float poise, reach;
    public final boolean stuns;
    private Multimap<Attribute, AttributeModifier> extra;

    public MeleeWeapon(int durability, int damage, float speed, float poise, float reach, boolean stuns, Properties p) {
        //#if MC>=12100
        //$$ super(tier(durability), p.attributes(attributes(tier(durability), damage, speed, reach)));
        //#else
        super(tier(durability), damage, speed, p);
        //#endif
        this.poise = poise;
        this.reach = reach;
        this.stuns = stuns;
    }

    static Tier tier(int durability) {
        return new Tier() {
            @Override
            public int getUses() {
                return durability;
            }

            @Override
            public float getSpeed() {
                return 8f;
            }

            @Override
            public float getAttackDamageBonus() {
                return 0;
            }

            //#if MC>=12100
            //$$ @Override
            //$$ public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
            //$$     return net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL;
            //$$ }
            //#else
            @Override
            public int getLevel() {
                return 2;
            }
            //#endif

            @Override
            public int getEnchantmentValue() {
                return 14;
            }

            @Override
            public Ingredient getRepairIngredient() {
                return Ingredient.of(Items.IRON_INGOT);
            }
        };
    }

    //#if MC>=12100
    //$$ /** Sword damage and speed, plus the longer reach of a spear or an axe. */
    //$$ static net.minecraft.world.item.component.ItemAttributeModifiers attributes(Tier tier, int damage, float speed, float reach) {
    //$$     var m = SwordItem.createAttributes(tier, damage, speed);
    //$$     if (reach > 0) m = m.withModifierAdded(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE,
    //$$             new AttributeModifier(com.hollowhours.HollowHours.id("weapon_reach"), reach, AttributeModifier.Operation.ADD_VALUE),
    //$$             net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND);
    //$$     return m;
    //$$ }
    //#else
    @SuppressWarnings("deprecation")
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
        if (slot != EquipmentSlot.MAINHAND || reach <= 0) return base;
        if (extra == null) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.putAll(base);
            //#if FABRIC
            //$$ // Fabric 1.20.1 has no reach attribute: the longer reach is left out
            //#else
            b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH, "weapon reach", reach, AttributeModifier.Operation.ADDITION));
            //#endif
            extra = b.build();
        }
        return extra;
    }
    //#endif

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    //#if MC>=12100
    //$$ public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity user) {
    //#else
    public int getUseDuration(ItemStack stack) {
    //#endif
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (stuns) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    //#if MC>=12100
    //$$ public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //#else
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        tip.add(Component.translatable("hollowhours.weapon.poise", Math.round(poise * 100)).withStyle(ChatFormatting.DARK_PURPLE));
        tip.add(Component.translatable("hollowhours.weapon.melee.hint").withStyle(ChatFormatting.GRAY));
    }

    public static boolean guarding(Player p) {
        return p.isUsingItem() && p.getUseItem().getItem() instanceof MeleeWeapon;
    }
}
