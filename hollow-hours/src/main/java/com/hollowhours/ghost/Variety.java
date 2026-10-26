package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Replay value (port of variety.js): every ghost rolls a modifier each time it appears, every in-game day has a
 * mood (calm ... haunted) and random small events keep the tension up between hunts (never the same three in a row).
 */
public final class Variety {
    private Variety() {}

    public record Mod(String id, int w, double dur, double vis, double spd, double stalk) {}

    static final Mod[] MODS = {
            new Mod("plain", 40, 1, 1, 1, 1),
            new Mod("frenzied", 14, 0.8, 1, 1.25, 0.7),
            new Mod("lingering", 14, 1.6, 1.1, 0.95, 1.5),
            new Mod("blind", 12, 1, 0.6, 1, 1),
            new Mod("keen", 12, 1, 1.35, 1, 0.9),
            new Mod("shy", 8, 0.5, 1, 1, 0.5),
    };

    public static Mod rollMod() {
        int tot = 0;
        for (Mod m : MODS) tot += m.w;
        int r = R.randInt(0, tot - 1);
        for (Mod m : MODS) {
            r -= m.w;
            if (r < 0) return m;
        }
        return MODS[0];
    }

    // ------------------------------------------------------------------ night moods
    record Mood(String id, int w, double gap, double ev) {}

    static final Mood[] MOODS = {new Mood("calm", 30, 1.35, 0.7), new Mood("normal", 35, 1.0, 1.0), new Mood("restless", 22, 0.75, 1.4), new Mood("haunted", 13, 0.55, 2.0)};
    static Mood mood = MOODS[1];
    static long moodDay = -1;

    public static double moodGap() {
        return mood.gap;
    }

    public static double moodEv() {
        return mood.ev;
    }

    static Mood rollMood() {
        int tot = 0;
        for (Mood m : MOODS) tot += m.w;
        int r = R.randInt(0, tot - 1);
        for (Mood m : MOODS) {
            r -= m.w;
            if (r < 0) return m;
        }
        return MOODS[1];
    }

    // ------------------------------------------------------------------ random events (every 100 ticks)
    private static final ArrayDeque<String> RECENT = new ArrayDeque<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    static final String[] EVENTS = {"footsteps", "blackout", "coldspot", "cry", "knockwood", "storm", "ownvoice", "heartstop", "shadow", "falseflee"};
    static final int[] WEIGHTS = {14, 9, 10, 12, 9, 6, 7, 8, 8, 6};

    public static void tick(MinecraftServer server, long now) {
        ServerLevel over = server.overworld();
        long day = over.getDayTime() / 24000L;
        if (day != moodDay) {
            moodDay = day;
            mood = rollMood();
            if (!mood.id.equals("normal")) {
                Later.run(100, () -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) Fx.actionbar(p, Component.translatable("hollowhours.mood." + mood.id));
                });
            }
        }
        if (!HHConfig.EVENTS_ENABLED.get() || Director.ghostBusy(server)) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!GhostBrain.targetable(p)) continue;
            Long at = NEXT.get(p.getUUID());
            if (at == null) {
                NEXT.put(p.getUUID(), now + (long) R.rand(2 * 1200, 5 * 1200));
                continue;
            }
            if (now < at || Hiding.isHidden(p)) continue;
            boolean night = W.isNight(over);
            NEXT.put(p.getUUID(), now + (long) (R.rand(3 * 1200, 7 * 1200) / (mood.ev * (night ? 1.4 : 1))));
            String ev = pick();
            RECENT.addLast(ev);
            while (RECENT.size() > 3) RECENT.removeFirst();
            run(p, ev);
            break;
        }
    }

    static String pick() {
        List<String> pool = new ArrayList<>();
        List<Integer> w = new ArrayList<>();
        for (int i = 0; i < EVENTS.length; i++) if (!RECENT.contains(EVENTS[i])) {
            pool.add(EVENTS[i]);
            w.add(WEIGHTS[i]);
        }
        int tot = 0;
        for (int x : w) tot += x;
        int r = R.randInt(0, Math.max(0, tot - 1));
        for (int i = 0; i < pool.size(); i++) {
            r -= w.get(i);
            if (r < 0) return pool.get(i);
        }
        return pool.isEmpty() ? "footsteps" : pool.get(0);
    }

    static Vec3 around(Vec3 l, double d) {
        double a = R.rand(0, Math.PI * 2);
        return new Vec3(l.x + Math.cos(a) * d, l.y, l.z + Math.sin(a) * d);
    }

    public static void run(ServerPlayer p, String ev) {
        Vec3 l = p.position();
        switch (ev) {
            case "footsteps" -> {
                Vec3 a = around(l, 9);
                for (int k = 0; k < 7; k++) {
                    double f = 1 - k / 8.0;
                    Later.run(k * 9, () -> Fx.playAt(p, R.pick("step.stone", "step.gravel", "step.grass"),
                            l.x + (a.x - l.x) * f, l.y, l.z + (a.z - l.z) * f, 1.3f, R.pitch(0.7, 0.9)));
                }
            }
            case "blackout" -> {
                Fx.effect(p, MobEffects.DARKNESS, 160, 0);
                Fx.playSelf(p, "mob.warden.heartbeat", 1.4f, 0.9f);
                Later.run(40, () -> Fx.actionbar(p, Component.translatable("hollowhours.event.blackout")));
            }
            case "coldspot" -> {
                Fx.effect(p, MobEffects.MOVEMENT_SLOWDOWN, 100, 0);
                p.setTicksFrozen(Math.max(p.getTicksFrozen(), 120));
                Fx.actionbar(p, Component.translatable("hollowhours.event.cold"));
                Fx.playSelf(p, "ambient.cave", 1, 0.5f);
            }
            case "cry" -> {
                Vec3 d0 = around(Vec3.ZERO, 1);
                for (int k = 0; k < 4; k++) {
                    int kk = k;
                    Later.run(k * 50, () -> {
                        double r = 22 - kk * 5;
                        Fx.playAt(p, "mob.ghast.moan", l.x + d0.x * r, l.y, l.z + d0.z * r, (float) (0.8 + kk * 0.3), 0.55f);
                    });
                }
            }
            case "knockwood" -> {
                Vec3 a = around(l, 6);
                for (int k = 0; k < 3; k++) Later.run(k * 14, () -> Fx.playAt(p, "dig.wood", a.x, l.y, a.z, 1.8f, 0.8f));
            }
            case "storm" -> {
                ServerLevel over = p.serverLevel();
                if (over.dimensionType().hasSkyLight()) over.setWeatherParameters(0, 20 * 75, true, true);
                Fx.playSelf(p, "ambient.weather.thunder", 1.5f, 0.7f);
            }
            case "ownvoice" -> {
                if (HHConfig.FAKE_MESSAGES.get())
                    p.sendSystemMessage(Component.translatable("chat.type.text", p.getDisplayName(), Component.translatable("hollowhours.ownvoice." + R.randInt(0, 3))));
            }
            case "heartstop" -> {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, null));
                for (int k = 0; k < 5; k++) Later.run(20 + k * 12, () -> Fx.playSelf(p, "mob.warden.heartbeat", 1.8f, 1.2f));
            }
            case "shadow" -> Stalkers.outdoor(p);
            case "falseflee" -> {
                Vec3 a = around(l, 30);
                Fx.playAt(p, "game.player.hurt", a.x, l.y, a.z, 2f, 0.8f);
                Later.run(60, () -> {
                    if (HHConfig.FAKE_MESSAGES.get() && p.getServer() != null) Director.fakeLeave(p.getServer());
                });
            }
            default -> {}
        }
    }
}
