package com.hollowhours.entity;

import net.minecraft.ChatFormatting;

import java.util.Map;

/**
 * The five hunting ghosts. All numbers come from the Bedrock add-on (body.js / variety.js / entity scripts)
 * so the Java version behaves the same, plus the new horror data (kill cinematics, glow colour).
 */
public enum GhostKind {
    //            n  key      colour                     w    h    climb drop brk   spd   dur  vis   stalk  creep(a,b,s0,s1)      knock bash  insp  peek  sx    sy    egg bg/fg
    WAIL(1, "wail", ChatFormatting.GRAY, 1.0f, 4.8f, 4, 4, 1.0f, 1.08f, 1.2f, 1.3f, 0.7f, 40, 90, 2, 4, 2.35f, 3.48f, 2.43f, 1.77f, 0.95f, 1.12f, 0xB8B4AC, 0x5A0C0C,
            Map.ofEntries(Map.entry("crawl_drop", 11.5), Map.entry("fold", 0.0), Map.entry("lift", 12.0), Map.entry("crawl_head", 0.0), Map.entry("peek_drop", 25.0),
                    Map.entry("peek_body", 92.0), Map.entry("peek_hp", 0.0), Map.entry("crawl_sq", 0.9), Map.entry("crawl_ea", 0.0), Map.entry("knock_drop", 5.33),
                    Map.entry("knock_bend", 8.0), Map.entry("knock_arm", -60.0), Map.entry("knock_fore", -150.0), Map.entry("insp_drop", 12.63), Map.entry("insp_bend", 20.0),
                    Map.entry("insp_arm", -70.0), Map.entry("insp_fore", -104.0))),
    WIDOW(2, "widow", ChatFormatting.DARK_AQUA, 0.6f, 1.85f, 3, 3, 1.6f, 0.92f, 2.2f, 1.15f, 1.3f, 90, 200, 2, 3, 1.33f, 1.49f, 0.67f, 0.81f, 1.0f, 1.0f, 0x2E4A52, 0x9FD7E0,
            Map.ofEntries(Map.entry("crawl_drop", 5.5), Map.entry("fold", 10.0), Map.entry("lift", 10.0), Map.entry("crawl_head", 0.0), Map.entry("peek_drop", 20.0),
                    Map.entry("peek_body", 92.0), Map.entry("peek_hp", 0.0), Map.entry("crawl_sq", 1.0), Map.entry("crawl_ea", 0.0), Map.entry("knock_drop", 2.96),
                    Map.entry("knock_bend", 4.0), Map.entry("knock_arm", -160.0), Map.entry("knock_fore", -70.0), Map.entry("insp_drop", 8.16), Map.entry("insp_bend", -44.0),
                    Map.entry("insp_arm", -82.0), Map.entry("insp_fore", -98.0))),
    ASH(3, "ash", ChatFormatting.GOLD, 1.6f, 3.2f, 2, 3, 0.6f, 1.0f, 0.8f, 1.0f, 0.6f, 120, 220, 6, 10, 2.76f, 4.09f, 2.65f, 1.95f, 1.3f, 0.85f, 0x1A0F0A, 0xFF7A1A,
            Map.ofEntries(Map.entry("crawl_drop", 7.8), Map.entry("fold", 0.0), Map.entry("lift", 14.0), Map.entry("crawl_head", 0.0), Map.entry("peek_drop", 18.0),
                    Map.entry("peek_body", 96.0), Map.entry("peek_hp", 0.0), Map.entry("crawl_sq", 1.0), Map.entry("crawl_ea", 0.0), Map.entry("knock_drop", 5.18),
                    Map.entry("knock_bend", 16.0), Map.entry("knock_arm", -130.0), Map.entry("knock_fore", -145.0), Map.entry("insp_drop", 11.87), Map.entry("insp_bend", -12.0),
                    Map.entry("insp_arm", -94.0), Map.entry("insp_fore", -104.0))),
    VOID(4, "void", ChatFormatting.DARK_PURPLE, 1.0f, 6.2f, 5, 5, 1.1f, 1.05f, 1.2f, 1.1f, 1.0f, 20, 120, 1, 8, 2.42f, 3.68f, 2.38f, 1.75f, 0.95f, 1.39f, 0x0E0E12, 0xE8E8E8,
            Map.ofEntries(Map.entry("crawl_drop", 8.3), Map.entry("fold", 26.0), Map.entry("lift", -6.0), Map.entry("crawl_head", 80.0), Map.entry("peek_drop", 31.0),
                    Map.entry("peek_body", 92.0), Map.entry("peek_hp", 0.0), Map.entry("crawl_sq", 0.7), Map.entry("crawl_ea", -60.0), Map.entry("knock_drop", 5.16),
                    Map.entry("knock_bend", 20.0), Map.entry("knock_arm", -60.0), Map.entry("knock_fore", -150.0), Map.entry("insp_drop", 13.35), Map.entry("insp_bend", 36.0),
                    Map.entry("insp_arm", -106.0), Map.entry("insp_fore", -116.0))),
    STAG(5, "stag", ChatFormatting.WHITE, 1.4f, 5.0f, 4, 5, 0.85f, 1.45f, 1.4f, 1.1f, 1.0f, 25, 65, 4, 7, 2.83f, 4.03f, 3.01f, 2.22f, 1.0f, 1.0f, 0xD9CFC0, 0x6B1010,
            Map.ofEntries(Map.entry("crawl_drop", 13.6), Map.entry("fold", 4.0), Map.entry("lift", 10.0), Map.entry("crawl_head", -20.0), Map.entry("peek_drop", 22.0),
                    Map.entry("peek_body", 92.0), Map.entry("peek_hp", 25.0), Map.entry("crawl_sq", 0.85), Map.entry("crawl_ea", 0.0), Map.entry("knock_drop", 5.81),
                    Map.entry("knock_bend", 24.0), Map.entry("knock_arm", -100.0), Map.entry("knock_fore", -145.0), Map.entry("insp_drop", 13.28), Map.entry("insp_bend", 24.0),
                    Map.entry("insp_arm", -106.0), Map.entry("insp_fore", -98.0)));

