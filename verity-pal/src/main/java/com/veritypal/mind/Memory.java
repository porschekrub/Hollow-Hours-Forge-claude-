package com.veritypal.mind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.veritypal.VerityPal;
import com.veritypal.config.PalConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What Verity remembers about each player in every world and on every server of this computer:
 * config/veritypal-memory.json next to the game (Verity JE's own memory is kept per world). How much she likes them,
 * facts they told her, the worlds they played in together, how often they died.
 */
public final class Memory {
    private Memory() {}

    public static final class Person {
        public String name = "";
        public int like = 10;
        public Map<String, String> facts = new LinkedHashMap<>();
        public List<String> worlds = new ArrayList<>();
        public int deaths;
        public long met;
        public long lastSeen;
    }

    static final class Data {
        Map<String, Person> players = new LinkedHashMap<>();
    }

    static Data data = new Data();
    static boolean dirty;
    static int saveIn;
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("veritypal-memory.json");
    }

    public static void register(IEventBus bus) {
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) seen(p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                of(p).lastSeen = System.currentTimeMillis();
                changed();
            }
        });
    }

    public static synchronized void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Data d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<Data>() {}.getType());
                if (d != null && d.players != null) data = d;
            }
        } catch (Exception e) {
            VerityPal.LOG.warn("[Verity Pal] could not read {}: {}", file(), e.toString());
        }
    }

    public static synchronized void save() {
        if (!dirty || !PalConfig.on(PalConfig.MEMORY)) return;
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (Exception e) {
            VerityPal.LOG.warn("[Verity Pal] could not write {}: {}", file(), e.toString());
        }
    }

    public static void changed() {
        dirty = true;
        if (saveIn <= 0) saveIn = 200;
    }

    public static void tick() {
        if (saveIn > 0 && --saveIn == 0) save();
    }

    public static synchronized Person of(Player p) {
        Person r = data.players.computeIfAbsent(p.getUUID().toString(), k -> {
            Person n = new Person();
            n.met = System.currentTimeMillis();
            return n;
        });
        String name = p.getGameProfile().getName();
        if (!name.equals(r.name)) {
            r.name = name;
            changed();
        }
        return r;
    }

    @Nullable
    public static Person byName(String name) {
        for (Person p : data.players.values()) if (p.name.equalsIgnoreCase(name.trim())) return p;
        return null;
    }

    @Nullable
    public static Person byId(UUID id) {
        return data.players.get(id.toString());
    }

    static synchronized void seen(ServerPlayer p) {
        Person r = of(p);
        r.lastSeen = System.currentTimeMillis();
        String world = worldName();
        if (world != null && !r.worlds.contains(world)) {
            r.worlds.add(world);
            if (r.worlds.size() > 40) r.worlds.remove(0);
        }
        changed();
    }

    @Nullable
    static String worldName() {
        var s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return null;
        String n = s.getWorldData().getLevelName();
        return s.isDedicatedServer() ? "server: " + n : n;
    }

    public static int like(Player p) {
        return PalConfig.on(PalConfig.LIKES) ? of(p).like : 10;
    }

    public static synchronized int addLike(Player p, int change) {
        Person r = of(p);
        r.like = Math.max(0, Math.min(20, r.like + change));
        changed();
        return r.like;
    }

    public static synchronized void died(Player p) {
        of(p).deaths++;
        changed();
    }

    public static synchronized String remember(Player p, String fact) {
        Person r = of(p);
        String key = key(fact);
        r.facts.put(key, fact.trim());
        while (r.facts.size() > 30) r.facts.remove(r.facts.keySet().iterator().next());
        changed();
        return key;
    }

    public static synchronized boolean forget(Player p, String about) {
        Person r = of(p);
        String a = about.toLowerCase(Locale.ROOT).trim();
        boolean any = r.facts.keySet().removeIf(k -> k.contains(key(a)) || r.facts.get(k).toLowerCase(Locale.ROOT).contains(a));
        if (any) changed();
        return any;
    }

    static String key(String fact) {
        String k = fact.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_|_$", "");
        return k.length() > 40 ? k.substring(0, 40) : k;
    }

    /** For her prompt: what she knows about this player in every world. */
    public static synchronized String about(Player p) {
        if (!PalConfig.on(PalConfig.MEMORY)) return "";
        Person r = of(p);
        StringBuilder sb = new StringBuilder();
        if (!r.facts.isEmpty()) sb.append(" Things you remember about them from every world: ").append(String.join("; ", r.facts.values())).append('.');
        if (r.worlds.size() > 1) sb.append(" You have been with them in ").append(r.worlds.size()).append(" worlds (").append(String.join(", ", r.worlds.subList(Math.max(0, r.worlds.size() - 5), r.worlds.size()))).append(").");
        if (r.deaths > 0) sb.append(" They have died ").append(r.deaths).append(" times with you around.");
        if (r.met > 0) {
            long days = (System.currentTimeMillis() - r.met) / 86_400_000L;
            if (days > 0) sb.append(" You met them ").append(days).append(" real days ago.");
        }
        return sb.toString();
    }

    /** (tests) */
    public static JsonObject snapshot() {
        return JsonParser.parseString(GSON.toJson(data)).getAsJsonObject();
    }
}
