package com.veritypal;

import com.veritypal.talk.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import varmite.verity.VerityConfig;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.llm.AiManager;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.event.ModEvents;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.network.ModNetwork;
import varmite.verity.network.PlayTtsPayload;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything Verity Pal asks of Verity JE, in one place (so a new version of Verity JE only needs changes here).
 * Verity JE keeps one Verity per server: VerityState.verityEntity is her body when she is out of the pocket.
 * Minecraft's own methods are always called on her as a Mob (never through the Verity class).
 */
public final class V {
    private V() {}

    public static final ResourceLocation ENTITY = VerityPal.rl("verity", "verity");
    public static final ResourceLocation ITEM = VerityPal.rl("verity", "verity_item");
    public static final ResourceLocation DEMON = VerityPal.rl("verity", "verity_demon");

    /** Her body in the world, or null (in a pocket, or not met yet). */
    @Nullable
    public static Mob body() {
        VerityEntity v = VerityState.verityEntity;
        Mob m = v;
        return m != null && !m.isRemoved() && m.isAlive() ? m : null;
    }

    @Nullable
    static VerityEntity verity() {
        Mob m = body();
        return m == null ? null : (VerityEntity) m;
    }

    public static boolean isVerity(@Nullable Entity e) {
        return e != null && ENTITY.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    @Nullable
    public static EntityType<?> entityType() {
        return ForgeRegistries.ENTITY_TYPES.getValue(ENTITY);
    }

    @Nullable
    public static Item item() {
        return ForgeRegistries.ITEMS.getValue(ITEM);
    }

    public static boolean inPocket(@Nullable Player p) {
        if (p == null) return false;
        try {
            return ModEvents.hasVerityInInventory(p);
        } catch (Throwable t) {
            Item it = item();
            if (it == null) return false;
            for (ItemStack s : p.getInventory().items) if (s.is(it)) return true;
            return p.getOffhandItem().is(it);
        }
    }

    /** Her name (Verity JE's setting "customName"). */
    public static String name() {
        try {
            String n = VerityConfig.VERITY_CUSTOM_NAME.get();
            if (n != null && !n.isBlank()) return n.trim();
        } catch (Throwable ignored) {
        }
        return "Verity";
    }

    @Nullable
    public static UUID ownerId() {
        VerityEntity v = verity();
        if (v == null) return null;
        try {
            Optional<UUID> o = v.getOwnerUUID();
            return o.orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The player she belongs to (or the nearest one, when she has no owner), or null. */
    @Nullable
    public static ServerPlayer owner(MinecraftServer server) {
        Mob b = body();
        UUID id = ownerId();
        if (id != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) return p;
        }
        if (b != null && b.level() instanceof ServerLevel l) {
            Player p = l.getNearestPlayer(b, 64);
            if (p instanceof ServerPlayer sp) return sp;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (inPocket(p)) return p;
        return null;
    }

    /** Players who can hear her: near her body, or carrying her. */
    public static java.util.List<ServerPlayer> listeners(MinecraftServer server) {
        java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
        Mob b = body();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (b != null && p.level() == b.level() && p.distanceTo(b) < 48 || inPocket(p)) out.add(p);
        }
        return out;
    }

    public static void face(String variant) {
        VerityEntity v = verity();
        if (v == null) return;
        try {
            v.setVariant(variant);
        } catch (Throwable ignored) {
        }
    }

    public static String face() {
        VerityEntity v = verity();
        try {
            return v == null ? "happy" : v.getVariant();
        } catch (Throwable t) {
            return "happy";
        }
    }

    public static void talking(int ticks) {
        VerityEntity v = verity();
        if (v == null) return;
        try {
            v.startTalking(ticks);
        } catch (Throwable ignored) {
        }
    }

    /** Verity JE's own "say something random" timer is held back: Verity Pal decides when she talks on her own (Life). */
    public static void holdIdleChat() {
        try {
            if (VerityState.idleChatTimer < 1200) VerityState.idleChatTimer = 1200;
        } catch (Throwable ignored) {
        }
    }

    public static boolean following() {
        try {
            return VerityState.followPlayer;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void follow(boolean on) {
        try {
            VerityState.followPlayer = on;
        } catch (Throwable ignored) {
        }
    }

    /** Her mood with the players of this world, 0 - 20 (Verity JE's "karma"). */
    public static float karma(MinecraftServer server) {
        try {
            return WorldSpawnData.get(server.overworld()).verityKarma;
        } catch (Throwable t) {
            return 10;
        }
    }

    public static void addKarma(MinecraftServer server, float amount) {
        try {
            ModEvents.updateAndSyncKarma(server.overworld(), amount);
        } catch (Throwable ignored) {
        }
    }

    public static boolean demonLoose(MinecraftServer server) {
        try {
            return WorldSpawnData.get(server.overworld()).hasLiveDemon();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Verity's AI is set up (a key, or a local Ollama). Without it she uses lines written here. */
    public static boolean aiReady() {
        try {
            String prov = VerityConfig.AI_PROVIDER.get().name();
            if (prov.equals("OLLAMA")) return true;
            if (System.getProperty("veritypal.fakeai") != null) return true;
            String key = VerityConfig.API_KEY.get();
            String url = VerityConfig.AI_ENDPOINT.get();
            return key != null && !key.isBlank() || prov.equals("OPENAI") && url != null && !url.isBlank();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Asks her AI to react to something that happened (her answer is said aloud and written in chat by Verity JE).
     * The note is in English: she answers in the player's language anyway.
     */
    public static void react(@Nullable ServerPlayer p, String note) {
        try {
            AiManager.queryAI(verity(), "<SYSTEM> " + note, p);
        } catch (Throwable t) {
            VerityPal.LOG.warn("[Verity Pal] could not ask Verity's AI: {}", t.toString());
        }
    }

    /** A line of hers that was written here (no AI): in chat and aloud, to everyone who can hear her. */
    public static void say(MinecraftServer server, String th, String en) {
        Mob b = body();
        for (ServerPlayer p : listeners(server)) {
            String line = Lang.thai(p) ? th : en;
            p.sendSystemMessage(Component.literal("<" + name() + "> " + line));
            Entity from = b != null && b.level() == p.level() ? b : p;
            voice(p, from, line);
        }
        talking(Math.max(40, Math.min(160, (th.length() + en.length()) * 2)));
    }

    /** Says a line aloud for one player (Verity JE's voice packet). */
    public static void voice(ServerPlayer p, Entity from, String line) {
        com.veritypal.friends.Heard.ours = true;
        try {
            ModNetwork.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PlayTtsPayload(from.getId(), line));
        } catch (Throwable t) {
            if (VOICE_WARNED++ == 0) VerityPal.LOG.warn("[Verity Pal] could not use Verity's voice: {}", t.toString());
        } finally {
            com.veritypal.friends.Heard.ours = false;
        }
    }

    static int VOICE_WARNED;

    /** Says a line to everyone near: "<Verity → Chicken> line". */
    public static void sayTo(MinecraftServer server, String to, String line) {
        Mob b = body();
        for (ServerPlayer p : listeners(server)) {
            p.sendSystemMessage(Component.literal("<" + name() + " → " + to + "> " + line));
            voice(p, b != null && b.level() == p.level() ? b : p, line);
        }
        talking(Math.max(40, Math.min(160, line.length() * 3)));
    }

    // ------------------------------------------------------------------ is this Verity JE the one we know?
    /** What did not match (a newer Verity JE may have changed): written to the log and told to players. */
    public static final java.util.List<String> MISSING = new java.util.ArrayList<>();

    static void check() {
        String ver = ModList.get().getModContainerById("verity").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        VerityPal.LOG.info("[Verity Pal] Verity JE {} found", ver);
        need("varmite.verity.entity.llm.AiAPI", "getSystemPrompt");
        need("varmite.verity.entity.llm.AiAPI", "ask");
        need("varmite.verity.entity.llm.AiManager", "queryAI");
        need("varmite.verity.entity.llm.actions.Tools", "transform_next_day");
        need("varmite.verity.entity.verity.VerityEntity", "transformIntoDemon");
        // her voice is on the game's side only (looking at it on a server would load the game's client classes)
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            need("varmite.verity.client.audio.TTSHandler", "playTTS");
            need("varmite.verity.client.audio.TTSHandler", "transcribeAudio");
        }
        need("varmite.verity.entity.VerityState", null);
        if (!MISSING.isEmpty()) VerityPal.LOG.warn("[Verity Pal] this Verity JE ({}) is not the one Verity Pal was made for (6.0.0): missing {}", ver, MISSING);
    }

    static void need(String cls, @Nullable String method) {
        try {
            Class<?> c = Class.forName(cls, false, V.class.getClassLoader());
            if (method == null) return;
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) if (m.getName().equals(method)) return;
            MISSING.add(cls.substring(cls.lastIndexOf('.') + 1) + "." + method);
        } catch (Throwable t) {
            MISSING.add(cls.substring(cls.lastIndexOf('.') + 1));
        }
    }
}
