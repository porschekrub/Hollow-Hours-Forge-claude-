package net.minecraftforge.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Fabric shim: Forge's tags as Fabric's conventional "c:" tags (or vanilla's own where 1.20.1 has no "c:" one). */
public final class Tags {
    private Tags() {}

    static TagKey<Block> block(String path) {
        //#if MC>=12100
        //$$ return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
        //#else
        return TagKey.create(Registries.BLOCK, new ResourceLocation("c", path));
        //#endif
    }

    public static final class Blocks {
        private Blocks() {}

        public static final TagKey<Block> ORES = block("ores");
        //#if MC>=12100
        //$$ public static final TagKey<Block> ORES_COAL = block("ores/coal");
        //$$ public static final TagKey<Block> ORES_COPPER = block("ores/copper");
        //$$ public static final TagKey<Block> ORES_DIAMOND = block("ores/diamond");
        //$$ public static final TagKey<Block> ORES_EMERALD = block("ores/emerald");
        //$$ public static final TagKey<Block> ORES_GOLD = block("ores/gold");
        //$$ public static final TagKey<Block> ORES_IRON = block("ores/iron");
        //$$ public static final TagKey<Block> ORES_LAPIS = block("ores/lapis");
        //$$ public static final TagKey<Block> ORES_NETHERITE_SCRAP = block("ores/netherite_scrap");
        //$$ public static final TagKey<Block> ORES_QUARTZ = block("ores/quartz");
        //$$ public static final TagKey<Block> ORES_REDSTONE = block("ores/redstone");
        //$$ public static final TagKey<Block> GLASS_BLOCKS = block("glass_blocks");
        //$$ public static final TagKey<Block> GLASS = GLASS_BLOCKS;
        //#else
        public static final TagKey<Block> ORES_COAL = BlockTags.COAL_ORES;
        public static final TagKey<Block> ORES_COPPER = BlockTags.COPPER_ORES;
        public static final TagKey<Block> ORES_DIAMOND = BlockTags.DIAMOND_ORES;
        public static final TagKey<Block> ORES_EMERALD = BlockTags.EMERALD_ORES;
        public static final TagKey<Block> ORES_GOLD = BlockTags.GOLD_ORES;
        public static final TagKey<Block> ORES_IRON = BlockTags.IRON_ORES;
        public static final TagKey<Block> ORES_LAPIS = BlockTags.LAPIS_ORES;
        public static final TagKey<Block> ORES_NETHERITE_SCRAP = block("netherite_scrap_ores");
        public static final TagKey<Block> ORES_QUARTZ = block("quartz_ores");
        public static final TagKey<Block> ORES_REDSTONE = BlockTags.REDSTONE_ORES;
        public static final TagKey<Block> GLASS = block("glass_blocks");
        public static final TagKey<Block> GLASS_BLOCKS = GLASS;
        //#endif
        public static final TagKey<Block> GLASS_PANES = block("glass_panes");
    }
}
