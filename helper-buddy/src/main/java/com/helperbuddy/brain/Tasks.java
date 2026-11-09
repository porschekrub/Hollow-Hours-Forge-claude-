package com.helperbuddy.brain;

import com.helperbuddy.compat.Compat;
import com.helperbuddy.HelperBuddy;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.talk.ItemWords;
import com.helperbuddy.talk.Nlu;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One-off jobs: craft, smelt, fetch from a chest, breed or shear animals, light up the area with torches, flatten the
 * ground. Anything that needs materials uses what is in the bag (and nearby chests) and says what is missing.
 */
public final class Tasks {
    final Brain b;
    final HelperEntity h;
    @Nullable
    CompoundTag spec;
    int phase, count, guard;
    @Nullable
    BlockPos at;
    final Reach reach;
    @Nullable
    net.minecraft.world.entity.LivingEntity animal;
    final List<BlockPos> todo = new ArrayList<>();
    /** villagers (or other things) already tried that did not work out */
    final java.util.Set<java.util.UUID> skip = new java.util.HashSet<>();

    Tasks(Brain b) {
        this.b = b;
        this.h = b.h;
        this.reach = new Reach(b);
    }

    public boolean active() {
        return spec != null;
    }

    // ------------------------------------------------------------------ specs
    public static CompoundTag spec(String kind) {
        CompoundTag t = new CompoundTag();
        t.putString("kind", kind);
        return t;
    }

    public static CompoundTag craft(String itemId, int n) {
        CompoundTag t = spec("craft");
        t.putString("item", itemId);
        t.putInt("n", Math.max(1, n));
        return t;
    }

    public static CompoundTag smelt(String text, @Nullable Integer n) {
        CompoundTag t = spec("smelt");
        t.putString("text", text == null ? "" : text);
        if (n != null) t.putInt("n", n);
        return t;
    }

    public static CompoundTag fetch(String text, @Nullable Integer n) {
        CompoundTag t = spec("fetch");
        t.putString("text", text == null ? "" : text);
        if (n != null) t.putInt("n", n);
        return t;
    }

    public static CompoundTag breed(@Nullable String animal) {
        CompoundTag t = spec("breed");
        if (animal != null) t.putString("animal", animal);
        return t;
    }

    /** Fishing: n fish. */
    public static CompoundTag fish(@Nullable Integer n) {
        CompoundTag t = spec("fish");
        if (n != null) t.putInt("n", n);
        t.putBoolean("long", true);
        return t;
    }

    /** Trading with villagers for `want` (null: emeralds), paying with `pay` when given. */
    public static CompoundTag trade(@Nullable String want, @Nullable String pay, @Nullable Integer n) {
        CompoundTag t = spec("trade");
        if (want != null) t.putString("want", want);
        if (pay != null) t.putString("pay", pay);
        if (n != null) t.putInt("n", n);
        t.putBoolean("long", true);
        return t;
    }

    /** Enchanting an item (null: what the owner holds, else a book). */
    public static CompoundTag enchant(@Nullable String item, @Nullable String kind) {
        CompoundTag t = spec("enchant");
        if (item != null) t.putString("item", item);
        if (kind != null) t.putString("kind", kind);
        return t;
    }

    /** Brewing a potion ("minecraft:long_swiftness"), as potion / splash_potion / lingering_potion. */
    public static CompoundTag brew(String potion, @Nullable String form, @Nullable Integer n) {
        CompoundTag t = spec("brew");
        t.putString("potion", potion);
        if (form != null) t.putString("form", form);
        if (n != null) t.putInt("n", n);
        t.putBoolean("long", true);
        return t;
    }

    /** Running a machine: put the item in and collect what it makes ("run"), or just collect ("collect"). */
    public static CompoundTag machine(@Nullable String item, @Nullable Integer n, @Nullable String words, @Nullable BlockPos pos, String mode) {
        CompoundTag t = spec("machine");
        if (item != null) t.putString("item", item);
        if (n != null) t.putInt("n", n);
        if (words != null) t.putString("words", words);
        if (pos != null) t.putLong("pos", pos.asLong());
        t.putString("mode", mode);
        t.putBoolean("long", true);
        return t;
    }

