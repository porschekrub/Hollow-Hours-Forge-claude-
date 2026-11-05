package net.minecraftforge.eventbus.api;

import java.util.function.Consumer;

/** Fabric shim of Forge's event bus. The listener's event class is given (ports/pp.py adds it to every call). */
public interface IEventBus {
    <T extends Event> void addListener(Class<T> type, Consumer<T> listener);

    <T extends Event> void addListener(EventPriority priority, Class<T> type, Consumer<T> listener);

    /** A class: its static @SubscribeEvent methods; an object: its instance @SubscribeEvent methods. */
    void register(Object target);

    /** @return true when a listener cancelled it */
    boolean post(Event event);
}
