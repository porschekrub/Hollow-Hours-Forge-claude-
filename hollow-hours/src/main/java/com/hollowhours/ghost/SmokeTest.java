package com.hollowhours.ghost;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.BoardsEntity;
import com.hollowhours.entity.ClosetEntity;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.entity.StalkerEntity;
import com.hollowhours.entity.StalkerKind;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.registry.HHItems;
import com.hollowhours.registry.HHSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Run by the CI server smoke test (-Dhollowhours.smoketest=true): loads all data, spawns every entity, ticks the
 * ghost systems for a moment, then prints "SMOKE TEST OK" and stops the server. Any exception fails the build.
 */
final class SmokeTest {
    private SmokeTest() {}

    static void run(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            BlockPos base = level.getSharedSpawnPos();
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ());
            Vec3 at = new Vec3(base.getX() + 0.5, y, base.getZ() + 0.5);
            check(GhostTalk.data().lines.containsKey("en") && GhostTalk.data().lines.containsKey("th"), "dialogue languages");
            for (GhostKind k : GhostKind.values()) {
                check(!KillTracks.killsFor(k.n).isEmpty(), "kill tracks for ghost " + k.n);
                check(!GhostTalk.pool("en", k.n, "greet").isEmpty() && !GhostTalk.pool("th", k.n, "greet").isEmpty(), "lines for ghost " + k.n);
            }
            for (GhostKind k : GhostKind.values())
                check(KillTracks.phase("grab", k.n) != null && KillTracks.phase("scream", k.n) != null && KillTracks.phase("hold", k.n) != null, "core kill tracks " + k.n);
            for (String s : HHSounds.NAMES) check(HHSounds.get(s) != null, "sound " + s);
            check(GhostTalk.classify("you are so stupid").intent().equals("taunt"), "intent taunt");
            check(GhostTalk.classify("ขอร้องอย่าทำร้ายฉัน").intent().equals("plead"), "intent plead th");
            check("minecraft:diamond".equals(Curses.resolveItem("give me 5 diamonds")), "resolve item en");
            check("minecraft:iron_pickaxe".equals(Curses.resolveItem("ขออีเต้อเหล็กหน่อย")), "resolve item th");
            aiUnderstanding();
            bloodMoon(server, level, at);
            storyAndSurvival(server, level, at);
            ItemStack cursed = Curses.makeCursed(Items.IRON_SWORD, 1, 3);
            check(Curses.isCursed(cursed), "curse item");