    public void start(CompoundTag s) {
        b.leaveJob();
        spec = s.copy();
        phase = 0;
        count = 0;
        guard = 0;
        at = h.blockPosition();
        todo.clear();
        skip.clear();
        animal = null;
        h.setMode(Mode.TASK);
    }

    void end() {
        spec = null;
        todo.clear();
        animal = null;
        h.setWorking(false);
    }

    void done(boolean stay) {
        if (com.helperbuddy.talk.Say.LOG_UNHEARD && spec != null)
            com.helperbuddy.HelperBuddy.LOG.info("[{}] task {} done (count {}, phase {})", h.helperName(), spec.getString("kind"), count, phase);
        end();
        if (!b.plan.active()) h.setMode(stay ? Mode.STAY : Mode.FOLLOW);
        b.report("task", null);
    }

    public String statusShort(boolean th) {
        if (spec == null) return "";
        String k = spec.getString("kind");
        return switch (k) {
            case "craft" -> th ? "คราฟต์" : "crafting";
            case "smelt" -> (th ? "ถลุง " : "smelting ") + count;
            case "breed" -> (th ? "ผสมพันธุ์ " : "breeding ") + count;
            case "shear" -> (th ? "ตัดขนแกะ " : "shearing ") + count;
            case "torch" -> (th ? "ปักคบเพลิง " : "torches ") + count;
            case "flatten" -> (th ? "ปรับพื้นที่ " : "flattening ") + (todo.isEmpty() ? "" : count + "/" + (count + todo.size()));
            case "fetch" -> th ? "หยิบของจากหีบ" : "fetching";
            case "fish" -> (th ? "ตกปลา " : "fishing ") + count;
            case "trade" -> (th ? "แลกของ " : "trading ") + count;
            case "enchant" -> th ? "ร่ายมนตร์" : "enchanting";
            case "brew" -> (th ? "ต้มยา " : "brewing ") + (spec.contains("step") ? (spec.getInt("step") + 1) + "/" + spec.getList("path", 8).size() : "");
            case "machine" -> (th ? "ใช้เครื่อง " : "machine ") + count;
            default -> k;
        };
    }

    // ------------------------------------------------------------------ tick
    void tick() {
        if (spec == null) {
            if (com.helperbuddy.talk.Say.LOG_UNHEARD) com.helperbuddy.HelperBuddy.LOG.info("[{}] working on a task that is gone", h.helperName());
            h.setMode(Mode.STAY);
            return;
        }
        if (b.breaker.busy()) {
            b.breaker.tick();
            return;
        }
        if (++guard > (spec.getBoolean("long") ? 20 * 60 * 30 : 20 * 60 * 5)) {
            Say.owner(h, "งานนี้ใช้เวลานานเกินไป ขอหยุดก่อนนะ", "This is taking too long, I'll stop here");
            done(true);
            return;
        }
        switch (spec.getString("kind")) {
            case "craft" -> tickCraft();
            case "smelt" -> tickSmelt();
            case "fetch" -> tickFetch();
            case "breed" -> tickBreed();
            case "shear" -> tickShear();
            case "torch" -> tickTorch();
            case "flatten" -> tickFlatten();
            case "fish" -> Skills.fish(this);
            case "trade" -> Skills.trade(this);
            case "enchant" -> Skills.enchant(this);
            case "brew" -> Skills.brew(this);
            case "machine" -> Skills.machine(this);
            default -> done(true);
        }
    }

    // ------------------------------------------------------------------ give results to the owner
    void deliver(Item item, int n) {
        ServerPlayer p = h.owner();
        if (p == null || n <= 0) return;
        if (p.level() != h.level() || p.distanceTo(h) > 6) b.mover().bringTo(p);
        int left = n;
        for (int i = 0; i < h.bag.getContainerSize() && left > 0; i++) {
            ItemStack s = h.bag.getItem(i);
            if (!s.is(item)) continue;
            int t = Math.min(left, s.getCount());
            Bag.giveTo(p, s.split(t));
            left -= t;
        }
        h.bag.setChanged();
        h.act("wave");
    }

