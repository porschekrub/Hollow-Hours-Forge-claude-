package com.hollowhours.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Fixes cracks a ghost made in a door/window/gate of the same material (used in the right-click event). */
public class RepairKitItem extends Item {
    public final String fam;

    public RepairKitItem(Properties props, String fam) {
        super(props);
        this.fam = fam;
    }

    @Override
    public void appendHoverText(ItemStack it, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.hollowhours.repair.tip." + fam).withStyle(ChatFormatting.GRAY));
    }
}
