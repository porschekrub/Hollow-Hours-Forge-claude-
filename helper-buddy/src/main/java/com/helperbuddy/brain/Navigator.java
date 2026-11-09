package com.helperbuddy.brain;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.talk.Nlu;
import com.helperbuddy.talk.Say;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * "Where is the village?" / "take me to a desert": finds structures, biomes and places (also of other mods, by name),
 * says which way and how far, and leads the player there (GUIDE mode), any distance.
 */
public final class Navigator {
    private Navigator() {}

    /** A place that was found. */
    public record Found(String th, String en, BlockPos pos, ResourceKey<Level> dim) {}

    /** The last place each player asked about ("take me there"). */
    public static final Map<UUID, Found> LAST = new HashMap<>();

    // ------------------------------------------------------------------ finding
    /** The place, or null with the reason in why[0] (th) / why[1] (en). */
    @Nullable
    public static Found find(ServerPlayer p, HelperEntity h, Nlu.Place place, String[] why) {
        ServerLevel level = p.serverLevel();
        BlockPos from = p.blockPosition();
        switch (place.kind()) {
            case "coords" -> {
                int[] c = place.pos();
                return new Found("พิกัด " + c[0] + " " + c[1] + " " + c[2], c[0] + " " + c[1] + " " + c[2], new BlockPos(c[0], c[1], c[2]), level.dimension());
            }
            case "me" -> {
                return new Found("ตรงที่คุณยืน", "where you stand", from, level.dimension());
            }
            case "helper" -> {
                return new Found(h.helperName(), h.helperName(), h.blockPosition(), h.level().dimension());
            }
            case "work" -> {
                if (h.home() == null) {
                    why[0] = "ผมยังไม่มีที่ทำงานเลย";
                    why[1] = "I have no work spot yet";
                    return null;
                }
                return new Found("ที่ทำงานของผม", "my work spot", h.home(), h.level().dimension());
            }
            case "bed" -> {
                BlockPos bed = p.getRespawnPosition();
                if (bed == null) {
                    why[0] = "คุณยังไม่เคยนอนเตียงไหนเลย (ยังไม่มีบ้านให้พาไป) ลองวางเตียงแล้วนอนก่อนนะ";
                    why[1] = "You haven't slept in a bed yet, so there's no home to go to. Place a bed and sleep in it first";
                    return null;
                }
                return new Found("บ้านของคุณ (เตียง)", "your home (bed)", bed, p.getRespawnDimension());
            }
            case "spawn" -> {
                ServerLevel ow = p.server.overworld();
                return new Found("จุดเกิดของโลก", "the world spawn", ow.getSharedSpawnPos(), ow.dimension());
            }
            case "death" -> {
                var d = p.getLastDeathLocation();
                if (d.isEmpty()) {
                    why[0] = "คุณยังไม่เคยตายในโลกนี้เลย (เก่งนี่)";
                    why[1] = "You haven't died in this world yet (nice)";
                    return null;
                }
                GlobalPos g = d.get();
                return new Found("ที่คุณตายล่าสุด", "where you last died", g.pos(), g.dimension());
            }
            case "portal" -> {
                BlockPos at = findPortal(level, from, 128);
                if (at == null) {
                    boolean nether = level.dimension() == Level.NETHER;
                    why[0] = "ไม่เจอประตูเนเธอร์ในระยะ 128 บล็อก" + (nether ? " (ในเนเธอร์ประตูจะอยู่ใกล้กว่าในโลกปกติ 8 เท่า ลองเดินไปทางที่มา)"
                            : " สั่ง \"สร้างประตูเนเธอร์\" ได้เลย (ส่งออบซิเดียน 10 ก้อน + หินเหล็กไฟให้ผมก่อน)");
                    why[1] = "No nether portal within 128 blocks" + (nether ? " (in the Nether portals are 8 times closer, try going back the way you came)"
                            : ". Say \"build a nether portal\" (give me 10 obsidian and a flint and steel first)");
                    return null;
                }
                return new Found(place.th(), place.en(), at, level.dimension());
            }
            case "structure" -> {
                Found f = structure(level, from, place.ids(), place.th(), place.en());
                if (f == null) notFound(place, why, level);
                return f;
            }
            case "biome" -> {
                Found f = biome(level, from, place.ids(), place.th(), place.en());
                if (f == null) notFound(place, why, level);
                return f;
            }
            default -> {
                Found f = search(p, level, from, place.text());
                if (f == null) {
                    why[0] = "ข้าไม่รู้จัก \"" + place.text() + "\" (ไม่มีสิ่งก่อสร้าง ไบโอม หรือสัตว์ชื่อนี้ในโลกนี้ รวมทั้งของมอดอื่นด้วย)";
                    why[1] = "I don't know \"" + place.text() + "\" (no structure, biome or creature by that name in this world, other mods included)";
                }
                return f;
            }
        }
    }