    public final int n;
    public final String key;
    public final ChatFormatting color;
    public final float width, height;
    public final int climbUp, dropDown;
    public final float breakMult;
    /** variety.js PROFILE: chase speed, chase/stare duration, perception range, stalk time multipliers */
    public final float spd, dur, vis, stalk;
    public final int creepMin, creepMax, creepStepMin, creepStepMax;
    public final float knockReach, bashReach, inspectReach, peekReach;
    public final float scaleXZ, scaleY;
    public final int eggBg, eggFg;
    public final Map<String, Double> molang;

    GhostKind(int n, String key, ChatFormatting color, float w, float h, int up, int drop, float brk, float spd, float dur, float vis, float stalk,
              int ca, int cb, int s0, int s1, float knock, float bash, float insp, float peek, float sx, float sy, int bg, int fg, Map<String, Double> molang) {
        this.n = n;
        this.key = key;
        this.color = color;
        this.width = w;
        this.height = h;
        this.climbUp = up;
        this.dropDown = drop;
        this.breakMult = brk;
        this.spd = spd;
        this.dur = dur;
        this.vis = vis;
        this.stalk = stalk;
        this.creepMin = ca;
        this.creepMax = cb;
        this.creepStepMin = s0;
        this.creepStepMax = s1;
        this.knockReach = knock;
        this.bashReach = bash;
        this.inspectReach = insp;
        this.peekReach = peek;
        this.scaleXZ = sx;
        this.scaleY = sy;
        this.eggBg = bg;
        this.eggFg = fg;
        this.molang = molang;
    }

    public String id() {
        return "ghost_" + n;
    }

    /** Translation key of the display name. */
    public String nameKey() {
        return "entity.hollowhours.ghost_" + n;
    }

    public static GhostKind byN(int n) {
        for (GhostKind k : values()) if (k.n == n) return k;
        return WAIL;
    }

    public static GhostKind byKey(String key) {
        for (GhostKind k : values()) if (k.key.equalsIgnoreCase(key)) return k;
        return null;
    }

    /** Crawling hit box (Bedrock size_crawl). */
    public static final float CRAWL_W = 0.9f, CRAWL_H = 1.2f;
}
