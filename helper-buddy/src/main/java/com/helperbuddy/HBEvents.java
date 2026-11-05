package com.helperbuddy;

import com.helperbuddy.compat.Compat;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.registry.HBRegistry;
import com.helperbuddy.talk.Say;
import com.helperbuddy.talk.Talk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/** Game events: talking in chat, defending the owner, the first contract, the CI smoke test. */
public final class HBEvents {
    private HBEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, HBEvents::chat);
        bus.addListener(HBEvents::hurt);
        bus.addListener(HBEvents::login);
        bus.addListener(HBEvents::started);
        // before graves (Hollow Hours) or anything else takes the drops
        bus.addListener(EventPriority.HIGH, HBEvents::keepBookOnDeath);
        bus.addListener(HBEvents::giveBookBack);
    }

    static final String KEPT = "helperbuddy_kept";

    /** The command book stays with you when you die (it is handed back when you respawn). */
    static void keepBookOnDeath(net.minecraftforge.event.entity.living.LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !HBConfig.KEEP_BOOK.get()) return;
        net.minecraft.nbt.ListTag kept = new net.minecraft.nbt.ListTag();
        e.getDrops().removeIf(ie -> {
            if (!ie.getItem().is(HBRegistry.COMMAND_BOOK.get())) return false;
            kept.add(Compat.saveStack(ie.getItem()));
            return true;
        });
        if (kept.isEmpty()) return;
        // the persisted part of a player's data is carried over to the respawned player
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        net.minecraft.nbt.ListTag old = persisted.getList(KEPT, net.minecraft.nbt.Tag.TAG_COMPOUND);
        old.addAll(kept);
        persisted.put(KEPT, old);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    static void giveBookBack(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(KEPT)) return;
        net.minecraft.nbt.ListTag kept = persisted.getList(KEPT, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < kept.size(); i++) {
            ItemStack s = Compat.loadStack(kept.getCompound(i));
            if (!s.isEmpty() && !p.getInventory().add(s)) p.drop(s, false);
        }
        persisted.remove(KEPT);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** "!ไปตัดไม้", "!chop wood", "Buddy, chop wood", "ทุกคน มานี่" */
    static void chat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        String msg = e.getRawText().trim();
        if (msg.isEmpty()) return;
        boolean bang = msg.startsWith("!");
        boolean named = false;
        if (!bang) {
            String low = msg.toLowerCase();
            if (low.startsWith("ทุกคน") || low.startsWith("everyone") || low.startsWith("ผู้ช่วย") || low.startsWith("helper,") || low.startsWith("helper ")) named = true;
            else for (HelperEntity h : Helpers.owned(p)) {
                String n = h.helperName().toLowerCase();
                if (!n.isEmpty() && low.startsWith(n) && (low.length() == n.length() || !Character.isLetterOrDigit(low.charAt(n.length())))) {
                    named = true;
                    break;
                }
            }
        }
        if (!bang && !named) return;
        String text = bang ? msg.substring(1).trim() : msg.replaceFirst("^(?i)(ผู้ช่วย|helper)[\\s,:]*", "");
        p.server.execute(() -> Talk.command(p, text));
        if (bang) e.setCanceled(true); // orders with "!" are not broadcast to everybody
    }

    /** Helpers defend their owner: whatever hurts the owner, or whatever the owner attacks, becomes their target. */
    static void hurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        Entity src = e.getSource().getEntity();
        if (victim.level().isClientSide) return;
        if (victim instanceof ServerPlayer p && src instanceof LivingEntity attacker && attacker != p && !(attacker instanceof HelperEntity)) {
            for (HelperEntity h : Helpers.owned(p))
                if (h.level() == p.level() && h.distanceTo(p) < 32 && (h.settings.get("fight") || h.settings.get("fightBack")))
                    h.brain().combat().setTarget(attacker, attacker instanceof Player ? HBConfig.ALLOW_PVP_FIGHT_BACK.get() : true, false);
        }
        if (src instanceof ServerPlayer p && victim != p && !(victim instanceof HelperEntity) && !(victim instanceof Player)) {
            for (HelperEntity h : Helpers.owned(p))
                if (h.level() == p.level() && h.distanceTo(p) < 24 && h.settings.get("fight")) h.brain().combat().setTarget(victim, true, false);
        }
    }

    /** The first time a player joins they get a helper contract (config). */
    static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !HBConfig.GIVE_CONTRACT_ON_JOIN.get()) return;
        CompoundTag data = p.getPersistentData();
        CompoundTag keep = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (keep.getBoolean("helperbuddy_contract")) return;
        keep.putBoolean("helperbuddy_contract", true);
        data.put(Player.PERSISTED_NBT_TAG, keep);
        p.getInventory().add(new ItemStack(HBRegistry.CONTRACT.get()));
        p.sendSystemMessage(Component.literal(Say.t(p, "§6[Helper Buddy]§r คุณได้รับ §eสัญญาจ้างผู้ช่วย§r! คลิกขวาที่พื้นเพื่อเรียกผู้ช่วยมาทำงานให้",
                "§6[Helper Buddy]§r You got a §eHelper Contract§r! Use it on the ground to call your helper")));
    }

    static void started(ServerStartedEvent e) {
        if (Boolean.getBoolean("helperbuddy.smoketest")) SmokeTest.start(e.getServer());
    }
}
