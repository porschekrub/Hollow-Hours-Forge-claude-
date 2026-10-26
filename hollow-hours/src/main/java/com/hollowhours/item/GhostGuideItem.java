package com.hollowhours.item;

import com.hollowhours.ghost.Guide;
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

/** The ghost guide: who is awake, how to survive, talk to ghosts, ask them for (cursed) items. */
public class GhostGuideItem extends Item {
    public GhostGuideItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack it = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) Guide.open(sp, 0);
        return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack it, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.hollowhours.ghost_guide.tip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack it) {
        return false;
    }
}
