package com.hollowhours.fun;

import com.hollowhours.registry.HHEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Right-click: throw the ball (a basketball goes up in an arc for a shot, a football is tossed down to play). */
public class BallItem extends Item {
    final int kind;

    public BallItem(Properties props, int kind) {
        super(props);
        this.kind = kind;
    }

    @Override
    //#if MC>=12100
    //$$ public void appendHoverText(ItemStack s, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //#else
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        tip.add(Component.translatable(getDescriptionId() + ".tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack it = p.getItemInHand(hand);
        if (!level.isClientSide) {
            BallEntity ball = HHEntities.BALL.get().create(level);
            if (ball != null) {
                ball.setKind(kind);
                Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
                ball.moveTo(eye.x + look.x * 0.6, eye.y - 0.35 + look.y * 0.4, eye.z + look.z * 0.6, p.getYRot(), 0);
                Vec3 v = kind == BallEntity.BASKETBALL ? look.scale(0.75).add(0, 0.28, 0) : look.scale(0.35).add(0, 0.05, 0);
                ball.setDeltaMovement(v.add(p.getDeltaMovement().multiply(1, 0, 1)));
                ball.touchedBy(p);
                level.addFreshEntity(ball);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.7f);
                if (!p.getAbilities().instabuild) it.shrink(1);
            }
        }
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(it, level.isClientSide);
    }
}
