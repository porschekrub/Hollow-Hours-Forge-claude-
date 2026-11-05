package com.hollowhours.endgame;

import com.hollowhours.ghost.GhostBrain;

/** The last chapter (placeholder until it is written). */
public final class Endgame {
    private Endgame() {}

    /** A knocked-out ghost stays down while the final hunt needs it to. */
    public static boolean keepDown(GhostBrain b) {
        return false;
    }
}
