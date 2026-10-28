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
            if (!built && mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen && inWorldTicks < 1200) return;
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
        List<Entity> all = new ArrayList<>();
        for (Entity en : level.getAllEntities()) all.add(en); // discarding while iterating breaks the entity map
        for (Entity en : all) {
            if (en instanceof GhostEntity || en instanceof StalkerEntity || en instanceof ClosetEntity || en instanceof com.hollowhours.grave.GraveEntity) en.discard();
            // superflat slimes from before mob spawning was turned off would kill the player mid-tour
            else if (en instanceof net.minecraft.world.entity.monster.Enemy) en.discard();
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

    private static void Thirst_set(ServerPlayer sp, float v) {
        com.hollowhours.ghost.Thirst.set(sp, v);
        com.hollowhours.ghost.Survival.forceSync(sp);
    }

    private static void Sanity_set(ServerPlayer sp, float v) {
        com.hollowhours.ghost.Sanity.set(sp, v);
        com.hollowhours.ghost.Survival.forceSync(sp);
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

        // before the story: a normal world and a happy player
        STEPS.add(t -> {
            if (t == 0) server(sp -> com.hollowhours.ghost.Story.force(sp.server, -1));
            if (t == 30) Atmos.snap();
            return t >= 32;
        });
        String[] happy = {"idle", "walk", "run", "mine"};
        for (String hp : happy) {
            STEPS.add(t -> {
                if (t == 0) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    lookAt = origin.add(0, 1.6, 30);
                    server(sp -> {
                        clearAll(sp);
                        pin(sp);
                    });
                }
                if (t == 2) {
                    mc.options.keyUp.setDown(!hp.equals("idle") && !hp.equals("mine"));
                    mc.options.keyLeft.setDown(hp.equals("walk"));
                    mc.options.keySprint.setDown(hp.equals("run"));
                    if (hp.equals("run") && mc.player != null) mc.player.setSprinting(true);
                }
                if (hp.equals("mine") && mc.player != null && t % 5 == 0) mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (t == 14 || t == 24) shot("happy_" + hp + (t == 14 ? "_a" : "_b"));
                if (t >= 26) {
                    mc.options.keyUp.setDown(false);
                    mc.options.keyLeft.setDown(false);
                    mc.options.keySprint.setDown(false);
                    if (mc.player != null) mc.player.setSprinting(false);
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    return true;
                }
                return false;
            });
        }
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 1.6, 12);
                server(sp -> {
                    pin(sp);
                    sp.removeEffect(MobEffects.NIGHT_VISION);
                });
            }
            if (t == 20) shot("normal_night_before_story");
            if (t == 22) server(sp -> {
                com.hollowhours.ghost.Story.force(sp.server, 1);
                sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 30, 0, false, false));
            });
            if (t == 60) Atmos.snap();
            return t >= 62;
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

        // the player's scared animations, seen from the front in third person
        record Pose(String name, float fear, boolean walk, boolean sprint, boolean sneak, boolean mine, float hp, boolean side) {}
        Pose[] poses = {
                new Pose("idle_calm", 0.1f, false, false, false, false, 20, false), new Pose("idle_scared", 1f, false, false, false, false, 20, false),
                new Pose("walk_scared", 0.8f, true, false, false, false, 20, false), new Pose("walk_scared_side", 0.8f, true, false, false, false, 20, true),
                new Pose("run_panic", 1f, true, true, false, false, 20, false), new Pose("run_panic_side", 1f, true, true, false, false, 20, true),
                new Pose("sneak_scared", 1f, true, false, true, false, 20, true), new Pose("mine_shaking", 0.9f, false, false, false, true, 20, false),
                new Pose("limp_hurt", 0.6f, true, false, false, false, 4, true)};
        for (Pose ps : poses) {
            STEPS.add(t -> {
                if (t == 0) {
                    Fear.force = ps.fear();
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    lookAt = origin.add(0, 1.6, 30);
                    server(sp -> {
                        clearAll(sp);
                        pin(sp);
                        sp.setHealth(ps.hp());
                    });
                }
                if (t == 2) {
                    mc.options.keyUp.setDown(ps.walk());
                    mc.options.keyLeft.setDown(ps.side());
                    mc.options.keyShift.setDown(ps.sneak());
                    mc.options.keySprint.setDown(ps.sprint());
                    if (ps.sprint() && mc.player != null) mc.player.setSprinting(true);
                }
                if (ps.mine() && mc.player != null && t % 5 == 0) mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (t == 22 || t == 27) shot("player_" + ps.name() + (t == 22 ? "_a" : "_b"));
                if (t >= 28) {
                    mc.options.keyUp.setDown(false);
                    mc.options.keyLeft.setDown(false);
                    mc.options.keyShift.setDown(false);
                    mc.options.keySprint.setDown(false);
                    if (mc.player != null) mc.player.setSprinting(false);
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    Fear.force = -1;
                    server(sp -> {
                        pin(sp);
                        sp.setHealth(sp.getMaxHealth());
                    });
                    return true;
                }
                return false;
            });
        }

        // the atmosphere: the shader at night, fog by day and by night, the blood moon (no night vision)
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 1.6, 12);
                server(sp -> {
                    clearAll(sp);
                    pin(sp);
                    sp.removeEffect(MobEffects.NIGHT_VISION);
                });
            }
            if (t == 20) shot("atmos_night_plain");
            if (t == 22) Fear.force = 1;
            if (t == 40) shot("atmos_night_fear");
            if (t == 41) Fear.force = -1;
            return t >= 42;
        });
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                clearAll(sp);
                sp.serverLevel().setDayTime(6000);
                com.hollowhours.ghost.Atmosphere.startFog(sp.server, 20 * 120, 1f);
            });
            if (t == 30) Atmos.snap();
            if (t == 40) shot("atmos_day_fog");
            if (t == 41) server(sp -> sp.serverLevel().setDayTime(18000));
            if (t == 70) shot("atmos_night_fog");
            if (t == 71) server(sp -> com.hollowhours.ghost.Atmosphere.stopFog(sp.server));
            if (t == 90) Atmos.snap();
            return t >= 92;
        });
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                clearAll(sp);
                pin(sp);
                sp.serverLevel().setDayTime(15000);
                com.hollowhours.ghost.Atmosphere.forceBlood = 1;
            });
            if (t == 45) {
                Atmos.snap();
                lookAt = origin.add(10, 1.6 + 7.5, 0);
            }
            if (t == 60) shot("bloodmoon_title");
            if (t == 190) shot("bloodmoon_sky");
            if (t == 191) lookAt = origin.add(0, 1.6, 12);
            if (t == 210) shot("bloodmoon_ground");
            if (t == 211) server(sp -> showGhost(sp, GhostKind.byN(3), origin.add(0, 0, 7), GhostAnim.CHASE));
            if (t == 240) shot("bloodmoon_ghost");
            if (t == 241) server(sp -> {
                clearAll(sp);
                com.hollowhours.ghost.Atmosphere.forceBlood = 0;
                sp.serverLevel().setDayTime(18000);
            });
            if (t == 270) Atmos.snap();
            return t >= 272;
        });

        // the phone: home, messages, the games, health; the emergency alert with subtitles
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                clearAll(sp);
                pin(sp);
                net.minecraft.world.item.ItemStack ph = com.hollowhours.item.PhoneItem.fresh(sp.getRandom());
                com.hollowhours.item.PhoneItem.setBattery(ph, 64);
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ph);
                com.hollowhours.item.PhoneItem.addMessage(sp, "hollowhours.phone.msg.alert");
            });
            if (t == 10) PhoneScreen.open(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (t == 16) shot("phone_home");
            if (t == 17 && mc.screen instanceof PhoneScreen ps) ps.app = PhoneScreen.App.MESSAGES;
            if (t == 20) shot("phone_messages");
            if (t == 21 && mc.screen instanceof PhoneScreen ps) {
                ps.app = PhoneScreen.App.SNAKE;
                ps.snakeStart();
            }
            if (t == 40) shot("phone_snake");
            if (t == 41 && mc.screen instanceof PhoneScreen ps) {
                ps.app = PhoneScreen.App.TAP;
                ps.tapStart();
            }
            if (t == 60) shot("phone_ghost_tap");
            if (t == 61 && mc.screen instanceof PhoneScreen ps) ps.app = PhoneScreen.App.HEALTH;
            if (t == 64) shot("phone_health");
            if (t == 66) mc.setScreen(null);
            if (t == 70) server(sp -> {
                com.hollowhours.item.PhoneItem.setBattery(sp.getMainHandItem(), 0);
            });
            if (t == 76) PhoneScreen.open(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (t == 80) shot("phone_dead");
            if (t == 82) mc.setScreen(null);
            return t >= 84;
        });
        STEPS.add(t -> {
            if (t == 0) PhoneAlert.start(true);
            if (t == 4 || t == 50 || t == 110 || t == 180) shot("alert_t" + t);
            return !PhoneAlert.active() || t > 900;
        });
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                Thirst_set(sp, 3.5f);
                Sanity_set(sp, 18);
            });
            if (t == 40) shot("hud_thirsty_low_sanity");
            if (t == 41) server(sp -> {
                Thirst_set(sp, 20);
                Sanity_set(sp, 100);
            });
            return t >= 44;
        });
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 0.6, 4);
                BloodParticle.made = 0;
                server(sp -> {
                    clearAll(sp);
                    pin(sp);
                    var l = sp.serverLevel();
                    l.setDayTime(6000);
                    net.minecraft.world.entity.EntityType<?>[] types = {net.minecraft.world.entity.EntityType.PIG, net.minecraft.world.entity.EntityType.ZOMBIE,
                            net.minecraft.world.entity.EntityType.CREEPER, net.minecraft.world.entity.EntityType.SKELETON, net.minecraft.world.entity.EntityType.ENDERMAN};
                    for (int i = 0; i < types.length; i++) {
                        var en = types[i].create(l);
                        if (!(en instanceof net.minecraft.world.entity.Mob m)) continue;
                        m.setNoAi(true);
                        m.moveTo(origin.x + (i - 2) * 1.6, origin.y, origin.z + 4, 180, 0);
                        l.addFreshEntity(m);
                    }
                });
            }
            if (t >= 5 && t <= 25 && t % 5 == 0) server(sp -> {
                for (var en : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(8))) {
                    en.invulnerableTime = 0;
                    en.hurt(sp.damageSources().playerAttack(sp), 1.5f);
                    en.setHealth(Math.max(1, en.getHealth()));
                }
            });
            if (t == 9) shot("blood_spray");
            if (t == 60) shot("blood_splats");
            if (t == 62) {
                HollowHours.LOG.info("[client smoke] blood drops made: {}", BloodParticle.made);
                if (BloodParticle.made == 0) HollowHours.LOG.error("[client smoke] no blood particles arrived (com.hollowhours)");
                server(sp -> {
                    clearAll(sp);
                    sp.serverLevel().setDayTime(18000);
                });
            }
            return t >= 64;
        });

        physicsAndFun(mc);

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

    // ------------------------------------------------------------------ physics, the 3D flashlight, the playground
    /** Everything the tour put down around the start: blocks above the ground, items, balls, debris, the swing seat. */
    private static void clearArea(ServerPlayer sp) {
        clearAll(sp);
        ServerLevel l = sp.serverLevel();
        net.minecraft.core.BlockPos o = net.minecraft.core.BlockPos.containing(origin);
        for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(o.offset(-14, 0, -6), o.offset(14, 14, 20)))
            if (!l.getBlockState(b).isAir()) l.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
        List<Entity> all = new ArrayList<>();
        for (Entity en : l.getAllEntities()) all.add(en);
        for (Entity en : all)
            if (en instanceof net.minecraft.world.entity.item.ItemEntity || en instanceof net.minecraft.world.entity.ExperienceOrb
                    || en instanceof net.minecraft.world.entity.item.FallingBlockEntity || en instanceof com.hollowhours.fun.BallEntity
                    || en instanceof com.hollowhours.physics.FallingTreeEntity || en instanceof net.minecraft.world.entity.item.PrimedTnt
                    || en instanceof net.minecraft.world.entity.Mob)
                en.discard();
        if (sp.isPassenger()) sp.stopRiding();
    }

    private static net.minecraft.core.BlockPos at(double dx, double dy, double dz) {
        return net.minecraft.core.BlockPos.containing(origin.add(dx, dy, dz));
    }

    private static void give(ServerPlayer sp, net.minecraft.world.item.Item main, net.minecraft.world.item.Item off) {
        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, main == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(main));
        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, off == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(off));
    }

    private static void physicsAndFun(Minecraft mc) {
        // daylight + night vision: these are about seeing how things move
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                clearArea(sp);
                pin(sp);
                sp.serverLevel().setDayTime(6000);
                sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 30, 0, false, false));
            });
            return t >= 10;
        });
        // a tree, chopped from its west side: it tips over to the east, seen from the south
        final net.minecraft.core.BlockPos[] stump = {null};
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(2, 3, 9);
                server(sp -> {
                    ServerLevel l = sp.serverLevel();
                    net.minecraft.core.BlockPos base = at(0, 0, 9);
                    stump[0] = base;
                    var reg = l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE);
                    boolean grown = reg.getHolder(net.minecraft.data.worldgen.features.TreeFeatures.OAK)
                            .map(h -> h.value().place(l, l.getChunkSource().getGenerator(), l.random, base)).orElse(false);
                    if (!grown) {
                        for (int y = 0; y < 5; y++) l.setBlock(base.above(y), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), 3);
                        for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(base.offset(-2, 3, -2), base.offset(2, 6, 2)))
                            if (l.getBlockState(b).isAir()) l.setBlock(b, net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState(), 3);
                    }
                    HollowHours.LOG.info("[client smoke] tree at {} (feature: {})", base, grown);
                });
            }
            if (t == 20) shot("tree_standing");
            if (t == 22) server(sp -> {
                sp.teleportTo(stump[0].getX() - 1.5 + 0.5, origin.y, stump[0].getZ() + 0.5);
                give(sp, net.minecraft.world.item.Items.DIAMOND_AXE, null);
                sp.gameMode.destroyBlock(stump[0]);
                sp.teleportTo(stump[0].getX() + 3.5, origin.y, stump[0].getZ() - 8.5);
            });
            if (t == 23) lookAt = Vec3.atBottomCenterOf(stump[0]).add(2.5, 2, 0);
            if (t == 30 || t == 38 || t == 46 || t == 54) shot("tree_falling_t" + (t - 22));
            if (t == 80) shot("tree_fallen_items");
            return t >= 82;
        });
        // items lying on the ground: blocks as little cubes, flat things flat
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 0, 2.6);
                server(sp -> {
                    clearArea(sp);
                    pin(sp);
                    give(sp, null, null);
                    net.minecraft.world.item.Item[] items = {net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.OAK_LOG,
                            net.minecraft.world.item.Items.DIAMOND, net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.APPLE,
                            com.hollowhours.registry.HHItems.FLASHLIGHT.get(), net.minecraft.world.item.Items.CRAFTING_TABLE, net.minecraft.world.item.Items.BREAD};
                    for (int i = 0; i < items.length; i++) {
                        var ie = new net.minecraft.world.entity.item.ItemEntity(sp.serverLevel(), origin.x - 1.4 + (i % 4) * 0.95, origin.y + 0.6,
                                origin.z + 2.2 + (i / 4) * 0.9, new net.minecraft.world.item.ItemStack(items[i]));
                        ie.setDeltaMovement(0, 0.1, 0);
                        ie.setPickUpDelay(32767);
                        sp.serverLevel().addFreshEntity(ie);
                    }
                });
            }
            if (t == 3) shot("items_falling");
            if (t == 40) shot("items_on_ground");
            return t >= 42;
        });
        // an iron vein: break one, the rest burst out one after another
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 0.8, 4.5);
                server(sp -> {
                    clearArea(sp);
                    pin(sp);
                    ServerLevel l = sp.serverLevel();
                    int[][] vein = {{0, 0, 4}, {1, 0, 4}, {0, 1, 4}, {1, 1, 5}, {-1, 0, 5}, {0, 0, 5}, {0, 2, 5}, {1, 2, 5}, {-1, 1, 6}, {0, 1, 6}};
                    for (int[] v : vein) l.setBlock(at(v[0], v[1], v[2]), net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState(), 3);
                    for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(at(-2, 0, 5), at(2, 3, 7)))
                        if (l.getBlockState(b).isAir()) l.setBlock(b, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                    give(sp, net.minecraft.world.item.Items.DIAMOND_PICKAXE, null);
                });
            }
            if (t == 12) shot("vein_before");
            if (t == 14) server(sp -> sp.gameMode.destroyBlock(at(0, 0, 4)));
            if (t == 18 || t == 24 || t == 32 || t == 50) shot("vein_t" + (t - 14));
            if (t == 52) server(sp -> {
                int ores = 0;
                for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(at(-2, 0, 3), at(2, 3, 7)))
                    if (sp.serverLevel().getBlockState(b).is(net.minecraft.world.level.block.Blocks.IRON_ORE)) ores++;
                if (ores > 0) HollowHours.LOG.error("[client smoke] vein mining left {} iron ore (com.hollowhours)", ores);
                else HollowHours.LOG.info("[client smoke] vein mining took the whole vein");
            });
            return t >= 54;
        });
        // the 3D flashlight in first and third person, and putting a battery in
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 1.2, 8);
                server(sp -> {
                    clearArea(sp);
                    pin(sp);
                    var fl = new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.FLASHLIGHT.get());
                    com.hollowhours.ghost.Flashlight.stamp(fl, com.hollowhours.ghost.Flashlight.FULL / 4, true);
                    sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, fl);
                    sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.BATTERY.get(), 3));
                });
            }
            if (t == 12) shot("flashlight_first_person");
            if (t == 13) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            if (t == 22) shot("flashlight_third_person");
            if (t == 23) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (t == 30) server(sp -> com.hollowhours.ghost.Flashlight.reload(sp, sp.getMainHandItem()));
            if (t == 33 || t == 38 || t == 43 || t == 48 || t == 56) shot("reload_t" + (t - 30));
            return t >= 60;
        });
        // the playground: every piece in a row, with the items in the hotbar
        net.minecraft.core.Direction north = net.minecraft.core.Direction.NORTH;
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 0.8, 8);
                server(sp -> {
                    clearArea(sp);
                    pin(sp);
                    give(sp, null, null);
                    ServerLevel l = sp.serverLevel();
                    var bs = com.hollowhours.registry.HHBlocks.ALL;
                    for (int i = 0; i < bs.size(); i++) {
                        var st = bs.get(i).get().defaultBlockState().setValue(com.hollowhours.fun.FunBlock.FACING, net.minecraft.core.Direction.NORTH);
                        if (st.hasProperty(com.hollowhours.fun.FunBlocks.LIT)) st = st.setValue(com.hollowhours.fun.FunBlocks.LIT, true);
                        l.setBlock(at(-7.5 + i * 2.5, 0, 8), st, 3);
                    }
                    for (int i = 0; i < bs.size(); i++) sp.getInventory().setItem(i, new net.minecraft.world.item.ItemStack(bs.get(i).get()));
                    sp.getInventory().setItem(7, new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.BASKETBALL.get()));
                    sp.getInventory().setItem(8, new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.SOCCER_BALL.get()));
                });
            }
            if (t == 25) shot("playground");
            if (t == 26) lookAt = origin.add(-7.5, 0.8, 8);
            if (t == 34) shot("playground_left");
            if (t == 35) lookAt = origin.add(7.5, 0.8, 8);
            if (t == 43) shot("playground_right");
            return t >= 44;
        });
        // the swing: sit, and it swings higher and higher (seen from the side)
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                clearArea(sp);
                pin(sp);
                ServerLevel l = sp.serverLevel();
                net.minecraft.core.BlockPos sw = at(0, 0, 4);
                var st = com.hollowhours.registry.HHBlocks.SWING.get().defaultBlockState().setValue(com.hollowhours.fun.FunBlock.FACING, north);
                l.setBlock(sw, st, 3);
                st.use(l, sp, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(sw), north, sw, false));
                Sanity_set(sp, 50);
            });
            if (t == 5) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                lookAt = origin.add(12, 1.2, 4);
            }
            if (t == 30 || t == 120 || t == 133 || t == 146) shot("swing_t" + t);
            if (t == 150) {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                server(sp -> {
                    HollowHours.LOG.info("[client smoke] swing: riding {} sanity {}", sp.getVehicle() == null ? "nothing" : sp.getVehicle().getType().toShortString(),
                            com.hollowhours.ghost.Sanity.get(sp));
                    sp.stopRiding();
                });
            }
            if (t == 160) shot("swing_left");
            return t >= 162;
        });
        // the trampoline: dropped onto it, bouncing (seen from the front)
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                clearArea(sp);
                ServerLevel l = sp.serverLevel();
                l.setBlock(at(0, 0, 0), com.hollowhours.registry.HHBlocks.TRAMPOLINE.get().defaultBlockState(), 3);
                sp.teleportTo(origin.x, origin.y + 4, origin.z);
                sp.setDeltaMovement(Vec3.ZERO);
            });
            if (t == 2) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                lookAt = origin.add(0, 1.6, 30);
            }
            if (t >= 20 && t <= 80 && t % 6 == 0) shot("trampoline_t" + t);
            if (t == 82) {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                server(sp -> HollowHours.LOG.info("[client smoke] trampoline: health {} y {}", sp.getHealth(), sp.getY() - origin.y));
            }
            return t >= 84;
        });
        // basketball: a shot that drops through the rim; football: rolled into the goal
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 1.5, 6);
                server(sp -> {
                    clearArea(sp);
                    pin(sp);
                    Sanity_set(sp, 50);
                    ServerLevel l = sp.serverLevel();
                    l.setBlock(at(0, 0, 6), com.hollowhours.registry.HHBlocks.BASKETBALL_HOOP.get().defaultBlockState().setValue(com.hollowhours.fun.FunBlock.FACING, north), 3);
                    var ball = HHEntities.BALL.get().create(l);
                    if (ball == null) return;
                    ball.setKind(com.hollowhours.fun.BallEntity.BASKETBALL);
                    // the rim is 1.5 up and a quarter block in front (north) of the hoop block's centre
                    net.minecraft.core.BlockPos h = at(0, 0, 6);
                    ball.moveTo(h.getX() + 0.5, h.getY() + 3.2, h.getZ() + 0.25, 0, 0);
                    ball.setDeltaMovement(0, 0.05, 0);
                    ball.touchedBy(sp);
                    l.addFreshEntity(ball);
                });
            }
            if (t == 14 || t == 20 || t == 28 || t == 40) shot("basketball_t" + t);
            if (t == 45) server(sp -> {
                HollowHours.LOG.info("[client smoke] basketball: sanity {} (50 before)", com.hollowhours.ghost.Sanity.get(sp));
                if (com.hollowhours.ghost.Sanity.get(sp) <= 50) HollowHours.LOG.error("[client smoke] the basket did not count (com.hollowhours)");
            });
            if (t == 46) {
                lookAt = origin.add(0, 0.6, 7);
                server(sp -> {
                    clearArea(sp);
                    pin(sp);
                    Sanity_set(sp, 50);
                    ServerLevel l = sp.serverLevel();
                    // the goal opens toward the player (north-facing = open side to the north)
                    l.setBlock(at(0, 0, 7), com.hollowhours.registry.HHBlocks.SOCCER_GOAL.get().defaultBlockState().setValue(com.hollowhours.fun.FunBlock.FACING, north), 3);
                    var ball = HHEntities.BALL.get().create(l);
                    if (ball == null) return;
                    ball.setKind(com.hollowhours.fun.BallEntity.SOCCER);
                    ball.moveTo(origin.x, origin.y + 0.1, origin.z + 2.5, 0, 0);
                    ball.setDeltaMovement(0, 0.15, 0.55);
                    ball.touchedBy(sp);
                    l.addFreshEntity(ball);
                });
            }
            if (t == 52 || t == 58 || t == 66 || t == 80) shot("football_t" + (t - 46));
            if (t == 84) server(sp -> {
                HollowHours.LOG.info("[client smoke] football: sanity {} (50 before)", com.hollowhours.ghost.Sanity.get(sp));
                if (com.hollowhours.ghost.Sanity.get(sp) <= 50) HollowHours.LOG.error("[client smoke] the goal did not count (com.hollowhours)");
                Sanity_set(sp, 100);
            });
            return t >= 86;
        });
        // the arcade machine and the TV, close up
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(-1, 1.0, 3);
                server(sp -> {
                    clearArea(sp);
                    pin(sp);
                    ServerLevel l = sp.serverLevel();
                    l.setBlock(at(-1, 0, 3), com.hollowhours.registry.HHBlocks.ARCADE.get().defaultBlockState().setValue(com.hollowhours.fun.FunBlock.FACING, north), 3);
                    l.setBlock(at(1.5, 0, 3), com.hollowhours.registry.HHBlocks.TV.get().defaultBlockState().setValue(com.hollowhours.fun.FunBlock.FACING, north)
                            .setValue(com.hollowhours.fun.FunBlocks.LIT, true), 3);
                });
            }
            if (t == 20) shot("arcade_closeup");
            if (t == 21) lookAt = origin.add(1.5, 0.6, 3);
            if (t == 30) shot("tv_closeup");
            if (t == 32) PhoneScreen.openArcade(at(-1, 0, 3));
            if (t == 38) shot("arcade_screen_home");
            if (t == 39 && mc.screen instanceof PhoneScreen ps) {
                ps.app = PhoneScreen.App.SNAKE;
                ps.snakeStart();
            }
            if (t == 60) shot("arcade_screen_snake");
            if (t == 62) mc.setScreen(null);
            if (t == 64) server(sp -> {
                clearArea(sp);
                pin(sp);
                give(sp, null, null);
            });
            return t >= 66;
        });
        // graves: die with things, get a compass, open it; die again, smash it, bring it all back with a restorer
        for (int round = 0; round < 2; round++) {
            final int rd = round;
            final int[] deadAt = {-1};
            STEPS.add(t -> {
                if (t == 0) {
                    lookAt = origin.add(0, 0.6, 3);
                    server(sp -> {
                        clearArea(sp);
                        pin(sp);
                        sp.getInventory().clearContent();
                        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                        sp.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 12));
                        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                        sp.setExperienceLevels(12);
                        sp.teleportTo(origin.x, origin.y, origin.z + 3);
                    });
                }
                if (t == 5) server(sp -> sp.hurt(sp.damageSources().genericKill(), Float.MAX_VALUE));
                boolean d = mc.player == null || !mc.player.isAlive() || mc.screen instanceof DeathScreen;
                if (d && deadAt[0] < 0) deadAt[0] = t;
                if (deadAt[0] >= 0 && t == deadAt[0] + 20 && mc.player != null) {
                    mc.player.respawn();
                    mc.setScreen(null);
                }
                if (deadAt[0] >= 0 && t == deadAt[0] + 30) server(sp -> {
                    pin(sp);
                    sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 30, 0, false, false));
                });
                if (deadAt[0] >= 0 && t == deadAt[0] + 60) {
                    shot(rd == 0 ? "grave" : "grave_again");
                    server(sp -> {
                        var graves = sp.serverLevel().getEntitiesOfClass(com.hollowhours.grave.GraveEntity.class, sp.getBoundingBox().inflate(8));
                        boolean compass = sp.getInventory().items.stream().anyMatch(it -> it.is(com.hollowhours.registry.HHItems.GRAVE_COMPASS.get()));
                        HollowHours.LOG.info("[client smoke] grave round {}: graves {} compass {} level {}", rd, graves.size(), compass, sp.experienceLevel);
                        if (graves.isEmpty() || !compass) HollowHours.LOG.error("[client smoke] no grave or no compass after dying (com.hollowhours)");
                        if (graves.isEmpty()) return;
                        var ge = graves.get(0);
                        if (rd == 0) ge.interact(sp, net.minecraft.world.InteractionHand.MAIN_HAND);
                        else {
                            for (int i = 0; i < 3; i++) ge.hurt(sp.damageSources().playerAttack(sp), 1f);
                            sp.getInventory().add(new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.RESTORER.get()));
                        }
                    });
                }
                if (rd == 1 && deadAt[0] >= 0 && t == deadAt[0] + 64) server(sp -> {
                    var r = sp.getInventory().items.stream().filter(it -> it.is(com.hollowhours.registry.HHItems.RESTORER.get())).findFirst().orElse(null);
                    if (r != null) {
                        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, r.copy());
                        r.setCount(0);
                        sp.getOffhandItem().use(sp.level(), sp, net.minecraft.world.InteractionHand.OFF_HAND);
                    }
                });
                if (deadAt[0] >= 0 && t == deadAt[0] + 70) {
                    shot(rd == 0 ? "grave_opened" : "grave_restored");
                    server(sp -> {
                        boolean sword = sp.getInventory().contains(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                        boolean graveLeft = !sp.serverLevel().getEntitiesOfClass(com.hollowhours.grave.GraveEntity.class, sp.getBoundingBox().inflate(8)).isEmpty();
                        HollowHours.LOG.info("[client smoke] grave round {}: sword back {} level {} grave still there {}", rd, sword, sp.experienceLevel, graveLeft);
                        if (!sword || sp.experienceLevel < 12 || graveLeft) HollowHours.LOG.error("[client smoke] grave round {} did not give everything back (com.hollowhours)", rd);
                    });
                }
                if (t > 400) {
                    HollowHours.LOG.error("[client smoke] grave round {} never finished (com.hollowhours)", rd);
                    return true;
                }
                return deadAt[0] >= 0 && t >= deadAt[0] + 72;
            });
        }
        // explosions throw blocks: a little brick hut blown up (last: it leaves a crater, which is filled back in)
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 1.5, 12);
                server(sp -> {
                    clearArea(sp);
                    pin(sp);
                    ServerLevel l = sp.serverLevel();
                    for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(at(-2, 0, 10), at(2, 3, 14))) {
                        boolean wall = b.getX() == at(-2, 0, 0).getX() || b.getX() == at(2, 0, 0).getX() || b.getZ() == at(0, 0, 10).getZ() || b.getZ() == at(0, 0, 14).getZ() || b.getY() == at(0, 3, 0).getY();
                        if (wall) l.setBlock(b, net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState(), 3);
                    }
                });
            }
            if (t == 15) shot("explosion_before");
            if (t == 16) server(sp -> sp.serverLevel().explode(null, origin.x, origin.y + 1, origin.z + 12, 3.5f, net.minecraft.world.level.Level.ExplosionInteraction.TNT));
            if (t == 18 || t == 22 || t == 28 || t == 38 || t == 70) shot("explosion_t" + (t - 16));
            if (t == 72) server(sp -> {
                int flying = sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, sp.getBoundingBox().inflate(40)).size();
                HollowHours.LOG.info("[client smoke] explosion debris still flying: {}", flying);
                ServerLevel l = sp.serverLevel();
                clearArea(sp);
                for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(at(-8, -3, 4), at(8, -1, 20))) {
                    var want = b.getY() == at(0, -1, 0).getY() ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK : net.minecraft.world.level.block.Blocks.DIRT;
                    if (l.getBlockState(b).isAir()) l.setBlock(b, want.defaultBlockState(), 2);
                }
                pin(sp);
            });
            return t >= 76;
        });
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
