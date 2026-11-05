package net.minecraftforge.client.extensions.common;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Fabric shim: an item's client extras, from its own initializeClient(Consumer) (called once, by reflection). */
public interface IClientItemExtensions {
    IClientItemExtensions DEFAULT = new IClientItemExtensions() {};

    default HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return null;
    }

    Map<Item, IClientItemExtensions> CACHE = new ConcurrentHashMap<>();

    static IClientItemExtensions of(ItemStack stack) {
        return of(stack.getItem());
    }

    static IClientItemExtensions of(Item item) {
        return CACHE.computeIfAbsent(item, it -> {
            IClientItemExtensions[] out = {DEFAULT};
            try {
                Method m = it.getClass().getMethod("initializeClient", Consumer.class);
                m.invoke(it, (Consumer<IClientItemExtensions>) e -> out[0] = e);
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
            return out[0];
        });
    }
}
