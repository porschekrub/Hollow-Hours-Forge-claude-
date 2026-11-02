package com.hollowhours.ghost;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.item.BoardItem;
import com.hollowhours.item.RepairKitItem;
import com.hollowhours.registry.HHItems;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The Forge event hub: every game event the ghost systems react to. */
public final class GhostEvents {
    private GhostEvents() {}

    static final String GUIDE_TAG = "hollowhours_guide_given";
    private static final Map<UUID, Integer> KILLED_BY = new HashMap<>();
    private static final Map<UUID, Integer> FLED_FROM = new HashMap<>();
    private static final Map<UUID, Long> BED_AT = new HashMap<>();
    private static boolean smokeDone;

    public static void register(IEventBus bus) {
        bus.addListener(GhostEvents::onServerTick);
        bus.addListener(GhostEvents::onPlayerTick);
        bus.addListener(EventPriority.HIGH, GhostEvents::onRightClickBlock);
        bus.addListener(GhostEvents::onRightClickItem);
        bus.addListener(GhostEvents::onUseFinish);
        bus.addListener(GhostEvents::onUseStop);
        bus.addListener(GhostEvents::onHurt);
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, GhostEvents::onDamage);
        bus.addListener(GhostEvents::onLivingTick);
        bus.addListener((PlayerWakeUpEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && !e.updateLevel()) Sanity.onWakeUp(p);
        });
        bus.addListener(EventPriority.HIGH, GhostEvents::onAttacked);
        bus.addListener(GhostEvents::onBreak);
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOW, com.hollowhours.physics.Physics::onBreak);
        bus.addListener(com.hollowhours.physics.Physics::onExplosion);
        bus.addListener(GhostEvents::onPlace);
        bus.addListener(GhostEvents::onMount);
        bus.addListener(EventPriority.HIGHEST, GhostEvents::onDeath);
        bus.addListener(GhostEvents::onDrops);
        bus.addListener(EventPriority.LOW, com.hollowhours.grave.Graves::onDrops);
        bus.addListener(com.hollowhours.grave.Graves::onXp);
        bus.addListener(GhostEvents::onRespawn);
        bus.addListener(GhostEvents::onLogin);
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Atmosphere.send(p);
        });
        bus.addListener(GhostEvents::onLogout);
        bus.addListener(GhostEvents::onCrafted);
        bus.addListener(GhostEvents::onChat);
        bus.addListener(GhostEvents::onLevelLoad);
        bus.addListener(GhostEvents::onServerStarted);
        bus.addListener(GhostEvents::onServerStopping);
        com.hollowhours.rescue.Rescue.register(bus);
    }

    // ------------------------------------------------------------------ ticking
    static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        long now = server.overworld().getGameTime();
        Later.tick(now);
        safe("boards", () -> Boards.tick(now));
        safe("stalkers", () -> Stalkers.tick(server, now));
        safe("thirst", () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) Thirst.trackFood(p);
        });
        if (now % 5 == 0) safe("music", () -> Director.musicTick(server, now));
        if (now % 10 == 0) safe("dread", () -> Director.dreadTick(server, now));
        if (now % 20 == 0) {
            safe("story", () -> Story.tick(server, now));
            safe("atmosphere", () -> Atmosphere.tick(server, now));
            safe("phone", () -> PhoneUse.tick(server, now));
            safe("survival", () -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    Thirst.tick(p, now);
                    Sanity.tick(p, now);
                    Survival.sync(p);
                }
            });
            safe("soul", () -> Soul.tick(server, now));
            safe("graves", () -> com.hollowhours.grave.Graves.tick(server));
            safe("rescue", () -> com.hollowhours.rescue.Rescue.tick(server));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) Curses.hasteTick(p);
        }
        // the first nights are normal: nothing haunts anyone before the story starts
        if (now % 40 == 0 && !CLIENT_SMOKE && Story.activeCached()) {
            safe("director", () -> Director.spawnTick(server, now));
            for (ServerLevel level : server.getAllLevels()) safe("cracks", () -> Breach.maintain(level));
        }
        if (now % 100 == 0 && !CLIENT_SMOKE && Story.activeCached()) {
            safe("variety", () -> Variety.tick(server, now));
            safe("stalker schedule", () -> Stalkers.schedule(server, now));
        }
        if (!smokeDone && Boolean.getBoolean("hollowhours.smoketest") && server.getTickCount() >= 100) {
            smokeDone = true;
            SmokeTest.run(server);
        }
    }

    /** CI client screenshots: no natural ghosts or stalkers interrupting the tour */
    static final boolean CLIENT_SMOKE = Boolean.getBoolean("hollowhours.clientsmoke");

    public static boolean clientSmoke() {
        return CLIENT_SMOKE;
    }

    static void safe(String what, Runnable r) {
        try {
            r.run();
        } catch (Exception ex) {
            HollowHours.LOG.error("[Hollow Hours] {} tick failed", what, ex);
        }
    }

    static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long now = p.serverLevel().getGameTime();
        try {
            Perception.playerTick(p);
            Hiding.tick(p);
            Sprint.tick(p, now);
            Flashlight.tick(p, now);
        } catch (Exception ex) {
            HollowHours.LOG.error("[Hollow Hours] player tick failed", ex);
        }
    }

    // ------------------------------------------------------------------ interactions
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Player pl = e.getEntity();
        BlockPos pos = e.getPos();
        ItemStack held = e.getItemStack();
        if (pl.level().isClientSide) {
            // don't let a boarded door swing open for a frame (the server decides the rest)
            if (!(held.getItem() instanceof BoardItem) && !(held.getItem() instanceof RepairKitItem) && Boards.isBoardedDoor(pl.level(), pos)) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (!(pl instanceof ServerPlayer p)) return;
        ServerLevel level = p.serverLevel();
        BlockState s = level.getBlockState(pos);
        if (e.getHand() != InteractionHand.MAIN_HAND) {
            if (Boards.isBoardedDoor(level, pos)) cancel(e);
            return;
        }
        if (KillSequence.isVictim(p) || Hiding.isHidden(p)) {
            cancel(e);
            return;
        }
        // repair kits
        if (held.getItem() instanceof RepairKitItem kit && Breach.classify(s) != null) {
            if (Breach.repair(p, level, pos, kit.fam)) cancel(e);
            return;
        }
        // boards (nailing, prying, boarded doors stay shut)
        if (Boards.onRightClick(p, pos, held)) {
            cancel(e);
            return;
        }
        // sneak + right-click a bed = slide under it
        if (s.getBlock() instanceof BedBlock && p.isShiftKeyDown()) {
            long now = level.getGameTime();
            if (now - BED_AT.getOrDefault(p.getUUID(), -99L) >= 15) {
                BED_AT.put(p.getUUID(), now);
                Hiding.hideUnderBed(p, pos);
            }
            cancel(e);
            return;
        }
        if (Curses.isCursed(held)) Curses.handleUse(p, "useon", held);
    }

    static void cancel(PlayerInteractEvent.RightClickBlock e) {
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            if (KillSequence.isVictim(p)) {
                e.setCanceled(true);
                return;
            }
            if (Curses.isCursed(e.getItemStack()) && !e.getItemStack().isEdible()) Curses.handleUse(p, "use", e.getItemStack());
        }
    }

    static void onUseFinish(LivingEntityUseItemEvent.Finish e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            Thirst.onFinishUsing(sp, e.getItem());
            Sanity.onFinishUsing(sp, e.getItem());
        }
        if (e.getEntity() instanceof ServerPlayer p && Curses.isCursed(e.getItem())) Curses.handleUse(p, "consume", e.getItem());
    }

    static void onUseStop(LivingEntityUseItemEvent.Stop e) {
        if (e.getEntity() instanceof ServerPlayer p && Curses.isCursed(e.getItem())) Curses.handleUse(p, "release", e.getItem());
    }

    /** While a ghost holds you nothing else gets to hurt you (a slime or a zombie would cut the kill short). */
    static void onAttacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && KillSequence.isVictim(p) && !GhostDamage.isGhost(e.getSource())
                && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
    }

    static void onDamage(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
        BloodFx.hurt(e.getEntity(), e.getSource(), e.getAmount());
    }

    static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
        var le = e.getEntity();
        if (!le.level().isClientSide && le.tickCount % 15 == 0) BloodFx.drip(le);
    }

    static void onHurt(LivingHurtEvent e) {
        float amount = e.getAmount();
        if (e.getSource().getEntity() instanceof ServerPlayer attacker && e.getSource().getDirectEntity() == attacker)
            amount = Curses.attackBonus(attacker, amount);
        if (e.getEntity() instanceof ServerPlayer p) {
            boolean melee = e.getSource().getDirectEntity() instanceof LivingEntity;
            amount = Curses.onPlayerHurt(p, amount, melee);
            if (!GhostDamage.isGhost(e.getSource())) amount *= (float) Soul.damageMult(p);
        }
        e.setAmount(amount);
    }

    static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = e.getState();
        Boards.onBreak(p, level, e.getPos(), s);
        if (Breach.onPlayerBreak(p, level, e.getPos(), s)) e.setCanceled(true);
        Curses.handleUse(p, "break", null);
    }

    static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getLevel() instanceof ServerLevel level) Breach.onPlace(level, e.getPos());
    }

    static void onMount(EntityMountEvent e) {
        if (!e.isDismounting() || !(e.getEntityMounting() instanceof ServerPlayer p)) return;
        if (!p.isAlive() || p.isRemoved() || p.hasDisconnected() || e.getEntityBeingMounted() == null) return;
        if (!KillSequence.allowDismount(p, e.getEntityBeingMounted()) || !Hiding.allowDismount(p, e.getEntityBeingMounted())) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ death & respawn
    public static void markKilledByGhost(ServerPlayer p, int ghostN) {
        KILLED_BY.put(p.getUUID(), ghostN);
    }

    public static void markLoggedOutInKill(ServerPlayer p, int ghostN) {
        FLED_FROM.put(p.getUUID(), ghostN);
    }

    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Hiding.cleanup(p);
        Flashlight.clearLights(p);
        if (GhostDamage.isGhost(e.getSource()) && e.getSource().getEntity() instanceof GhostEntity g) KILLED_BY.putIfAbsent(p.getUUID(), g.kind.n);
        Integer n = KILLED_BY.get(p.getUUID());
        if (n == null) return;
        Director.stopMusicFor(p);
        Vec3 l = p.position();
        Fx.sound(p.level(), "ghost.jumpscare", l, 1f, 1f, 30);
        Fx.playSelf(p, "ghost.growl", 1f, 0.7f);
        Fx.playSelf(p, "mob.ghast.scream", 1f, 0.5f);
        Fx.playSelf(p, "ghost.flatline", 1f, 1f);
        Fx.title(p, Component.translatable("hollowhours.death.big." + R.randInt(0, 2)), null, 0, 30, 20);
        Fx.shake(p, 1f, 36);
        Fx.fade(p, 0x6E0000, 1, 5, 18);
    }

    static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer)) return;
        // the guide stays with you
        e.getDrops().removeIf(it -> it.getItem().is(HHItems.GHOST_GUIDE.get()));
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Soul.onRespawn(p);
        Sprint.onRespawn(p);
        Atmosphere.send(p);
        if (!e.isEndConquered()) giveGuideIfMissing(p, false);
        Integer n = KILLED_BY.remove(p.getUUID());
        if (n != null) Later.run(30, () -> {
            if (!p.isAlive() || p.hasDisconnected()) return;
            Jumpscares.afterDeath(p, n);
            Fx.playSelf(p, "ghost.jumpscare", 0.9f, 0.9f);
            Fx.title(p, Component.translatable("hollowhours.death.small." + R.randInt(0, 1)), null, 0, 30, 20);
            Fx.shake(p, 0.6f, 20);
        });
    }

    static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    static void giveGuideIfMissing(ServerPlayer p, boolean firstJoin) {
        CompoundTag d = persisted(p);
        if (firstJoin && d.getBoolean(GUIDE_TAG)) return;
        if (!firstJoin && !d.getBoolean(GUIDE_TAG)) return;
        for (ItemStack s : p.getInventory().items) if (s.is(HHItems.GHOST_GUIDE.get())) return;
        if (p.getInventory().offhand.get(0).is(HHItems.GHOST_GUIDE.get())) return;
        d.putBoolean(GUIDE_TAG, true);
        ItemStack it = new ItemStack(HHItems.GHOST_GUIDE.get());
        if (!p.getInventory().add(it)) p.drop(it, false);
        if (firstJoin) p.sendSystemMessage(Component.translatable("hollowhours.guide.given").withStyle(ChatFormatting.DARK_RED));
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        giveGuideIfMissing(p, true);
        Story.onLogin(p);
        Atmosphere.send(p);
        Survival.forceSync(p);
        Integer n = FLED_FROM.remove(p.getUUID());
        if (n != null) Later.run(80, () -> punishFlee(p, n));
    }

    /** It waited for you. */
    static void punishFlee(ServerPlayer p, int n) {
        if (p.hasDisconnected() || !p.isAlive() || !GhostBrain.targetable(p)) return;
        Vec3 spot = Director.findSpot(p, true, 3, 6);
        if (spot == null) spot = p.position().subtract(p.getViewVector(1f).multiply(3, 0, 3));
        GhostEntity g = Director.spawnGhost(p.serverLevel(), GhostKind.byN(n), spot);
        if (g == null) return;
        g.setRaged(true);
        Fx.title(p, Component.translatable("hollowhours.fled.title"), Component.translatable("hollowhours.fled.sub"), 0, 40, 20);
        Later.run(10, () -> {
            if (g.isAlive()) g.brain().snap(p);
        });
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        KillSequence.onLogout(p);
        Hiding.cleanup(p);
        Flashlight.clearLights(p);
        Sprint.forget(p);
        PhoneUse.forget(p);
        Survival.forget(p);
        Thirst.forget(p);
        Perception.forget(p);
        Director.forget(p);
        GhostTalk.forget(p);
        Boards.forget(p);
        BED_AT.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ misc
    static void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        Curses.onCrafted(e.getCrafting(), e.getInventory());
    }

    static final Pattern CHAT = Pattern.compile("^\\s*(wail|widow|ash|void|stag|ผีร่ำไห้|ร่ำไห้|เจ้าสาว|แม่ม่าย|เถ้า|ยิ้ม|เขากวาง|หิวโหย)\\s*[:,>\\-.!?]+\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    /** "wail: who are you?" in chat talks to that ghost (everyone still sees the message). */
    static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        Matcher m = CHAT.matcher(e.getRawText());
        if (!m.matches()) return;
        String who = m.group(1).toLowerCase();
        int n = switch (who) {
            case "wail", "ผีร่ำไห้", "ร่ำไห้" -> 1;
            case "widow", "เจ้าสาว", "แม่ม่าย" -> 2;
            case "ash", "เถ้า" -> 3;
            case "void", "ยิ้ม" -> 4;
            default -> 5;
        };
        String text = m.group(2);
        p.server.execute(() -> GhostTalk.talk(p, n, text, null));
    }

    static void onLevelLoad(LevelEvent.Load e) {
        if (e.getLevel() instanceof ServerLevel level) {
            try {
                Flashlight.cleanupOld(level);
            } catch (Exception ex) {
                HollowHours.LOG.warn("[Hollow Hours] flashlight cleanup failed", ex);
            }
        }
    }

    static void onServerStarted(ServerStartedEvent e) {
        Later.clear();
        Director.reset();
        Atmosphere.reset();
        KillSequence.clear();
        Stalkers.clear();
        KILLED_BY.clear();
        smokeDone = false;
    }

    static void onServerStopping(ServerStoppingEvent e) {
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            Flashlight.clearLights(p);
            Hiding.cleanup(p);
        }
        Stalkers.clear();
        KillSequence.clear();
        Later.clear();
    }
}
