package com.veritypal.friends;

import com.veritypal.VerityPal;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * What Verity just said (her AI's answers reach the players as Verity JE's voice packets, which pass through here,
 * see TtsPayloadMixin). Used to let a Helper Buddy helper answer her, and by the tests.
 */
public final class Heard {
    private Heard() {}

    /** Set while Verity Pal itself sends a line (those are not her AI's answers). */
    public static boolean ours;
    @Nullable
    static Consumer<String> waiting;
    static long until;
    public static volatile String last = "";
    public static volatile int count;

    public static void expect(Consumer<String> then) {
        waiting = then;
        until = System.currentTimeMillis() + 45_000;
    }

    public static void spoken(String text) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || ours || text == null) return;
        last = text;
        count++;
        VerityPal.LOG.info("[Verity Pal] Verity said: {}", text);
        Consumer<String> w = waiting;
        if (w == null) return;
        waiting = null;
        if (System.currentTimeMillis() > until) return;
        w.accept(text);
    }
}
