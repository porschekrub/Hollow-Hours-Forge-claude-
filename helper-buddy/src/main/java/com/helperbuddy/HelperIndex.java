package com.helperbuddy;

import com.helperbuddy.compat.Compat;
import com.helperbuddy.entity.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where every helper was last seen (saved with the world). A helper left behind in a chunk that is not loaded any more
 * is not in the game's memory: with this its owner can still call it ("come") from anywhere, and helpers that were
 * working go on when their owner comes back into the game.
 */
public final class HelperIndex extends SavedData {
    public record Entry(UUID id, UUID owner, String name, String dim, long pos, String mode) {
        public BlockPos at() {
            return BlockPos.of(pos);
        }
    }

    final Map<UUID, Entry> map = new HashMap<>();

    public static HelperIndex get(MinecraftServer server) {
        return Compat.saved(server.overworld().getDataStorage(), HelperIndex::load, HelperIndex::new, "helperbuddy_helpers");
    }

    static HelperIndex load(CompoundTag t) {
        HelperIndex x = new HelperIndex();
        for (Tag e : t.getList("helpers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            if (!c.hasUUID("id") || !c.hasUUID("owner")) continue;
            x.map.put(c.getUUID("id"), new Entry(c.getUUID("id"), c.getUUID("owner"), c.getString("name"), c.getString("dim"), c.getLong("pos"), c.getString("mode")));
        }
        return x;
    }

    @Override
    //#if MC>=12100
    //$$ public CompoundTag save(CompoundTag t, net.minecraft.core.HolderLookup.Provider registries) {
    //#else
    public CompoundTag save(CompoundTag t) {
    //#endif
        ListTag l = new ListTag();
        for (Entry e : map.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.id());
            c.putUUID("owner", e.owner());
            c.putString("name", e.name());
            c.putString("dim", e.dim());
            c.putLong("pos", e.pos());
            c.putString("mode", e.mode());
            l.add(c);
        }
        t.put("helpers", l);
        return t;
    }

    /** Once a second for every loaded helper. */
    public void note(HelperEntity h) {
        if (h.ownerId() == null) return;
        String dim = h.level().dimension().location().toString();
        Entry old = map.get(h.getUUID());
        long pos = h.blockPosition().asLong();
        String mode = h.mode().name(), name = h.rawName();
        if (old != null && old.dim().equals(dim) && old.mode().equals(mode) && old.name().equals(name) && old.owner().equals(h.ownerId())
                && (old.pos() == pos || old.at().distSqr(h.blockPosition()) < 64)) return;
        map.put(h.getUUID(), new Entry(h.getUUID(), h.ownerId(), name, dim, pos, mode));
        setDirty();
    }

    public void forget(UUID id) {
        if (map.remove(id) != null) setDirty();
    }

    /** The owner's helpers that are not in the game's memory right now. */
    public List<Entry> asleep(MinecraftServer server, UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : map.values()) if (e.owner().equals(owner) && Helpers.byUuid(server, e.id()) == null) out.add(e);
        return out;
    }

    /** Loads the chunks those helpers are in (they show up a moment later). Returns how many are being woken. */
    public int wake(MinecraftServer server, List<Entry> entries) {
        int n = 0;
        for (Entry e : entries) {
            ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, HelperBuddy.rl(e.dim())));
            if (l == null) continue;
            Helpers.hold(l, e.at());
            n++;
        }
        return n;
    }

    /** After a wake-up: helpers that still are not there although their chunk is loaded are gone for good. */
    public void prune(MinecraftServer server, List<Entry> entries) {
        for (Entry e : entries) {
            if (Helpers.byUuid(server, e.id()) != null) continue;
            ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, HelperBuddy.rl(e.dim())));
            BlockPos at = e.at();
            if (l == null || l.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) != null && l.isPositionEntityTicking(at)) forget(e.id());
        }
    }

    public static Level levelOf(MinecraftServer server, Entry e) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, HelperBuddy.rl(e.dim())));
    }
}
