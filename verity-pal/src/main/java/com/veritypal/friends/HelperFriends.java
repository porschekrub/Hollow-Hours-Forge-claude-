package com.veritypal.friends;

import com.veritypal.V;
import com.veritypal.VerityPal;
import com.veritypal.config.PalConfig;
import com.veritypal.talk.Lang;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Verity and Helper Buddy's helpers are friends: now and then she says something to a helper standing next to her and
 * the helper answers in its own way (with its own AI and voice when Helper Buddy has them). Helper Buddy is optional:
 * everything here is looked up while the game runs, nothing of it is needed to build or to play without it.
 */
public final class HelperFriends {
    private HelperFriends() {}

    static final Random R = new Random();
    static Boolean present;
    static Method helperName, sayRaw, sayTo, aiEnabled, aiChat;
    static java.lang.reflect.Constructor<?> msg;
    static long next;

    static boolean present() {
        if (present == null) {
            present = ModList.get().isLoaded("helperbuddy");
            if (present) {
                try {
                    Class<?> he = Class.forName("com.helperbuddy.entity.HelperEntity");
                    Class<?> say = Class.forName("com.helperbuddy.talk.Say");
                    Class<?> ai = Class.forName("com.helperbuddy.talk.AiBridge");
                    Class<?> m = Class.forName("com.helperbuddy.talk.AiBridge$Msg");
                    helperName = he.getMethod("helperName");
                    sayRaw = say.getMethod("raw", Player.class, he, String.class);
                    sayTo = say.getMethod("to", Player.class, he, String.class, String.class);
                    aiEnabled = ai.getMethod("enabled");
                    aiChat = ai.getMethod("chat", String.class, List.class, int.class, double.class);
                    msg = m.getConstructor(String.class, String.class);
                    VerityPal.LOG.info("[Verity Pal] Helper Buddy found: Verity is friends with the helpers");
                } catch (Throwable t) {
                    VerityPal.LOG.warn("[Verity Pal] Helper Buddy is installed but could not be talked to: {}", t.toString());
                    present = false;
                }
            }
        }
        return present;
    }

    public static boolean isHelper(@Nullable Entity e) {
        return e != null && "helperbuddy".equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getNamespace()) && present();
    }

    public static String name(Entity helper) {
        try {
            return (String) helperName.invoke(helper);
        } catch (Throwable t) {
            return helper.getName().getString();
        }
    }

    /** The helpers near her (for her prompt). */
    public static List<Entity> near(int r) {
        List<Entity> out = new ArrayList<>();
        Mob b = V.body();
        if (b == null || !PalConfig.on(PalConfig.FRIENDS) || !present()) return out;
        for (Entity e : b.level().getEntities(b, b.getBoundingBox().inflate(r), HelperFriends::isHelper)) out.add(e);
        return out;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 200 != 7) return;
        long now = System.currentTimeMillis();
        if (next == 0) next = now + (long) (PalConfig.FRIEND_MINUTES.get() * 60_000L * (0.5 + R.nextDouble()));
        if (now < next) return;
        List<Entity> h = near(10);
        if (h.isEmpty()) return;
        ServerPlayer p = V.owner(server);
        if (p == null || p.distanceTo(V.body()) > 24) return;
        next = now + (long) (PalConfig.FRIEND_MINUTES.get() * 60_000L * (0.6 + R.nextDouble() * 0.8));
        chat(server, p, h.get(R.nextInt(h.size())), null);
    }

    /** Verity says something to a helper (line given, or made up by her AI), then the helper answers. */
    public static void chat(MinecraftServer server, ServerPlayer p, Entity helper, @Nullable String line) {
        String hn = name(helper);
        if (line != null) {
            V.sayTo(server, hn, line);
            answer(server, p, helper, line);
            return;
        }
        if (V.aiReady()) {
            Heard.expect(text -> answer(server, p, helper, text));
            V.react(p, "Your friend " + hn + " (a Helper Buddy helper: a little worker who mines, farms and builds for " + p.getGameProfile().getName()
                    + ") is right next to you. Say one short, friendly-teasing line TO " + hn + " (start with their name), not to the player.");
        } else {
            String[][] lines = {{hn + " วันนี้ขยันจังนะ ใครจ้างมาเหรอ", hn + ", working hard today? Who's paying you?"},
                    {hn + " ช่วยขุดให้หน่อยสิ ฉันไม่มีมือ", hn + ", dig for me, will you? I have no hands"},
                    {hn + " เห็นไหม คนนี้ตายบ่อยมากเลย", hn + ", have you seen how often this one dies?"}};
            String[] l = lines[R.nextInt(lines.length)];
            String text = Lang.thai(p) ? l[0] : l[1];
            V.sayTo(server, hn, text);
            answer(server, p, helper, text);
        }
    }

    static void answer(MinecraftServer server, ServerPlayer p, Entity helper, String verityLine) {
        if (!helper.isAlive()) return;
        String hn = name(helper);
        boolean th = Lang.thai(p);
        try {
            if ((Boolean) aiEnabled.invoke(null)) {
                String sys = "You are " + hn + ", a friendly little Minecraft helper (Helper Buddy) who works for " + p.getGameProfile().getName()
                        + ". Your friend " + V.name() + ", a smug talking yellow smiley ball, just said to you: \"" + verityLine + "\". Answer " + V.name()
                        + " with one short, playful line (under 15 words) in " + (th ? "Thai" : "English") + ". Only the line, no quotes.";
                @SuppressWarnings("unchecked")
                CompletableFuture<String> f = (CompletableFuture<String>) aiChat.invoke(null, sys, List.of(msg.newInstance("user", verityLine)), 300, 0.9);
                f.thenAccept(r -> server.execute(() -> {
                    if (r != null && !r.isBlank() && helper.isAlive()) speak(p, helper, r);
                    else canned(p, helper);
                }));
                return;
            }
        } catch (Throwable t) {
            VerityPal.LOG.warn("[Verity Pal] Helper Buddy's AI could not answer: {}", t.toString());
        }
        canned(p, helper);
    }

    static void canned(ServerPlayer p, Entity helper) {
        String[][] lines = {{"ฮ่าๆ " + V.name() + " พูดอีกก็ถูกอีก", "Ha, " + V.name() + ", you're not wrong"},
                {"ไม่ต้องมายุ่งน่า " + V.name() + " ฉันทำงานอยู่", "Not now, " + V.name() + ", I'm working"},
                {V.name() + " ลูกบอลขี้เกียจ ไปช่วยบ้างสิ", V.name() + ", you lazy ball, come and help"}};
        String[] l = lines[R.nextInt(lines.length)];
        VerityPal.later(30, () -> {
            try {
                sayTo.invoke(null, p, helper, l[0], l[1]);
            } catch (Throwable t) {
                VerityPal.LOG.warn("[Verity Pal] a helper could not answer: {}", t.toString());
            }
        });
    }

    static void speak(ServerPlayer p, Entity helper, String line) {
        VerityPal.later(20, () -> {
            try {
                sayRaw.invoke(null, p, helper, line);
            } catch (Throwable t) {
                VerityPal.LOG.warn("[Verity Pal] a helper could not answer: {}", t.toString());
            }
        });
    }
}
