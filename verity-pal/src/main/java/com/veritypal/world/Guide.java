package com.veritypal.world;

import com.veritypal.V;
import com.veritypal.talk.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Verity leads a player somewhere: she rolls ahead toward the place, waits when they fall behind, hops over what
 * she can't get past, and shows the way and the distance above the hotbar. Any distance.
 */
public final class Guide {
    private Guide() {}

    static final class Trip {
        final UUID player;
        /** the player as they were (a test player is not in the player list) */
        ServerPlayer ref;
        final Places.Found to;
        int reported;
        Vec3 last;
        int stuck, waitTalk, t;

        Trip(UUID player, Places.Found to) {
            this.player = player;
            this.to = to;
        }
    }

    @Nullable
    static Trip trip;

    public static boolean active() {
        return trip != null;
    }

    @Nullable
    public static String where() {
        return trip == null ? null : trip.to.name();
    }

    /** @return null when it started, else why not (English, for the AI) */
    @Nullable
    public static String start(ServerPlayer p, Places.Found f) {
        Mob b = V.body();
        if (b == null) return "your body is in the player's pocket: they have to let you out first (place or throw you)";
        if (!p.level().dimension().equals(f.dim())) return f.name() + " is in another dimension (" + f.dim().location().getPath() + "): they have to go there first";
        Hide.comeOutNow(p.server);
        Life.wake();
        trip = new Trip(p.getUUID(), f);
        trip.ref = p;
        trip.reported = (int) dist(p, f.pos());
        V.follow(false);
        if (b.level() != p.level() || b.distanceTo(p) > 24) bringTo(b, p);
        return null;
    }

    public static void stop() {
        if (trip == null) return;
        Mob b = V.body();
        if (b != null) b.getNavigation().stop();
        V.follow(true);
        trip = null;
    }

    public static void stopAll() {
        trip = null;
    }

    static double dist(ServerPlayer p, BlockPos to) {
        return Math.hypot(p.getX() - (to.getX() + 0.5), p.getZ() - (to.getZ() + 0.5));
    }

    static void bringTo(Mob b, ServerPlayer p) {
        BlockPos spot = Places.safeSpotNear(p.level(), p.blockPosition().relative(p.getDirection(), 2), 3);
        if (spot == null) spot = p.blockPosition();
        if (b.level() != p.level()) return;
        b.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        ((ServerLevel) b.level()).sendParticles(ParticleTypes.POOF, b.getX(), b.getY() + 0.3, b.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
    }

    public static void tick(MinecraftServer server) {
        Trip g = trip;
        if (g == null) return;
        g.t++;
        ServerPlayer o = server.getPlayerList().getPlayer(g.player);
        if (o == null && g.ref instanceof net.minecraftforge.common.util.FakePlayer) o = g.ref;
        Mob b = V.body();
        if (o == null || b == null || !o.isAlive()) {
            if (b == null || o == null) trip = null;
            return;
        }
        boolean th = Lang.thai(o);
        if (!o.level().dimension().equals(g.to.dim())) {
            if (g.t % 400 == 1) V.say(server, "ที่ที่จะไปอยู่อีกมิตินะ ไปมิตินั้นก่อนสิ", "That place is in another dimension. Go there first");
            return;
        }
        if (b.level() != o.level() || b.distanceTo(o) > 56) {
            bringTo(b, o);
            return;
        }
        BlockPos target = g.to.pos();
        double toTarget = Math.hypot(b.getX() - (target.getX() + 0.5), b.getZ() - (target.getZ() + 0.5));
        double ownerToTarget = dist(o, target);
        if (ownerToTarget < 8 || toTarget < 3 && ownerToTarget < 16) {
            arrived(server, o, g);
            return;
        }
        // the way, above the hotbar
        if (g.t % 10 == 0) {
            double br = Places.bearing(target.getX() + 0.5 - o.getX(), target.getZ() + 0.5 - o.getZ());
            String arrow = Places.arrow(Places.turn(br, o.getYRot()));
            o.displayClientMessage(Component.literal("§e" + V.name() + " §7➜ §f" + g.to.name() + "  §e" + arrow + " §f" + (int) ownerToTarget + (th ? " บล็อก" : " blocks")), true);
        }
        int left = (int) ownerToTarget;
        if (g.reported - left >= 300) {
            g.reported = left;
            V.say(server, "อีก " + left + " บล็อก ตามมาดีๆ ล่ะ", left + " blocks to go. Keep up");
        }
        double ownerDist = b.distanceTo(o);
        boolean ownerBehind = ownerToTarget > toTarget + 2;
        if (ownerBehind && ownerDist > 18) {
            // waits for the slowpoke
            b.getNavigation().stop();
            b.getLookControl().setLookAt(o, 30, 30);
            if (++g.waitTalk % 500 == 60) V.say(server, "ทางนี้! เดินช้ายังกับเต่า", "This way! You walk like a turtle");
            return;
        }
        if (g.t % 10 != 0) return;
        // the next stop: about 12 blocks on toward the place, on the ground
        double br = Math.toRadians(Places.bearing(target.getX() + 0.5 - b.getX(), target.getZ() + 0.5 - b.getZ()));
        double step = Math.min(12, toTarget);
        int wx = (int) Math.floor(b.getX() + Math.sin(br) * step), wz = (int) Math.floor(b.getZ() - Math.cos(br) * step);
        ServerLevel level = (ServerLevel) b.level();
        BlockPos way = toTarget <= 12 ? target : Places.landNear(level, wx, wz, b.blockPosition().getY());
        if (way == null) way = new BlockPos(wx, b.blockPosition().getY(), wz);
        boolean moving = b.getNavigation().moveTo(way.getX() + 0.5, way.getY(), way.getZ() + 0.5, ownerDist < 6 ? 1.6 : 1.35);
        Vec3 now = b.position();
        if (g.last != null && now.distanceTo(g.last) < 0.8) g.stuck++;
        else g.stuck = 0;
        g.last = now;
        if (!moving || g.stuck >= 4) {
            // water, cliffs, walls: a bouncy ball hops over
            BlockPos spot = Places.safeSpotNear(level, way, 4);
            if (spot != null) {
                level.sendParticles(ParticleTypes.POOF, b.getX(), b.getY() + 0.3, b.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                b.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                level.playSound(null, spot, SoundEvents.SLIME_JUMP_SMALL, SoundSource.NEUTRAL, 0.6f, 1.4f);
            }
            g.stuck = 0;
        }
        // a trail of sparkles toward the place
        Vec3 a = b.position().add(0, 0.6, 0);
        for (int k = 1; k <= 5; k++)
            level.sendParticles(o, ParticleTypes.END_ROD, true, a.x + Math.sin(br) * k * 0.8, a.y, a.z - Math.cos(br) * k * 0.8, 1, 0, 0, 0, 0);
    }

    static void arrived(MinecraftServer server, ServerPlayer o, Trip g) {
        Mob b = V.body();
        if (b != null) b.getNavigation().stop();
        trip = null;
        V.follow(true);
        V.face("happy");
        o.displayClientMessage(Component.literal("§e" + V.name() + " §7➜ §a" + g.to.name() + " ✔"), true);
        if (V.aiReady())
            V.react(o, "You led " + o.getGameProfile().getName() + " all the way to " + g.to.name() + " and you have just arrived. Tell them, in character (one short line).");
        else V.say(server, "ถึงแล้ว! " + g.to.name() + " ไง ไม่ต้องขอบคุณหรอก (แต่ขอบคุณก็ดี)", "Here we are: " + g.to.name() + ". You're welcome");
    }
}
