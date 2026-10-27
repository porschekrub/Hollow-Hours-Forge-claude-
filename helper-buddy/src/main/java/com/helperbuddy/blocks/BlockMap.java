package com.helperbuddy.blocks;

import com.helperbuddy.gen.Gen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The house generators speak in the original add-on's (Bedrock) block names and abstract states: this turns them into
 * Java block states for a building that faces `facing` (local +z = facing, local +x = left of it).
 */
public final class BlockMap {
    private BlockMap() {}

    /** Bedrock names that differ in Java (pale oak does not exist in 1.20.1: birch is the closest pale wood). */
    static final Map<String, String> RENAME = new HashMap<>();

    static {
        RENAME.put("wooden_door", "oak_door");
        RENAME.put("trapdoor", "oak_trapdoor");
        RENAME.put("wooden_pressure_plate", "oak_pressure_plate");
        RENAME.put("brick_block", "bricks");
        RENAME.put("stonecutter_block", "stonecutter");
        RENAME.put("bed", "red_bed");
        RENAME.put("portal", "nether_portal");
        RENAME.put("snow", "snow_block");
        RENAME.put("azalea_leaves_flowered", "flowering_azalea_leaves");
        RENAME.put("cauldron", "water_cauldron");
        RENAME.put("grass", "grass_block");
        RENAME.put("melon_block", "melon");
        RENAME.put("lit_pumpkin", "jack_o_lantern");
        RENAME.put("concrete", "white_concrete");
        RENAME.put("wool", "white_wool");
        RENAME.put("carpet", "white_carpet");
        RENAME.put("stained_glass", "white_stained_glass");
        RENAME.put("hardened_clay", "terracotta");
        RENAME.put("golden_rail", "powered_rail");
    }

    public static String javaName(String bedrockId) {
        String n = bedrockId.startsWith("minecraft:") ? bedrockId.substring(10) : bedrockId;
        String r = RENAME.get(n);
        if (r != null) return r;
        if (n.startsWith("pale_oak_")) return "birch_" + n.substring(9);
        if (n.startsWith("stripped_pale_oak_")) return "stripped_birch_" + n.substring(18);
        if (n.equals("pale_moss_block")) return "moss_block";
        if (n.equals("pale_moss_carpet")) return "moss_carpet";
        return n;
    }

    @Nullable
    public static Block block(String bedrockId) {
        String n = javaName(bedrockId);
        ResourceLocation rl = ResourceLocation.tryParse(n.contains(":") ? n : "minecraft:" + n);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return null;
        return BuiltInRegistries.BLOCK.get(rl);
    }

    /** The item that places this block (null for blocks without an item: they are free). */
    @Nullable
    public static Item item(String bedrockOrItemId) {
        String n = javaName(bedrockOrItemId);
        if (n.equals("water")) return Items.WATER_BUCKET;
        if (n.equals("water_cauldron")) return Items.CAULDRON;
        if (n.equals("nether_portal")) return Items.FLINT_AND_STEEL;
        ResourceLocation rl = ResourceLocation.tryParse(n.contains(":") ? n : "minecraft:" + n);
        if (rl == null) return null;
        if (BuiltInRegistries.ITEM.containsKey(rl)) return BuiltInRegistries.ITEM.get(rl);
        Block b = block(bedrockOrItemId);
        if (b != null && b.asItem() != Items.AIR) return b.asItem();
        return null;
    }

    /** Local direction name (px / nx / pz / nz) to a world direction. */
    public static Direction dir(Direction facing, @Nullable String d, String def) {
        String v = d == null ? def : d;
        return switch (v) {
            case "px" -> facing.getCounterClockWise();
            case "nx" -> facing.getClockWise();
            case "nz" -> facing.getOpposite();
            default -> facing;
        };
    }

    static Direction.Axis axis(Direction facing, @Nullable String a) {
        if (a == null || a.equals("y")) return Direction.Axis.Y;
        Direction d = a.equals("x") ? facing.getCounterClockWise() : facing;
        return d.getAxis();
    }

    /** Java block state for one generator block, or null when the block does not exist here. */
    @Nullable
    public static BlockState state(Gen.Blk b, Direction facing) {
        return state(b.id, b.st, facing);
    }

