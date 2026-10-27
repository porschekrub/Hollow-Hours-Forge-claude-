package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CMusic;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The haunting itself (port of main.js spawnTick / musicTick / dreadTick / fake messages):
 * omens first (strange sounds, music), sometimes a false alarm, then a ghost appears behind someone,
 * comes knocking if they are indoors, or a stalker shows up instead. Plus music, heartbeat and whispers.
 */
public final class Director {
    private Director() {}

    static final int MIN = 1200;
    public static int forcedPower = 0;
    private static boolean started;
    private static long nextSpawnAt, nextFakeJoinAt, lastFakeJoin = -100000;
    private static final Map<Integer, Long> nextAt = new HashMap<>();
    private static final Map<Integer, Boolean> wasAlive = new HashMap<>();
    private static Omen omen;
    private static final Map<UUID, Long> indoorNext = new HashMap<>();
    private static final Map<UUID, Long> ambientNext = new HashMap<>();
    private static final Map<UUID, String> musicSent = new HashMap<>();
    private static final Map<UUID, Long> musicWantedAt = new HashMap<>();
    private static final Map<UUID, long[]> dread = new HashMap<>();
    private static final Map<UUID, List<String>> scareBags = new HashMap<>();

    private record Omen(UUID player, long start, long end, boolean falseAlarm) {}

    public static void reset() {
        started = false;
        omen = null;
        nextAt.clear();
        wasAlive.clear();
        indoorNext.clear();
        ambientNext.clear();
        musicSent.clear();
        dread.clear();
    }

    static double rand(double a, double b) {
        return R.rand(a, b);
    }

    static long nextGap() {
        double g = R.chance(HHConfig.QUICK_CHANCE.get()) ? rand(4 * MIN, 6 * MIN) : rand(HHConfig.GAP_MIN.get() * 20, HHConfig.GAP_MAX.get() * 20);
        g *= Variety.moodGap();
        if (Atmosphere.bloodMoon()) g /= HHConfig.BLOOD_SPAWN_MULT.get();
        return (long) g;
    }

    /** The moon just rose red: the next hunt comes soon. */
    static void bloodMoonRose(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (started) nextSpawnAt = Math.min(nextSpawnAt, now + (long) rand(40 * 20, 100 * 20));
    }

    static int maxAlive() {
        int m = HHConfig.MAX_ALIVE.get();
        return Atmosphere.bloodMoon() ? Math.max(m + 1, 2) : m;
    }

    // ------------------------------------------------------------------ hunting ghosts alive
    /** Live ghosts on the server (kept up to date by GhostEntity.onAddedToWorld / onRemovedFromWorld). */
    public static final java.util.Set<GhostEntity> LIVE = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static List<GhostEntity> allGhosts(MinecraftServer server) {
        List<GhostEntity> out = new ArrayList<>();
        for (GhostEntity g : LIVE) if (g.isAlive() && !g.isRemoved()) out.add(g);
        return out;
    }

    public static boolean ghostBusy(MinecraftServer server) {
        if (omen != null) return true;
        return !allGhosts(server).isEmpty();
    }

    private static boolean huntAllowedIn(ServerLevel l) {
        return l.dimension() == Level.OVERWORLD || HHConfig.GHOSTS_IN_NETHER.get();
    }

