package com.hollowhours.ghost;

import com.hollowhours.entity.BoardsEntity;
import com.hollowhours.registry.HHItems;
import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nailing boards (port of boards.js): hold a board and right-click a closed door (up to 4 boards, any mix of
 * wood/stone/iron) or a window (one board crosses up to 4 touching glass blocks). Your character hammers them on.
 * A boarded door can't be opened. Sneak + right-click with an empty hand = pry the outer board off.
 */
public final class Boards {
    private Boards() {}

    static final int[] STRIKES = {9, 16, 23};
    static final int SEQ = 32;
    static final float[] HAMMER_PITCH = {1.0f, 0.86f, 1.22f};
    static final double PANEL_HALF = 1.5 / 16;

    record Session(ServerPlayer p, ServerLevel level, List<BoardsEntity> recs, int mat, int kind, long t0) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> COOL = new HashMap<>();
    private static final Map<UUID, Long> HINT = new HashMap<>();

    public static int matOf(ItemStack it) {
        if (it.is(HHItems.BOARD_WOOD.get())) return 0;
        if (it.is(HHItems.BOARD_STONE.get())) return 1;
        if (it.is(HHItems.BOARD_IRON.get())) return 2;
        return -1;
    }

    static boolean boardable(@Nullable Breach.Info info) {
        return info != null && (info.kind().equals("door") || info.kind().equals("glass") || info.kind().equals("pane"));
    }

    static boolean busy(ServerPlayer p) {
        long t = p.serverLevel().getGameTime();
        if (SESSIONS.containsKey(p.getUUID()) || t - COOL.getOrDefault(p.getUUID(), -99L) < 8) return true;
        COOL.put(p.getUUID(), t);
        return false;
    }

    static void hint(ServerPlayer p, String key) {
        long t = p.serverLevel().getGameTime();
        if (t - HINT.getOrDefault(p.getUUID(), -99L) < 30) return;
        HINT.put(p.getUUID(), t);
        Fx.actionbar(p, Component.translatable(key));
    }

    // ------------------------------------------------------------------ geometry
    /** {axis, side, x, z}: where a board sits on the player's side of a closed door. */
    @Nullable
    static double[] doorPlane(Level level, BlockPos pos, Vec3 P) {
        BlockState s = level.getBlockState(pos);
        AABB b = s.getShape(level, pos).bounds();
        if (b.getXsize() <= 0) return null;
        if (b.getXsize() < b.getZsize()) {
            double c = pos.getX() + (b.minX + b.maxX) / 2;
            int sd = P.x >= c ? 1 : 0;
            return new double[]{1, sd, c + (sd == 1 ? 1 : -1) * (PANEL_HALF + 0.07), pos.getZ() + 0.5};
        }
        double c = pos.getZ() + (b.minZ + b.maxZ) / 2;
        int sd = P.z >= c ? 1 : 0;
        return new double[]{0, sd, pos.getX() + 0.5, c + (sd == 1 ? 1 : -1) * (PANEL_HALF + 0.07)};
    }

    static int glassAxis(Level level, BlockPos start, Breach.Info info, Vec3 P) {
        if (info.kind().equals("pane")) {
            AABB b = level.getBlockState(start).getShape(level, start).bounds();
            if (Math.abs(b.getXsize() - b.getZsize()) > 0.2) return b.getXsize() > b.getZsize() ? 0 : 1;
        }
        return Math.abs(P.x - (start.getX() + 0.5)) > Math.abs(P.z - (start.getZ() + 0.5)) ? 1 : 0;
    }

    static double[] glassPlane(BlockPos pos, Breach.Info info, Vec3 P, int ax) {
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        double c = ax == 0 ? cz : cx;
        int sd = (ax == 0 ? P.z : P.x) >= c ? 1 : 0;
        double off = (sd == 1 ? 1 : -1) * (info.kind().equals("pane") ? 1 / 16.0 + 0.06 : 0.5 + 0.035);
        return ax == 0 ? new double[]{ax, sd, cx, cz + off} : new double[]{ax, sd, cx + off, cz};
    }

