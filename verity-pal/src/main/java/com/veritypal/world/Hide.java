package com.veritypal.world;

import com.veritypal.V;
import com.veritypal.VerityPal;
import com.veritypal.config.PalConfig;
import com.veritypal.mind.Mood;
import com.veritypal.story.StoryGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The coward: when a ghost or a scary monster comes, instead of helping Verity may dig into the ground, cover the
 * hole over her head and wait there until it is gone. Then she climbs out, puts the ground back and acts like nothing happened.
 */
public final class Hide {
    private Hide() {}

    static final Random R = new Random();
    static final Pattern SPOOKY = Pattern.compile(".*(ghost|demon|phantom_?man|stalker|wraith|shadow|specter|spectre|spirit|entity|creature|horror|skinwalker|wendigo|"
            + "man_from|cave_dweller|dweller|herobrine|nightmare|hunter|watcher|faceless|siren|mimic|crawler|lurker|haunt|banshee|reaper|nun|clown).*");

    static final class Hiding {
        final List<BlockPos> dug = new ArrayList<>();
        final List<BlockState> was = new ArrayList<>();
        @Nullable BlockPos cover;
        BlockState coverWas = Blocks.AIR.defaultBlockState();
        String from = "";
        UUID scary;
        int step, quiet, t;
        boolean down, asked;
        boolean wasFollowing;
    }

    @Nullable
    static Hiding hiding;
    /** No new hiding before this tick (after climbing out). */
    static long calmUntil;
    /** Scary things she already decided not to hide from (she only rolls the dice once per creature). */
    static final java.util.Set<UUID> BRAVED = new java.util.HashSet<>();

    public static void register(IEventBus bus) {}

    public static boolean active() {
        return hiding != null;
    }

    @Nullable
    public static String from() {
        return hiding == null ? null : hiding.from;
    }

