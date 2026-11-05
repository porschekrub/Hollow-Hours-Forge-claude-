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
            physicsAndFun(server, level, at);
            rescue(server);
            check(com.hollowhours.phone.PhoneChat.category("ช่วยด้วย ผีมา").equals("scared") && com.hollowhours.phone.PhoneChat.category("love you").equals("love")
                    && com.hollowhours.phone.PhoneChat.category("แม่อยู่ไหน").equals("where") && com.hollowhours.phone.PhoneChat.category("hello").equals("greet"), "chat categories");
            HollowHours.LOG.info("[smoke] {}", com.hollowhours.grave.GraveSmoke.run(server));
            ItemStack cursed = Curses.makeCursed(Items.IRON_SWORD, 1, 3);
            check(Curses.isCursed(cursed), "curse item");

            for (GhostKind k : GhostKind.values()) {
                GhostEntity g = Director.spawnGhost(level, k, at.add(k.n * 3, 0, 0));
                check(g != null, "spawn ghost " + k.n);
                for (int i = 0; i < 40; i++) g.tick();
                g.discard();
            }
            combat(level, at);
            sips(level, at);
            endgame(server, level, at);
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
     * The last chapter end to end: the five kept down, the map, the mountain built (somewhere out of the way), the
     * boss rising and beaten, the rift, and the world at peace afterwards. Everything is reset at the end.
     */
    static void endgame(MinecraftServer server, ServerLevel level, Vec3 at) {
        long t0 = System.currentTimeMillis();
        com.hollowhours.endgame.Endgame.reset(server);
        net.minecraftforge.common.util.FakePlayer fp = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        fp.moveTo(at.x, at.y, at.z);
        // the hunt: every one knocked out stays down; the last one brings the map
        com.hollowhours.endgame.Endgame.data(server).stage = com.hollowhours.endgame.Endgame.HUNT;
        java.util.List<GhostEntity> five = new java.util.ArrayList<>();
        for (GhostKind k : GhostKind.values()) {
            GhostEntity g = Director.spawnGhost(level, k, at.add(k.n * 3, 0, 5));
            check(g != null, "endgame ghost " + k.n);
            five.add(g);
            GhostBrain b = g.brain();
            for (int i = 0; i < 300 && b.mode != GhostBrain.Mode.KO; i++) com.hollowhours.combat.GhostCombat.poiseHit(g, fp, 20, false);
            check(b.mode == GhostBrain.Mode.KO, "endgame ghost " + k.n + " knocked out");
            b.timers.put("koUntil", b.now());
            com.hollowhours.combat.GhostCombat.tickKnockedOut(b, b.now());
            check(b.mode == GhostBrain.Mode.KO, "endgame ghost " + k.n + " stays down");
        }
        check(com.hollowhours.endgame.Endgame.data(server).down == 0b111110, "all five counted");
        com.hollowhours.endgame.Endgame.toMap(server);
        check(com.hollowhours.endgame.Endgame.stage(server) == com.hollowhours.endgame.Endgame.MAP, "map stage");
        for (GhostEntity g : five) g.discard();
        // the mountain, 300 blocks away so it doesn't spoil the other tests
        BlockPos site = BlockPos.containing(at).offset(300, 0, 40);
        com.hollowhours.endgame.Endgame.setSite(server, site);
        ItemStack map = com.hollowhours.endgame.Endgame.makeMap(level, site);
        check(com.hollowhours.endgame.Endgame.isMountainMap(map), "mountain map");
        for (int cx = (site.getX() - 72) >> 4; cx <= (site.getX() + 72) >> 4; cx++)
            for (int cz = (site.getZ() - 72) >> 4; cz <= (site.getZ() + 72) >> 4; cz++) level.getChunk(cx, cz);
        com.hollowhours.endgame.Arena.planNow(level);
        int ops = com.hollowhours.endgame.Arena.queued();
        while (com.hollowhours.endgame.Arena.building()) com.hollowhours.endgame.Arena.serverTick(server);
        BlockPos center = com.hollowhours.endgame.Endgame.center(server);
        check(center != null && com.hollowhours.endgame.Endgame.data(server).built == 2, "mountain built");
        check(!level.getBlockState(center.below()).isAir() && level.getBlockState(center.above(5)).isAir(), "cavern floor and air");
        // the boss: rises, takes the beating, sinks; the rift opens; stepping in ends the story
        com.hollowhours.endgame.Arena.spawnBoss(level, Vec3.atBottomCenterOf(center));
        var bosses = level.getEntitiesOfClass(com.hollowhours.endgame.WeepingEntity.class, new net.minecraft.world.phys.AABB(center).inflate(8));
        check(bosses.size() == 1, "boss spawned");
        var boss = bosses.get(0);
        for (int i = 0; i < 90; i++) boss.tick();
        check(boss.anim() == com.hollowhours.endgame.WeepingEntity.A_IDLE && Math.abs(boss.getMaxHealth() - com.hollowhours.config.HHConfig.BOSS_HEALTH.get()) < 1, "boss risen (" + boss.getMaxHealth() + ")");
        int hits = 0;
        while (boss.isAlive() && hits < 500) {
            boss.invulnerableTime = 0;
            boss.hurt(level.damageSources().playerAttack(fp), 25);
            hits++;
        }
        check(!boss.isAlive() && com.hollowhours.endgame.Endgame.stage(server) == com.hollowhours.endgame.Endgame.RIFT, "boss beaten (" + hits + " hits)");
        for (int i = 0; i < 150 && !boss.isRemoved(); i++) boss.tick();
        check(boss.isRemoved(), "boss gone after sinking");
        com.hollowhours.endgame.RiftEntity rift = HHEntities.RIFT.get().create(level);
        check(rift != null, "rift");
        rift.moveTo(center.getX() + 0.5, center.getY() + 1.2, center.getZ() + 0.5, 0, 0);
        level.addFreshEntity(rift);
        com.hollowhours.endgame.Endgame.enterRift(fp);
        check(com.hollowhours.endgame.Endgame.peaceful(server) && !Story.active(server), "peace after the rift");
        rift.discard();
        fp.setInvulnerable(false);
        HollowHours.LOG.info("[smoke] endgame OK: mountain {} blocks, boss beaten in {} hits, {} ms", ops, hits, System.currentTimeMillis() - t0);
        com.hollowhours.endgame.Endgame.reset(server);
    }

    /** Drinking from water: still, flowing, under a lily pad, past tall grass, a cauldron; not through stone. */
    static void sips(ServerLevel level, Vec3 at) {
        net.minecraftforge.common.util.FakePlayer fp = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        BlockPos o = BlockPos.containing(at).offset(-30, 0, -30);
        String[] cases = {"still", "flowing", "lily", "grass", "cauldron", "stone"};
        for (int i = 0; i < cases.length; i++) {
            BlockPos b = o.offset(i * 4, 0, 0);
            for (BlockPos q : BlockPos.betweenClosed(b.offset(-1, -2, -1), b.offset(1, 3, 3))) level.setBlock(q, Blocks.AIR.defaultBlockState(), 2 | 16);
            for (BlockPos q : BlockPos.betweenClosed(b.offset(-1, -2, -1), b.offset(1, -2, 3))) level.setBlock(q, Blocks.STONE.defaultBlockState(), 2 | 16);
            level.setBlock(b.below(), Blocks.STONE.defaultBlockState(), 2 | 16); // stand here, look at the block 2 ahead and 1 down
            BlockPos w = b.offset(0, -1, 2);
            switch (cases[i]) {
                case "still" -> level.setBlock(w, Blocks.WATER.defaultBlockState(), 2 | 16);
                case "flowing" -> level.setBlock(w, net.minecraft.world.level.material.Fluids.FLOWING_WATER.defaultFluidState().createLegacyBlock()
                        .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 4), 2 | 16);
                case "lily" -> {
                    level.setBlock(w, Blocks.WATER.defaultBlockState(), 2 | 16);
                    level.setBlock(w.above(), Blocks.LILY_PAD.defaultBlockState(), 2 | 16);
                }
                case "grass" -> {
                    level.setBlock(w, Blocks.WATER.defaultBlockState(), 2 | 16);
                    level.setBlock(b.offset(0, 0, 1), Blocks.GRASS.defaultBlockState(), 2 | 16);
                    level.setBlock(b.offset(0, -1, 1), Blocks.DIRT.defaultBlockState(), 2 | 16);
                }
                case "cauldron" -> level.setBlock(w, Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3), 2 | 16);
                default -> {
                    level.setBlock(w, Blocks.WATER.defaultBlockState(), 2 | 16);
                    level.setBlock(b.offset(0, 0, 1), Blocks.STONE.defaultBlockState(), 2 | 16);
                    level.setBlock(b.offset(0, 1, 1), Blocks.STONE.defaultBlockState(), 2 | 16);
                }
            }
            Vec3 eye = new Vec3(b.getX() + 0.5, b.getY() + 1.62, b.getZ() + 0.5);
            Vec3 tgt = Vec3.atCenterOf(w).add(0, 0.3, 0);
            double dx = tgt.x - eye.x, dy = tgt.y - eye.y, dz = tgt.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            fp.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, yaw, pitch);
            BlockPos found = Thirst.waterLookedAt(fp, 5.0);
            boolean ok = cases[i].equals("stone") ? found == null : found != null;
            check(ok, "sip water (" + cases[i] + "): " + found);
        }
        HollowHours.LOG.info("[smoke] drinking water OK (still, flowing, lily pad, past grass, cauldron; not through stone)");
    }

    /** Weapons knock ghosts out (and they get up again), armour and strength numbers, a gun reloads from ammo. */
    static void combat(ServerLevel level, Vec3 at) {
        net.minecraftforge.common.util.FakePlayer fp = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        fp.moveTo(at.x, at.y, at.z);
        GhostEntity g = Director.spawnGhost(level, GhostKind.values()[2], at.add(4, 0, 0));
        check(g != null, "combat ghost");
        GhostBrain b = g.brain();
        for (int i = 0; i < 40; i++) g.tick();
        float max = com.hollowhours.combat.GhostCombat.maxPoise(b);
        int hits = 0;
        while (b.mode != GhostBrain.Mode.KO && hits < 200) {
            com.hollowhours.combat.GhostCombat.poiseHit(g, fp, 9, true);
            hits++;
        }
        check(b.mode == GhostBrain.Mode.KO && g.isKnockedOut(), "ghost knocked out (" + hits + " pistol hits, poise " + max + ")");
        check(!KillSequence.begin(b, fp), "a knocked-out ghost can't grab");
        b.timers.put("koUntil", b.now());
        com.hollowhours.combat.GhostCombat.tickKnockedOut(b, b.now()); // (no players here: a ticking ghost would just vanish)
        check(b.mode != GhostBrain.Mode.KO && !g.isKnockedOut(), "ghost wakes up");
        HollowHours.LOG.info("[smoke] combat: {} pistol hits knock ghost {} out (poise {})", hits, g.kind.n, max);
        g.discard();
        // armour set bonus, strength
        for (net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> it : HHItems.ARMOR)
            if (it.get() instanceof com.hollowhours.combat.MilitaryArmor.Piece pc && pc.mat == com.hollowhours.combat.MilitaryArmor.Mat.HUNTER)
                fp.setItemSlot(pc.getEquipmentSlot(), new ItemStack(pc));
        int defense = 0; // (attributes from worn armour only update when the player ticks)
        for (ItemStack worn : fp.getArmorSlots()) if (worn.getItem() instanceof net.minecraft.world.item.ArmorItem ai) defense += ai.getDefense();
        check(Math.abs(com.hollowhours.combat.MilitaryArmor.ghostCut(fp) - 0.30f) < 0.01f && defense >= 20, "hunter armour set (" + defense + ", cut " + com.hollowhours.combat.MilitaryArmor.ghostCut(fp) + ")");
        com.hollowhours.combat.Fitness.setStrength(fp, 50);
        check(Math.abs(com.hollowhours.combat.Fitness.strength(fp) - 50) < 0.01f
                && fp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getValue() >= 23.9, "strength gives health");
        // a pistol reloads from the inventory
        ItemStack gun = new ItemStack(HHItems.PISTOL.get());
        fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        fp.getInventory().add(new ItemStack(HHItems.PISTOL_AMMO.get(), 20));
        check(((com.hollowhours.combat.GunItem) gun.getItem()).startReload(fp, gun), "reload starts");
        gun.getTag().putLong("reloadAt", level.getGameTime());
        gun.inventoryTick(level, fp, fp.getInventory().selected, true);
        check(com.hollowhours.combat.GunItem.ammo(gun) == 12 && com.hollowhours.combat.GunItem.ammoIn(fp, HHItems.PISTOL_AMMO.get()) == 8, "reload takes ammo");
        for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values()) fp.setItemSlot(s, ItemStack.EMPTY);
        fp.getInventory().clearContent();
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
                check(paths.get(paths.size() - 1).equals("/v1beta/models/gemini-flash-lite-latest:generateContent"), "gemini path " + paths);
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
        com.hollowhours.item.PhoneItem.addWear(phone, com.hollowhours.item.PhoneItem.WEAR_DOUBLE);
        check(Math.abs(com.hollowhours.item.PhoneItem.drainMult(phone) - (1 + (com.hollowhours.item.PhoneItem.wear(phone)) / com.hollowhours.item.PhoneItem.WEAR_DOUBLE)) < 1e-4
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

    static void physicsAndFun(MinecraftServer server, ServerLevel level, Vec3 at) {
        for (String r : new String[] {"swing", "slide", "trampoline", "basketball_hoop", "soccer_goal", "arcade", "tv", "basketball", "soccer_ball"})
            check(server.getRecipeManager().byKey(HollowHours.id(r)).isPresent(), "recipe " + r);
        for (var b : com.hollowhours.registry.HHBlocks.ALL)
            check(server.getLootData().getLootTable(b.get().getLootTable()) != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "loot table " + b.getId());
        check(com.hollowhours.physics.Physics.oreKind(net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE.defaultBlockState())
                .equals(com.hollowhours.physics.Physics.oreKind(net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState())), "deepslate ore is the same vein");

        // a basketball dropped 4 blocks onto stone bounces back up, and never sinks into the floor
        net.minecraft.core.BlockPos floor = net.minecraft.core.BlockPos.containing(at).above(30);
        for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(floor.offset(-3, 0, -3), floor.offset(3, 0, 3)))
            level.setBlock(b, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        var ball = com.hollowhours.registry.HHEntities.BALL.get().create(level);
        check(ball != null, "ball entity");
        ball.setKind(com.hollowhours.fun.BallEntity.BASKETBALL);
        double top = floor.getY() + 1;
        ball.moveTo(floor.getX() + 0.5, top + 4, floor.getZ() + 0.5, 0, 0);
        level.addFreshEntity(ball);
        boolean landed = false;
        double low = 99, rebound = 0;
        for (int i = 0; i < 90; i++) {
            ball.tick();
            low = Math.min(low, ball.getY() - top);
            if (ball.onGround()) landed = true;
            else if (landed) rebound = Math.max(rebound, ball.getY() - top);
        }
        ball.discard();
        for (net.minecraft.core.BlockPos b : net.minecraft.core.BlockPos.betweenClosed(floor.offset(-3, 0, -3), floor.offset(3, 0, 3)))
            level.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        check(landed && low > -0.01 && rebound > 1.2 && rebound < 4, String.format("ball bounce (lowest %.2f, rebound %.2f)", low, rebound));
        HollowHours.LOG.info("[smoke] physics and playground OK (ball rebound {} blocks)", String.format("%.2f", rebound));
    }

    /** The haven exists, is rich and safe; the rescue is planned 35-100 days out; the clock reads right. */
    static void rescue(MinecraftServer server) {
        ServerLevel haven = server.getLevel(com.hollowhours.rescue.Rescue.HAVEN);
        check(haven != null, "the haven dimension");
        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (int i = 0; i < 64; i++) {
            BlockPos p = new BlockPos((i % 8 - 4) * 400, 70, (i / 8 - 4) * 400);
            var b = haven.getBiome(p);
            String id = b.unwrapKey().map(k -> k.location().toString()).orElse("?");
            seen.add(id);
            check(id.startsWith("hollowhours:haven_"), "haven biome at " + p + ": " + id);
            check(b.value().getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.MONSTER).isEmpty(), "no monsters in " + id);
        }
        check(seen.size() >= 3, "haven has several biomes: " + seen);
        BlockPos spot = com.hollowhours.rescue.Rescue.havenSpot(haven);
        check(haven.getBlockState(spot.below()).isSolid() && haven.getFluidState(spot.below()).isEmpty(), "haven landing spot on dry land " + spot);
        check("06:00".equals(com.hollowhours.rescue.Rescue.clock(0)) && "00:00".equals(com.hollowhours.rescue.Rescue.clock(18000))
                && "12:30".equals(com.hollowhours.rescue.Rescue.clock(6500)), "rescue clock");
        long now = server.overworld().getDayTime();
        for (int i = 0; i < 20; i++) {
            com.hollowhours.rescue.Rescue.schedule(server);
            long days = com.hollowhours.rescue.Rescue.nextArrival(server) / 24000L - now / 24000L;
            check(days >= 35 && days <= 100, "rescue in " + days + " days");
        }
        com.hollowhours.rescue.Rescue.cancelPlan(server);
        HollowHours.LOG.info("[smoke] rescue OK: haven biomes {}, landing at {}", seen, spot);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