            for (GhostKind k : GhostKind.values()) {
                GhostEntity g = Director.spawnGhost(level, k, at.add(k.n * 3, 0, 0));
                check(g != null, "spawn ghost " + k.n);
                for (int i = 0; i < 40; i++) g.tick();
                g.discard();
            }
            for (StalkerKind k : StalkerKind.values()) {
                StalkerEntity s = HHEntities.stalker(k).create(level);
                check(s != null, "stalker " + k);
                s.moveTo(at.x, at.y, at.z - 4);
                level.addFreshEntity(s);
                s.discard();
            }
            ClosetEntity c = HHEntities.CLOSET.get().create(level);
            check(c != null, "closet");
            c.moveTo(at.x + 2, at.y, at.z + 2, 90, 0);
            level.addFreshEntity(c);
            c.pulseDoor(ClosetEntity.DOOR_OPEN, 5);
            c.discard();
            // a door with boards
            BlockPos door = BlockPos.containing(at).offset(0, 0, 6);
            level.setBlock(door.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState(), 3);
            level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            BoardsEntity b = BoardsEntity.create(level, door, BoardsEntity.KIND_DOOR, 0, 1, Vec3.atBottomCenterOf(door).add(0, 0, 0.2));
            b.addBoard(0);
            b.addBoard(2);
            check(b.hitsToClear(1, false)[1] == 1, "iron board too strong for power 1");
            check(Breach.hitOpening(level, door, 3, false, new Vec3(0, 0, 1)) != Breach.Res.GONE, "hit boarded door");
            b.discard();
            check(new ItemStack(HHItems.FLASHLIGHT.get()).getItem() != Items.AIR, "items registered");
            HollowHours.LOG.info("SMOKE TEST OK");
        } catch (Throwable t) {
            HollowHours.LOG.error("SMOKE TEST FAILED", t);
        }
        server.halt(false);
    }

    /**
     * The AI path against a fake OpenAI-compatible server on localhost: the request reaches /v1/chat/completions, the
     * JSON answer (even wrapped in a code fence) is read, and the item ids it names are checked.
     */
    static void aiUnderstanding() throws Exception {
        java.util.List<String> paths = new java.util.ArrayList<>();
        com.sun.net.httpserver.HttpServer http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", ex -> {
            paths.add(ex.getRequestURI().getPath());
            String req = new String(ex.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (ex.getRequestURI().getPath().endsWith(":generateContent")) {
                // Gemini's native API: key in x-goog-api-key, answer in candidates/content/parts (a thought part is skipped)
                String key = ex.getRequestHeaders().getFirst("x-goog-api-key");
                String text = "AQ.fake-smoke-key".equals(key) && req.contains("systemInstruction")
                        ? "{\\\"intent\\\":\\\"plead\\\",\\\"count\\\":1}" : "nope";
                String gem = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"thinking...\",\"thought\":true},{\"text\":\"" + text + "\"}]}}]}";
                byte[] out = gem.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("content-type", "application/json");
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
                return;
            }
            String content = req.contains("diamond sword")
                    ? "{\"intent\":\"ask_item\",\"topic\":null,\"offer\":null,\"ask\":\"diamond_sword\",\"count\":2}"
                    : "Sure:\n```json\n{\"intent\":\"bargain\",\"offer\":\"minecraft:cooked_beef\",\"offer_name\":\"steak\",\"count\":1}\n```";
            com.google.gson.JsonObject msg = new com.google.gson.JsonObject();
            msg.addProperty("role", "assistant");
            msg.addProperty("content", content);
            com.google.gson.JsonObject choice = new com.google.gson.JsonObject();
            choice.add("message", msg);
            com.google.gson.JsonArray choices = new com.google.gson.JsonArray();
            choices.add(choice);
            com.google.gson.JsonObject body = new com.google.gson.JsonObject();
            body.add("choices", choices);
            byte[] out = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("content-type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        http.start();
        try {
            AiBridge.testSettings = new String[] {"custom", "http://127.0.0.1:" + http.getAddress().getPort(), ""};
            check(AiBridge.enabled(), "ai enabled");
            GhostTalk.Understood a = GhostTalk.understand(null, null, new GhostTalk.Memory(), "give me 2 diamond swords pls").get(15, java.util.concurrent.TimeUnit.SECONDS);
            check(a != null && a.intent().equals("ask_item") && a.count() == 2 && "minecraft:diamond_sword".equals(Curses.checkItemId(a.ask())), "ai ask item: " + a);
            GhostTalk.Understood b = GhostTalk.understand(null, null, new GhostTalk.Memory(), "เอาสเต๊กไปไหม แล้วปล่อยฉันไป").get(15, java.util.concurrent.TimeUnit.SECONDS);
            check(b != null && b.intent().equals("bargain") && "steak".equals(b.offerName()), "ai offer: " + b);
            GhostTalk.Offer o = GhostTalk.offerForId(b.offer());
            check(o != null && o.items().contains(Items.COOKED_BEEF) && o.bonus() > 0, "ai offer item");
            check(GhostTalk.offerForId("diamond").bonus() == 0.20, "ai offer diamond bonus");
            check("!".equals(Curses.checkItemId("command_block")), "ai deny list");
            check(paths.size() == 2 && paths.get(0).equals("/v1/chat/completions"), "ai endpoint " + paths);
            // gemini with a new-style "AQ." key goes to the native endpoint
            String baseWas = AiBridge.geminiBase;
            try {
                AiBridge.testSettings = new String[] {"gemini", "", "AQ.fake-smoke-key"};
                AiBridge.geminiBase = "http://127.0.0.1:" + http.getAddress().getPort() + "/v1beta/models/";
                GhostTalk.Understood gm = GhostTalk.understand(null, null, new GhostTalk.Memory(), "please let me go").get(15, java.util.concurrent.TimeUnit.SECONDS);
                check(gm != null && gm.intent().equals("plead"), "gemini native: " + gm + " " + paths);
                check(paths.get(paths.size() - 1).equals("/v1beta/models/gemini-flash-latest:generateContent"), "gemini path " + paths);
            } finally {
                AiBridge.geminiBase = baseWas;
            }
            HollowHours.LOG.info("[smoke] AI understanding OK: {} | {}", a, b);
        } finally {
            AiBridge.testSettings = null;
            http.stop(0);
        }
    }

    /** A forced blood moon rises at night, makes ghosts 3x deadlier, allows two at once, and sets at dawn. */
    static void bloodMoon(MinecraftServer server, ServerLevel level, Vec3 at) {
        long keep = level.getDayTime();
        level.setDayTime(18000);
        Atmosphere.forceBlood = 1;
        Atmosphere.tick(server, level.getGameTime());
        check(Atmosphere.bloodMoon(), "blood moon rises");
        check(Director.maxAlive() >= 2, "blood moon allows two ghosts");
        GhostEntity g = Director.spawnGhost(level, GhostKind.byN(1), at.add(0, 0, 8));
        check(g != null, "blood moon ghost");
        float dmg = g.attackDamage();
        check(Math.abs(dmg - 3 * com.hollowhours.config.HHConfig.DAMAGE.get()) < 0.01, "blood moon damage x3: " + dmg);
        g.discard();
        Atmosphere.startFog(server, 200, 0.8f);
        check(Atmosphere.fogNow(server) >= 0.8f, "fog");
        Atmosphere.stopFog(server);
        Atmosphere.forceBlood = -1;
        level.setDayTime(6000);
        Atmosphere.tick(server, level.getGameTime());
        check(!Atmosphere.bloodMoon(), "blood moon sets");
        Atmosphere.forceBlood = 0;
        level.setDayTime(keep);
        HollowHours.LOG.info("[smoke] blood moon OK (ghost damage {})", dmg);
    }

    /** The calendar, the phone and charger, thirst, blood colours. */
    static void storyAndSurvival(MinecraftServer server, ServerLevel level, Vec3 at) {
        long keep = level.getDayTime();
        Story.force(server, 0);
        level.setDayTime(18000); // night 1
        check(!Story.active(server), "night 1 is normal");
        level.setDayTime(2 * 24000 + 8000); // day 3, afternoon
        check(!Story.active(server) && Story.alertDue(server), "day 3: alert due, no ghosts yet");
        level.setDayTime(2 * 24000 + 13000); // night 3
        check(Story.active(server), "night 3: the haunting starts");
        Story.force(server, -1);
        check(!Story.active(server), "story held back");
        Story.force(server, 1);
        check(Story.active(server), "story forced on");
        Story.force(server, 0);
        level.setDayTime(keep);
        Story.tick(server, level.getGameTime());

        net.minecraft.world.item.ItemStack phone = com.hollowhours.item.PhoneItem.fresh(level.random);
        float b = com.hollowhours.item.PhoneItem.battery(phone);
        check(b >= 15 && b <= 85, "fresh phone battery " + b);
        check(com.hollowhours.item.PhoneItem.messages(phone).size() == 2, "phone messages");
        com.hollowhours.item.PhoneItem.addWear(phone, 90);
        check(Math.abs(com.hollowhours.item.PhoneItem.drainMult(phone) - (1 + (com.hollowhours.item.PhoneItem.wear(phone)) / 90f)) < 1e-4
                && com.hollowhours.item.PhoneItem.drainMult(phone) >= 2, "battery wear doubles the drain");
        check(new net.minecraft.world.item.ItemStack(HHItems.CHARGER.get()).getMaxDamage() == 3, "charger has 3 charges");

        check(Thirst.quench(net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION), net.minecraft.world.item.alchemy.Potions.WATER)) == 7,
                "water bottle quenches 7");
        check(Thirst.quench(new ItemStack(Items.MILK_BUCKET)) > 0 && Thirst.quench(new ItemStack(Items.BREAD)) == 0, "drinks");

        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        var creeper = net.minecraft.world.entity.EntityType.CREEPER.create(level);
        var golem = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(level);
        var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
        check(BloodFx.color(pig) == 0x8A0606 && BloodFx.color(creeper) == 0x3DAA3D && BloodFx.color(zombie) == 0x5A1A0C && BloodFx.color(golem) < 0,
                "blood colours");
        HollowHours.LOG.info("[smoke] story, phone, thirst and blood OK");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
