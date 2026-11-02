package com.hollowhours.rescue;

import com.hollowhours.HollowHours;
import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.ghost.Atmosphere;
import com.hollowhours.ghost.Director;
import com.hollowhours.ghost.GhostBrain;
import com.hollowhours.ghost.Story;
import com.hollowhours.item.PhoneItem;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CNotice;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The way out. On day 7 a message comes through: help is on its way, a helicopter will come in 35-100 days, at a set
 * time. When it lands it waits five minutes. Whoever is on board is flown to the haven - a rich, beautiful land with no
 * ghosts. Whoever misses it waits for the next one, and the ghosts take it out on them (twice as cruel, blood moons
 * five times). In the haven, after 5-10 days, they find you anyway: a helicopter waits (as long as it takes) to fly you
 * back where it all began, for good - and there the ghosts are three times as cruel, blood moons six times.
 */
public final class Rescue {
    private Rescue() {}

    public static final ResourceKey<Level> HAVEN = ResourceKey.create(Registries.DIMENSION, HollowHours.id("haven"));
    public static final int WAITING = 0, IN_HAVEN = 1, HOME = 2;
    static final String PHASE = "hollowhours_rescue", MISSED = "hollowhours_rescue_missed", TOLD = "hollowhours_rescue_told",
            INVADE_AT = "hollowhours_invade_at", INVADED = "hollowhours_invaded";
    /** set while we move people between worlds ourselves (portals out of the haven are closed) */
    static boolean transferring;

    public static final class Data extends SavedData {
        /** world day time the next helicopter comes (-1: none planned) */
        long arriveAt = -1;
        /** game time the pickup now under way is over (-1: none) */
        long pickupUntil = -1;
        boolean announced;
        @Nullable
        BlockPos havenSpot;

        static Data load(CompoundTag t) {
            Data d = new Data();
            d.arriveAt = t.getLong("arriveAt");
            d.pickupUntil = t.getLong("pickupUntil");
            d.announced = t.getBoolean("announced");
            if (t.contains("havenSpot")) d.havenSpot = BlockPos.of(t.getLong("havenSpot"));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putLong("arriveAt", arriveAt);
            t.putLong("pickupUntil", pickupUntil);
            t.putBoolean("announced", announced);
            if (havenSpot != null) t.putLong("havenSpot", havenSpot.asLong());
            return t;
        }
    }

