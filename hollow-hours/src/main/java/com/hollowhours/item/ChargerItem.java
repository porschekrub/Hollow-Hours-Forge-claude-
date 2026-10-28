package com.hollowhours.item;

import com.hollowhours.registry.HHItems;
import com.hollowhours.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Hold right-click for 2 seconds: the emptiest phone you carry is charged to 100%. Three charges, then it's used up. */
public class ChargerItem extends Item {
    public ChargerItem(Properties props) {
        super(props);
    }

    @Nullable
    static ItemStack target(Player p) {
        ItemStack best = null;
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.is(HHItems.PHONE.get()) && PhoneItem.battery(s) < 99.5f) best = s;
        }
        if (best != null) return best;
        for (ItemStack s : p.getInventory().items)
            if (s.is(HHItems.PHONE.get()) && PhoneItem.battery(s) < 99.5f && (best == null || PhoneItem.battery(s) < PhoneItem.battery(best))) best = s;
        return best;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack it = p.getItemInHand(hand);
        if (target(p) == null) {
            if (!level.isClientSide) p.displayClientMessage(Component.translatable("item.hollowhours.charger.nothing").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(it);
        }
        p.startUsingItem(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) Fx.playSelf(sp, "phone.charge", 0.8f, 1f);
        return InteractionResultHolder.consume(it);
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 40;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level level, LivingEntity e) {
        if (!level.isClientSide && e instanceof ServerPlayer p) {
            ItemStack phone = target(p);
            if (phone != null) {
                PhoneItem.setBattery(phone, 100);
                Fx.playSelf(p, "phone.notify", 0.6f, 1.3f);
                int left = s.getMaxDamage() - s.getDamageValue() - 1;
                p.displayClientMessage(Component.translatable("item.hollowhours.charger.done", Math.max(0, left)).withStyle(ChatFormatting.GREEN), true);
                s.hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(p.getUsedItemHand()));
            }
        }
        return s;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.hollowhours.charger.left", s.getMaxDamage() - s.getDamageValue()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.hollowhours.charger.tip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