    // ------------------------------------------------------------------ craft
    void tickCraft() {
        Item item = BuiltInRegistries.ITEM.get(HelperBuddy.rl(spec.getString("item")));
        int n = spec.getInt("n");
        boolean th = Say.thai(h.owner());
        String name = new ItemStack(item).getHoverName().getString();
        if (item == Items.AIR || !Crafter.craftable(h.level(), item)) {
            Say.owner(h, "ผมไม่รู้วิธีคราฟต์ " + name + " ครับ", "I don't know a recipe for " + name);
            done(true);
            return;
        }
        // ingredients from nearby chests too
        List<Bag.Store> stores = Bag.containersNear(h.level(), h.blockPosition(), 10);
        Crafter.Result r = Crafter.craft(h, item, n, 0);
        if (r.made() < n && !stores.isEmpty()) {
            for (Bag.Store st : stores) {
                for (var rec : Crafter.recipesFor(h.level(), item))
                    for (var ing : rec.getIngredients())
                        if (!ing.isEmpty()) Bag.pull(h, List.of(st), ing::test, 64);
            }
            Crafter.Result r2 = Crafter.craft(h, item, n - r.made(), 0);
            r = new Crafter.Result(r.made() + r2.made(), r2.missing());
        }
        h.swing(InteractionHand.MAIN_HAND);
        h.level().playSound(null, h.blockPosition(), SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.NEUTRAL, 0.8f, 1.1f);
        if (r.made() > 0) {
            Say.owner(h, "คราฟต์ §e" + name + "§r เสร็จแล้ว " + r.made() + " ชิ้น!" + (r.made() < n ? " (ได้ไม่ครบเพราะของไม่พอ)" : ""),
                    "Crafted §e" + r.made() + " " + name + "§r!" + (r.made() < n ? " (not all of them: not enough materials)" : ""));
            h.addXp(1);
            deliver(item, r.made());
        } else {
            String miss = r.missing() == null ? "" : Crafter.missingNames(r.missing());
            Say.owner(h, "คราฟต์ " + name + " ไม่ได้ ขาดวัตถุดิบ: §c" + miss + "§r (ใส่ของในกระเป๋าผมหรือวางไว้ในหีบใกล้ๆ)",
                    "I can't craft " + name + ", missing: §c" + miss + "§r (put them in my bag or a chest nearby)");
        }
        if (!th) h.act("cheer");
        done(false);
    }

