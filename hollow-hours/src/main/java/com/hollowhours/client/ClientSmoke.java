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
    /** CI promo shoot: instead of the test tour, film the update (portrait frames for the promo clip) */
    private static final boolean PROMO = Boolean.getBoolean("hollowhours.promo");
    /** promo HUD: 0 = nothing but the hand, 1 = also ammo / struggle bar / boss bar / titles */
    static int promoHud = 0;

    @SubscribeEvent
    public static void promoOverlays(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre e) {
        if (!ON || !PROMO) return;
        String id = e.getOverlay().id().toString();
        boolean keep = promoHud == 1 && (id.equals("hollowhours:combat") || id.equals("minecraft:boss_event_progress") || id.equals("minecraft:title_text")
                || id.equals("hollowhours:horror_fx") || id.equals("hollowhours:ending") || id.equals("hollowhours:jumpscare"))
                || id.equals("hollowhours:ending");
        if (!keep) e.setCanceled(true);
    }

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
    /** looking straight ahead along +z with this pitch (first-person body shots), -1 = off */
    private static float lookDown = -1;

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
            if (lookDown >= 0 && mc.player.isAlive()) {
                mc.player.setYRot(0);
                mc.player.yRotO = 0;
                mc.player.setXRot(lookDown);
                mc.player.xRotO = lookDown;
            } else if (lookAt != null && !KillCam.active() && mc.player.isAlive()) face(mc, lookAt);
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
        if (PROMO) {
            promo(mc);
            return;
        }
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
            if (t == 22) server(sp -> {
                boolean flash = sp.getInventory().items.stream().anyMatch(it -> it.is(com.hollowhours.registry.HHItems.FLASHLIGHT.get()));
                boolean battery = sp.getInventory().items.stream().anyMatch(it -> it.is(com.hollowhours.registry.HHItems.BATTERY.get()));
                HollowHours.LOG.info("[client smoke] starter kit: flashlight {} battery {}", flash, battery);
                if (!flash || !battery) HollowHours.LOG.error("[client smoke] no starter flashlight / battery (com.hollowhours)");
            });
            return t >= 30;
        });
        // the recipe book (K): how things are made, what they are used for, every item of every mod
        STEPS.add(t -> {
            if (t == 0) {
                com.hollowhours.client.recipes.RecipeIndex.clear();
                mc.setScreen(new com.hollowhours.client.recipes.RecipeScreen(null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE), false));
                int n = com.hollowhours.client.recipes.RecipeIndex.recipeCount(), items = com.hollowhours.client.recipes.RecipeIndex.items().size();
                HollowHours.LOG.info("[client smoke] recipe book: {} items, {} recipes", items, n);
                if (n < 500 || items < 800) HollowHours.LOG.error("[client smoke] recipe book index too small ({} items, {} recipes) (com.hollowhours)", items, n);
                int pick = com.hollowhours.client.recipes.RecipeIndex.forItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE), false).size();
                int ingot = com.hollowhours.client.recipes.RecipeIndex.forItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT), true).size();
                int sword = com.hollowhours.client.recipes.RecipeIndex.forItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD), false).size();
                int mod = com.hollowhours.client.recipes.RecipeIndex.forItem(new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.FLASHLIGHT.get()), false).size();
                HollowHours.LOG.info("[client smoke] recipe book: iron pickaxe made by {}, iron ingot used in {}, netherite sword made by {}, flashlight made by {}", pick, ingot, sword, mod);
                if (pick < 1 || ingot < 20 || sword < 1) HollowHours.LOG.error("[client smoke] recipe lookups are wrong (com.hollowhours)");
            }
            if (t == 15) shot("recipes_iron_pickaxe");
            if (t == 16) mc.setScreen(new com.hollowhours.client.recipes.RecipeScreen(null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT), true));
            if (t == 30) shot("recipes_uses_iron_ingot");
            if (t == 31) mc.setScreen(new com.hollowhours.client.recipes.RecipeScreen(null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD), false));
            if (t == 45) shot("recipes_smithing");
            if (t == 46) mc.setScreen(new com.hollowhours.client.recipes.RecipeScreen(null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT), false));
            if (t == 60) shot("recipes_smelting");
            if (t == 61) mc.setScreen(new com.hollowhours.client.recipes.RecipeScreen(null, new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.FLASHLIGHT.get()), false));
            if (t == 75) shot("recipes_mod_item");
            if (t == 76) mc.setScreen(new com.hollowhours.client.recipes.RecipeScreen(null, net.minecraft.world.item.ItemStack.EMPTY, false));
            if (t == 90) shot("recipes_browser");
            if (t == 91) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            if (t == 95) mc.setScreen(new com.hollowhours.client.recipes.RecipeScreen(mc.screen, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH), false));
            if (t == 109) shot("recipes_from_inventory");
            if (t == 110 && mc.screen != null) mc.screen.onClose();
            if (t == 115) {
                if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) HollowHours.LOG.error("[client smoke] closing the recipe book did not go back to the inventory (com.hollowhours)");
                mc.setScreen(null);
            }
            return t >= 118;
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
        bodyLanguage(mc, false);
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

        bodyLanguage(mc, true);

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

        cover(mc);

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
        // the phone's chat (Mom answers, a location to Dad), the map, the rescue countdown, the last messages, navigating
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                net.minecraft.world.item.ItemStack ph = com.hollowhours.item.PhoneItem.fresh(sp.getRandom());
                com.hollowhours.item.PhoneItem.setBattery(ph, 80);
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ph);
                com.hollowhours.phone.PhoneChat.reset(sp);
                com.hollowhours.rescue.Rescue.schedule(sp.server);
                com.hollowhours.phone.PhoneInfo.send(sp);
            });
            if (t == 10) PhoneScreen.open(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (t == 12) shot("phone_home_apps");
            if (t == 14 && mc.screen instanceof PhoneScreen ps) ps.app = PhoneScreen.App.CHAT;
            if (t == 18) shot("phone_chat_contacts");
            if (t == 20) {
                PhoneApps.chatWith = "mom";
                PhoneApps.typed.append("hi mom, are you okay?");
            }
            if (t == 22 && mc.screen instanceof PhoneScreen ps) PhoneApps.sendTyped(ps);
            if (t == 28) shot("phone_chat_typing");
            if (t == 150) shot("phone_chat_mom");
            if (t == 152 && mc.screen instanceof PhoneScreen ps) {
                PhoneApps.chatWith = "dad";
                PhoneApps.sendLocation(ps);
            }
            if (t == 260) shot("phone_chat_dad_location");
            if (t == 262 && mc.screen instanceof PhoneScreen ps) ps.app = PhoneScreen.App.MAP;
            if (t == 270) shot("phone_map");
            if (t == 272 && mc.screen instanceof PhoneScreen ps) ps.app = PhoneScreen.App.RESCUE;
            if (t == 276) shot("phone_rescue");
            if (t == 278) server(sp -> com.hollowhours.phone.PhoneChat.storyMessages(sp, 3));
            if (t == 290 && mc.screen instanceof PhoneScreen ps) {
                ps.app = PhoneScreen.App.CHAT;
                PhoneApps.chatWith = null;
            }
            if (t == 300) shot("phone_final_notice");
            if (t == 600) {
                PhoneApps.chatWith = "friend";
                shot("phone_chat_contacts_lost");
            }
            if (t == 604) shot("phone_chat_final");
            if (t == 606 && mc.screen instanceof PhoneScreen ps) {
                PhoneApps.typed.append("hello?? answer me");
                PhoneApps.sendTyped(ps);
            }
            if (t == 620) shot("phone_chat_failed");
            if (t == 622) server(sp -> {
                List<String> l = com.hollowhours.phone.PhoneChat.log(sp, "friend");
                boolean finalCame = l.stream().anyMatch(x -> x.endsWith("friend.final")), failed = l.get(l.size() - 1).startsWith("me|x|");
                List<String> mom = com.hollowhours.phone.PhoneChat.log(sp, "mom"), dad = com.hollowhours.phone.PhoneChat.log(sp, "dad");
                boolean momAnswered = mom.size() >= 3 && mom.get(2).startsWith("them|"), dadGotLocation = dad.stream().anyMatch(x -> x.startsWith("me|l|"));
                HollowHours.LOG.info("[client smoke] chat: mom answered {}, location to dad {}, last messages {}, then not delivered {}", momAnswered, dadGotLocation, finalCame, failed);
                if (!momAnswered || !dadGotLocation || !finalCame || !failed) HollowHours.LOG.error("[client smoke] the phone chat is not right (com.hollowhours)");
            });
            if (t == 624) {
                var info = ClientPhone.info;
                if (info != null) for (var tg : info.targets())
                    if (tg.kind().equals("home")) ClientPhone.navigate(new ClientPhone.Nav("home", "Home", tg.x() + 40, tg.y(), tg.z() + 25, tg.dim()));
                mc.setScreen(null);
                lookAt = origin.add(0, 1.6, 30);
            }
            if (t == 640) shot("nav_arrow");
            if (t == 642) {
                if (ClientPhone.nav == null) HollowHours.LOG.error("[client smoke] navigation did not start (com.hollowhours)");
                ClientPhone.navigate(null);
                server(sp -> {
                    com.hollowhours.phone.PhoneChat.reset(sp);
                    com.hollowhours.rescue.Rescue.cancelPlan(sp.server);
                });
            }
            return t >= 644;
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
            // thirst follows the hunger bar (10% faster); the canteen gives 15% more than a water bottle
            if (t == 45) server(sp -> {
                Thirst_set(sp, 12);
                com.hollowhours.ghost.Thirst.setHydration(sp, 0);
                sp.getFoodData().setFoodLevel(20);
                sp.getFoodData().setSaturation(0);
                com.hollowhours.ghost.Thirst.resetTracking(sp);
                sp.causeFoodExhaustion(4.01f);
            });
            if (t == 70) server(sp -> {
                float th = com.hollowhours.ghost.Thirst.get(sp), food = sp.getFoodData().getFoodLevel();
                var can = new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.CANTEEN.get());
                can.finishUsingItem(sp.level(), sp);
                float after = com.hollowhours.ghost.Thirst.get(sp);
                HollowHours.LOG.info("[client smoke] thirst after one hunger point: {} (food {}), canteen drink: {} -> {}, sips {}", th, food, th, after,
                        com.hollowhours.item.CanteenItem.sips(can));
                if (Math.abs(th - (12 - 1.1f)) > 0.05f || Math.abs(after - th - 7 * 1.15f) > 0.05f || com.hollowhours.item.CanteenItem.sips(can) != 2)
                    HollowHours.LOG.error("[client smoke] thirst / canteen numbers are off (com.hollowhours)");
            });
            if (t == 41) server(sp -> {
                Thirst_set(sp, 20);
                Sanity_set(sp, 100);
            });
            return t >= 72;
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
        combat(mc);

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

        rescue(mc);
        endgame(mc);
        // the main menu (its panorama is the haunted clearing above)
        STEPS.add(t -> {
            if (t == 0) mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            if (t == 40 || t == 140) shot("title_screen_t" + t);
            if (t == 142) mc.setScreen(null);
            return t >= 145;
        });
    }

    // ------------------------------------------------------------------ the rescue: helicopter, haven, invasion, home
    @javax.annotation.Nullable
    private static com.hollowhours.rescue.HelicopterEntity heliNear(Minecraft mc) {
        com.hollowhours.rescue.HelicopterEntity best = null;
        for (var en : mc.level.entitiesForRendering())
            if (en instanceof com.hollowhours.rescue.HelicopterEntity h && h.kind() != com.hollowhours.rescue.HelicopterEntity.DROP_OFF
                    && (best == null || h.distanceTo(mc.player) < best.distanceTo(mc.player))) best = h;
        return best;
    }

    private static void rescue(Minecraft mc) {
        // help is coming (the phone), then the helicopter flies in
        STEPS.add(t -> {
            if (t == 0) {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                lookAt = origin.add(0, 1.6, 30);
                server(sp -> {
                    clearAll(sp);
                    pin(sp);
                    sp.setGameMode(GameType.SURVIVAL);
                    sp.serverLevel().setDayTime(6000);
                    com.hollowhours.rescue.Rescue.reset(sp);
                    com.hollowhours.rescue.Rescue.schedule(sp.server);
                });
            }
            if (t == 130) shot("rescue_coming_notice");
            if (t == 140) server(sp -> com.hollowhours.rescue.Rescue.arriveNow(sp.server));
            var h = heliNear(mc);
            if (h != null) lookAt = h.position().add(0, 2, 0);
            if (t == 200) shot("rescue_fly_in");
            if (t == 300) shot("rescue_descend");
            if (t == 390) shot("rescue_landed");
            if (t == 392 && h == null) HollowHours.LOG.error("[client smoke] no rescue helicopter came (com.hollowhours)");
            return t >= 392;
        });
        // a close look at it, then climbing in
        STEPS.add(t -> {
            var h = heliNear(mc);
            if (h == null) return t > 40;
            if (t == 0) server(sp -> {
                var sh = com.hollowhours.rescue.HelicopterEntity.live(com.hollowhours.rescue.HelicopterEntity.PICKUP);
                if (sh.isEmpty()) return;
                Vec3 v = sh.get(0).local(-7, 0, 1);
                sp.teleportTo(v.x, sh.get(0).getY(), v.z);
            });
            if (t >= 2 && t < 40) lookAt = h.position().add(0, 1.5, 0);
            if (t == 20) shot("rescue_door");
            if (t == 24) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            if (t == 34) shot("rescue_door_tp");
            if (t == 36) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (t == 40) server(sp -> {
                var sh = com.hollowhours.rescue.HelicopterEntity.live(com.hollowhours.rescue.HelicopterEntity.PICKUP);
                if (!sh.isEmpty()) sh.get(0).interact(sp, net.minecraft.world.InteractionHand.MAIN_HAND);
                HollowHours.LOG.info("[client smoke] boarded the pickup: {}", sp.getVehicle() instanceof com.hollowhours.rescue.HelicopterEntity);
                if (!(sp.getVehicle() instanceof com.hollowhours.rescue.HelicopterEntity))
                    HollowHours.LOG.error("[client smoke] could not board the helicopter (com.hollowhours)");
            });
            if (t > 42) lookAt = h.local(-10, 1.6, 0.4);
            if (t == 56) shot("rescue_aboard");
            if (t == 60) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            if (t == 72) shot("rescue_aboard_tp");
            if (t == 150) shot("rescue_takeoff_tp");
            if (t == 152) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (t == 180) shot("rescue_fade");
            return mc.level.dimension() == com.hollowhours.rescue.Rescue.HAVEN || t > 500;
        });
        // the haven (once the new world has loaded)
        STEPS.add(t -> mc.screen == null && mc.level.dimension() == com.hollowhours.rescue.Rescue.HAVEN && t > 40 || t > 1600);
        STEPS.add(t -> {
            if (t == 0) {
                HollowHours.LOG.info("[client smoke] in the haven: {}", mc.level.dimension() == com.hollowhours.rescue.Rescue.HAVEN);
                if (mc.level.dimension() != com.hollowhours.rescue.Rescue.HAVEN) HollowHours.LOG.error("[client smoke] the helicopter did not reach the haven (com.hollowhours)");
                server(sp -> sp.serverLevel().setDayTime(5000));
            }
            Vec3 me = mc.player.position();
            if (t == 4) shot("haven_notice");
            for (int i = 0; i < 4; i++) if (t == 60 + i * 25 - 10) lookAt = me.add(Math.sin(i * Math.PI / 2) * 40, 6, Math.cos(i * Math.PI / 2) * 40);
            for (int i = 0; i < 4; i++) if (t == 60 + i * 25) shot("haven_view_" + i);
            if (t == 160) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            if (t == 170) shot("haven_me");
            if (t == 172) {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                server(sp -> {
                    var l = sp.serverLevel();
                    var box = sp.getBoundingBox().inflate(96);
                    int animals = l.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, box).size();
                    int monsters = l.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, box, m -> !(m instanceof GhostEntity)).size();
                    String biome = l.getBiome(sp.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("?");
                    HollowHours.LOG.info("[client smoke] haven: phase {}, biome {}, animals {}, monsters {}, safe {}", com.hollowhours.rescue.Rescue.phase(sp), biome,
                            animals, monsters, com.hollowhours.rescue.Rescue.safe(sp));
                    if (com.hollowhours.rescue.Rescue.phase(sp) != com.hollowhours.rescue.Rescue.IN_HAVEN || monsters > 0 || !com.hollowhours.rescue.Rescue.safe(sp))
                        HollowHours.LOG.error("[client smoke] the haven is not right (com.hollowhours)");
                });
            }
            return t >= 174;
        });
        // they find it: the extraction helicopter lands out there, ghosts come (they can't hurt the tour: resistance)
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false));
                com.hollowhours.rescue.Rescue.invadeNow(sp);
            });
            var h = heliNear(mc);
            GhostEntity ghost = null;
            for (var en : mc.level.entitiesForRendering()) if (en instanceof GhostEntity gh) ghost = gh;
            if (t == 40) shot("invasion_notice");
            if (t > 100 && t < 160 && ghost != null) lookAt = ghost.position().add(0, 1.5, 0);
            if (t == 158) shot("invasion_ghost");
            if (t >= 160 && h != null) lookAt = h.position().add(0, 2, 0);
            if (t == 230) shot("extract_coming");
            if (t == 300) server(sp -> HollowHours.LOG.info("[client smoke] invasion: ghosts near {}, extraction helicopters {}",
                    com.hollowhours.ghost.GhostBrain.ghostsNear(sp.serverLevel(), sp.position(), 64).size(),
                    com.hollowhours.rescue.HelicopterEntity.live(com.hollowhours.rescue.HelicopterEntity.EXTRACT).size()));
            if (t == 302 && com.hollowhours.rescue.HelicopterEntity.live(com.hollowhours.rescue.HelicopterEntity.EXTRACT).isEmpty())
                HollowHours.LOG.error("[client smoke] no extraction helicopter in the haven (com.hollowhours)");
            return t >= 304;
        });
        // once it has landed: aboard, and home for good
        STEPS.add(t -> {
            if (t % 10 == 0 && !(mc.player.getVehicle() instanceof com.hollowhours.rescue.HelicopterEntity)) server(sp -> {
                for (var h : com.hollowhours.rescue.HelicopterEntity.live(com.hollowhours.rescue.HelicopterEntity.EXTRACT)) {
                    if (h.state() != com.hollowhours.rescue.HelicopterEntity.LANDED || sp.getVehicle() != null) continue;
                    Vec3 v = h.local(-6, 0, 1);
                    sp.teleportTo(v.x, h.getY(), v.z);
                    h.interact(sp, net.minecraft.world.InteractionHand.MAIN_HAND);
                    HollowHours.LOG.info("[client smoke] boarded the extraction: {}", sp.getVehicle() == h);
                }
            });
            var h = heliNear(mc);
            if (h != null && mc.player.getVehicle() == null) lookAt = h.position().add(0, 2, 0);
            if (t == 120) shot("extract_waiting");
            if (mc.player.getVehicle() instanceof com.hollowhours.rescue.HelicopterEntity v && v.isLeaving() && v.stateTicks == 40) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                shot("extract_takeoff_tp");
            }
            if (mc.player.getVehicle() instanceof com.hollowhours.rescue.HelicopterEntity v && v.isLeaving() && v.stateTicks == 44)
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            return mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD || t > 900;
        });
        STEPS.add(t -> mc.screen == null && t > 40 || t > 1200);
        STEPS.add(t -> {
            if (t == 0) lookAt = mc.player.position().add(0, 1.6, 30);
            if (t == 30) shot("home_notice");
            if (t == 32) server(sp -> {
                double g = com.hollowhours.rescue.Rescue.harshness(sp, false), b = com.hollowhours.rescue.Rescue.harshness(sp, true);
                HollowHours.LOG.info("[client smoke] home: phase {}, ghosts x{}, blood moon x{}", com.hollowhours.rescue.Rescue.phase(sp), g, b);
                if (com.hollowhours.rescue.Rescue.phase(sp) != com.hollowhours.rescue.Rescue.HOME || g != 3 || b != 6)
                    HollowHours.LOG.error("[client smoke] coming home did not work (com.hollowhours)");
            });
            return t >= 34;
        });
    }

    // ------------------------------------------------------------------ the last chapter: five down, the map, the mountain, the boss, the end
    static Vec3 egCenter = Vec3.ZERO, egDir = new Vec3(1, 0, 0), egHome = Vec3.ZERO;
    static float egYaw;

    @javax.annotation.Nullable
    private static com.hollowhours.endgame.WeepingEntity serverBoss(ServerPlayer sp) {
        var l = sp.serverLevel().getEntitiesOfClass(com.hollowhours.endgame.WeepingEntity.class, sp.getBoundingBox().inflate(64));
        return l.isEmpty() ? null : l.get(0);
    }

    @javax.annotation.Nullable
    private static com.hollowhours.endgame.WeepingEntity clientBoss(Minecraft mc) {
        for (var en : mc.level.entitiesForRendering()) if (en instanceof com.hollowhours.endgame.WeepingEntity w) return w;
        return null;
    }

    /** the player stands `dist` blocks in front of the boss (at its feet level), facing it */
    private static void standBefore(ServerPlayer sp, double dist, double side) {
        Vec3 f = new Vec3(-Math.sin(Math.toRadians(egYaw)), 0, Math.cos(Math.toRadians(egYaw)));
        Vec3 r = new Vec3(-f.z, 0, f.x);
        Vec3 at = egCenter.add(f.scale(dist)).add(r.scale(side));
        sp.teleportTo(at.x, egCenter.y, at.z);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.setHealth(sp.getMaxHealth());
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 3, false, false));
    }

    private static void endgame(Minecraft mc) {
        // all five come at once; each one knocked out stays down
        STEPS.add(t -> {
            if (t == 0) {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                egHome = mc.player.position();
                lookAt = egHome.add(0, 1.6, 20);
                server(sp -> {
                    clearAll(sp);
                    sp.serverLevel().setDayTime(18000);
                    sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 30, 0, false, false));
                    sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 10, 4, false, false));
                    int st = com.hollowhours.endgame.Endgame.stage(sp.server);
                    HollowHours.LOG.info("[client smoke] endgame after coming home: {}", com.hollowhours.endgame.Endgame.status(sp.server));
                    if (st != com.hollowhours.endgame.Endgame.COUNTDOWN) HollowHours.LOG.error("[client smoke] coming home did not start the countdown (com.hollowhours)");
                    com.hollowhours.endgame.Endgame.startHunt(sp.server);
                });
            }
            if (t == 30) shot("endgame_hunt_title");
            // knock each one out as it comes, then line them up in front of the player
            if (t > 40 && t % 10 == 0 && t < 330) server(sp -> {
                net.minecraftforge.common.util.FakePlayer fp = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(sp.serverLevel());
                for (GhostEntity g : Director.allGhosts(sp.server)) {
                    if (com.hollowhours.combat.GhostCombat.knockedOut(g.brain())) continue;
                    for (int i = 0; i < 300 && !com.hollowhours.combat.GhostCombat.knockedOut(g.brain()); i++) com.hollowhours.combat.GhostCombat.poiseHit(g, sp, 25, false);
                }
            });
            if (t == 330) server(sp -> {
                int i = 0;
                for (GhostEntity g : Director.allGhosts(sp.server)) {
                    double a = Math.toRadians(-50 + 25 * i++);
                    g.moveTo(egHome.x + Math.sin(a) * 7, egHome.y, egHome.z + Math.cos(a) * 7, g.getYRot(), 0);
                }
                var d = com.hollowhours.endgame.Endgame.data(sp.server);
                HollowHours.LOG.info("[client smoke] final hunt: {} ghosts, down mask {}, stage {}", Director.allGhosts(sp.server).size(), Integer.toBinaryString(d.down), d.stage);
                if (Integer.bitCount(d.down) < 5) HollowHours.LOG.error("[client smoke] not all five were counted down (com.hollowhours)");
            });
            if (t == 345) lookAt = egHome.add(0, 0.5, 7);
            if (t == 360) shot("endgame_five_down");
            if (t == 470) shot("endgame_they_remember");
            if (t == 720) shot("endgame_map_given");
            return t >= 724;
        });
        // the old map in hand
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                int slot = -1;
                for (int i = 0; i < 9; i++) if (com.hollowhours.endgame.Endgame.isMountainMap(sp.getInventory().getItem(i))) slot = i;
                if (slot < 0) for (int i = 9; i < 36; i++) if (com.hollowhours.endgame.Endgame.isMountainMap(sp.getInventory().getItem(i))) {
                    var m = sp.getInventory().getItem(i);
                    sp.getInventory().setItem(i, sp.getInventory().getItem(0));
                    sp.getInventory().setItem(0, m);
                    slot = 0;
                }
                HollowHours.LOG.info("[client smoke] mountain map in slot {}, stage {}", slot, com.hollowhours.endgame.Endgame.status(sp.server));
                if (slot < 0) HollowHours.LOG.error("[client smoke] no map to the mountain (com.hollowhours)");
                else {
                    sp.getInventory().selected = slot;
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(slot));
                }
                sp.containerMenu.broadcastChanges();
            });
            if (t == 2) lookAt = mc.player.position().add(0, 0.2, 6);
            if (t == 40) shot("endgame_map_in_hand");
            return t >= 42;
        });
        // the mountain is built near here (for the tour); look at it from outside
        final int[] builtAt = {-1};
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                net.minecraft.core.BlockPos site = net.minecraft.core.BlockPos.containing(egHome).offset(110, 0, 0);
                com.hollowhours.endgame.Endgame.setSite(sp.server, site);
                Vec3 dir = com.hollowhours.endgame.Arena.mouthDir(sp.server);
                egDir = dir;
                Vec3 view = Vec3.atBottomCenterOf(site).add(dir.scale(82));
                int y = sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) view.x, (int) view.z);
                sp.teleportTo(view.x, y + 6, view.z);
                sp.getAbilities().flying = true;
                sp.getAbilities().mayfly = true;
                sp.onUpdateAbilities();
            });
            if (t > 5 && builtAt[0] < 0 && t % 10 == 0) server(sp -> {
                if (com.hollowhours.endgame.Endgame.data(sp.server).built == 2 && builtAt[0] < 0) {
                    builtAt[0] = 0;
                    egCenter = Vec3.atBottomCenterOf(com.hollowhours.endgame.Endgame.center(sp.server));
                }
            });
            if (builtAt[0] >= 0) {
                builtAt[0]++;
                net.minecraft.core.BlockPos site = net.minecraft.core.BlockPos.containing(egHome).offset(110, 0, 0);
                if (builtAt[0] == 2) lookAt = Vec3.atBottomCenterOf(site).add(0, 12, 0);
                if (builtAt[0] == 40) shot("endgame_mountain");
                if (builtAt[0] == 42) server(sp -> {
                    Vec3 at = egCenter.add(egDir.scale(com.hollowhours.endgame.Arena.TRENCH + 6));
                    int y = sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
                    sp.teleportTo(at.x, y + 1.5, at.z);
                });
                if (builtAt[0] == 46) {
                    Vec3 m = egCenter.add(egDir.scale(com.hollowhours.endgame.Arena.MOUTH));
                    lookAt = new Vec3(m.x, mc.player.getEyeY() - 2.5, m.z);
                }
                if (builtAt[0] == 80) shot("endgame_mine_mouth");
                if (builtAt[0] == 82) server(sp -> {
                    Vec3 at = egCenter.add(egDir.scale(com.hollowhours.endgame.Arena.RX + 0.5));
                    var c = com.hollowhours.endgame.Endgame.center(sp.server);
                    sp.getAbilities().flying = false;
                    sp.onUpdateAbilities();
                    sp.teleportTo(at.x, c.getY() + (com.hollowhours.endgame.Arena.ledge(sp.server) - c.getY()) + 0.1, at.z);
                });
                if (builtAt[0] == 86) lookAt = egCenter.add(0, 1.5, 0);
                if (builtAt[0] == 130) shot("endgame_cavern_from_ledge");
                return builtAt[0] >= 132;
            }
            if (t > 900) {
                HollowHours.LOG.error("[client smoke] the mountain was never built (com.hollowhours)");
                return true;
            }
            return false;
        });
        // down into the cavern: it wakes and rises out of the floor
        final int[] riseAt = {-1};
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                Vec3 at = egCenter.add(egDir.scale(9));
                sp.teleportTo(at.x, egCenter.y, at.z);
            });
            if (t == 2) lookAt = egCenter.add(0, 3.5, 0);
            if (riseAt[0] < 0 && clientBoss(mc) != null) {
                riseAt[0] = t;
                server(sp -> {
                    var b = serverBoss(sp);
                    if (b != null) {
                        b.ciHold(100000);
                        egYaw = b.getYRot();
                    }
                });
            }
            if (riseAt[0] >= 0) {
                int d = t - riseAt[0];
                if (d == 20 || d == 50 || d == 90) shot("boss_rise_t" + d);
                if (d == 100) server(sp -> {
                    var b = serverBoss(sp);
                    HollowHours.LOG.info("[client smoke] boss: health {}/{} anim {}", b == null ? -1 : b.getHealth(), b == null ? -1 : b.getMaxHealth(), b == null ? -1 : b.anim());
                    if (b == null || b.anim() != com.hollowhours.endgame.WeepingEntity.A_IDLE) HollowHours.LOG.error("[client smoke] the boss did not rise (com.hollowhours)");
                    else egYaw = b.getYRot();
                });
                if (d == 104) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    shot("boss_idle_tp");
                }
                if (d == 106) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                return d >= 110;
            }
            if (t > 400) {
                HollowHours.LOG.error("[client smoke] the boss never woke up (com.hollowhours)");
                return true;
            }
            return false;
        });
        // every attack, one by one
        String[][] attacks = {{"slam_near", "5.2", "0"}, {"slam_far", "7.4", "0"}, {"sweep", "4.5", "0"}, {"scream", "6", "0"}, {"weep", "8", "0"}, {"grab", "4.6", "0"}};
        for (String[] atk : attacks) {
            String name = atk[0];
            double dist = Double.parseDouble(atk[1]);
            final int[] freedAt = {-1};
            STEPS.add(t -> {
                if (t == 0) {
                    freedAt[0] = -1;
                    server(sp -> standBefore(sp, dist, 0));
                }
                if (t == 2) lookAt = egCenter.add(0, 3.2, 0);
                if (t == 6) mc.options.setCameraType(name.startsWith("slam") || name.equals("sweep") ? net.minecraft.client.CameraType.THIRD_PERSON_BACK : net.minecraft.client.CameraType.FIRST_PERSON);
                if (t == 8) server(sp -> {
                    var b = serverBoss(sp);
                    if (b == null) HollowHours.LOG.error("[client smoke] no boss for {} (com.hollowhours)", name);
                    else b.ciAttack(name, sp);
                });
                int[] at = switch (name) {
                    case "slam_near", "slam_far" -> new int[]{18, 29, 31, 40};
                    case "sweep" -> new int[]{20, 29, 33, 38};
                    case "scream" -> new int[]{18, 26, 40};
                    case "weep" -> new int[]{25, 40, 55, 75};
                    default -> new int[]{16, 22, 34};
                };
                for (int k = 0; k < at.length; k++) if (t == at[k]) shot("boss_" + name + "_" + (k + 1));
                if (name.equals("grab") && t > 34 && freedAt[0] < 0) {
                    if (CombatClient.struggling()) {
                        if (t % 2 == 0) net.minecraft.client.KeyMapping.click(mc.options.keyJump.getKey());
                        if (t == 50) shot("boss_grab_struggle");
                    } else if (t > 40) {
                        freedAt[0] = t;
                        HollowHours.LOG.info("[client smoke] boss grab over: result {}", CombatClient.result);
                        if (CombatClient.result != 1) HollowHours.LOG.error("[client smoke] could not break free of the boss (com.hollowhours)");
                    }
                }
                if (name.equals("grab")) {
                    if (freedAt[0] >= 0 && t == freedAt[0] + 6) shot("boss_grab_freed_stun");
                    if (t > 260 && freedAt[0] < 0) {
                        HollowHours.LOG.error("[client smoke] the boss grab never ended (com.hollowhours)");
                        return true;
                    }
                    return freedAt[0] >= 0 && t >= freedAt[0] + 50;
                }
                if (t == at[at.length - 1] + 2) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                return t >= at[at.length - 1] + 40;
            });
        }
        // beaten: it stops crying, reaches up, sinks; the rift opens
        final int[] riftAt = {-1};
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                standBefore(sp, 9, 0);
                var b = serverBoss(sp);
                if (b != null) b.hurt(b.damageSources().genericKill(), 100000);
            });
            if (t == 2) lookAt = egCenter.add(0, 4, 0);
            if (t == 20 || t == 60 || t == 110) shot("boss_defeat_t" + t);
            if (t == 130) shot("boss_won_title");
            if (t > 150 && riftAt[0] < 0) for (var en : mc.level.entitiesForRendering())
                if (en instanceof com.hollowhours.endgame.RiftEntity) riftAt[0] = t;
            if (riftAt[0] >= 0) {
                int d = t - riftAt[0];
                if (d == 2) lookAt = egCenter.add(0, 2, 0);
                if (d == 50) shot("rift");
                if (d == 52) server(sp -> HollowHours.LOG.info("[client smoke] after the boss: {}", com.hollowhours.endgame.Endgame.status(sp.server)));
                return d >= 54;
            }
            if (t > 500) {
                HollowHours.LOG.error("[client smoke] no rift after the boss (com.hollowhours)");
                return true;
            }
            return false;
        });
        // into the light: the flash, the boom, the credits; home, and the world at peace
        STEPS.add(t -> {
            if (t == 0) server(sp -> sp.teleportTo(egCenter.x, egCenter.y + 0.5, egCenter.z));
            if (t == 3 || t == 10) shot("ending_flash_t" + t);
            if (mc.screen instanceof CreditsScreen cs) {
                if (cs.ticks == 25) shot("credits_start");
                if (cs.ticks == 40) cs.scroll = 260;
                if (cs.ticks == 45) shot("credits_epilogue");
                if (cs.ticks == 46) cs.scroll = 560;
                if (cs.ticks == 50) shot("credits_five");
                if (cs.ticks == 51) cs.scroll = 1000;
                if (cs.ticks == 55) shot("credits_names");
                if (cs.ticks == 56) cs.scroll = cs.total - cs.height * 0.5f;
                if (cs.ticks == 62) shot("credits_end");
                if (cs.ticks == 64) cs.onClose();
            }
            if (t > 80 && mc.screen == null) {
                server(sp -> {
                    boolean peace = com.hollowhours.endgame.Endgame.peaceful(sp.server), story = com.hollowhours.ghost.Story.active(sp.server);
                    HollowHours.LOG.info("[client smoke] the end: peaceful {} story {} home {} ghosts {}", peace, story, sp.position().distanceTo(egHome) < 40,
                            Director.allGhosts(sp.server).size());
                    if (!peace || story) HollowHours.LOG.error("[client smoke] the world is not at peace after the end (com.hollowhours)");
                });
                return true;
            }
            if (t > 600) {
                HollowHours.LOG.error("[client smoke] the credits never rolled (com.hollowhours)");
                mc.setScreen(null);
                return true;
            }
            return false;
        });
        STEPS.add(t -> {
            if (t == 0) lookAt = mc.player.position().add(0, 1.6, 30);
            if (t == 40) shot("endgame_peaceful_home");
            return t >= 42;
        });
    }

    // ------------------------------------------------------------------ first-person body language
    private static void keys(Minecraft mc, boolean up, boolean sprint, boolean sneak) {
        mc.options.keyUp.setDown(up);
        mc.options.keySprint.setDown(sprint);
        mc.options.keyShift.setDown(sneak);
        if (mc.player != null) mc.player.setSprinting(sprint);
    }

    /** Hands in first person (and the body in third person) while standing, walking, running, sneaking, jumping, eating, drinking. */
    private static void bodyLanguage(Minecraft mc, boolean scared) {
        String tag = scared ? "scared" : "happy";
        record Move(String name, boolean up, boolean sprint, boolean sneak, boolean jump, int[] shots) {}
        Move[] moves = {
                new Move("idle", false, false, false, false, new int[] {20, 30}),
                new Move("walk", true, false, false, false, new int[] {18, 23}),
                new Move("run", true, true, false, false, new int[] {18, 22}),
                new Move("sneak", true, false, true, false, new int[] {20, 26}),
                new Move("jump", false, false, false, true, new int[] {13, 18, 24})};
        for (Move mv : moves) {
            STEPS.add(t -> {
                if (t == 0) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    lookAt = origin.add(0, 1.6, 30);
                    Fear.force = scared ? 0.6f : -1;
                    server(sp -> {
                        clearAll(sp);
                        pin(sp);
                        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                        sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                    });
                }
                if (t == 4) keys(mc, mv.up(), mv.sprint(), mv.sneak());
                if (mv.jump() && t == 10 && mc.player != null) mc.player.jumpFromGround();
                for (int i = 0; i < mv.shots().length; i++) if (t == mv.shots()[i]) shot("fp_" + tag + "_" + mv.name() + "_" + (char) ('a' + i));
                if (t >= 32) {
                    keys(mc, false, false, false);
                    return true;
                }
                return false;
            });
        }
        // looking down at your own body while moving
        Move[] down = {
                new Move("idle", false, false, false, false, new int[] {22}),
                new Move("walk", true, false, false, false, new int[] {18, 23}),
                new Move("run", true, true, false, false, new int[] {18, 22}),
                new Move("sneak", true, false, true, false, new int[] {20, 26}),
                new Move("jump", false, false, false, true, new int[] {14, 20})};
        for (Move mv : down) {
            STEPS.add(t -> {
                if (t == 0) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    lookDown = 62;
                    Fear.force = scared ? 0.6f : -1;
                    server(sp -> {
                        clearAll(sp);
                        pin(sp);
                    });
                }
                if (t == 4) keys(mc, mv.up(), mv.sprint(), mv.sneak());
                if (mv.jump() && t == 10 && mc.player != null) mc.player.jumpFromGround();
                for (int i = 0; i < mv.shots().length; i++) if (t == mv.shots()[i]) shot("fp_" + tag + "_body_" + mv.name() + "_" + (char) ('a' + i));
                if (t >= 30) {
                    keys(mc, false, false, false);
                    lookDown = -1;
                    return true;
                }
                return false;
            });
        }
        // charging the phone from the power bank (first person, then from the front)
        for (int view = 0; view < 2; view++) {
            final boolean third = view == 1;
            STEPS.add(t -> {
                if (t == 0) {
                    lookAt = origin.add(0, 1.6, 30);
                    mc.options.setCameraType(third ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT : net.minecraft.client.CameraType.FIRST_PERSON);
                    server(sp -> {
                        pin(sp);
                        sp.getInventory().clearContent();
                        var ph = com.hollowhours.item.PhoneItem.fresh(sp.getRandom());
                        com.hollowhours.item.PhoneItem.setBattery(ph, 30);
                        sp.getInventory().setItem(1, ph);
                        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.CHARGER.get()));
                    });
                }
                if (t == 6) mc.options.keyUse.setDown(true);
                if (t == 11 || t == 26 || t == 40) shot((third ? "tp_" : "fp_") + tag + "_powerbank_t" + (t - 6));
                if (t == 42) {
                    mc.options.keyUse.setDown(false);
                    server(sp -> {
                        var ph = sp.getInventory().getItem(1);
                        HollowHours.LOG.info("[client smoke] power bank: phone battery {} (30 before), uses left {}", com.hollowhours.item.PhoneItem.battery(ph),
                                sp.getMainHandItem().getMaxDamage() - sp.getMainHandItem().getDamageValue());
                        if (com.hollowhours.item.PhoneItem.battery(ph) < 60) HollowHours.LOG.error("[client smoke] the power bank did not charge the phone (com.hollowhours)");
                    });
                }
                if (t == 45) server(sp -> {
                    int left = sp.getMainHandItem().getMaxDamage() - sp.getMainHandItem().getDamageValue();
                    HollowHours.LOG.info("[client smoke] power bank after unplugging: {} charges left", left);
                    if (left != 2) HollowHours.LOG.error("[client smoke] unplugging did not use one power bank charge (com.hollowhours)");
                });
                if (t >= 46) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    server(sp -> sp.getInventory().clearContent());
                    return true;
                }
                return false;
            });
        }
        // the mod's own things in 3D, in both hands
        if (!scared) STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 1.6, 30);
                server(sp -> {
                    pin(sp);
                    sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.GHOST_GUIDE.get()));
                    sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, com.hollowhours.item.PhoneItem.fresh(sp.getRandom()));
                });
            }
            if (t == 16) shot("fp_items_3d");
            if (t == 17) server(sp -> {
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.CANTEEN.get()));
                sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.BOARD_WOOD.get()));
            });
            if (t == 30) shot("fp_items_3d_b");
            if (t == 31) server(sp -> {
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            });
            return t >= 33;
        });
        // holding a sword (the arm holds it), swinging it
        STEPS.add(t -> {
            if (t == 0) {
                lookAt = origin.add(0, 1.6, 30);
                server(sp -> {
                    pin(sp);
                    sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                    sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE));
                });
            }
            if (t == 16) shot("fp_" + tag + "_hold");
            if (t == 18 && mc.player != null) mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (t == 19 || t == 21) shot("fp_" + tag + "_swing_" + (t == 19 ? "a" : "b"));
            if (t == 23) server(sp -> {
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            });
            return t >= 26;
        });
        // scared and standing with empty hands for a while: hugging yourself
        if (scared) STEPS.add(t -> {
            if (t == 0) {
                FirstPersonAnim.forceHug = true;
                server(ClientSmoke::pin);
            }
            if (t == 30 || t == 36) shot("fp_scared_hug_" + (t == 30 ? "a" : "b"));
            if (t == 38) FirstPersonAnim.forceHug = false;
            return t >= 40;
        });
        // eating bread, drinking from the canteen, scooping river water - first person, then the same from the front
        for (int view = 0; view < 2; view++) {
            final boolean third = view == 1;
            STEPS.add(t -> {
                if (t == 0) {
                    mc.options.setCameraType(third ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT : net.minecraft.client.CameraType.FIRST_PERSON);
                    server(sp -> {
                        pin(sp);
                        sp.getFoodData().setFoodLevel(10);
                        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 4));
                    });
                }
                if (t == 6) mc.options.keyUse.setDown(true);
                if (t == 18) shot((third ? "tp_" : "fp_") + tag + "_eat");
                if (t == 20) mc.options.keyUse.setDown(false);
                if (t == 24) server(sp -> sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(com.hollowhours.registry.HHItems.CANTEEN.get())));
                if (t == 30) mc.options.keyUse.setDown(true);
                if (t == 35) shot((third ? "tp_" : "fp_") + tag + "_drink_cap");
                if (t == 44) shot((third ? "tp_" : "fp_") + tag + "_drink");
                if (t == 46) mc.options.keyUse.setDown(false);
                if (t == 50) server(sp -> sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY));
                if (!third && t == 54) FirstPersonAnim.startSip();
                if (!third && (t == 58 || t == 66)) shot("fp_" + tag + "_sip_" + (t == 58 ? "a" : "b"));
                if (third && t == 54 && mc.player != null) mc.player.jumpFromGround();
                if (third && t == 58) shot("tp_" + tag + "_jump");
                if (third && t == 62) FirstPersonAnim.startSip();
                if (third && t == 66) shot("tp_" + tag + "_scoop");
                if (t >= 72) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    Fear.force = -1;
                    return true;
                }
                return false;
            });
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
                            com.hollowhours.registry.HHItems.FLASHLIGHT.get(), net.minecraft.world.item.Items.CRAFTING_TABLE, net.minecraft.world.item.Items.BREAD,
                            com.hollowhours.registry.HHItems.PHONE.get(), com.hollowhours.registry.HHItems.GHOST_GUIDE.get(), com.hollowhours.registry.HHItems.CANTEEN.get(),
                            com.hollowhours.registry.HHItems.CHARGER.get()};
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

    // ------------------------------------------------------------------ fighting back (2.11)
    private static net.minecraft.world.item.ItemStack modItem(String id) {
        var it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(HollowHours.MODID, id));
        var s = new net.minecraft.world.item.ItemStack(it == null ? net.minecraft.world.item.Items.BARRIER : it);
        if (s.getItem() instanceof com.hollowhours.combat.GunItem g) s.getOrCreateTag().putInt("ammo", g.spec.mag());
        return s;
    }

    private static void hold(ServerPlayer sp, String id) {
        sp.getInventory().selected = 0;
        sp.getInventory().setItem(0, modItem(id));
        sp.containerMenu.broadcastChanges();
    }

    @javax.annotation.Nullable
    private static GhostEntity clientGhost(Minecraft mc) {
        GhostEntity best = null;
        for (var en : mc.level.entitiesForRendering())
            if (en instanceof GhostEntity g && (best == null || g.distanceTo(mc.player) < best.distanceTo(mc.player))) best = g;
        return best;
    }

    private static void combat(Minecraft mc) {
        // every weapon in the hand: first person, shooting, aiming / guarding, third person
        String[] weapons = {"pistol", "rifle", "shotgun", "sniper", "grenade", "combat_knife", "katana", "battle_axe", "stun_baton", "spear"};
        for (String id : weapons) {
            boolean gun = id.equals("pistol") || id.equals("rifle") || id.equals("shotgun") || id.equals("sniper");
            boolean melee = !gun && !id.equals("grenade");
            STEPS.add(t -> {
                if (t == 0) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    lookAt = origin.add(0, 1.6, 14);
                    server(sp -> {
                        clearAll(sp);
                        pin(sp);
                        hold(sp, id);
                    });
                }
                if (t == 10) shot("hold_" + id + "_fp");
                if (gun) {
                    if (t == 11) server(sp -> {
                        var s = sp.getMainHandItem();
                        if (s.getItem() instanceof com.hollowhours.combat.GunItem g) g.fire(sp, s);
                        else HollowHours.LOG.error("[client smoke] {} is not in the hand (com.hollowhours)", id);
                    });
                    if (t == 13) shot("hold_" + id + "_fire");
                    if (t == 16) mc.options.keyShift.setDown(true);
                    if (t == 26) shot("hold_" + id + "_aim");
                    if (t == 27) mc.options.keyShift.setDown(false);
                }
                if (melee) {
                    if (t == 11 && mc.gameMode != null) {
                        mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                        mc.options.keyUse.setDown(true);
                    }
                    if (t == 20) {
                        shot("hold_" + id + "_guard");
                        if (!mc.player.isUsingItem()) HollowHours.LOG.error("[client smoke] {} does not guard on right-click (com.hollowhours)", id);
                    }
                    if (t == 21) mc.options.keyUse.setDown(false);
                }
                if (t == 28) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                if (t == 38) shot("hold_" + id + "_front");
                if (id.equals("grenade")) {
                    if (t == 40) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    if (t == 42) server(sp -> sp.getMainHandItem().use(sp.level(), sp, net.minecraft.world.InteractionHand.MAIN_HAND));
                    if (t == 60) shot("grenade_flying");
                    if (t == 93 || t == 96 || t == 104) shot("grenade_boom_t" + (t - 92));
                    return t >= 110;
                }
                return t >= 40;
            });
        }
        // the three armour sets
        String[] sets = {"tactical", "heavy", "hunter"};
        for (String set : sets) {
            STEPS.add(t -> {
                if (t == 0) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    lookAt = origin.add(0, 1.6, 14);
                    server(sp -> {
                        pin(sp);
                        sp.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
                        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, modItem(set + "_helmet"));
                        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, modItem(set + "_chestplate"));
                        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, modItem(set + "_leggings"));
                        sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, modItem(set + "_boots"));
                    });
                }
                if (t == 12) shot("armor_" + set);
                if (t == 13) server(sp -> {
                    float cut = com.hollowhours.combat.MilitaryArmor.ghostCut(sp), str = com.hollowhours.combat.MilitaryArmor.struggle(sp);
                    HollowHours.LOG.info("[client smoke] armour {}: armor {} toughness {} ghost cut {} struggle {}", set, sp.getArmorValue(),
                            sp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS), cut, str);
                    if (sp.getArmorValue() < 15 || cut <= 0 || str <= 0) HollowHours.LOG.error("[client smoke] armour set {} gives nothing (com.hollowhours)", set);
                });
                if (t == 14) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                if (t == 16) {
                    mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                }
                if (t == 26) shot("armor_" + set + "_inventory");
                if (t == 27) mc.setScreen(null);
                return t >= 29;
            });
        }
        // shooting a ghost until it drops (a showcase ghost: it stands still)
        STEPS.add(t -> {
            if (t == 0) {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                lookAt = origin.add(0, GhostKind.WAIL.height * 0.6, 9);
                server(sp -> {
                    clearAll(sp);
                    pin(sp);
                    for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET})
                        sp.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    hold(sp, "rifle");
                    showGhost(sp, GhostKind.WAIL, origin.add(0, 0, 9), GhostAnim.IDLE);
                });
            }
            if (t >= 8 && t <= 70 && t % 2 == 0) server(sp -> {
                var s = sp.getMainHandItem();
                var gs = sp.serverLevel().getEntitiesOfClass(GhostEntity.class, sp.getBoundingBox().inflate(20));
                if (gs.isEmpty() || !(s.getItem() instanceof com.hollowhours.combat.GunItem g) || com.hollowhours.combat.GhostCombat.knockedOut(gs.get(0).brain())) return;
                float before = com.hollowhours.combat.GhostCombat.poise(gs.get(0).brain());
                g.fire(sp, s);
                if (com.hollowhours.combat.GhostCombat.poise(gs.get(0).brain()) < before || com.hollowhours.combat.GhostCombat.knockedOut(gs.get(0).brain())) gunHits++;
                gunShots++;
            });
            if (t == 15) shot("gun_hits_ghost");
            if (t == 74) server(sp -> {
                var gs = sp.serverLevel().getEntitiesOfClass(GhostEntity.class, sp.getBoundingBox().inflate(20));
                boolean ko = !gs.isEmpty() && com.hollowhours.combat.GhostCombat.knockedOut(gs.get(0).brain());
                HollowHours.LOG.info("[client smoke] rifle vs ghost: {} shots, {} hits, knocked out {}", gunShots, gunHits, ko);
                if (gunHits == 0) HollowHours.LOG.error("[client smoke] gun shots never hit the ghost (com.hollowhours)");
                else if (!ko) HollowHours.LOG.error("[client smoke] the ghost was shot {} times and never went down (com.hollowhours)", gunHits);
            });
            if (t == 76) lookAt = origin.add(0, 0.5, 9);
            if (t == 92) {
                GhostEntity cg = clientGhost(mc);
                HollowHours.LOG.info("[client smoke] knocked-out ghost on the client: lying {}", cg == null ? "none" : String.format("%.2f", cg.koLerp));
                if (cg == null || cg.koLerp < 0.9f) HollowHours.LOG.error("[client smoke] the knocked-out ghost is not lying down (com.hollowhours)");
                shot("ghost_knocked_out");
            }
            if (t == 93) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            if (t == 93) lookAt = origin.add(0, 1.6, -14);
            if (t == 104) shot("ghost_knocked_out_tp");
            if (t == 105) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            return t >= 106;
        });
        // grabbed: mash to break free, then knock the ghost out for real (a living one this time)
        final int[] strAt = {-1}, endAt = {-1};
        STEPS.add(t -> {
            if (t == 0) {
                strAt[0] = endAt[0] = -1;
                lookAt = origin.add(0, GhostKind.WAIL.height * 0.7, 2.6);
                server(sp -> {
                    clearAll(sp);
                    pin(sp);
                    sp.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
                    com.hollowhours.combat.Fitness.setStrength(sp, 0);
                    GhostEntity g = Director.spawnGhost(sp.serverLevel(), GhostKind.WAIL, origin.add(0, 0, 2.6));
                    if (g == null) return;
                    KillSequence.forceKill = 0;
                    if (!KillSequence.begin(g.brain(), sp)) HollowHours.LOG.error("[client smoke] the struggle grab did not start (com.hollowhours)");
                });
            }
            if (t == 4) lookAt = null;
            if (strAt[0] < 0 && CombatClient.struggling()) strAt[0] = t;
            if (strAt[0] >= 0 && endAt[0] < 0) {
                int d = t - strAt[0];
                if (d == 4) shot("struggle_start");
                if (d >= 10 && d % 2 == 0) net.minecraft.client.KeyMapping.click(mc.options.keyJump.getKey());
                if (d == 30) shot("struggle_mashing");
                if (!CombatClient.struggling()) {
                    endAt[0] = t;
                    HollowHours.LOG.info("[client smoke] struggle over after {} ticks, {} presses, result {}", d, CombatClient.mashes, CombatClient.result);
                    if (CombatClient.result != 1) HollowHours.LOG.error("[client smoke] mashing did not break free (com.hollowhours)");
                }
            }
            if (endAt[0] >= 0) {
                int d = t - endAt[0];
                if (d == 3) shot("struggle_won");
                if (d == 6) server(sp -> {
                    HollowHours.LOG.info("[client smoke] after breaking free: victim {} riding {} alive {} strength {}", KillSequence.isVictim(sp),
                            sp.getVehicle() != null, sp.isAlive(), com.hollowhours.combat.Fitness.strength(sp));
                    if (KillSequence.isVictim(sp) || sp.getVehicle() != null || !sp.isAlive()) HollowHours.LOG.error("[client smoke] still held after breaking free (com.hollowhours)");
                    hold(sp, "shotgun");
                });
                GhostEntity cg = clientGhost(mc);
                if (d >= 6 && d < 40 && cg != null) lookAt = cg.position().add(0, cg.getBbHeight() * 0.55, 0);
                if (d >= 10 && d <= 34 && d % 4 == 2) server(sp -> {
                    var s = sp.getMainHandItem();
                    var gs = sp.serverLevel().getEntitiesOfClass(GhostEntity.class, sp.getBoundingBox().inflate(20));
                    if (gs.isEmpty() || !(s.getItem() instanceof com.hollowhours.combat.GunItem g) || com.hollowhours.combat.GhostCombat.knockedOut(gs.get(0).brain())) return;
                    s.getOrCreateTag().putInt("ammo", 6);
                    g.fire(sp, s);
                });
                if (d == 12) shot("shotgun_vs_ghost");
                if (d == 40) server(sp -> {
                    var gs = sp.serverLevel().getEntitiesOfClass(GhostEntity.class, sp.getBoundingBox().inflate(20));
                    if (gs.isEmpty()) {
                        HollowHours.LOG.error("[client smoke] the ghost vanished after the struggle (com.hollowhours)");
                        return;
                    }
                    var b = gs.get(0).brain();
                    if (!com.hollowhours.combat.GhostCombat.knockedOut(b)) {
                        HollowHours.LOG.warn("[client smoke] shotgun did not knock the living ghost out (poise {}), forcing it", com.hollowhours.combat.GhostCombat.poise(b));
                        for (int i = 0; i < 30 && !com.hollowhours.combat.GhostCombat.knockedOut(b); i++) com.hollowhours.combat.GhostCombat.poiseHit(gs.get(0), sp, 10, false);
                    }
                    boolean grabbed = KillSequence.begin(b, sp);
                    HollowHours.LOG.info("[client smoke] living ghost knocked out {} mode {} grab refused {}", com.hollowhours.combat.GhostCombat.knockedOut(b), b.mode, !grabbed);
                    if (!com.hollowhours.combat.GhostCombat.knockedOut(b) || grabbed) HollowHours.LOG.error("[client smoke] a knocked-out ghost is not out (com.hollowhours)");
                });
                if (d == 44 && clientGhost(mc) != null) lookAt = clientGhost(mc).position().add(0, 0.4, 0);
                if (d == 60) shot("living_ghost_knocked_out");
                if (d == 61) server(sp -> {
                    clearAll(sp);
                    pin(sp);
                    com.hollowhours.combat.Fitness.setStrength(sp, 0);
                    sp.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
                });
                return d >= 64;
            }
            if (t > 500) {
                HollowHours.LOG.error("[client smoke] the struggle never showed up or never ended (com.hollowhours)");
                server(sp -> clearAll(sp));
                return true;
            }
            return false;
        });
    }

    private static int gunShots, gunHits;

    // ------------------------------------------------------------------ the main menu's panorama: a haunted clearing, the five standing round you
    static Vec3 coverAt = Vec3.ZERO;

    private static void coverScene(ServerPlayer sp, Vec3 c) {
        ServerLevel l = sp.serverLevel();
        java.util.Random r = new java.util.Random(13);
        net.minecraft.core.BlockPos o = net.minecraft.core.BlockPos.containing(c);
        java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> put = (b, st) -> l.setBlock(b, st, 2 | 16);
        var B = new Object() {
            net.minecraft.world.level.block.state.BlockState s(net.minecraft.world.level.block.Block b) {
                return b.defaultBlockState();
            }
        };
        // dead ground, nothing else standing
        for (int dx = -36; dx <= 36; dx++)
            for (int dz = -36; dz <= 36; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 36) continue;
                for (int y = 0; y < 14; y++) put.accept(o.offset(dx, y, dz), B.s(net.minecraft.world.level.block.Blocks.AIR));
                int k = r.nextInt(100);
                var g = k < 30 ? net.minecraft.world.level.block.Blocks.PODZOL : k < 52 ? net.minecraft.world.level.block.Blocks.COARSE_DIRT
                        : k < 58 ? net.minecraft.world.level.block.Blocks.MUD : net.minecraft.world.level.block.Blocks.GRASS_BLOCK;
                put.accept(o.offset(dx, -1, dz), B.s(g));
                if (d > 3 && r.nextInt(100) < 7) put.accept(o.offset(dx, 0, dz), B.s(r.nextBoolean() ? net.minecraft.world.level.block.Blocks.DEAD_BUSH : net.minecraft.world.level.block.Blocks.GRASS));
            }
        // dead trees all round
        double[] ghostAng = {0, 72, 144, 216, 288};
        // low mounds further out, so the land isn't flat to the horizon
        for (int dx = -36; dx <= 36; dx++)
            for (int dz = -36; dz <= 36; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 17 || d > 36) continue;
                double hh = (1.4 + Math.sin(dx * 0.31) * Math.cos(dz * 0.27) + 0.6 * Math.sin((dx + dz) * 0.13)) * Math.min(1, (d - 17) / 8);
                int hgt = (int) Math.round(hh);
                for (int y = 0; y < hgt; y++)
                    put.accept(o.offset(dx, y, dz), B.s(y == hgt - 1 ? (r.nextInt(3) == 0 ? net.minecraft.world.level.block.Blocks.COARSE_DIRT : net.minecraft.world.level.block.Blocks.PODZOL)
                            : net.minecraft.world.level.block.Blocks.DIRT));
            }
        for (int i = 0; i < 46; i++) {
            double a = r.nextDouble() * Math.PI * 2, rad = 9 + r.nextDouble() * 27;
            boolean clash = false;
            for (double ga : ghostAng) if (Math.abs(Math.toDegrees(Math.atan2(Math.sin(a - Math.toRadians(ga)), Math.cos(a - Math.toRadians(ga))))) < 16 && rad < 18) clash = true;
            if (clash) continue;
            net.minecraft.core.BlockPos b0 = o.offset((int) Math.round(Math.cos(a) * rad), 0, (int) Math.round(Math.sin(a) * rad));
            net.minecraft.core.BlockPos b = b0;
            while (!l.getBlockState(b).isAir() && b.getY() < b0.getY() + 4) b = b.above();
            int h = 6 + r.nextInt(6);
            for (int y = 0; y < h; y++) put.accept(b.above(y), B.s(net.minecraft.world.level.block.Blocks.DARK_OAK_LOG));
            for (int br = 0; br < 3; br++) {
                int sx = r.nextInt(3) - 1, sz = r.nextInt(3) - 1;
                if (sx == 0 && sz == 0) sx = 1;
                int y0 = h - 1 - r.nextInt(3), len = 2 + r.nextInt(3);
                for (int k = 1; k <= len; k++)
                    put.accept(b.offset(sx * k, y0 + k / 2, sz * k), net.minecraft.world.level.block.Blocks.DARK_OAK_LOG.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, sx != 0 ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z));
                if (r.nextInt(3) == 0) put.accept(b.offset(sx * (len + 1), y0 + len / 2, sz * (len + 1)), B.s(net.minecraft.world.level.block.Blocks.COBWEB));
            }
        }
        // the abandoned house behind one of them
        {
            double a = Math.toRadians(200);
            net.minecraft.core.BlockPos h = o.offset((int) Math.round(Math.cos(a) * 17), 0, (int) Math.round(Math.sin(a) * 17));
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++)
                    for (int y = 0; y <= 4; y++) {
                        boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                        net.minecraft.core.BlockPos b = h.offset(dx, y, dz);
                        if (y == 0) put.accept(b, B.s(net.minecraft.world.level.block.Blocks.COBBLESTONE));
                        else if (y == 4) put.accept(b, B.s(r.nextInt(6) == 0 ? net.minecraft.world.level.block.Blocks.AIR : net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS));
                        else if (wall) {
                            boolean window = y == 2 && (Math.abs(dx) == 1 || Math.abs(dz) == 1) && !(Math.abs(dx) == 3 && Math.abs(dz) == 3);
                            put.accept(b, B.s(window ? (r.nextBoolean() ? net.minecraft.world.level.block.Blocks.AIR : net.minecraft.world.level.block.Blocks.GLASS_PANE)
                                    : r.nextInt(9) == 0 ? net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE : net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS));
                        }
                    }
            put.accept(h.offset(0, 3, 0), net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++)
                    if (Math.abs(dx) == 4 || Math.abs(dz) == 4)
                        if (r.nextInt(3) > 0) put.accept(h.offset(dx, 5, dz), B.s(net.minecraft.world.level.block.Blocks.DARK_OAK_SLAB));
        }
        // the graves
        for (int i = 0; i < 7; i++) {
            double a = Math.toRadians(40 + (i - 3) * 9), rad = 13 + (i % 2) * 2.5;
            net.minecraft.core.BlockPos g = o.offset((int) Math.round(Math.cos(a) * rad), 0, (int) Math.round(Math.sin(a) * rad));
            put.accept(g, B.s(net.minecraft.world.level.block.Blocks.COBBLESTONE_WALL));
            put.accept(g.above(), B.s(i % 3 == 0 ? net.minecraft.world.level.block.Blocks.SKELETON_SKULL : net.minecraft.world.level.block.Blocks.ANDESITE_WALL));
            put.accept(g.offset(1, 0, 0), net.minecraft.world.level.block.Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true)
                    .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 1 + i % 3));
        }
        // the mound you stand on (the menu's view looks a little down, so the faces stay in sight)
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int hgt = d < 1.6 ? 2 : d < 3.2 ? 1 : 0;
                for (int y = 0; y < hgt; y++)
                    put.accept(o.offset(dx, y, dz), B.s(r.nextInt(3) == 0 ? net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE
                            : r.nextBoolean() ? net.minecraft.world.level.block.Blocks.COBBLESTONE : net.minecraft.world.level.block.Blocks.STONE));
            }
        put.accept(o.offset(1, 2, 1), net.minecraft.world.level.block.Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true)
                .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3));
        put.accept(o.offset(-1, 2, -1), B.s(net.minecraft.world.level.block.Blocks.SKELETON_SKULL));
        // blood on the ground round the mound, bones
        for (int i = 0; i < 70; i++) {
            double a = r.nextDouble() * Math.PI * 2, rad = 3.3 + r.nextDouble() * 6;
            net.minecraft.core.BlockPos b = o.offset((int) Math.round(Math.cos(a) * rad), 0, (int) Math.round(Math.sin(a) * rad));
            if (l.getBlockState(b).isAir() && !l.getBlockState(b.below()).isAir())
                put.accept(b, i % 11 == 0 ? B.s(net.minecraft.world.level.block.Blocks.SKELETON_SKULL) : B.s(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE));
        }
        // the five, close, each by a cold lantern, all looking at you
        GhostAnim[] anims = {GhostAnim.GRAB, GhostAnim.CHASE, GhostAnim.LAUGH, GhostAnim.STALK, GhostAnim.CRAWL_CHASE};
        int[] kinds = {1, 3, 4, 5, 2};
        double[] rads = {4.2, 4.6, 6.4, 4.8, 2.9};
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(ghostAng[i]);
            Vec3 at = c.add(Math.cos(a) * rads[i], kinds[i] == 2 ? 1 : 0, Math.sin(a) * rads[i]);
            GhostEntity g = showGhost(sp, GhostKind.byN(kinds[i]), at, anims[i]);
            if (g != null) {
                float yaw = (float) Math.toDegrees(Math.atan2(-(c.x - at.x), c.z - at.z));
                g.moveTo(at.x, at.y, at.z, yaw, 0);
                g.setYHeadRot(yaw);
                g.setYBodyRot(yaw);
            }
            Vec3 side = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(1.9);
            net.minecraft.core.BlockPos post = net.minecraft.core.BlockPos.containing(at.add(side).add(Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6));
            put.accept(post, B.s(net.minecraft.world.level.block.Blocks.DARK_OAK_FENCE));
            put.accept(post.above(), B.s(net.minecraft.world.level.block.Blocks.SOUL_LANTERN));
        }
        // and further off, between the trees, the others watching
        double[] st = {36, 180, 252};
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(st[i]);
            Vec3 at = c.add(Math.cos(a) * 12, 0, Math.sin(a) * 12);
            StalkerEntity s = HHEntities.stalker(StalkerKind.values()[i]).create(l);
            if (s == null) continue;
            float yaw = (float) Math.toDegrees(Math.atan2(-(c.x - at.x), c.z - at.z));
            s.owned = true;
            s.moveTo(at.x, at.y, at.z, yaw, 0);
            s.setYHeadRot(yaw);
            s.setYBodyRot(yaw);
            s.setStalkPose(i % 3);
            l.addFreshEntity(s);
        }
    }

    private static void panorama(Minecraft mc, String name) {
        try {
            mc.grabPanoramixScreenshot(mc.gameDirectory, 1024, 1024);
        } catch (Throwable e) {
            HollowHours.LOG.error("[client smoke] panorama {} failed (com.hollowhours)", name, e);
        }
    }

    /** (a few ticks after panorama(): the six files are written on another thread) */
    private static void keepPanorama(Minecraft mc, String name) {
        try {
            java.nio.file.Path dir = mc.gameDirectory.toPath().resolve("screenshots");
            for (int i = 0; i < 6; i++) {
                java.nio.file.Path f = dir.resolve("panorama_" + i + ".png");
                if (java.nio.file.Files.exists(f))
                    java.nio.file.Files.move(f, dir.resolve("cover_" + name + "_panorama_" + i + ".png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            HollowHours.LOG.info("[client smoke] menu panorama '{}' captured", name);
        } catch (Throwable e) {
            HollowHours.LOG.error("[client smoke] panorama {} failed (com.hollowhours)", name, e);
        }
    }

    private static void cover(Minecraft mc) {
        Vec3[] look = {Vec3.ZERO};
        double[] gamma = {1};
        STEPS.add(t -> {
            if (t == 0) {
                coverAt = origin.add(-240, 0, 160);
                look[0] = coverAt.add(4.2, 3.0, 0);
                gamma[0] = mc.options.gamma().get();
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                server(sp -> {
                    clearAll(sp);
                    sp.teleportTo(coverAt.x, coverAt.y + 3, coverAt.z);
                    sp.serverLevel().setDayTime(15000);
                    sp.removeEffect(MobEffects.NIGHT_VISION);
                    sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 5, 4, false, false));
                    com.hollowhours.ghost.Atmosphere.forceBlood = 1;
                });
            }
            if (t == 60) server(sp -> {
                coverScene(sp, coverAt);
                sp.teleportTo(coverAt.x + 0.5, coverAt.y + 2, coverAt.z + 0.5);
            });
            if (t == 62) lookAt = look[0];
            if (t == 64) mc.options.gamma().set(1.0);
            if (t == 100) Atmos.snap();
            // 1: the blood moon in the dark, only the lanterns and their eyes
            if (t == 220) {
                face(mc, look[0]);
                shot("covershot_blood_dark");
                panorama(mc, "blood_dark");
            }
            if (t == 240) keepPanorama(mc, "blood_dark");
            if (t == 250) {
                mc.options.gamma().set(gamma[0]);
                server(sp -> {
                    com.hollowhours.ghost.Atmosphere.forceBlood = 0;
                    clearAll(sp);
                    pin(sp);
                });
            }
            if (t == 280) Atmos.snap();
            return t >= 282;
        });
    }

    // ------------------------------------------------------------------ the promo shoot (portrait, frames every 2 ticks)
    static int frameNo;

    /** a frame of shot `name` (frames of one shot are numbered in order) */
    private static void frame(String name) {
        Minecraft mc = Minecraft.getInstance();
        String file = String.format("promo_%s_%04d.png", name, ++frameNo);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), c -> {});
    }

    private static void turn(Minecraft mc, float yaw, float pitch) {
        lookAt = null;
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setYHeadRot(yaw);
        mc.player.yBodyRot = yaw;
        mc.player.setXRot(pitch);
        mc.player.xRotO = pitch;
    }

    @javax.annotation.Nullable
    private static GhostEntity sceneGhost(ServerPlayer sp, int kind) {
        for (GhostEntity g : sp.serverLevel().getEntitiesOfClass(GhostEntity.class, sp.getBoundingBox().inflate(40)))
            if (g.kind.n == kind) return g;
        return null;
    }

    private static Vec3 around(Vec3 c, double deg, double r, double up) {
        double a = Math.toRadians(deg);
        return c.add(Math.cos(a) * r, up, Math.sin(a) * r);
    }

    private static void gun(ServerPlayer sp, String id) {
        sp.getInventory().selected = 0;
        sp.getInventory().setItem(0, modItem(id));
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
    }

    private static void fireAt(ServerPlayer sp) {
        var s = sp.getMainHandItem();
        if (s.getItem() instanceof com.hollowhours.combat.GunItem g) {
            s.getOrCreateTag().putInt("ammo", g.spec.mag());
            g.fire(sp, s);
        }
    }

    private static void promo(Minecraft mc) {
        coverAt = origin.add(-240, 0, 160);
        Vec3 eye = coverAt.add(0.5, 2 + 1.62, 0.5);
        // setup: the haunted clearing under a blood moon, brightness up, no HUD
        STEPS.add(t -> {
            if (t == 0) {
                mc.options.gamma().set(1.0);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                server(sp -> {
                    ServerLevel l = sp.serverLevel();
                    l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, sp.server);
                    l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, sp.server);
                    l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, sp.server);
                    l.setWeatherParameters(6000, 0, false, false);
                    sp.setGameMode(GameType.SURVIVAL);
                    sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 4, false, false));
                    sp.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 60 * 30, 0, false, false));
                    l.setDayTime(15000);
                    com.hollowhours.ghost.Atmosphere.forceBlood = 1;
                    clearAll(sp);
                    sp.getInventory().clearContent();
                    sp.teleportTo(coverAt.x, coverAt.y + 3, coverAt.z);
                });
            }
            if (t == 60) server(sp -> {
                coverScene(sp, coverAt);
                sp.teleportTo(coverAt.x + 0.5, coverAt.y + 2, coverAt.z + 0.5);
            });
            if (t == 100) Atmos.snap();
            return t >= 240;
        });
        // 1. the haunted main menu
        STEPS.add(t -> {
            if (t == 0) mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            if (t >= 20 && t < 80 && t % 2 == 0) frame("01_menu");
            if (t == 80) mc.setScreen(null);
            return t >= 84;
        });
        // 2. all five round you: one slow turn
        STEPS.add(t -> {
            turn(mc, -90 + t * 2.4f, 4);
            if (t % 2 == 0 && t < 150) frame("02_five");
            return t >= 150;
        });
        // 3. one charges at you
        STEPS.add(t -> {
            Vec3 from = around(coverAt.add(0.5, 0, 0.5), 36, 11, 0), to = around(coverAt.add(0.5, 0, 0.5), 36, 1.6, 0);
            if (t == 0) server(sp -> {
                GhostEntity g = showGhost(sp, GhostKind.byN(3), from, GhostAnim.CHASE);
                if (g != null) g.addTag("promo_charge");
            });
            Vec3 now = from.add(to.subtract(from).scale(Math.min(1, t / 30.0)));
            if (t >= 2) server(sp -> {
                for (GhostEntity g : sp.serverLevel().getEntitiesOfClass(GhostEntity.class, sp.getBoundingBox().inflate(30)))
                    if (g.getTags().contains("promo_charge")) {
                        float yaw = (float) Math.toDegrees(Math.atan2(-(eye.x - now.x), eye.z - now.z));
                        g.moveTo(now.x, now.y + (Math.min(1, t / 30.0) > 0.5 ? 1 : 0), now.z, yaw, 0);
                        g.setYHeadRot(yaw);
                        g.setYBodyRot(yaw);
                    }
            });
            lookAt = now.add(0, 2.2, 0);
            if (t % 2 == 0 && t <= 34) frame("03_charge");
            if (t == 36) server(sp -> {
                for (GhostEntity g : sp.serverLevel().getEntitiesOfClass(GhostEntity.class, sp.getBoundingBox().inflate(30)))
                    if (g.getTags().contains("promo_charge")) g.discard();
            });
            return t >= 38;
        });
        // 4. the rifle: shots into the miner until he drops
        STEPS.add(t -> {
            if (t == 0) server(sp -> gun(sp, "rifle"));
            lookAt = around(coverAt.add(0.5, 0, 0.5), 0, 4.2, 2.8);
            if (t >= 10 && t <= 56 && t % 3 == 1) server(sp -> {
                GhostEntity g = sceneGhost(sp, 1);
                if (g != null && !com.hollowhours.combat.GhostCombat.knockedOut(g.brain())) fireAt(sp);
            });
            if (t >= 8 && t % 2 == 0 && t <= 80) frame("04_rifle");
            return t >= 82;
        });
        // 5. the shotgun at the stag
        STEPS.add(t -> {
            if (t == 0) server(sp -> gun(sp, "shotgun"));
            lookAt = around(coverAt.add(0.5, 0, 0.5), 216, 4.8, 2.6);
            if (t == 10 || t == 22) server(ClientSmoke::fireAt);
            if (t >= 6 && t % 2 == 0 && t <= 34) frame("05_shotgun");
            return t >= 36;
        });
        // 6. the sniper, scoped, at the tall one
        STEPS.add(t -> {
            if (t == 0) server(sp -> gun(sp, "sniper"));
            lookAt = around(coverAt.add(0.5, 0, 0.5), 144, 6.4, 5.2);
            if (t == 4) mc.options.keyShift.setDown(true);
            if (t == 18) server(ClientSmoke::fireAt);
            if (t >= 8 && t % 2 == 0 && t <= 30) frame("06_sniper");
            if (t == 32) mc.options.keyShift.setDown(false);
            return t >= 34;
        });
        // 7. a grenade at the watchers in the trees
        STEPS.add(t -> {
            if (t == 0) server(sp -> gun(sp, "grenade"));
            lookAt = around(coverAt.add(0.5, 0, 0.5), 252, 9, 3.5);
            if (t == 6) server(sp -> sp.getMainHandItem().use(sp.level(), sp, net.minecraft.world.InteractionHand.MAIN_HAND));
            if (t >= 4 && t % 2 == 0 && t <= 76) frame("07_grenade");
            return t >= 78;
        });
        // 8. the three armour sets (third person, rifle in hand)
        String[] sets = {"tactical", "heavy", "hunter"};
        for (String set : sets) STEPS.add(t -> {
            if (t == 0) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                server(sp -> {
                    gun(sp, "rifle");
                    sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, modItem(set + "_helmet"));
                    sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, modItem(set + "_chestplate"));
                    sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, modItem(set + "_leggings"));
                    sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, modItem(set + "_boots"));
                });
            }
            turn(mc, 200 + t * 1.5f, 8);
            if (t >= 6 && t % 2 == 0 && t <= 22) frame("08_armor_" + set);
            if (t == 24 && set.equals("hunter")) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            return t >= 24;
        });
        // 9. grabbed: the struggle bar, mashing, free (a living ghost, right in front)
        final int[] strAt = {-1}, endAt = {-1};
        STEPS.add(t -> {
            if (t == 0) {
                promoHud = 1;
                strAt[0] = endAt[0] = -1;
                server(sp -> {
                    for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) sp.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    Vec3 at = around(coverAt.add(0.5, 2, 0.5), 108, 2.4, 0);
                    GhostEntity g = Director.spawnGhost(sp.serverLevel(), GhostKind.WAIL, at);
                    if (g == null) return;
                    KillSequence.forceKill = 0;
                    KillSequence.begin(g.brain(), sp);
                });
            }
            if (t == 4) lookAt = null;
            if (strAt[0] < 0 && CombatClient.struggling()) strAt[0] = t;
            if (t % 2 == 0 && t <= 160) frame("09_struggle");
            if (strAt[0] >= 0 && endAt[0] < 0) {
                if (t - strAt[0] >= 14 && t % 2 == 1) net.minecraft.client.KeyMapping.click(mc.options.keyJump.getKey());
                if (!CombatClient.struggling()) endAt[0] = t;
            }
            if (endAt[0] >= 0 && t >= endAt[0] + 24 || t > 200) {
                promoHud = 0;
                server(sp -> {
                    for (GhostEntity g : Director.allGhosts(sp.server)) if (!g.showcase) g.discard();
                    sp.teleportTo(coverAt.x + 0.5, coverAt.y + 2, coverAt.z + 0.5);
                });
                return true;
            }
            return false;
        });
        // 10. the map to the mountain, in hand
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                net.minecraft.core.BlockPos site = net.minecraft.core.BlockPos.containing(coverAt).offset(170, 0, 30);
                com.hollowhours.endgame.Endgame.setSite(sp.server, site);
                com.hollowhours.endgame.Endgame.data(sp.server).stage = com.hollowhours.endgame.Endgame.MAP;
                sp.getInventory().setItem(0, com.hollowhours.endgame.Endgame.makeMap(sp.serverLevel(), site));
                sp.getInventory().selected = 0;
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
            });
            turn(mc, 20, 30);
            if (t >= 16 && t % 2 == 0 && t <= 30) frame("10_map");
            return t >= 32;
        });
        // 11. the mountain: built, then a slow push in from the air
        final int[] built = {-1};
        STEPS.add(t -> {
            net.minecraft.core.BlockPos site = net.minecraft.core.BlockPos.containing(coverAt).offset(170, 0, 30);
            if (t == 0) server(sp -> {
                Vec3 dir = com.hollowhours.endgame.Arena.mouthDir(sp.server);
                egDir = dir;
                Vec3 v = Vec3.atBottomCenterOf(site).add(dir.scale(110));
                sp.getAbilities().flying = true;
                sp.getAbilities().mayfly = true;
                sp.onUpdateAbilities();
                sp.teleportTo(v.x, coverAt.y + 30, v.z);
            });
            if (built[0] < 0 && t % 10 == 5) server(sp -> {
                if (com.hollowhours.endgame.Endgame.data(sp.server).built == 2 && built[0] < 0) {
                    built[0] = 0;
                    egCenter = Vec3.atBottomCenterOf(com.hollowhours.endgame.Endgame.center(sp.server));
                }
            });
            if (built[0] >= 0) {
                int d = built[0]++;
                Vec3 target = Vec3.atBottomCenterOf(site).add(0, 14, 0);
                if (d >= 20) {
                    Vec3 cam = Vec3.atBottomCenterOf(site).add(egDir.scale(110 - (d - 20) * 0.9)).add(0, 30 - (d - 20) * 0.35, 0);
                    server(sp -> sp.teleportTo(cam.x, cam.y - 1.62, cam.z));
                    lookAt = target;
                    if (d % 2 == 0 && d <= 100) frame("11_mountain");
                }
                if (d == 102) server(sp -> {
                    Vec3 at = egCenter.add(egDir.scale(com.hollowhours.endgame.Arena.TRENCH + 7));
                    int y = sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
                    sp.getAbilities().flying = false;
                    sp.onUpdateAbilities();
                    sp.teleportTo(at.x, y + 0.1, at.z);
                });
                if (d >= 106 && d <= 130) {
                    Vec3 m = egCenter.add(egDir.scale(com.hollowhours.endgame.Arena.MOUTH));
                    lookAt = new Vec3(m.x, mc.player.getEyeY() - 2.0, m.z);
                    if (d % 2 == 0) frame("12_mouth");
                }
                return d >= 132;
            }
            return t > 900;
        });
        // 12. under the mountain: it rises; the attacks; it is beaten; the rift; the end
        final int[] rise = {-1};
        STEPS.add(t -> {
            if (t == 0) {
                promoHud = 1;
                server(sp -> {
                    Vec3 at = egCenter.add(egDir.scale(9));
                    sp.teleportTo(at.x, egCenter.y, at.z);
                    sp.serverLevel().setDayTime(18000);
                });
            }
            lookAt = egCenter.add(0, 4.2, 0);
            if (rise[0] < 0 && clientBoss(mc) != null) {
                rise[0] = t;
                server(sp -> {
                    var b = serverBoss(sp);
                    if (b != null) b.ciHold(100000);
                });
            }
            if (rise[0] >= 0) {
                int d = t - rise[0];
                if (d % 2 == 0 && d <= 96) frame("13_rise");
                if (d == 98) server(sp -> {
                    var b = serverBoss(sp);
                    if (b != null) egYaw = b.getYRot();
                });
                return d >= 100;
            }
            return t > 400;
        });
        String[][] atk = {{"slam_near", "5.4", "14_slam", "44"}, {"sweep", "4.6", "15_sweep", "44"}, {"scream", "7", "16_scream", "40"}, {"grab", "4.6", "17_grab", "0"}};
        for (String[] a : atk) {
            final int[] freed = {-1};
            STEPS.add(t -> {
                if (t == 0) {
                    freed[0] = -1;
                    server(sp -> standBefore(sp, Double.parseDouble(a[1]), 0));
                }
                lookAt = egCenter.add(0, a[0].equals("scream") ? 5 : 3.2, 0);
                if (t == 6) server(sp -> {
                    var b = serverBoss(sp);
                    if (b != null) b.ciAttack(a[0], sp);
                });
                if (t >= 6 && t % 2 == 0 && t <= 120) frame(a[2]);
                if (a[0].equals("grab")) {
                    if (t > 30 && CombatClient.struggling() && t % 2 == 1 && t > 44) net.minecraft.client.KeyMapping.click(mc.options.keyJump.getKey());
                    if (t > 40 && !CombatClient.struggling() && freed[0] < 0) freed[0] = t;
                    return freed[0] >= 0 && t >= freed[0] + 20 || t > 200;
                }
                return t >= Integer.parseInt(a[3]) + 8;
            });
        }
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                standBefore(sp, 9, 0);
                sp.removeEffect(MobEffects.DARKNESS);
                var b = serverBoss(sp);
                if (b != null) b.hurt(b.damageSources().genericKill(), 100000);
            });
            lookAt = egCenter.add(0, 4.5, 0);
            if (t % 3 == 0 && t <= 150) frame("18_defeat");
            boolean rift = false;
            for (var en : mc.level.entitiesForRendering()) if (en instanceof com.hollowhours.endgame.RiftEntity) rift = true;
            if (rift) {
                lookAt = egCenter.add(0, 2.2, 0);
                if (t % 2 == 0) frame("19_rift");
            }
            return rift && t >= 230 || t > 500;
        });
        STEPS.add(t -> {
            if (t == 0) server(sp -> sp.teleportTo(egCenter.x, egCenter.y + 0.5, egCenter.z));
            if (t % 2 == 0 && t <= 50) frame("20_flash");
            if (mc.screen instanceof CreditsScreen cs) {
                if (cs.ticks == 30) cs.scroll = 40;
                if (cs.ticks >= 30 && cs.ticks <= 60 && cs.ticks % 2 == 0) frame("21_credits");
            }
            if (t > 120) {
                if (mc.screen != null) mc.screen.onClose();
                return true;
            }
            return false;
        });
        STEPS.add(t -> {
            promoHud = 0;
            if (t == 2) turn(mc, mc.player.getYRot(), -5);
            if (t >= 30 && t % 2 == 0 && t <= 50) frame("22_morning");
            if (t == 52) mc.options.gamma().set(0.5);
            return t >= 54;
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
                    KillSequence.noStruggleOnce = !(k.n == 1 && kill == 0); // one kill shows the struggle being lost
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
