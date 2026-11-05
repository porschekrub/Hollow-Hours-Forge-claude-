package net.minecraftforge.fml.event.lifecycle;

import net.minecraftforge.eventbus.api.Event;

import java.util.concurrent.CompletableFuture;

public class FMLClientSetupEvent extends Event {
    public CompletableFuture<Void> enqueueWork(Runnable work) {
        work.run();
        return CompletableFuture.completedFuture(null);
    }
}
