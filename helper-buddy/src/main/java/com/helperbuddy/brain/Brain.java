package com.helperbuddy.brain;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.config.HBConfig;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.entity.Mode;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** The helper's mind: one tick at a time it fights, eats, follows orders and works. */
public final class Brain {
    public final HelperEntity h;
    private final Mover mover;
    final Breaker breaker;
    private final Combat combat;
    final Care care;
    final WorkJob work;
    final FoodJob food;
    final DigJob dig;
    final BuildJob build;
    final Plan plan;
    final Tasks tasks;
    final FarmJob farm;
    private final Map<String, Long> cooldowns = new HashMap<>();
    long t;
    private int followTpCd;

    public Brain(HelperEntity h) {
        this.h = h;
        mover = new Mover(h);
        breaker = new Breaker(h);
        combat = new Combat(this);
        care = new Care(this);
        work = new WorkJob(this);
        food = new FoodJob(this);
        dig = new DigJob(this);
        build = new BuildJob(this);
        plan = new Plan(this);
        tasks = new Tasks(this);
        farm = new FarmJob(this);
        load(h.jobs);
    }

    public Mover mover() {
        return mover;
    }

    public Combat combat() {
        return combat;
    }

    public Breaker breaker() {
        return breaker;
    }

    public BuildJob build() {
        return build;
    }

    public DigJob dig() {
        return dig;
    }

    public WorkJob work() {
        return work;
    }

    public FoodJob food() {
        return food;
    }

    public Plan plan() {
        return plan;
    }

    public Tasks tasks() {
        return tasks;
    }

    public FarmJob farm() {
        return farm;
    }

    public Care care() {
        return care;
    }

    public ServerLevel level() {
        return (ServerLevel) h.level();
    }

    /** True at most once every `ticks` for this key. */
    public boolean cooldown(String key, int ticks) {
        long now = h.level().getGameTime();
        Long last = cooldowns.get(key);
        if (last != null && now - last < ticks) return false;
        cooldowns.put(key, now);
        return true;
    }

    public static int radius() {
        return HBConfig.SEARCH_RADIUS.get();
    }

    // ------------------------------------------------------------------ main tick
    public void tick() {
        t++;
        if (h.isKO()) {
            mover.stop();
            breaker.cancel();
            combat.clear();
            if (t % 20 == 0) h.setStatus("");
            return;
        }
        if (t % 20 == 0) updateStatus();
        care.tick();
        plan.tick();
        if (t % 10 == 3) combat.scan();
        if (combat.tick()) {
            if (breaker.busy() && !breaker.target().closerToCenterThan(h.position(), 5)) breaker.cancel();
            return;
        }
        if (care.goingForFood()) {
            food.tick(true, true, null);
            return;
        }
        if (h.isSitting() && h.mode() != Mode.STAY) h.setSitting(false);
        switch (h.mode()) {
            case FOLLOW -> tickFollow();
            case STAY -> {
                mover.stop();
                ServerPlayer o = h.owner();
                if (o != null && o.level() == h.level() && o.distanceToSqr(h) < 64) mover.look(o.getEyePosition());
            }
            case GUARD -> tickGuard();
            case MINE, CHOP, GATHER -> work.tick(h.mode());
            case FOOD -> food.tickMode();
            case GOTO -> tickGoto();
            case DIG -> dig.tick();
            case BUILD -> build.tick();
            case FARM -> farm.tick();
            case TASK -> tasks.tick();
        }
    }

    void updateStatus() {
        String s = "";
        boolean th = Say.thai(h.owner());
        String ps = plan.statusShort(th);
        if (ps != null) s = ps;
        else switch (h.mode()) {
            case BUILD -> s = build.statusShort(th);
            case DIG -> s = dig.statusShort();
            case MINE, CHOP, GATHER -> {
                ItemStack tl = h.tool();
                s = tl.isEmpty() ? (th ? "ไม่มีเครื่องมือ" : "no tool") : tl.isDamageableItem() ? (tl.getMaxDamage() - tl.getDamageValue()) + "/" + tl.getMaxDamage() : "";
            }
            case TASK -> s = tasks.statusShort(th);
            default -> {
            }
        }
        if (care.goingForFood()) s = th ? "หิว ไปหาอาหาร" : "hungry, finding food";
        h.setStatus(s);
    }

    // ------------------------------------------------------------------ follow
    void tickFollow() {
        ServerPlayer o = h.owner();
        if (o == null) {
            mover.stop();
            return;
        }
        boolean tp = HBConfig.TELEPORT_TO_OWNER.get() && h.settings.get("teleport");
        if (o.level() != h.level()) {
            if (tp && followTpCd-- <= 0) {
                followTpCd = 60;
                mover.bringTo(o);
            }
            return;
        }
        double d = h.distanceTo(o);
        if (d > 28 && tp) {
            if (followTpCd-- <= 0) {
                followTpCd = 20;
                mover.teleportNear(o.blockPosition());
            }
            return;
        }
        if (d > 3.2) {
            Mover.Res r = mover.moveTo(o, d > 12 ? 1.45 : 1.15, 2.4);
            if (tp && d > 9 && (r == Mover.Res.NO_PATH || mover.stuck(80))) mover.teleportNear(o.blockPosition());
        } else {
            mover.stop();
            mover.look(o.getEyePosition());
        }
    }