    @Nullable
    public static BlockState state(String id, @Nullable Gen.St st0, Direction facing) {
        Block block = block(id);
        if (block == null) return null;
        Gen.St st = st0 != null ? st0 : new Gen.St();
        BlockState s = block.defaultBlockState();
        if (block instanceof StairBlock) {
            s = s.setValue(StairBlock.FACING, dir(facing, st.f, "pz")).setValue(StairBlock.HALF, "top".equals(st.half) ? Half.TOP : Half.BOTTOM);
        } else if (block instanceof SlabBlock) {
            s = s.setValue(SlabBlock.TYPE, "top".equals(st.half) ? SlabType.TOP : SlabType.BOTTOM);
        } else if (block instanceof DoorBlock) {
            s = s.setValue(DoorBlock.FACING, dir(facing, st.f, "pz")).setValue(DoorBlock.HALF, Boolean.TRUE.equals(st.upper) ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                    .setValue(DoorBlock.HINGE, Boolean.TRUE.equals(st.hinge) ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
        } else if (block instanceof BedBlock) {
            s = s.setValue(BedBlock.FACING, dir(facing, st.f, "pz")).setValue(BedBlock.PART, Boolean.TRUE.equals(st.head) ? BedPart.HEAD : BedPart.FOOT);
        } else if (block == Blocks.LADDER) {
            s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir(facing, st.f, "nz"));
        } else if (block instanceof GrindstoneBlock) {
            s = s.setValue(GrindstoneBlock.FACE, AttachFace.FLOOR).setValue(GrindstoneBlock.FACING, dir(facing, st.f, "nz"));
        } else if (block == Blocks.ANVIL) {
            s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir(facing, st.f, "nz").getClockWise());
        } else if (block instanceof LanternBlock) {
            s = s.setValue(LanternBlock.HANGING, Boolean.TRUE.equals(st.hanging));
        } else if (block instanceof LeavesBlock) {
            s = s.setValue(LeavesBlock.PERSISTENT, true); // decorative hedges never decay
        } else if (block == Blocks.WATER_CAULDRON) {
            s = s.setValue(LayeredCauldronBlock.LEVEL, 3);
        } else if (block == Blocks.BARREL) {
            s = s.setValue(BlockStateProperties.FACING, Direction.UP);
        } else if (block == Blocks.NETHER_PORTAL) {
            Direction.Axis ax = axis(facing, st.axis == null ? "x" : st.axis);
            s = s.setValue(BlockStateProperties.HORIZONTAL_AXIS, ax == Direction.Axis.Y ? Direction.Axis.X : ax);
        } else if (block instanceof RotatedPillarBlock && s.hasProperty(RotatedPillarBlock.AXIS)) {
            s = s.setValue(RotatedPillarBlock.AXIS, axis(facing, st.axis));
        } else if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            // furnace, chest, smoker, blast furnace, stonecutter, lectern, loom, campfire ...
            s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir(facing, st.f, "nz"));
        }
        return s;
    }

    /** Blocks made of two parts placed together (the generator lists both; the second is skipped when already there). */
    public static boolean twoPart(BlockState s) {
        return s.getBlock() instanceof DoorBlock || s.getBlock() instanceof BedBlock;
    }

    /** The other half of a door or bed. */
    @Nullable
    public static net.minecraft.core.BlockPos otherHalf(BlockState s, net.minecraft.core.BlockPos p) {
        if (s.getBlock() instanceof DoorBlock) return s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? p.above() : p.below();
        if (s.getBlock() instanceof BedBlock) {
            Direction f = s.getValue(BedBlock.FACING);
            return s.getValue(BedBlock.PART) == BedPart.FOOT ? p.relative(f) : p.relative(f.getOpposite());
        }
        return null;
    }

    @Nullable
    public static BlockState otherHalfState(BlockState s) {
        if (s.getBlock() instanceof DoorBlock)
            return s.setValue(DoorBlock.HALF, s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        if (s.getBlock() instanceof BedBlock) return s.setValue(BedBlock.PART, s.getValue(BedBlock.PART) == BedPart.FOOT ? BedPart.HEAD : BedPart.FOOT);
        return null;
    }
}