    // ------------------------------------------------------------------ smelt (a little furnace of its own: it needs fuel)
    boolean smeltWanted(ItemStack s, String text) {
        if (s.isEmpty() || Bag.isTool(s) || Care.foodValue(s) > 0 || s.is(Items.POTATO)) return false;
        ItemStack out = Crafter.smeltResult(h.level(), s);
        if (out.isEmpty()) return false;
        if (text.isEmpty()) return !s.is(ItemTags.LOGS) && !s.is(ItemTags.PLANKS);
        List<String> ores = Nlu.oresIn(text);
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        for (String g : ores) if (id.contains(g.equals("debris") ? "debris" : g)) return true;
        if (!ores.isEmpty()) return false;
        if (Nlu.has(text, "ทราย", "sand", "glass", "กระจก")) return s.is(ItemTags.SAND);
        if (Nlu.has(text, "ถ่าน", "charcoal")) return s.is(ItemTags.LOGS);
        if (Nlu.has(text, "หิน", "stone")) return s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.STONE);
        if (Nlu.has(text, "อิฐ", "brick")) return s.is(Items.CLAY_BALL) || s.is(Items.NETHERRACK);
        String want = ItemWords.resolve(text);
        if (want != null) return BuiltInRegistries.ITEM.getKey(out.getItem()).toString().equals(want) || id.equals(want.replace("minecraft:", ""));
        return id.startsWith("raw_") || id.endsWith("_ore");
    }

    void tickSmelt() {
        if (h.tickCount % 10 != 0) return;
        String text = spec.getString("text");
        int max = spec.contains("n") ? spec.getInt("n") : 2304;
        if (count >= max) {
            finishSmelt();
            return;
        }
        int slot = -1;
        for (int i = 0; i < h.bag.getContainerSize(); i++) if (smeltWanted(h.bag.getItem(i), text)) {
            slot = i;
            break;
        }
        if (slot < 0) {
            // ores waiting in a chest nearby?
            if (phase == 0) {
                phase = 1;
                List<Bag.Store> stores = Bag.containersNear(h.level(), h.blockPosition(), 10);
                if (!stores.isEmpty() && Bag.pull(h, stores, s -> smeltWanted(s, text), Math.min(max - count, 256)) > 0) return;
            }
            finishSmelt();
            return;
        }
        if (!b.care.burn(1)) {
            Say.owner(h, "ถลุงต่อไม่ได้ เชื้อเพลิงหมด ใส่ §eถ่านหิน§r หรือไม้ในกระเป๋าผมหน่อยนะ", "I'm out of fuel. Put some §ecoal§r or wood in my bag");
            finishSmelt();
            return;
        }
        ItemStack in = h.bag.getItem(slot);
        ItemStack out = Crafter.smeltResult(h.level(), in).copy();
        in.shrink(1);
        Bag.addOrDrop(h, out, null);
        h.bag.setChanged();
        count++;
        h.setWorking(true);
        if (count % 4 == 1) {
            h.level().playSound(null, h.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 0.8f, 1f);
            if (h.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.FLAME, h.getX(), h.getY() + 1.2, h.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
        }
        h.addXp(0.1f);
    }

    void finishSmelt() {
        if (count > 0) Say.owner(h, "ถลุงเสร็จแล้ว §e" + count + "§r ชิ้น อยู่ในกระเป๋าผมนะ", "Smelted §e" + count + "§r items, they're in my bag");
        else Say.owner(h, "ไม่มีอะไรให้ถลุงเลย (ใส่แร่ดิบ ทราย หรือหินในกระเป๋าผม แล้วสั่งใหม่นะ)", "Nothing to smelt (put raw ores, sand or cobblestone in my bag and ask again)");
        done(true);
    }

    // ------------------------------------------------------------------ fetch from chests
    void tickFetch() {
        String text = spec.getString("text");
        String id = ItemWords.resolve(text);
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(HelperBuddy.rl(id));
        if (item == null || item == Items.AIR) {
            Say.owner(h, "ไม่แน่ใจว่าจะให้หยิบอะไร ลองบอกชื่อของให้ชัดๆ อีกทีนะ", "I'm not sure what to fetch. Tell me the item name again");
            done(false);
            return;
        }
        int n = spec.contains("n") ? spec.getInt("n") : 64;
        List<Bag.Store> stores = Bag.containersNear(h.level(), h.blockPosition(), 12);
        if (stores.isEmpty() && h.home() != null) stores = Bag.containersNear(h.level(), h.home(), 12);
        ServerPlayer p = h.owner();
        if (stores.isEmpty() && p != null && p.level() == h.level()) stores = Bag.containersNear(h.level(), p.blockPosition(), 12);
        final Item it = item;
        int got = Bag.pull(h, stores, s -> s.is(it), n);
        String name = new ItemStack(item).getHoverName().getString();
        if (got <= 0) {
            Say.owner(h, "ไม่เจอ " + name + " ในหีบแถวนี้เลย", "No " + name + " in the chests around here");
            done(false);
            return;
        }
        deliver(item, got);
        Say.owner(h, "เอา " + name + " มาให้แล้ว §e" + got + "§r ชิ้น!", "Here are §e" + got + " " + name + "§r!");
        done(false);
    }

    // ------------------------------------------------------------------ breed: feeds pairs of grown-up animals
    void tickBreed() {
        String only = spec.contains("animal") ? spec.getString("animal") : null;
        if (animal == null || !animal.isAlive() || !(animal instanceof Animal a0) || !a0.canFallInLove()) {
            animal = null;
            for (Animal a : h.level().getEntitiesOfClass(Animal.class, new AABB(h.blockPosition()).inflate(24, 6, 24),
                    a -> a.isAlive() && !a.isBaby() && a.canFallInLove() && a.getAge() == 0 && (only == null || FoodJob.matches(a, only)))) {
                if (foodFor(a) >= 0) {
                    animal = a;
                    break;
                }
            }
            if (animal == null) {
                Say.owner(h, count > 0 ? "ให้อาหารสัตว์ไป " + count + " ตัวแล้ว เดี๋ยวลูกๆ จะออกมานะ" : "ไม่มีสัตว์ที่ผสมพันธุ์ได้ หรือไม่มีอาหารของมันในกระเป๋าผม (เช่น ข้าวสาลี แครอท เมล็ดพืช)",
                        count > 0 ? "Fed " + count + " animals, babies are on the way" : "No animals ready to breed, or I have no food for them (wheat, carrots, seeds)");
                done(true);
                return;
            }
        }
        Animal a = (Animal) animal;
        Mover.Res r = b.mover().moveTo(a, 1.1, 1.6);
        if (r == Mover.Res.NO_PATH || b.mover().stuck(80)) {
            animal = null;
            return;
        }
        if (h.distanceTo(a) > 2.6) return;
        int slot = foodFor(a);
        if (slot < 0) {
            animal = null;
            return;
        }
        h.bag.getItem(slot).shrink(1);
        h.bag.setChanged();
        a.setInLove(null);
        h.swing(InteractionHand.MAIN_HAND);
        h.level().playSound(null, a.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6f, 1.2f);
        count++;
        animal = null;
    }

    int foodFor(Animal a) {
        for (int i = 0; i < h.bag.getContainerSize(); i++) if (a.isFood(h.bag.getItem(i))) return i;
        return -1;
    }

    // ------------------------------------------------------------------ shear
    void tickShear() {
        if (!Bag.equip(h, Bag.Kind.SHEARS)) {
            Reach.needTool(h, Bag.Kind.SHEARS);
            done(true);
            return;
        }
        if (animal == null || !animal.isAlive() || !(animal instanceof Sheep s0) || !s0.readyForShearing()) {
            animal = null;
            for (Sheep s : h.level().getEntitiesOfClass(Sheep.class, new AABB(h.blockPosition()).inflate(24, 6, 24), s -> s.readyForShearing())) {
                animal = s;
                break;
            }
            if (animal == null) {
                b.work.pickup(st -> st.is(ItemTags.WOOL), 10);
                Say.owner(h, count > 0 ? "ตัดขนแกะเสร็จแล้ว " + count + " ตัว" : "แถวนี้ไม่มีแกะที่ตัดขนได้", count > 0 ? "Sheared " + count + " sheep" : "No sheep to shear around here");
                done(true);
                return;
            }
        }
        Sheep s = (Sheep) animal;
        Mover.Res r = b.mover().moveTo(s, 1.1, 1.6);
        if (r == Mover.Res.NO_PATH || b.mover().stuck(80)) {
            animal = null;
            return;
        }
        if (h.distanceTo(s) > 2.6) return;
        s.shear(SoundSource.NEUTRAL);
        Compat.hurtItem(h.tool(), 1, h, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.swing(InteractionHand.MAIN_HAND);
        count++;
        animal = null;
        b.work.pickup(st -> st.is(ItemTags.WOOL), 6);
    }

    // ------------------------------------------------------------------ torches in the dark
    void tickTorch() {
        if (h.tickCount % 5 != 0) return;
        Level l = h.level();
        if (Bag.count(h, Items.TORCH) <= 0) {
            Say.owner(h, count > 0 ? "ปักคบเพลิงไป " + count + " อัน คบเพลิงหมดแล้ว" : "ผมไม่มีคบเพลิงเลย ใส่ §eคบเพลิง§r ในกระเป๋าผมก่อนนะ (หรือให้ถ่าน+ไม้ เดี๋ยวผมคราฟต์เอง)",
                    count > 0 ? "Placed " + count + " torches, I'm out of them now" : "I have no torches. Put some §etorches§r in my bag (or coal and sticks, I'll craft them)");
            if (count == 0 && Crafter.craft(h, Items.TORCH, 16, 0).made() > 0) return;
            done(true);
            return;
        }
        if (todo.isEmpty() && phase == 0) {
            phase = 1;
            BlockPos c = at == null ? h.blockPosition() : at;
            for (int dx = -16; dx <= 16; dx += 1)
                for (int dz = -16; dz <= 16; dz += 1)
                    for (int dy = -4; dy <= 4; dy++) {
                        BlockPos p = c.offset(dx, dy, dz);
                        if (!Mover.standable(l, p)) continue;
                        if (l.getBrightness(LightLayer.BLOCK, p) >= 8 || l.canSeeSky(p) && l.isDay()) continue;
                        if (!Blocks.TORCH.defaultBlockState().canSurvive(l, p)) continue;
                        todo.add(p);
                    }
            todo.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(c)));
        }
        while (!todo.isEmpty() && l.getBrightness(LightLayer.BLOCK, todo.get(0)) >= 8) todo.remove(0);
        if (todo.isEmpty()) {
            Say.owner(h, count > 0 ? "ปักคบเพลิงเสร็จแล้ว " + count + " อัน แถวนี้สว่างแล้ว!" : "แถวนี้สว่างอยู่แล้วครับ", count > 0 ? "Placed " + count + " torches, it's bright now!" : "It's already bright around here");
            done(true);
            return;
        }
        BlockPos p = todo.get(0);
        Reach.St r = reach.tick(p, 4.5, true);
        if (r == Reach.St.FAIL) {
            todo.remove(0);
            return;
        }
        if (r != Reach.St.READY) return;
        todo.remove(0);
        if (!l.getBlockState(p).isAir() || !Blocks.TORCH.defaultBlockState().canSurvive(l, p)) return;
        l.setBlock(p, Blocks.TORCH.defaultBlockState(), 3);
        Bag.take(h, Items.TORCH, 1);
        h.swing(InteractionHand.MAIN_HAND);
        l.playSound(null, p, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7f, 1f);
        count++;
    }

    // ------------------------------------------------------------------ flatten: natural blocks above the ground go, holes are filled
    void tickFlatten() {
        Level l = h.level();
        if (phase == 0) {
            phase = 1;
            BlockPos c = at == null ? h.blockPosition() : at;
            int r = spec.contains("r") ? spec.getInt("r") : 7;
            for (int dy = 8; dy >= 0; dy--)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos p = c.offset(dx, dy, dz);
                        BlockState s = l.getBlockState(p);
                        if (!s.isAir() && s.getFluidState().isEmpty() && Jobs.natural(l, p, s) && Breaker.breakable(h, p, s)) todo.add(p);
                    }
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = c.offset(dx, -1, dz);
                    if (Mover.passable(l, p) || !l.getFluidState(p).isEmpty()) todo.add(p.immutable());
                }
        }
        int budget = 6;
        while (budget-- > 0 && !todo.isEmpty()) {
            BlockPos p = todo.remove(0);
            BlockState s = l.getBlockState(p);
            BlockPos c = at == null ? h.blockPosition() : at;
            if (p.getY() < c.getY()) {
                // fill a hole in the floor
                if (!Mover.passable(l, p) && l.getFluidState(p).isEmpty()) continue;
                boolean free = !h.settings.get("needBlocks");
                Item fillItem = Bag.count(h, Items.DIRT) > 0 ? Items.DIRT : Bag.count(h, Items.COBBLESTONE) > 0 ? Items.COBBLESTONE : null;
                if (fillItem == null && !free) continue;
                if (fillItem != null) Bag.take(h, fillItem, 1);
                l.setBlock(p, (fillItem == Items.COBBLESTONE ? Blocks.COBBLESTONE : Blocks.DIRT).defaultBlockState(), 3);
            } else {
                if (s.isAir() || !Jobs.natural(l, p, s)) continue;
                Breaker.harvest(h, p, s, true);
            }
            count++;
            h.setWorking(true);
            if (count % 3 == 0) h.swing(InteractionHand.MAIN_HAND);
        }
        BlockPos c = at == null ? h.blockPosition() : at;
        if (h.position().distanceToSqr(Vec3.atBottomCenterOf(c)) > 9) b.mover().moveTo(c, 1.1, 1.5);
        if (todo.isEmpty()) {
            Say.owner(h, "ปรับพื้นที่เสร็จแล้ว! (" + count + " บล็อก)", "The ground is flat now! (" + count + " blocks)");
            h.addXp(5);
            done(true);
        }
    }

    // ------------------------------------------------------------------ save
    void save(CompoundTag jobs) {
        if (spec == null) {
            jobs.remove("task");
            return;
        }
        CompoundTag t = spec.copy();
        t.putInt("_count", count);
        if (at != null) t.putLong("_at", at.asLong());
        jobs.put("task", t);
    }

    void load(CompoundTag jobs) {
        if (!jobs.contains("task")) {
            spec = null;
            return;
        }
        spec = jobs.getCompound("task").copy();
        count = spec.getInt("_count");
        at = spec.contains("_at") ? BlockPos.of(spec.getLong("_at")) : null;
        phase = 0;
        todo.clear();
    }
}
