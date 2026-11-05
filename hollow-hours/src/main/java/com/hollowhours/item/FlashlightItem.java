package com.hollowhours.item;

import com.hollowhours.ghost.Flashlight;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Right-click: on/off. Sneak + right-click: put in a new battery. The bar shows the battery. */
public class FlashlightItem extends Item {
    public static final int DUR_SPAN = 100;

    public FlashlightItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack it = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) Flashlight.use(sp, it);
        return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack it) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack it) {
        return Math.round(13f * Flashlight.charge(it) / Flashlight.FULL);
    }

    @Override
    public int getBarColor(ItemStack it) {
        float f = Flashlight.charge(it) / (float) Flashlight.FULL;
        return Mth.hsvToRgb(Math.max(0f, f) / 3f, 1f, 1f);
    }

    @Override
    public boolean isFoil(ItemStack it) {
        return Flashlight.on(it) && Flashlight.charge(it) > 0;
    }

    @Override
    public boolean isValidRepairItem(ItemStack it, ItemStack repair) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack it) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    //#if MC>=12100
    //$$ public void appendHoverText(ItemStack it, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //#else
    public void appendHoverText(ItemStack it, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        int sec = Flashlight.charge(it) / 20;
        tip.add(Component.translatable("item.hollowhours.flashlight.charge", sec / 60, String.format("%02d", sec % 60)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.hollowhours.flashlight.tip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
