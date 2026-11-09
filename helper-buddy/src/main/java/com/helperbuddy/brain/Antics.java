package com.helperbuddy.brain;

import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.talk.Persona;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What a sassy / rude helper gets up to on its own: when a Hollow Hours ghost comes it may dig a hole and hide instead
 * of helping, it gets bored when it has nothing to do, it says things when its owner dies or pokes it.
 */
public final class Antics {
    final Brain b;
    final HelperEntity h;
    /** ghosts it already decided about (one roll each) */
    final Set<UUID> rolled = new HashSet<>();
    // hiding
    BlockPos hole, cover;
    BlockState coverWas;
    long hideUntil, clearSince;
    long lastOrder;

    Antics(Brain b) {
        this.b = b;
        this.h = b.h;
    }

    public boolean hiding() {
        return hole != null;
    }

    public void onOrder() {
        lastOrder = h.level().getGameTime();
    }

    static boolean isGhost(Entity e) {
        return e.isAlive() && e.getType().getCategory() == MobCategory.MONSTER && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("hollowhours");
    }

    /** @return true while it is busy with a trick (the rest of the brain waits) */
    boolean tick() {
        long now = h.level().getGameTime();
        if (hole != null) {
            tickHide(now);
            return true;
        }
        if (b.t % 20 != 7) return false;
        Persona.Style st = Persona.of(h);
        if (st != Persona.Style.POLITE && HBConfig.TROLLING.get()) {
            for (Entity e : h.level().getEntities(h, h.getBoundingBox().inflate(14), Antics::isGhost)) {
                if (!rolled.add(e.getUUID())) continue;
                if (rolled.size() > 64) rolled.clear();
                if (h.getRandom().nextFloat() < HBConfig.TROLL_CHANCE.get() && startHide()) return true;
            }
        }
        // bored
        ServerPlayer o = h.owner();
        if (st != Persona.Style.POLITE && o != null && o.level() == h.level() && o.distanceTo(h) < 16 && (h.mode() == Mode.FOLLOW || h.mode() == Mode.STAY)
                && now - lastOrder > 20 * 60 * 6 && h.getRandom().nextInt(400) == 0 && b.cooldown("idle", 20 * 60 * 5))
            quip("idle");
        return false;
    }

    public void quip(String event) {
        String[] q = Persona.quip(Persona.of(h), event, Persona.R);
        ServerPlayer o = h.owner();
        if (q != null && o != null) Say.raw(o, h, Say.t(o, q[0], q[1]));
    }

    // ------------------------------------------------------------------ hiding from ghosts
    boolean startHide() {
        if (!(h.level() instanceof ServerLevel sl) || !h.onGround() || h.isKO() || h.isPassenger()) return false;
        BlockPos at = h.blockPosition();
        BlockPos down1 = at.below(), down2 = at.below(2);
        if (!diggable(sl, down1) || !diggable(sl, down2) || !sl.getBlockState(at.below(3)).isFaceSturdy(sl, at.below(3), net.minecraft.core.Direction.UP)) return false;
        quip("ghostRun");
        b.mover().stop();
        b.breaker().cancel();
        sl.destroyBlock(down1, true, h);
        sl.destroyBlock(down2, true, h);
        h.teleportTo(down2.getX() + 0.5, down2.getY(), down2.getZ() + 0.5);
        h.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        // a lid over its head (from its bag if it has some dirt or stone)
        BlockState lid = Blocks.DIRT.defaultBlockState();
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.getItem() instanceof BlockItem bi && (s.is(net.minecraft.world.item.Items.COBBLESTONE) || s.is(net.minecraft.world.item.Items.DIRT)
                    || s.is(net.minecraft.world.item.Items.COBBLED_DEEPSLATE))) {
                lid = bi.getBlock().defaultBlockState();
                s.shrink(1);
                break;
            }
        }
        coverWas = sl.getBlockState(at);
        if (coverWas.canBeReplaced()) {
            sl.setBlock(at, lid, 3);
            cover = at;
        }
        sl.playSound(null, at, SoundEvents.GRAVEL_BREAK, SoundSource.NEUTRAL, 1f, 0.8f);
        hole = down2;
        hideUntil = h.level().getGameTime() + 20 * 60;
        clearSince = -1;
        return true;
    }

    static boolean diggable(ServerLevel sl, BlockPos p) {
        BlockState s = sl.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && s.getDestroySpeed(sl, p) >= 0 && s.getDestroySpeed(sl, p) < 30 && sl.getBlockEntity(p) == null;
    }

    void tickHide(long now) {
        b.mover().stop();
        if (h.blockPosition().distSqr(hole) > 2) h.teleportTo(hole.getX() + 0.5, hole.getY(), hole.getZ() + 0.5);
        if (b.t % 20 != 0) return;
        boolean ghost = !h.level().getEntities(h, h.getBoundingBox().inflate(24), Antics::isGhost).isEmpty();
        if (ghost) clearSince = -1;
        else if (clearSince < 0) clearSince = now;
        if (now < hideUntil && (ghost || now - clearSince < 20 * 8)) {
            if (h.level() instanceof ServerLevel sl && h.getRandom().nextInt(3) == 0)
                sl.sendParticles(ParticleTypes.SMOKE, hole.getX() + 0.5, hole.getY() + 2.2, hole.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0);
            return;
        }
        endHide();
    }

    public void endHide() {
        if (hole == null) return;
        if (h.level() instanceof ServerLevel sl) {
            if (cover != null && !sl.getBlockState(cover).isAir()) sl.destroyBlock(cover, false, h);
            BlockPos up = hole.above(2);
            h.teleportTo(up.getX() + 0.5, up.getY(), up.getZ() + 0.5);
            // fill the hole back in, it's a tidy coward
            sl.setBlock(hole, Blocks.DIRT.defaultBlockState(), 3);
            sl.setBlock(hole.above(), Blocks.DIRT.defaultBlockState(), 3);
        }
        hole = null;
        cover = null;
        quip("ghostBack");
    }
}
