package com.veritypal;

import com.mojang.authlib.GameProfile;
import com.veritypal.config.PalConfig;
import com.veritypal.friends.Heard;
import com.veritypal.mind.Memory;
import com.veritypal.story.StoryGate;
import com.veritypal.tools.PalTools;
import com.veritypal.world.Guide;
import com.veritypal.world.Hide;
import com.veritypal.world.HouseBuilder;
import com.veritypal.world.Life;
import com.veritypal.world.Places;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import varmite.verity.VerityConfig;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.llm.AiManager;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.types.AiProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The server test of the workflow (-Dveritypal.smoketest=true, a real Forge server with the real Verity JE): the
 * story switch, the prompt, the new tools (where is, lead the way, build a house), hiding from a scary creature,
 * popping out of a pocket, the memory, and (with -Dveritypal.fakeai) a whole question to her AI through a fake AI
 * server. Writes CHECK lines and "VERITY PAL SMOKE OK" / "FAILED" to the log, then stops the server.
 */
public final class SmokeTest {
    private SmokeTest() {}

    public static final boolean ON = Boolean.getBoolean("veritypal.smoketest");
    static final Map<String, Boolean> CHECKS = new LinkedHashMap<>();
    static int t;
    static ServerPlayer p;
    static PalTools tools;
    static final List<String> results = new ArrayList<>();
    static BlockPos guideFrom;
    static double guideStartDist;
    static Entity scary;
    static int hideY;
    static int heardBefore, likeBefore;
    static final java.util.Set<Long> FORCED = new java.util.HashSet<>();

    static void check(String what, boolean ok, Object detail) {
        CHECKS.put(what, ok);
        VerityPal.LOG.info("[Verity Pal] CHECK {}: {} ({})", what, ok ? "ok" : "FAILED", detail);
    }

    public static void start(MinecraftServer server) {
        VerityPal.LOG.info("[Verity Pal] smoke test starts");
    }

    /** A tool, called from another thread the way her AI does. */
    static void tool(String name, java.util.function.Supplier<String> call) {
        CompletableFuture.runAsync(() -> {
            String r;
            try {
                r = call.get();
            } catch (Throwable e) {
                r = "ERROR " + e;
            }
            synchronized (results) {
                results.add(name + ": " + r);
            }
        });
    }

    static String result(String name) {
        synchronized (results) {
            for (String r : results) if (r.startsWith(name + ": ")) return r.substring(name.length() + 2);
        }
        return null;
    }

