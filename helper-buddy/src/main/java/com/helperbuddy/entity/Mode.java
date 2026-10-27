package com.helperbuddy.entity;

/** What the helper is doing. */
public enum Mode {
    FOLLOW("เดินตาม", "following", 0x55FFFF),
    STAY("ยืนรอ", "waiting", 0xAAAAAA),
    MINE("ขุดแร่", "mining", 0xFFAA00),
    CHOP("ตัดไม้", "chopping wood", 0x00AA00),
    GATHER("เก็บบล็อก", "gathering", 0xFFFF55),
    FOOD("หาอาหาร", "finding food", 0xFF5555),
    GOTO("เดินทาง", "travelling", 0x55FFFF),
    DIG("สร้างเหมือง", "building a mine", 0x777777),
    BUILD("สร้าง", "building", 0xFF55FF),
    GUARD("เฝ้ายาม", "guarding", 0xFF5555),
    FARM("ทำฟาร์ม", "farming", 0x55FF55),
    TASK("ทำงาน", "working", 0xFFFFFF);

    public final String th, en;
    public final int color;

    Mode(String th, String en, int color) {
        this.th = th;
        this.en = en;
        this.color = color;
    }

    public String label(boolean thai) {
        return thai ? th : en;
    }

    public static Mode of(String s) {
        for (Mode m : values()) if (m.name().equalsIgnoreCase(s)) return m;
        return FOLLOW;
    }

    /** Modes where the helper works on its own (does not follow the owner). */
    public boolean working() {
        return this == MINE || this == CHOP || this == GATHER || this == FOOD || this == DIG || this == FARM || this == TASK;
    }
}
