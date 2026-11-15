package com.veritypal.world;

import com.veritypal.V;
import com.veritypal.VerityPal;
import com.veritypal.config.PalConfig;
import com.veritypal.mind.Memory;
import com.veritypal.mind.Mood;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Her everyday life: she comes when her name is called, sleeps next to your bed, wanders off when she is bored, talks
 * on her own, teases you when you die and remembers who hit her.
 */
public final class Life {
    private Life() {}

    static final Random R = new Random();

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, Life::chat);
        bus.addListener(Life::death);
        bus.addListener(Life::hurt);
    }

    // ------------------------------------------------------------------ her name
    /** Ways her name gets written (Thai too), besides the name in Verity JE's settings. */
    static final Pattern THAI_NAME = Pattern.compile("เว?อ?ริ?ตี้?|วีริตี้|เวริที|เวอริที|verity|veriti|verety|varity", Pattern.CASE_INSENSITIVE);
    static long lastChat;

    public static boolean calls(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        String n = V.name().toLowerCase(Locale.ROOT);
        return t.contains(n) || t.contains("เวริตี้") || t.contains("เวอริตี้") || t.contains("เวริตี") || t.contains("วีริตี้") || t.contains("เวอริตี") || t.contains("เวอร์ริตี้");
    }

    static void chat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        lastChat = System.currentTimeMillis();
        WANDER_AT = 0;
        if (!PalConfig.on(PalConfig.SUMMON) || !calls(e.getRawText())) return;
        Mob b = V.body();
        if (b == null && V.inPocket(p)) {
            // a grudge: she may stay in the pocket
            if (Memory.like(p) < 5 && R.nextInt(100) < 45) return;
            popOut(p);
        } else if (b != null && (b.level() != p.level() || b.distanceTo(p) > 24)) {
            if (Hide.active() || Memory.like(p) < 5 && R.nextInt(100) < 45) return;
            comeTo(p, b);
        }
    }

    /** Out of the pocket, next to the player. @return false when she isn't in their pocket */
    public static boolean popOut(ServerPlayer p) {
        Item it = V.item();
        EntityType<?> type = V.entityType();
        if (it == null || type == null) return false;
        ItemStack stack = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) {
            stack = s;
            break;
        }
        if (stack.isEmpty() && p.getOffhandItem().is(it)) stack = p.getOffhandItem();
        if (stack.isEmpty()) return false;
        ServerLevel level = p.serverLevel();
        BlockPos at = Places.safeSpotNear(level, p.blockPosition().relative(p.getDirection(), 2), 2);
        if (at == null) at = p.blockPosition();
        String variant = stack.hasTag() && stack.getTag().contains("VerityVariant") ? stack.getTag().getString("VerityVariant") : "happy";
        Entity e = type.spawn(level, (ItemStack) null, null, at, MobSpawnType.MOB_SUMMONED, false, false);
        if (e == null) return false;
        stack.shrink(1);
        if (e instanceof Mob m) m.setPersistenceRequired();
        V.face(variant.isBlank() ? "happy" : variant);
        try {
            ((varmite.verity.entity.verity.VerityEntity) e).setOwnerUUID(p.getUUID());
        } catch (Throwable ignored) {
        }
        level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.3, e.getZ(), 12, 0.2, 0.2, 0.2, 0.04);
        level.playSound(null, at, SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1f, 1.6f);
        V.follow(true);
        VerityPal.LOG.info("[Verity Pal] Verity pops out of {}'s pocket", p.getGameProfile().getName());
        return true;
    }

    public static void comeTo(ServerPlayer p, Mob b) {
        if (b.level() != p.level()) {
            Entity moved = b.changeDimension(p.serverLevel());
            if (moved instanceof Mob m) b = m;
            else return;
        }
        BlockPos at = Places.safeSpotNear(p.level(), p.blockPosition().relative(p.getDirection(), 2), 3);
        if (at == null) at = p.blockPosition();
        ServerLevel level = (ServerLevel) b.level();
        level.sendParticles(ParticleTypes.POOF, b.getX(), b.getY() + 0.3, b.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
        b.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.sendParticles(ParticleTypes.POOF, b.getX(), b.getY() + 0.3, b.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, at, SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1f, 1.4f);
        Wander.cancel();
        V.follow(true);
    }

    // ------------------------------------------------------------------ hits and deaths
    static final Map<UUID, Long> DEATH_TALK = new HashMap<>();

    static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer server = p.server;
        Mob b = V.body();
        boolean near = b != null && b.level() == p.level() && b.distanceTo(p) < 64 || V.inPocket(p);
        if (!near) return;
        Memory.died(p);
        if (!PalConfig.on(PalConfig.TEASE_ON_DEATH)) return;
        long now = System.currentTimeMillis();
        if (now - DEATH_TALK.getOrDefault(p.getUUID(), 0L) < 90_000) return;
        DEATH_TALK.put(p.getUUID(), now);
        String how = e.getSource().getLocalizedDeathMessage(p).getString();
        int deaths = Memory.of(p).deaths;
        Mood.set(Mood.Kind.PLAYFUL);
        VerityPal.later(50, () -> {
            ServerPlayer pl = server.getPlayerList().getPlayer(p.getUUID());
            if (V.aiReady())
                V.react(pl, p.getGameProfile().getName() + " just died (\"" + how + "\"). That is death number " + deaths + " with you around. "
                        + "React in character: tease them about it (one short line), unless they keep dying in a sad way.");
            else V.say(server, "ตายอีกแล้วเหรอ? เก่งจริงๆ เลยนะ (ประชด)", "Dead again? Truly a genius");
        });
    }

    static void hurt(LivingAttackEvent e) {
        if (!V.isVerity(e.getEntity()) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (System.currentTimeMillis() - HIT.getOrDefault(p.getUUID(), 0L) < 4000) return;
        HIT.put(p.getUUID(), System.currentTimeMillis());
        Memory.addLike(p, -1);
        Mood.set(Mood.Kind.GRUMPY);
        V.face("serious_2");
        if (V.aiReady()) V.react(p, p.getGameProfile().getName() + " just hit you. React (annoyed, one short line).");
        else V.say(p.server, "โอ๊ย! ตีทำไมเนี่ย จำไว้เลยนะ", "Ow! Why would you hit me? I'll remember that");
    }

    static final Map<UUID, Long> HIT = new HashMap<>();

    // ------------------------------------------------------------------ sleeping next to the bed
    @Nullable
    static UUID sleepingWith;
    static boolean sleptWasFollowing;

    public static boolean sleeping() {
        return sleepingWith != null;
    }

    public static void wake() {
        if (sleepingWith == null) return;
        sleepingWith = null;
        Mob b = V.body();
        if (b != null) b.setNoAi(false);
        V.face("happy");
        V.follow(sleptWasFollowing);
    }

    static void tickSleep(MinecraftServer server, Mob b) {
        if (sleepingWith != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(sleepingWith);
            if (p == null || !p.isSleeping()) {
                wake();
                return;
            }
            if (server.getTickCount() % 40 == 0) ((ServerLevel) b.level()).sendParticles(ParticleTypes.NOTE, b.getX(), b.getY() + 0.8, b.getZ(), 0, 0, 0, 0, 0);
            return;
        }
        if (!PalConfig.on(PalConfig.SLEEP) || Hide.active() || Guide.active()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isSleeping() || p.level() != b.level() || p.distanceTo(b) > 40) continue;
            BlockPos bed = p.getSleepingPos().orElse(p.blockPosition());
            BlockPos spot = null;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos q = bed.relative(d);
                if (Places.standable(b.level(), q) || Places.passable(b.level(), q) && Places.passable(b.level(), q.above())) {
                    spot = q;
                    break;
                }
            }
            sleepingWith = p.getUUID();
            sleptWasFollowing = V.following();
            V.follow(false);
            Wander.cancel();
            b.getNavigation().stop();
            if (spot != null) b.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            else b.teleportTo(bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5);
            b.setNoAi(true);
            Vec3 to = Vec3.atCenterOf(bed);
            b.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, to);
            V.face("happy_sleep");
            Mood.set(Mood.Kind.SLEEPY);
            if (R.nextInt(3) == 0) V.say(server, "ฝันดีนะ... อย่ากรนดังล่ะ", "Night... don't snore");
            return;
        }
    }

    // ------------------------------------------------------------------ talking on her own
    static long nextTalk;

    static void tickTalk(MinecraftServer server, Mob b) {
        long now = System.currentTimeMillis();
        int minutes = PalConfig.CHAT_MINUTES.get();
        if (nextTalk == 0) nextTalk = now + minutes * 60_000L;
        if (now < nextTalk) return;
        nextTalk = now + (long) (minutes * 60_000L * (0.6 + R.nextDouble() * 0.8));
        if (!PalConfig.on(PalConfig.CHATTY) || Hide.active() || sleeping() || !V.aiReady()) return;
        if (now - lastChat < 60_000) return;
        ServerPlayer o = V.owner(server);
        if (o == null || b.level() != o.level() || b.distanceTo(o) > 32) return;
        String[] ideas = {
                "Say something on your own, in character, fitting your mood (" + Mood.now.word + "): a comment about what they are doing, a random fact, a complaint or a question.",
                "You are " + Mood.now.word + ". Start a little conversation: tease them about something you can see in world_state.",
                "Notice something in world_state (their health, what they hold, the weather, a creature) and comment on it in character.",
                "Ask them a short, personal question (and remember the answer with remember_forever if it matters)."};
        V.talking(80);
        V.react(o, ideas[R.nextInt(ideas.length)] + " One or two short sentences.");
    }

    public static void tick(MinecraftServer server) {
        Mob b = V.body();
        if (b == null) {
            sleepingWith = null;
            Wander.cancel();
            return;
        }
        if (server.getTickCount() % 10 == 0) tickSleep(server, b);
        Wander.tick(server, b);
        if (server.getTickCount() % 100 == 0) tickTalk(server, b);
    }

    static long WANDER_AT;

    // ------------------------------------------------------------------ wandering off when bored
    public static final class Wander {
        static Vec3 goal;
        static int left;
        static Vec3 playerWas;
        static int still;

        public static boolean active() {
            return goal != null;
        }

        public static void cancel() {
            if (goal == null) return;
            goal = null;
            Mob b = V.body();
            if (b != null) b.getNavigation().stop();
            V.follow(true);
        }

        /** @return null or why not */
        @Nullable
        public static String start(Mob b, @Nullable ServerPlayer near) {
            if (Hide.active() || Guide.active() || sleeping()) return "you are busy";
            double a = R.nextDouble() * Math.PI * 2, r = 8 + R.nextInt(8);
            Vec3 c = near != null ? near.position() : b.position();
            BlockPos land = Places.landNear((ServerLevel) b.level(), (int) Math.floor(c.x + Math.cos(a) * r), (int) Math.floor(c.z + Math.sin(a) * r), b.blockPosition().getY());
            if (land == null) return "nowhere to go";
            goal = Vec3.atBottomCenterOf(land);
            left = 20 * (25 + R.nextInt(25));
            V.follow(false);
            b.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
            return null;
        }

        static void tick(MinecraftServer server, Mob b) {
            if (goal != null) {
                if (--left <= 0) {
                    cancel();
                    return;
                }
                if (server.getTickCount() % 40 == 0) {
                    if (b.position().distanceTo(goal) > 2) b.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
                    else if (R.nextInt(3) == 0) {
                        // looks around, picks somewhere else close by
                        double a = R.nextDouble() * Math.PI * 2;
                        BlockPos land = Places.landNear((ServerLevel) b.level(), (int) (goal.x + Math.cos(a) * 4), (int) (goal.z + Math.sin(a) * 4), b.blockPosition().getY());
                        if (land != null) goal = Vec3.atBottomCenterOf(land);
                    }
                }
                return;
            }
            if (server.getTickCount() % 200 != 0 || !PalConfig.on(PalConfig.WANDER) || Hide.active() || Guide.active() || sleeping()) return;
            ServerPlayer o = V.owner(server);
            if (o == null || o.level() != b.level() || o.distanceTo(b) > 24) return;
            // the player stands around and nobody talks to her: bored
            Vec3 now = o.position();
            if (playerWas != null && now.distanceTo(playerWas) < 3) still++;
            else still = 0;
            playerWas = now;
            boolean quiet = System.currentTimeMillis() - lastChat > 120_000;
            if (still >= 6 && quiet && (Mood.now == Mood.Kind.BORED || Mood.now == Mood.Kind.PLAYFUL || R.nextInt(4) == 0)) {
                still = 0;
                if (start(b, o) == null) {
                    Mood.set(Mood.Kind.BORED);
                    V.say(server, "เบื่อ... ไปเดินเล่นแป๊บนะ", "Boring... I'm going for a roll");
                }
            }
        }
    }

    /** (tests) */
    static CompoundTag state() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("sleeping", sleeping());
        t.putBoolean("wander", Wander.active());
        return t;
    }

    static boolean isPlayer(Entity e) {
        return e instanceof Player;
    }
}
