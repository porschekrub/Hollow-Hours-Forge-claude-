package com.hollowhours.item;

import com.hollowhours.compat.ItemData;
import com.hollowhours.registry.HHItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's phone. Battery 0-100 (random when you get it), drains a little in your pocket, more with the screen
 * on and a lot while gaming. The more hours it has been used, the faster it drains (battery wear). Charge it with a
 * charger (3 charges each). Apps: messages (the emergency alert), games (they calm you down: sanity), health.
 */
public class PhoneItem extends Item {
    public static final String BAT = "battery", WEAR = "wear", MSGS = "messages";

    public PhoneItem(Properties props) {
        super(props);
    }

    public static ItemStack fresh(RandomSource r) {
        ItemStack s = new ItemStack(HHItems.PHONE.get());
        CompoundTag t = new CompoundTag();
        t.putFloat(BAT, 15 + r.nextInt(71));
        t.putFloat(WEAR, r.nextInt(40));
        ListTag msgs = new ListTag();
        msgs.add(StringTag.valueOf("hollowhours.phone.msg.mom"));
        msgs.add(StringTag.valueOf("hollowhours.phone.msg.friend"));
        t.put(MSGS, msgs);
        ItemData.set(s, t);
        return s;
    }

    public static float battery(ItemStack s) {
        CompoundTag t = ItemData.tag(s);
        return t != null && t.contains(BAT) ? t.getFloat(BAT) : 50;
    }

    public static void setBattery(ItemStack s, float v) {
        ItemData.edit(s, nbt -> nbt.putFloat(BAT, Mth.clamp(v, 0, 100)));
    }

    /** plugged into a power bank right now (shows the charging screen) */
    public static boolean charging(ItemStack s) {
        return ItemData.get(s).getBoolean("Charging");
    }

    public static void setCharging(ItemStack s, boolean on) {
        if (on) ItemData.edit(s, nbt -> nbt.putBoolean("Charging", true));
        else ItemData.remove(s, "Charging");
    }

    /** Minutes of screen time so far. */
    public static float wear(ItemStack s) {
        CompoundTag t = ItemData.tag(s);
        return t != null ? t.getFloat(WEAR) : 0;
    }

    public static void addWear(ItemStack s, float minutes) {
        float w = wear(s) + minutes;
        ItemData.edit(s, nbt -> nbt.putFloat(WEAR, w));
    }

    /** minutes of use after which the battery drains twice as fast */
    public static final float WEAR_DOUBLE = 180;

    /** Drain multiplier from wear: twice as fast after 3 hours of use, three times after 6 hours. */
    public static float drainMult(ItemStack s) {
        return 1 + wear(s) / WEAR_DOUBLE;
    }

    /** Battery health shown in the settings app. */
    public static int health(ItemStack s) {
        return Math.max(20, Math.round(100 - wear(s) / WEAR_DOUBLE * 50));
    }

    public static List<String> messages(ItemStack s) {
        List<String> out = new ArrayList<>();
        CompoundTag t = ItemData.tag(s);
        if (t == null || !t.contains(MSGS)) return out;
        for (Tag e : t.getList(MSGS, Tag.TAG_STRING)) out.add(e.getAsString());
        return out;
    }

    /** Adds a message (translation key) to every phone the player carries. */
    public static void addMessage(ServerPlayer p, String key) {
        for (ItemStack s : allPhones(p)) {
            ListTag l = ItemData.get(s).getList(MSGS, Tag.TAG_STRING);
            boolean has = false;
            for (Tag e : l) if (e.getAsString().equals(key)) has = true;
            if (!has) l.add(StringTag.valueOf(key));
            ItemData.edit(s, nbt -> nbt.put(MSGS, l));
        }
    }

    /** Adds a message with arguments ("key|arg|arg"), replacing an older one with the same key. */
    public static void setMessage(ServerPlayer p, String key, String full) {
        for (ItemStack s : allPhones(p)) {
            ListTag l = ItemData.get(s).getList(MSGS, Tag.TAG_STRING);
            l.removeIf(e -> e.getAsString().equals(key) || e.getAsString().startsWith(key + "|"));
            l.add(StringTag.valueOf(full));
            ItemData.edit(s, nbt -> nbt.put(MSGS, l));
        }
    }

    static List<ItemStack> allPhones(Player p) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : p.getInventory().items) if (s.is(HHItems.PHONE.get())) out.add(s);
        for (ItemStack s : p.getInventory().offhand) if (s.is(HHItems.PHONE.get())) out.add(s);
        return out;
    }

    /** The phone in use: main hand, off hand, then the pocket. charged = only one with battery left. */
    @Nullable
    public static ItemStack find(Player p, boolean charged) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.is(HHItems.PHONE.get()) && (!charged || battery(s) > 0)) return s;
        }
        for (ItemStack s : allPhones(p)) if (!charged || battery(s) > 0) return s;
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack it = p.getItemInHand(hand);
        if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.hollowhours.client.PhoneScreen.open(hand));
        return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean selected) {
        if (level.isClientSide || !(e instanceof Player p)) return;
        // a charging mark left behind (the power bank was let go some other way)
        if (charging(s) && !(p.isUsingItem() && p.getUseItem().is(HHItems.CHARGER.get()))) setCharging(s, false);
        if (!ItemData.get(s).contains(BAT)) {
            ItemStack f = fresh(p.getRandom());
            ItemData.set(s, ItemData.tag(f));
            return;
        }
        // standby: about 7 hours of real time from full when nobody touches it
        if (p.tickCount % 100 == slot % 100 && battery(s) > 0)
            setBattery(s, battery(s) - 0.012f * drainMult(s) * com.hollowhours.config.HHConfig.PHONE_DRAIN.get().floatValue());
    }

    @Override
    public boolean isBarVisible(ItemStack s) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack s) {
        return Math.round(13 * battery(s) / 100f);
    }

    @Override
    public int getBarColor(ItemStack s) {
        float f = battery(s) / 100f;
        return Mth.hsvToRgb(f / 3f, 1f, 1f);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    //#if MC>=12100
    //$$ public void appendHoverText(ItemStack s, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //#else
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        int b = Math.round(battery(s));
        tip.add(Component.translatable("item.hollowhours.phone.battery", b).withStyle(b > 20 ? ChatFormatting.GREEN : ChatFormatting.RED));
        tip.add(Component.translatable("item.hollowhours.phone.health", health(s)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.hollowhours.phone.tip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
