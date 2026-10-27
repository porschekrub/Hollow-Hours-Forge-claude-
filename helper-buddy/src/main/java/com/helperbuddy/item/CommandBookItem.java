package com.helperbuddy.item;

import com.helperbuddy.net.HBNet;
import com.helperbuddy.registry.HBRegistry;
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

/** Orders helpers from anywhere: lists them, opens their menu, sends them items. */
public class CommandBookItem extends Item {
    public CommandBookItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (!level.isClientSide && p instanceof ServerPlayer sp) HBNet.openBook(sp);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }

    /** Gives the book unless the player already has one. */
    public static boolean give(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (s.is(HBRegistry.COMMAND_BOOK.get())) return true;
        return p.getInventory().add(new ItemStack(HBRegistry.COMMAND_BOOK.get()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.helperbuddy.command_book.tip").withStyle(ChatFormatting.GRAY));
    }
}
