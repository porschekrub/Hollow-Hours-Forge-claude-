package net.minecraftforge.internal;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** A small event bus: listeners by event class (a listener also gets the subclasses' events), in priority order. */
public final class EventBusImpl implements IEventBus {
    private record Listener(EventPriority priority, boolean receiveCanceled, Consumer<Event> call, long order) {}

    private final Map<Class<?>, List<Listener>> listeners = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Listener>> resolved = new ConcurrentHashMap<>();
    private long counter;
    private final String name;

    public EventBusImpl(String name) {
        this.name = name;
    }

    @Override
    public <T extends Event> void addListener(Class<T> type, Consumer<T> listener) {
        addListener(EventPriority.NORMAL, type, listener);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(EventPriority priority, Class<T> type, Consumer<T> listener) {
        add(type, new Listener(priority, false, e -> listener.accept((T) e), counter++));
    }

    private synchronized void add(Class<?> type, Listener l) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(l);
        resolved.clear();
    }

    @Override
    public void register(Object target) {
        boolean isClass = target instanceof Class<?>;
        Class<?> cls = isClass ? (Class<?>) target : target.getClass();
        for (Method m : cls.getDeclaredMethods()) {
            SubscribeEvent s = m.getAnnotation(SubscribeEvent.class);
            if (s == null || m.getParameterCount() != 1 || Modifier.isStatic(m.getModifiers()) != isClass) continue;
            Class<?> type = m.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(type)) continue;
            m.setAccessible(true);
            Object self = isClass ? null : target;
            add(type, new Listener(s.priority(), s.receiveCanceled(), e -> {
                try {
                    m.invoke(self, e);
                } catch (java.lang.reflect.InvocationTargetException ex) {
                    Throwable c = ex.getCause();
                    if (c instanceof RuntimeException r) throw r;
                    if (c instanceof Error er) throw er;
                    throw new RuntimeException(c);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }, counter++));
        }
    }

    private List<Listener> listenersFor(Class<?> type) {
        return resolved.computeIfAbsent(type, t -> {
            List<Listener> out = new ArrayList<>();
            for (Class<?> c = t; c != null && Event.class.isAssignableFrom(c); c = c.getSuperclass()) {
                List<Listener> l = listeners.get(c);
                if (l != null) out.addAll(l);
            }
            out.sort(Comparator.comparingInt((Listener l) -> l.priority().ordinal()).thenComparingLong(Listener::order));
            return out;
        });
    }

    @Override
    public boolean post(Event event) {
        for (Listener l : listenersFor(event.getClass())) {
            if (event.isCanceled() && !l.receiveCanceled()) continue;
            l.call().accept(event);
        }
        return event.isCanceled();
    }

    @Override
    public String toString() {
        return "EventBus[" + name + "]";
    }
}
