package com.hollowhours.grave;

import com.hollowhours.compat.Compat;
import com.hollowhours.HollowHours;
import com.hollowhours.config.HHConfig;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.registry.HHItems;
import com.hollowhours.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Graves. When you die, everything you carried and all your experience go into a grave where you fell, and you get a
 * compass that points to it. Only you can open it (right-click) or break it (hit it 3 times: it crumbles and takes
 * everything with it). After 15 minutes it crumbles by itself. What a crumbled grave took can be brought back with a
 * Grave Restorer (3 uses). Anything that doesn't fit in your inventory waits and comes to you when there is room.
 */
public final class Graves extends SavedData {
    static final String NAME = "hollowhours_graves";
    /** crumbled graves remembered per player for the restorer */
    static final int KEEP_LOST = 5;

    public static final class Grave {
        public UUID id, owner, entity;
        public String ownerName = "";
        public ResourceKey<Level> dim = Level.OVERWORLD;
        public BlockPos pos = BlockPos.ZERO;
        public long created;
        public int xp;
        public final List<ItemStack> items = new ArrayList<>();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putUUID("Owner", owner);
            if (entity != null) t.putUUID("Entity", entity);
            t.putString("Name", ownerName);
            t.putString("Dim", dim.location().toString());
            t.putLong("Pos", pos.asLong());
            t.putLong("Created", created);
            t.putInt("Xp", xp);
            t.put("Items", saveItems(items));
            return t;
        }

