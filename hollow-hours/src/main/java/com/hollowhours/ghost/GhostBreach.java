package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.BoardsEntity;
import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Getting into houses (port of intrude.js, simplified onto vanilla pathfinding):
 * stuck while chasing -> crawl if too tall -> pick the best door / window / boarded opening near the player ->
 * walk outside it -> smash it until it bursts -> squeeze through (sometimes gets stuck and breaks the frame).
 * Can't break the door? Sometimes it turns on the wall instead, otherwise it gives up for tonight.
 * Player on a pillar out of reach? It smashes the pillar.
 */
public final class GhostBreach {
    enum Sub { NONE, APPROACH, BASH, SQUEEZE, STUCK, DIG }

    record Cand(BlockPos pos, @Nullable Breach.Info info, Vec3 dir, BlockPos stand, BlockPos inside, boolean dig, double score) {}

    private final GhostBrain b;
    Sub sub = Sub.NONE;
    Cand cand;
    List<Cand> alt = new ArrayList<>();
    final Set<Long> failed = new HashSet<>();
    long t0, nextHit, squeezeAt, phaseAt;
    int period, blocked, openTries, planFails, digFails, walkFail;
    boolean triedCrawl, failedAll, forSearch;
    Boolean wallRoll;
    // squeeze
    Vec3[] path;
    int seg;
    double u;
    boolean willStick, stuckDone;
    List<BlockPos> frame = new ArrayList<>();
    int fi, fh;
    Vec3 hold;
    // dig
    BlockPos digCell;
    int digH, digNeed;
    @Nullable ServerPlayer player;

    GhostBreach(GhostBrain b) {
        this.b = b;
    }

    public static int rollPower(boolean bonus) {
        double r = R.next();
        int p = r < 0.5 ? 1 : r < 0.85 ? 2 : 3;   // 50% wood only, 35% stone/copper, 15% brutal (iron)
        if (bonus) p++;
        return Math.min(3, p);
    }

    public boolean squeezing() {
        return sub == Sub.SQUEEZE || sub == Sub.STUCK;
    }

    public void reset() {
        sub = Sub.NONE;
        cand = null;
        alt.clear();
        if (b.g.noPhysics) b.g.noPhysics = false;
    }

    // ------------------------------------------------------------------ what blocks may be smashed
    private static final Pattern PROTECT = Pattern.compile("(chest|barrel|shulker_box|furnace|smoker|hopper|dropper|dispenser|brewing_stand|beacon|conduit|enchanting_table|anvil|_bed$|lectern|respawn_anchor|lodestone|spawner|obsidian|netherite_block|ancient_debris|diamond_block|emerald_block|jukebox|bookshelf|decorated_pot|dragon_egg|portal|command_block|structure_block|jigsaw|barrier|bedrock|^light$|reinforced_deepslate)");