    // ------------------------------------------------------------------ guard: stays at its post and fights everything that comes
    void tickGuard() {
        BlockPos home = h.home() != null ? h.home() : h.blockPosition();
        if (h.home() == null) h.setHome(home);
        if (h.blockPosition().distSqr(home) > 9) {
            if (mover.moveTo(home, 1.1, 1.2) == Mover.Res.NO_PATH || mover.stuck(100)) mover.teleportNear(home);
        } else mover.stop();
    }

    // ------------------------------------------------------------------ go to a place
    public void setGoto(BlockPos pos, ResourceKey<Level> dim) {
        CompoundTag g = new CompoundTag();
        g.putLong("pos", pos.asLong());
        g.putString("dim", dim.location().toString());
        h.jobs.put("goto", g);
        h.setMode(Mode.GOTO);
        mover.resetProgress();
    }

    public boolean hasGoto() {
        return h.jobs.contains("goto");
    }

    void tickGoto() {
        CompoundTag g = h.jobs.getCompound("goto");
        if (!h.jobs.contains("goto")) {
            h.setMode(Mode.STAY);
            return;
        }
        BlockPos pos = BlockPos.of(g.getLong("pos"));
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, HelperBuddy.rl(g.getString("dim")));
        if (!h.level().dimension().equals(dim)) {
            ServerLevel dl = h.getServer() == null ? null : h.getServer().getLevel(dim);
            if (dl != null && cooldown("gototp", 40)) {
                BlockPos spot = Mover.safeSpotNear(dl, pos, 4);
                Vec3 at = Vec3.atBottomCenterOf(spot != null ? spot : pos);
                h.changeDimension(dl, new net.minecraftforge.common.util.ITeleporter() {
                    @Override
                    public net.minecraft.world.entity.Entity placeEntity(net.minecraft.world.entity.Entity e, ServerLevel c, ServerLevel to, float yaw,
                                                                          java.util.function.Function<Boolean, net.minecraft.world.entity.Entity> re) {
                        net.minecraft.world.entity.Entity n = re.apply(false);
                        n.moveTo(at.x, at.y, at.z);
                        return n;
                    }
                });
            }
            return;
        }
        double dxz = Math.hypot(h.getX() - (pos.getX() + 0.5), h.getZ() - (pos.getZ() + 0.5));
        if (dxz <= 1.8 && Math.abs(h.getY() - pos.getY()) <= 3) {
            arrive(pos);
            return;
        }
        if (dxz > 48) {
            mover.teleportNear(pos);
            return;
        }
        Mover.Res r = mover.moveTo(pos, 1.2, 1.2);
        if (r == Mover.Res.NO_PATH || mover.stuck(100)) {
            if (!mover.teleportNear(pos)) arrive(pos);
        }
    }

    void arrive(BlockPos pos) {
        mover.stop();
        h.jobs.remove("goto");
        h.setMode(Mode.STAY);
        if (!plan.active()) Say.owner(h, "ถึงแล้วครับ (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")", "I'm there (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
    }

    // ------------------------------------------------------------------ hooks
    /** Progress events for multi-step orders ("chop 20 logs"). */
    public void report(String ev, Object what) {
        plan.onProgress(ev, what);
    }

    /** Items the current job collects on purpose (never thrown away as junk). */
    public boolean wanted(ItemStack st) {
        return h.mode() == Mode.GATHER && work.wantsItem(st);
    }

    public void onBagFull() {
        if (!Bag.isFull(h)) return;
        if (h.settings.get("chest")) {
            BlockPos at = h.blockPosition();
            var stores = Bag.containersNear(h.level(), at, 8);
            if (stores.isEmpty() && h.home() != null && h.home().distSqr(at) < 144) stores = Bag.containersNear(h.level(), h.home(), 8);
            if (!stores.isEmpty()) {
                int moved = Bag.deposit(h, stores, s -> Bag.isTool(s) || s.is(net.minecraft.world.item.Items.TORCH) || Care.foodValue(s) > 0);
                if (moved > 0) {
                    Say.notify(h, "deposit", 600, "กระเป๋าเต็ม เลยเอาของ " + moved + " ชิ้นไปเก็บในหีบใกล้ๆ ให้แล้ว", "My bag was full, so I put " + moved + " items in a chest nearby");
                    return;
                }
            }
        }
        Say.notify(h, "full", 1200, "§eกระเป๋าเต็มแล้ว!§r ผมหยุดพักก่อนนะ (ย่อแล้วคลิกขวาที่ตัวผมเพื่อเปิดกระเป๋า หรือวางหีบไว้ใกล้ๆ)",
                "§eMy bag is full!§r I'll pause (sneak + right-click me to open my bag, or put a chest nearby)");
    }

    public void onKnockOut() {
        mover.stop();
        breaker.cancel();
        combat.clear();
        h.setWorking(false);
    }

    /** Leaves the current job: a house stays paused, a mine job stays saved, plans are dropped. */
    public void leaveJob() {
        breaker.cancel();
        mover.stop();
        work.end();
        food.end();
        tasks.end();
        plan.clear();
        care.cancelHunger();
        combat.dropHunt();
        h.jobs.remove("goto");
        h.setWorking(false);
    }

    // ------------------------------------------------------------------ save
    public void save(CompoundTag jobs) {
        build.save(jobs);
        dig.save(jobs);
        plan.save(jobs);
        work.save(jobs);
        food.save(jobs);
        tasks.save(jobs);
        farm.save(jobs);
    }

    void load(CompoundTag jobs) {
        build.load(jobs);
        dig.load(jobs);
        plan.load(jobs);
        work.load(jobs);
        food.load(jobs);
        tasks.load(jobs);
        farm.load(jobs);
    }

    public Level levelRaw() {
        return h.level();
    }
}
