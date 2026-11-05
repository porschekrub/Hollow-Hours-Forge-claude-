package com.hollowhours.grave;

import com.hollowhours.HollowHours;
import com.hollowhours.compat.ItemData;
import com.hollowhours.registry.HHItems;
import com.hollowhours.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import java.util.UUID;

/**
 * Points to your grave. To throw it away: hold right-click for 3 seconds, twice (within 10 seconds).
 */
public class GraveCompassItem extends Item {
    public static final int HOLD = 60, AGAIN_WITHIN = 200;

    public GraveCompassItem(Properties props) {
        super(props);
    }

    static ItemStack forGrave(Graves.Grave g) {
        ItemStack s = new ItemStack(HHItems.GRAVE_COMPASS.get());
        CompoundTag t = new CompoundTag();
        t.putUUID("Grave", g.id);
        t.putLong("Pos", g.pos.asLong());
        t.putString("Dim", g.dim.location().toString());
        t.putString("Owner", g.ownerName);
        ItemData.set(s, t);
        return s;
    }

    @Nullable
    public static UUID graveId(ItemStack s) {
        CompoundTag t = ItemData.tag(s);
        return t != null && t.hasUUID("Grave") ? t.getUUID("Grave") : null;
    }

    /** where the needle points */
    @Nullable
    public static GlobalPos target(ItemStack s) {
        CompoundTag t = ItemData.tag(s);
        if (t == null || !t.contains("Pos")) return null;
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, HollowHours.rl(t.getString("Dim"))), BlockPos.of(t.getLong("Pos")));
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return true;
    }

    @Override
    //#if MC>=12100 && FABRIC
    //$$ public void appendHoverText(ItemStack s, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //$$     Level level = net.minecraftforge.internal.Sides.clientLevel();
    //#elif MC>=12100
    //$$ public void appendHoverText(ItemStack s, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //$$     Level level = tooltipContext.level();
    //#else
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        GlobalPos g = target(s);
        if (g != null) {
            tip.add(Component.translatable("item.hollowhours.grave_compass.pos", g.pos().getX(), g.pos().getY(), g.pos().getZ()).withStyle(ChatFormatting.GRAY));
            if (level != null && !level.dimension().equals(g.dimension()))
                tip.add(Component.translatable("item.hollowhours.grave_compass.dim", g.dimension().location().getPath()).withStyle(ChatFormatting.DARK_PURPLE));
        }
        tip.add(Component.translatable("item.hollowhours.grave_compass.tip").withStyle(ChatFormatting.DARK_GRAY));
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
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity who, ItemStack s, int remaining) {
        if (level.isClientSide || !(who instanceof ServerPlayer p)) return;
        //#if MC>=12100
        //$$ int held = getUseDuration(s, who) - remaining;
        //#else
        int held = getUseDuration(s) - remaining;
        //#endif
        if (held < HOLD) {
            if (held % 20 == 0) Fx.actionbar(p, Component.translatable("item.hollowhours.grave_compass.holding", 3 - held / 20));
            return;
        }
        p.stopUsingItem();
        CompoundTag t = ItemData.get(s);
        long now = level.getGameTime();
        if (t.getLong("FirstHold") > 0 && now - t.getLong("FirstHold") <= AGAIN_WITHIN) {
            level.playSound(null, p.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
            Fx.actionbar(p, Component.translatable("item.hollowhours.grave_compass.destroyed"));
            s.shrink(1);
        } else {
            ItemData.edit(s, nbt -> nbt.putLong("FirstHold", now));
            level.playSound(null, p.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);
            Fx.actionbar(p, Component.translatable("item.hollowhours.grave_compass.again"));
        }
    }
}
