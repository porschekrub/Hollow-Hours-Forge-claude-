package com.veritypal.world;

import com.mojang.datafixers.util.Pair;
import com.veritypal.VerityPal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Finding places by name: structures and biomes (of every mod), creatures around, the player's bed, the world spawn,
 * where they died, a nether portal, coordinates. And telling which way and how far they are.
 */
public final class Places {
    private Places() {}

    public record Found(String name, BlockPos pos, ResourceKey<Level> dim) {}

    /** The last place each player asked about ("take me there"). */
    public static final Map<UUID, Found> LAST = new HashMap<>();

    /** Common names -> structure ids / tags (# = tag), biome ids. */
    static final Map<String, String[]> STRUCTURES = new LinkedHashMap<>();
    static final Map<String, String[]> BIOMES = new LinkedHashMap<>();

    static {
        STRUCTURES.put("village", new String[] {"#minecraft:village"});
        STRUCTURES.put("nether fortress", new String[] {"minecraft:fortress"});
        STRUCTURES.put("fortress", new String[] {"minecraft:fortress"});
        STRUCTURES.put("bastion", new String[] {"minecraft:bastion_remnant"});
        STRUCTURES.put("stronghold", new String[] {"minecraft:stronghold"});
        STRUCTURES.put("end portal", new String[] {"minecraft:stronghold"});
        STRUCTURES.put("ancient city", new String[] {"minecraft:ancient_city"});
        STRUCTURES.put("mansion", new String[] {"minecraft:mansion"});
        STRUCTURES.put("ocean monument", new String[] {"minecraft:monument"});
        STRUCTURES.put("monument", new String[] {"minecraft:monument"});
        STRUCTURES.put("desert temple", new String[] {"minecraft:desert_pyramid"});
        STRUCTURES.put("pyramid", new String[] {"minecraft:desert_pyramid"});
        STRUCTURES.put("jungle temple", new String[] {"minecraft:jungle_pyramid"});
        STRUCTURES.put("temple", new String[] {"minecraft:desert_pyramid", "minecraft:jungle_pyramid"});
        STRUCTURES.put("witch hut", new String[] {"minecraft:swamp_hut"});
        STRUCTURES.put("igloo", new String[] {"minecraft:igloo"});
        STRUCTURES.put("outpost", new String[] {"minecraft:pillager_outpost"});
        STRUCTURES.put("shipwreck", new String[] {"#minecraft:shipwreck"});
        STRUCTURES.put("ruined portal", new String[] {"#minecraft:ruined_portal"});
        STRUCTURES.put("ocean ruin", new String[] {"#minecraft:ocean_ruin"});
        STRUCTURES.put("mineshaft", new String[] {"#minecraft:mineshaft"});
        STRUCTURES.put("trail ruins", new String[] {"minecraft:trail_ruins"});
        STRUCTURES.put("end city", new String[] {"minecraft:end_city"});
        STRUCTURES.put("buried treasure", new String[] {"minecraft:buried_treasure"});
        BIOMES.put("snow", new String[] {"minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes", "minecraft:snowy_slopes"});
        BIOMES.put("ocean", new String[] {"#minecraft:is_ocean"});
        BIOMES.put("mountain", new String[] {"#minecraft:is_mountain"});
        BIOMES.put("forest", new String[] {"#minecraft:is_forest"});
        BIOMES.put("jungle", new String[] {"#minecraft:is_jungle"});
        BIOMES.put("cherry", new String[] {"minecraft:cherry_grove"});
        BIOMES.put("mushroom", new String[] {"minecraft:mushroom_fields"});
        BIOMES.put("badlands", new String[] {"#minecraft:is_badlands"});
        BIOMES.put("mesa", new String[] {"#minecraft:is_badlands"});
        BIOMES.put("swamp", new String[] {"minecraft:swamp", "minecraft:mangrove_swamp"});
        BIOMES.put("beach", new String[] {"#minecraft:is_beach"});
        BIOMES.put("river", new String[] {"#minecraft:is_river"});
        BIOMES.put("savanna", new String[] {"#minecraft:is_savanna"});
        BIOMES.put("taiga", new String[] {"#minecraft:is_taiga"});
    }

