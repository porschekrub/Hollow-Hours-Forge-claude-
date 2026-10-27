package com.helperbuddy.entity;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-helper switches (the settings screen). */
public final class Settings {
    public static final String[] ORES = {"coal", "copper", "iron", "gold", "redstone", "lapis", "diamond", "emerald", "nether", "debris"};

    /** name -> default */
    public static final Map<String, Boolean> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("fightBack", true);   // anything that hurts the helper gets hit back (default ON)
        DEFAULTS.put("fight", true);       // defend the owner and attack monsters nearby (creepers included)
        DEFAULTS.put("dig", true);         // dig through stone / dirt to reach ores and blocks
        DEFAULTS.put("replant", true);     // replant saplings and crops
        DEFAULTS.put("pickup", true);      // pick up saplings, sticks, apples, food drops
        DEFAULTS.put("chest", true);       // put things in a nearby chest when the bag is full
        DEFAULTS.put("needBlocks", false); // build with real blocks from the bag (off = free building)
        DEFAULTS.put("chestBuild", true);  // when real blocks are needed, take them from nearby chests
        DEFAULTS.put("keepJunk", false);   // keep all cobblestone/dirt (off = one stack of each)
        DEFAULTS.put("selfCare", true);    // eat, cook and go find food when hungry
        DEFAULTS.put("torches", true);     // place torches in dark places while working (if it has any)
        DEFAULTS.put("teleport", true);    // teleport to the owner when left far behind
    }

    private final Map<String, Boolean> flags = new LinkedHashMap<>(DEFAULTS);
    private final Map<String, Boolean> ores = new LinkedHashMap<>();

    public Settings() {
        for (String o : ORES) ores.put(o, true);
    }

    public boolean get(String key) {
        return flags.getOrDefault(key, DEFAULTS.getOrDefault(key, false));
    }

    public void set(String key, boolean v) {
        if (DEFAULTS.containsKey(key)) flags.put(key, v);
    }

    public boolean ore(String group) {
        return ores.getOrDefault(group, true);
    }

    public void setOre(String group, boolean v) {
        ores.put(group, v);
    }

    public Map<String, Boolean> flags() {
        return flags;
    }

    public Map<String, Boolean> ores() {
        return ores;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        flags.forEach(t::putBoolean);
        CompoundTag o = new CompoundTag();
        ores.forEach(o::putBoolean);
        t.put("ores", o);
        return t;
    }

    public void load(CompoundTag t) {
        for (String k : DEFAULTS.keySet()) if (t.contains(k)) flags.put(k, t.getBoolean(k));
        CompoundTag o = t.getCompound("ores");
        for (String k : ORES) if (o.contains(k)) ores.put(k, o.getBoolean(k));
    }
}
