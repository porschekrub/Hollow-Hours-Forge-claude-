package com.hollowhours.entity;

/** What the ghost model is doing (synced to clients, picks the GeckoLib animation). */
public enum GhostAnim {
    IDLE, STALK, SNAP, CHASE, CRAWL_CHASE, BASH, STUCK, SQUEEZE, KNOCK, SEARCH, CRAWL_SEARCH, INSPECT, PEEK, CLIMB, SPAWN,
    GRAB, HOLD, SCREAM, KILL, FAREWELL, YANK, LUNGE, FOLLOW_RUN, FOLLOW_CRAWL, LAUGH;

    private static final GhostAnim[] VALUES = values();

    public static GhostAnim of(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : IDLE;
    }

    public boolean low() {
        return this == CRAWL_CHASE || this == SQUEEZE || this == STUCK || this == CRAWL_SEARCH || this == PEEK || this == FOLLOW_CRAWL;
    }
}