    static void notFound(Nlu.Place place, String[] why, ServerLevel level) {
        String dim = level.dimension().location().getPath();
        why[0] = "หา" + place.th() + "ในมิตินี้ (" + dim + ") ไม่เจอเลยในระยะประมาณ 1,600 บล็อก" + (dim.equals("overworld") && place.ids().length > 0
                && (place.ids()[0].contains("fortress") || place.ids()[0].contains("bastion")) ? " (อันนี้อยู่ในเนเธอร์นะ ไปเนเธอร์ก่อน)" : "");
        why[1] = "No " + place.en() + " in this dimension (" + dim + ") within about 1,600 blocks" + (dim.equals("overworld") && place.ids().length > 0
                && (place.ids()[0].contains("fortress") || place.ids()[0].contains("bastion")) ? " (that one is in the Nether)" : "");
    }

    /** The closest nether portal block (from the portal points of interest, loaded or not). */
    @Nullable
    public static BlockPos findPortal(ServerLevel level, BlockPos from, int r) {
        try {
            return level.getPoiManager().findClosest(p -> p.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.NETHER_PORTAL), from, r,
                    net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).orElse(null);
        } catch (Exception e) {
            HelperBuddy.LOG.warn("[Helper Buddy] could not look for portals: {}", e.toString());
            return null;
        }
    }

    /** (tests) */
    @Nullable
    public static BlockPos findStructure(ServerLevel level, BlockPos from, String[] ids) {
        Found f = structure(level, from, ids, "", "");
        return f == null ? null : f.pos();
    }

    @Nullable
    public static BlockPos findBiome(ServerLevel level, BlockPos from, String[] ids) {
        Found f = biome(level, from, ids, "", "");
        return f == null ? null : f.pos();
    }

    @Nullable
    static Found structure(ServerLevel level, BlockPos from, String[] ids, String th, String en) {
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        BlockPos best = null;
        for (String id : ids) {
            BlockPos at = null;
            try {
                if (id.startsWith("#")) {
                    at = level.findNearestMapStructure(TagKey.create(Registries.STRUCTURE, HelperBuddy.rl(id.substring(1))), from, 100, false);
                } else {
                    var holder = reg.getHolder(ResourceKey.create(Registries.STRUCTURE, HelperBuddy.rl(id)));
                    if (holder.isEmpty()) continue;
                    Pair<BlockPos, Holder<Structure>> r = level.getChunkSource().getGenerator()
                            .findNearestMapStructure(level, HolderSet.direct(holder.get()), from, 100, false);
                    if (r != null) at = r.getFirst();
                }
            } catch (Exception e) {
                HelperBuddy.LOG.warn("[Helper Buddy] could not look for {}: {}", id, e.toString());
            }
            if (at != null && (best == null || at.distSqr(from) < best.distSqr(from))) best = at;
        }
        if (best == null) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, best.getX(), best.getZ());
        if (best.getY() == 0 || Math.abs(best.getY() - y) > 40) best = new BlockPos(best.getX(), y, best.getZ());
        return new Found(th, en, best, level.dimension());
    }

    @Nullable
    static Found biome(ServerLevel level, BlockPos from, String[] ids, String th, String en) {
        List<Predicate<Holder<Biome>>> tests = new ArrayList<>();
        for (String id : ids) {
            if (id.startsWith("#")) {
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, HelperBuddy.rl(id.substring(1)));
                tests.add(b -> b.is(tag));
            } else {
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, HelperBuddy.rl(id));
                tests.add(b -> b.is(key));
            }
        }
        // standing in it already: "here"
        Holder<Biome> here = level.getBiome(from);
        for (Predicate<Holder<Biome>> t : tests) if (t.test(here)) return new Found(th, en, from, level.dimension());
        Pair<BlockPos, Holder<Biome>> r = level.findClosestBiome3d(b -> tests.stream().anyMatch(t -> t.test(b)), from, 6400, 32, 64);
        if (r == null) return null;
        BlockPos at = r.getFirst();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        return new Found(th, en, new BlockPos(at.getX(), y, at.getZ()), level.dimension());
    }

    /** Any structure, biome or creature (vanilla or modded) whose id has the words. */
    @Nullable
    static Found search(ServerPlayer p, ServerLevel level, BlockPos from, String words) {
        List<String> toks = new ArrayList<>();
        for (String w : words.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) if (w.length() >= 3 && !w.matches("the|and|for|nearest|closest|where")) toks.add(w);
        if (toks.isEmpty()) return null;
        Predicate<ResourceLocation> named = id -> toks.stream().allMatch(t -> id.getPath().contains(t) || id.getNamespace().equals(t));
        List<String> sIds = new ArrayList<>();
        for (ResourceLocation id : level.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet()) if (named.test(id)) sIds.add(id.toString());
        if (!sIds.isEmpty()) {
            Found f = structure(level, from, sIds.toArray(new String[0]), words, words);
            if (f != null) return f;
        }
        List<String> bIds = new ArrayList<>();
        for (ResourceLocation id : level.registryAccess().registryOrThrow(Registries.BIOME).keySet()) if (named.test(id)) bIds.add(id.toString());
        if (!bIds.isEmpty()) {
            Found f = biome(level, from, bIds.toArray(new String[0]), words, words);
            if (f != null) return f;
        }
        // a creature that is around (loaded): the nearest one
        net.minecraft.world.entity.Entity best = null;
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e == p || !named.test(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))) continue;
            if (best == null || e.distanceToSqr(p) < best.distanceToSqr(p)) best = e;
        }
        if (best != null) return new Found(best.getName().getString(), best.getName().getString(), best.blockPosition(), level.dimension());
        return null;
    }

    // ------------------------------------------------------------------ directions
    static final String[] COMPASS_TH = {"เหนือ", "ตะวันออกเฉียงเหนือ", "ตะวันออก", "ตะวันออกเฉียงใต้", "ใต้", "ตะวันตกเฉียงใต้", "ตะวันตก", "ตะวันตกเฉียงเหนือ"};
    static final String[] COMPASS_EN = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

    /** 0 = north, 90 = east (clockwise). */
    public static double bearing(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        return (a + 360) % 360;
    }

    public static String compass(double deg, boolean th) {
        int i = (int) Math.round(deg / 45.0) % 8;
        return th ? COMPASS_TH[i] : COMPASS_EN[i];
    }

    /** Where it is compared with where the player looks. */
    public static String relative(double bearing, float yaw, boolean th) {
        double facing = ((yaw + 180) % 360 + 360) % 360;
        double rel = ((bearing - facing + 540) % 360) - 180;
        double a = Math.abs(rel);
        if (a < 25) return th ? "ตรงไปข้างหน้าเลย" : "straight ahead";
        if (a > 155) return th ? "อยู่ข้างหลังคุณ" : "behind you";
        String side = rel > 0 ? (th ? "ขวา" : "right") : (th ? "ซ้าย" : "left");
        if (a < 65) return th ? "เฉียงไปทาง" + side + "มือ" : "ahead to your " + side;
        if (a < 115) return th ? "ทาง" + side + "มือ" : "to your " + side;
        return th ? "ข้างหลังเฉียง" + side : "behind you to the " + side;
    }

    public static String describe(ServerPlayer p, Found f, boolean th) {
        if (!p.level().dimension().equals(f.dim())) {
            String d = f.dim().location().getPath();
            return th ? f.th() + " อยู่อีกมิติ (" + d + ") ที่ " + f.pos().getX() + " " + f.pos().getY() + " " + f.pos().getZ()
                    : f.en() + " is in another dimension (" + d + ") at " + f.pos().getX() + " " + f.pos().getY() + " " + f.pos().getZ();
        }
        double dx = f.pos().getX() + 0.5 - p.getX(), dz = f.pos().getZ() + 0.5 - p.getZ();
        int dist = (int) Math.round(Math.hypot(dx, dz));
        int dy = f.pos().getY() - p.blockPosition().getY();
        if (dist < 6 && Math.abs(dy) < 6) return th ? f.th() + " อยู่ตรงนี้แหละ!" : f.en() + " is right here!";
        double b = bearing(dx, dz);
        String y = Math.abs(dy) < 8 ? "" : th ? (dy > 0 ? " สูงกว่าคุณ " + dy + " บล็อก" : " ต่ำกว่าคุณ " + (-dy) + " บล็อก")
                : (dy > 0 ? ", " + dy + " blocks higher" : ", " + (-dy) + " blocks lower");
        return th ? f.th() + " อยู่ทางทิศ§e" + compass(b, true) + "§r (" + relative(b, p.getYRot(), true) + ") ห่างไป §e" + dist + "§r บล็อก (x " + f.pos().getX() + ", z " + f.pos().getZ() + ")" + y
                : f.en() + " is §e" + compass(b, false) + "§r (" + relative(b, p.getYRot(), false) + "), §e" + dist + "§r blocks away (x " + f.pos().getX() + ", z " + f.pos().getZ() + ")" + y;
    }

    // ------------------------------------------------------------------ leading the way (GUIDE mode)
    public static void startGuide(ServerPlayer p, HelperEntity h, Found f) {
        h.brain().leaveJob();
        CompoundTag g = new CompoundTag();
        g.putLong("pos", f.pos().asLong());
        g.putString("dim", f.dim().location().toString());
        g.putString("th", f.th());
        g.putString("en", f.en());
        g.putInt("reported", (int) Math.hypot(f.pos().getX() - p.getX(), f.pos().getZ() - p.getZ()));
        h.jobs.put("guide", g);
        if (h.level() != p.level() || h.distanceTo(p) > 12) h.brain().mover().bringTo(p);
        h.setMode(Mode.GUIDE);
    }

    /** One tick of leading the owner. */
    static void tick(Brain b) {
        HelperEntity h = b.h;
        CompoundTag g = h.jobs.getCompound("guide");
        ServerPlayer o = h.owner();
        if (!h.jobs.contains("guide") || o == null) {
            h.setMode(Mode.FOLLOW);
            return;
        }
        BlockPos target = BlockPos.of(g.getLong("pos"));
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, HelperBuddy.rl(g.getString("dim")));
        boolean th = Say.thai(o);
        if (!o.level().dimension().equals(dim)) {
            if (b.cooldown("guide:dim", 20 * 30)) Say.owner(h, "ที่ที่จะไปอยู่อีกมิติ (" + dim.location().getPath() + ") ไปมิตินั้นก่อน แล้วข้าจะนำทางต่อ",
                    "It's in another dimension (" + dim.location().getPath() + "). Go there first and I'll lead on");
            b.tickFollow();
            return;
        }
        if (h.level() != o.level() || h.distanceTo(o) > 64) {
            b.mover().bringTo(o);
            return;
        }
        double toTarget = Math.hypot(h.getX() - (target.getX() + 0.5), h.getZ() - (target.getZ() + 0.5));
        double ownerToTarget = Math.hypot(o.getX() - (target.getX() + 0.5), o.getZ() - (target.getZ() + 0.5));
        if (ownerToTarget < 8 || toTarget < 4) {
            b.mover().stop();
            h.jobs.remove("guide");
            h.setMode(Mode.FOLLOW);
            Say.owner(h, "ถึงแล้ว! " + g.getString("th") + " อยู่ตรงนี้ (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")",
                    "Here we are! " + g.getString("en") + " (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")");
            h.act("cheer");
            return;
        }
        // progress every ~250 blocks
        int left = (int) ownerToTarget;
        if (g.getInt("reported") - left >= 250) {
            g.putInt("reported", left);
            double br = bearing(target.getX() + 0.5 - o.getX(), target.getZ() + 0.5 - o.getZ());
            Say.owner(h, "อีก " + left + " บล็อก ไปทาง" + compass(br, true) + " (" + relative(br, o.getYRot(), true) + ")",
                    left + " blocks to go, " + compass(br, false) + " (" + relative(br, o.getYRot(), false) + ")");
        }
        double ownerDist = h.distanceTo(o);
        boolean ownerBehind = ownerToTarget > toTarget + 2;
        if (ownerBehind && ownerDist > 20) {
            // wait for the slowpoke
            b.mover().stop();
            b.mover().look(o.getEyePosition());
            if (b.cooldown("guide:wait", 20 * 20))
                Say.owner(h, "ทางนี้! ตามมาเร็วๆ หน่อยสิ", "This way! Keep up");
            return;
        }
        // the next stop: about 16 blocks on toward the target, on the ground
        double br = Math.toRadians(bearing(target.getX() + 0.5 - h.getX(), target.getZ() + 0.5 - h.getZ()));
        double step = Math.min(16, toTarget);
        int wx = (int) Math.floor(h.getX() + Math.sin(br) * step), wz = (int) Math.floor(h.getZ() - Math.cos(br) * step);
        ServerLevel level = (ServerLevel) h.level();
        int wy = toTarget <= 16 ? target.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
        BlockPos way = new BlockPos(wx, wy, wz);
        Mover.Res r = b.mover().moveTo(way, ownerDist < 6 ? 1.3 : 1.15, 1.5);
        if (r == Mover.Res.NO_PATH || b.mover().stuck(60)) {
            // water, cliffs, walls: a helper knows a shortcut
            BlockPos spot = Mover.safeSpotNear(level, way, 4);
            if (spot != null) b.mover().teleportNear(spot);
        }
        if (h.tickCount % 10 == 0) {
            Vec3 a = h.position().add(0, 1.2, 0);
            for (int k = 1; k <= 6; k++)
                level.sendParticles(o, ParticleTypes.END_ROD, true, a.x + Math.sin(br) * k * 0.8, a.y, a.z - Math.cos(br) * k * 0.8, 1, 0, 0, 0, 0);
        }
    }
}
