package com.hollowhours.endgame;

import com.hollowhours.compat.Compat;
import com.hollowhours.compat.ItemData;
import com.hollowhours.HollowHours;
import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.ghost.Director;
import com.hollowhours.ghost.GhostBrain;
import com.hollowhours.ghost.GhostTalk;
import com.hollowhours.ghost.KillSequence;
import com.hollowhours.ghost.Story;
import com.hollowhours.item.PhoneItem;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CEnding;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.rescue.Rescue;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The last chapter.
 * <ol>
 * <li>COUNTDOWN - back home from the haven, the days are counted (20 by default). Near the end the crying starts.</li>
 * <li>HUNT - on that night all five come at once. Knock every one of them out: down they stay.</li>
 * <li>MAP - with the last one down they remember who they were, point the way, and fade. Everyone gets an old map of
 * Mist Hollow with the mountain marked. Under the mountain, in the old gold mine, the thing that weeps is waiting.</li>
 * <li>RIFT - it is beaten; where it sank a rift of light opens. Step in: a flash, a boom, the credits.</li>
 * <li>DONE - the world is quiet. No more ghosts. Play on.</li>
 * </ol>
 */
public final class Endgame {
    private Endgame() {}

    public static final int NONE = 0, COUNTDOWN = 1, HUNT = 2, MAP = 3, RIFT = 4, DONE = 5;
    static final String MAP_GIVEN = "hollowhours_endgame_map", CREDITS = "hollowhours_endgame_credits", WARNED = "hollowhours_endgame_warned";

    public static final class Data extends SavedData {
        public int stage;
        long fromDay;
        public int down;
        @Nullable
        BlockPos site;
        public int built;     // 0 no, 1 building, 2 done
        @Nullable
        BlockPos center;      // the cavern floor, where it sits
        long riftUntil;
        final List<String> fighters = new ArrayList<>();

        static Data load(CompoundTag t) {
            Data d = new Data();
            d.stage = t.getInt("stage");
            d.fromDay = t.getLong("fromDay");
            d.down = t.getInt("down");
            if (t.contains("site")) d.site = BlockPos.of(t.getLong("site"));
            if (t.contains("center")) d.center = BlockPos.of(t.getLong("center"));
            d.built = t.getInt("built") == 2 ? 2 : 0;
            d.riftUntil = t.getLong("riftUntil");
            for (String n : t.getString("fighters").split(",")) if (!n.isBlank()) d.fighters.add(n);
            return d;
        }

        @Override
        //#if MC>=12100
        //$$ public CompoundTag save(CompoundTag t, net.minecraft.core.HolderLookup.Provider registries) {
        //#else
        public CompoundTag save(CompoundTag t) {
        //#endif
            t.putInt("stage", stage);
            t.putLong("fromDay", fromDay);
            t.putInt("down", down);
            if (site != null) t.putLong("site", site.asLong());
            if (center != null) t.putLong("center", center.asLong());
            t.putInt("built", built == 2 ? 2 : 0);
            t.putLong("riftUntil", riftUntil);
            t.putString("fighters", String.join(",", fighters));
            return t;
        }
    }

    public static Data data(MinecraftServer server) {
        return Compat.saved(server.overworld().getDataStorage(), Data::load, Data::new, "hollowhours_endgame");
    }

    public static int stage(MinecraftServer server) {
        return server == null ? NONE : data(server).stage;
    }

    /** The story is over: no more ghosts, ever. */
    public static boolean peaceful(MinecraftServer server) {
        return server != null && data(server).stage == DONE;
    }

    public static boolean hunting(MinecraftServer server) {
        return stage(server) == HUNT;
    }

    static boolean on() {
        return HHConfig.ENDGAME.get();
    }

    static void set(MinecraftServer server, int stage) {
        Data d = data(server);
        d.stage = stage;
        d.setDirty();
    }

    static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ------------------------------------------------------------------ countdown
    /** Back home from the haven (Rescue.home): the days start counting. */
    public static void onHome(MinecraftServer server) {
        if (!on()) return;
        Data d = data(server);
        if (d.stage != NONE) return;
        d.stage = COUNTDOWN;
        d.fromDay = Story.day(server.overworld());
        d.setDirty();
    }

    public static long daysLeft(MinecraftServer server) {
        Data d = data(server);
        return d.fromDay + HHConfig.ENDGAME_DAYS.get() - Story.day(server.overworld());
    }