    public static boolean breakable(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || W.passable(level, pos)) return false;
        if (Breach.classify(s) != null) return false;
        if (s.hasBlockEntity() || s.getDestroySpeed(level, pos) < 0) return false;
        Block blk = s.getBlock();
        if (blk instanceof StairBlock || blk instanceof LadderBlock) return false;
        String id = BuiltInRegistries.BLOCK.getKey(blk).getPath();
        return !PROTECT.matcher(id).find();
    }

    static int hardness(ServerLevel level, BlockPos pos) {
        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
        if (id.matches(".*(leaves|wool|hay|sponge|moss|snow|dirt|grass|sand|gravel|clay|mud|farmland|soul_soil|mycelium|podzol|glass).*")) return 2;
        if (id.matches(".*(netherite|iron_block|gold_block|copper_block|cut_copper|waxed_).*")) return 8;
        if (id.matches(".*(deepslate|blackstone|basalt|end_stone|purpur|prismarine|quartz).*")) return 6;
        if (id.matches(".*(plank|log|_wood|stem|hyphae|bamboo|fence|crafting).*")) return 3;
        if (id.matches(".*(bricks|terracotta|concrete|tuff|_ore).*")) return 5;
        if (id.matches(".*(slab).*")) return 4;
        return 4;
    }

    int digHits(ServerLevel level, BlockPos pos) {
        double k = new double[]{1.4, 1.4, 1.0, 0.75}[Math.max(1, Math.min(3, b.power()))];
        return Math.max(1, (int) Math.round(hardness(level, pos) * k * b.kind.breakMult * (b.g.isRaged() ? 0.75 : 1)));
    }

    private boolean wallsAllowed() {
        if (!HHConfig.BREAK_WALLS.get()) return false;
        if (wallRoll == null) {
            double ch = new double[]{0, 0.35, 0.55, 0.75}[b.power()];
            wallRoll = R.chance(ch);
            if (wallRoll) for (ServerPlayer q : b.playersNear(28)) Fx.actionbar(q, Component.translatable("hollowhours.breach.wall." + R.randInt(0, 1)));
        }
        return wallRoll;
    }

    // ------------------------------------------------------------------ entry points
    /** Called by the chase when the ghost has made no progress for ~2 seconds. */
    public void onStuck(ServerPlayer target, long now) {
        if (failedAll) return;
        player = target;
        forSearch = false;
        GhostEntity g = b.g;
        if (!triedCrawl && b.kind.height > 2.05 && !g.isCrawling()) {
            triedCrawl = true;
            g.setCrawling(true);
            g.setAnim(GhostAnim.CRAWL_CHASE);
            return;
        }
        List<Cand> cands = findBreach(target.position());
        if (!cands.isEmpty() && openTries < 6) {
            openTries++;
            startApproach(cands, now);
            return;
        }
        // on a pillar out of reach: smash the block under the player
        if (target.getY() > g.getY() + 1.5 && Math.hypot(target.getX() - g.getX(), target.getZ() - g.getZ()) < 3.5) {
            BlockPos under = BlockPos.containing(target.getX(), target.getY() - 0.05, target.getZ()).below(0);
            if (!W.passable(g.level(), under.below()) && breakable(b.level(), under.below()) && under.getY() - 1 - g.getY() <= b.kind.height + 1) {
                for (ServerPlayer q : b.playersNear(20)) Fx.actionbar(q, Component.translatable("hollowhours.breach.topple"));
                startDigAt(under.below(), now);
                return;
            }
        }
        if (wallsAllowed() && startDig(target.position(), now)) return;
        if (++planFails >= 3 || !failed.isEmpty()) {
            failedAll = true;
            giveUp();
        }
    }

    /** A searching ghost can't walk to the spot it wants to check: break in. */
    public boolean breachToward(Vec3 goal, long now) {
        if (!HHConfig.BREAK_DOORS.get()) return false;
        forSearch = true;
        List<Cand> cands = findBreach(goal);
        if (!cands.isEmpty()) {
            startApproach(cands, now);
            return true;
        }
        if (wallsAllowed()) return startDig(goal, now);
        return false;
    }

    // ------------------------------------------------------------------ openings near a point
    List<Cand> findBreach(Vec3 P) {
        ServerLevel level = b.level();
        GhostEntity g = b.g;
        List<Cand> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        BlockPos base = BlockPos.containing(P);
        int pw = b.power();
        for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) for (int dy = -3; dy <= 3; dy++) {
            BlockPos q = base.offset(dx, dy, dz);
            BlockState s = level.getBlockState(q);
            Breach.Info info = Breach.classify(s);
            if (info == null || info.kind().equals("trapdoor")) continue;
            if (info.isDoorLike() && Breach.isOpen(s)) continue;
            BlockPos pos = Breach.basePos(s, q);
            if (!seen.add(pos.asLong()) || failed.contains(pos.asLong())) continue;
            Cand c = sidesOf(level, pos, info, s, g.position(), P);
            if (c == null) continue;
            BoardsEntity boards = BoardsEntity.at(level, pos);
            int[] bh = Breach.boardsHits(boards, pw, g.isRaged());
            double score = Vec3.atCenterOf(c.stand).distanceTo(g.position()) + 1.4 * Vec3.atCenterOf(c.inside).distanceTo(P)
                    + (info.cls() > pw || bh[1] > 0 ? 14 : 0) + bh[0] * 0.6 - (info.fam().equals("glass") ? 2 : 0)
                    - (Breach.crackOf(level, pos) != null ? 3 : 0);
            out.add(new Cand(c.pos, info, c.dir, c.stand, c.inside, false, score));
        }
        out.sort((a, c) -> Double.compare(a.score, c.score));
        // keep the ones the ghost can actually walk to (check the best few)
        List<Cand> ok = new ArrayList<>();
        for (Cand c : out) {
            if (ok.size() >= 4) break;
            Path p = g.getNavigation().createPath(c.stand, 1);
            if (p != null && p.canReach()) ok.add(c);
            else if (ok.isEmpty() && out.indexOf(c) < 2 && W.outdoors(level, Vec3.atBottomCenterOf(c.stand))) ok.add(c);   // phases there when unseen
        }
        return ok;
    }

    @Nullable
    static Cand sidesOf(ServerLevel level, BlockPos pos, Breach.Info info, BlockState s, @Nullable Vec3 G, Vec3 P) {
        Direction.Axis ax;
        if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock) {
            ax = s.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
        } else {
            boolean E = W.solid(level, pos.east()), Wt = W.solid(level, pos.west()), N = W.solid(level, pos.north()), S = W.solid(level, pos.south());
            if ((E || Wt) && !(N && S)) ax = Direction.Axis.Z;
            else if ((N || S) && !(E && Wt)) ax = Direction.Axis.X;
            else ax = G != null && Math.abs(G.x - (pos.getX() + 0.5)) > Math.abs(G.z - (pos.getZ() + 0.5)) ? Direction.Axis.X : Direction.Axis.Z;
        }
        int nx = ax == Direction.Axis.X ? 1 : 0, nz = ax == Direction.Axis.Z ? 1 : 0;
        BlockPos A = pos.offset(-nx, 0, -nz), B = pos.offset(nx, 0, nz);
        double dA = Math.hypot(A.getX() + 0.5 - P.x, A.getZ() + 0.5 - P.z), dB = Math.hypot(B.getX() + 0.5 - P.x, B.getZ() + 0.5 - P.z);
        BlockPos O, I;
        if (Math.abs(dA - dB) < 0.4 && G != null) {
            double gA = Math.hypot(A.getX() + 0.5 - G.x, A.getZ() + 0.5 - G.z), gB = Math.hypot(B.getX() + 0.5 - G.x, B.getZ() + 0.5 - G.z);
            O = gA <= gB ? A : B;
            I = gA <= gB ? B : A;
        } else {
            O = dA >= dB ? A : B;
            I = dA >= dB ? B : A;
        }
        Vec3 dir = new Vec3((I.getX() - O.getX()) / 2.0, 0, (I.getZ() - O.getZ()) / 2.0);
        BlockPos stand = W.standCell(level, O.getX() + 0.5, O.getZ() + 0.5, pos.getY(), 3, 2);
        if (stand == null || stand.getY() < pos.getY() - 2) return null;
        if (!W.passable(level, I)) return null;
        BlockPos inside = W.standCell(level, I.getX() + 0.5, I.getZ() + 0.5, pos.getY(), 4, 1);
        return new Cand(pos, info, dir, stand, inside == null ? I : inside, false, 0);
    }

    // ------------------------------------------------------------------ approach
    private void startApproach(List<Cand> cands, long now) {
        cand = cands.get(0);
        alt = new ArrayList<>(cands.subList(1, cands.size()));
        sub = Sub.APPROACH;
        t0 = now;
        walkFail = 0;
        b.mode = GhostBrain.Mode.BREACH;
        b.g.getNavigation().moveTo(cand.stand.getX() + 0.5, cand.stand.getY(), cand.stand.getZ() + 0.5, 1.0);
        b.g.setAnim(b.g.isCrawling() ? GhostAnim.CRAWL_CHASE : GhostAnim.CHASE);
        if (!forSearch) for (ServerPlayer p : b.playersNear(24)) {
            if (R.chance(0.5) && !Hiding.isHidden(p)) Fx.actionbar(p, Component.translatable("hollowhours.breach.coming." + R.randInt(0, 2)));
        }
    }

    Vec3 standPoint(Cand c) {
        return new Vec3(c.stand.getX() + 0.5 - c.dir.x * 0.1, c.stand.getY(), c.stand.getZ() + 0.5 - c.dir.z * 0.1);
    }

    Vec3 lookPoint(Cand c) {
        return Vec3.atBottomCenterOf(c.pos).add(0, c.info != null && c.info.kind().equals("door") ? 1.0 : 0.5, 0);
    }

    // ------------------------------------------------------------------ tick
    void tick(long now) {
        GhostEntity g = b.g;
        if (b.appeaseUntil > now) {
            reset();
            b.mode = GhostBrain.Mode.CHASE;
            return;
        }
        switch (sub) {
            case APPROACH -> tickApproach(now);
            case BASH -> tickBash(now);
            case SQUEEZE -> tickSqueeze(now);
            case STUCK -> tickStuck(now);
            case DIG -> tickDig(now);
            default -> resume(now);
        }
        if (g.noPhysics && sub != Sub.SQUEEZE && sub != Sub.STUCK) g.noPhysics = false;
    }

    private void tickApproach(long now) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        BlockState s = level.getBlockState(cand.pos);
        if (cand.dig) {
            if (W.passable(level, cand.pos)) {
                resume(now);
                return;
            }
        } else {
            Breach.Info info = Breach.classify(s);
            if (info == null || (info.isDoorLike() && Breach.isOpen(s))) {
                if (W.passable(level, cand.pos) && g.position().distanceTo(standPoint(cand)) < 4.5) startSqueeze(now);
                else resume(now);
                return;
            }
        }
        Vec3 T = standPoint(cand);
        double d = Math.hypot(T.x - g.getX(), T.z - g.getZ());
        if (d < 0.9 && Math.abs(g.getY() - T.y) < 1.3) {
            if (cand.dig) startDigAt(cand.pos, now);
            else startBash(now);
            return;
        }
        if (now - t0 > 240 || walkFail > 6) {
            phaseTo(T, now);
            return;
        }
        if (g.getNavigation().isDone()) {
            walkFail++;
            g.getNavigation().moveTo(T.x, T.y, T.z, 1.0);
        }
    }

    /** Can't walk there: when no one is looking it simply appears there in a puff of smoke. */
    private void phaseTo(Vec3 T, long now) {
        GhostEntity g = b.g;
        boolean watched = false;
        for (ServerPlayer p : b.playersNear(48)) if (W.lookDot(p, g.position()) > 0.82) watched = true;
        if (watched && now - t0 < 320) return;
        for (int i = 0; i < 6; i++) Fx.puff(g.level(), ParticleTypes.LARGE_SMOKE, g.position(), 0.6);
        Fx.sound(g.level(), "mob.warden.nearby_close", g.position(), 1f, 0.5f, 32);
        b.teleportFacing(T, lookPoint(cand));
        for (int i = 0; i < 6; i++) Fx.puff(g.level(), ParticleTypes.LARGE_SMOKE, T, 0.6);
        if (cand.dig) startDigAt(cand.pos, now);
        else startBash(now);
    }

    // ------------------------------------------------------------------ bash
    private void startBash(long now) {
        GhostEntity g = b.g;
        sub = Sub.BASH;
        t0 = now;
        blocked = 0;
        squeezeAt = 0;
        boolean rg = g.isRaged();
        period = rg ? 20 : 28;
        nextHit = now + (rg ? 13 : 17);
        g.getNavigation().stop();
        boolean low = !b.canStand();
        if (low && b.kind.height > 2.05) g.setCrawling(true);
        g.setAnim(g.isCrawling() ? GhostAnim.STUCK : GhostAnim.BASH);
        b.face(lookPoint(cand));
        growl(true);
    }

    private void tickBash(long now) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        if (squeezeAt > 0) {
            if (now >= squeezeAt) startSqueeze(now);
            return;
        }
        BlockState s = level.getBlockState(cand.pos);
        Breach.Info info = Breach.classify(s);
        if (info == null) {
            if (W.passable(level, cand.pos)) startSqueeze(now);
            else resume(now);
            return;
        }
        if (info.isDoorLike() && Breach.isOpen(s)) {
            startSqueeze(now);
            return;
        }
        if (now % 5 == 0) b.face(lookPoint(cand));
        g.setDeltaMovement(0, g.getDeltaMovement().y, 0);
        if (now >= nextHit) {
            nextHit = now + period;
            Breach.Res r = Breach.hitOpening(level, cand.pos, b.power(), g.isRaged(), cand.dir);
            if (r == Breach.Res.BROKEN) {
                squeezeAt = now + 7;
                growl(true);
                return;
            }
            if (r == Breach.Res.GONE) {
                startSqueeze(now);
                return;
            }
            if (r == Breach.Res.BLOCKED && ++blocked >= 2) {
                failTarget(now);
                return;
            }
            if (R.chance(0.25)) growl(false);
            if (R.chance(0.15)) for (ServerPlayer p : b.playersNear(16)) Fx.actionbar(p, Component.translatable("hollowhours.breach.bash." + R.randInt(0, 3)));
        }
        if (now - t0 > 1500) failTarget(now);
    }

    private void failTarget(long now) {
        ServerLevel level = b.level();
        failed.add(cand.pos.asLong());
        for (int dy = -2; dy <= 2; dy++) failed.add(cand.pos.offset(0, dy, 0).asLong());
        Fx.sound(level, "mob.ravager.roar", b.g.position(), 2f, 0.45f, 64);
        growl(true);
        List<Cand> rest = new ArrayList<>();
        for (Cand c : alt) if (!failed.contains(c.pos.asLong())) rest.add(c);
        if (!rest.isEmpty()) {
            startApproach(rest, now);
            return;
        }
        ServerPlayer p = player != null && player.isAlive() ? player : b.target();
        if (p != null && wallsAllowed() && startDig(p.position(), now)) return;
        failedAll = true;
        giveUp();
    }

    void giveUp() {
        for (ServerPlayer p : b.playersNear(32)) Fx.actionbar(p, Component.translatable("hollowhours.breach.giveup"));
        Fx.sound(b.level(), "mob.ghast.scream", b.g.position(), 2f, 0.4f, 64);
        sub = Sub.NONE;
        Later.run(30, () -> {
            if (b.g.isAlive()) b.vanish();
        });
    }

    // ------------------------------------------------------------------ squeeze through
    private void startSqueeze(long now) {
        GhostEntity g = b.g;
        sub = Sub.SQUEEZE;
        t0 = now;
        squeezeAt = 0;
        g.getNavigation().stop();
        if (b.kind.height > 2.05) g.setCrawling(true);
        g.setAnim(GhostAnim.SQUEEZE);
        Vec3 L = g.position();
        double oy = cand.pos.getY();
        path = new Vec3[]{L, new Vec3(cand.pos.getX() + 0.5, oy, cand.pos.getZ() + 0.5),
                new Vec3(cand.pos.getX() + 0.5 + cand.dir.x * 2.3, oy, cand.pos.getZ() + 0.5 + cand.dir.z * 2.3)};
        seg = 0;
        u = 0;
        boolean small = cand.info == null || !cand.info.kind().equals("door");
        willStick = !stuckDone && R.chance(small ? 0.7 : 0.35) && b.kind.height > 2.05;
        g.noPhysics = true;
        Fx.sound(g.level(), "ghost.snap", g.position(), 1.6f, R.pitch(0.7, 0.9), 32);
    }

    private void tickSqueeze(long now) {
        GhostEntity g = b.g;
        Vec3 a = path[seg], c = path[seg + 1];
        double Ls = Math.max(0.2, Math.hypot(c.x - a.x, c.z - a.z) + Math.abs(c.y - a.y));
        u += (seg == 0 ? (g.isRaged() ? 0.2 : 0.15) : (g.isRaged() ? 0.1 : 0.07)) / Ls;
        if (u >= 1) {
            seg++;
            u = 0;
            if (seg >= path.length - 1) {
                finishSqueeze(now);
                return;
            }
        }
        Vec3 A = path[seg], B = path[seg + 1];
        Vec3 loc = A.add(B.subtract(A).scale(u));
        g.setDeltaMovement(Vec3.ZERO);
        g.setPos(loc.x, loc.y, loc.z);
        Vec3 end = path[path.length - 1];
        b.face(new Vec3(end.x + cand.dir.x * 3, loc.y + 0.6, end.z + cand.dir.z * 3));
        if (now % 9 == 0) Fx.sound(g.level(), R.chance(0.5) ? "ghost.snap" : "dig.gravel", loc, 1.3f, R.pitch(0.5, 0.9), 24);
        if (willStick && seg == 1 && u > 0.08) startStuck(now);
    }

    private void finishSqueeze(long now) {
        GhostEntity g = b.g;
        g.noPhysics = false;
        BlockPos floor = W.standCell(g.level(), g.getX(), g.getZ(), Mth.floor(g.getY()) + 1, 4, 1);
        if (floor != null) g.setPos(g.getX(), floor.getY(), g.getZ());
        triedCrawl = true;
        failed.clear();
        failedAll = false;
        stuckDone = false;
        planFails = 0;
        openTries = 0;
        wallRoll = null;
        growl(true);
        for (ServerPlayer p : b.playersNear(20)) {
            Fx.actionbar(p, Component.translatable("hollowhours.breach.inside." + R.randInt(0, 2)));
            Fx.shake(p, 0.6f, 14);
        }
        resume(now);
    }

    private void startStuck(long now) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        sub = Sub.STUCK;
        t0 = now;
        stuckDone = true;
        willStick = false;
        g.setAnim(GhostAnim.STUCK);
        frame = new ArrayList<>();
        if (cand.info == null || !cand.info.kind().equals("door")) {
            int px = cand.dir.x != 0 ? 0 : 1, pz = cand.dir.x != 0 ? 1 : 0;
            List<BlockPos> cs = new ArrayList<>();
            cs.add(cand.pos.above());
            for (int sgn : new int[]{-1, 1}) for (int hh = 0; hh <= 1; hh++) cs.add(cand.pos.offset(px * sgn, hh, pz * sgn));
            for (BlockPos q : cs) if (breakable(level, q)) frame.add(q);
            while (frame.size() > 2 + R.randInt(0, 1)) frame.remove(frame.size() - 1);
        }
        fi = 0;
        fh = 0;
        nextHit = now + 18;
        hold = g.position();
        Fx.sound(level, "mob.ghast.scream", g.position(), 2.2f, 0.45f, 48);
        Fx.shakeNear(level, g.position(), 12, 0.5f, 12);
        for (ServerPlayer p : b.playersNear(18)) Fx.actionbar(p, Component.translatable("hollowhours.breach.stuck." + R.randInt(0, 2)));
    }

    private void tickStuck(long now) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        g.setPos(hold.x + R.rand(-0.04, 0.04), hold.y, hold.z + R.rand(-0.04, 0.04));
        if (now % 23 == 0) Fx.sound(level, R.chance(0.5) ? "ghost.growl" : "ghost.snap", hold, 1.8f, R.pitch(0.5, 0.8), 40);
        if (now >= nextHit) {
            nextHit = now + (g.isRaged() ? 10 : 15);
            if (fi >= frame.size()) {
                if (now - t0 > 50) sub = Sub.SQUEEZE;
                return;
            }
            BlockPos q = frame.get(fi);
            if (!breakable(level, q)) {
                fi++;
                fh = 0;
                return;
            }
            fh++;
            int need = digHits(level, q);
            hitBlockFx(level, q);
            if (fh >= need) {
                Breach.clearGeneric(level, q);
                level.destroyBlock(q, true, g);
                fi++;
                fh = 0;
            } else Breach.crackGeneric(level, q, fh / (double) need);
        }
        if (now - t0 > 400) sub = Sub.SQUEEZE;
        if (sub == Sub.SQUEEZE) g.setAnim(GhostAnim.SQUEEZE);
    }

    // ------------------------------------------------------------------ dig through walls
    boolean startDig(Vec3 P, long now) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        Vec3 L = g.position();
        double dx = P.x - L.x, dz = P.z - L.z, d = Math.max(1e-6, Math.hypot(dx, dz)), ux = dx / d, uz = dz / d;
        int hgt = g.isCrawling() ? 2 : 3;
        BlockPos cell = null;
        for (double s = 0.6; s < Math.min(d, 24); s += 0.5) {
            int x = Mth.floor(L.x + ux * s), z = Mth.floor(L.z + uz * s);
            for (int h = 0; h < hgt; h++) {
                BlockPos q = new BlockPos(x, Mth.floor(L.y) + h, z);
                if (!W.passable(level, q)) {
                    cell = q;
                    break;
                }
            }
            if (cell != null) break;
        }
        if (cell == null) return false;
        BlockState cs = level.getBlockState(cell);
        Breach.Info info = Breach.classify(cs);
        Vec3 dirv = Math.abs(ux) > Math.abs(uz) ? new Vec3(Math.signum(ux), 0, 0) : new Vec3(0, 0, Math.signum(uz));
        if (info != null) {
            BlockPos pos = Breach.basePos(cs, cell);
            Cand c = sidesOf(level, pos, info, level.getBlockState(pos), L, P);
            if (c == null) c = new Cand(pos, info, dirv, g.blockPosition(), BlockPos.containing(P), false, 0);
            startApproach(List.of(c), now);
            return true;
        }
        if (!breakable(level, cell)) {
            if (++digFails > 4) {
                failedAll = true;
                giveUp();
            }
            return false;
        }
        double near = Math.hypot(cell.getX() + 0.5 - L.x, cell.getZ() + 0.5 - L.z);
        if (near < 2.4) {
            startDigAt(cell, now);
            return true;
        }
        BlockPos stand = W.standCell(level, cell.getX() + 0.5 - dirv.x, cell.getZ() + 0.5 - dirv.z, Mth.floor(L.y) + 1, 3, 1);
        if (stand == null) stand = cell.offset((int) -dirv.x, 0, (int) -dirv.z);
        cand = new Cand(cell, null, dirv, stand, cell, true, 0);
        alt = new ArrayList<>();
        sub = Sub.APPROACH;
        t0 = now;
        walkFail = 0;
        b.mode = GhostBrain.Mode.BREACH;
        g.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0);
        return true;
    }

    void startDigAt(BlockPos cell, long now) {
        GhostEntity g = b.g;
        sub = Sub.DIG;
        t0 = now;
        digCell = cell;
        digH = 0;
        digNeed = digHits(b.level(), cell);
        nextHit = now + 12;
        if (cand == null || !cand.dig) cand = new Cand(cell, null, Vec3.ZERO, g.blockPosition(), cell, true, 0);
        b.mode = GhostBrain.Mode.BREACH;
        g.getNavigation().stop();
        g.setAnim(g.isCrawling() ? GhostAnim.STUCK : GhostAnim.BASH);
    }

    private void tickDig(long now) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        if (now % 5 == 0) b.face(Vec3.atCenterOf(digCell));
        g.setDeltaMovement(0, g.getDeltaMovement().y, 0);
        if (now >= nextHit) {
            nextHit = now + (g.isRaged() ? 11 : 16);
            if (W.passable(level, digCell)) {
                Breach.clearGeneric(level, digCell);
                resume(now);
                return;
            }
            if (!breakable(level, digCell)) {
                Breach.clearGeneric(level, digCell);
                failed.add(digCell.asLong());
                resume(now);
                return;
            }
            digH++;
            hitBlockFx(level, digCell);
            if (digH >= digNeed) {
                Breach.clearGeneric(level, digCell);
                level.destroyBlock(digCell, true, g);
                resume(now);
                return;
            }
            Breach.crackGeneric(level, digCell, digH / (double) digNeed);
        }
        if (now - t0 > 400) resume(now);
    }

    private void hitBlockFx(ServerLevel level, BlockPos q) {
        BlockState s = level.getBlockState(q);
        Vec3 c = Vec3.atCenterOf(q);
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        Fx.sound(level, id.matches(".*(plank|log|wood|stem).*") ? "ghost.bang.wood" : "ghost.bang.stone", c, 2f, R.pitch(0.8, 1.05), 48);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), c.x, c.y, c.z, 16, 0.3, 0.3, 0.3, 0.12);
        Fx.shakeNear(level, c, 12, 0.35f, 7);
    }

    // ------------------------------------------------------------------ done: back to chasing / searching
    void resume(long now) {
        GhostEntity g = b.g;
        g.noPhysics = false;
        sub = Sub.NONE;
        cand = null;
        if (b.search != null && forSearch) {
            if (b.target() != null && Perception.perceive(g, b, now, false) != null) {
                b.search = null;
                b.startChase(b.target(), now);
                return;
            }
            Search.resumeAfterBreach(b, now);
            return;
        }
        ServerPlayer t = b.target();
        if (t == null) {
            b.mode = GhostBrain.Mode.LOST;
            return;
        }
        if (Perception.perceive(g, b, now, false) == null) {
            Search.start(b, now, null, t.position(), null, null);
            return;
        }
        b.startChase(t, now);
    }

    private void growl(boolean loud) {
        Fx.sound(b.level(), R.chance(0.5) ? "ghost.growl" : "mob.ghast.scream", b.g.position(), loud ? 2.2f : 1.4f, R.pitch(0.45, 0.7), 48);
    }
}
