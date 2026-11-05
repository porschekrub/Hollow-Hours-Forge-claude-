package com.hollowhours.item;

import com.hollowhours.compat.ItemData;
import net.minecraft.nbt.CompoundTag;
import com.hollowhours.ghost.Thirst;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A canteen: 3 drinks, each 15% more than a water bottle. Right-click water to fill it up again.
 */
public class CanteenItem extends Item {
    public static final int SIPS = 3;
    /** a water bottle quenches 7: the canteen 15% more */
    public static final float PER_SIP = 7 * 1.15f;

    public CanteenItem(Properties props) {
        super(props);
    }

    /** sips left (a new canteen is full) */
    public static int sips(ItemStack s) {
        CompoundTag t = ItemData.get(s);
        return t.contains("Sips") ? t.getInt("Sips") : SIPS;
    }

    static void setSips(ItemStack s, int n) {
        ItemData.edit(s, nbt -> nbt.putInt("Sips", Math.max(0, Math.min(SIPS, n))));
    }

    /** the water source block the player is looking at, if any */
    static boolean lookingAtWater(Level level, Player p) {
        BlockHitResult hit = getPlayerPOVHitResult(level, p, ClipContext.Fluid.SOURCE_ONLY);
        return hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (sips(s) < SIPS && lookingAtWater(level, p)) {
            level.playSound(p, p.getX(), p.getY(), p.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1f, 0.9f);
            if (!level.isClientSide) setSips(s, SIPS);
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }
        if (sips(s) <= 0) {
            if (!level.isClientSide) p.displayClientMessage(Component.translatable("item.hollowhours.canteen.empty"), true);
            return InteractionResultHolder.fail(s);
        }
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level level, LivingEntity who) {
        if (!level.isClientSide && who instanceof ServerPlayer p) {
            Thirst.drink(p, PER_SIP);
            if (!p.getAbilities().instabuild) setSips(s, sips(s) - 1);
        }
        return s;
    }

    @Override
    //#if MC>=12100
    //$$ public int getUseDuration(ItemStack s, net.minecraft.world.entity.LivingEntity user) {
    //#else
    public int getUseDuration(ItemStack s) {
    //#endif
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.DRINK;
    }

    @Override
    public boolean isBarVisible(ItemStack s) {
        return sips(s) < SIPS;
    }

    @Override
    public int getBarWidth(ItemStack s) {
        return Math.round(13f * sips(s) / SIPS);
    }

    @Override
    public int getBarColor(ItemStack s) {
        return 0x3A8FE0;
    }

    @Override
    //#if MC>=12100
    //$$ public void appendHoverText(ItemStack s, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //#else
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        tip.add(Component.translatable("item.hollowhours.canteen.sips", sips(s), SIPS).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("item.hollowhours.canteen.tip").withStyle(ChatFormatting.GRAY));
    }
}
