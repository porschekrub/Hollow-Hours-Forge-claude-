package com.hollowhours.item;

import com.hollowhours.ghost.Flashlight;
import com.hollowhours.registry.HHItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Right-click with a flashlight in the other hand (or anywhere in the inventory) = reload it. */
public class BatteryItem extends Item {
    public BatteryItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack it = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) {
            ItemStack other = p.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            ItemStack flash = other.is(HHItems.FLASHLIGHT.get()) ? other : ItemStack.EMPTY;
            if (flash.isEmpty()) for (ItemStack s : p.getInventory().items) if (s.is(HHItems.FLASHLIGHT.get()) && Flashlight.charge(s) < Flashlight.FULL) {
                flash = s;
                break;
            }
            if (!flash.isEmpty()) Flashlight.reload(sp, flash);
        }
        return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
    }

    @Override
    //#if MC>=12100
    //$$ public void appendHoverText(ItemStack it, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //#else
    public void appendHoverText(ItemStack it, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        tip.add(Component.translatable("item.hollowhours.battery.tip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
