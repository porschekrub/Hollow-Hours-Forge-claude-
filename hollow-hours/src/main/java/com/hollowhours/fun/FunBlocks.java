package com.hollowhours.fun;

import com.hollowhours.ghost.Sanity;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * The playground and the living room, to keep your mind together:
 * swing (sit and swing), slide (whee), trampoline (bounce higher and higher), basketball hoop and soccer goal (score
 * with a ball), arcade machine (the phone's games, no battery needed) and a TV (watching it calms you).
 */
public final class FunBlocks {
    private FunBlocks() {}

    /** Sanity for playing, at most once per `cooldown` ticks per player and kind. */
    static void reward(Player p, String kind, float amount, int cooldown) {
        if (!(p instanceof ServerPlayer sp)) return;
        var d = sp.getPersistentData();
        long now = sp.serverLevel().getGameTime();
        String key = "hollowhours_fun_" + kind;
        if (now - d.getLong(key) < cooldown) return;
        d.putLong(key, now);
        Sanity.add(sp, amount);
    }

    // ------------------------------------------------------------------ swing
    /** someone is on the swing: the seat is drawn by its entity, swinging, instead of hanging still in the block model */
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");

    public static class Swing extends FunBlock {
        public Swing(Properties p) {
            super(p, 0, 0, 3, 16, 16, 13);
            registerDefaultState(defaultBlockState().setValue(OCCUPIED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            super.createBlockStateDefinition(b);
            b.add(OCCUPIED);
        }

        @Override
        public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (p.isPassenger()) return InteractionResult.PASS;
            for (Entity e : level.getEntitiesOfClass(SwingSeatEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(0.5, 2, 0.5))) if (e.isVehicle()) return InteractionResult.CONSUME;
            SwingSeatEntity seat = HHEntities.SWING_SEAT.get().create(level);
            if (seat == null) return InteractionResult.PASS;
            seat.setup(pos, s.getValue(FACING));
            level.addFreshEntity(seat);
            p.startRiding(seat, true);
            level.setBlock(pos, s.setValue(OCCUPIED, true), 3);
            return InteractionResult.CONSUME;
        }
    }

    // ------------------------------------------------------------------ slide
    public static class Slide extends FunBlock {
        public Slide(Properties p) {
            super(p, 2, 0, 0, 14, 16, 16);
        }

        @Override
        public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
            Direction f = s.getValue(FACING);
            // climb on at the back, slide down the front
            p.teleportTo(pos.getX() + 0.5 - f.getStepX() * 0.3, pos.getY() + 1.0, pos.getZ() + 0.5 - f.getStepZ() * 0.3);
            p.setDeltaMovement(f.getStepX() * 1.1, 0.15, f.getStepZ() * 1.1);
            p.hurtMarked = true;
            p.fallDistance = 0;
            if (!level.isClientSide) {
                level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 0.8f, 1.6f);
                reward(p, "slide", 5, 60);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    // ------------------------------------------------------------------ trampoline
    public static class Trampoline extends FunBlock {
        public Trampoline(Properties p) {
            super(p, 0, 0, 0, 16, 7, 16);
        }

        @Override
        public void fallOn(Level level, BlockState s, BlockPos pos, Entity e, float dist) {
            e.causeFallDamage(dist, 0.0f, level.damageSources().fall());
        }

        @Override
        public void updateEntityAfterFallOn(BlockGetter level, Entity e) {
            if (e.isSuppressingBounce()) {
                super.updateEntityAfterFallOn(level, e);
                return;
            }
            Vec3 v = e.getDeltaMovement();
            if (v.y < 0) {
                // a little higher every bounce, up to about 5 blocks
                double up = Math.min(0.95, -v.y * 0.9 + 0.35);
                e.setDeltaMovement(v.x, up, v.z);
                if (e instanceof Player p && !p.level().isClientSide) {
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_JUMP, SoundSource.BLOCKS, 0.6f, 1.3f);
                    reward(p, "bounce", 1.5f, 10);
                }
            }
        }
    }

    // ------------------------------------------------------------------ hoop / goal (scoring is done by the ball)
    public static class Hoop extends FunBlock {
        public Hoop(Properties p) {
            super(p, 6, 0, 10, 10, 16, 16);
        }
    }

    public static class Goal extends FunBlock {
        public Goal(Properties p) {
            super(p, 0, 0, 6, 16, 16, 16);
        }

        @Override
        public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext ctx) {
            // the back net only: balls (and players) go in
            Direction f = s.getValue(FACING);
            return switch (f) {
                case NORTH -> Block.box(0, 0, 15, 16, 16, 16);
                case SOUTH -> Block.box(0, 0, 0, 16, 16, 1);
                case WEST -> Block.box(15, 0, 0, 16, 16, 16);
                default -> Block.box(0, 0, 0, 1, 16, 16);
            };
        }
    }

    // ------------------------------------------------------------------ arcade
    public static class Arcade extends FunBlock {
        public Arcade(Properties p) {
            super(p, 1, 0, 2, 15, 16, 15);
        }

        @Override
        public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.hollowhours.client.PhoneScreen.openArcade(pos));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    // ------------------------------------------------------------------ TV
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static class Tv extends FunBlock implements EntityBlock {
        public Tv(Properties p) {
            super(p, 1, 0, 4, 15, 13, 12);
            registerDefaultState(defaultBlockState().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            super.createBlockStateDefinition(b);
            b.add(LIT);
        }

        @Override
        public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide) {
                boolean on = !s.getValue(LIT);
                level.setBlock(pos, s.setValue(LIT, on), 3);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, on ? 1.4f : 0.9f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState s) {
            return new TvBlockEntity(pos, s);
        }
    }

    public static class TvBlockEntity extends BlockEntity {
        public TvBlockEntity(BlockPos pos, BlockState s) {
            super(com.hollowhours.registry.HHBlocks.TV_BE.get(), pos, s);
        }
    }

    /** The ball went in: cheers, confetti, sanity for whoever touched it last. */
    static void score(ServerLevel level, Vec3 at, @Nullable LivingEntity by, boolean basket) {
        level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.08);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 12, 0.5, 0.5, 0.5, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, basket ? 1.4f : 1.1f);
        if (by instanceof ServerPlayer p) {
            reward(p, basket ? "basket" : "goal", 6, 20);
            Fx.title(p, net.minecraft.network.chat.Component.translatable(basket ? "hollowhours.fun.basket" : "hollowhours.fun.goal"), null, 2, 25, 8);
        }
    }
}
