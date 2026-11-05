package com.hollowhours.grave;

import com.hollowhours.compat.Compat;
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

/** Brings back what your newest broken or crumbled grave took with it. 3 uses. */
public class RestorerItem extends Item {
    public RestorerItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (level.isClientSide || !(p instanceof ServerPlayer sp)) return InteractionResultHolder.success(s);
        if (!Graves.restore(sp)) {
            sp.displayClientMessage(Component.translatable("item.hollowhours.restorer.none"), true);
            return InteractionResultHolder.fail(s);
        }
        if (!sp.getAbilities().instabuild) Compat.hurtItem(s, 1, sp, hand);
        sp.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.consume(s);
    }

    @Override
    //#if MC>=12100
    //$$ public void appendHoverText(ItemStack s, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //#else
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        tip.add(Component.translatable("item.hollowhours.restorer.tip").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.hollowhours.restorer.left", s.getMaxDamage() - s.getDamageValue()).withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return true;
    }
}
