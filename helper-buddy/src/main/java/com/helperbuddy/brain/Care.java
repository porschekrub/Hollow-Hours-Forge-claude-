package com.helperbuddy.brain;

import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.talk.Say;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraftforge.common.ForgeHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/** Eating, being fed, cooking food in the bag, and going to find food when hungry. */
public final class Care {
    final Brain b;
    final HelperEntity h;
    long hungryUntil = -1;
    float fuelCredit;

    Care(Brain b) {
        this.b = b;
        this.h = b.h;
    }

    // ------------------------------------------------------------------ food values
    static final Set<net.minecraft.world.item.Item> BAD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
            Items.SUSPICIOUS_STEW, Items.CHORUS_FRUIT);
    static final Set<net.minecraft.world.item.Item> PRECIOUS = Set.of(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT);

    /** Nutrition (0 = not food, -1 = rotten flesh, refused). */
    public static int foodValue(ItemStack st) {
        if (st.isEmpty()) return 0;
        if (st.is(Items.ROTTEN_FLESH)) return -1;
        if (st.is(Items.ENCHANTED_GOLDEN_APPLE)) return 20;
        if (st.is(Items.GOLDEN_APPLE)) return 10;
        FoodProperties f = st.getFoodProperties(null);
        if (f == null) return 0;
        return Math.max(1, f.getNutrition());
    }

    /** Heal amount of one piece: each food point = 5% of max health (bread = 25%, steak = 40%). */
    static float healOf(HelperEntity h, int value) {
        return value * h.maxHp() * 0.05f;
    }

    /** The owner feeds the helper with the item in hand. */
    public static void feed(ServerPlayer p, HelperEntity h, ItemStack held) {
        int n = foodValue(held);
        if (n == 0) return;
        if (n < 0) {
            Say.to(p, h, "§cไม่เอาเนื้อเน่านะครับ!§r กินแล้วท้องเสีย ขออย่างอื่นได้ไหม", "§cNo rotten flesh, please!§r Anything else?");
            return;
        }
        if (!h.isKO() && h.getHealth() >= h.getMaxHealth() - 0.5f) {
            Say.to(p, h, "อิ่มแล้วครับ เลือดเต็มแล้ว", "I'm full, my health is already full");
            return;
        }
        float before = h.isKO() ? h.koHp : h.getHealth();
        boolean wasKO = h.isKO();
        h.healHp(healOf(h, n));
        if (!p.getAbilities().instabuild) {
            ItemStack rem = held.getCraftingRemainingItem();
            held.shrink(1);
            if (!rem.isEmpty()) Bag.giveTo(p, rem);
        }
        effects(h);
        float after = h.isKO() ? h.koHp : h.getHealth();
        if (h.isKO())
            Say.to(p, h, "อร่อย! (เลือด " + (int) after + "/" + h.maxHp() + ") ยังลุกไม่ไหว ต้องได้อีก " + (int) Math.ceil(h.maxHp() * 0.1 - after) + " ถึงจะฟื้น",
                    "Yummy! (" + (int) after + "/" + h.maxHp() + ") I still can't get up, I need " + (int) Math.ceil(h.maxHp() * 0.1 - after) + " more");
        else if (!wasKO)
            Say.to(p, h, "ขอบคุณครับ อร่อยมาก! (เลือด " + (int) before + " → " + (int) after + "/" + h.maxHp() + ")",
                    "Thank you, delicious! (" + (int) before + " → " + (int) after + "/" + h.maxHp() + ")");
    }

    static void effects(HelperEntity h) {
        h.level().playSound(null, h.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.9f, 1f);
        h.level().playSound(null, h.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 0.5f, 1.1f);
        if (h.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HEART, h.getX(), h.getY() + 1.6, h.getZ(), 4, 0.4, 0.3, 0.4, 0);
        h.act("eat");
    }

    // ------------------------------------------------------------------ eating from the bag
    @Nullable
    ItemStack bestFood(boolean allowPrecious) {
        float missing = h.getMaxHealth() - h.getHealth();
        ItemStack fit = null, small = null;
        int fitV = -1, smallV = Integer.MAX_VALUE;
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.isEmpty() || BAD.contains(s.getItem()) || (!allowPrecious && PRECIOUS.contains(s.getItem()))) continue;
            int v = foodValue(s);
            if (v <= 0) continue;
            if (healOf(h, v) <= missing + h.maxHp() * 0.05f && v > fitV) {
                fitV = v;
                fit = s;
            }
            if (v < smallV) {
                smallV = v;
                small = s;
            }
        }
        return fit != null ? fit : small;
    }

    public boolean hasFood(boolean allowPrecious) {
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (!s.isEmpty() && !BAD.contains(s.getItem()) && (allowPrecious || !PRECIOUS.contains(s.getItem())) && foodValue(s) > 0) return true;
        }
        return false;
    }

    /** Eats one piece of food from its own bag. */
    public boolean eatFromBag(boolean announce) {
        if (h.isKO() || h.getHealth() >= h.getMaxHealth() - 0.5f) return false;
        ItemStack f = bestFood(h.getHealth() < h.getMaxHealth() * 0.2f);
        if (f == null) return false;
        ItemStack eaten = f.copyWithCount(1);
        h.healHp(healOf(h, foodValue(f)));
        ItemStack rem = f.getCraftingRemainingItem();
        f.shrink(1);
        if (!rem.isEmpty()) Bag.add(h, rem);
        h.bag.setChanged();
        effects(h);
        if (announce)
            Say.owner(h, Say.mix(h.owner(), "กิน {} ไป 1 ชิ้น (เลือด " + (int) h.getHealth() + "/" + h.maxHp() + ")", "Ate one {} (" + (int) h.getHealth() + "/" + h.maxHp() + " health)", eaten));
        else
            Say.notify(h, "selfeat", 1200, Say.mix(h.owner(), "ผมเจ็บนิดหน่อย เลยกิน {} ในกระเป๋าไปแล้วนะ (เลือด " + (int) h.getHealth() + "/" + h.maxHp() + ")",
                    "I was a bit hurt, so I ate {} from my bag (" + (int) h.getHealth() + "/" + h.maxHp() + " health)", eaten));
        return true;
    }

    // ------------------------------------------------------------------ cooking (with fuel from the bag)
    public static boolean isRawFood(HelperEntity h, ItemStack s) {
        if (s.isEmpty() || foodValue(s) <= 0 && !s.is(Items.POTATO) && !s.is(Items.KELP)) return false;
        Optional<SmeltingRecipe> r = h.level().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(s.copyWithCount(1)), h.level());
        return r.isPresent() && r.get().getResultItem(h.level().registryAccess()).isEdible();
    }

    /** Uses up fuel for `n` items (one coal = 8). Returns false when there is no fuel. */
    boolean burn(int n) {
        while (fuelCredit < n) {
            int slot = -1;
            int best = 0;
            for (int i = 0; i < h.bag.getContainerSize(); i++) {
                ItemStack s = h.bag.getItem(i);
                if (s.isEmpty() || Bag.isTool(s)) continue;
                int bt = ForgeHooks.getBurnTime(s, RecipeType.SMELTING);
                if (bt <= 0 || s.is(Items.LAVA_BUCKET)) continue;
                // prefer coal/charcoal, then other fuels (not planks/logs if the player may want them: they are used last)
                int pref = s.is(Items.COAL) || s.is(Items.CHARCOAL) ? 3 : s.is(Items.COAL_BLOCK) || s.is(Items.BLAZE_ROD) ? 2 : 1;
                if (pref > best) {
                    best = pref;
                    slot = i;
                }
            }
            if (slot < 0) return false;
            ItemStack s = h.bag.getItem(slot);
            fuelCredit += ForgeHooks.getBurnTime(s, RecipeType.SMELTING) / 200f;
            ItemStack rem = s.getCraftingRemainingItem();
            s.shrink(1);
            if (!rem.isEmpty()) Bag.add(h, rem);
        }
        fuelCredit -= n;
        return true;
    }

    /** Cooks raw food in the bag (and makes bread from wheat). Returns how many were made. */
    public int cookInBag(int max) {
        int made = 0;
        for (int i = 0; i < h.bag.getContainerSize() && made < max; i++) {
            ItemStack s = h.bag.getItem(i);
            if (!isRawFood(h, s)) continue;
            ItemStack out = h.level().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(s.copyWithCount(1)), h.level()).get()
                    .getResultItem(h.level().registryAccess());
            while (!s.isEmpty() && made < max && burn(1)) {
                s.shrink(1);
                Bag.addOrDrop(h, out.copy(), null);
                made++;
            }
        }
        while (made < max && Bag.count(h, Items.WHEAT) >= 3) {
            Bag.take(h, Items.WHEAT, 3);
            Bag.addOrDrop(h, new ItemStack(Items.BREAD), null);
            made++;
        }
        if (made > 0) {
            h.level().playSound(null, h.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 0.8f, 1f);
            if (h.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SMOKE, h.getX(), h.getY() + 1.6, h.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
            h.bag.setChanged();
        }
        return made;
    }

    // ------------------------------------------------------------------ tick
    void tick() {
        if (!h.settings.get("selfCare")) {
            hungryUntil = -1;
            return;
        }
        long now = h.level().getGameTime();
        int slot = (int) ((b.t + h.getId()) % 40);
        boolean fighting = b.combat().fighting();
        float max = h.getMaxHealth();
        if (slot == 7 && !fighting && h.getHealth() < max * 0.5f) eatFromBag(false);
        if (slot == 21 && b.t % 120 < 40) {
            for (int i = 0; i < h.bag.getContainerSize(); i++)
                if (isRawFood(h, h.bag.getItem(i))) {
                    cookInBag(1);
                    break;
                }
        }
        if (hungryUntil >= 0) {
            if (now > hungryUntil || hasFood(false) || !h.mode().working()) {
                hungryUntil = -1;
                if (hasFood(false)) eatFromBag(false);
                Say.notify(h, "hungry:back", 600, "ได้อาหารแล้ว กลับไปทำงานต่อนะ", "Got some food, back to work");
            }
            return;
        }
        if (slot == 13 && h.getHealth() < max * 0.35f && !fighting && !hasFood(true)) {
            if (h.mode().working() && h.mode() != Mode.FOOD) {
                hungryUntil = now + 20 * 120;
                b.work.end();
                Say.notify(h, "hungry", 1200, "§eผมหิวแล้ว§r (เลือด " + (int) h.getHealth() + "/" + h.maxHp() + ") ขอไปหาอาหารก่อนนะ แล้วจะกลับมาทำงานต่อ",
                        "§eI'm hungry§r (" + (int) h.getHealth() + "/" + h.maxHp() + ") so I'll find some food first, then get back to work");
            } else
                Say.notify(h, "hungry:ask", 2400, "§eผมหิวแล้ว§r (เลือด " + (int) h.getHealth() + "/" + h.maxHp() + ") มีอาหารให้ผมไหม? หรือสั่ง \"หาอาหาร\" ก็ได้",
                        "§eI'm hungry§r (" + (int) h.getHealth() + "/" + h.maxHp() + "). Got some food for me? Or tell me to \"find food\"");
        }
    }

    public boolean goingForFood() {
        return hungryUntil >= 0;
    }

    void cancelHunger() {
        hungryUntil = -1;
    }
}
