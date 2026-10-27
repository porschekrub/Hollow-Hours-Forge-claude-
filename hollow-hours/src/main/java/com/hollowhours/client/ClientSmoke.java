package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.ClosetEntity;
import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.entity.StalkerEntity;
import com.hollowhours.entity.StalkerKind;
import com.hollowhours.ghost.Director;
import com.hollowhours.ghost.Jumpscares;
import com.hollowhours.ghost.KillSequence;
import com.hollowhours.ghost.KillTracks;
import com.hollowhours.net.C2SGuideRequest;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CBetrayal;
import com.hollowhours.registry.HHEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * CI only (-Dhollowhours.clientsmoke=true, started with --quickPlaySingleplayer smoke under a virtual display):
 * walks through every ghost, stalker, jumpscare, kill cinematic and screen, takes screenshots of each, then quits.
 * The screenshots are published with the CI logs so the visuals can be checked without a GPU or a player.
 */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientSmoke {
    private static final boolean ON = Boolean.getBoolean("hollowhours.clientsmoke");

    private ClientSmoke() {}

    private interface Step {
        /** @return true when finished */
        boolean tick(int t);
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int stepIndex = -1, stepTick, waitTicks, shotNo, inWorldTicks;
    private static boolean built, done;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 lookAt;

    // ------------------------------------------------------------------ events
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Opening e) {
        if (ON) HollowHours.LOG.info("[client smoke] screen opening: {}", e.getNewScreen() == null ? "none" : e.getNewScreen().getClass().getName());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!ON || done || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus = false;
            if (mc.player == null || mc.level == null) {
                waitTicks++;
                if (waitTicks % 200 == 0) shot("waiting_" + waitTicks);
                if (waitTicks == 900) {
                    HollowHours.LOG.warn("[client smoke] quick play did not open the world, trying to load it directly");
                    mc.createWorldOpenFlows().loadLevel(mc.screen, "smoke");
                }
                if (waitTicks > 2400) fail("never got into the world");
                return;
            }
            inWorldTicks++;
            if (inWorldTicks < 100) return; // let chunks load and render
            if (!built) build(mc);
            if (lookAt != null && !KillCam.active() && mc.player.isAlive()) face(mc, lookAt);
            if (stepIndex >= STEPS.size()) return;
            if (STEPS.get(stepIndex).tick(stepTick++)) {
                stepIndex++;
                stepTick = 0;
                if (stepIndex >= STEPS.size()) {
                    HollowHours.LOG.info("[client smoke] took {} screenshots", shotNo);
                    HollowHours.LOG.info("CLIENT SMOKE OK");
                    STEPS.add(t -> {
                        if (t == 60) {
                            done = true;
                            mc.stop();
                        }
                        return false;
                    });
                }
            }
        } catch (Throwable ex) {
            HollowHours.LOG.error("[client smoke] step {} failed", stepIndex, ex);
            stepIndex++;
            stepTick = 0;
        }
    }

    private static void fail(String why) {
        HollowHours.LOG.error("[client smoke] FAILED: {} (com.hollowhours)", why);
        done = true;
        Minecraft.getInstance().stop();
    }

    // ------------------------------------------------------------------ helpers
    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        String file = String.format("%03d_%s.png", ++shotNo, name);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), c -> {});
    }

    private static void server(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            try {
                action.accept(sp);
            } catch (Throwable ex) {
                HollowHours.LOG.error("[client smoke] server action failed", ex);
            }
        });
    }

    private static void face(Minecraft mc, Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
        mc.player.yBodyRot = yaw;
    }

    private static void clearAll(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        for (Entity en : level.getAllEntities()) {
            if (en instanceof GhostEntity || en instanceof StalkerEntity || en instanceof ClosetEntity) en.discard();
        }
    }

    private static void pin(ServerPlayer sp) {
        sp.teleportTo(origin.x, origin.y, origin.z);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.setHealth(sp.getMaxHealth());
        sp.getFoodData().setFoodLevel(20);
    }

    private static GhostEntity showGhost(ServerPlayer sp, GhostKind k, Vec3 at, GhostAnim anim) {
        GhostEntity g = HHEntities.ghost(k).create(sp.serverLevel());
        if (g == null) return null;
        g.showcase = true;
        float yaw = (float) Math.toDegrees(Math.atan2(-(origin.x - at.x), origin.z - at.z));
        g.moveTo(at.x, at.y, at.z, yaw, 0);
        g.setYHeadRot(yaw);
        g.setYBodyRot(yaw);
        g.setAnim(anim);
        sp.serverLevel().addFreshEntity(g);
        return g;
    }

    private static void wait(int ticks) {
        STEPS.add(t -> t >= ticks);
    }

    // ------------------------------------------------------------------ the tour
    private static void build(Minecraft mc) {
        built = true;
        stepIndex = 0;
        origin = mc.player.position();
        HollowHours.LOG.info("[client smoke] in the world at {}", origin);
        // setup: night, no weather, no natural ghosts, survival, night vision for the model shots
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                ServerLevel l = sp.serverLevel();
                l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, sp.server);
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, sp.server);
                l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, sp.server);
                l.setDayTime(18000);
                l.setWeatherParameters(6000, 0, false, false);
                sp.setGameMode(GameType.SURVIVAL);
                sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 30, 0, false, false));
                clearAll(sp);
                pin(sp);
            });
            if (t == 20) shot("world");
            return t >= 30;
        });

        // every ghost: idle, chasing pose, close-up of the face
        for (GhostKind k : GhostKind.values()) {
            final GhostAnim[] anims = {GhostAnim.IDLE, GhostAnim.CHASE, GhostAnim.KNOCK, GhostAnim.CRAWL_CHASE};
            for (int i = 0; i < anims.length; i++) {
                final GhostAnim a = anims[i];
                final double dist = i == 0 ? 5.5 : 4.5;
                STEPS.add(t -> {
                    if (t == 0) {
                        Vec3 at = origin.add(0, 0, dist);
                        lookAt = at.add(0, a.low() ? 0.6 : k.height * 0.55, 0);
                        server(sp -> {
                            clearAll(sp);
                            pin(sp);
                            showGhost(sp, k, at, a);
                        });
                    }
                    if (t == 30) shot("ghost" + k.n + "_" + a.name().toLowerCase());
                    return t >= 34;
                });
            }
            STEPS.add(t -> {
                if (t == 0) {
                    Vec3 at = origin.add(0, 0, 2.2);
                    lookAt = at.add(0, k.height * 0.85, 0);
                    server(sp -> {
                        clearAll(sp);
                        pin(sp);
                        showGhost(sp, k, at, GhostAnim.IDLE);
                    });
                }
                if (t == 30) shot("ghost" + k.n + "_face");
                return t >= 34;
            });
        }

        // the line-up, stalkers, closet
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 1.8, 10);
                server(sp -> {
                    clearAll(sp);
                    pin(sp);
                    int i = 0;
                    for (GhostKind k : GhostKind.values()) showGhost(sp, k, origin.add((i++ - 2) * 3.2, 0, 10), GhostAnim.IDLE);
                    ClosetEntity c = HHEntities.CLOSET.get().create(sp.serverLevel());
                    if (c != null) {
                        c.moveTo(origin.x + 4, origin.y, origin.z + 5, 135, 0);
                        c.setYHeadRot(135);
                        sp.serverLevel().addFreshEntity(c);
                    }
                });
            }
            if (t == 40) shot("lineup");
            return t >= 44;
        });
        for (StalkerKind k : StalkerKind.values()) {
            for (int pose = 0; pose < 3; pose++) {
                final int ps = pose;
                STEPS.add(t -> {
                    if (t == 0) {
                        Vec3 at = origin.add(0, 0, 4);
                        lookAt = at.add(0, 1.4, 0);
                        server(sp -> {
                            clearAll(sp);
                            pin(sp);
                            StalkerEntity s = HHEntities.stalker(k).create(sp.serverLevel());
                            if (s == null) return;
                            s.owned = true;
                            s.moveTo(at.x, at.y, at.z, 180, 0);
                            s.setYHeadRot(180);
                            s.setYBodyRot(180);
                            s.setStalkPose(ps);
                            sp.serverLevel().addFreshEntity(s);
                        });
                    }
                    if (t == 30) shot("stalker_" + k.name().toLowerCase() + "_" + ps);
                    return t >= 34;
                });
            }
        }

        // jumpscares (no night vision: as dark as in the game)
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                clearAll(sp);
                sp.removeEffect(MobEffects.NIGHT_VISION);
            });
            return t >= 10;
        });
        int[] who = {1, 2, 3, 4, 5, 11, 12, 13};
        for (int w : who) {
            STEPS.add(t -> {
                if (t == 0) {
                    lookAt = origin.add(0, 1.6, 6);
                    server(sp -> {
                        pin(sp);
                        Jumpscares.scare(sp, w, Jumpscares.STRONG);
                    });
                }
                if (t == 3 || t == 6 || t == 10 || t == 16) shot("jumpscare_" + w + "_t" + t);
                return t >= 70;
            });
        }
        STEPS.add(t -> {
            if (t == 0) server(sp -> Jumpscares.dragOut(sp, 2, true));
            if (t == 4 || t == 10 || t == 18 || t == 28) shot("drag_t" + t);
            return t >= 80;
        });

        // the screens
        for (int page = 0; page < 13; page++) {
            final int pg = page;
            STEPS.add(t -> {
                if (t == 0) HHNet.toServer(new C2SGuideRequest(pg));
                if (t == 12) shot("guide_page" + pg);
                return t >= 14;
            });
        }
        STEPS.add(t -> {
            if (t == 0) {
                mc.setScreen(null);
                List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
                mc.setScreen(new BetrayalScreen(new S2CBetrayal(true, 3, 10, ids, List.of("Steve", "Alex", "Porsche"))));
            }
            if (t == 10) shot("betrayal_t0");
            if (t == 70) shot("betrayal_t3s");
            if (t == 80) mc.setScreen(null);
            return t >= 90;
        });

        // every kill cinematic (night vision again so the choreography is visible)
        for (GhostKind k : GhostKind.values()) {
            int kills = Math.max(1, KillTracks.killsFor(k.n).size());
            for (int i = 0; i < kills; i++) killStep(mc, k, i);
        }
    }

    private static String animName(GhostEntity g) {
        try {
            var c = g.getAnimatableInstanceCache().getManagerForId(g.getId()).getAnimationControllers().get("main");
            return c == null || c.getCurrentAnimation() == null ? "none" : c.getCurrentAnimation().animation().name();
        } catch (Throwable e) {
            return "?";
        }
    }

    private static void killStep(Minecraft mc, GhostKind k, int kill) {
        final int[] deadAt = {-1};
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, k.height * 0.7, 2.6);
                deadAt[0] = -1;
                server(sp -> {
                    clearAll(sp);
                    pin(sp);
                    sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 30, 0, false, false));
                    GhostEntity g = Director.spawnGhost(sp.serverLevel(), k, origin.add(0, 0, 2.6));
                    if (g == null) return;
                    KillSequence.forceKill = kill;
                    if (!KillSequence.begin(g.brain(), sp)) HollowHours.LOG.error("[client smoke] kill {}/{} did not start (com.hollowhours)", k.n, kill);
                });
            }
            if (t == 4) lookAt = null;
            String base = "kill_g" + k.n + "_" + (kill + 1);
            boolean dead = mc.player == null || !mc.player.isAlive() || mc.screen instanceof DeathScreen;
            if (deadAt[0] < 0 && dead) deadAt[0] = t;
            if (deadAt[0] < 0 && t > 0 && t % 8 == 7) com.hollowhours.client.render.GhostRenderer.debugTag = base + "_t" + String.format("%03d", t + 1);
            if (deadAt[0] < 0 && t > 0 && t % 8 == 0) {
                GhostEntity kg = KillCam.ghost();
                HollowHours.LOG.info("[killcam] {} phase {} track {} trackT {} jumpscare {} player {} vehicle {} anim {}", base + "_t" + String.format("%03d", t),
                        KillCam.phase, KillCam.track, kg == null ? -1 : String.format("%.2f", kg.trackTime(0)), Jumpscare.active(), mc.player == null ? null : mc.player.position(),
                        mc.player == null || mc.player.getVehicle() == null ? "none" : mc.player.getVehicle().getType().toShortString(), kg == null ? "-" : animName(kg));
                shot(base + "_t" + String.format("%03d", t));
            }
            if (deadAt[0] >= 0) {
                int d = t - deadAt[0];
                if (d == 10 || d == 40) shot(base + "_dead" + d);
                if (d == 50 && mc.player != null) {
                    mc.player.respawn();
                    mc.setScreen(null);
                }
                return d >= 110;
            }
            if (t > 900) {
                HollowHours.LOG.error("[client smoke] kill {}/{} never finished (com.hollowhours)", k.n, kill);
                return true;
            }
            return false;
        });
    }
}
