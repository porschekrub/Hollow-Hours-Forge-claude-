package com.hollowhours.item;

import com.hollowhours.entity.ClosetEntity;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Fx;
import com.hollowhours.util.W;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Places a wardrobe facing you. Needs 2 blocks of free height. */
public class ClosetItem extends Item {
    public ClosetItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        BlockPos cell = W.standCell(level, at.getX() + 0.5, at.getZ() + 0.5, at.getY(), 2, 2);
        if (cell == null || cell.getY() < at.getY() - 1) {
            if (p instanceof ServerPlayer sp) Fx.actionbar(sp, Component.translatable("hollowhours.closet.noroom"));
            return InteractionResult.FAIL;
        }
        Vec3 c = new Vec3(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        if (!level.getEntitiesOfClass(ClosetEntity.class, new AABB(c, c).inflate(0.95)).isEmpty()) return InteractionResult.FAIL;
        ClosetEntity e = HHEntities.CLOSET.get().create(level);
        if (e == null) return InteractionResult.FAIL;
        float yaw = 0;
        if (p != null) {
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            yaw = (float) (Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 90.0) * 90.0);
        }
        e.moveTo(c.x, c.y, c.z, yaw, 0);
        e.setYHeadRot(yaw);
        level.addFreshEntity(e);
        Fx.sound(level, "ghost.closet.close", c, 1f, 0.9f, 16);
        if (p == null || !p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    //#if MC>=12100
    //$$ public void appendHoverText(ItemStack it, net.minecraft.world.item.Item.TooltipContext tooltipContext, List<Component> tip, TooltipFlag flag) {
    //#else
    public void appendHoverText(ItemStack it, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    //#endif
        tip.add(Component.translatable("item.hollowhours.closet_item.tip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
