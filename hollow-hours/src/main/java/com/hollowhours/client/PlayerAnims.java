package com.hollowhours.client;

import java.util.HashMap;
import java.util.Map;

/** Short animations other players do that the server tells us about (by entity id): scooping water. */
public final class PlayerAnims {
    private PlayerAnims() {}

    private static final Map<Integer, Long> SIP = new HashMap<>();

    static void sip(int entity) {
        SIP.put(entity, ClientFx.now());
        if (SIP.size() > 64) SIP.values().removeIf(t -> ClientFx.now() - t > FirstPersonAnim.SIP_TICKS);
    }

    /** 0..1 through the scoop, or -1 when not scooping. The local player's own scoop counts too. */
    public static float sipProgress(int entity, boolean local, float partial) {
        Long t0 = local ? Long.valueOf(FirstPersonAnim.sipT0) : SIP.get(entity);
        if (t0 == null) return -1;
        float k = (ClientFx.now() - t0 + partial) / FirstPersonAnim.SIP_TICKS;
        return k < 0 || k > 1 ? -1 : k;
    }
}
