package net.minecraftforge.common;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;

public final class ToolActions {
    private ToolActions() {}

    public static final ToolAction PICKAXE_DIG = new ToolAction("pickaxe_dig", s -> s.getItem() instanceof PickaxeItem || s.is(ItemTags.PICKAXES));
    public static final ToolAction AXE_DIG = new ToolAction("axe_dig", s -> s.getItem() instanceof AxeItem || s.is(ItemTags.AXES));
    public static final ToolAction SHOVEL_DIG = new ToolAction("shovel_dig", s -> s.getItem() instanceof ShovelItem || s.is(ItemTags.SHOVELS));
    public static final ToolAction HOE_DIG = new ToolAction("hoe_dig", s -> s.getItem() instanceof HoeItem || s.is(ItemTags.HOES));
    public static final ToolAction SWORD_DIG = new ToolAction("sword_dig", s -> s.getItem() instanceof SwordItem || s.is(ItemTags.SWORDS));
    public static final ToolAction SHEARS_DIG = new ToolAction("shears_dig", s -> s.getItem() instanceof ShearsItem);
    public static final ToolAction AXE_STRIP = new ToolAction("axe_strip", s -> s.getItem() instanceof AxeItem);
    public static final ToolAction SHOVEL_FLATTEN = new ToolAction("shovel_flatten", s -> s.getItem() instanceof ShovelItem);
    public static final ToolAction HOE_TILL = new ToolAction("till", s -> s.getItem() instanceof HoeItem);
    public static final ToolAction SHIELD_BLOCK = new ToolAction("shield_block", s -> s.getItem() instanceof net.minecraft.world.item.ShieldItem);
}
