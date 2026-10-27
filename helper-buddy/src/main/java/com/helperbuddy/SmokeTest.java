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
            if (t == 800) {
                check(h.isAlive(), "helper alive");
                check(Bag.count(h, Items.IRON_INGOT) > 0 || Bag.count(h, Items.CRAFTING_TABLE) > 0, "crafting / smelting produced something");
                HelperBuddy.LOG.info("[smoke] mode {} status '{}' bag {}/36", h.mode(), h.status(), Bag.usedSlots(h));
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

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
