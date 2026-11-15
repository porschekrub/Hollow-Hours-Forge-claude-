package varmite.verity.entity;

import varmite.verity.entity.verity.VerityEntity;

/** Stand-in (see README.md): Verity JE's shared state (one Verity per server). */
public class VerityState {
    public static boolean hasSpawned;
    public static VerityEntity verityEntity;
    public static boolean transformFollowingDay;
    public static boolean followPlayer;
    public static int idleChatTimer;
    public static int lonelinessTimer;
    public static boolean isClientTalking;
}