        static Grave load(CompoundTag t) {
            Grave g = new Grave();
            g.id = t.getUUID("Id");
            g.owner = t.getUUID("Owner");
            if (t.hasUUID("Entity")) g.entity = t.getUUID("Entity");
            g.ownerName = t.getString("Name");
            g.dim = ResourceKey.create(Registries.DIMENSION, HollowHours.rl(t.getString("Dim")));
            g.pos = BlockPos.of(t.getLong("Pos"));
            g.created = t.getLong("Created");
            g.xp = t.getInt("Xp");
            g.items.addAll(loadItems(t.getList("Items", Tag.TAG_COMPOUND)));
            return g;
        }
    }

    final Map<UUID, Grave> active = new LinkedHashMap<>();
    final Map<UUID, Deque<Grave>> lost = new HashMap<>();
    final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    static ListTag saveItems(List<ItemStack> items) {
        ListTag l = new ListTag();
        for (ItemStack s : items) if (!s.isEmpty()) l.add(Compat.saveStack(s));
        return l;
    }

    static List<ItemStack> loadItems(ListTag l) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            ItemStack s = Compat.loadStack(l.getCompound(i));
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    @Override
    //#if MC>=12100
    //$$ public CompoundTag save(CompoundTag t, net.minecraft.core.HolderLookup.Provider registries) {
    //#else
    public CompoundTag save(CompoundTag t) {
    //#endif
        ListTag a = new ListTag();
        active.values().forEach(g -> a.add(g.save()));
        t.put("Active", a);
        ListTag l = new ListTag();
        lost.values().forEach(d -> d.forEach(g -> l.add(g.save())));
        t.put("Lost", l);
        ListTag p = new ListTag();
        pending.forEach((who, items) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Owner", who);
            e.put("Items", saveItems(items));
            p.add(e);
        });
        t.put("Pending", p);
        return t;
    }

    static Graves load(CompoundTag t) {
        Graves d = new Graves();
        ListTag a = t.getList("Active", Tag.TAG_COMPOUND);
        for (int i = 0; i < a.size(); i++) {
            Grave g = Grave.load(a.getCompound(i));
            d.active.put(g.id, g);
        }
        ListTag l = t.getList("Lost", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            Grave g = Grave.load(l.getCompound(i));
            d.lost.computeIfAbsent(g.owner, k -> new ArrayDeque<>()).addLast(g);
        }
        ListTag p = t.getList("Pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) {
            CompoundTag e = p.getCompound(i);
            d.pending.put(e.getUUID("Owner"), new ArrayList<>(loadItems(e.getList("Items", Tag.TAG_COMPOUND))));
        }
        return d;
    }

    /** This player's graves that are still standing (the phone's map shows them). */
    public List<Grave> standing(UUID owner) {
        List<Grave> out = new ArrayList<>();
        for (Grave g : active.values()) if (g.owner.equals(owner)) out.add(g);
        return out;
    }

    public static Graves get(MinecraftServer server) {
        return Compat.saved(server.overworld().getDataStorage(), Graves::load, Graves::new, NAME);
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static long lifetime() {
        return HHConfig.GRAVE_MINUTES.get() * 60L * 20L;
    }

    @Nullable
    public Grave active(UUID id) {
        return id == null ? null : active.get(id);
    }

    // ------------------------------------------------------------------ dying
    static boolean makesGrave(ServerPlayer p) {
        return HHConfig.GRAVES.get() && !p.isSpectator() && !p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
    }

    /** All your experience goes into the grave instead of onto the floor. */
    public static void onXp(LivingExperienceDropEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && makesGrave(p)) e.setDroppedExperience(0);
    }

    /** Everything you dropped goes into the grave. */
    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !makesGrave(p)) return;
        List<ItemStack> items = new ArrayList<>();
        for (ItemEntity ie : e.getDrops()) if (!ie.getItem().isEmpty()) items.add(ie.getItem().copy());
        int xp = totalXp(p);
        if (items.isEmpty() && xp <= 0) return;
        e.setCanceled(true);
        create(p, items, xp);
    }

    /** every experience point the player has (not just the 7 per level vanilla drops) */
    static int totalXp(ServerPlayer p) {
        int lvl = p.experienceLevel, pts = 0;
        for (int i = 0; i < lvl; i++) pts += i >= 30 ? 112 + (i - 30) * 9 : i >= 15 ? 37 + (i - 15) * 5 : 7 + i * 2;
        return pts + Math.round(p.experienceProgress * p.getXpNeededForNextLevel());
    }

    static Grave create(ServerPlayer p, List<ItemStack> items, int xp) {
        ServerLevel level = p.serverLevel();
        Graves d = get(p.server);
        Grave g = new Grave();
        g.id = UUID.randomUUID();
        g.owner = p.getUUID();
        g.ownerName = p.getGameProfile().getName();
        g.dim = level.dimension();
        g.pos = restingPlace(level, p.blockPosition());
        g.created = now(p.server);
        g.xp = xp;
        g.items.addAll(items);
        GraveEntity ge = HHEntities.GRAVE.get().create(level);
        if (ge != null) {
            ge.moveTo(g.pos.getX() + 0.5, g.pos.getY(), g.pos.getZ() + 0.5, p.getYRot() + 180, 0);
            ge.setup(g);
            level.addFreshEntity(ge);
            g.entity = ge.getUUID();
        }
        d.active.put(g.id, g);
        d.give(p.getUUID(), GraveCompassItem.forGrave(g));
        d.setDirty();
        p.sendSystemMessage(Component.translatable("hollowhours.grave.made", g.pos.getX(), g.pos.getY(), g.pos.getZ(), HHConfig.GRAVE_MINUTES.get())
                .withStyle(ChatFormatting.GRAY));
        return g;
    }

    /** Where the grave goes: where you fell, but not inside a block, in lava, or under the world. */
    static BlockPos restingPlace(ServerLevel level, BlockPos at) {
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 2;
        BlockPos p = new BlockPos(at.getX(), Math.max(minY, Math.min(maxY, at.getY())), at.getZ());
        if (at.getY() < minY) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
            p = new BlockPos(at.getX(), Math.max(minY, top), at.getZ());
        }
        for (int i = 0; i < 48 && p.getY() < maxY; i++) {
            var s = level.getBlockState(p);
            if (!s.isSuffocating(level, p) && s.getFluidState().isEmpty()) break;
            p = p.above();
        }
        return p;
    }

    // ------------------------------------------------------------------ opening, smashing, crumbling
    /** Right-click by the owner: everything that fits comes back; the rest stays in the grave. */
    static void open(ServerPlayer p, GraveEntity ge, Grave g) {
        Graves d = get(p.server);
        int back = 0;
        Iterator<ItemStack> it = g.items.iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            int before = s.getCount();
            p.getInventory().add(s);
            back += before - s.getCount();
            if (s.isEmpty()) it.remove();
        }
        if (g.xp > 0) {
            giveXp(p, g.xp);
            g.xp = 0;
        }
        p.level().playSound(null, ge.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.8f);
        if (g.items.isEmpty()) {
            d.active.remove(g.id);
            removeCompass(p, g.id);
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.SOUL, ge.getX(), ge.getY() + 0.6, ge.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
            ge.discard();
            Fx.actionbar(p, Component.translatable("hollowhours.grave.opened"));
        } else Fx.actionbar(p, Component.translatable("hollowhours.grave.partial", g.items.size()));
        d.setDirty();
    }

    /** Hit 3 times by its owner: it crumbles, and everything in it is gone (until a restorer brings it back). */
    static void smash(ServerPlayer p, GraveEntity ge, Grave g) {
        Graves d = get(p.server);
        crumble(d, g);
        removeCompass(p, g.id);
        ServerLevel level = (ServerLevel) ge.level();
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState()),
                ge.getX(), ge.getY() + 0.5, ge.getZ(), 40, 0.35, 0.4, 0.35, 0.1);
        level.playSound(null, ge.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2f, 0.6f);
        ge.discard();
        p.sendSystemMessage(Component.translatable("hollowhours.grave.smashed").withStyle(ChatFormatting.RED));
    }

    /** The grave's contents move to the player's crumbled-graves list (newest first, a few kept). */
    static void crumble(Graves d, Grave g) {
        d.active.remove(g.id);
        Deque<Grave> q = d.lost.computeIfAbsent(g.owner, k -> new ArrayDeque<>());
        q.addFirst(g);
        while (q.size() > KEEP_LOST) q.removeLast();
        d.setDirty();
    }

    /** Every second: graves older than 15 minutes crumble; waiting items are handed over when there is room. */
    public static void tick(MinecraftServer server) {
        if (!HHConfig.GRAVES.get() && get(server).active.isEmpty()) return;
        Graves d = get(server);
        long now = now(server), life = lifetime();
        for (Grave g : new ArrayList<>(d.active.values())) {
            if (now - g.created < life) continue;
            crumble(d, g);
            ServerLevel level = server.getLevel(g.dim);
            Entity e = level == null || g.entity == null ? null : level.getEntity(g.entity);
            if (e != null) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
                level.playSound(null, e.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1f, 0.6f);
                e.discard();
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(g.owner);
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("hollowhours.grave.expired").withStyle(ChatFormatting.RED));
                removeCompass(owner, g.id);
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) d.deliver(p);
    }

    // ------------------------------------------------------------------ the restorer, waiting items
    /** The newest crumbled grave of this player, taken off the list (null if there is none). */
    @Nullable
    public static Grave popLost(MinecraftServer server, UUID owner) {
        Graves d = get(server);
        Deque<Grave> q = d.lost.get(owner);
        if (q == null || q.isEmpty()) return null;
        Grave g = q.removeFirst();
        d.setDirty();
        return g;
    }

    /** A restorer: the newest crumbled grave's items and experience come back. */
    static boolean restore(ServerPlayer p) {
        Grave g = popLost(p.server, p.getUUID());
        if (g == null) return false;
        Graves d = get(p.server);
        for (ItemStack s : g.items) d.give(p.getUUID(), s.copy());
        if (g.xp > 0) giveXp(p, g.xp);
        d.deliver(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 1.3f);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.6, 0.4, 0.03);
        p.sendSystemMessage(Component.translatable("hollowhours.restorer.done", g.items.size()).withStyle(ChatFormatting.AQUA));
        return true;
    }

    /** Experience back a level at a time (one big float addition can end a hair short of the last level). */
    static void giveXp(ServerPlayer p, int xp) {
        while (xp > 0) {
            int step = Math.max(1, Math.min(xp, p.getXpNeededForNextLevel() - Math.round(p.experienceProgress * p.getXpNeededForNextLevel())));
            p.giveExperiencePoints(step);
            xp -= step;
        }
    }

    /** Queue an item for a player: it arrives as soon as there is room in their inventory. */
    void give(UUID who, ItemStack s) {
        if (s.isEmpty()) return;
        pending.computeIfAbsent(who, k -> new ArrayList<>()).add(s);
        setDirty();
    }

    public static int waiting(MinecraftServer server, UUID who) {
        List<ItemStack> l = get(server).pending.get(who);
        return l == null ? 0 : l.size();
    }

    void deliver(ServerPlayer p) {
        List<ItemStack> l = pending.get(p.getUUID());
        if (l == null || !p.isAlive() || p.isSpectator()) return;
        boolean got = false;
        Iterator<ItemStack> it = l.iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            int before = s.getCount();
            p.getInventory().add(s);
            if (s.getCount() != before) got = true;
            if (s.isEmpty()) it.remove();
        }
        if (l.isEmpty()) pending.remove(p.getUUID());
        if (got) {
            setDirty();
            p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
            if (!l.isEmpty()) Fx.actionbar(p, Component.translatable("hollowhours.grave.waiting", l.size()));
        } else if (!l.isEmpty() && p.tickCount % 200 < 20) Fx.actionbar(p, Component.translatable("hollowhours.grave.full", l.size()));
    }

    /** The grave's compass is no use once the grave is gone. */
    static void removeCompass(ServerPlayer p, UUID grave) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(HHItems.GRAVE_COMPASS.get()) && grave.equals(GraveCompassItem.graveId(inv.getItem(i)))) inv.setItem(i, ItemStack.EMPTY);
        List<ItemStack> l = get(p.server).pending.get(p.getUUID());
        if (l != null) l.removeIf(s -> s.is(HHItems.GRAVE_COMPASS.get()) && grave.equals(GraveCompassItem.graveId(s)));
    }
}
