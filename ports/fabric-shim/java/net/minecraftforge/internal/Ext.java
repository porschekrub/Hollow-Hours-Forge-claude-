package net.minecraftforge.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge's extra methods on Minecraft classes (onAddedToWorld, shouldRiderSit, ...) are plain methods of the mod's own
 * classes on Fabric: the shim calls them when they are there (looked up once per class).
 */
public final class Ext {
    private Ext() {}

    private static final MethodHandle NONE = MethodHandles.constant(Object.class, null);
    private static final ClassValue<Map<String, MethodHandle>> CACHE = new ClassValue<>() {
        @Override
        protected Map<String, MethodHandle> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static MethodHandle find(Object o, String name, Class<?> ret) {
        return CACHE.get(o.getClass()).computeIfAbsent(name, n -> {
            // only the mods' own classes (Minecraft's never have these methods)
            if (o.getClass().getName().startsWith("net.minecraft.")) return NONE;
            try {
                return MethodHandles.publicLookup().findVirtual(o.getClass(), n, MethodType.methodType(ret));
            } catch (ReflectiveOperationException e) {
                return NONE;
            }
        });
    }

    public static void call(Object o, String name) {
        if (o == null) return;
        MethodHandle h = find(o, name, void.class);
        if (h == NONE) return;
        try {
            h.invoke(o);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static boolean bool(Object o, String name, boolean otherwise) {
        if (o == null) return otherwise;
        MethodHandle h = find(o, name, boolean.class);
        if (h == NONE) return otherwise;
        try {
            return (boolean) h.invoke(o);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
