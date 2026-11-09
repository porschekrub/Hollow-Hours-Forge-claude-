package com.helperbuddy;

import com.helperbuddy.brain.Bag;
import com.helperbuddy.brain.BuildJob;
import com.helperbuddy.brain.Crafter;
import com.helperbuddy.brain.Tasks;
import com.helperbuddy.blocks.BlockMap;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.gen.ExtraGen;
import com.helperbuddy.gen.Gen;
import com.helperbuddy.gen.HouseGen;
import com.helperbuddy.registry.HBRegistry;
import com.helperbuddy.talk.ItemWords;
import com.helperbuddy.talk.Nlu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.Set;
import java.util.TreeSet;

/**
 * CI server smoke test (-Dhelperbuddy.smoketest=true): checks every generator block maps to a Java block, the
 * built-in brain understands sample sentences, then spawns a helper and lets it build, mine, chop, farm, collect, dig
 * a mine and craft for a while. Any exception or failed check fails the build. Prints "SMOKE TEST OK" and stops.
 */
public final class SmokeTest {
    private SmokeTest() {}

    static MinecraftServer server;
    static HelperEntity h;
    /** the skills: fishing, trading, enchanting, brewing, a machine, end crystals */
    static HelperEntity hf, ht, he, hb, hm, hc;
    static net.minecraft.world.entity.Entity crystal;
    static final boolean[] skillsOk = new boolean[4];

    static String bagText(HelperEntity x) {
        StringBuilder b = new StringBuilder();
        if (!x.tool().isEmpty()) b.append("hand ").append(x.tool().getItem()).append("; ");
        for (int i = 0; i < x.bag.getContainerSize(); i++)
            if (!x.bag.getItem(i).isEmpty()) b.append(x.bag.getItem(i).getCount()).append(' ').append(x.bag.getItem(i).getItem()).append(", ");
        return b.toString();
    }
    static int t;
    static BlockPos base;