    /** The place, or null with the reason in why[0]. */
    @Nullable
    public static Found find(ServerPlayer p, String what, String[] why) {
        ServerLevel level = p.serverLevel();
        BlockPos from = p.blockPosition();
        String w = what == null ? "" : what.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        w = w.replaceAll("^(the|a|an|nearest|closest)\\s+", "").replaceAll("^(the|a|an|nearest|closest)\\s+", "").trim();
        if (w.isEmpty()) {
            why[0] = "no place was named";
            return null;
        }
        // coordinates: "120 64 -300" / "120 -300"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d+)[ ,]+(-?\\d+)(?:[ ,]+(-?\\d+))?").matcher(w);
        if (m.find() && w.replaceAll("[-\\d ,.xyz:=]", "").isBlank()) {
            int x = Integer.parseInt(m.group(1)), a = Integer.parseInt(m.group(2));
            BlockPos at = m.group(3) != null ? new BlockPos(x, a, Integer.parseInt(m.group(3))) : new BlockPos(x, surfaceY(level, x, a, from.getY()), a);
            return new Found("X " + at.getX() + " Y " + at.getY() + " Z " + at.getZ(), at, level.dimension());
        }
        if (w.matches("there|that place|it|last|same place|ที่นั่น|ตรงนั้น")) {
            Found f = LAST.get(p.getUUID());
            if (f == null) why[0] = "the player hasn't asked about any place yet";
            return f;
        }
        if (w.matches("(my )?(home|house|bed|base)|บ้าน|เตียง")) {
            BlockPos bed = p.getRespawnPosition();
            if (bed == null) {
                why[0] = "the player has never slept in a bed in this world (no home known)";
                return null;
            }
            return new Found("your bed", bed, p.getRespawnDimension());
        }
        if (w.matches("(world )?spawn|จุดเกิด")) {
            ServerLevel ow = p.server.overworld();
            return new Found("the world spawn", ow.getSharedSpawnPos(), ow.dimension());
        }
        if (w.matches("(where i |my )?(last )?death( point| spot)?|where i died|ที่ตาย")) {
            var d = p.getLastDeathLocation();
            if (d.isEmpty()) {
                why[0] = "the player hasn't died in this world";
                return null;
            }
            GlobalPos g = d.get();
            return new Found("where you last died", g.pos(), g.dimension());
        }
        if (w.matches("(nether )?portal")) {
            BlockPos at = portal(level, from, 128);
            if (at == null) {
                why[0] = "no nether portal within 128 blocks";
                return null;
            }
            return new Found("the nether portal", at, level.dimension());
        }
        String[] ids = STRUCTURES.get(w);
        if (ids == null && w.endsWith("s")) ids = STRUCTURES.get(w.substring(0, w.length() - 1));
        if (ids != null) {
            Found f = structure(level, from, ids, what);
            if (f == null) why[0] = "no " + what + " in this dimension within about 1,600 blocks";
            return f;
        }
        ids = BIOMES.get(w);
        if (ids != null) {
            Found f = biome(level, from, ids, what);
            if (f == null) why[0] = "no " + what + " biome within about 6,000 blocks";
            return f;
        }
        // an id: "minecraft:ancient_city", "#minecraft:village", a modded one
        if (w.contains(":")) {
            String id = w.replace(' ', '_');
            String bare = id.startsWith("#") ? id.substring(1) : id;
            ResourceLocation rl = ResourceLocation.tryParse(bare);
            if (rl != null) {
                if (id.startsWith("#") || level.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(rl)) {
                    Found f = structure(level, from, new String[] {id}, what);
                    if (f != null) return f;
                }
                if (level.registryAccess().registryOrThrow(Registries.BIOME).containsKey(rl)) {
                    Found f = biome(level, from, new String[] {id}, what);
                    if (f != null) return f;
                }
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                    Entity e = nearestEntity(p, x -> rl.equals(BuiltInRegistries.ENTITY_TYPE.getKey(x.getType())));
                    if (e != null) return new Found(e.getName().getString(), e.blockPosition(), level.dimension());
                }
            }
        }
        Found f = search(p, level, from, w);
        if (f == null) why[0] = "no structure, biome or creature called \"" + what + "\" (other mods included) could be found here";
        return f;
    }

    @Nullable
    static Entity nearestEntity(ServerPlayer p, Predicate<Entity> test) {
        Entity best = null;
        for (Entity e : p.serverLevel().getAllEntities()) {
            if (e == p || !e.isAlive() || !test.test(e)) continue;
            if (best == null || e.distanceToSqr(p) < best.distanceToSqr(p)) best = e;
        }
        return best;
    }

    @Nullable
    public static BlockPos portal(ServerLevel level, BlockPos from, int r) {
        try {
            return level.getPoiManager().findClosest(t -> t.is(PoiTypes.NETHER_PORTAL), from, r, PoiManager.Occupancy.ANY).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    static Found structure(ServerLevel level, BlockPos from, String[] ids, String name) {
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        BlockPos best = null;
        for (String id : ids) {
            BlockPos at = null;
            try {
                if (id.startsWith("#")) {
                    at = level.findNearestMapStructure(TagKey.create(Registries.STRUCTURE, VerityPal.rl(id.substring(1))), from, 100, false);
                } else {
                    var holder = reg.getHolder(ResourceKey.create(Registries.STRUCTURE, VerityPal.rl(id)));
                    if (holder.isEmpty()) continue;
                    Pair<BlockPos, Holder<Structure>> r = level.getChunkSource().getGenerator()
                            .findNearestMapStructure(level, HolderSet.direct(holder.get()), from, 100, false);
                    if (r != null) at = r.getFirst();
                }
            } catch (Exception e) {
                VerityPal.LOG.warn("[Verity Pal] could not look for {}: {}", id, e.toString());
            }
            if (at != null && (best == null || at.distSqr(from) < best.distSqr(from))) best = at;
        }
        if (best == null) return null;
        int y = surfaceY(level, best.getX(), best.getZ(), best.getY() == 0 ? from.getY() : best.getY());
        if (best.getY() == 0 || !level.dimensionType().hasCeiling() && Math.abs(best.getY() - y) > 40) best = new BlockPos(best.getX(), y, best.getZ());
        return new Found(name, best, level.dimension());
    }

    @Nullable
    static Found biome(ServerLevel level, BlockPos from, String[] ids, String name) {
        List<Predicate<Holder<Biome>>> tests = new ArrayList<>();
        for (String id : ids) {
            if (id.startsWith("#")) {
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, VerityPal.rl(id.substring(1)));
                tests.add(b -> b.is(tag));
            } else {
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, VerityPal.rl(id));
                tests.add(b -> b.is(key));
            }
        }
        Holder<Biome> here = level.getBiome(from);
        for (Predicate<Holder<Biome>> t : tests) if (t.test(here)) return new Found(name + " (you are in it)", from, level.dimension());
        Pair<BlockPos, Holder<Biome>> r = level.findClosestBiome3d(b -> tests.stream().anyMatch(t -> t.test(b)), from, 6400, 32, 64);
        if (r == null) return null;
        BlockPos at = r.getFirst();
        return new Found(name, new BlockPos(at.getX(), surfaceY(level, at.getX(), at.getZ(), at.getY()), at.getZ()), level.dimension());
    }

    /** Any structure, biome or creature (of any mod) whose id or name has the words. */
    @Nullable
    static Found search(ServerPlayer p, ServerLevel level, BlockPos from, String words) {
        List<String> toks = new ArrayList<>();
        for (String w : words.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) if (w.length() >= 3 && !w.matches("the|and|for|nearest|closest|where|biome|structure")) toks.add(w);
        if (toks.isEmpty()) {
            // a Thai (or other) name: a creature around whose shown name matches
            String n = words.trim();
            Entity e = nearestEntity(p, x -> x.getName().getString().toLowerCase(Locale.ROOT).contains(n));
            return e == null ? null : new Found(e.getName().getString(), e.blockPosition(), level.dimension());
        }
        Predicate<ResourceLocation> named = id -> toks.stream().allMatch(t -> id.getPath().contains(t) || id.getNamespace().equals(t)
                || t.length() > 3 && t.endsWith("s") && id.getPath().contains(t.substring(0, t.length() - 1)));
        List<String> sIds = new ArrayList<>();
        for (ResourceLocation id : level.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet()) if (named.test(id)) sIds.add(id.toString());
        if (!sIds.isEmpty() && sIds.size() <= 24) {
            Found f = structure(level, from, sIds.toArray(new String[0]), words);
            if (f != null) return f;
        }
        List<String> bIds = new ArrayList<>();
        for (ResourceLocation id : level.registryAccess().registryOrThrow(Registries.BIOME).keySet()) if (named.test(id)) bIds.add(id.toString());
        if (!bIds.isEmpty()) {
            Found f = biome(level, from, bIds.toArray(new String[0]), words);
            if (f != null) return f;
        }
        Entity e = nearestEntity(p, x -> named.test(BuiltInRegistries.ENTITY_TYPE.getKey(x.getType()))
                || toks.stream().allMatch(t -> x.getName().getString().toLowerCase(Locale.ROOT).contains(t)));
        if (e != null) return new Found(e.getName().getString(), e.blockPosition(), level.dimension());
        return null;
    }

    // ------------------------------------------------------------------ the ground
    public static int surfaceY(ServerLevel level, int x, int z, int nearY) {
        BlockPos p = landNear(level, x, z, nearY);
        if (p != null) return p.getY();
        if (level.dimensionType().hasCeiling()) return nearY;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    public static boolean passable(Level l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.getCollisionShape(l, p).isEmpty() && !s.liquid();
    }

    public static boolean standable(Level l, BlockPos p) {
        BlockState below = l.getBlockState(p.below());
        return below.isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP) && passable(l, p) && passable(l, p.above()) && l.getFluidState(p).isEmpty();
    }

    /** Somewhere to stand in the column (x, z), near nearY. Never the Nether's roof. */
    @Nullable
    public static BlockPos landNear(ServerLevel l, int x, int z, int nearY) {
        l.getChunk(x >> 4, z >> 4);
        int min = l.getMinBuildHeight() + 1, max = l.getMaxBuildHeight() - 2;
        if (l.dimensionType().hasCeiling()) max = Math.min(max, l.getMinBuildHeight() + l.dimensionType().logicalHeight() - 3);
        else {
            int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, top, z);
            if (standable(l, p)) return p;
            if (Math.abs(nearY - top) > 48) nearY = top;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= 64; d++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int y = nearY + d * sign;
                if (y >= min && y <= max && standable(l, p.set(x, y, z))) return p.immutable();
                if (d == 0) break;
            }
        }
        return null;
    }

    /** A free spot next to base (base counts), a little up or down. */
    @Nullable
    public static BlockPos safeSpotNear(Level l, BlockPos base, int r) {
        int[][] offs = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int ring = 0; ring <= r; ring++)
            for (int[] o : offs)
                for (int dy = 2; dy >= -4; dy--) {
                    BlockPos p = base.offset(o[0] * Math.max(1, ring), dy, o[1] * Math.max(1, ring));
                    if (standable(l, p)) return p;
                }
        return null;
    }

    // ------------------------------------------------------------------ directions
    static final String[] COMPASS_TH = {"เหนือ", "ตะวันออกเฉียงเหนือ", "ตะวันออก", "ตะวันออกเฉียงใต้", "ใต้", "ตะวันตกเฉียงใต้", "ตะวันตก", "ตะวันตกเฉียงเหนือ"};
    static final String[] COMPASS_EN = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
    static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    /** 0 = north, 90 = east (clockwise). */
    public static double bearing(double dx, double dz) {
        return (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
    }

    public static String compass(double deg, boolean th) {
        int i = (int) Math.round(deg / 45.0) % 8;
        return th ? COMPASS_TH[i] : COMPASS_EN[i];
    }

    /** Degrees from where the player looks to the bearing (-180 .. 180, + = to the right). */
    public static double turn(double bearing, float yaw) {
        double facing = ((yaw + 180) % 360 + 360) % 360;
        return ((bearing - facing + 540) % 360) - 180;
    }

    public static String arrow(double turn) {
        int i = (int) Math.round(((turn + 360) % 360) / 45.0) % 8;
        return ARROWS[i];
    }

    public static String relative(double bearing, float yaw) {
        double rel = turn(bearing, yaw), a = Math.abs(rel);
        if (a < 25) return "straight ahead of them";
        if (a > 155) return "behind them";
        String side = rel > 0 ? "right" : "left";
        if (a < 65) return "ahead to their " + side;
        if (a < 115) return "to their " + side;
        return "behind them to the " + side;
    }

    /** For the AI (English; it tells the player in their language). */
    public static String describe(ServerPlayer p, Found f) {
        if (!p.level().dimension().equals(f.dim()))
            return f.name() + " is in another dimension (" + f.dim().location().getPath() + ") at X " + f.pos().getX() + " Y " + f.pos().getY() + " Z " + f.pos().getZ();
        double dx = f.pos().getX() + 0.5 - p.getX(), dz = f.pos().getZ() + 0.5 - p.getZ();
        int dist = (int) Math.round(Math.hypot(dx, dz));
        int dy = f.pos().getY() - p.blockPosition().getY();
        String at = "X " + f.pos().getX() + " Y " + f.pos().getY() + " Z " + f.pos().getZ();
        if (dist < 6 && Math.abs(dy) < 6) return f.name() + " is right here (" + at + ")";
        double b = bearing(dx, dz);
        String y = Math.abs(dy) < 8 ? "" : dy > 0 ? ", " + dy + " blocks higher" : ", " + (-dy) + " blocks lower";
        return f.name() + " is " + dist + " blocks to the " + compass(b, false) + " (" + relative(b, p.getYRot()) + ")" + y + ", at " + at;
    }
}