    /** Touching glass in the same plane, up to 4, starting at the clicked one. */
    static List<BlockPos> glassGroup(Level level, BlockPos start, Breach.Info info, int ax) {
        int[][] nb = ax == 0 ? new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}} : new int[][]{{0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && out.size() < 4) {
            BlockPos c = q.poll();
            Breach.Info inf = Breach.classify(level.getBlockState(c));
            if (inf == null || !inf.kind().equals(info.kind())) continue;
            if (BoardsEntity.at(level, c) == null) out.add(c);
            for (int[] d : nb) {
                BlockPos n = c.offset(d[0], d[1], d[2]);
                if (seen.contains(n) || n.distManhattan(start) > 3) continue;
                seen.add(n);
                q.add(n);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ events
    /**
     * Right-click on a block. Returns true when the click was used (the door must not open / item not placed).
     */
    public static boolean onRightClick(ServerPlayer p, BlockPos clicked, ItemStack held) {
        ServerLevel level = p.serverLevel();
        BlockState s = level.getBlockState(clicked);
        Breach.Info info = Breach.classify(s);
        int mat = matOf(held);
        if (mat >= 0) {
            if (boardable(info)) {
                if (!busy(p)) startNailing(p, level, clicked, s, info, mat, held);
                return true;
            }
            hint(p, "hollowhours.boards.hint");
            return false;
        }
        if (info == null || !(info.kind().equals("door") || info.fam().equals("glass"))) return false;
        BlockPos pos = Breach.basePos(s, clicked);
        BoardsEntity r = BoardsEntity.at(level, pos);
        if (r == null || r.count() <= 0) return false;
        boolean used = info.kind().equals("door");
        if (held.isEmpty() && p.isShiftKeyDown()) {
            if (!busy(p)) pry(p, level, r);
            return true;
        }
        if (used && !(held.getItem() instanceof com.hollowhours.item.RepairKitItem)) hint(p, "hollowhours.boards.nailed");
        return used;
    }

    /** Client-side prediction: a boarded door must not swing open for a frame. */
    public static boolean isBoardedDoor(Level level, BlockPos clicked) {
        BlockState s = level.getBlockState(clicked);
        Breach.Info info = Breach.classify(s);
        if (info == null || !info.kind().equals("door")) return false;
        BoardsEntity r = BoardsEntity.at(level, Breach.basePos(s, clicked));
        return r != null && r.count() > 0;
    }

    static void startNailing(ServerPlayer p, ServerLevel level, BlockPos clicked, BlockState s, Breach.Info info, int mat, ItemStack held) {
        Vec3 P = p.position();
        if (P.distanceTo(Vec3.atCenterOf(clicked)) > 4.6) {
            hint(p, "hollowhours.boards.closer");
            return;
        }
        List<BoardsEntity> recs = new ArrayList<>();
        int kind;
        if (info.kind().equals("door")) {
            if (Breach.isOpen(s)) {
                hint(p, "hollowhours.boards.closefirst");
                return;
            }
            BlockPos pos = Breach.basePos(s, clicked);
            BoardsEntity r = BoardsEntity.at(level, pos);
            if (r != null && r.count() >= 4) {
                hint(p, "hollowhours.boards.full");
                return;
            }
            if (!take(p, held)) return;
            if (r == null) {
                double[] pl = doorPlane(level, pos, P);
                if (pl == null) return;
                r = BoardsEntity.create(level, pos, BoardsEntity.KIND_DOOR, (int) pl[0], (int) pl[1], new Vec3(pl[2], pos.getY(), pl[3]));
            }
            r.addBoard(mat);
            recs.add(r);
            kind = 0;
        } else {
            int ax = glassAxis(level, clicked, info, P);
            List<BlockPos> cells = glassGroup(level, clicked, info, ax);
            if (cells.isEmpty()) {
                hint(p, "hollowhours.boards.glassdone");
                return;
            }
            if (!take(p, held)) return;
            for (BlockPos c : cells) {
                Breach.Info inf = Breach.classify(level.getBlockState(c));
                if (inf == null) continue;
                double[] pl = glassPlane(c, inf, P, ax);
                BoardsEntity r = BoardsEntity.create(level, c, BoardsEntity.KIND_GLASS, (int) pl[0], (int) pl[1], new Vec3(pl[2], c.getY(), pl[3]));
                r.addBoard(mat);
                recs.add(r);
            }
            kind = 1;
        }
        if (recs.isEmpty()) return;
        recs.get(0).setFx(1);
        for (int i = 1; i < recs.size(); i++) recs.get(i).setFx(2);
        Fx.sound(level, "ghost.board.place", recs.get(0).outerLoc(), 1f, R.pitch(0.9, 1.1), 16);
        Fx.effect(p, MobEffects.MOVEMENT_SLOWDOWN, SEQ, 3);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        SESSIONS.put(p.getUUID(), new Session(p, level, recs, mat, kind, level.getGameTime()));
    }

    static boolean take(ServerPlayer p, ItemStack held) {
        if (p.getAbilities().instabuild) return true;
        if (held.isEmpty()) return false;
        held.shrink(1);
        return true;
    }

    static void pry(ServerPlayer p, ServerLevel level, BoardsEntity r) {
        Vec3 loc = r.outerLoc();
        if (r.kind() == BoardsEntity.KIND_DOOR) {
            int m = r.removeOuter();
            if (m >= 0 && !p.getAbilities().instabuild) give(p, new ItemStack(HHItems.boardFor(m)));
            int left = r.isAlive() ? r.count() : 0;
            Fx.actionbar(p, left > 0 ? Component.translatable("hollowhours.boards.pry", left) : Component.translatable("hollowhours.boards.pry.all"));
        } else {
            r.burst(false);
            Fx.actionbar(p, Component.translatable("hollowhours.boards.pry.glass"));
        }
        Fx.sound(level, "ghost.board.pry", loc, 1f, R.pitch(0.9, 1.1), 16);
        level.sendParticles(ParticleTypes.CRIT, loc.x, loc.y, loc.z, 8, 0.2, 0.2, 0.2, 0.1);
    }

    static void give(ServerPlayer p, ItemStack it) {
        if (!p.getInventory().add(it)) p.drop(it, false);
    }

    /** Mining a boarded door / glass: door boards come back. */
    public static void onBreak(ServerPlayer p, ServerLevel level, BlockPos clicked, BlockState s) {
        Breach.Info info = Breach.classify(s);
        if (info == null || !(info.kind().equals("door") || info.fam().equals("glass"))) return;
        BoardsEntity r = BoardsEntity.at(level, Breach.basePos(s, clicked));
        if (r == null) return;
        if (r.kind() == BoardsEntity.KIND_DOOR && !p.getAbilities().instabuild) {
            Vec3 l = r.outerLoc();
            for (int i = 0; i < r.count(); i++) {
                var e = new net.minecraft.world.entity.item.ItemEntity(level, l.x, l.y, l.z, new ItemStack(HHItems.boardFor(r.mat(i))));
                level.addFreshEntity(e);
            }
        }
        r.discard();
    }

    // ------------------------------------------------------------------ hammering
    public static void tick(long now) {
        for (Iterator<Session> it = SESSIONS.values().iterator(); it.hasNext(); ) {
            Session S = it.next();
            long t = now - S.t0;
            BoardsEntity first = S.recs.get(0);
            for (int k : STRIKES) if (t == k && first.isAlive()) {
                Vec3 loc = first.outerLoc();
                Fx.sound(S.level, "ghost.hammer", loc, 1.3f, HAMMER_PITCH[S.mat] * R.pitch(0.95, 1.05), 24);
                S.level.sendParticles(ParticleTypes.POOF, loc.x, loc.y, loc.z, 3, 0.1, 0.1, 0.1, 0.01);
                S.p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            }
            if (t >= SEQ || !S.p.isAlive() || S.p.hasDisconnected()) {
                it.remove();
                finish(S);
            }
        }
    }

    static void finish(Session S) {
        for (BoardsEntity r : S.recs) if (r.isAlive()) r.setFx(0);
        if (!S.p.isAlive() || S.p.hasDisconnected()) return;
        String[] mat = {"wood", "stone", "iron"};
        if (S.kind == 0) {
            BoardsEntity r = S.recs.get(0);
            Fx.actionbar(S.p, Component.translatable("hollowhours.boards.done.door", Component.translatable("hollowhours.boards.mat." + mat[S.mat]), r.isAlive() ? r.count() : 0));
        } else Fx.actionbar(S.p, Component.translatable("hollowhours.boards.done.glass", S.recs.size()));
    }

    public static void forget(Player p) {
        SESSIONS.remove(p.getUUID());
    }
}
