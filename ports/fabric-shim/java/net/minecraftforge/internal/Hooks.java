package net.minecraftforge.internal;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Fabric callbacks -> the shim's Forge events on {@link MinecraftForge#EVENT_BUS} (the rest come from the mixins). */
public final class Hooks {
    private Hooks() {}

    private static boolean done;

    public static boolean post(Event e) {
        return MinecraftForge.EVENT_BUS.post(e);
    }

    public static void init() {
        if (done) return;
        done = true;
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            ServerLifecycleHooks.currentServer = s;
            post(new ServerStartingEvent(s));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> post(new ServerStartedEvent(s)));
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> post(new ServerStoppingEvent(s)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            post(new ServerStoppedEvent(s));
            if (ServerLifecycleHooks.currentServer == s) ServerLifecycleHooks.currentServer = null;
        });
        ServerTickEvents.START_SERVER_TICK.register(s -> post(new TickEvent.ServerTickEvent(TickEvent.Phase.START, s)));
        ServerTickEvents.END_SERVER_TICK.register(s -> post(new TickEvent.ServerTickEvent(TickEvent.Phase.END, s)));
        ServerTickEvents.START_WORLD_TICK.register(l -> post(new TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER, TickEvent.Phase.START, l)));
        ServerTickEvents.END_WORLD_TICK.register(l -> post(new TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER, TickEvent.Phase.END, l)));
        ServerWorldEvents.LOAD.register((s, l) -> post(new LevelEvent.Load(l)));
        ServerWorldEvents.UNLOAD.register((s, l) -> post(new LevelEvent.Unload(l)));
        CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> post(new RegisterCommandsEvent(d, sel, ctx)));
        CommonLifecycleEvents.TAGS_LOADED.register((reg, client) -> post(new TagsUpdatedEvent(reg, client)));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((msg, sender, params) ->
                !post(new ServerChatEvent(sender, msg.signedContent(), msg.decoratedContent())));
        ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> post(new PlayerEvent.PlayerLoggedInEvent(h.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> post(new PlayerEvent.PlayerLoggedOutEvent(h.player)));
        ServerPlayerEvents.COPY_FROM.register((old, now, alive) -> {
            // Forge keeps a player's "PlayerPersisted" data through death and the way back from the End
            CompoundTag o = Persist.of(old);
            if (o.contains("PlayerPersisted")) Persist.of(now).put("PlayerPersisted", o.getCompound("PlayerPersisted").copy());
            post(new PlayerEvent.Clone(now, old, !alive));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((old, now, alive) -> post(new PlayerEvent.PlayerRespawnEvent(now, alive)));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((p, from, to) ->
                post(new PlayerEvent.PlayerChangedDimensionEvent(p, from.dimension(), to.dimension())));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> !post(new LivingAttackEvent(e, src, amount)));
        ServerEntityEvents.ENTITY_LOAD.register((e, l) -> Ext.call(e, "onAddedToWorld"));
        ServerEntityEvents.ENTITY_UNLOAD.register((e, l) -> Ext.call(e, "onRemovedFromWorld"));
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !post(new BlockEvent.BreakEvent(level, pos, state, player)));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            PlayerInteractEvent.RightClickBlock e = new PlayerInteractEvent.RightClickBlock(player, hand, hit);
            if (!post(e)) return InteractionResult.PASS;
            // cancelled: nothing more happens (Fabric goes on with PASS, so "nothing" is FAIL here)
            return e.getCancellationResult() == InteractionResult.PASS ? InteractionResult.FAIL : e.getCancellationResult();
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            PlayerInteractEvent.RightClickItem e = new PlayerInteractEvent.RightClickItem(player, hand);
            if (!post(e)) return InteractionResultHolder.pass(player.getItemInHand(hand));
            InteractionResult r = e.getCancellationResult();
            return r == InteractionResult.PASS || r == InteractionResult.FAIL ? InteractionResultHolder.fail(player.getItemInHand(hand))
                    : new InteractionResultHolder<>(r, player.getItemInHand(hand));
        });
        EntitySleepEvents.STOP_SLEEPING.register((e, pos) -> {
            if (e instanceof Player p) post(new PlayerWakeUpEvent(p, false, false));
        });
    }
}
