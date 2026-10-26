package com.hollowhours.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A board to nail over a door or window (the nailing itself is handled in the right-click event). */
public class BoardItem extends Item {
    public final int mat;

    public BoardItem(Properties props, int mat) {
        super(props);
        this.mat = mat;
    }

    @Override
    public void appendHoverText(ItemStack it, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.hollowhours.board.tip." + mat).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.hollowhours.board.tip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
