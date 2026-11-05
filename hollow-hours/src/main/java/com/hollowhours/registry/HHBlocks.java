package com.hollowhours.registry;

import com.hollowhours.HollowHours;
import com.hollowhours.fun.FunBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Playground and living-room blocks (to keep your sanity up). */
public final class HHBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, HollowHours.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HollowHours.MODID);

    /** every block here, in creative-tab order */
    public static final List<Supplier<Block>> ALL = new ArrayList<>();

    static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion();
    }

    static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.METAL).noOcclusion();
    }

    static Supplier<Block> block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties props) {
        Supplier<Block> b = BLOCKS.register(name, () -> make.apply(props));
        HHItems.ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
                tip.add(Component.translatable("block.hollowhours." + name + ".tip").withStyle(ChatFormatting.GRAY));
            }
        });
        ALL.add(b);
        return b;
    }

    public static final Supplier<Block> SWING = block("swing", FunBlocks.Swing::new, wood().noCollission());
    public static final Supplier<Block> SLIDE = block("slide", FunBlocks.Slide::new, metal().sound(SoundType.METAL));
    public static final Supplier<Block> TRAMPOLINE = block("trampoline", FunBlocks.Trampoline::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.0f).sound(SoundType.WOOL).noOcclusion());
    public static final Supplier<Block> BASKETBALL_HOOP = block("basketball_hoop", FunBlocks.Hoop::new, metal());
    public static final Supplier<Block> SOCCER_GOAL = block("soccer_goal", FunBlocks.Goal::new, metal());
    public static final Supplier<Block> ARCADE = block("arcade", FunBlocks.Arcade::new, wood().lightLevel(s -> 6));
    public static final Supplier<Block> TV = block("tv", FunBlocks.Tv::new, metal().lightLevel(s -> s.getValue(FunBlocks.LIT) ? 9 : 0));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<FunBlocks.TvBlockEntity>> TV_BE = BLOCK_ENTITIES.register("tv",
            () -> BlockEntityType.Builder.of(FunBlocks.TvBlockEntity::new, TV.get()).build(null));

    private HHBlocks() {}
}