    public static void tick(MinecraftServer server) {
        t++;
        ServerLevel level = server.overworld();
        try {
            if (t == 40) {
                check("verity version", V.MISSING.isEmpty(), V.MISSING);
                check("story off by default", StoryGate.off(), PalConfig.STORY.get());
                check("story gate: no demon day", VerityConfig.DAY_COUNT.get() == StoryGate.NEVER, VerityConfig.DAY_COUNT.get());
                check("story gate: animals not killed", !VerityConfig.KILL_WILDLIFE.get() && !VerityConfig.KILL_VILLAGERS.get(), "");
                check("story gate: no darkness", !VerityConfig.TRUE_DARKNESS.get(), "");
                server.setDifficulty(Difficulty.NORMAL, true);
                level.setDayTime(1000);
            }
            if (t == 60) {
                BlockPos spawn = level.getSharedSpawnPos();
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
                p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("0e0e0e0e-1111-2222-3333-444455556666"), "PalTester"));
                p.setPos(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                tools = newTools(p, server);
                EntityType<?> type = V.entityType();
                Entity v = type == null ? null : type.spawn(level, (ItemStack) null, null, top.east(2), MobSpawnType.MOB_SUMMONED, false, false);
                if (v instanceof VerityEntity ve) ve.setOwnerUUID(p.getUUID());
                check("verity spawned", V.body() != null && V.body() == v, v);
                V.follow(true);
            }
            if (t == 80) {
                tool("where_is", () -> {
                    String r = tools.where_is("village");
                    // no village within 1,600 blocks of this world's spawn: another kind of place
                    for (String other : new String[] {"forest", "river", "plains", "ocean"}) if (r.startsWith("Could not")) r = tools.where_is(other);
                    return r;
                });
                tool("look_around", () -> tools.look_around());
                tool("about_mods", () -> tools.about_mods(""));
            }
            if (t == 200) {
                String w = result("where_is");
                check("tool where_is", w != null && w.contains("blocks to the"), w);
                String l = result("look_around");
                check("tool look_around", l != null && l.contains("Biome"), l);
                String m = result("about_mods");
                check("tool about_mods", m != null && m.contains("Verity"), m);
                prompt(server);
                Mob b = V.body();
                guideFrom = b.blockPosition();
                Places.Found f = Places.LAST.get(p.getUUID());
                if (f != null) guideStartDist = Math.hypot(b.getX() - f.pos().getX(), b.getZ() - f.pos().getZ());
                // there (when it isn't too far for a test), else 90 blocks east
                Places.Found to = Places.LAST.get(p.getUUID());
                String where = to != null && guideStartDist < 1200 ? "there" : (b.getBlockX() + 90) + " " + b.getBlockZ();
                if (to == null || guideStartDist >= 1200) guideStartDist = 90;
                tool("guide_me_to", () -> tools.guide_me_to(where));
            }
            // the "player" follows her while she leads the way (a test player doesn't load the world around it: forced chunks do)
            if (t > 200 && t % 20 == 0 && V.body() != null) {
                Mob b = V.body();
                if (t < 620) p.setPos(b.getX() - 2, b.getY(), b.getZ() - 2);
                int cx = b.getBlockX() >> 4, cz = b.getBlockZ() >> 4;
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        if (FORCED.add(net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz))) level.setChunkForced(cx + dx, cz + dz, true);
            }
            if (t == 620) {
                String g = result("guide_me_to");
                Mob b = V.body();
                Places.Found f = Places.LAST.get(p.getUUID());
                double now = f == null || b == null ? 99999 : Math.hypot(b.getX() - f.pos().getX(), b.getZ() - f.pos().getZ());
                check("tool guide_me_to: she leads the way", g != null && g.contains("leading") && (now < guideStartDist - 15 || !Guide.active()),
                        g + " | distance " + (int) guideStartDist + " -> " + (int) now);
                tool("stop_guiding", () -> tools.stop_guiding());
            }
            if (t == 640) {
                p.setYRot(0);
                tool("build_house", () -> tools.build_house("oak"));
            }
            if (t == 800) {
                int beds = 0, planks = 0;
                for (BlockPos q : BlockPos.betweenClosed(p.blockPosition().offset(-12, -3, -12), p.blockPosition().offset(12, 8, 12))) {
                    var s = level.getBlockState(q);
                    if (s.is(Blocks.YELLOW_BED)) beds++;
                    if (s.is(Blocks.OAK_PLANKS)) planks++;
                }
                check("tool build_house", beds == 2 && planks > 60, result("build_house") + " | bed parts " + beds + ", planks " + planks);
                // hiding: something scary 8 blocks away
                PalConfig.HIDE_CHANCE.set(1.0);
                Mob b = V.body();
                hideY = b.blockPosition().getY();
                EntityType<?> husk = ForgeRegistries.ENTITY_TYPES.getValue(VerityPal.rl("minecraft", System.getProperty("veritypal.testscary", "minecraft:husk").split(":")[1]));
                scary = husk.spawn(level, (ItemStack) null, null, b.blockPosition().east(6), MobSpawnType.COMMAND, false, false);
                if (scary instanceof Mob sm) sm.setNoAi(true);
            }
            if (t == 900) {
                Mob b = V.body();
                check("she hides from " + (scary == null ? "?" : scary.getName().getString()), Hide.active() && b != null && b.blockPosition().getY() < hideY,
                        "y " + hideY + " -> " + (b == null ? "?" : b.blockPosition().getY()));
                if (scary != null) scary.discard();
            }
            if (t == 1200) {
                Mob b = V.body();
                check("she comes out when it is gone", !Hide.active() && b != null && b.blockPosition().getY() >= hideY - 1, b == null ? "?" : b.blockPosition());
                likeBefore = Memory.like(p);
                tool("like_player", () -> tools.like_player("PalTester", 2));
                tool("remember_forever", () -> tools.remember_forever("PalTester's favourite food is mango sticky rice"));
            }
            if (t == 1260) {
                Memory.save();
                String mem;
                try {
                    mem = java.nio.file.Files.readString(Memory.file());
                } catch (Exception e) {
                    mem = e.toString();
                }
                check("memory in every world (" + Memory.file().getFileName() + ")", mem.contains("mango sticky rice") && mem.contains("\"like\": " + Math.min(20, likeBefore + 2)), result("like_player"));
                // back in the pocket, then her name is called
                Mob b = V.body();
                ItemStack item = new ItemStack(V.item());
                p.getInventory().add(item);
                b.discard();
                check("in the pocket", V.body() == null && V.inPocket(p), "");
                check("pops out of the pocket", Life.popOut(p) && V.body() != null, V.body());
                heardBefore = Heard.count;
                if (System.getProperty("veritypal.fakeai") != null) askAi(server);
            }
            boolean ai = System.getProperty("veritypal.fakeai") != null;
            if (t > 1262 && (!ai || t >= 1260 + 20 * 40 || Heard.count > heardBefore && t > 1300)) {
                if (System.getProperty("veritypal.fakeai") != null)
                    check("her AI used a new tool and answered", Heard.count > heardBefore && Heard.last.contains("หมู่บ้าน"), "said: " + Heard.last);
                finish(server);
            }
        } catch (Throwable e) {
            VerityPal.LOG.error("[Verity Pal] smoke test step {} crashed", t, e);
            check("step " + t, false, e);
            finish(server);
        }
    }

    static PalTools newTools(ServerPlayer p, MinecraftServer server) throws Exception {
        var c = PalTools.class.getDeclaredConstructor(ServerPlayer.class, MinecraftServer.class);
        c.setAccessible(true);
        return c.newInstance(p, server);
    }

    /** Verity JE's prompt, with the story off and on. */
    static void prompt(MinecraftServer server) {
        try {
            Method m = Class.forName("varmite.verity.entity.llm.AiAPI").getDeclaredMethod("getSystemPrompt", ServerPlayer.class, VerityEntity.class, ServerLevel.class,
                    long.class, float.class, boolean.class);
            m.setAccessible(true);
            VerityEntity v = (VerityEntity) V.body();
            String off = (String) m.invoke(null, p, v, server.overworld(), 4L, 10f, false);
            check("prompt with the story off", off.contains("<verity_pal>") && off.contains("guide_me_to") && !off.contains("ThatMob") && !off.contains("demon_presence")
                    && !off.contains("Something is coming"), off.length() + " characters");
            VerityPal.LOG.info("[Verity Pal] PROMPT (story off):\n{}", off);
            PalConfig.STORY.set(true);
            String on = (String) m.invoke(null, p, v, server.overworld(), 4L, 10f, false);
            PalConfig.STORY.set(false);
            check("prompt with the story on", on.contains("ThatMob") && on.contains("<verity_pal>"), on.length() + " characters");
        } catch (Throwable e) {
            check("prompt", false, e);
        }
    }

    /** A question to her AI (a fake AI server answers: it asks for where_is first, then says something in Thai). */
    static void askAi(MinecraftServer server) {
        try {
            VerityConfig.AI_PROVIDER.set(AiProvider.OPENAI);
            VerityConfig.AI_ENDPOINT.set(System.getProperty("veritypal.fakeai"));
            VerityConfig.API_KEY.set("test-key");
            AiManager.queryAI((VerityEntity) V.body(), "<PalTester> เวริตี้ หมู่บ้านอยู่ไหน TEST", p);
        } catch (Throwable e) {
            check("ask her AI", false, e);
        }
    }

    static void finish(MinecraftServer server) {
        boolean ok = !CHECKS.isEmpty() && CHECKS.values().stream().allMatch(b -> b);
        List<String> bad = new ArrayList<>();
        CHECKS.forEach((k, v) -> {
            if (!v) bad.add(k);
        });
        VerityPal.LOG.info(ok ? "[Verity Pal] VERITY PAL SMOKE OK ({} checks)" : "[Verity Pal] VERITY PAL SMOKE FAILED: {}", ok ? CHECKS.size() : bad);
        Guide.stopAll();
        HouseBuilder.finishAll();
        if (!Boolean.getBoolean("veritypal.keeprunning")) server.halt(false);
    }
}
