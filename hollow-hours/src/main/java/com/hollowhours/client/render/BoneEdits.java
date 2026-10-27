package com.hollowhours.client.render;

import software.bernie.geckolib.core.animatable.model.CoreGeoBone;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Per-frame tweaks on top of the keyframes (twitches, breathing, head look).
 *
 * GeckoLib keeps a bone's value from the previous frame when no keyframe sets that channel, and once a tweak has
 * marked the bone as changed it also skips the bone's reset. Tweaks written as "current value + delta" therefore pile
 * up frame after frame: a chest breathing by 4% grew to dozens of times its size within a second (the long stretched
 * "tunnels" seen during kills) and twitching heads spun around. So each frame first puts back the value a bone had
 * before last frame's tweak, wherever the bone still holds exactly what was written.
 */
final class BoneEdits {
    private static final int CH = 9; // rot xyz, pos xyz, scale xyz
    /** per bone: [0..8] value before the tweak, [9..17] value written (NaN = channel not touched) */
    private final Map<CoreGeoBone, float[]> edits = new IdentityHashMap<>();

    /** Call once per frame before any tweak. */
    void begin() {
        for (Map.Entry<CoreGeoBone, float[]> e : edits.entrySet()) {
            CoreGeoBone b = e.getKey();
            float[] v = e.getValue();
            for (int i = 0; i < CH; i++) {
                if (!Float.isNaN(v[CH + i]) && get(b, i) == v[CH + i]) set(b, i, v[i]);
            }
        }
        edits.clear();
    }

    void addRot(CoreGeoBone b, float x, float y, float z) {
        add(b, 0, x);
        add(b, 1, y);
        add(b, 2, z);
    }

    void addPos(CoreGeoBone b, float x, float y, float z) {
        add(b, 3, x);
        add(b, 4, y);
        add(b, 5, z);
    }

    void mulScale(CoreGeoBone b, float x, float y, float z) {
        if (x != 1) write(b, 6, get(b, 6) * x);
        if (y != 1) write(b, 7, get(b, 7) * y);
        if (z != 1) write(b, 8, get(b, 8) * z);
    }

    private void add(CoreGeoBone b, int ch, float d) {
        if (d != 0) write(b, ch, get(b, ch) + d);
    }

    private void write(CoreGeoBone b, int ch, float value) {
        float[] v = edits.computeIfAbsent(b, k -> {
            float[] a = new float[CH * 2];
            Arrays.fill(a, Float.NaN);
            return a;
        });
        if (Float.isNaN(v[CH + ch])) v[ch] = get(b, ch);
        if (!Float.isFinite(value)) value = v[ch];
        set(b, ch, value);
        v[CH + ch] = get(b, ch);
    }

    private static float get(CoreGeoBone b, int ch) {
        return switch (ch) {
            case 0 -> b.getRotX();
            case 1 -> b.getRotY();
            case 2 -> b.getRotZ();
            case 3 -> b.getPosX();
            case 4 -> b.getPosY();
            case 5 -> b.getPosZ();
            case 6 -> b.getScaleX();
            case 7 -> b.getScaleY();
            default -> b.getScaleZ();
        };
    }

    private static void set(CoreGeoBone b, int ch, float v) {
        switch (ch) {
            case 0 -> b.setRotX(v);
            case 1 -> b.setRotY(v);
            case 2 -> b.setRotZ(v);
            case 3 -> b.setPosX(v);
            case 4 -> b.setPosY(v);
            case 5 -> b.setPosZ(v);
            case 6 -> b.setScaleX(v);
            case 7 -> b.setScaleY(v);
            default -> b.setScaleZ(v);
        }
    }
}
