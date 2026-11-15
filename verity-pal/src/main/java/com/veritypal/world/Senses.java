package com.veritypal.world;

import com.veritypal.V;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** What Verity sees around the player: creatures (and which mod they come from), what they look at and hold, where they are. */
public final class Senses {
    private Senses() {}

    /** "Cave Dweller (mod: The Cave Dweller)" or "Zombie" for the game's own things. */
    public static String modOf(ResourceLocation id) {
        if (id == null || id.getNamespace().equals("minecraft")) return "";
        Optional<String> n = ModList.get().getModContainerById(id.getNamespace()).map(c -> c.getModInfo().getDisplayName());
        return " (mod: " + n.orElse(id.getNamespace()) + ")";
    }

    static String thing(Entity e) {
        return e.getName().getString() + modOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    static String item(ItemStack s) {
        return s.isEmpty() ? "nothing" : s.getCount() + "x " + s.getHoverName().getString() + modOf(BuiltInRegistries.ITEM.getKey(s.getItem()));
    }

    static String block(BlockState s) {
        return s.getBlock().getName().getString() + modOf(BuiltInRegistries.BLOCK.getKey(s.getBlock()));
    }

    public static String lookAround(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        StringBuilder sb = new StringBuilder();
        BlockPos at = p.blockPosition();
        // where
        Holder<Biome> biome = level.getBiome(at);
        String bName = biome.unwrapKey().map(k -> k.location().getPath().replace('_', ' ') + modOf(k.location())).orElse("?");
        sb.append("Biome: ").append(bName).append(". ");
        List<String> structs = new ArrayList<>();
        try {
            for (Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet> e : level.structureManager().getAllStructuresAt(at).entrySet()) {
                ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(e.getKey());
                if (id != null && level.structureManager().getStructureAt(at, e.getKey()).isValid()) structs.add(id.getPath().replace('_', ' ') + modOf(id));
            }
        } catch (Exception ignored) {
        }
        if (!structs.isEmpty()) sb.append("They are inside: ").append(String.join(", ", structs)).append(". ");
        // what they look at
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1), end = eye.add(look.scale(24));
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, eye, end, p.getBoundingBox().expandTowards(look.scale(24)).inflate(1), e -> e != p && !e.isSpectator() && e.isPickable());
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (eh != null && (bh.getType() == HitResult.Type.MISS || eh.getLocation().distanceTo(eye) < bh.getLocation().distanceTo(eye))) {
            Entity e = eh.getEntity();
            sb.append("They are looking at: ").append(V.isVerity(e) ? "you (Verity)" : thing(e)).append(", ").append((int) e.distanceTo(p)).append(" blocks away. ");
        } else if (bh.getType() == HitResult.Type.BLOCK) {
            sb.append("They are looking at: ").append(block(level.getBlockState(bh.getBlockPos()))).append(", ").append((int) Math.sqrt(bh.getBlockPos().distToCenterSqr(eye))).append(" blocks away. ");
        }
        sb.append("Holding: ").append(item(p.getMainHandItem()));
        if (!p.getOffhandItem().isEmpty()) sb.append(" and ").append(item(p.getOffhandItem()));
        sb.append(". ");
        // creatures around, grouped
        Map<String, int[]> seen = new LinkedHashMap<>();
        Map<String, Entity> nearestOf = new LinkedHashMap<>();
        for (Entity e : level.getEntities(p, new AABB(at).inflate(40), x -> x instanceof LivingEntity && x.isAlive() && !V.isVerity(x))) {
            String k = thing(e);
            seen.computeIfAbsent(k, x -> new int[1])[0]++;
            Entity n = nearestOf.get(k);
            if (n == null || e.distanceToSqr(p) < n.distanceToSqr(p)) nearestOf.put(k, e);
        }
        if (seen.isEmpty()) sb.append("No creatures within 40 blocks. ");
        else {
            List<String> l = new ArrayList<>();
            List<Map.Entry<String, Entity>> es = new ArrayList<>(nearestOf.entrySet());
            es.sort((a, b) -> Double.compare(a.getValue().distanceToSqr(p), b.getValue().distanceToSqr(p)));
            for (Map.Entry<String, Entity> e : es) {
                if (l.size() >= 14) break;
                Entity n = e.getValue();
                double b = Places.bearing(n.getX() - p.getX(), n.getZ() - p.getZ());
                l.add(seen.get(e.getKey())[0] + "x " + e.getKey() + " (nearest " + (int) n.distanceTo(p) + " blocks " + Places.compass(b, false) + ")");
            }
            sb.append("Creatures around: ").append(String.join("; ", l)).append(". ");
        }
        // interesting blocks very close
        Map<String, Integer> blocks = new LinkedHashMap<>();
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-6, -4, -6), at.offset(6, 4, 6))) {
            BlockState s = level.getBlockState(q);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
            String path = id.getPath();
            if (path.endsWith("_ore") || path.contains("spawner") || path.contains("chest") || path.contains("portal") || !id.getNamespace().equals("minecraft") && !s.isAir())
                blocks.merge(block(s), 1, Integer::sum);
        }
        if (!blocks.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (Map.Entry<String, Integer> e : blocks.entrySet()) {
                if (l.size() >= 10) break;
                l.add(e.getValue() + "x " + e.getKey());
            }
            sb.append("Notable blocks within 6: ").append(String.join(", ", l)).append('.');
        }
        return sb.toString().trim();
    }

    /** The mods installed (name: what they add), or one mod in detail. */
    public static String mods(String which) {
        String w = which == null ? "" : which.trim().toLowerCase(Locale.ROOT);
        List<IModInfo> all = new ArrayList<>();
        for (IModInfo m : ModList.get().getMods()) {
            String id = m.getModId();
            if (id.equals("minecraft") || id.equals("forge")) continue;
            all.add(m);
        }
        if (!w.isEmpty() && !w.equals("all") && !w.equals("every")) {
            for (IModInfo m : all) {
                if (!m.getModId().toLowerCase(Locale.ROOT).contains(w) && !m.getDisplayName().toLowerCase(Locale.ROOT).contains(w)) continue;
                return detail(m);
            }
            return "No installed mod is called \"" + which + "\". Installed: " + names(all);
        }
        return all.size() + " mods are installed: " + names(all);
    }

    static String names(List<IModInfo> all) {
        List<String> l = new ArrayList<>();
        for (IModInfo m : all) l.add(m.getDisplayName() + " (" + count(m.getModId()) + ")");
        return String.join(", ", l);
    }

    static String count(String ns) {
        int ent = 0, it = 0, bl = 0;
        for (ResourceLocation k : ForgeRegistries.ENTITY_TYPES.getKeys()) if (k.getNamespace().equals(ns)) ent++;
        for (ResourceLocation k : ForgeRegistries.ITEMS.getKeys()) if (k.getNamespace().equals(ns)) it++;
        for (ResourceLocation k : ForgeRegistries.BLOCKS.getKeys()) if (k.getNamespace().equals(ns)) bl++;
        List<String> l = new ArrayList<>();
        if (ent > 0) l.add(ent + " creatures");
        if (bl > 0) l.add(bl + " blocks");
        if (it > 0) l.add(it + " items");
        return l.isEmpty() ? "no new things" : String.join(", ", l);
    }

    static String detail(IModInfo m) {
        String ns = m.getModId();
        StringBuilder sb = new StringBuilder(m.getDisplayName()).append(" ").append(m.getVersion()).append(": ");
        String d = m.getDescription() == null ? "" : m.getDescription().replaceAll("\\s+", " ").trim();
        if (d.length() > 300) d = d.substring(0, 300) + "...";
        sb.append(d).append(" It adds ").append(count(ns)).append('.');
        List<String> ents = new ArrayList<>();
        for (var e : ForgeRegistries.ENTITY_TYPES.getEntries())
            if (e.getKey().location().getNamespace().equals(ns) && ents.size() < 20) ents.add(e.getValue().getDescription().getString());
        if (!ents.isEmpty()) sb.append(" Creatures: ").append(String.join(", ", ents)).append('.');
        List<String> items = new ArrayList<>();
        for (var e : ForgeRegistries.ITEMS.getEntries())
            if (e.getKey().location().getNamespace().equals(ns) && items.size() < 20) items.add(new ItemStack(e.getValue()).getHoverName().getString());
        if (!items.isEmpty()) sb.append(" Items: ").append(String.join(", ", items)).append('.');
        return sb.toString();
    }
}
