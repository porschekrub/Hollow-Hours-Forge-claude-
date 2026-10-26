package com.hollowhours.util;

import com.hollowhours.HollowHours;

import java.util.ArrayList;
import java.util.List;

/** Server-side delayed tasks (system.runTimeout of the Bedrock scripts). Ticked from the server tick event. */
public final class Later {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> ADD = new ArrayList<>();
    private static long now;
    private static boolean ticking;

    private record Task(long at, Runnable run) {}

    private Later() {}

    public static void run(int ticks, Runnable r) {
        Task t = new Task(now + Math.max(1, ticks), r);
        if (ticking) ADD.add(t);
        else TASKS.add(t);
    }

    public static long now() {
        return now;
    }

    public static void tick(long serverTick) {
        now = serverTick;
        ticking = true;
        try {
            for (int i = TASKS.size() - 1; i >= 0; i--) {
                Task t = TASKS.get(i);
                if (t.at <= now) {
                    TASKS.remove(i);
                    try {
                        t.run.run();
                    } catch (Throwable e) {
                        HollowHours.LOG.debug("delayed task failed", e);
                    }
                }
            }
        } finally {
            ticking = false;
            TASKS.addAll(ADD);
            ADD.clear();
        }
    }

    public static void clear() {
        TASKS.clear();
        ADD.clear();
    }
}
