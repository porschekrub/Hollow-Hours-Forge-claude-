package com.veritypal.client;

import com.veritypal.V;
import com.veritypal.VerityPal;
import com.veritypal.config.PalConfig;
import com.veritypal.world.Guide;
import com.veritypal.world.Hide;
import com.veritypal.world.HouseBuilder;
import com.veritypal.world.Places;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import varmite.verity.entity.verity.VerityEntity;

import javax.sound.sampled.AudioFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The client test of the workflow (-Dveritypal.clientsmoke=true, a real game with the real Verity JE, in the world the
 * server test made): the settings page, Verity in the world, the Thai voice and ears (a fake Gemini answers), leading
 * the way, a house and hiding, with screenshots. Ends with "CLIENT SMOKE OK" and closes the game.
 */
public final class ClientSmoke {
    private ClientSmoke() {}

    public static final boolean ON = Boolean.getBoolean("veritypal.clientsmoke");
    static int t;
    static final Map<String, Boolean> CHECKS = new LinkedHashMap<>();
    static volatile String heard;
    static Entity scary;

    static void check(String what, boolean ok, Object detail) {
        CHECKS.put(what, ok);
        VerityPal.LOG.info("[Verity Pal] CLIENT CHECK {}: {} ({})", what, ok ? "ok" : "FAILED", detail);
    }

    static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "veritypal_" + name + ".png", mc.getMainRenderTarget(), c -> VerityPal.LOG.info("[Verity Pal] screenshot {}", name));
    }

    static void server(Runnable r) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(r);
    }

    static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    static void look(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, at);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        t++;
        try {
            if (t == 60) mc.setScreen(new PalConfigScreen(null));
            if (t == 90) shot("config");
            if (t == 100) {
                mc.setScreen(null);
                server(() -> {
                    ServerPlayer p = sp();
                    if (p == null) return;
                    p.serverLevel().setDayTime(1000);
                    Mob b = V.body();
                    BlockPos at = Places.safeSpotNear(p.level(), p.blockPosition().relative(p.getDirection(), 3), 3);
                    if (at == null) at = p.blockPosition().east(3);
                    if (b == null) {
                        EntityType<?> type = V.entityType();
                        Entity e = type.spawn(p.serverLevel(), (ItemStack) null, null, at, MobSpawnType.MOB_SUMMONED, false, false);
                        if (e instanceof VerityEntity v) v.setOwnerUUID(p.getUUID());
                    } else b.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                    V.follow(true);
                    V.face("crazy");
                });
            }
            if (t == 150) {
                Mob b = V.body();
                check("verity in the world", b != null, b);
                if (b != null) look(b.position().add(0, 0.3, 0));
            }
            if (t == 160) shot("verity");
            if (t == 170) {
                Mob b = V.body();
                Entity ce = b == null ? null : mc.level.getEntity(b.getId());
                boolean took = ThaiVoice.take("สวัสดี ฉันชื่อเวริตี้ ถามอะไรมาก็ได้ ฉันรู้ทุกอย่าง", mc.player, ce instanceof VerityEntity v ? v : null);
                check("thai line goes to the Thai voice", took, Gemini.key().isEmpty() ? "no key" : "key set");
                new Thread(() -> {
                    byte[] silence = new byte[16000 * 2];
                    heard = ThaiEars.hear(silence, new AudioFormat(16000, 16, 1, true, false));
                }, "VerityPal-test-ears").start();
            }
            if (t == 400) {
                check("Thai voice made by Gemini", ThaiVoice.made > 0, "clips " + ThaiVoice.made + " " + Gemini.lastError);
                check("Gemini ears hear Thai", heard != null && heard.contains(V.name()) && heard.contains("หมู่บ้าน"), heard);
                server(() -> {
                    ServerPlayer p = sp();
                    if (p == null) return;
                    BlockPos to = p.blockPosition().east(70);
                    Guide.start(p, new Places.Found("the test spot", new BlockPos(to.getX(), Places.surfaceY(p.serverLevel(), to.getX(), to.getZ(), to.getY()), to.getZ()),
                            p.level().dimension()));
                });
            }
            if (t == 480) {
                Mob b = V.body();
                if (b != null) look(b.position().add(0, 0.3, 0));
            }
            if (t == 490) {
                check("leading the way", Guide.active(), Guide.where());
                shot("guide");
            }
            if (t == 500) server(() -> {
                Guide.stop();
                ServerPlayer p = sp();
                if (p == null) return;
                p.setYRot(0);
                HouseBuilder.start(p, "cherry");
            });
            if (t == 640) {
                mc.player.setYRot(0);
                mc.player.setXRot(5);
            }
            if (t == 650) {
                check("house built", !HouseBuilder.busy(), "");
                shot("house");
                server(() -> {
                    PalConfig.HIDE_CHANCE.set(1.0);
                    Mob b = V.body();
                    if (b == null) return;
                    String id = System.getProperty("veritypal.testscary", "minecraft:husk");
                    EntityType<?> husk = ForgeRegistries.ENTITY_TYPES.getValue(VerityPal.rl(id));
                    scary = husk.spawn((ServerLevel) b.level(), (ItemStack) null, null, b.blockPosition().east(5), MobSpawnType.COMMAND, false, false);
                    if (scary instanceof Mob m) m.setNoAi(true);
                });
            }
            if (t == 740) {
                check("she hides", Hide.active(), Hide.from());
                Mob b = V.body();
                if (b != null) look(b.position().add(0, 1.2, 0));
            }
            if (t == 750) shot("hide");
            if (t == 760) server(() -> {
                if (scary != null) scary.discard();
            });
            if (t == 800) {
                boolean ok = !CHECKS.isEmpty() && CHECKS.values().stream().allMatch(b -> b);
                VerityPal.LOG.info(ok ? "[Verity Pal] CLIENT SMOKE OK ({} checks)" : "[Verity Pal] CLIENT SMOKE FAILED: {}", ok ? CHECKS.size() : CHECKS);
                if (ok) VerityPal.LOG.info("CLIENT SMOKE OK");
            }
            if (t == 840) mc.stop();
        } catch (Throwable e) {
            VerityPal.LOG.error("[Verity Pal] client test step {} crashed", t, e);
            check("step " + t, false, e);
        }
    }
}
