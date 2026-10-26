package com.hollowhours.ghost;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hollowhours.HollowHours;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Timelines of the grab / scream / kill animations, generated together with the animation files
 * (tools/gen_ghost_anims.py) so that the victim's body, camera, hits and screen effects line up exactly
 * with what the ghost model does. Loaded from the jar on both sides.
 *
 * Offsets are in blocks, relative to the ghost: forward (+ = in front of it), up, side (+ = its left).
 */
public final class KillTracks {
    public static final class Key {
        public final float t, a, b, c;

        Key(float t, float a, float b, float c) {
            this.t = t;
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    /** A moment in the timeline: hit = damage tick, fx = screen effect name, sound = sound name. */
    public record Event(float t, String type, String arg, float strength) {}

    public static final class Track {
        public final String name;
        public final int index;
        public final float length;
        /** animation to play on the ghost ("animation.ghost.<anim>") and whether it loops (the betrayal hold) */
        public String anim;
        public boolean loop;
        public final List<Key> victim = new ArrayList<>();
        public final List<Key> head = new ArrayList<>();
        public final List<Key> roll = new ArrayList<>();
        public final List<Event> events = new ArrayList<>();

        Track(String name, int index, float length) {
            this.name = name;
            this.index = index;
            this.length = length;
        }

        float local(float t) {
            return loop && length > 0 ? t % length : t;
        }

        public Vec3 victimAt(float t) {
            return sample(victim, local(t), new Vec3(1.2, 0.0, 0.0));
        }

        public Vec3 headAt(float t) {
            return sample(head, local(t), new Vec3(0, 3, 0));
        }

        public float rollAt(float t) {
            return (float) sample(roll, local(t), Vec3.ZERO).x;
        }

        public int ticks() {
            return Math.max(1, Math.round(length * 20));
        }

        private static Vec3 sample(List<Key> keys, float t, Vec3 def) {
            if (keys.isEmpty()) return def;
            if (t <= keys.get(0).t) return new Vec3(keys.get(0).a, keys.get(0).b, keys.get(0).c);
            for (int i = 0; i + 1 < keys.size(); i++) {
                Key k0 = keys.get(i), k1 = keys.get(i + 1);
                if (t >= k0.t && t <= k1.t) {
                    float f = k1.t == k0.t ? 0 : (t - k0.t) / (k1.t - k0.t);
                    f = f * f * (3 - 2 * f); // smoothstep: no jerks between keys
                    return new Vec3(k0.a + (k1.a - k0.a) * f, k0.b + (k1.b - k0.b) * f, k0.c + (k1.c - k0.c) * f);
                }
            }
            Key l = keys.get(keys.size() - 1);
            return new Vec3(l.a, l.b, l.c);
        }
    }

    private static final List<Track> ALL = new ArrayList<>();
    private static final Map<String, Track> BY_NAME = new HashMap<>();
    private static boolean loaded;

    private KillTracks() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = KillTracks.class.getResourceAsStream("/data/hollowhours/kill_tracks.json")) {
            if (in == null) {
                HollowHours.LOG.error("kill_tracks.json missing from the jar");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("tracks");
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                Track tr = new Track(o.get("name").getAsString(), ALL.size(), o.get("length").getAsFloat());
                tr.anim = o.has("anim") ? o.get("anim").getAsString() : tr.name;
                tr.loop = o.has("loop") && o.get("loop").getAsBoolean();
                readKeys(o.getAsJsonArray("victim"), tr.victim);
                readKeys(o.getAsJsonArray("head"), tr.head);
                if (o.has("roll")) readKeys(o.getAsJsonArray("roll"), tr.roll);
                if (o.has("events")) {
                    for (JsonElement ev : o.getAsJsonArray("events")) {
                        JsonObject eo = ev.getAsJsonObject();
                        tr.events.add(new Event(eo.get("t").getAsFloat(), eo.get("type").getAsString(),
                                eo.has("arg") ? eo.get("arg").getAsString() : "", eo.has("s") ? eo.get("s").getAsFloat() : 1f));
                    }
                }
                ALL.add(tr);
                BY_NAME.put(tr.name, tr);
            }
            HollowHours.LOG.info("Loaded {} kill cinematic tracks", ALL.size());
        } catch (Exception ex) {
            HollowHours.LOG.error("Could not read kill tracks", ex);
        }
    }

    private static void readKeys(JsonArray arr, List<Key> out) {
        if (arr == null) return;
        for (JsonElement e : arr) {
            JsonArray k = e.getAsJsonArray();
            float t = k.get(0).getAsFloat();
            float a = k.size() > 1 ? k.get(1).getAsFloat() : 0, b = k.size() > 2 ? k.get(2).getAsFloat() : 0, c = k.size() > 3 ? k.get(3).getAsFloat() : 0;
            out.add(new Key(t, a, b, c));
        }
    }

    public static Track get(int index) {
        load();
        return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
    }

    public static Track get(String name) {
        load();
        return BY_NAME.get(name);
    }

    public static int indexOf(String name) {
        Track t = get(name);
        return t == null ? -1 : t.index;
    }

    /** A shared phase (grab / scream / hold) as fitted to ghost n: "grab_g3", falling back to "grab". */
    public static Track phase(String phase, int n) {
        Track t = get(phase + "_g" + n);
        return t != null ? t : get(phase);
    }

    /** The kill animations available for ghost n (kill_n_1, kill_n_2, ...). */
    public static List<Track> killsFor(int n) {
        load();
        List<Track> out = new ArrayList<>();
        for (Track t : ALL) if (t.name.startsWith("kill_" + n + "_")) out.add(t);
        return out;
    }
}