    // ------------------------------------------------------------------ main schedule (every 40 ticks)
    public static void spawnTick(MinecraftServer server, long now) {
        if (!started) {
            started = true;
            nextSpawnAt = now + (long) rand(HHConfig.FIRST_SPAWN_MIN.get() * 20, HHConfig.FIRST_SPAWN_MAX.get() * 20);
            for (GhostKind k : GhostKind.values()) {
                nextAt.put(k.n, 0L);
                wasAlive.put(k.n, false);
            }
            nextFakeJoinAt = now + (long) rand(5 * MIN, 14 * MIN);
        }
        if (HHConfig.FAKE_MESSAGES.get() && now >= nextFakeJoinAt) {
            nextFakeJoinAt = now + (long) rand(5 * MIN, 14 * MIN);
            if (!server.getPlayerList().getPlayers().isEmpty()) fakeJoin(server, now);
        }
        Map<Integer, Integer> alive = new HashMap<>();
        int total = 0;
        for (GhostEntity g : allGhosts(server)) {
            if (g.brainIfPresent() != null && g.brain().scareOnly) continue;
            alive.merge(g.kind.n, 1, Integer::sum);
            total++;
        }
        for (GhostKind k : GhostKind.values()) {
            int a = alive.getOrDefault(k.n, 0);
            if (wasAlive.getOrDefault(k.n, false) && a == 0) nextAt.put(k.n, now + (long) rand(HHConfig.RESPAWN_MIN.get() * 20, HHConfig.RESPAWN_MAX.get() * 20));
            wasAlive.put(k.n, a > 0);
        }
        ServerLevel over = server.overworld();
        // ---- random creepy sounds while nothing hunts (more in caves)
        if (omen == null && total == 0) {
            for (ServerPlayer pl : over.players()) {
                if (!GhostBrain.targetable(pl)) continue;
                if (R.chance(W.isUnderground(over, pl.position()) ? 0.012 : 0.003)) mimicSound(pl);
            }
            // ---- indoors: knocks, or a strange sound and a stalker at the window
            boolean night = W.isNight(over);
            for (ServerPlayer pl : over.players()) {
                if (!GhostBrain.targetable(pl) || Hiding.isHidden(pl) || !W.isSheltered(pl)) continue;
                Long at = indoorNext.get(pl.getUUID());
                if (at == null) {
                    indoorNext.put(pl.getUUID(), now + (long) rand(3 * MIN, 6 * MIN));
                    continue;
                }
                if (now < at) continue;
                if (nextSpawnAt - now < 2 * MIN) {
                    indoorNext.put(pl.getUUID(), now + (long) rand(MIN, 2 * MIN));
                    continue;
                }
                indoorNext.put(pl.getUUID(), now + (long) (night ? rand(8 * MIN, 14 * MIN) : rand(14 * MIN, 22 * MIN)));
                boolean ok = HHConfig.STALKERS_ENABLED.get() ? Stalkers.indoor(pl) : GhostVisit.startKnockVisit(pl, randomKind());
                if (ok) {
                    nextSpawnAt = Math.max(nextSpawnAt, now + 90 * 20);
                    break;
                }
            }
        }
        // ---- omen in progress
        if (omen != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(omen.player);
            if (p == null || total > 0) {
                omen = null;
                nextSpawnAt = now + nextGap();
                return;
            }
            double progress = Math.min(1, (now - omen.start) / (double) Math.max(1, omen.end - omen.start));
            if (R.chance(0.7)) omenSound(p, progress);
            if (now < omen.end) return;
            Omen o = omen;
            omen = null;
            if (o.falseAlarm) {
                nextSpawnAt = now + (long) rand(4 * MIN, 8 * MIN);
                return;
            }
            List<Integer> ready = new ArrayList<>();
            for (GhostKind k : GhostKind.values()) if (alive.getOrDefault(k.n, 0) == 0 && now >= nextAt.getOrDefault(k.n, 0L)) ready.add(k.n);
            if (!ready.isEmpty() && R.chance(HHConfig.HOUSE_EVENT_CHANCE.get()) && W.isSheltered(p)) {
                GhostKind pk = GhostKind.byN(R.pick(ready));
                boolean ok = R.chance(0.35) ? GhostVisit.startKnock(p, pk, R.chance(0.65)) : GhostVisit.startBreakIn(p, pk);
                if (ok) {
                    nextSpawnAt = now + nextGap();
                    return;
                }
            }
            if (HHConfig.STALKERS_ENABLED.get() && R.chance(W.isUnderground(p.level(), p.position()) ? 0.2 : HHConfig.GLIMPSE_CHANCE.get())) {
                nextSpawnAt = now + (Stalkers.outdoor(p) ? nextGap() : (long) rand(2 * MIN, 4 * MIN));
                return;
            }
            Vec3 spot = ready.isEmpty() ? null : findSpot(p, true, 22, 30);
            if (spot == null) {
                nextSpawnAt = now + (long) rand(4 * MIN, 8 * MIN);
                return;
            }
            int first = R.pick(ready);
            spawnGhost(p.serverLevel(), GhostKind.byN(first), spot);
            // blood moon: sometimes two come together, from different sides
            if (Atmosphere.bloodMoonFor(p.level()) && total + 1 < maxAlive() && R.chance(HHConfig.BLOOD_DOUBLE_CHANCE.get())) {
                ready.remove((Integer) first);
                Vec3 spot2 = ready.isEmpty() ? null : findSpot(p, false, 20, 32);
                if (spot2 != null) {
                    GhostKind second = GhostKind.byN(R.pick(ready));
                    Later.run((int) rand(20, 80), () -> {
                        if (p.isAlive() && !p.hasDisconnected()) spawnGhost(p.serverLevel(), second, spot2);
                    });
                }
            }
            nextSpawnAt = now + nextGap();
            return;
        }
        // ---- time for a new omen?
        if (total >= maxAlive() || now < nextSpawnAt) return;
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            if (!huntAllowedIn(l)) continue;
            for (ServerPlayer p : l.players()) if (GhostBrain.targetable(p) && !KillSequence.isVictim(p)) players.add(p);
        }
        if (players.isEmpty()) {
            nextSpawnAt = now + 2 * MIN;
            return;
        }
        if (HHConfig.ONLY_AT_NIGHT.get() && !W.isNight(over)) {
            nextSpawnAt = now + MIN;
            return;
        }
        ServerPlayer p = R.pick(players);
        long len = (long) (rand(15 * 20, 40 * 20) * (Atmosphere.bloodMoon() ? 0.5 : 1));
        omen = new Omen(p.getUUID(), now, now + len, R.chance(HHConfig.FALSE_ALARM_CHANCE.get()));
        String k = R.chance(0.5) ? "ambient1" : "ambient2";
        sendMusic(p, k, now);
        ambientNext.put(p.getUUID(), now + 50 * 20 + (long) rand(4 * MIN, 10 * MIN));
    }

    public static GhostKind randomKind() {
        return GhostKind.values()[R.randInt(0, GhostKind.values().length - 1)];
    }

    @Nullable
    public static GhostEntity spawnGhost(ServerLevel level, GhostKind kind, Vec3 at) {
        GhostEntity g = HHEntities.ghost(kind).create(level);
        if (g == null) return null;
        g.moveTo(at.x, at.y, at.z, R.pitch(0, 360), 0);
        level.addFreshEntity(g);
        Fx.sound(level, "ghost.g" + kind.n + ".spawn", at, 2f, 1f, 64);
        return g;
    }

    /** A cursed item calls a ghost right now (if none is out). */
    public static boolean summonGhost(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null || !allGhosts(server).isEmpty()) return false;
        Vec3 spot = findSpot(p, true, 22, 30);
        if (spot == null) return false;
        return spawnGhost(p.serverLevel(), randomKind(), spot) != null;
    }

    // ------------------------------------------------------------------ spawn spot behind the player
    @Nullable
    public static Vec3 findSpot(ServerPlayer player, boolean behind, double dmin, double dmax) {
        ServerLevel level = player.serverLevel();
        int px = Mth.floor(player.getX()), py = Mth.floor(player.getY()), pz = Mth.floor(player.getZ());
        boolean underground = W.isUnderground(level, player.position());
        Double backAng = null;
        if (behind) {
            Vec3 v = player.getViewVector(1f);
            backAng = Math.atan2(v.z, v.x) + Math.PI;
        }
        for (int t = 0; t < 30; t++) {
            double ang = (backAng != null && t < 20) ? backAng + rand(-0.9, 0.9) : rand(0, Math.PI * 2);
            double dist = rand(dmin, dmax);
            int x = Mth.floor(px + Math.cos(ang) * dist), z = Mth.floor(pz + Math.sin(ang) * dist);
            if (!level.hasChunkAt(new BlockPos(x, py, z))) continue;
            if (underground) {
                for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6}) {
                    BlockPos c = new BlockPos(x, py + dy, z);
                    if (W.passable(level, c) && W.passable(level, c.above()) && W.passable(level, c.above(2)) && !W.passable(level, c.below())
                            && level.getBlockState(c).getFluidState().isEmpty()) return new Vec3(x + 0.5, c.getY(), z + 0.5);
                }
            } else {
                BlockPos top = W.topBlock(level, x + 0.5, z + 0.5, py + 40, py - 40);
                if (top == null) continue;
                BlockState s = level.getBlockState(top);
                if (s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || s.getBlock() instanceof LeavesBlock) continue;
                if (!s.getFluidState().isEmpty()) return new Vec3(x + 0.5, top.getY(), z + 0.5);
                return new Vec3(x + 0.5, top.getY() + 1, z + 0.5);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ omens & mimicry
    public static void mimicSound(ServerPlayer p) {
        Vec3 l = p.position();
        double a = rand(0, Math.PI * 2), d = rand(4, 14);
        double x = l.x + Math.cos(a) * d, z = l.z + Math.sin(a) * d;
        switch (R.randInt(0, 14)) {
            case 0 -> Fx.playAt(p, "random.door_open", x, l.y, z, 1, 1);
            case 1 -> Fx.playAt(p, "random.door_close", x, l.y, z, 1, 1);
            case 2 -> Fx.playAt(p, "mob.creeper.say", x, l.y, z, 1.4f, 1);
            case 3 -> Fx.playAt(p, "mob.zombie.say", x, l.y, z, 1, R.pitch(0.7, 1.1));
            case 4 -> Fx.playAt(p, "mob.skeleton.say", x, l.y, z, 1, 1);
            case 5 -> Fx.playAt(p, "mob.villager.idle", x, l.y, z, 1, R.pitch(0.5, 0.8));
            case 6 -> Fx.playAt(p, "random.chestopen", x, l.y, z, 1, 1);
            case 7 -> Fx.playAt(p, "dig.wood", x, l.y, z, 1.5f, 1);
            case 8 -> Fx.playAt(p, "game.player.hurt", x, l.y, z, 1, 1);
            case 9 -> Fx.playAt(p, "ambient.cave", l.x, l.y, l.z, 1, 1);
            case 10 -> Fx.playAt(p, "mob.ghast.moan", x, l.y, z, 1.3f, R.pitch(0.5, 0.8));
            case 11 -> {
                Fx.playAt(p, "mob.ghast.moan", x, l.y, z, 1.2f, 0.6f);
                Later.run(40, () -> Fx.playAt(p, "mob.ghast.moan", x, l.y, z, 1.4f, 0.55f));
            }
            case 12 -> {
                if (HHConfig.FAKE_MESSAGES.get())
                    p.sendSystemMessage(Component.translatable("chat.type.text", p.getDisplayName(), Component.translatable("hollowhours.fakechat." + R.randInt(0, 4))));
            }
            case 13 -> {
                for (int k = 0; k < 4; k++) Later.run(k * 8, () -> Fx.playAt(p, "step.stone", x, l.y, z, 1, R.pitch(0.9, 1.1)));
            }
            default -> {
                if (HHConfig.FAKE_MESSAGES.get() && p.getServer() != null) fakeJoin(p.getServer(), p.serverLevel().getGameTime());
            }
        }
    }

    static void omenSound(ServerPlayer p, double progress) {
        Vec3 l = p.position();
        double a = rand(0, Math.PI * 2), d = rand(5, 12);
        double x = l.x + Math.cos(a) * d, z = l.z + Math.sin(a) * d;
        if (R.chance(0.45)) {
            mimicSound(p);
            return;
        }
        double r = R.next();
        if (r < 0.40) Fx.playAt(p, "ghost.creak", x, l.y, z, (float) rand(0.6, 1.2), R.pitch(0.55, 1.0));
        else if (r < 0.65) Fx.playAt(p, "ambient.cave", l.x, l.y, l.z, 1, R.pitch(0.5, 0.9));
        else if (r < 0.85) Fx.playAt(p, "mob.warden.heartbeat", l.x, l.y, l.z, (float) (0.5 + progress), 0.9f);
        else Fx.playAt(p, "ghost.growl", x, l.y, z, (float) rand(0.2, 0.5), R.pitch(0.4, 0.7));
        if (progress > 0.7 && R.chance(0.5)) Fx.effect(p, MobEffects.DARKNESS, 50, 0);
    }

    static final String[] NAME_A = {"Dark", "Shadow", "Night", "Pixel", "Ninja", "Cool", "Epic", "Blue", "Red", "Ghost", "Wolf", "Fox", "Tiger", "Dragon", "Alpha", "Neko", "Ice", "Fire", "Mega", "Lucky", "Toxic", "Crazy", "Happy", "Sleepy", "Boss", "Mini", "Super", "Silent", "Lone", "Frost"};
    static final String[] NAME_B = {"Gamer", "Player", "Miner", "Builder", "Craft", "Slayer", "King", "Master", "Hunter", "Boy", "Girl", "Cat", "Panda", "Bear", "Pro", "Noob", "Steve", "Zero", "Mod", "Warrior", "Wizard", "Sniper", "Blox", "Creeper", "Knight"};
    static final String[] NAME_TH = {"Nong", "Beam", "Ploy", "Tong", "Ice", "Fah", "Ning", "Pop", "Max", "Mild", "Kao", "Tum", "Bank", "Film", "Fluke", "Gun", "Jane", "Mook", "Nut", "Oat", "Pang", "Ram", "Sam", "Toey", "Mint", "Pun", "Prem", "Dew", "Net", "Boat"};

    static String digits(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(R.randInt(0, 9));
        return sb.toString();
    }

    public static String randomName() {
        double r = R.next();
        String nm;
        if (r < 0.22) nm = R.pick(NAME_A) + R.pick(NAME_B) + digits(R.randInt(1, 3));
        else if (r < 0.34) nm = "xX" + R.pick(NAME_A) + R.pick(NAME_B) + "Xx";
        else if (r < 0.52) nm = R.pick(NAME_TH) + digits(R.randInt(2, 4));
        else if (r < 0.62) nm = R.pick(NAME_TH) + "_" + R.pick("TH", "YT", "Pro", "Gamer", "Zaa", "Jung");
        else if (r < 0.86) nm = R.pick(NAME_A) + "_" + R.pick(NAME_B);
        else if (r < 0.94) nm = R.pick(NAME_B) + R.pick(NAME_TH) + digits(2);
        else nm = R.pick(NAME_TH) + R.pick(NAME_A) + digits(3);
        return nm.length() > 16 ? nm.substring(0, 16) : nm;
    }

    public static void fakeJoin(MinecraftServer server, long now) {
        if (now - lastFakeJoin < 60 * 20) return;
        lastFakeJoin = now;
        server.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.joined", randomName()).withStyle(ChatFormatting.YELLOW), false);
    }

    public static void fakeLeave(MinecraftServer server) {
        server.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.left", "Player" + R.randInt(100, 999)).withStyle(ChatFormatting.YELLOW), false);
    }

    public static void whisper(ServerPlayer p) {
        if (GhostTalk.talkLocked(p)) return;
        Fx.actionbar(p, Component.translatable("hollowhours.whisper." + R.randInt(0, 14)));
    }

    /** Next indoor scare sound: every sound once before any repeats. */
    public static String nextScareSound(ServerPlayer p) {
        String[] all = {"giggle", "musicbox", "scream", "glass", "roof", "whisper", "wetbreath", "wallknock", "bell", "sob", "chains", "creak"};
        List<String> bag = scareBags.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        if (bag.isEmpty()) bag.addAll(List.of(all));
        return bag.remove(R.randInt(0, bag.size() - 1));
    }

    // ------------------------------------------------------------------ music (every 5 ticks)
    public static void musicTick(MinecraftServer server, long now) {
        List<GhostEntity> ghosts = allGhosts(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String want = null;
            for (GhostEntity g : ghosts) {
                if (g.level() != p.level() || g.position().distanceTo(p.position()) > 64) continue;
                GhostBrain b = g.brain();
                if (b.mode == GhostBrain.Mode.KNOCK || b.scareOnly) continue;
                if (b.mode == GhostBrain.Mode.LOST || b.isLeaving()) continue;
                if (b.mode == GhostBrain.Mode.SEARCH || b.mode == GhostBrain.Mode.STALK || b.mode == GhostBrain.Mode.SPAWNING) {
                    if (want == null) want = "spawn";
                    continue;
                }
                if (b.mode == GhostBrain.Mode.KILL) continue;
                want = "chase";
            }
            String sent = musicSent.getOrDefault(p.getUUID(), "");
            if (want != null) {
                musicWantedAt.put(p.getUUID(), now);
                if (!want.equals(sent)) sendMusic(p, want, now);
                continue;
            }
            long wantedAt = musicWantedAt.getOrDefault(p.getUUID(), -10000L);
            if ((sent.equals("chase") || sent.equals("spawn")) && now - wantedAt > 60) {
                sendMusic(p, "", now);
                sent = "";
            }
            Long amb = ambientNext.get(p.getUUID());
            if (amb == null) {
                ambientNext.put(p.getUUID(), now + (long) rand(90 * 20, 5 * MIN));
                continue;
            }
            if (now >= amb && sent.isEmpty() && omen == null) {
                String k = R.chance(0.5) ? "ambient1" : "ambient2";
                sendMusic(p, k, now);
                ambientNext.put(p.getUUID(), now + 50 * 20 + (long) rand(4 * MIN, 10 * MIN));
                Later.run(50 * 20, () -> {
                    if (k.equals(musicSent.get(p.getUUID()))) musicSent.put(p.getUUID(), "");
                });
            }
        }
    }

    static void sendMusic(ServerPlayer p, String key, long now) {
        musicSent.put(p.getUUID(), key);
        HHNet.toPlayer(p, new S2CMusic(key));
    }

    public static void stopMusicFor(ServerPlayer p) {
        sendMusic(p, "", 0);
    }

    // ------------------------------------------------------------------ dread (every 10 ticks): heartbeat + whispers behind you
    public static void dreadTick(MinecraftServer server, long now) {
        List<GhostEntity> ghosts = allGhosts(server);
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            double dmin = 999;
            for (GhostEntity g : ghosts) {
                if (g.level() != pl.level()) continue;
                if (g.brainIfPresent() != null && g.brain().scareOnly) continue;
                double d = Math.hypot(g.getX() - pl.getX(), g.getZ() - pl.getZ());
                if (d < dmin) dmin = d;
            }
            if (dmin > 45) {
                dread.remove(pl.getUUID());
                continue;
            }
            double k = Math.min(1, Math.max(0, 1 - dmin / 45));
            long[] st = dread.computeIfAbsent(pl.getUUID(), u -> new long[]{0, now + 100, 0});
            Vec3 l = pl.position();
            if (st[2] == 0) {
                st[2] = 1;
                Fx.playAt(pl, "ghost.horror.drone", l.x, l.y, l.z, 1.4f, 1);
            }
            if (now >= st[0]) {
                Fx.playAt(pl, "ghost.horror.heart", l.x, l.y, l.z, (float) (0.5 + 1.8 * k), (float) (0.85 + 0.45 * k));
                st[0] = now + Math.round(34 - 20 * k);
            }
            if (k > 0.35 && now >= st[1]) {
                Vec3 v = pl.getViewVector(1f);
                Fx.playAt(pl, "ghost.horror.whisper", l.x - v.x * 2, l.y + 1.6, l.z - v.z * 2, 1.3f, R.pitch(0.9, 1.05));
                st[1] = now + Math.round(rand(160, 320) * (1.3 - k));
            }
        }
    }

    /** After a strong jumpscare: silence, then a racing heart and a whisper right next to your ear. */
    public static void dreadBoost(ServerPlayer p) {
        Later.run(46, () -> {
            Vec3 q = p.position(), v = p.getViewVector(1f);
            Fx.playAt(p, "ghost.horror.whisper", q.x + v.x * 1.2, q.y + 1.6, q.z + v.z * 1.2, 1.8f, R.pitch(0.85, 1));
        });
        for (int k = 0; k < 7; k++) {
            int kk = k;
            Later.run(52 + k * Math.round(26 - k * 2), () -> Fx.playAt(p, "ghost.horror.heart", p.getX(), p.getY(), p.getZ(),
                    (float) Math.max(0.5, 2.4 - kk * 0.25), (float) (0.9 + kk * 0.03)));
        }
    }

    public static void forget(ServerPlayer p) {
        indoorNext.remove(p.getUUID());
        ambientNext.remove(p.getUUID());
        musicSent.remove(p.getUUID());
        dread.remove(p.getUUID());
    }
}