    /** Every 20 ticks. */
    public static void tick(MinecraftServer server, long now) {
        if (!on()) return;
        Data d = data(server);
        ServerLevel over = server.overworld();
        switch (d.stage) {
            case NONE -> {
                // worlds that came home before this chapter existed start counting now
                for (ServerPlayer p : server.getPlayerList().getPlayers())
                    if (Rescue.phase(p) == Rescue.HOME) {
                        onHome(server);
                        break;
                    }
            }
            case COUNTDOWN -> {
                long left = daysLeft(server);
                long tod = over.getDayTime() % 24000L;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (Rescue.phase(p) != Rescue.HOME) continue;
                    int warned = persisted(p).getInt(WARNED);
                    if (left <= 3 && warned < 1) {
                        persisted(p).putInt(WARNED, 1);
                        PhoneItem.addMessage(p, "hollowhours.endgame.msg.crying");
                        Fx.msg(p, Component.translatable("hollowhours.endgame.crying").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                        Fx.playSelf(p, "boss.cry", 0.35f, 0.8f);
                    }
                    if (left <= 0 && warned < 2 && tod >= 6000) {
                        persisted(p).putInt(WARNED, 2);
                        PhoneItem.addMessage(p, "hollowhours.endgame.msg.tonight");
                        Fx.title(p, Component.translatable("hollowhours.endgame.tonight").withStyle(ChatFormatting.DARK_RED), Component.translatable("hollowhours.endgame.tonight.sub").withStyle(ChatFormatting.GRAY), 20, 80, 30);
                    }
                }
                if (left <= 0 && tod >= 13000 && tod < 23000 && !server.getPlayerList().getPlayers().isEmpty()) startHunt(server);
            }
            case HUNT -> tickHunt(server, now);
            case MAP -> {
                if (now >= mapAt) for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!persisted(p).getBoolean(MAP_GIVEN)) giveMap(p);
                Arena.tick(server, d, now);
            }
            case RIFT -> Arena.tick(server, d, now);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ the hunt
    public static void startHunt(MinecraftServer server) {
        Data d = data(server);
        d.stage = HUNT;
        d.down = 0;
        d.setDirty();
        List<ServerPlayer> players = targets(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Fx.title(p, Component.translatable("hollowhours.endgame.hunt.title").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("hollowhours.endgame.hunt.sub").withStyle(ChatFormatting.GRAY), 10, 90, 30);
            PhoneItem.addMessage(p, "hollowhours.endgame.msg.hunt");
            Fx.playSelf(p, "boss.cry", 0.7f, 0.7f);
        }
        for (GhostKind k : GhostKind.values()) {
            final GhostKind kind = k;
            Later.run(20 + k.ordinal() * 50, () -> spawnFor(server, kind, true));
        }
        HollowHours.LOG.info("[Hollow Hours] the final hunt begins ({} players)", players.size());
    }

    static List<ServerPlayer> targets(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.overworld().players()) if (GhostBrain.targetable(p) && !KillSequence.isVictim(p)) out.add(p);
        return out;
    }

    static boolean isDown(Data d, GhostKind k) {
        return (d.down & (1 << k.n)) != 0;
    }

    @Nullable
    static GhostEntity spawnFor(MinecraftServer server, GhostKind k, boolean announce) {
        Data d = data(server);
        if (d.stage != HUNT || isDown(d, k)) return null;
        List<ServerPlayer> ts = targets(server);
        if (ts.isEmpty()) return null;
        ServerPlayer p = ts.get(R.randInt(0, ts.size() - 1));
        Vec3 spot = Director.findSpot(p, true, 20, 32);
        if (spot == null) spot = Director.findSpot(p, false, 14, 36);
        if (spot == null) return null;
        GhostEntity g = Director.spawnGhost(p.serverLevel(), k, spot);
        if (g != null && announce) Later.run(30, () -> {
            if (g.isAlive()) for (ServerPlayer o : g.level().getServer().getPlayerList().getPlayers())
                if (o.level() == g.level() && o.position().distanceTo(g.position()) < 48) GhostTalk.say(o, g, "hunt", 1);
        });
        return g;
    }

    private static final long[] missingSince = new long[6];
    static long mapAt;

    static void tickHunt(MinecraftServer server, long now) {
        Data d = data(server);
        boolean[] alive = new boolean[6];
        for (GhostEntity g : Director.allGhosts(server)) if (g.brainIfPresent() != null && !g.brain().scareOnly) alive[g.kind.n] = true;
        for (GhostKind k : GhostKind.values()) {
            if (isDown(d, k) || alive[k.n]) {
                missingSince[k.n] = 0;
                continue;
            }
            // one that is not down yet keeps coming back (a little while after it wandered off)
            if (missingSince[k.n] == 0) missingSince[k.n] = now;
            if (now - missingSince[k.n] > 20 * 25 && spawnFor(server, k, false) != null) missingSince[k.n] = 0;
        }
    }

