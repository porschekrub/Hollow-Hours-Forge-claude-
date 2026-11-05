package net.minecraftforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Fabric shim: entries are kept until register(bus), then put straight into the game registry (still open). */
public class DeferredRegister<T> {
    private final ResourceKey<? extends Registry<T>> key;
    private final String modid;
    private final List<Entry<? extends T>> entries = new ArrayList<>();
    private boolean done;

    public static final class Entry<I> implements Supplier<I> {
        final ResourceLocation id;
        final Supplier<? extends I> make;
        I value;

        Entry(ResourceLocation id, Supplier<? extends I> make) {
            this.id = id;
            this.make = make;
        }

        @Override
        public I get() {
            if (value == null) throw new IllegalStateException("not registered yet: " + id);
            return value;
        }

        public ResourceLocation getId() {
            return id;
        }
    }

    private DeferredRegister(ResourceKey<? extends Registry<T>> key, String modid) {
        this.key = key;
        this.modid = modid;
    }

    public static <B> DeferredRegister<B> create(ResourceKey<? extends Registry<B>> key, String modid) {
        return new DeferredRegister<>(key, modid);
    }

    public <I extends T> Supplier<I> register(String name, Supplier<? extends I> make) {
        //#if MC>=12100
        //$$ Entry<I> e = new Entry<>(ResourceLocation.fromNamespaceAndPath(modid, name), make);
        //#else
        Entry<I> e = new Entry<>(new ResourceLocation(modid, name), make);
        //#endif
        entries.add(e);
        if (done) put(e);
        return e;
    }

    public void register(IEventBus bus) {
        if (done) return;
        done = true;
        for (Entry<? extends T> e : entries) put(e);
    }

    @SuppressWarnings("unchecked")
    private <I extends T> void put(Entry<I> e) {
        Registry<T> reg = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
        if (reg == null) throw new IllegalStateException("no registry " + key.location());
        e.value = (I) Registry.register(reg, e.id, e.make.get());
    }
}
