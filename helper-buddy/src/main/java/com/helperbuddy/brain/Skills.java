package com.helperbuddy.brain;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.compat.Compat;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The helper's skills beyond digging and building: fishing, trading with villagers, enchanting, brewing and running
 * machines (furnaces, other mods' machines). Each runs as a one-off task (Tasks) and tells the owner how it went.
 */
final class Skills {
    private Skills() {}

    // ================================================================== common
    static String id(Item i) {
        return BuiltInRegistries.ITEM.getKey(i).toString();
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(HelperBuddy.rl(id));
    }

    static String name(Item i) {
        return new ItemStack(i).getHoverName().getString();
    }

    /** Walks within `dist` of a block (teleports when it can't get there); true when there. */
    static boolean near(Tasks t, BlockPos p, double dist) {
        HelperEntity h = t.h;
        if (h.getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= dist) {
            t.b.mover().stop();
            t.b.mover().look(p);
            return true;
        }
        Mover.Res r = t.b.mover().moveTo(p, 1.15, Math.max(0.6, dist - 1.2));
        if (r == Mover.Res.NO_PATH || t.b.mover().stuck(80)) t.b.mover().teleportNear(p);
        return false;
    }

    /** The closest block that matches, around `c` (null: none). */
    @Nullable
    static BlockPos findBlock(Level l, BlockPos c, int r, int ry, Predicate<BlockState> f) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -ry, -r), c.offset(r, ry, r))) {
            if (!f.test(l.getBlockState(q))) continue;
            double d = q.distSqr(c);
            if (d < bd) {
                bd = d;
                best = q.immutable();
            }
        }
        return best;
    }

    /** Around the helper, then around its owner. */
    @Nullable
    static BlockPos findNear(Tasks t, int r, Predicate<BlockState> f) {
        BlockPos p = findBlock(t.h.level(), t.h.blockPosition(), r, 4, f);
        ServerPlayer o = t.h.owner();
        if (p == null && o != null && o.level() == t.h.level()) p = findBlock(t.h.level(), o.blockPosition(), r, 4, f);
        return p;
    }

    /** Puts a block from the bag down next to the helper; its position, or null. */
    @Nullable
    static BlockPos placeNear(HelperEntity h, Block block) {
        Level l = h.level();
        Item it = block.asItem();
        if (Bag.count(h, it) <= 0) return null;
        BlockPos at = h.blockPosition();
        int[][] offs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int dy = 0; dy >= -1; dy--)
            for (int[] o : offs) {
                BlockPos p = at.offset(o[0], dy, o[1]);
                if (!l.getBlockState(p).canBeReplaced() || !l.getFluidState(p).isEmpty()) continue;
                if (!l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)) continue;
                if (!l.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, new AABB(p)).isEmpty()) continue;
                l.setBlock(p, block.defaultBlockState(), 3);
                Bag.take(h, it, 1);
                h.swing(InteractionHand.MAIN_HAND);
                l.playSound(null, p, block.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
                return p;
            }
        return null;
    }

    /** A block it needs: one nearby, else one from the bag (crafted when it can). */
    @Nullable
    static BlockPos station(Tasks t, Block block, int r) {
        BlockPos p = findNear(t, r, s -> s.is(block));
        if (p != null) return p;
        if (Bag.count(t.h, block.asItem()) <= 0) {
            List<Bag.Store> stores = Bag.containersNear(t.h.level(), t.h.blockPosition(), 10);
            if (!stores.isEmpty()) Bag.pull(t.h, stores, s -> s.is(block.asItem()), 1);
        }
        if (Bag.count(t.h, block.asItem()) <= 0) Crafter.craft(t.h, block.asItem(), 1, 0);
        return placeNear(t.h, block);
    }

    /** A water source to work at and a dry spot next to it: {water, stand}, or null. */
    @Nullable
    static BlockPos[] waterSpot(Level l, BlockPos c, int r) {
        BlockPos[] best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -4, -r), c.offset(r, 3, r))) {
            if (!l.getFluidState(q).is(FluidTags.WATER) || !l.getFluidState(q).isSource() || !l.getBlockState(q.above()).isAir()) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos s = q.relative(d);
                BlockPos stand = Mover.standable(l, s) ? s : Mover.standable(l, s.above()) ? s.above() : null;
                if (stand == null) continue;
                double dist = stand.distSqr(c);
                if (dist < bd) {
                    bd = dist;
                    best = new BlockPos[] {q.immutable(), stand.immutable()};
                }
                break;
            }
        }
        return best;
    }

    static void spark(Level l, net.minecraft.core.particles.SimpleParticleType type, Vec3 at, int n, double spread) {
        if (l instanceof ServerLevel sl) sl.sendParticles(type, at.x, at.y, at.z, n, spread, spread * 0.5, spread, 0.02);
    }

    /** Hands a stack to the owner when they are close, else keeps it in the bag. */
    static void handOver(HelperEntity h, ItemStack st) {
        ServerPlayer o = h.owner();
        if (o != null && o.level() == h.level() && o.distanceTo(h) < 8) {
            Bag.giveTo(o, st);
            h.act("wave");
        } else Bag.addOrDrop(h, st, null);
    }

    // ================================================================== fishing
    static boolean isRod(ItemStack s) {
        return s.getItem() instanceof FishingRodItem;
    }

    static boolean equipRod(HelperEntity h) {
        if (isRod(h.tool())) return true;
        for (int i = 0; i < h.bag.getContainerSize(); i++)
            if (isRod(h.bag.getItem(i))) {
                Bag.swapIntoHand(h, i);
                return true;
            }
        return false;
    }

    static void fish(Tasks t) {
        HelperEntity h = t.h;
        CompoundTag s = t.spec;
        Level l = h.level();
        int n = s.contains("n") ? s.getInt("n") : 16;
        if (t.phase == 0) {
            if (!equipRod(h) && (Crafter.craft(h, Items.FISHING_ROD, 1, 0).made() <= 0 || !equipRod(h))) {
                Say.owner(h, "ผมไม่มี§eเบ็ดตกปลา§rเลย ส่งมาให้หน่อย (หรือให้ไม้ 3 + เชือก 2 เดี๋ยวผมทำเอง)",
                        "I have no §efishing rod§r. Give me one (or 3 sticks and 2 string and I'll make it)");
                t.done(true);
                return;
            }
            BlockPos[] w = waterSpot(l, h.blockPosition(), 24);
            ServerPlayer o = h.owner();
            if (w == null && o != null && o.level() == l) w = waterSpot(l, o.blockPosition(), 24);
            if (w == null) {
                Say.owner(h, "แถวนี้ไม่มีน้ำให้ตกปลาเลย พาผมไปใกล้แม่น้ำ ทะเล หรือบ่อน้ำก่อนนะ", "There's no water to fish in around here. Take me near a river, the sea or a pond");
                t.done(true);
                return;
            }
            s.putLong("water", w[0].asLong());
            s.putLong("stand", w[1].asLong());
            s.remove("bite");
            t.phase = 1;
        }
        BlockPos water = BlockPos.of(s.getLong("water")), stand = BlockPos.of(s.getLong("stand"));
        if (t.phase == 1) {
            if (h.blockPosition().distSqr(stand) <= 2) {
                t.b.mover().stop();
                t.phase = 2;
            } else {
                Mover.Res r = t.b.mover().moveTo(stand, 1.15, 0.5);
                if (r == Mover.Res.ARRIVED) t.phase = 2;
                else if (r == Mover.Res.NO_PATH || t.b.mover().stuck(100)) {
                    if (!t.b.mover().teleportNear(stand)) t.phase = 2;
                }
            }
            return;
        }
        Vec3 bob = new Vec3(water.getX() + 0.5, water.getY() + 0.9, water.getZ() + 0.5);
        t.b.mover().look(bob);
        if (!isRod(h.tool()) && !equipRod(h)) {
            finishFish(t, "เบ็ดพังแล้ว", "my rod broke");
            return;
        }
        if (Bag.isFull(h)) {
            finishFish(t, "กระเป๋าเต็ม", "my bag is full");
            return;
        }
        h.setWorking(true);
        long now = l.getGameTime();
        if (!s.contains("bite")) {
            int lure = Compat.fishLure(h.tool());
            int wait = s.getBoolean("fast") ? 20 + h.getRandom().nextInt(20) : Math.max(60, 100 + h.getRandom().nextInt(500) - lure * 100);
            s.putLong("bite", now + wait);
            h.swing(InteractionHand.MAIN_HAND);
            l.playSound(null, h.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5f, 0.4f / (h.getRandom().nextFloat() * 0.4f + 0.8f));
            return;
        }
        long bite = s.getLong("bite");
        if (now % 20 == 0) spark(l, ParticleTypes.FISHING, bob, 1, 0.05);
        if (bite - now < 25 && now % 3 == 0) spark(l, ParticleTypes.BUBBLE, bob.add((bite - now) * 0.08, -0.2, 0), 2, 0.05);
        if (now < bite) return;
        s.remove("bite");
        ItemStack rod = h.tool();
        List<ItemStack> loot = Compat.fishLoot((ServerLevel) l, bob, rod, Compat.fishLuck(rod));
        int fish = 0, other = 0;
        for (ItemStack st : loot) {
            if (st.is(ItemTags.FISHES)) fish += st.getCount();
            else other += st.getCount();
            t.b.report("fish", st);
            Bag.addOrDrop(h, st.copy(), null);
        }
        t.count += fish;
        s.putInt("other", s.getInt("other") + other);
        spark(l, ParticleTypes.SPLASH, bob, 12, 0.3);
        l.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.35f, 1f);
        l.playSound(null, h.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1f, 0.4f / (h.getRandom().nextFloat() * 0.4f + 0.8f));
        h.swing(InteractionHand.MAIN_HAND);
        Compat.hurtItem(rod, 1, h, InteractionHand.MAIN_HAND);
        ExperienceOrb.award((ServerLevel) l, h.position().add(0, 0.5, 0), 1 + h.getRandom().nextInt(6));
        h.addXp(0.5f);
        if (t.count >= n) finishFish(t, null, null);
    }

    static void finishFish(Tasks t, @Nullable String whyTh, @Nullable String whyEn) {
        int other = t.spec.getInt("other");
        String th = "ตกปลาได้ §e" + t.count + "§r ตัว" + (other > 0 ? " (กับของอื่นอีก " + other + " ชิ้น)" : "") + " อยู่ในกระเป๋าผมนะ";
        String en = "Caught §e" + t.count + "§r fish" + (other > 0 ? " (and " + other + " other things)" : "") + ", they're in my bag";
        if (whyTh != null) {
            th = "หยุดตกปลาก่อน " + whyTh + " " + th;
            en = "Stopped fishing, " + whyEn + ". " + en;
        }
        Say.owner(h(t), th, en);
        t.done(true);
    }

    static HelperEntity h(Tasks t) {
        return t.h;
    }

    // ================================================================== trading
    static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItem(a, b);
    }

    static boolean canPay(HelperEntity h, MerchantOffer o) {
        ItemStack a = o.getCostA(), b = o.getCostB();
        if (!b.isEmpty() && same(a, b)) return Bag.count(h, s -> same(s, a)) >= a.getCount() + b.getCount();
        return Bag.count(h, s -> same(s, a) && !Bag.isTool(s)) >= a.getCount() && (b.isEmpty() || Bag.count(h, s -> same(s, b) && !Bag.isTool(s)) >= b.getCount());
    }

    static boolean wanted(MerchantOffer o, String want, String pay) {
        if (o.isOutOfStock()) return false;
        if (!id(o.getResult().getItem()).equals(want)) return false;
        return pay.isEmpty() || id(o.getCostA().getItem()).equals(pay) || !o.getCostB().isEmpty() && id(o.getCostB().getItem()).equals(pay);
    }

    static String costText(MerchantOffer o, boolean th) {
        String a = o.getCostA().getCount() + " " + o.getCostA().getHoverName().getString();
        return o.getCostB().isEmpty() ? a : a + (th ? " กับ " : " and ") + o.getCostB().getCount() + " " + o.getCostB().getHoverName().getString();
    }

    @Nullable
    static AbstractVillager findTrader(Tasks t, String want, String pay) {
        HelperEntity h = t.h;
        AbstractVillager best = null;
        double bd = Double.MAX_VALUE;
        for (AbstractVillager v : h.level().getEntitiesOfClass(AbstractVillager.class, h.getBoundingBox().inflate(40, 12, 40),
                v -> v.isAlive() && !v.isBaby() && !t.skip.contains(v.getUUID()) && !v.isTrading())) {
            boolean any = false;
            for (MerchantOffer o : v.getOffers()) {
                if (!wanted(o, want, pay)) continue;
                if (canPay(h, o)) any = true;
                else if (!t.spec.contains("need")) {
                    t.spec.putString("need", costText(o, true));
                    t.spec.putString("needEn", costText(o, false));
                }
            }
            if (!any) continue;
            t.spec.putBoolean("seen", true);
            double d = v.distanceToSqr(h);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
    }

    static void trade(Tasks t) {
        HelperEntity h = t.h;
        CompoundTag s = t.spec;
        String want = s.contains("want") ? s.getString("want") : "minecraft:emerald";
        String pay = s.getString("pay");
        int n = s.contains("n") ? s.getInt("n") : want.equals("minecraft:emerald") ? 64 : 1;
        if (t.count >= n) {
            finishTrade(t, want);
            return;
        }
        if (!(t.animal instanceof AbstractVillager v) || !v.isAlive() || v.isTrading()) {
            t.animal = findTrader(t, want, pay);
            if (t.animal == null) {
                finishTrade(t, want);
                return;
            }
            return;
        }
        Mover.Res r = t.b.mover().moveTo(v, 1.15, 1.6);
        if (r == Mover.Res.NO_PATH || t.b.mover().stuck(100)) {
            if (h.distanceTo(v) > 3) t.b.mover().teleportNear(v.blockPosition());
        }
        if (h.distanceTo(v) > 2.8) return;
        t.b.mover().stop();
        t.b.mover().look(v.getEyePosition());
        v.getLookControl().setLookAt(h, 30f, 30f);
        if (h.tickCount % 12 != 0) return;
        MerchantOffer offer = null;
        for (MerchantOffer o : v.getOffers()) if (wanted(o, want, pay) && canPay(h, o)) {
            offer = o;
            break;
        }
        if (offer == null) {
            t.skip.add(v.getUUID());
            t.animal = null;
            return;
        }
        ItemStack a = offer.getCostA().copy(), b = offer.getCostB().copy();
        Bag.take(h, st -> same(st, a) && !Bag.isTool(st), a.getCount());
        if (!b.isEmpty()) Bag.take(h, st -> same(st, b) && !Bag.isTool(st), b.getCount());
        ItemStack got = offer.assemble();
        ServerPlayer o = h.owner();
        if (o != null) v.setTradingPlayer(o);
        v.notifyTrade(offer);
        if (o != null) v.setTradingPlayer(null);
        Bag.addOrDrop(h, got.copy(), null);
        if (id(got.getItem()).equals(want)) t.count += got.getCount();
        h.swing(InteractionHand.MAIN_HAND);
        v.playSound(SoundEvents.VILLAGER_YES, 1f, v.getVoicePitch());
        spark(h.level(), ParticleTypes.HAPPY_VILLAGER, v.position().add(0, 2.1, 0), 4, 0.3);
        t.b.report("trade", got);
        h.addXp(1);
    }

    static void finishTrade(Tasks t, String want) {
        HelperEntity h = t.h;
        String wn = name(item(want));
        if (t.count > 0) Say.owner(h, "แลกของกับชาวบ้านได้ §e" + wn + " " + t.count + "§r ชิ้น อยู่ในกระเป๋าผมนะ", "Traded for §e" + t.count + " " + wn + "§r, they're in my bag");
        else if (t.spec.contains("need"))
            Say.owner(h, "มีชาวบ้านแลก " + wn + " แต่ผมไม่มีของจ่าย ต้องใช้ §c" + t.spec.getString("need") + "§r ใส่ในกระเป๋าผมแล้วสั่งใหม่นะ",
                    "A villager trades " + wn + " but I can't pay: it takes §c" + t.spec.getString("needEn") + "§r. Put that in my bag and ask again");
        else Say.owner(h, "ไม่มีชาวบ้านแถวนี้ (ประมาณ 40 บล็อก) ที่แลก " + wn + " เลย", "No villager around here (about 40 blocks) trades " + wn);
        t.done(true);
    }

    // ================================================================== enchanting
    static int bookshelves(Level l, BlockPos table) {
        int n = 0;
        //#if MC>=12100
        //$$ for (BlockPos off : net.minecraft.world.level.block.EnchantingTableBlock.BOOKSHELF_OFFSETS) if (net.minecraft.world.level.block.EnchantingTableBlock.isValidBookShelf(l, table, off)) n++;
        //#else
        for (BlockPos off : net.minecraft.world.level.block.EnchantmentTableBlock.BOOKSHELF_OFFSETS) if (net.minecraft.world.level.block.EnchantmentTableBlock.isValidBookShelf(l, table, off)) n++;
        //#endif
        return Math.min(15, n);
    }

    static int findTarget(HelperEntity h, CompoundTag s) {
        if (s.contains("target")) {
            ItemStack want = Compat.loadStack(s.getCompound("target"));
            for (int i = 0; i < h.bag.getContainerSize(); i++) if (Compat.sameItemSameData(h.bag.getItem(i), want)) return i;
            return -1;
        }
        return -1;
    }

    static void enchant(Tasks t) {
        HelperEntity h = t.h;
        CompoundTag s = t.spec;
        Level l = h.level();
        ServerPlayer o = h.owner();
        if (t.phase == 0 && !s.contains("target")) {
            // what to enchant: the item (or kind of item) it was told, else what the owner holds, else a book
            ItemStack target = ItemStack.EMPTY;
            Predicate<ItemStack> told = st -> {
                if (s.contains("item")) return st.is(item(s.getString("item")));
                if (!s.contains("kind")) return true;
                String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), k = s.getString("kind");
                return path.equals(k) || path.endsWith("_" + k);
            };
            boolean named = s.contains("item") || s.contains("kind");
            if (named) {
                for (int i = 0; i < h.bag.getContainerSize() && target.isEmpty(); i++) {
                    ItemStack b = h.bag.getItem(i);
                    if (told.test(b) && b.isEnchantable()) target = b;
                }
                if (target.isEmpty() && told.test(h.tool()) && h.tool().isEnchantable()) {
                    Bag.addOrDrop(h, h.tool().copy(), null);
                    h.setTool(ItemStack.EMPTY);
                    return;
                }
            }
            if (target.isEmpty() && o != null && o.level() == l && o.distanceTo(h) < 8 && o.getMainHandItem().isEnchantable() && told.test(o.getMainHandItem())) {
                ItemStack in = o.getMainHandItem().copy();
                o.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                Bag.addOrDrop(h, in, null);
                s.putBoolean("fromOwner", true);
                target = in;
            }
            if (target.isEmpty() && !named)
                for (int i = 0; i < h.bag.getContainerSize() && target.isEmpty(); i++) if (h.bag.getItem(i).is(Items.BOOK)) target = h.bag.getItem(i);
            if (target.isEmpty()) {
                String what = s.contains("item") ? name(item(s.getString("item"))) : s.contains("kind") ? s.getString("kind") : null;
                Say.owner(h, what != null ? "ผมไม่มี " + what + " ที่ร่ายมนตร์ได้ (ของที่ร่ายแล้วร่ายซ้ำไม่ได้) ส่งให้ผมก่อนนะ"
                                : "จะให้ร่ายมนตร์อะไรล่ะ? ถือของไว้ในมือแล้วสั่ง หรือใส่ของ/หนังสือในกระเป๋าผม",
                        what != null ? "I have no " + what + " that can be enchanted (enchanted things can't be enchanted again). Give me one first"
                                : "Enchant what? Hold the item and ask, or put it (or a book) in my bag");
                t.done(false);
                return;
            }
            s.put("target", Compat.saveStack(target.copyWithCount(1)));
        }
        if (t.phase == 0) {
            if (Bag.count(h, Items.LAPIS_LAZULI) < 3) {
                List<Bag.Store> stores = Bag.containersNear(l, h.blockPosition(), 10);
                if (!stores.isEmpty()) Bag.pull(h, stores, st -> st.is(Items.LAPIS_LAZULI), 64);
            }
            if (Bag.count(h, Items.LAPIS_LAZULI) <= 0) {
                Say.owner(h, "ร่ายมนตร์ต้องใช้§eลาพิส ลาซูลี§r (1-3 ชิ้น) ใส่ในกระเป๋าผมก่อนนะ", "Enchanting takes §elapis lazuli§r (1-3). Put some in my bag first");
                giveBack(t);
                t.done(false);
                return;
            }
            BlockPos table = station(t, Blocks.ENCHANTING_TABLE, 16);
            if (table == null) {
                Say.owner(h, "ไม่มีโต๊ะร่ายมนตร์แถวนี้ และผมทำเองไม่ได้ (ต้องใช้เพชร 2 ออบซิเดียน 4 หนังสือ 1)",
                        "There's no enchanting table here and I can't make one (2 diamonds, 4 obsidian, 1 book)");
                giveBack(t);
                t.done(false);
                return;
            }
            s.putLong("table", table.asLong());
            t.phase = 1;
        }
        BlockPos table = BlockPos.of(s.getLong("table"));
        if (!l.getBlockState(table).is(Blocks.ENCHANTING_TABLE)) {
            t.phase = 0;
            return;
        }
        if (!near(t, table, 3.2)) return;
        int slot = findTarget(h, s);
        if (slot < 0) {
            Say.owner(h, "ของที่จะร่ายมนตร์หายไปจากกระเป๋าผม", "The thing to enchant is gone from my bag");
            t.done(false);
            return;
        }
        ItemStack target = h.bag.getItem(slot);
        int power = bookshelves(l, table);
        net.minecraft.util.RandomSource r = h.getRandom();
        int[] cost = new int[3];
        for (int i = 0; i < 3; i++) {
            cost[i] = EnchantmentHelper.getEnchantmentCost(r, i, power, target);
            if (cost[i] < i + 1) cost[i] = 0;
        }
        boolean free = s.getBoolean("free") || o != null && o.getAbilities().instabuild;
        int levels = o == null ? 0 : o.experienceLevel;
        int lapis = Bag.count(h, Items.LAPIS_LAZULI);
        int pick = -1;
        for (int i = 2; i >= 0; i--)
            if (cost[i] > 0 && (free || levels >= cost[i]) && lapis >= i + 1) {
                pick = i;
                break;
            }
        if (pick < 0) {
            if (cost[0] <= 0) Say.owner(h, target.getHoverName().getString() + " ร่ายมนตร์ไม่ได้ครับ", target.getHoverName().getString() + " can't be enchanted");
            else if (o == null) Say.owner(h, "ร่ายมนตร์ใช้เลเวลของคุณ ต้องออนไลน์อยู่ด้วยนะ", "Enchanting uses your levels, so you need to be online");
            else Say.owner(h, "เลเวลไม่พอ ต้องมีอย่างน้อย §e" + cost[0] + "§r เลเวล (ตอนนี้มี " + levels + ")", "Not enough levels: it takes at least §e" + cost[0] + "§r (you have " + levels + ")");
            giveBack(t);
            t.done(false);
            return;
        }
        ItemStack result = Compat.enchantAt((ServerLevel) l, r, target.copyWithCount(1), cost[pick]);
        if (!result.isEnchanted() && !result.is(Items.ENCHANTED_BOOK)) {
            Say.owner(h, "ร่ายมนตร์ไม่ติดเลย ลองใหม่อีกทีนะ", "The enchantment didn't take. Try again");
            t.done(false);
            return;
        }
        target.shrink(1);
        h.bag.setChanged();
        Bag.take(h, Items.LAPIS_LAZULI, pick + 1);
        if (!free && o != null) o.giveExperienceLevels(-(pick + 1));
        l.playSound(null, table, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, l.getRandom().nextFloat() * 0.1f + 0.9f);
        spark(l, ParticleTypes.ENCHANT, Vec3.atCenterOf(table).add(0, 0.8, 0), 40, 0.6);
        h.swing(InteractionHand.MAIN_HAND);
        h.addXp(3);
        t.b.report("enchant", result);
        Say.owner(h, Say.mix(o, "ร่ายมนตร์เสร็จแล้ว (ระดับ " + cost[pick] + ", ชั้นหนังสือ " + power + "): ", "Enchanted (level " + cost[pick] + ", " + power + " bookshelves): ", Say.item(result)));
        handOver(h, result);
        t.done(false);
    }

    /** The owner's item goes back to them when the job can't be done. */
    static void giveBack(Tasks t) {
        if (!t.spec.getBoolean("fromOwner")) return;
        int slot = findTarget(t.h, t.spec);
        if (slot < 0) return;
        ItemStack st = t.h.bag.getItem(slot).copy();
        t.h.bag.setItem(slot, ItemStack.EMPTY);
        handOver(t.h, st);
    }

    // ================================================================== brewing
    static final Item[] KNOWN_INGREDIENTS = {Items.NETHER_WART, Items.SUGAR, Items.BLAZE_POWDER, Items.GHAST_TEAR, Items.MAGMA_CREAM, Items.GLISTERING_MELON_SLICE,
            Items.GOLDEN_CARROT, Items.PUFFERFISH, Items.RABBIT_FOOT, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE, Items.PHANTOM_MEMBRANE, Items.TURTLE_HELMET,
            Items.REDSTONE, Items.GLOWSTONE_DUST, Items.GUNPOWDER, Items.DRAGON_BREATH};

    static String key(ItemStack s) {
        return id(s.getItem()) + "|" + Compat.potionId(s);
    }

    /** The ingredients, in order, that turn a water bottle into the goal (null: no way). */
    @Nullable
    static List<Item> brewPath(Level l, ItemStack goal, List<Item> ingredients) {
        record Node(ItemStack st, List<Item> steps) {}
        String goalKey = key(goal);
        ArrayDeque<Node> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        ItemStack water = Compat.potion(Items.POTION, "minecraft:water");
        q.add(new Node(water, List.of()));
        seen.add(key(water));
        while (!q.isEmpty()) {
            Node n = q.poll();
            if (n.steps.size() >= 6) continue;
            for (Item ing : ingredients) {
                ItemStack next = Compat.brew(l, new ItemStack(ing), n.st);
                if (next.isEmpty()) continue;
                String k = key(next);
                if (!seen.add(k)) continue;
                List<Item> steps = new ArrayList<>(n.steps);
                steps.add(ing);
                if (k.equals(goalKey)) return steps;
                q.add(new Node(next, steps));
            }
        }
        return null;
    }

    static boolean isWater(ItemStack s) {
        return s.is(Items.POTION) && Compat.potionId(s).equals("minecraft:water");
    }

    static void brew(Tasks t) {
        HelperEntity h = t.h;
        CompoundTag s = t.spec;
        Level l = h.level();
        String potion = s.getString("potion");
        Item form = item(s.contains("form") ? s.getString("form") : "minecraft:potion");
        int batches = Math.max(1, Math.min(3, ((s.contains("n") ? s.getInt("n") : 3) + 2) / 3));
        if (t.phase == 0) {
            ItemStack goal = Compat.potion(form, potion);
            if (goal.isEmpty()) {
                Say.owner(h, "ไม่รู้จักยาแบบนั้นครับ", "I don't know that potion");
                t.done(false);
                return;
            }
            // what it has (bag + chests nearby) and the way there
            List<Bag.Store> stores = Bag.containersNear(l, h.blockPosition(), 10);
            Set<Item> all = new LinkedHashSet<>(List.of(KNOWN_INGREDIENTS));
            for (int i = 0; i < h.bag.getContainerSize(); i++) if (Compat.brewIngredient(l, h.bag.getItem(i))) all.add(h.bag.getItem(i).getItem());
            List<Item> path = brewPath(l, goal, new ArrayList<>(all));
            if (path == null) {
                Say.owner(h, "ผมไม่รู้วิธีต้ม " + goal.getHoverName().getString() + " ครับ", "I don't know how to brew " + goal.getHoverName().getString());
                t.done(false);
                return;
            }
            List<String> missing = new ArrayList<>();
            for (Item ing : new LinkedHashSet<>(path)) {
                int need = (int) path.stream().filter(x -> x == ing).count() * batches;
                if (Bag.count(h, ing) < need && !stores.isEmpty()) Bag.pull(h, stores, st -> st.is(ing), need - Bag.count(h, ing));
                if (Bag.count(h, ing) < need) missing.add(name(ing) + " x" + (need - Bag.count(h, ing)));
            }
            if (Bag.count(h, Items.BLAZE_POWDER) <= (path.contains(Items.BLAZE_POWDER) ? 1 : 0) && !stores.isEmpty())
                Bag.pull(h, stores, st -> st.is(Items.BLAZE_POWDER), 2);
            int bottles = 3 * batches;
            if (Bag.count(h, Skills::isWater) + Bag.count(h, Items.GLASS_BOTTLE) < bottles && !stores.isEmpty())
                Bag.pull(h, stores, st -> isWater(st) || st.is(Items.GLASS_BOTTLE), bottles);
            int have = Bag.count(h, Skills::isWater) + Bag.count(h, Items.GLASS_BOTTLE);
            if (have < bottles) Crafter.craft(h, Items.GLASS_BOTTLE, bottles - have, 0);
            if (Bag.count(h, Skills::isWater) + Bag.count(h, Items.GLASS_BOTTLE) <= 0) missing.add(name(Items.GLASS_BOTTLE) + " x" + bottles);
            if (!missing.isEmpty()) {
                StringBuilder steps = new StringBuilder();
                for (Item i : path) steps.append(steps.length() == 0 ? "" : " → ").append(name(i));
                Say.owner(h, "ต้ม " + goal.getHoverName().getString() + " ต้องใช้: " + steps + " (และผงเบลซเป็นเชื้อเพลิง) ตอนนี้ขาด §c" + String.join(", ", missing) + "§r",
                        "Brewing " + goal.getHoverName().getString() + " goes: " + steps + " (and blaze powder as fuel). I'm missing §c" + String.join(", ", missing) + "§r");
                t.done(false);
                return;
            }
            ListTag p = new ListTag();
            for (Item i : path) p.add(StringTag.valueOf(id(i)));
            s.put("path", p);
            s.putInt("batch", 0);
            t.phase = Bag.count(h, Skills::isWater) >= Math.min(3, bottles) ? 2 : 1;
        }
        if (t.phase == 1) {
            // filling bottles at the water
            if (!s.contains("water")) {
                BlockPos[] w = waterSpot(l, h.blockPosition(), 24);
                if (w == null && Bag.count(h, Skills::isWater) > 0) {
                    t.phase = 2;
                    return;
                }
                if (w == null) {
                    Say.owner(h, "ต้องเติมน้ำใส่ขวดแต่แถวนี้ไม่มีน้ำ", "I need to fill the bottles but there's no water here");
                    t.done(false);
                    return;
                }
                s.putLong("water", w[0].asLong());
            }
            BlockPos w = BlockPos.of(s.getLong("water"));
            if (!near(t, w, 3.5)) return;
            int empty = Bag.take(h, Items.GLASS_BOTTLE, 64);
            for (int i = 0; i < empty; i++) Bag.addOrDrop(h, Compat.potion(Items.POTION, "minecraft:water"), null);
            l.playSound(null, w, SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1f, 1f);
            h.swing(InteractionHand.MAIN_HAND);
            t.phase = 2;
            return;
        }
        if (t.phase == 2) {
            BlockPos stand = station(t, Blocks.BREWING_STAND, 16);
            if (stand == null) {
                Say.owner(h, "ไม่มีแท่นต้มยาแถวนี้ และผมทำเองไม่ได้ (ต้องใช้แท่งเบลซ 1 หินกรวด 3)", "There's no brewing stand here and I can't make one (1 blaze rod, 3 cobblestone)");
                t.done(false);
                return;
            }
            s.putLong("stand", stand.asLong());
            t.phase = 3;
        }
        BlockPos stand = BlockPos.of(s.getLong("stand"));
        if (!(l.getBlockEntity(stand) instanceof BrewingStandBlockEntity be)) {
            t.phase = 2;
            return;
        }
        if (!near(t, stand, 3.2)) return;
        h.setWorking(true);
        ListTag path = s.getList("path", 8);
        if (t.phase == 3) {
            // load: anything in the stand comes out, three water bottles go in, fuel if it has none
            for (int i = 0; i < 4; i++) {
                ItemStack in = be.getItem(i);
                if (!in.isEmpty()) {
                    Bag.addOrDrop(h, in.copy(), null);
                    be.setItem(i, ItemStack.EMPTY);
                }
            }
            int put = 0;
            for (int i = 0; i < 3; i++)
                if (Bag.take(h, Skills::isWater, 1) > 0) {
                    be.setItem(i, Compat.potion(Items.POTION, "minecraft:water"));
                    put++;
                }
            if (put == 0) {
                finishBrew(t, be);
                return;
            }
            if (be.getItem(4).isEmpty() && Bag.take(h, Items.BLAZE_POWDER, 1) > 0) be.setItem(4, new ItemStack(Items.BLAZE_POWDER));
            be.setChanged();
            s.putInt("step", 0);
            s.remove("stepAt");
            h.swing(InteractionHand.MAIN_HAND);
            t.phase = 4;
            return;
        }
        // phase 4: one ingredient after another; the stand brews it (20 seconds each)
        int step = s.getInt("step");
        long now = l.getGameTime();
        ItemStack ing = be.getItem(3);
        if (step < path.size()) {
            Item want = item(path.getString(step));
            if (s.contains("stepAt") && (ing.isEmpty() || !ing.is(want))) {
                // that one is done (dragon's breath leaves its bottle behind)
                if (!ing.isEmpty()) {
                    Bag.addOrDrop(h, ing.copy(), null);
                    be.setItem(3, ItemStack.EMPTY);
                }
                s.putInt("step", step + 1);
                s.remove("stepAt");
                l.playSound(null, stand, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1f);
                return;
            }
            if (ing.isEmpty()) {
                if (Bag.take(h, want, 1) <= 0) {
                    Say.owner(h, "ต้มยาต่อไม่ได้ " + name(want) + " หมด", "I can't go on brewing, I ran out of " + name(want));
                    finishBrew(t, be);
                    return;
                }
                be.setItem(3, new ItemStack(want));
                be.setChanged();
                s.putLong("stepAt", now);
                h.swing(InteractionHand.MAIN_HAND);
                return;
            }
            if (s.contains("stepAt") && now - s.getLong("stepAt") > 20 * 30) {
                if (be.getItem(4).isEmpty() && Bag.take(h, Items.BLAZE_POWDER, 1) > 0) {
                    be.setItem(4, new ItemStack(Items.BLAZE_POWDER));
                    be.setChanged();
                    s.putLong("stepAt", now);
                    return;
                }
                Say.owner(h, "แท่นต้มยาไม่ทำงาน (ไม่มี§eผงเบลซ§rเป็นเชื้อเพลิง?)", "The brewing stand isn't brewing (no §eblaze powder§r for fuel?)");
                Bag.addOrDrop(h, ing.copy(), null);
                be.setItem(3, ItemStack.EMPTY);
                finishBrew(t, be);
            }
            return;
        }
        // a batch is done: the potions come out
        for (int i = 0; i < 3; i++) {
            ItemStack p = be.getItem(i);
            if (p.isEmpty()) continue;
            if (key(p).equals(key(Compat.potion(form, potion)))) t.count += p.getCount();
            Bag.addOrDrop(h, p.copy(), null);
            be.setItem(i, ItemStack.EMPTY);
        }
        be.setChanged();
        s.putInt("batch", s.getInt("batch") + 1);
        if (s.getInt("batch") < batches && Bag.count(h, Skills::isWater) > 0) t.phase = 3;
        else finishBrew(t, be);
    }

    static void finishBrew(Tasks t, @Nullable BrewingStandBlockEntity be) {
        HelperEntity h = t.h;
        ItemStack goal = Compat.potion(item(t.spec.contains("form") ? t.spec.getString("form") : "minecraft:potion"), t.spec.getString("potion"));
        if (be != null)
            for (int i = 0; i < 3; i++) {
                ItemStack p = be.getItem(i);
                if (p.isEmpty()) continue;
                Bag.addOrDrop(h, p.copy(), null);
                be.setItem(i, ItemStack.EMPTY);
            }
        if (t.count > 0) {
            Say.owner(h, Say.mix(h.owner(), "ต้มยาเสร็จแล้ว §e" + t.count + "§r ขวด: ", "Brewed §e" + t.count + "§r bottles: ", Say.item(goal)));
            ServerPlayer o = h.owner();
            if (o != null && o.level() == h.level() && o.distanceTo(h) < 8) {
                Item gi = goal.getItem();
                String gk = key(goal);
                for (int i = 0; i < h.bag.getContainerSize(); i++) {
                    ItemStack st = h.bag.getItem(i);
                    if (st.is(gi) && key(st).equals(gk)) {
                        Bag.giveTo(o, st.copy());
                        h.bag.setItem(i, ItemStack.EMPTY);
                    }
                }
                h.act("wave");
            }
        }
        t.done(true);
    }

    // ================================================================== machines (furnaces, other mods' machines)
    static boolean storage(Level l, BlockPos p) {
        var be = l.getBlockEntity(p);
        return be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity || be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity;
    }

    @Nullable
    static BlockPos findMachine(Tasks t, String words) {
        Level l = t.h.level();
        List<String> toks = new ArrayList<>();
        for (String w : words.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9_:]+")) if (w.length() >= 3 && !w.matches("the|machine|this|that|into")) toks.add(w);
        String resolved = words.isBlank() ? null : com.helperbuddy.talk.ItemWords.resolve(words);
        Predicate<BlockState> named = st -> {
            String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
            if (resolved != null && id.equals(resolved)) return true;
            return !toks.isEmpty() && toks.stream().allMatch(id::contains);
        };
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        List<BlockPos> centers = new ArrayList<>();
        centers.add(t.h.blockPosition());
        ServerPlayer o = t.h.owner();
        if (o != null && o.level() == l) centers.add(o.blockPosition());
        boolean byName = resolved != null || !toks.isEmpty();
        for (BlockPos c : centers)
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-12, -4, -12), c.offset(12, 4, 12))) {
                if (l.getBlockEntity(q) == null) continue;
                BlockState st = l.getBlockState(q);
                if (byName ? !named.test(st) : storage(l, q)) continue;
                if (!Machines.isMachine(l, q)) continue;
                double d = q.distSqr(c) + (c == centers.get(0) ? 0 : 4);
                if (d < bd) {
                    bd = d;
                    best = q.immutable();
                }
            }
        return best;
    }

    static void machine(Tasks t) {
        HelperEntity h = t.h;
        CompoundTag s = t.spec;
        Level l = h.level();
        String mode = s.contains("mode") ? s.getString("mode") : "run";
        if (t.phase == 0) {
            BlockPos m = s.contains("pos") ? BlockPos.of(s.getLong("pos")) : findMachine(t, s.getString("words"));
            if (m == null || !Machines.isMachine(l, m)) {
                Say.owner(h, "หาเครื่องนั้นไม่เจอ (ในระยะ 12 บล็อก) ลองยืนใกล้ๆ แล้วมองที่เครื่อง แล้วสั่ง \"ใส่ ... ในเครื่องนี้\"",
                        "I can't find that machine (within 12 blocks). Stand near it, look at it and say \"put ... in this machine\"");
                t.done(false);
                return;
            }
            s.putLong("m", m.asLong());
            t.phase = 1;
        }
        BlockPos m = BlockPos.of(s.getLong("m"));
        String mName = l.getBlockState(m).getBlock().getName().getString();
        if (!near(t, m, 3.5)) return;
        h.setWorking(true);
        if (t.phase == 1) {
            ListTag in = new ListTag();
            if (mode.equals("run")) {
                Item it = s.contains("item") ? item(s.getString("item")) : null;
                int n = s.contains("n") ? s.getInt("n") : 64 * 9;
                int put = 0;
                if (it != null && it != Items.AIR) {
                    for (int i = 0; i < h.bag.getContainerSize() && put < n; i++) {
                        ItemStack st = h.bag.getItem(i);
                        if (!st.is(it)) continue;
                        ItemStack go = st.copyWithCount(Math.min(st.getCount(), n - put));
                        int k = Machines.insert(l, m, go);
                        st.shrink(k);
                        put += k;
                        if (k < go.getCount()) break;
                    }
                    h.bag.setChanged();
                    in.add(StringTag.valueOf(id(it)));
                    // a furnace-like machine needs fuel too
                    if (put > 0 && !Compat.smelt(l, new ItemStack(it)).isEmpty() && Machines.contents(l, m).stream().noneMatch(st -> Compat.burnTime(st) > 0 && !st.is(it))) {
                        for (int i = 0; i < h.bag.getContainerSize(); i++) {
                            ItemStack st = h.bag.getItem(i);
                            if (st.isEmpty() || Compat.burnTime(st) <= 0 || Bag.isTool(st) || st.is(it)) continue;
                            int want = Math.max(1, (int) Math.ceil(put * 200.0 / Compat.burnTime(st)));
                            int k = Machines.insert(l, m, st.copyWithCount(Math.min(want, st.getCount())));
                            st.shrink(k);
                            if (k > 0) {
                                in.add(StringTag.valueOf(id(st.getItem())));
                                break;
                            }
                        }
                        h.bag.setChanged();
                    }
                }
                if (it != null && put == 0) {
                    Say.owner(h, "ใส่ " + name(it) + " ใน" + mName + "ไม่ได้ (ผมไม่มี หรือเครื่องไม่รับ)", "I couldn't put " + name(it) + " in the " + mName + " (I have none, or it won't take it)");
                    t.done(false);
                    return;
                }
                if (put > 0) {
                    Say.owner(h, "ใส่ " + name(it) + " " + put + " ชิ้นใน" + mName + "แล้ว จะรอเก็บของที่ได้ให้นะ", "Put " + put + " " + name(it) + " in the " + mName + ", I'll wait and collect what comes out");
                    h.swing(InteractionHand.MAIN_HAND);
                }
            }
            s.put("in", in);
            s.putLong("since", l.getGameTime());
            t.phase = 2;
            return;
        }
        // phase 2: take out what it makes
        if (h.tickCount % 20 != 0) return;
        Set<String> inputs = new HashSet<>();
        for (int i = 0; i < s.getList("in", 8).size(); i++) inputs.add(s.getList("in", 8).getString(i));
        List<ItemStack> got = Machines.extract(l, m, st -> !inputs.contains(id(st.getItem())) && Compat.burnTime(st) <= 0, 64 * 4);
        for (ItemStack g : got) {
            t.count += g.getCount();
            Bag.addOrDrop(h, g, null);
        }
        if (!got.isEmpty()) h.swing(InteractionHand.MAIN_HAND);
        boolean working = Machines.contents(l, m).stream().anyMatch(st -> inputs.contains(id(st.getItem())) && Compat.burnTime(st) <= 0);
        long waited = l.getGameTime() - s.getLong("since");
        if (mode.equals("collect") || !working && waited > 60 || waited > 20 * 60 * 10) {
            if (t.count > 0) Say.owner(h, "เก็บของจาก" + mName + "ได้ §e" + t.count + "§r ชิ้น อยู่ในกระเป๋าผมนะ", "Collected §e" + t.count + "§r items from the " + mName + ", they're in my bag");
            else Say.owner(h, mName + "ยังไม่มีของออกมาเลย", "Nothing has come out of the " + mName + " yet");
            t.done(true);
        }
    }
}
