package net.minecraftforge.common;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Supplier;

/** Fabric shim: the shim registers entity types before items, so the type is there already. */
public class ForgeSpawnEggItem extends SpawnEggItem {
    public ForgeSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Properties props) {
        super(type.get(), backgroundColor, highlightColor, props);
    }
}
