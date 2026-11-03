package com.helperbuddy.brain;

import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.Say;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fighting: defends the owner, fights back when hurt (setting "fightBack", on by default), hunts animals for food.
 * Creepers are fought hit-and-run (one hit, step back while it hisses) or shot with a bow when it has one.
 */
public final class Combat {
    final Brain b;
    final HelperEntity h;
    @Nullable
    LivingEntity target;
    boolean hunting;
    long since;
    int attackCd, retreatUntil, bowCd;
    final Map<UUID, Long> unreachable = new HashMap<>();
    @Nullable
    ItemStack stashedTool;

    Combat(Brain b) {
        this.b = b;
        this.h = b.h;
    }

    public boolean fighting() {
        return target != null && target.isAlive() && !hunting;
    }

    @Nullable
    public LivingEntity target() {
        return target;
    }

    void clear() {
        target = null;
        hunting = false;
        restoreTool();
    }

    void dropHunt() {
        if (hunting) clear();
    }

    // ------------------------------------------------------------------ who may be attacked
    boolean canTarget(LivingEntity e, boolean explicit) {
        if (e == null || !e.isAlive() || e == h || e instanceof HelperEntity) return false;
        if (e instanceof Player p) {
            if (h.isOwner(p) || p.isCreative() || p.isSpectator()) return false;
            return explicit && HBConfig.ALLOW_PVP_FIGHT_BACK.get() && h.getServer() != null && h.getServer().isPvpAllowed() && com.helperbuddy.PvpRule.on(h.getServer());
        }
        if (e instanceof AbstractVillager || e instanceof IronGolem || e instanceof SnowGolem) return false;
        if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) return false;
        if (e instanceof TamableAnimal t && t.isTame()) return false;
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return false;
        if (explicit) return true;
        if (!(e instanceof Enemy) && e.getType().getCategory() != MobCategory.MONSTER) return false;
        // neutral mobs (endermen, piglins, zombified piglins) only when they are angry at us
        if (e instanceof NeutralMob nm && e instanceof Mob m) {
            LivingEntity tg = m.getTarget();
            return tg != null && (tg == h || tg instanceof Player p && h.isOwner(p));
        }
        return true;
    }

    public void setTarget(@Nullable LivingEntity e, boolean explicit, boolean hunt) {
        if (e == null) {
            clear();
            return;
        }
        if (h.isKO()) return;
        if (!hunt && !explicit && !h.settings.get("fight")) return;
        if (!canTarget(e, explicit || hunt)) return;
        Long until = unreachable.get(e.getUUID());
        if (until != null && h.level().getGameTime() < until && !explicit) return;
        if (target == e) return;
        if (hunt && fighting()) return; // monsters first
        target = e;
        hunting = hunt;
        since = h.level().getGameTime();
        b.mover().resetProgress();
    }

    /** Looks for monsters close to the helper or its owner. */
    void scan() {
        if (h.isKO() || !h.settings.get("fight") || fighting()) return;
        double r = h.mode() == com.helperbuddy.entity.Mode.GUARD ? 16 : 10;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        ServerPlayer o = h.owner();
        List<Vec3> centers = new java.util.ArrayList<>();
        centers.add(h.position());
        if (o != null && o.level() == h.level() && o.distanceToSqr(h) < 24 * 24) centers.add(o.position());
        for (Vec3 c : centers) {
            for (LivingEntity e : h.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 6, r), e -> canTarget(e, false))) {
                double d = e.distanceToSqr(h);
                if (d < bd && h.hasLineOfSight(e)) {
                    bd = d;
                    best = e;
                }
            }
        }
        if (best != null) setTarget(best, false, false);
    }

    /** Something hurt the helper: hit back (setting "fightBack", default on). */
    public void onHurt(DamageSource src) {
        if (!h.settings.get("fightBack") || h.isKO()) return;
        Entity a = src.getEntity();
        if (a instanceof LivingEntity le && a != h) {
            if (canTarget(le, true)) {
                if (fighting() && target != le && target.distanceToSqr(h) < le.distanceToSqr(h)) return;
                target = le;
                hunting = false;
                since = h.level().getGameTime();
                return;
            }
            return;
        }
        // explosions, arrows from nowhere ...: the closest hostile mob
        if (!fighting()) {
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : h.level().getEntitiesOfClass(LivingEntity.class, h.getBoundingBox().inflate(12), e -> canTarget(e, false))) {
                double d = e.distanceToSqr(h);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
            if (best != null) {
                target = best;
                hunting = false;
                since = h.level().getGameTime();
            }
        }
    }

    // ------------------------------------------------------------------ weapons
    static double meleeScore(ItemStack it) {
        if (it.isEmpty()) return 1;
        double base = 0;
        var mods = it.getAttributeModifiers(EquipmentSlot.MAINHAND).get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        for (var m : mods) base += m.getAmount();
        base += EnchantmentHelper.getDamageBonus(it, net.minecraft.world.entity.MobType.UNDEFINED);
        if (it.getItem() instanceof SwordItem) base += 0.5;
        if (it.isDamageableItem() && it.getMaxDamage() - it.getDamageValue() <= 1) base -= 50;
        return base;
    }

    void equipWeapon() {
        double best = meleeScore(h.tool());
        int slot = -1;
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.isEmpty() || !(s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem || Bag.isTool(s))) continue;
            double sc = meleeScore(s);
            if (sc > best + 0.4) {
                best = sc;
                slot = i;
            }
        }
        if (slot >= 0) {
            if (stashedTool == null) stashedTool = h.tool().copy();
            Bag.swapIntoHand(h, slot);
        }
    }

    void restoreTool() {
        if (stashedTool == null) return;
        ItemStack want = stashedTool;
        stashedTool = null;
        if (ItemStack.isSameItemSameTags(h.tool(), want)) return;
        for (int i = 0; i < h.bag.getContainerSize(); i++)
            if (ItemStack.isSameItem(h.bag.getItem(i), want)) {
                Bag.swapIntoHand(h, i);
                return;
            }
    }

    int bowSlot() {
        for (int i = 0; i < h.bag.getContainerSize(); i++) if (h.bag.getItem(i).getItem() instanceof BowItem) return i;
        return -1;
    }

    boolean hasArrows() {
        return Bag.count(h, s -> s.getItem() instanceof ArrowItem) > 0;
    }

    // ------------------------------------------------------------------ tick
    /** Fights the current target. Returns true while busy fighting. */
    boolean tick() {
        if (target == null) return false;
        LivingEntity t = target;
        long now = h.level().getGameTime();
        boolean alive = t.isAlive() && t.level() == h.level() && !t.isRemoved();
        if (!alive || t.distanceToSqr(h) > 32 * 32 || now - since > 20 * 90 || (!hunting && !h.settings.get("fight") && !h.settings.get("fightBack"))
                || t instanceof Player p && (p.isCreative() || p.isSpectator())) {
            if (!alive && t.isDeadOrDying()) onKill(t);
            clear();
            b.mover().stop();
            return false;
        }
        if (attackCd > 0) attackCd--;
        if (bowCd > 0) bowCd--;
        b.mover().look(t.getEyePosition());
        double d = h.distanceTo(t);
        boolean creeper = t instanceof Creeper;
        // ---- creepers: never stand next to a hissing one
        if (creeper) {
            Creeper c = (Creeper) t;
            if (c.getSwellDir() > 0 || h.tickCount < retreatUntil) {
                retreat(t, 7.5);
                return true;
            }
        }
        // ---- bow: creepers, flying mobs, or targets it cannot walk to
        int bow = bowSlot();
        boolean ranged = bow >= 0 && hasArrows() && (creeper || !t.onGround() && !t.isInWater() || d > 5 && b.mover().stuck(40));
        if (ranged && d > 4 && d < 20 && h.hasLineOfSight(t)) {
            b.mover().stop();
            if (bowCd <= 0) shoot(t, bow);
            return true;
        }
        double reach = creeper ? 3.1 : 2.4 + t.getBbWidth() / 2;
        if (d > reach || Math.abs(t.getY() - h.getY()) > 2.2) {
            Mover.Res r = b.mover().moveTo(t, hunting ? 1.2 : 1.35, reach - 0.4);
            if (r == Mover.Res.NO_PATH || b.mover().stuck(60)) {
                unreachable.put(t.getUUID(), now + 20 * 15);
                if (unreachable.size() > 200) unreachable.clear();
                clear();
                b.mover().stop();
                return false;
            }
            return true;
        }
        b.mover().stop();
        if (attackCd > 0) return true;
        equipWeapon();
        boolean wasAlive = t.isAlive();
        h.swing(InteractionHand.MAIN_HAND);
        h.doHurtTarget(t);
        if (creeper) {
            // extra shove so it does not get close enough to blow up, then back off
            Vec3 dir = t.position().subtract(h.position()).normalize();
            t.push(dir.x * 0.9, 0.25, dir.z * 0.9);
            retreatUntil = h.tickCount + 16;
        }
        if (!h.tool().isEmpty() && h.tool().isDamageableItem() && Bag.damageTool(h))
            Say.notify(h, "weaponbroke", 40, "§cอาวุธพังแล้ว!§r ส่งอาวุธ/เครื่องมือใหม่มาให้หน่อยนะ", "§cMy weapon broke!§r Send me a new one, please");
        attackCd = creeper ? 10 : (int) Math.max(8, 20 / Math.max(1.0, attackSpeed()));
        if (wasAlive && !t.isAlive()) onKill(t);
        return true;
    }

    double attackSpeed() {
        double sp = 4;
        var mods = h.tool().getAttributeModifiers(EquipmentSlot.MAINHAND).get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        for (var m : mods) sp += m.getAmount();
        return Math.max(0.6, sp);
    }

    void retreat(LivingEntity from, double dist) {
        Vec3 away = h.position().subtract(from.position());
        if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
        Vec3 goal = from.position().add(away.normalize().scale(dist));
        b.mover().moveTo(goal, 1.45, 1.0);
    }

    void shoot(LivingEntity t, int bowSlot) {
        ItemStack bow = h.bag.getItem(bowSlot);
        int arrowSlot = -1;
        for (int i = 0; i < h.bag.getContainerSize(); i++) if (h.bag.getItem(i).getItem() instanceof ArrowItem) {
            arrowSlot = i;
            break;
        }
        if (arrowSlot < 0) return;
        ItemStack arrowStack = h.bag.getItem(arrowSlot);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(h, arrowStack.copyWithCount(1), 1f);
        int power = bow.getEnchantmentLevel(Enchantments.POWER_ARROWS);
        if (power > 0) arrow.setBaseDamage(arrow.getBaseDamage() + power * 0.5 + 0.5);
        if (bow.getEnchantmentLevel(Enchantments.FLAMING_ARROWS) > 0) arrow.setSecondsOnFire(100);
        double dx = t.getX() - h.getX(), dz = t.getZ() - h.getZ();
        double dy = t.getY(0.3333) - arrow.getY();
        double dist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + dist * 0.2, dz, 1.6f, 6f);
        boolean infinity = bow.getEnchantmentLevel(Enchantments.INFINITY_ARROWS) > 0 && arrowStack.is(Items.ARROW);
        if (infinity) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        else arrowStack.shrink(1);
        h.level().addFreshEntity(arrow);
        h.level().playSound(null, h.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1f, 1f / (h.getRandom().nextFloat() * 0.4f + 0.8f));
        bow.hurtAndBreak(1, h, e -> {});
        h.act("shoot");
        bowCd = 22;
    }

    void onKill(LivingEntity t) {
        h.addXp(Math.max(3, Math.min(50, Math.round(t.getMaxHealth() / 2))));
        b.report("kill", t);
        if (hunting) b.food.afterKill(t);
    }
}
