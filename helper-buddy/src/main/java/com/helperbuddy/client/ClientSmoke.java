package com.helperbuddy.client;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.brain.Bag;
import com.helperbuddy.client.screen.HelperScreen;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.gen.Gen;
import com.helperbuddy.net.HBNet;
import com.helperbuddy.registry.HBRegistry;
import com.helperbuddy.talk.Talk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * CI only (-Dhelperbuddy.clientsmoke=true, quick play into the world "smoke" under a virtual display): shows the
 * helper in every skin and pose, opens every screen, lets it build and talk, and takes screenshots of each.
 */
@Mod.EventBusSubscriber(modid = HelperBuddy.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientSmoke {
    private static final boolean ON = Boolean.getBoolean("helperbuddy.clientsmoke");

    private ClientSmoke() {}

    private interface Step {
        boolean tick(int t);
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int stepIndex = -1, stepTick, waitTicks, shotNo, inWorldTicks;
    private static boolean built, done;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 lookAt;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!ON || done || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus = false;
            if (mc.player == null || mc.level == null) {
                waitTicks++;
                if (waitTicks == 900) mc.createWorldOpenFlows().loadLevel(mc.screen, "smoke");
                if (waitTicks > 2400) fail("never got into the world");
                return;
            }
            inWorldTicks++;
            if (inWorldTicks < 100) return;
            if (!built) build(mc);
            if (lookAt != null && mc.player.isAlive()) face(mc, lookAt);
            if (stepIndex >= STEPS.size()) return;
            if (STEPS.get(stepIndex).tick(stepTick++)) {
                stepIndex++;
                stepTick = 0;
                if (stepIndex >= STEPS.size()) {
                    HelperBuddy.LOG.info("[client smoke] took {} screenshots", shotNo);
                    HelperBuddy.LOG.info("CLIENT SMOKE OK");
                    STEPS.add(t -> {
                        if (t == 40) {
                            done = true;
                            mc.stop();
                        }
                        return false;
                    });
                }
            }
        } catch (Throwable ex) {
            HelperBuddy.LOG.error("[client smoke] step {} failed", stepIndex, ex);
            stepIndex++;
            stepTick = 0;
        }
    }

    private static void fail(String why) {
        HelperBuddy.LOG.error("[client smoke] FAILED: {} (com.helperbuddy)", why);
        done = true;
        Minecraft.getInstance().stop();
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, String.format("%03d_%s.png", ++shotNo, name), mc.getMainRenderTarget(), c -> {});
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
                HelperBuddy.LOG.error("[client smoke] server action failed", ex);
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

    static HelperEntity helper;

    private static void clearHelpers(ServerPlayer sp) {
        for (Entity en : sp.serverLevel().getAllEntities()) if (en instanceof HelperEntity) en.discard();
    }

    private static HelperEntity spawn(ServerPlayer sp, Vec3 at, int skin) {
        HelperEntity h = HBRegistry.HELPER.get().create(sp.serverLevel());
        if (h == null) return null;
        float yaw = (float) Math.toDegrees(Math.atan2(-(origin.x - at.x), origin.z - at.z));
        h.moveTo(at.x, at.y, at.z, yaw, 0);
        h.setYHeadRot(yaw);
        h.setYBodyRot(yaw);
        HelperEntity.initNew(h, sp);
        h.setSkin(skin);
        h.setMode(Mode.STAY);
        h.setTool(new ItemStack(Items.IRON_PICKAXE));
        sp.serverLevel().addFreshEntity(h);
        return h;
    }

    private static void pin(ServerPlayer sp) {
        sp.teleportTo(origin.x, origin.y, origin.z);
        sp.setDeltaMovement(Vec3.ZERO);
    }

    private static void build(Minecraft mc) {
        built = true;
        stepIndex = 0;
        origin = mc.player.position();
        HelperBuddy.LOG.info("[client smoke] in the world at {}", origin);
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                ServerLevel l = sp.serverLevel();
                l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, sp.server);
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, sp.server);
                l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, sp.server);
                l.setDayTime(6000);
                sp.setGameMode(GameType.CREATIVE);
                clearHelpers(sp);
                pin(sp);
                helper = spawn(sp, origin.add(0, 0, 3), 0);
            });
            if (t == 20) lookAt = origin.add(0, 1.2, 3);
            if (t == 40) shot("helper_front");
            return t >= 44;
        });
        // every skin in a row
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                clearHelpers(sp);
                pin(sp);
                for (int i = 0; i < HelperEntity.SKINS; i++) spawn(sp, origin.add((i - 2.5) * 1.3, 0, 5), i);
            });
            if (t == 5) lookAt = origin.add(0, 1.0, 5);
            if (t == 30) shot("skins");
            return t >= 34;
        });
        // poses
        String[] poses = {"sit", "ko", "work", "wave", "cheer", "walk"};
        for (String pose : poses) {
            STEPS.add(t -> {
                if (t == 0) server(sp -> {
                    clearHelpers(sp);
                    pin(sp);
                    helper = spawn(sp, origin.add(0, 0, 2.6), 1);
                });
                if (t == 5) {
                    lookAt = origin.add(0, 1.0, 2.6);
                    server(sp -> {
                        HelperEntity h = first(sp);
                        if (h == null) return;
                        switch (pose) {
                            case "sit" -> h.setSitting(true);
                            case "ko" -> h.knockOut();
                            case "work" -> h.setWorking(true);
                            case "walk" -> h.setMode(Mode.FOLLOW);
                            default -> {
                            }
                        }
                    });
                }
                if (t == 12 && (pose.equals("wave") || pose.equals("cheer"))) server(sp -> {
                    HelperEntity h = first(sp);
                    if (h != null) h.act(pose);
                });
                if (t == 30) shot("pose_" + pose);
                return t >= 34;
            });
        }
        // screens
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                clearHelpers(sp);
                pin(sp);
                helper = spawn(sp, origin.add(0, 0, 3), 2);
                Bag.add(helper, new ItemStack(Items.OAK_LOG, 32));
            });
            if (t == 10) server(sp -> {
                HelperEntity h = first(sp);
                if (h != null) HBNet.openMenu(sp, h);
            });
            if (t == 30) shot("menu_main");
            return t >= 32;
        });
        for (String tab : new String[] {"jobs", "build", "settings", "talk"}) {
            STEPS.add(t -> {
                if (t == 0 && mc.screen instanceof HelperScreen hs) mc.setScreen(new HelperScreen(hs.data(), tab));
                if (t == 12) shot("menu_" + tab);
                return t >= 14;
            });
        }
        STEPS.add(t -> {
            if (t == 0) {
                mc.setScreen(null);
                server(HBNet::openBook);
            }
            if (t == 20) shot("command_book");
            if (t == 22) mc.setScreen(null);
            return t >= 24;
        });
        // dying keeps the command book: it comes back on respawn
        final int[] deadAt = {-1};
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                sp.getInventory().clearContent();
                sp.getInventory().add(new ItemStack(HBRegistry.COMMAND_BOOK.get()));
                sp.getInventory().add(new ItemStack(Items.BREAD, 3));
            });
            if (t == 5) server(sp -> sp.hurt(sp.damageSources().genericKill(), Float.MAX_VALUE));
            boolean dead = mc.player == null || !mc.player.isAlive() || mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen;
            if (dead && deadAt[0] < 0) deadAt[0] = t;
            if (deadAt[0] >= 0 && t == deadAt[0] + 20 && mc.player != null) {
                mc.player.respawn();
                mc.setScreen(null);
            }
            if (deadAt[0] >= 0 && t == deadAt[0] + 40) server(sp -> {
                boolean book = sp.getInventory().items.stream().anyMatch(it -> it.is(HBRegistry.COMMAND_BOOK.get()));
                HelperBuddy.LOG.info("[client smoke] command book after dying: {}", book);
                if (!book) HelperBuddy.LOG.error("[client smoke] the command book was lost on death (com.helperbuddy)");
                pin(sp);
            });
            if (t > 300) {
                HelperBuddy.LOG.error("[client smoke] the death test never finished (com.helperbuddy)");
                return true;
            }
            return deadAt[0] >= 0 && t >= deadAt[0] + 42;
        });
        // talking: an order chain and a question (the chat shows the answers)
        STEPS.add(t -> {
            if (t == 0) server(sp -> {
                HelperEntity h = first(sp);
                if (h == null) return;
                Talk.handle(sp, h, "ตัดไม้ 5 ท่อน แล้วเอามาให้");
                Talk.handle(sp, h, "where are diamonds?");
            });
            if (t == 10) mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
            if (t == 30) shot("talk_chat");
            if (t == 32) mc.setScreen(null);
            return t >= 34;
        });
        // building: a hotel and a random house, watched from the side
        String[][] builds = {{"hotel", "build_hotel"}, {"standard", "build_house"}, {"statue", "build_statue"}};
        for (int bi = 0; bi < builds.length; bi++) {
            final String[] b = builds[bi];
            final Vec3 spot = origin.add(-48 * (bi + 1), 0, 0);
            STEPS.add(t -> {
                if (t == 0) server(sp -> {
                    clearHelpers(sp);
                    sp.teleportTo(sp.serverLevel(), spot.x, spot.y, spot.z, 0f, 10f);
                    HelperEntity h = spawn(sp, spot.add(1.5, 0, -1), 3);
                    Gen.Opts o = new Gen.Opts();
                    o.type = b[0];
                    o.speed = 3;
                    o.floors = b[0].equals("hotel") ? 2 : null;
                    Talk.buildFromMenu(sp, h, o);
                });
                if (t == 2) lookAt = spot.add(0, 3, 14);
                if (t == 300) shot(b[1]);
                return t >= 302;
            });
        }
    }

    private static HelperEntity first(ServerPlayer sp) {
        for (Entity en : sp.serverLevel().getAllEntities()) if (en instanceof HelperEntity h && h.isAlive()) return h;
        return null;
    }
}
