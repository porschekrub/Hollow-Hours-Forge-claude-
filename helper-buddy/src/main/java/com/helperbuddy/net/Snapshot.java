package com.helperbuddy.net;

import com.helperbuddy.compat.Compat;
import com.helperbuddy.brain.Bag;
import com.helperbuddy.brain.Brain;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Settings;
import com.helperbuddy.talk.AiBridge;
import com.helperbuddy.talk.Say;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Everything a helper screen shows, sent from the server. */
public final class Snapshot {
    private Snapshot() {}

    public static CompoundTag of(HelperEntity h, ServerPlayer p) {
        boolean th = Say.thai(p);
        Brain b = h.brain();
        CompoundTag t = new CompoundTag();
        t.putInt("id", h.getId());
        t.putUUID("uuid", h.getUUID());
        t.putString("name", h.helperName());
        t.putInt("skin", h.skin());
        t.putFloat("hp", h.shownHp());
        t.putInt("max", h.maxHp());
        t.putInt("level", h.helperLevel());
        t.putFloat("xp", h.xp());
        t.putString("mode", h.mode().name());
        t.putString("modeLabel", h.mode().label(th));
        t.putString("status", h.status());
        t.putBoolean("ko", h.isKO());
        t.put("tool", Compat.saveStack(h.tool()));
        t.putInt("bagUsed", Bag.usedSlots(h));
        t.putInt("bagSize", h.bag.getContainerSize());
        CompoundTag flags = new CompoundTag();
        for (Map.Entry<String, Boolean> e : h.settings.flags().entrySet()) flags.putBoolean(e.getKey(), e.getValue());
        t.put("flags", flags);
        CompoundTag ores = new CompoundTag();
        for (String g : Settings.ORES) ores.putBoolean(g, h.settings.ore(g));
        t.put("ores", ores);
        if (b.plan().active()) t.putString("plan", b.plan().summary(th));
        if (b.build().active()) {
            CompoundTag bt = new CompoundTag();
            bt.putString("name", b.build().name(th));
            bt.putInt("progress", b.build().progress());
            bt.putBoolean("free", b.build().free());
            bt.putInt("missing", b.build().free() ? 0 : b.build().missingCount());
            bt.putString("missingText", b.build().free() ? "" : b.build().missingText(th, 8));
            t.put("build", bt);
        }
        if (b.dig().active()) t.putString("dig", b.dig().statusShort());
        t.putString("owner", h.ownerName());
        t.putString("dim", h.level().dimension().location().getPath());
        t.putInt("x", h.blockPosition().getX());
        t.putInt("y", h.blockPosition().getY());
        t.putInt("z", h.blockPosition().getZ());
        t.putInt("dist", p.level() == h.level() ? (int) p.distanceTo(h) : -1);
        t.putBoolean("ai", AiBridge.enabled());
        t.putBoolean("aiBuild", AiBridge.enabled() && HBConfig.AI_FOR_BUILDING.get());
        t.putBoolean("thai", th);
        t.putBoolean("freeBuild", HBConfig.FREE_BUILD.get());
        return t;
    }
}