    /** A knocked-out ghost stays down while the final hunt needs it to. */
    public static boolean keepDown(GhostBrain b) {
        MinecraftServer server = b.g.getServer();
        if (server == null || !on()) return false;
        Data d = data(server);
        return d.stage == HUNT && isDown(d, b.kind);
    }

    /** From GhostCombat.knockOut. */
    public static void onKnockOut(GhostBrain b) {
        MinecraftServer server = b.g.getServer();
        if (server == null || !on()) return;
        Data d = data(server);
        if (d.stage != HUNT || isDown(d, b.kind)) return;
        d.down |= 1 << b.kind.n;
        d.setDirty();
        int n = Integer.bitCount(d.down);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            Fx.msg(p, Component.translatable("hollowhours.endgame.down", Component.translatable("entity.hollowhours.ghost_" + b.kind.n), n)
                    .withStyle(ChatFormatting.GOLD));
        if (n >= GhostKind.values().length) Later.run(60, () -> toMap(server));
    }

    // ------------------------------------------------------------------ the map
    public static void toMap(MinecraftServer server) {
        Data d = data(server);
        if (d.stage >= MAP) return;
        d.stage = MAP;
        d.down = 0b111110;
        if (d.site == null) d.site = pickSite(server);
        d.setDirty();
        // the five remember who they were, point the way, and fade
        List<GhostEntity> ghosts = Director.allGhosts(server);
        int i = 0;
        for (GhostEntity g : ghosts) {
            final int delay = 20 + i++ * 50;
            Later.run(delay, () -> {
                if (!g.isAlive()) return;
                for (ServerPlayer o : server.getPlayerList().getPlayers())
                    if (o.level() == g.level() && o.position().distanceTo(g.position()) < 64) GhostTalk.say(o, g, "release", 3);
            });
            Later.run(delay + 70, () -> {
                if (!g.isAlive()) return;
                ServerLevel l = (ServerLevel) g.level();
                l.sendParticles(ParticleTypes.SOUL, g.getX(), g.getY() + 1, g.getZ(), 40, 0.5, 1.0, 0.5, 0.05);
                l.sendParticles(ParticleTypes.END_ROD, g.getX(), g.getY() + 1, g.getZ(), 20, 0.4, 1.0, 0.4, 0.08);
                Fx.sound(l, "boss.release", g.position(), 1.6f, 1.2f, 48);
                g.ejectPassengers();
                g.discard();
            });
        }
        mapAt = server.overworld().getGameTime() + 40 + i * 50 + 80;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) persisted(p).remove(MAP_GIVEN);
        Later.run(40 + i * 50 + 80, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!persisted(p).getBoolean(MAP_GIVEN)) giveMap(p);
                Fx.title(p, Component.translatable("hollowhours.endgame.map.title").withStyle(ChatFormatting.GOLD),
                        Component.translatable("hollowhours.endgame.map.sub").withStyle(ChatFormatting.GRAY), 10, 100, 30);
                PhoneItem.addMessage(p, "hollowhours.endgame.msg.map");
            }
        });
        HollowHours.LOG.info("[Hollow Hours] all five are down: the mountain is at {}", d.site);
    }

    static BlockPos pickSite(MinecraftServer server) {
        ServerLevel over = server.overworld();
        double cx = 0, cz = 0;
        int n = 0;
        for (ServerPlayer p : over.players()) {
            cx += p.getX();
            cz += p.getZ();
            n++;
        }
        if (n == 0) {
            BlockPos s = over.getSharedSpawnPos();
            cx = s.getX();
            cz = s.getZ();
        } else {
            cx /= n;
            cz /= n;
        }
        int dist = HHConfig.ENDGAME_DISTANCE.get();
        BlockPos best = null;
        for (int tries = 0; tries < 40; tries++) {
            double a = R.rand(0, Math.PI * 2);
            double r = dist * R.rand(0.85, 1.25);
            int x = (int) (cx + Math.cos(a) * r), z = (int) (cz + Math.sin(a) * r);
            BlockPos at = new BlockPos(x, 64, z);
            best = at;
            var biome = over.getUncachedNoiseBiome(x >> 2, 16, z >> 2);
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) continue;
            return at;
        }
        return best;
    }

    /** The CI / commands: put the mountain right here instead. */
    public static void setSite(MinecraftServer server, BlockPos at) {
        Data d = data(server);
        d.site = at;
        d.built = 0;
        d.center = null;
        d.setDirty();
    }

    public static ItemStack makeMap(ServerLevel over, BlockPos site) {
        ItemStack map = MapItem.create(over, site.getX(), site.getZ(), (byte) 3, true, true);
        MapItem.renderBiomePreviewMap(over, map);
        //#if MC>=12100
        //$$ MapItemSavedData.addTargetDecoration(map, site, "+", net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X);
        //$$ ItemData.setName(map, Component.translatable("item.hollowhours.mountain_map").withStyle(ChatFormatting.GOLD));
        //#else
        MapItemSavedData.addTargetDecoration(map, site, "+", MapDecoration.Type.RED_X);
        map.setHoverName(Component.translatable("item.hollowhours.mountain_map").withStyle(ChatFormatting.GOLD));
        //#endif
        ItemData.edit(map, nbt -> nbt.putBoolean("hollowhours_mountain", true));
        return map;
    }

    public static boolean isMountainMap(ItemStack s) {
        return s.is(Items.FILLED_MAP) && ItemData.get(s).getBoolean("hollowhours_mountain");
    }

    static void giveMap(ServerPlayer p) {
        Data d = data(p.server);
        if (d.site == null) return;
        persisted(p).putBoolean(MAP_GIVEN, true);
        ItemStack map = makeMap(p.server.overworld(), d.site);
        if (!p.getInventory().add(map)) p.drop(map, false);
        Fx.playAt(p, net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, p.getX(), p.getY() + 1, p.getZ(), 1f, 0.8f);
    }

    /** Holding the map: how far, and which way. */
    public static void playerTick(ServerPlayer p, long now) {
        if (now % 10 != 0 || !on() || stage(p.server) != MAP) return;
        boolean main = isMountainMap(p.getMainHandItem()), off = isMountainMap(p.getOffhandItem());
        if (!main && !off) return;
        Data d = data(p.server);
        if (d.site == null || p.level().dimension() != Level.OVERWORLD) return;
        BlockPos target = d.center != null ? d.center : d.site;
        if (d.center != null && p.position().distanceTo(Vec3.atCenterOf(d.center)) < 26) return; // down there: no need
        double dx = target.getX() + 0.5 - p.getX(), dz = target.getZ() + 0.5 - p.getZ();
        int dist = (int) Math.round(Math.hypot(dx, dz));
        double ang = Math.toDegrees(Math.atan2(-dx, dz)) - p.getYRot();
        ang = ((ang % 360) + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[(int) Math.floorMod(Math.round(ang / 45.0), 8)];
        Fx.actionbar(p, Component.translatable(dist < 40 ? "hollowhours.endgame.map.here" : "hollowhours.endgame.map.dist", dist, arrow)
                .withStyle(ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------------ the end
    /** From the boss, when it is beaten. */
    public static void onBossDefeated(MinecraftServer server, WeepingEntity boss) {
        Data d = data(server);
        if (d.stage >= RIFT) return;
        d.stage = RIFT;
        d.fighters.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) d.fighters.add(p.getGameProfile().getName());
        d.riftUntil = server.overworld().getGameTime() + 20L * 60 * 30;
        d.setDirty();
        ServerLevel level = (ServerLevel) boss.level();
        Vec3 at = boss.position();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Director.stopMusicFor(p);
            Fx.title(p, Component.translatable("hollowhours.endgame.won.title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("hollowhours.endgame.won.sub").withStyle(ChatFormatting.GRAY), 20, 120, 40);
        }
        // the five, one by one, as they were
        for (int i = 1; i <= 5; i++) {
            final int k = i;
            Later.run(60 + i * 30, () -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers())
                    Fx.msg(p, Component.translatable("hollowhours.endgame.freed." + k).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 2, at.z, 30, 1.5, 2, 1.5, 0.05);
            });
        }
        Later.run(150, () -> {
            RiftEntity rift = HHEntities.RIFT.get().create(level);
            if (rift == null) return;
            rift.moveTo(at.x, at.y + 1.2, at.z, 0, 0);
            level.addFreshEntity(rift);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) Fx.msg(p, Component.translatable("hollowhours.endgame.rift").withStyle(ChatFormatting.WHITE));
        });
    }

    /** Somebody stepped into the rift: a flash, a boom, the credits; home again, and the world at peace. */
    public static void enterRift(ServerPlayer p) {
        MinecraftServer server = p.server;
        Data d = data(server);
        if (d.stage < RIFT) return;
        if (persisted(p).contains(CREDITS) && persisted(p).getLong(CREDITS) > server.overworld().getGameTime() - 200) return;
        persisted(p).putLong(CREDITS, server.overworld().getGameTime());
        boolean first = d.stage != DONE;
        if (first) makePeace(server);
        List<String> names = new ArrayList<>(d.fighters);
        if (!names.contains(p.getGameProfile().getName())) names.add(p.getGameProfile().getName());
        HHNet.toPlayer(p, new S2CEnding(S2CEnding.BOOM, names));
        Fx.sound(p.level(), "ending.boom", p.position(), 3f, 1f, 32);
        p.setInvulnerable(true);
        Later.run(30, () -> {
            if (p.hasDisconnected()) return;
            sendHome(p);
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.removeAllEffects();
        });
        Later.run(200, () -> p.setInvulnerable(false));
    }

    static void makePeace(MinecraftServer server) {
        set(server, DONE);
        // the next morning, and a clear sky
        ServerLevel over = server.overworld();
        long dt = over.getDayTime();
        over.setDayTime(dt - dt % 24000L + 24000L + 200);
        over.setWeatherParameters(24000 * 3, 0, false, false);
        for (GhostEntity g : Director.allGhosts(server)) {
            g.ejectPassengers();
            g.discard();
        }
        for (ServerLevel l : server.getAllLevels())
            for (Entity e : l.getAllEntities())
                if (e instanceof com.hollowhours.entity.StalkerEntity) e.discard();
        Story.tick(server, server.overworld().getGameTime());
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            Director.stopMusicFor(o);
            PhoneItem.addMessage(o, "hollowhours.endgame.msg.peace");
        }
        HollowHours.LOG.info("[Hollow Hours] the story is over: the world is at peace");
    }

    static void sendHome(ServerPlayer p) {
        ServerLevel over = p.server.overworld();
        Vec3 to = null;
        if (p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD) {
            //#if MC>=12100
            //$$ var tr = p.findRespawnPositionAndUseSpawnBlock(false, net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
            //$$ if (!tr.missingRespawnBlock() && tr.newLevel() == over) to = tr.pos();
            //#else
            Optional<Vec3> v = Player.findRespawnPositionAndUseSpawnBlock(over, p.getRespawnPosition(), p.getRespawnAngle(), p.isRespawnForced(), false);
            if (v.isPresent()) to = v.get();
            //#endif
        }
        if (to == null) {
            BlockPos s = over.getSharedSpawnPos();
            BlockPos top = over.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s);
            to = Vec3.atBottomCenterOf(top);
        }
        p.stopRiding();
        p.teleportTo(over, to.x, to.y + 0.1, to.z, p.getYRot(), 0);
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------------ commands / tests
    public static void reset(MinecraftServer server) {
        Data d = data(server);
        d.stage = NONE;
        d.down = 0;
        d.site = null;
        d.center = null;
        d.built = 0;
        d.fighters.clear();
        d.setDirty();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            persisted(p).remove(MAP_GIVEN);
            persisted(p).remove(CREDITS);
            persisted(p).remove(WARNED);
        }
        Story.tick(server, server.overworld().getGameTime());
    }

    public static void skipCountdown(MinecraftServer server) {
        Data d = data(server);
        d.stage = COUNTDOWN;
        d.fromDay = Story.day(server.overworld()) - HHConfig.ENDGAME_DAYS.get();
        d.setDirty();
    }

    public static String status(MinecraftServer server) {
        Data d = data(server);
        String[] names = {"none", "countdown", "hunt", "map", "rift", "done"};
        return names[Math.max(0, Math.min(5, d.stage))] + (d.stage == COUNTDOWN ? " (" + daysLeft(server) + " days left)" : "")
                + (d.stage == HUNT ? " (" + Integer.bitCount(d.down) + "/5 down)" : "")
                + (d.site != null ? " mountain at " + d.site.getX() + " " + d.site.getZ() : "") + (d.built == 2 ? " (built)" : "");
    }

    public static void giveMapNow(ServerPlayer p) {
        Data d = data(p.server);
        if (d.site == null) {
            d.site = pickSite(p.server);
            d.setDirty();
        }
        giveMap(p);
    }

    @Nullable
    public static BlockPos center(MinecraftServer server) {
        return data(server).center;
    }

    static void hand(ServerPlayer p, ItemStack s) {
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
    }
}