    public static void start(MinecraftServer s) {
        server = s;
        try {
            staticChecks();
            ServerLevel level = s.overworld();
            BlockPos sp = level.getSharedSpawnPos();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
            base = new BlockPos(sp.getX(), y, sp.getZ());
            // a server without players stops ticking entities after 15 seconds unless chunks are force-loaded
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) level.setChunkForced((base.getX() >> 4) + dx, (base.getZ() >> 4) + dz, true);
            h = HBRegistry.HELPER.get().create(level);
            check(h != null, "create helper");
            h.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
            HelperEntity.initNew(h, null);
            level.addFreshEntity(h);
            h.setTool(new ItemStack(Items.IRON_PICKAXE));
            for (ItemStack st : new ItemStack[] {new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_HOE), new ItemStack(Items.TORCH, 64),
                    new ItemStack(Items.BREAD, 16), new ItemStack(Items.COAL, 16), new ItemStack(Items.WHEAT_SEEDS, 16), new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.RAW_IRON, 4)})
                Bag.add(h, st);
            Gen.Opts o = new Gen.Opts();
            o.type = "standard";
            o.size = 0;
            o.floors = 1;
            o.speed = 3;
            BuildJob.randomize(o, h.getRandom());
            BuildJob.Site site = h.brain().build().prepareAt(level, base.offset(0, 0, 3), Direction.NORTH, o, null, "test");
            HelperBuddy.LOG.info("[smoke] build site: ok={} {}", site.ok(), site.en());
            if (site.ok()) h.brain().build().start(site.job());
            MinecraftForge.EVENT_BUS.addListener(SmokeTest::tick);
        } catch (Throwable e) {
            HelperBuddy.LOG.error("SMOKE TEST FAILED", e);
            s.halt(false);
        }
    }

    static void staticChecks() {
        // every block of every building exists in Java
        Set<String> missing = new TreeSet<>();
        int plans = 0, blocks = 0;
        for (String[] type : ExtraGen.TYPES) {
            int variants = Math.max(1, ExtraGen.variantCount(type[0]));
            for (int v = 0; v < variants; v++)
                for (int wood = 0; wood < 9; wood += 4)
                    for (int mat = 0; mat < 10; mat += 3) {
                        Gen.Opts o = new Gen.Opts();
                        o.type = type[0];
                        o.variant = v;
                        o.model = v;
                        o.wood = wood;
                        o.mat = mat;
                        o.pool = true;
                        o.porch = true;
                        o.chimney = true;
                        o.light = true;
                        o.furniture = true;
                        for (int style = 0; style < (type[0].equals("standard") ? 4 : 1); style++) {
                            o.style = style;
                            Gen.Plan p = HouseGen.generate(o);
                            plans++;
                            for (Gen.Blk b : p.blocks) {
                                blocks++;
                                if (BlockMap.state(b, Direction.NORTH) == null) missing.add(b.id);
                                String it = b.placeItem();
                                if (it != null && BlockMap.item(it) == null) missing.add("item " + it);
                            }
                            if (p.fillBlock != null && BlockMap.state(p.fillBlock, null, Direction.NORTH) == null) missing.add("fill " + p.fillBlock);
                        }
                    }
        }
        HelperBuddy.LOG.info("[smoke] {} plans, {} blocks checked", plans, blocks);
        check(missing.isEmpty(), "unknown blocks: " + missing);
        // the built-in brain
        check(Nlu.parse("ไปตัดไม้").k.equals("chop"), "nlu chop th");
        check(Nlu.parse("chop wood").k.equals("chop"), "nlu chop en");
        check(Nlu.parse("ขุดเพชร").k.equals("mine"), "nlu mine th");
        check(Nlu.parse("build a hotel").k.equals("house") && "hotel".equals(Nlu.parse("build a hotel").house.o.type), "nlu hotel");
        check(Nlu.parse("สร้างคุก").k.equals("house"), "nlu prison th");
        check(Nlu.parse("เก็บไม้ไผ่ 64").k.equals("gather"), "nlu bamboo");
        check(Nlu.splitChain("ตัดไม้ 20 ท่อน แล้วขุดหิน 64 ก้อน แล้วเอามาให้").size() == 3, "nlu chain");
        check("minecraft:diamond_sword".equals(ItemWords.resolve("diamond sword")), "item words en");
        check("minecraft:diamond_sword".equals(ItemWords.resolve("ดาบเพชร")), "item words th");
        check(Crafter.craftable(server.overworld(), Items.CRAFTING_TABLE), "crafting recipes");
        // understanding amounts, places, memory and personality
        Nlu.Intent meat = Nlu.parse("หาเนื้อมา 64 อัน");
        check(meat.k.equals("findFood") && meat.meat && Integer.valueOf(64).equals(Nlu.extractQty(Nlu.norm("หาเนื้อมา 64 อัน")).n()), "nlu 64 meat");
        check(Nlu.parse("get 20 beef").meat && !Nlu.parse("hunt 3 cows").meat, "nlu beef vs cows");
        check(Nlu.parse("get me 64 meat").k.equals("findFood") && Nlu.parse("get me 64 meat").meat && Nlu.parse("เอาหมูมา 20 ชิ้น").meat
                && !Nlu.parse("ฆ่าวัว 3 ตัว").meat, "nlu meat pieces");
        check(Nlu.parse("ไปหมู่บ้านกัน").lead && Nlu.parse("มีหมู่บ้านแถวนี้ไหม").k.equals("locate") && Nlu.parse("ไปสร้างบ้านในป่า").k.equals("house")
                && Nlu.parse("go to the nearest village").lead && !Nlu.parse("หมู่บ้านไปทางไหน").lead, "nlu go to a place");
        check(Nlu.parse("หมู่บ้านอยู่ไหน").k.equals("locate") && Nlu.parse("หมู่บ้านอยู่ไหน").place.kind().equals("structure"), "nlu where is the village");
        check(Nlu.parse("take me to a desert").lead && Nlu.parse("take me to a desert").place.kind().equals("biome"), "nlu take me to");
        check(Nlu.parse("สร้างบ้าน").k.equals("house") && Nlu.parse("จำไว้ว่าฉันชอบแมว").k.equals("remember") && Nlu.parse("พูดหยาบได้").k.equals("persona"), "nlu new intents");
        check(com.helperbuddy.talk.Persona.th(com.helperbuddy.talk.Persona.Style.SASSY, "ผมไปแล้วนะครับ", new java.util.Random(1)).contains("ข้า"), "persona sassy");
        check(com.helperbuddy.talk.Persona.th(com.helperbuddy.talk.Persona.Style.RUDE, "คุณต้องการอะไรครับ", new java.util.Random(1)).contains("มึง"), "persona rude");
        check(com.helperbuddy.talk.Memory.selfTest(), "memory file");
        check(Nlu.parse("ตกปลา 10 ตัว").k.equals("fish") && Nlu.parse("go fishing").k.equals("fish"), "nlu fishing");
        check(Nlu.parse("แลกมรกตกับชาวบ้าน").k.equals("trade") && Nlu.parse("buy bread from villagers").k.equals("trade"), "nlu trading");
        check(Nlu.parse("ร่ายมนตร์ดาบ").k.equals("enchant") && "sword".equals(Nlu.parse("enchant my sword").text) && !Nlu.parse("คราฟโต๊ะร่ายมนตร์").k.equals("enchant"), "nlu enchanting");
        check(Nlu.parse("ต้มยาวิ่งเร็ว").k.equals("brew") && "swiftness".equals(Nlu.parse("ต้มยาวิ่งเร็ว").text) && "long_".equals(Nlu.parse("brew a long potion of swiftness").name), "nlu brewing");
        check(Nlu.parse("put raw iron in the furnace").k.equals("machine") && Nlu.parse("ใส่เหล็กดิบในเตาเผา").k.equals("machine"), "nlu machines");
        check(Nlu.parse("ไปเนเธอร์").k.equals("locate") && Nlu.parse("ไปเนเธอร์").place.kind().equals("portal") && Nlu.parse("take me to the end").lead, "nlu nether / end");
        // voice orders: the recording as a WAV file, the request to the AI, what it heard
        byte[] wav = com.helperbuddy.talk.VoiceIn.wav(new byte[] {(byte) 0xFF, (byte) 0x80, 0x00, 0x7F}, 16000);
        check(wav.length == 44 + 8 && new String(wav, 0, 4).equals("RIFF") && new String(wav, 8, 4).equals("WAVE"), "voice wav");
        check(com.helperbuddy.talk.AiBridge.heardRequest(wav).toString().contains("\"mime_type\":\"audio/wav\""), "voice request");
        check(com.helperbuddy.talk.AiBridge.heardText("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\\\"ตกปลา  3 ตัว\\\"\\n\"}]}}]}").equals("ตกปลา 3 ตัว"),
                "voice heard text");
        check(com.helperbuddy.talk.AiBridge.heardText("{\"candidates\":[]}").isEmpty(), "voice heard nothing");
        check(Nlu.parse("ฆ่ามังกร").k.equals("boss") && Nlu.parse("kill the dragon").k.equals("boss"), "nlu boss");
        check("smelter".equals(Nlu.parse("build an auto smelter").house.o.type) && "autodoor".equals(Nlu.parse("สร้างประตูอัตโนมัติ").house.o.type), "nlu redstone builds");
        byte[] pcm = new byte[] {0, 0, (byte) 0xE8, 0x03, 0x18, (byte) 0xFC, (byte) 0xFF, 0x7F};
        byte[] u = com.helperbuddy.talk.VoiceTts.toUlaw(pcm);
        check(u.length == 4 && Math.abs(com.helperbuddy.talk.VoiceTts.fromUlaw(u[1]) - 1000) < 40 && Math.abs(com.helperbuddy.talk.VoiceTts.fromUlaw(u[2]) + 1000) < 40, "mu-law");
    }

    static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || h == null) return;
        t++;
        try {
            if (t == 300) {
                HelperBuddy.LOG.info("[smoke] build progress {}% (placed blocks)", h.brain().build().progress());
                check(!h.brain().build().active() || h.brain().build().progress() > 0, "build made progress");
                h.brain().leaveJob();
                h.brain().build().cancel();
                h.setMode(Mode.MINE);
            }
            if (t == 360) h.setMode(Mode.CHOP);
            if (t == 420) {
                h.brain().work().setGather(new String[] {"sand", "dirt"});
                h.setMode(Mode.GATHER);
            }
            if (t == 480) h.setMode(Mode.FOOD);
            if (t == 540) h.setMode(Mode.FARM);
            if (t == 600) {
                Nlu.MineOpts mo = new Nlu.MineOpts();
                mo.depth = 4;
                mo.length = 6;
                mo.branch = 2;
                h.brain().dig().start(mo, Direction.EAST, h.blockPosition());
                h.setMode(Mode.DIG);
            }
            if (t == 700) h.brain().tasks().start(Tasks.craft("minecraft:crafting_table", 1));
            if (t == 760) h.brain().tasks().start(Tasks.smelt("iron", 4));
            if (t % 100 == 50) HelperBuddy.LOG.info("[smoke] t={} mode {} status '{}' tasks {} bag {}/36", t, h.mode(), h.status(), h.brain().tasks().active(), Bag.usedSlots(h));
            if (t == 790) {
                // finding a village (the flat test world has some) and a biome it doesn't have
                long t0 = System.currentTimeMillis();
                var v = com.helperbuddy.brain.Navigator.findStructure(server.overworld(), base, new String[] {"#minecraft:village"});
                HelperBuddy.LOG.info("[smoke] nearest village: {} ({} ms)", v, System.currentTimeMillis() - t0);
                check(v != null, "found a village");
                check(com.helperbuddy.brain.Navigator.findBiome(server.overworld(), base, new String[] {"minecraft:desert"}) == null, "no desert in a flat world");
            }
            if (t == 800) {
                check(h.isAlive(), "helper alive");
                StringBuilder bag = new StringBuilder();
                for (int i = 0; i < h.bag.getContainerSize(); i++)
                    if (!h.bag.getItem(i).isEmpty()) bag.append(h.bag.getItem(i).getCount()).append(' ').append(h.bag.getItem(i).getItem()).append(", ");
                HelperBuddy.LOG.info("[smoke] bag: {}", bag);
                check(Bag.count(h, Items.IRON_INGOT) > 0 || Bag.count(h, Items.CRAFTING_TABLE) > 0, "crafting / smelting produced something");
                HelperBuddy.LOG.info("[smoke] mode {} status '{}' bag {}/36", h.mode(), h.status(), Bag.usedSlots(h));
                startSkills(server.overworld());
            }
            if (t == 820) {
                BlockPos portal = com.helperbuddy.brain.Navigator.findPortal(server.overworld(), base, 128);
                HelperBuddy.LOG.info("[smoke] nearest nether portal: {}", portal);
                check(portal != null, "found the nether portal");
            }
            if (t > 810 && t % 100 == 0)
                for (HelperEntity x : new HelperEntity[] {hf, ht, he, hb, hm, hc})
                    if (x != null) HelperBuddy.LOG.info("[smoke] skill helper {}: mode {} status '{}' task {}", x.helperName(), x.mode(), x.status(), x.brain().tasks().active());
            if (t == 1200) {
                // log everything first, then check (one run shows every skill that broke)
                int caught = Bag.count(hf, st -> !st.isEmpty() && !(st.getItem() instanceof net.minecraft.world.item.FishingRodItem));
                HelperBuddy.LOG.info("[smoke] fishing: {} things caught", caught);
                HelperBuddy.LOG.info("[smoke] trading: {} emeralds, {} wheat left", Bag.count(ht, Items.EMERALD), Bag.count(ht, Items.WHEAT));
                HelperBuddy.LOG.info("[smoke] enchanting: {} enchanted books, bag {}", Bag.count(he, Items.ENCHANTED_BOOK), bagText(he));
                HelperBuddy.LOG.info("[smoke] end crystal alive: {}, arrows left {}", crystal.isAlive(), Bag.count(hc, Items.ARROW));
                skillsOk[0] = caught > 0;
                skillsOk[1] = Bag.count(ht, Items.EMERALD) > 0;
                skillsOk[2] = Bag.count(he, Items.ENCHANTED_BOOK) > 0;
                skillsOk[3] = !crystal.isAlive();
            }
            if (t == 1900) {
                int swift = Bag.count(hb, st -> com.helperbuddy.compat.Compat.potionId(st).equals("minecraft:swiftness"));
                HelperBuddy.LOG.info("[smoke] brewing: {} potions of swiftness, bag {}", swift, bagText(hb));
                HelperBuddy.LOG.info("[smoke] machine: {} iron ingots from the furnace, bag {}", Bag.count(hm, Items.IRON_INGOT), bagText(hm));
                check(skillsOk[0], "fishing caught something");
                check(skillsOk[1], "trading with a villager");
                check(skillsOk[2], "enchanting a book");
                check(skillsOk[3], "shooting the end crystal");
                check(swift > 0, "brewing a potion of swiftness");
                check(Bag.count(hm, Items.IRON_INGOT) > 0, "running a furnace (a machine)");
                HelperBuddy.LOG.info("SMOKE TEST OK");
                h = null;
                server.halt(false);
            }
        } catch (Throwable ex) {
            HelperBuddy.LOG.error("SMOKE TEST FAILED", ex);
            h = null;
            server.halt(false);
        }
    }

    static HelperEntity spawnHelper(ServerLevel level, BlockPos at, ItemStack... bag) {
        HelperEntity x = HBRegistry.HELPER.get().create(level);
        x.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        HelperEntity.initNew(x, null);
        level.addFreshEntity(x);
        for (ItemStack st : bag) Bag.add(x, st);
        return x;
    }

    /** One helper for each skill, each with what it needs, all at once. */
    static void startSkills(ServerLevel level) {
        // fishing at a little pond
        BlockPos fp = base.offset(20, 0, 20);
        for (int dx = 2; dx <= 4; dx++)
            for (int dz = -1; dz <= 1; dz++) level.setBlockAndUpdate(fp.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        hf = spawnHelper(level, fp, new ItemStack(Items.FISHING_ROD));
        net.minecraft.nbt.CompoundTag fish = Tasks.fish(2);
        fish.putBoolean("fast", true);
        hf.brain().tasks().start(fish);
        // trading with a farmer who buys wheat
        BlockPos tp = base.offset(-20, 0, 20);
        ht = spawnHelper(level, tp, new ItemStack(Items.WHEAT, 40));
        net.minecraft.world.entity.npc.Villager v = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        v.moveTo(tp.getX() + 2.5, tp.getY(), tp.getZ() + 0.5, 0, 0);
        v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        v.setNoAi(true);
        level.addFreshEntity(v);
        //#if MC>=12100
        //$$ v.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 12, 2, 0.05f));
        //#else
        v.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(new ItemStack(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 12, 2, 0.05f));
        //#endif
        ht.brain().tasks().start(Tasks.trade("minecraft:emerald", null, 1));
        // enchanting a book
        BlockPos ep = base.offset(20, 0, -20);
        level.setBlockAndUpdate(ep.offset(2, 0, 0), net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState());
        he = spawnHelper(level, ep, new ItemStack(Items.BOOK), new ItemStack(Items.LAPIS_LAZULI, 3));
        net.minecraft.nbt.CompoundTag en = Tasks.enchant(null, "book");
        en.putBoolean("free", true);
        he.brain().tasks().start(en);
        // brewing swiftness: nether wart, then sugar
        BlockPos bp = base.offset(-20, 0, -20);
        level.setBlockAndUpdate(bp.offset(2, 0, 0), net.minecraft.world.level.block.Blocks.BREWING_STAND.defaultBlockState());
        ItemStack water = com.helperbuddy.compat.Compat.potion(Items.POTION, "minecraft:water");
        hb = spawnHelper(level, bp, water.copy(), water.copy(), water.copy(), new ItemStack(Items.NETHER_WART), new ItemStack(Items.SUGAR), new ItemStack(Items.BLAZE_POWDER, 2));
        hb.brain().tasks().start(Tasks.brew("minecraft:swiftness", null, 3));
        // a furnace run as a machine
        BlockPos mp = base.offset(0, 0, -24);
        level.setBlockAndUpdate(mp.offset(2, 0, 0), net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
        hm = spawnHelper(level, mp, new ItemStack(Items.RAW_IRON, 2), new ItemStack(Items.COAL, 4));
        hm.brain().tasks().start(Tasks.machine("minecraft:raw_iron", 2, "furnace", null, "run"));
        // an end crystal to shoot
        BlockPos cp = base.offset(0, 0, 14);
        hc = spawnHelper(level, cp, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 16));
        crystal = new net.minecraft.world.entity.boss.enderdragon.EndCrystal(level, cp.getX() + 0.5, cp.getY() + 3, cp.getZ() + 10.5);
        ((net.minecraft.world.entity.boss.enderdragon.EndCrystal) crystal).setShowBottom(false);
        level.addFreshEntity(crystal);
        hc.brain().combat().startBoss();
        // a nether portal to find
        BlockPos pp = base.offset(-26, 0, 0);
        for (int x = 0; x <= 3; x++)
            for (int y = 0; y <= 4; y++) {
                boolean frame = x == 0 || x == 3 || y == 0 || y == 4;
                if (frame) level.setBlockAndUpdate(pp.offset(x, y, 0), net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState());
            }
        for (int x = 1; x <= 2; x++)
            for (int y = 1; y <= 3; y++)
                level.setBlock(pp.offset(x, y, 0), net.minecraft.world.level.block.Blocks.NETHER_PORTAL.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS, Direction.Axis.X), 2 | 16);
        HelperBuddy.LOG.info("[smoke] skills started");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
