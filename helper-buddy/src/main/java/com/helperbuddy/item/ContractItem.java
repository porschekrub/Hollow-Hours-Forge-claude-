package com.helperbuddy.item;

import com.helperbuddy.Helpers;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.registry.HBRegistry;
import com.helperbuddy.talk.Say;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Use it on the ground: a new helper appears and works for you. */
public class ContractItem extends Item {
    public ContractItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.PASS;
        int max = HBConfig.MAX_HELPERS_PER_PLAYER.get();
        if (Helpers.owned(p).size() >= max) {
            p.sendSystemMessage(Component.literal(Say.t(p, "§6[ผู้ช่วย]§r คุณมีผู้ช่วยครบ " + max + " ตัวแล้ว", "§6[Helper]§r You already have " + max + " helpers")));
            return InteractionResult.FAIL;
        }
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        HelperEntity h = HBRegistry.HELPER.get().create(level);
        if (h == null) return InteractionResult.FAIL;
        h.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot() + 180, 0);
        h.finalizeSpawn((ServerLevel) level, level.getCurrentDifficultyAt(at), MobSpawnType.SPAWN_EGG, null, null);
        HelperEntity.initNew(h, p);
        level.addFreshEntity(h);
        ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER, h.getX(), h.getY() + 1, h.getZ(), 20, 0.4, 0.6, 0.4, 0);
        level.playSound(null, at, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8f, 1.4f);
        if (!p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        Say.to(p, h, "สวัสดีครับ! ผมชื่อ §e" + h.helperName() + "§r เป็นผู้ช่วยของคุณแล้ว คลิกขวาที่ผมเพื่อเปิดเมนู หรือพิมพ์คุยกับผมในแชตโดยขึ้นต้นด้วย §e!§r เช่น §e!ไปตัดไม้",
                "Hi! I'm §e" + h.helperName() + "§r, your new helper. Right-click me for the menu, or talk to me in chat starting with §e!§r, e.g. §e!chop wood");
        CommandBookItem.give(p);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.helperbuddy.helper_contract.tip").withStyle(ChatFormatting.GRAY));
    }
}
