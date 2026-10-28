package com.hollowhours.fun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A playground / entertainment piece: faces the player who placed it, with its own shape. */
public class FunBlock extends HorizontalDirectionalBlock {
    private final VoxelShape north, east, south, west;

    /** shape given for a block facing north (x0, y0, z0, x1, y1, z1 in pixels) */
    public FunBlock(Properties props, double x0, double y0, double z0, double x1, double y1, double z1) {
        super(props);
        this.north = Block.box(x0, y0, z0, x1, y1, z1);
        this.south = Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
        this.west = Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
        this.east = Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (s.getValue(FACING)) {
            case EAST -> east;
            case SOUTH -> south;
            case WEST -> west;
            default -> north;
        };
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }
}
