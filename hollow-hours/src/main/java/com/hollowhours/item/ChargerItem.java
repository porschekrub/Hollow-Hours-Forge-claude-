package com.hollowhours.item;

import com.hollowhours.compat.Compat;
import com.hollowhours.compat.ItemData;
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

/**
 * A power bank with a cable. Hold right-click: you plug the cable into your emptiest phone, then watch its battery
 * climb (2% a tick) until it's full or you let go. Three charges, then the power bank is flat.
 */
public class ChargerItem extends Item {
    /** ticks of plugging the cable in before the charge starts */
    public static final int PLUG = 14;
    static final float PER_TICK = 2;
    static final String PLUGGED = "Plugged";

    public ChargerItem(Properties props) {
        super(props);
    }

    /** the phone to charge: one in a hand first, else the emptiest one */
    @Nullable
    public static ItemStack target(Player p) {
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
        if (!level.isClientSide && p instanceof ServerPlayer sp) Fx.playSelf(sp, "phone.click", 0.8f, 0.7f);
        return InteractionResultHolder.consume(it);
    }

    @Override
    public void onUseTick(Level level, LivingEntity who, ItemStack s, int remaining) {
        if (level.isClientSide || !(who instanceof ServerPlayer p)) return;
        //#if MC>=12100
        //$$ int held = getUseDuration(s, who) - remaining;
        //#else
        int held = getUseDuration(s) - remaining;
        //#endif
        ItemStack phone = target(p);
        if (held == PLUG) {
            if (phone == null) {
                p.stopUsingItem();
                return;
            }
            // plugged in: this is one of the power bank's three charges
            ItemData.edit(s, nbt -> nbt.putBoolean(PLUGGED, true));
            PhoneItem.setCharging(phone, true);
            Fx.playSelf(p, "phone.charge", 0.8f, 1f);
        }
        if (held <= PLUG) return;
        if (phone == null) {
            done(p, s, true);
            return;
        }
        PhoneItem.setCharging(phone, true);
        float b = Math.min(100, PhoneItem.battery(phone) + PER_TICK);
        PhoneItem.setBattery(phone, b);
        if (held % 5 == 0) p.displayClientMessage(Component.translatable("item.hollowhours.charger.charging", Math.round(b)).withStyle(ChatFormatting.GREEN), true);
        if (b >= 100) done(p, s, true);
    }

    /** Unplugged (full, or you let go): the charge is used up; tell the player. */
    static void done(ServerPlayer p, ItemStack s, boolean full) {
        if (ItemData.get(s).getBoolean(PLUGGED)) {
            ItemData.remove(s, PLUGGED);
            int left = s.getMaxDamage() - s.getDamageValue() - 1;
            if (full) Fx.playSelf(p, "phone.notify", 0.6f, 1.3f);
            ItemStack phone = null;
            for (ItemStack it : p.getInventory().items) if (it.is(HHItems.PHONE.get()) && PhoneItem.charging(it)) phone = it;
            if (PhoneItem.charging(p.getOffhandItem())) phone = p.getOffhandItem();
            net.minecraft.network.chat.MutableComponent msg = full ? Component.translatable("item.hollowhours.charger.done", Math.max(0, left))
                    : Component.translatable("item.hollowhours.charger.unplugged", phone == null ? "?" : Math.round(PhoneItem.battery(phone)), Math.max(0, left));
            p.displayClientMessage(msg.withStyle(ChatFormatting.GREEN), true);
            InteractionHand hand = p.getUsedItemHand();
            if (p.isUsingItem()) p.stopUsingItem();
            Compat.hurtItem(s, 1, p, hand);
        } else if (p.isUsingItem()) p.stopUsingItem();
        for (ItemStack it : p.getInventory().items) if (it.is(HHItems.PHONE.get())) PhoneItem.setCharging(it, false);
        for (ItemStack it : p.getInventory().offhand) if (it.is(HHItems.PHONE.get())) PhoneItem.setCharging(it, false);
    }

    @Override
    public void releaseUsing(ItemStack s, Level level, LivingEntity who, int remaining) {
        if (!level.isClientSide && who instanceof ServerPlayer p) done(p, s, false);
    }

    @Override
    //#if MC>=12100
    //$$ public int getUseDuration(ItemStack s, net.minecraft.world.entity.LivingEntity user) {
    //#else
    public int getUseDuration(ItemStack s) {
    //#endif
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.NONE;
    }

    @Override
    //#if MC>=12100
    //$$ public void appendHoverText(ItemStack s, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //#else
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        tip.add(Component.translatable("item.hollowhours.charger.left", s.getMaxDamage() - s.getDamageValue()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.hollowhours.charger.tip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
