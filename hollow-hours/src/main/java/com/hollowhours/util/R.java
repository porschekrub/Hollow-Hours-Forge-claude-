package com.hollowhours.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Small random helpers (mirrors rand/pick of the Bedrock scripts). */
public final class R {
    private R() {}

    public static double rand(double a, double b) {
        return a + ThreadLocalRandom.current().nextDouble() * (b - a);
    }

    public static int randInt(int a, int bInclusive) {
        if (bInclusive <= a) return a;
        return a + ThreadLocalRandom.current().nextInt(bInclusive - a + 1);
    }

    public static double next() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public static boolean chance(double p) {
        return ThreadLocalRandom.current().nextDouble() < p;
    }

    public static <T> T pick(List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    @SafeVarargs
    public static <T> T pick(T... arr) {
        return arr[ThreadLocalRandom.current().nextInt(arr.length)];
    }

    public static int pickIndex(int n) {
        return ThreadLocalRandom.current().nextInt(Math.max(1, n));
    }

    /** Weighted pick from a map of key -> weight. */
    public static <K> K weighted(Map<K, Integer> weights, List<K> allowed) {
        int tot = 0;
        for (K k : allowed) tot += Math.max(0, weights.getOrDefault(k, 1));
        if (tot <= 0) return allowed.get(0);
        int r = ThreadLocalRandom.current().nextInt(tot);
        for (K k : allowed) {
            r -= Math.max(0, weights.getOrDefault(k, 1));
            if (r < 0) return k;
        }
        return allowed.get(allowed.size() - 1);
    }

    public static float pitch(double a, double b) {
        return (float) rand(a, b);
    }
}