    /** One of the creatures she is scared of. */
    public static boolean scary(Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e instanceof Player || V.isVerity(e)) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (id.getNamespace().equals("verity")) return !StoryGate.off();
        String s = id.toString();
        String test = System.getProperty("veritypal.testscary");
        if (test != null && test.equals(s)) return true;
        for (String w : PalConfig.SCARY.get()) {
            String x = w.trim().toLowerCase(Locale.ROOT);
            if (x.equals(s) || x.endsWith(":*") && id.getNamespace().equals(x.substring(0, x.length() - 2))) return true;
        }
        if (!PalConfig.HIDE_GUESS.get() || id.getNamespace().equals("minecraft") || !(e instanceof Enemy)) return false;
        return le.getMaxHealth() >= 60 || SPOOKY.matcher(id.getPath()).matches();
    }

    public static void tick(MinecraftServer server) {
        Mob b = V.body();
        Hiding h = hiding;
        if (h != null) {
            if (b == null) {
                hiding = null;
                return;
            }
            tickHiding(server, b, h);
            return;
        }
        if (b == null || server.getTickCount() % 10 != 0 || !PalConfig.on(PalConfig.HIDE) || Life.sleeping()) return;
        if (server.getTickCount() < calmUntil) return;
        ServerLevel level = (ServerLevel) b.level();
        Entity found = null;
        for (Entity e : level.getEntities(b, b.getBoundingBox().inflate(20), Hide::scary)) {
            if (BRAVED.contains(e.getUUID())) continue;
            found = e;
            break;
        }
        if (found == null) return;
        double chance = PalConfig.HIDE_CHANCE.get() + (Mood.now == Mood.Kind.SCARED ? 0.15 : 0) - (Mood.now == Mood.Kind.PROUD ? 0.1 : 0);
        if (R.nextDouble() >= chance) {
            BRAVED.add(found.getUUID());
            V.face("hurt");
            V.say(server, "เอ่อ... " + found.getName().getString() + " มาแล้ว ฉันไม่ได้กลัวนะ แค่... ระวังตัวด้วยละกัน",
                    "Uh... a " + found.getName().getString() + ". I'm not scared. You go first though");
            return;
        }
        start(server, b, found);
    }

    /** Starts hiding (scary may be null: asked to hide). @return null, or why not */
    @Nullable
    public static String start(MinecraftServer server, Mob b, @Nullable Entity scary) {
        if (hiding != null) return "already hiding underground";
        ServerLevel level = (ServerLevel) b.level();
        BlockPos at = b.blockPosition();
        // something to dig into: solid ground, not bedrock or a chest
        if (!diggable(level, at.below()) || !diggable(level, at.below(2))) return "the ground here can't be dug (rock-hard, liquid or a container)";
        Guide.stop();
        Hiding h = new Hiding();
        h.from = scary == null ? "nothing (you just felt like hiding)" : scary.getName().getString();
        h.scary = scary == null ? null : scary.getUUID();
        h.wasFollowing = V.following();
        hiding = h;
        V.follow(false);
        b.getNavigation().stop();
        Mood.set(Mood.Kind.SCARED);
        V.face("hurt");
        if (scary != null) {
            String n = scary.getName().getString();
            String[][] lines = {
                    {"ไม่เอาด้วยหรอก! " + n + " ของแกจัดการเองนะ บาย!", "Nope! The " + n + " is all yours. Bye!"},
                    {"เฮ้ยๆ " + n + "! ฉันขอตัวไปมุดดินก่อนนะ", "A " + n + "! I'm going underground, good luck"},
                    {"จัดการเองนะ ฉันเป็นแค่ลูกบอล!", "Handle it yourself, I'm just a ball!"},
                    {"ช่วยเหรอ? ไม่ล่ะ ขุดหนีดีกว่า", "Help? No thanks. Digging time"}};
            String[] l = lines[R.nextInt(lines.length)];
            V.say(server, l[0], l[1]);
        }
        VerityPal.LOG.info("[Verity Pal] Verity hides from {}", h.from);
        return null;
    }

    static boolean diggable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return true;
        if (s.liquid() || level.getBlockEntity(p) != null) return false;
        float hard = s.getDestroySpeed(level, p);
        return hard >= 0 && hard <= 5;
    }

    static void tickHiding(MinecraftServer server, Mob b, Hiding h) {
        h.t++;
        ServerLevel level = (ServerLevel) b.level();
        if (!h.down) {
            // dig down: one block every 6 ticks, three deep, then the ground closes over her
            if (h.t % 6 != 0) return;
            BlockPos below = b.blockPosition().below();
            if (h.step < 3 && diggable(level, below)) {
                BlockState s = level.getBlockState(below);
                h.dug.add(below);
                h.was.add(s);
                if (!s.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), b.getX(), b.getY(), b.getZ(), 20, 0.3, 0.2, 0.3, 0.1);
                    level.playSound(null, below, s.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1f, 1.2f);
                    level.setBlock(below, Blocks.AIR.defaultBlockState(), 3);
                }
                b.teleportTo(below.getX() + 0.5, below.getY(), below.getZ() + 0.5);
                h.step++;
                return;
            }
            // close the hole over her head with what was there
            h.down = true;
            b.setNoAi(true);
            if (!h.dug.isEmpty()) {
                BlockPos top = h.dug.get(0);
                BlockState s = h.was.get(0);
                if (s.isAir()) s = Blocks.DIRT.defaultBlockState();
                h.cover = top;
                h.coverWas = level.getBlockState(top);
                level.setBlock(top, s, 3);
                level.playSound(null, top, s.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 0.9f);
            }
            return;
        }
        // waiting down there until nothing scary is around for 10 seconds (or 3 minutes passed)
        if (h.t % 20 != 0) return;
        java.util.List<Entity> around = h.scary == null ? java.util.List.of() : level.getEntities(b, b.getBoundingBox().inflate(32), Hide::scary);
        boolean still = h.scary == null ? h.t < 20 * 30 && !h.asked : !around.isEmpty();
        if (com.veritypal.SmokeTest.ON && h.t % 100 == 0) VerityPal.LOG.info("[Verity Pal] hiding: t {} quiet {} still {} {}", h.t, h.quiet, still, around);
        if (still) h.quiet = 0;
        else h.quiet++;
        if (h.quiet >= 10 || h.t > 20 * 180 || h.asked && h.quiet >= 1) comeOut(server, true);
    }

    /** Asked to come out ("come out", or something else needs her). */
    public static void comeOutNow(MinecraftServer server) {
        if (hiding != null) comeOut(server, false);
    }

    public static void askOut() {
        if (hiding != null) hiding.asked = true;
    }

    static void comeOut(MinecraftServer server, boolean react) {
        Hiding h = hiding;
        hiding = null;
        if (h == null) return;
        calmUntil = server.getTickCount() + 20 * 60;
        if (h.scary != null) BRAVED.add(h.scary);
        Mob b = V.body();
        ServerLevel level = b != null ? (ServerLevel) b.level() : server.overworld();
        // the ground goes back as it was, then she pops up on top of it
        if (h.cover != null) level.setBlock(h.cover, h.was.isEmpty() ? Blocks.AIR.defaultBlockState() : h.was.get(0), 3);
        for (int i = h.dug.size() - 1; i >= 0; i--) level.setBlock(h.dug.get(i), h.was.get(i), 3);
        BlockPos top = h.dug.isEmpty() ? (b != null ? b.blockPosition() : null) : h.dug.get(0);
        if (b != null && top != null) {
            BlockPos out = Places.safeSpotNear(level, top.above(), 3);
            if (out == null) out = top.above();
            b.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
            level.sendParticles(ParticleTypes.POOF, b.getX(), b.getY() + 0.3, b.getZ(), 10, 0.2, 0.2, 0.2, 0.03);
            b.setNoAi(false);
            VerityPal.LOG.info("[Verity Pal] Verity climbs out at {}", out);
        }
        V.follow(true);
        V.face("happy");
        Mood.set(Mood.Kind.PROUD);
        if (!react) return;
        ServerPlayer o = V.owner(server);
        if (V.aiReady() && o != null)
            V.react(o, "You hid underground from " + h.from + " and left " + o.getGameProfile().getName() + " alone with it. It is gone now and you climb back out, "
                    + "brushing off the dirt. React in character: cheeky, not sorry at all (one short line).");
        else V.say(server, "เรียบร้อยยัง? ฉันไม่ได้หนีนะ ไปตรวจใต้ดินมาเฉยๆ", "Is it gone? I wasn't hiding. I was inspecting the dirt");
    }

    public static void comeOutAll(MinecraftServer server, boolean react) {
        if (hiding != null) comeOut(server, react);
    }
}