    public static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "hollowhours_rescue");
    }

    static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int phase(Player p) {
        return persisted(p).getInt(PHASE);
    }

    public static int missed(Player p) {
        return persisted(p).getInt(MISSED);
    }

    public static boolean invaded(Player p) {
        return persisted(p).getBoolean(INVADED);
    }

    public static boolean inHaven(Entity e) {
        return e.level().dimension() == HAVEN;
    }

    /** Somewhere the ghosts can't get you: on board a helicopter, or in the haven before they find it. */
    public static boolean safe(Player p) {
        return p.getVehicle() instanceof HelicopterEntity || inHaven(p) && !invaded(p);
    }

    /** How much harder the ghosts hit this player (blood moon or not). */
    public static double harshness(Player p, boolean bloodMoon) {
        if (phase(p) == HOME) return bloodMoon ? HHConfig.RETURN_BLOOD_MULT.get() : HHConfig.RETURN_GHOST_MULT.get();
        if (missed(p) > 0) return bloodMoon ? HHConfig.MISSED_BLOOD_MULT.get() : HHConfig.MISSED_GHOST_MULT.get();
        return 1;
    }

    /** The cruellest of the players the ghosts are hunting right now (they come more often). */
    public static double harshest(MinecraftServer server) {
        double m = 1;
        boolean blood = Atmosphere.bloodMoon();
        for (ServerPlayer p : server.overworld().players()) m = Math.max(m, harshness(p, blood));
        return m;
    }

    // ------------------------------------------------------------------ time
    /** "14:30" for a world day time (0 = 6:00). */
    public static String clock(long dayTime) {
        long tod = Math.floorMod(dayTime, 24000L);
        int h = (int) ((tod / 1000 + 6) % 24), m = (int) (tod % 1000 * 60 / 1000);
        return String.format("%02d:%02d", h, m);
    }

    /** Plan the next helicopter: 35-100 days from today, at a random quarter hour. */
    public static void schedule(MinecraftServer server) {
        Data d = data(server);
        long now = server.overworld().getDayTime();
        int days = R.randInt(HHConfig.RESCUE_MIN_DAYS.get(), Math.max(HHConfig.RESCUE_MIN_DAYS.get(), HHConfig.RESCUE_MAX_DAYS.get()));
        int hour = R.randInt(0, 23), quarter = R.randInt(0, 3);
        long tod = Math.floorMod(hour - 6, 24) * 1000L + quarter * 250L;
        d.arriveAt = (now / 24000L + days) * 24000L + tod;
        d.setDirty();
    }

    /** in how many days, which day, what time, how many minutes it waits; how cruel the ghosts are for this player */
    static Object[] when(MinecraftServer server, long at, Player p) {
        long now = server.overworld().getDayTime();
        long days = at / 24000L - now / 24000L;
        return new Object[] {days, at / 24000L + 1, clock(at), HHConfig.RESCUE_PICKUP_MINUTES.get(), fmt(harshness(p, false)), fmt(harshness(p, true))};
    }

    static void notice(ServerPlayer p, String key, boolean danger, Object... args) {
        String[] a = new String[args.length];
        for (int i = 0; i < args.length; i++) a[i] = String.valueOf(args[i]);
        HHNet.toPlayer(p, new S2CNotice(key, a, danger));
        PhoneItem.setMessage(p, key, a.length == 0 ? key : key + "|" + String.join("|", a));
        p.sendSystemMessage(Component.translatable(key, (Object[]) a).withStyle(danger ? ChatFormatting.RED : ChatFormatting.AQUA));
    }

    // ------------------------------------------------------------------ the clock ticks (every 20 ticks)
    public static void tick(MinecraftServer server) {
        if (!HHConfig.RESCUE.get()) return;
        Data d = data(server);
        ServerLevel over = server.overworld();
        long time = over.getDayTime(), game = over.getGameTime();
        // day 7: help is coming
        if (!d.announced && Story.day(over) >= HHConfig.RESCUE_DAY.get() && !com.hollowhours.ghost.GhostEvents.clientSmoke()) {
            d.announced = true;
            schedule(server);
        }
        List<ServerPlayer> waiting = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (phase(p) == WAITING && !p.isSpectator()) waiting.add(p);
        // someone still waiting and nothing planned (they missed it, or joined late): another one is coming
        if (d.announced && d.arriveAt < 0 && d.pickupUntil < 0 && !waiting.isEmpty()) schedule(server);
        if (d.arriveAt > time) for (ServerPlayer p : waiting) {
            if (persisted(p).getLong(TOLD) == d.arriveAt || p.tickCount < 100) continue;
            persisted(p).putLong(TOLD, d.arriveAt);
            notice(p, missed(p) > 0 ? "hollowhours.rescue.again" : "hollowhours.rescue.coming", false, when(server, d.arriveAt, p));
        }
        // it's here
        if (d.arriveAt >= 0 && time >= d.arriveAt) {
            d.arriveAt = -1;
            d.pickupUntil = game + HelicopterEntity.FLY_IN + pickupTicks();
            dispatch(server, waiting);
            d.setDirty();
        }
        // the pickup is over: whoever is still here missed it
        if (d.pickupUntil >= 0) {
            hud(server, waiting);
            if (game > d.pickupUntil + HelicopterEntity.TAKE_OFF + 40 && HelicopterEntity.live(HelicopterEntity.PICKUP).isEmpty()) {
                d.pickupUntil = -1;
                d.setDirty();
                for (ServerPlayer p : waiting) {
                    persisted(p).putInt(MISSED, missed(p) + 1);
                    Fx.playSelf(p, "ghost.growl", 1f, 0.6f);
                }
                if (!waiting.isEmpty()) {
                    schedule(server);
                    for (ServerPlayer p : waiting) {
                        persisted(p).putLong(TOLD, d.arriveAt);
                        notice(p, "hollowhours.rescue.missed", true, when(server, d.arriveAt, p));
                    }
                }
            }
        }
        ServerLevel haven = server.getLevel(HAVEN);
        if (haven != null) for (ServerPlayer p : haven.players()) havenTick(server, haven, p, time, game);
    }

    static int pickupTicks() {
        return HHConfig.RESCUE_PICKUP_MINUTES.get() * 60 * 20;
    }

    /** A helicopter comes down near everyone still waiting in the overworld (one for each group of friends). */
    public static void dispatch(MinecraftServer server, List<ServerPlayer> waiting) {
        List<Vec3> pads = new ArrayList<>();
        for (ServerPlayer p : waiting) {
            if (p.level().dimension() != Level.OVERWORLD || !p.isAlive()) {
                notice(p, "hollowhours.rescue.away", true);
                continue;
            }
            boolean near = false;
            for (Vec3 v : pads) if (v.distanceTo(p.position()) < 96) near = true;
            if (!near) {
                Vec3 pad = landing(p.serverLevel(), p.blockPosition(), 14, 26);
                pads.add(pad);
                HelicopterEntity.arrive(p.serverLevel(), pad, HelicopterEntity.PICKUP, p.position());
            }
            notice(p, "hollowhours.rescue.landing", false, String.valueOf(HHConfig.RESCUE_PICKUP_MINUTES.get()));
        }
    }

    /** "4:32 left, 85 m ->" on the action bar while the helicopter waits. */
    static void hud(MinecraftServer server, List<ServerPlayer> waiting) {
        for (ServerPlayer p : waiting) {
            if (p.getVehicle() instanceof HelicopterEntity h) {
                Fx.actionbarKey(p, h.isLeaving() ? "hollowhours.rescue.hud.leaving" : "hollowhours.rescue.hud.aboard", mmss(h.ticksLeft()));
                continue;
            }
            HelicopterEntity h = HelicopterEntity.nearest(p, HelicopterEntity.PICKUP);
            if (h == null || h.isLeaving()) continue;
            Fx.actionbarKey(p, "hollowhours.rescue.hud.pickup", mmss(h.ticksLeft()), Math.round(h.distanceTo(p)), arrow(p, h.position()));
        }
    }

    static String mmss(int ticks) {
        int s = Math.max(0, ticks / 20);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    /** an arrow pointing from where you look to there */
    static String arrow(Player p, Vec3 to) {
        double dx = to.x - p.getX(), dz = to.z - p.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        int i = Math.floorMod(Math.round(Mth.wrapDegrees(yaw - p.getYRot()) / 45f), 8);
        return new String[] {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[i];
    }

    // ------------------------------------------------------------------ the haven
    static void havenTick(MinecraftServer server, ServerLevel haven, ServerPlayer p, long time, long game) {
        if (phase(p) != IN_HAVEN || p.isSpectator()) return;
        CompoundTag d = persisted(p);
        if (!d.getBoolean(INVADED)) {
            if (time < d.getLong(INVADE_AT)) return;
            d.putBoolean(INVADED, true);
            Atmosphere.send(p);
            notice(p, "hollowhours.rescue.invasion", true);
            Fx.playSelf(p, "ghost.snap", 1f, 0.7f);
            Fx.shake(p, 0.6f, 30);
        }
        if (p.getVehicle() instanceof HelicopterEntity h) {
            Fx.actionbarKey(p, h.isLeaving() ? "hollowhours.rescue.hud.leaving" : "hollowhours.rescue.hud.aboard_out");
            return;
        }
        // the helicopter waits somewhere out there, for as long as it takes
        HelicopterEntity h = HelicopterEntity.nearest(p, HelicopterEntity.EXTRACT);
        if (h == null) {
            Vec3 pad = landing(haven, p.blockPosition(), 35, 60);
            h = HelicopterEntity.arrive(haven, pad, HelicopterEntity.EXTRACT, p.position());
        }
        if (h != null) Fx.actionbarKey(p, "hollowhours.rescue.hud.extract", Math.round(h.distanceTo(p)), arrow(p, h.position()));
        // and they hunt you all the way there
        if (game % 200 < 20 && p.isAlive()) {
            int want = haven.isNight() ? 3 : 2;
            if (GhostBrain.ghostsNear(haven, p.position(), 64).size() < want) {
                Vec3 spot = Director.findSpot(p, false, 18, 30);
                if (spot != null) {
                    GhostEntity g = Director.spawnGhost(haven, Director.randomKind(), spot);
                    if (g != null) Later.run(25, () -> {
                        if (g.isAlive() && p.isAlive() && !p.hasDisconnected() && !safe(p)) g.brain().startChase(p, haven.getGameTime());
                    });
                }
            }
        }
    }

    /** The surface at a column, generating the chunk if it isn't there yet (getHeightmapPos only knows loaded chunks). */
    static BlockPos top(ServerLevel level, Heightmap.Types type, BlockPos at) {
        var chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        return new BlockPos(at.getX(), chunk.getHeight(type, at.getX() & 15, at.getZ() & 15) + 1, at.getZ());
    }

    /** Where helicopters set down in the haven: open dry land near the middle of the world. */
    public static BlockPos havenSpot(ServerLevel haven) {
        Data d = data(haven.getServer());
        if (d.havenSpot != null) return d.havenSpot;
        // one sample in the middle of a chunk per ring step (each may generate that chunk - kept few): dry, inland, the flattest wins
        BlockPos best = null, dry = null;
        int bestBump = Integer.MAX_VALUE;
        search:
        for (int r = 0; r <= 320; r += 64) {
            int n = Math.max(1, r / 40);
            for (int a = 0; a < n; a++) {
                double ang = a * Math.PI * 2 / n;
                int x = (((int) (Math.cos(ang) * r)) >> 4 << 4) + 8, z = (((int) (Math.sin(ang) * r)) >> 4 << 4) + 8;
                BlockPos top = top(haven, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (!haven.getFluidState(top.below()).isEmpty() || !haven.getBlockState(top.below()).isSolid() || top.getY() <= haven.getSeaLevel()) continue;
                if (dry == null) dry = top;
                String biome = haven.getBiome(top).unwrapKey().map(k -> k.location().getPath()).orElse("");
                if (biome.equals("haven_beach") || biome.equals("haven_lagoon") || top.getY() > haven.getSeaLevel() + 40) continue;
                int bump = 0;
                for (int[] o : new int[][] {{5, 0}, {-5, 0}, {0, 5}, {0, -5}})
                    bump = Math.max(bump, Math.abs(top(haven, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.offset(o[0], 0, o[1])).getY() - top.getY()));
                if (bump < bestBump) {
                    best = top;
                    bestBump = bump;
                }
                if (bestBump <= 1) break search;
            }
        }
        // all sea so far: look farther out for any dry land (a few more chunks)
        if (best == null && dry == null)
            far:
            for (int r = 448; r <= 1024; r += 128) {
                int n = r / 96;
                for (int a = 0; a < n; a++) {
                    double ang = a * Math.PI * 2 / n;
                    BlockPos t = top(haven, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos((int) (Math.cos(ang) * r) >> 4 << 4 | 8, 0, (int) (Math.sin(ang) * r) >> 4 << 4 | 8));
                    if (haven.getFluidState(t.below()).isEmpty() && haven.getBlockState(t.below()).isSolid() && t.getY() > haven.getSeaLevel()) {
                        dry = t;
                        break far;
                    }
                }
            }
        if (best == null) best = dry;
        // still nothing: a little island of grass in the sea
        if (best == null) {
            int y = haven.getSeaLevel() + 1;
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++)
                    for (int dy = -3; dy < 0; dy++)
                        haven.setBlockAndUpdate(new BlockPos(8 + dx, y + dy, 8 + dz), dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
            best = new BlockPos(8, y, 8);
        }
        d.havenSpot = best;
        d.setDirty();
        return best;
    }

    /** A flat open spot on the surface min..max blocks from a place (no trees, no water); the place itself if none. */
    public static Vec3 landing(ServerLevel level, BlockPos near, int min, int max) {
        for (int i = 0; i < 80; i++) {
            double ang = R.rand(0, Math.PI * 2), dist = R.rand(min, max);
            int x = near.getX() + (int) (Math.cos(ang) * dist), z = near.getZ() + (int) (Math.sin(ang) * dist);
            BlockPos top = top(level, Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
            if (!level.getFluidState(top.below()).isEmpty() || !level.getBlockState(top.below()).isSolid()) continue;
            boolean flat = true;
            for (int dx = -3; dx <= 3 && flat; dx += 3)
                for (int dz = -4; dz <= 4 && flat; dz += 4) {
                    BlockPos t = top(level, Heightmap.Types.MOTION_BLOCKING, new BlockPos(x + dx, 0, z + dz));
                    if (Math.abs(t.getY() - top.getY()) > 1 || !level.getFluidState(t.below()).isEmpty()) flat = false;
                }
            if (flat) return new Vec3(x + 0.5, top.getY(), z + 0.5);
        }
        BlockPos top = top(level, Heightmap.Types.MOTION_BLOCKING, near);
        return new Vec3(near.getX() + 0.5, top.getY(), near.getZ() + 0.5);
    }

    // ------------------------------------------------------------------ the flights
    /** The pickup made it: on to the haven. */
    public static void toHaven(List<ServerPlayer> all) {
        List<ServerPlayer> players = all.stream().filter(p -> !p.hasDisconnected() && p.isAlive()).toList();
        if (players.isEmpty()) return;
        MinecraftServer server = players.get(0).getServer();
        ServerLevel haven = server == null ? null : server.getLevel(HAVEN);
        if (haven == null) {
            HollowHours.LOG.error("[Hollow Hours] the haven dimension is missing: nobody can be rescued");
            return;
        }
        BlockPos spot = havenSpot(haven);
        long invade = server.overworld().getDayTime()
                + (long) R.rand(HHConfig.INVASION_MIN_DAYS.get() * 24000.0, Math.max(HHConfig.INVASION_MIN_DAYS.get(), HHConfig.INVASION_MAX_DAYS.get()) * 24000.0);
        int i = 0;
        for (ServerPlayer p : players) {
            CompoundTag d = persisted(p);
            d.putInt(PHASE, IN_HAVEN);
            d.putLong(INVADE_AT, invade);
            d.putBoolean(INVADED, false);
            // each on the ground of their own spot (never inside a hillside)
            double a = i++ * 1.3;
            BlockPos at = top(haven, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(spot.getX() + 0.5 + Math.cos(a) * 2.5, 0, spot.getZ() + 0.5 + Math.sin(a) * 2.5));
            move(p, haven, Vec3.atBottomCenterOf(at));
            Later.run(60, () -> {
                if (p.hasDisconnected()) return;
                Atmosphere.send(p);
                notice(p, "hollowhours.rescue.arrived", false);
                Fx.playSelf(p, "phone.notify", 0.7f, 1.2f);
            });
        }
        HelicopterEntity.leaveFrom(haven, Vec3.atBottomCenterOf(spot).add(0, 0, 9));
    }

    /** The extraction made it: back where it all began, for good. */
    public static void home(List<ServerPlayer> all) {
        List<ServerPlayer> players = all.stream().filter(p -> !p.hasDisconnected() && p.isAlive()).toList();
        if (players.isEmpty()) return;
        MinecraftServer server = players.get(0).getServer();
        if (server == null) return;
        ServerLevel over = server.overworld();
        Vec3 last = null;
        for (ServerPlayer p : players) {
            persisted(p).putInt(PHASE, HOME);
            persisted(p).remove(INVADED);
            // a bed in the haven doesn't take you back there
            if (p.getRespawnDimension() == HAVEN) p.setRespawnPosition(Level.OVERWORLD, null, 0, false, false);
            Vec3 to = null;
            if (p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD) {
                Optional<Vec3> v = Player.findRespawnPositionAndUseSpawnBlock(over, p.getRespawnPosition(), p.getRespawnAngle(), p.isRespawnForced(), false);
                if (v.isPresent()) to = v.get();
            }
            if (to == null) {
                BlockPos s = top(over, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, over.getSharedSpawnPos());
                to = Vec3.atBottomCenterOf(s);
            }
            move(p, over, to);
            last = to;
            Later.run(60, () -> {
                if (p.hasDisconnected()) return;
                Atmosphere.send(p);
                notice(p, "hollowhours.rescue.home", true,
                        fmt(HHConfig.RETURN_GHOST_MULT.get()), fmt(HHConfig.RETURN_BLOOD_MULT.get()));
            });
        }
        if (last != null) HelicopterEntity.leaveFrom(over, landing(over, BlockPos.containing(last), 8, 14));
    }

    static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }

    static void move(ServerPlayer p, ServerLevel to, Vec3 at) {
        transferring = true;
        try {
            p.stopRiding();
            p.teleportTo(to, at.x, at.y + 0.1, at.z, p.getYRot(), 0);
            p.fallDistance = 0;
        } finally {
            transferring = false;
        }
    }

    // ------------------------------------------------------------------ tests
    /** CI: the planned helicopter comes right now */
    public static void arriveNow(MinecraftServer server) {
        Data d = data(server);
        if (d.arriveAt < 0) schedule(server);
        d.arriveAt = server.overworld().getDayTime();
        d.setDirty();
    }

    /** CI: the ghosts find the haven right now */
    public static void invadeNow(ServerPlayer p) {
        persisted(p).putLong(INVADE_AT, 0);
    }

    /** CI: back to the start of the story */
    public static void reset(ServerPlayer p) {
        CompoundTag d = persisted(p);
        for (String k : new String[] {PHASE, MISSED, TOLD, INVADE_AT, INVADED}) d.remove(k);
    }

    public static long nextArrival(MinecraftServer server) {
        return data(server).arriveAt;
    }

    public static void cancelPlan(MinecraftServer server) {
        Data d = data(server);
        d.arriveAt = -1;
        d.setDirty();
    }

    // ------------------------------------------------------------------ events
    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOW, Rescue::onRespawn);
        bus.addListener(Rescue::onTravel);
        bus.addListener(EventPriority.HIGH, Rescue::onMount);
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && phase(p) == WAITING) persisted(p).remove(TOLD);
        });
    }

    /** Died in the haven: you wake up in the haven again (unless your bed is there anyway). Never back there otherwise. */
    static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isEndConquered()) return;
        if (phase(p) == IN_HAVEN && !inHaven(p)) {
            ServerLevel haven = p.server.getLevel(HAVEN);
            if (haven == null) return;
            move(p, haven, Vec3.atBottomCenterOf(top(haven, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, havenSpot(haven))));
        } else if (phase(p) != IN_HAVEN && inHaven(p)) {
            ServerLevel over = p.server.overworld();
            move(p, over, Vec3.atBottomCenterOf(top(over, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, over.getSharedSpawnPos())));
        } else return;
        Later.run(10, () -> Atmosphere.send(p));
    }

    /** No portals out of the haven: the helicopter is the only way back. */
    static void onTravel(EntityTravelToDimensionEvent e) {
        Entity en = e.getEntity();
        if (transferring || !inHaven(en)) return;
        var state = en.level().getBlockState(en.blockPosition());
        if (!state.is(Blocks.NETHER_PORTAL) && !state.is(Blocks.END_PORTAL) && !state.is(Blocks.END_GATEWAY)) return;
        e.setCanceled(true);
        if (en instanceof ServerPlayer p && en.tickCount % 40 == 0) Fx.actionbarKey(p, "hollowhours.rescue.portal");
    }

    /** Once it takes off nobody gets out. */
    static void onMount(EntityMountEvent e) {
        if (transferring || !e.isDismounting() || !(e.getEntityBeingMounted() instanceof HelicopterEntity h)) return;
        if (h.isLeaving() && e.getEntityMounting().isAlive() && !h.level().isClientSide) e.setCanceled(true);
    }
}
