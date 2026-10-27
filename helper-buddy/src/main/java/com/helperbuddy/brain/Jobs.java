package com.helperbuddy.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;
import java.util.Set;

/** Facts about blocks the jobs need: ores and their group, logs, natural ground, junk ... */
public final class Jobs {
    private Jobs() {}

    /** Ore group ("coal" ... "debris") or null. */
    @Nullable
    public static String oreGroup(BlockState s) {
        if (!s.is(Tags.Blocks.ORES)) return null;
        if (s.is(Tags.Blocks.ORES_NETHERITE_SCRAP)) return "debris";
        if (s.is(Blocks.NETHER_GOLD_ORE) || s.is(Tags.Blocks.ORES_QUARTZ)) return "nether";
        if (s.is(Tags.Blocks.ORES_COAL)) return "coal";
        if (s.is(Tags.Blocks.ORES_COPPER)) return "copper";
        if (s.is(Tags.Blocks.ORES_IRON)) return "iron";
        if (s.is(Tags.Blocks.ORES_GOLD)) return "gold";
        if (s.is(Tags.Blocks.ORES_REDSTONE)) return "redstone";
        if (s.is(Tags.Blocks.ORES_LAPIS)) return "lapis";
        if (s.is(Tags.Blocks.ORES_DIAMOND)) return "diamond";
        if (s.is(Tags.Blocks.ORES_EMERALD)) return "emerald";
        return "other";
    }

    public static boolean isOre(BlockState s) {
        return s.is(Tags.Blocks.ORES);
    }

    public static boolean isLog(BlockState s) {
        return s.is(BlockTags.LOGS) && !s.is(Blocks.CRIMSON_HYPHAE) && !s.is(Blocks.WARPED_HYPHAE);
    }

    public static boolean isLeaf(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(Blocks.NETHER_WART_BLOCK) || s.is(Blocks.WARPED_WART_BLOCK) || s.is(Blocks.SHROOMLIGHT);
    }

    /** Leaves placed by a player never decay: those trees are someone's build. */
    public static boolean naturalLeaf(BlockState s) {
        if (s.hasProperty(LeavesBlock.PERSISTENT)) return !s.getValue(LeavesBlock.PERSISTENT);
        return isLeaf(s);
    }

    /** Natural stone-like blocks the helper may dig through (with a pickaxe). */
    public static boolean isStone(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.COBBLED_DEEPSLATE)
                || s.is(Blocks.END_STONE) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK)
                || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.AMETHYST_BLOCK) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.INFESTED_STONE) || s.is(Blocks.INFESTED_DEEPSLATE);
    }

    /** Soft natural ground (shovel). */
    public static boolean isSoft(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL)
                || s.is(Blocks.MUD) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(Blocks.SUSPICIOUS_GRAVEL) || s.is(Blocks.SUSPICIOUS_SAND)
                || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.MYCELIUM) || s.is(Blocks.PODZOL);
    }

    /** May be dug through on the way (natural, not a build). */
    public static boolean diggable(BlockState s) {
        return isStone(s) || isSoft(s) || isOre(s);
    }

    public static Bag.Kind toolFor(BlockState s) {
        if (s.is(BlockTags.MINEABLE_WITH_PICKAXE)) return Bag.Kind.PICKAXE;
        if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) return Bag.Kind.SHOVEL;
        if (s.is(BlockTags.MINEABLE_WITH_AXE)) return Bag.Kind.AXE;
        if (s.is(BlockTags.MINEABLE_WITH_HOE)) return Bag.Kind.HOE;
        return null;
    }

    static final Set<net.minecraft.world.item.Item> JUNK = Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL, Items.GRANITE, Items.DIORITE,
            Items.ANDESITE, Items.TUFF, Items.NETHERRACK, Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.BLACKSTONE, Items.BASALT, Items.SMOOTH_BASALT, Items.SAND,
            Items.RED_SAND, Items.SANDSTONE, Items.RED_SANDSTONE, Items.FLINT, Items.SOUL_SAND, Items.SOUL_SOIL, Items.END_STONE, Items.MAGMA_BLOCK);

    public static boolean isJunk(ItemStack s) {
        return JUNK.contains(s.getItem());
    }

    public static float xpFor(BlockState s) {
        String g = oreGroup(s);
        if (g != null) return switch (g) {
            case "diamond", "emerald" -> 10;
            case "debris" -> 15;
            case "gold", "lapis" -> 4;
            case "iron", "redstone" -> 3;
            default -> 2;
        };
        if (isLog(s)) return 1;
        if (isLeaf(s)) return 0;
        return 0.2f;
    }

    public static boolean touchesLava(Level l, BlockPos p) {
        for (Direction d : Direction.values()) if (l.getFluidState(p.relative(d)).is(FluidTags.LAVA)) return true;
        return false;
    }

    public static boolean exposed(Level l, BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockState n = l.getBlockState(p.relative(d));
            if (n.isAir() || !n.isCollisionShapeFullBlock(l, p.relative(d)) && n.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    public static boolean isAirish(Level l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    /** Natural things that may be cleared to make room for a building (never a player's build). */
    public static boolean natural(Level l, BlockPos p, BlockState s) {
        if (s.isAir()) return true;
        if (isLog(s)) return Trees.isNaturalTreeLog(l, p);
        if (isLeaf(s)) return naturalLeaf(s);
        if (diggable(s)) return true;
        Block b = s.getBlock();
        if (s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.TALL_FLOWERS))
            return true;
        if (b == Blocks.ICE || b == Blocks.PACKED_ICE || b == Blocks.MUSHROOM_STEM || b == Blocks.BROWN_MUSHROOM_BLOCK || b == Blocks.RED_MUSHROOM_BLOCK
                || b == Blocks.MANGROVE_ROOTS || b == Blocks.MUDDY_MANGROVE_ROOTS || b == Blocks.BEE_NEST || b == Blocks.MOSS_BLOCK || b == Blocks.MOSS_CARPET
                || b == Blocks.SUGAR_CANE || b == Blocks.BAMBOO || b == Blocks.CACTUS || b == Blocks.PUMPKIN || b == Blocks.MELON || b == Blocks.COBWEB
                || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.POINTED_DRIPSTONE || b == Blocks.AZALEA || b == Blocks.FLOWERING_AZALEA
                || b == Blocks.BIG_DRIPLEAF || b == Blocks.BIG_DRIPLEAF_STEM || b == Blocks.SMALL_DRIPLEAF || b == Blocks.GLOW_LICHEN || b == Blocks.VINE
                || b == Blocks.SNOW || b == Blocks.KELP || b == Blocks.KELP_PLANT || b == Blocks.SEAGRASS || b == Blocks.TALL_SEAGRASS
                || b == Blocks.BROWN_MUSHROOM || b == Blocks.RED_MUSHROOM || b == Blocks.DEAD_BUSH || b == Blocks.HANGING_ROOTS || b == Blocks.SPORE_BLOSSOM
                || b == Blocks.PINK_PETALS)
            return true;
        return false;
    }
}
