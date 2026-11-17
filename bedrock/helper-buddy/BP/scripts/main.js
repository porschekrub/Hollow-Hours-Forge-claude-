import { CommandPermissionLevel, CustomCommandParamType, CustomCommandStatus, EntityInitializationCause, ItemStack, system, world, } from "@minecraft/server";
import { BOOK_ITEM, bringTo, giveBook } from "./actions.js";
import { getJob, jobProgress, tickBuild } from "./builder.js";
import { HP_PER_LEVEL, MAX_LEVEL, applyDamage, fightBack, initHealth, isKO, ownedHelpersNear, scanThreats, setTarget, tickCombat, tickHealth } from "./combat.js";
import { forgetFoodRuntime, tickFood, tickSelfCare } from "./food.js";
import { forgetMineRuntime, mineStatus, tickDig } from "./minejob.js";
import { forgetPlanRuntime, getPlan, stepProgress, tickGoto, tickPlan } from "./plan.js";
import { steer, stopMoving } from "./move.js";
import { helpers, rescan, track } from "./registry.js";
import { DEFAULT_NAME, dropRuntime, getMode, getName, getOwner, getOwnerName, getSettings, refreshNameTag, rt, setAi, setModeProp, setName, setOwner, setWorking, } from "./state.js";
import { getTool, giveToPlayer, updateHandDisplay } from "./storage.js";
import { durabilityText } from "./tools.js";
import { handleTalkCommand } from "./talk.js";
import { openBook, openMenu } from "./ui.js";
import { CONTRACT_ITEM, HELPER_ID, dist, isPassable, safeBlock, tellOwner } from "./util.js";
import { VERSION } from "./version.js";
import { endTask, tickWork } from "./work.js";
function greet(h, p) {
    tellOwner(p, getName(h), `สวัสดีครับ! ผมเป็นผู้ช่วยของคุณแล้ว §8(Helper Buddy v${VERSION})§r\n` +
        "§7• §fกดที่ตัวผม (หรือตีเบาๆ) เพื่อเปิดเมนู หรือใช้ §eหนังสือสั่งงาน§f สั่งผมจากที่ไหนก็ได้\n" +
        "§7• §fคุยกับผมได้: §e/helper:talk ไปขุดแร่§f หรือกด 'คุยกับผู้ช่วย' ในเมนู • สร้างบ้านได้หลายแบบ: §eบ้านใต้ดิน ปราสาท หอคอย บ้านต้นไม้ อิกลู ปิรามิด§f พิมพ์บอกได้เลย\n" +
        "§7• §fขุดแร่/สร้างเหมืองต้องให้ §eอีเต้อ§f (ดิน/ทรายต้องใช้ §eพลั่ว§f) ตัดไม้ต้องให้ §eขวาน§f — สร้างบ้านไม่ต้องใช้บล็อก\n" +
        "§7• §fผมช่วยสู้ได้ ถ้าเลือดหมดผมจะ§cสลบ§f (ไม่ตาย) ให้อาหารอะไรก็ได้ยกเว้นเนื้อเน่าจนเลือดถึง 10% ผมก็ลุกได้\n" +
        `§7• §fผมมี§bเลเวล 1-${MAX_LEVEL}§f (เลือด ${HP_PER_LEVEL}-${MAX_LEVEL * HP_PER_LEVEL}) เลเวลขึ้นจากการทำงานและต่อสู้`);
}
function initHelper(h) {
    if (!h.isValid)
        return;
    initHealth(h);
    if (!getOwnerName(h)) {
        let best;
        let bd = 16;
        for (const p of h.dimension.getPlayers({ location: h.location, maxDistance: 16 })) {
            const d = dist(p.location, h.location);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (typeof h.getDynamicProperty("h:name") !== "string")
            setName(h, DEFAULT_NAME);
        if (best) {
            setOwner(h, best);
            greet(h, best);
            giveBook(best);
        }
        setModeProp(h, "follow");
    }
    setAi(h, getMode(h) === "stay" ? "stay" : "follow");
    refreshNameTag(h);
    updateHandDisplay(h, true);
}
world.afterEvents.entitySpawn.subscribe((ev) => {
    const e = ev.entity;
    if (e.typeId !== HELPER_ID)
        return;
    track(e);
    if (ev.cause === EntityInitializationCause.Loaded)
        return;
    system.runTimeout(() => initHelper(e), 2);
});
world.afterEvents.entityLoad.subscribe((ev) => {
    if (ev.entity.typeId === HELPER_ID) {
        track(ev.entity);
        system.runTimeout(() => {
            if (ev.entity.isValid) {
                rt(ev.entity).ai = undefined;
                initHealth(ev.entity);
                updateHandDisplay(ev.entity, true);
            }
        }, 2);
    }
});
world.afterEvents.worldLoad.subscribe(() => {
    console.log(`[helper] Helper Buddy v${VERSION} loaded`);
    rescan();
    for (const h of helpers.values()) {
        rt(h).ai = undefined;
        initHealth(h);
        updateHandDisplay(h, true);
    }
});
// Open the menu: interact button (mobile) / right click (PC) ...
world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
    if (ev.target.typeId !== HELPER_ID)
        return;
    if (ev.itemStack?.typeId === "minecraft:name_tag")
        return; // let vanilla name tags rename the helper
    ev.cancel = true;
    const p = ev.player;
    const h = ev.target;
    const sneaking = p.isSneaking;
    system.run(() => openMenu(p, h, sneaking));
});
// Someone attacked someone: the helper fights back like a pet and defends its owner.
function onAttack(attacker, victim) {
    if (!attacker || !victim)
        return;
    try {
        if (!attacker.isValid || !victim.isValid)
            return;
        const aType = attacker.typeId;
        if (victim.typeId === HELPER_ID) {
            if (aType !== "minecraft:player" && aType !== HELPER_ID)
                setTarget(victim, attacker, true);
            return;
        }
        if (aType === "minecraft:player") {
            // the owner attacks a mob: its helpers join in
            for (const h of ownedHelpersNear(helpers.values(), attacker, 24))
                setTarget(h, victim, true);
            return;
        }
        if (victim.typeId === "minecraft:player" && aType !== HELPER_ID) {
            for (const h of ownedHelpersNear(helpers.values(), victim, 24))
                setTarget(h, attacker, true);
        }
    }
    catch {
        /* ignore */
    }
}
// ... or simply tapping/hitting it (players never hurt it).
world.afterEvents.entityHitEntity.subscribe((ev) => {
    const p = ev.damagingEntity;
    if (p.typeId === "minecraft:player" && ev.hitEntity.typeId === HELPER_ID) {
        openMenu(p, ev.hitEntity, p.isSneaking);
        return;
    }
    onAttack(p, ev.hitEntity);
});
world.afterEvents.projectileHitEntity.subscribe((ev) => {
    let hit;
    try {
        hit = ev.getEntityHit()?.entity;
    }
    catch {
        hit = undefined;
    }
    if (ev.source?.typeId === "minecraft:player" && hit?.typeId === HELPER_ID)
        return;
    onAttack(ev.source, hit);
});
// v1.5: the real entity health is only a buffer (always refilled). Every drop of that buffer is a hit: it is taken off
// the helper's own (visible) health, so healing/feeding can never be lost.
world.afterEvents.entityHealthChanged.subscribe((ev) => {
    try {
        const e = ev.entity;
        if (!e.isValid)
            return;
        if (ev.newValue < ev.oldValue) {
            applyDamage(e, ev.oldValue - ev.newValue);
            tickHealth(e); // knocks out right away when the visible health is gone
            if (!isKO(e)) {
                e.dimension.spawnParticle("minecraft:critical_hit_emitter", {
                    x: e.location.x, y: e.location.y + 1, z: e.location.z,
                });
            }
        }
        refreshNameTag(e);
    }
    catch {
        /* ignore */
    }
}, { entityTypes: [HELPER_ID] });
// v1.6: anything that hurts the helper (melee, arrows, creeper blasts ...) makes it fight back.
world.afterEvents.entityHurt.subscribe((ev) => {
    try {
        const e = ev.hurtEntity;
        if (!e.isValid || e.typeId !== HELPER_ID)
            return;
        fightBack(e, ev.damageSource?.damagingEntity);
    }
    catch {
        /* ignore */
    }
}, { entityTypes: [HELPER_ID] });
// The command book: use it in the air or on a block.
world.afterEvents.itemUse.subscribe((ev) => {
    if (ev.itemStack?.typeId === BOOK_ITEM)
        openBook(ev.source);
});
world.afterEvents.playerInteractWithBlock.subscribe((ev) => {
    if (ev.isFirstEvent && ev.itemStack?.typeId === BOOK_ITEM)
        openBook(ev.player);
});
// A free contract for players the first time they join a world with the add-on.
world.afterEvents.playerSpawn.subscribe((ev) => {
    if (!ev.initialSpawn)
        return;
    const p = ev.player;
    if (p.getDynamicProperty("helper:gotContract") === true)
        return;
    p.setDynamicProperty("helper:gotContract", true);
    system.runTimeout(() => {
        try {
            giveToPlayer(p, new ItemStack(CONTRACT_ITEM, 1));
            p.sendMessage("§6[ผู้ช่วย]§r คุณได้รับ §eสัญญาจ้างผู้ช่วย§r 1 ใบ! วางบนพื้นเพื่อเรียกผู้ช่วยออกมา แล้วจะได้ §eหนังสือสั่งงาน§r ไว้สั่งงานจากระยะไกล\n§7(คราฟต์สัญญาเพิ่มได้: กระดาษ 3 + เหล็ก 1 + ทอง 1 / หนังสือสั่งงาน: หนังสือ + เศษทอง)");
        }
        catch {
            /* ignore */
        }
    }, 60);
});
// ------------------------------------------------------------------ chat commands
system.beforeEvents.startup.subscribe((ev) => {
    const reg = ev.customCommandRegistry;
    const words = (n) => Array.from({ length: n }, (_, i) => ({ name: `word${i + 2}`, type: CustomCommandParamType.String }));
    const talk = (origin, ...args) => {
        const p = (origin.initiator ?? origin.sourceEntity);
        if (!p || p.typeId !== "minecraft:player")
            return { status: CustomCommandStatus.Failure, message: "ใช้ได้เฉพาะผู้เล่น" };
        const text = args.filter((a) => a !== undefined && a !== null).map(String).join(" ");
        system.run(() => {
            handleTalkCommand(p, text).catch((e) => console.warn(`[helper] talk error: ${e}`));
        });
        return { status: CustomCommandStatus.Success };
    };
    const register = (name, description, extra) => {
        reg.registerCommand({
            name, description, permissionLevel: CommandPermissionLevel.Any, cheatsRequired: false,
            mandatoryParameters: [{ name: "message", type: CustomCommandParamType.String }],
            optionalParameters: words(extra),
        }, talk);
    };
    for (const name of ["helper:talk"]) {
        try {
            register(name, "คุยและสั่งงานผู้ช่วย เช่น /helper:talk ไปขุดแร่", 9);
        }
        catch {
            try {
                register(name, "คุยและสั่งงานผู้ช่วย", 3);
            }
            catch (e) {
                console.warn(`[helper] cannot register ${name}: ${e}`);
            }
        }
    }
    try {
        reg.registerCommand({ name: "helper:book", description: "เปิดหนังสือสั่งงานผู้ช่วย (ขอเล่มใหม่ถ้าหาย)", permissionLevel: CommandPermissionLevel.Any, cheatsRequired: false }, (origin) => {
            const p = (origin.initiator ?? origin.sourceEntity);
            if (!p || p.typeId !== "minecraft:player")
                return { status: CustomCommandStatus.Failure };
            system.run(() => {
                giveBook(p);
                openBook(p);
            });
            return { status: CustomCommandStatus.Success };
        });
    }
    catch (e) {
        console.warn(`[helper] cannot register helper:book: ${e}`);
    }
});
// ------------------------------------------------------------------ main loop
const lastTp = new Map();
const followWatch = new Map();
/**
 * Safety net for following: the vanilla follow AI normally does the walking. If the helper has not moved for a
 * while although the owner is away, the script walks it for a moment; if far away, it teleports next to the owner.
 */
function followFallback(h, owner) {
    if (!owner)
        return;
    if (owner.dimension.id !== h.dimension.id) {
        // the owner went through a portal: follow them there
        const now = system.currentTick;
        if (now - (lastTp.get(h.id) ?? -999) >= 100) {
            lastTp.set(h.id, now);
            bringTo(h, owner);
        }
        return;
    }
    const d = dist(owner.location, h.location);
    const now = system.currentTick;
    const r = rt(h);
    let w = followWatch.get(h.id);
    if (!w) {
        w = { x: h.location.x, z: h.location.z, t: now };
        followWatch.set(h.id, w);
    }
    if (now < r.followUntil) {
        if (d > 3) {
            setAi(h, "work");
            steer(h, owner.location, 2.5);
        }
        else {
            r.followUntil = 0;
            stopMoving(h);
        }
    }
    else if (d > 6) {
        const moved = Math.abs(h.location.x - w.x) + Math.abs(h.location.z - w.z);
        if (moved > 0.4) {
            w.x = h.location.x;
            w.z = h.location.z;
            w.t = now;
        }
        else if (now - w.t > 40) {
            r.followUntil = now + 60; // script-walk for 3 seconds
            w.t = now;
        }
    }
    else {
        w.x = h.location.x;
        w.z = h.location.z;
        w.t = now;
    }
    if (d < 20)
        return;
    if (now - (lastTp.get(h.id) ?? -999) < 60)
        return;
    lastTp.set(h.id, now);
    // teleport to a safe spot next to the owner
    const base = { x: Math.floor(owner.location.x), y: Math.floor(owner.location.y), z: Math.floor(owner.location.z) };
    const offs = [[2, 0], [-2, 0], [0, 2], [0, -2], [1, 1], [-1, -1], [0, 0]];
    for (const [ox, oz] of offs) {
        for (let dy = 1; dy >= -2; dy--) {
            const x = base.x + ox, y = base.y + dy, z = base.z + oz;
            const below = safeBlock(h.dimension, { x, y: y - 1, z });
            if (below && !isPassable(below) && !below.isLiquid && isPassable(safeBlock(h.dimension, { x, y, z })) &&
                isPassable(safeBlock(h.dimension, { x, y: y + 1, z }))) {
                try {
                    h.teleport({ x: x + 0.5, y, z: z + 0.5 });
                }
                catch {
                    /* ignore */
                }
                return;
            }
        }
    }
}
function statusExtra(h) {
    const m = getMode(h);
    const plan = getPlan(h);
    if (plan) {
        const st = plan.steps[plan.i];
        if (st)
            return `${plan.i + 1}/${plan.steps.length} ${stepProgress(st).replace(/[()]/g, "")}`.trim();
    }
    if (rt(h).hungryUntil !== undefined)
        return "หิว ไปหาอาหาร";
    if (m === "build") {
        const j = getJob(h);
        if (!j)
            return undefined;
        if (j.stage === "clear")
            return "เคลียร์พื้นที่";
        const busy = rt(h).busyUntil > system.currentTick;
        return busy ? `${jobProgress(h, j)}% รอวัสดุ` : `${jobProgress(h, j)}%`;
    }
    if (m === "dig")
        return mineStatus(h);
    if (m === "mine" || m === "chop" || m === "gather") {
        const t = getTool(h);
        return t ? durabilityText(t).replace(/§./g, "") : "ไม่มีเครื่องมือ";
    }
    return undefined;
}
function tickHelper(h, tick) {
    const slot = (tick + (h.id.charCodeAt(h.id.length - 1) || 0)) % 20;
    if (slot === 0)
        refreshNameTag(h, statusExtra(h));
    if (slot === 10)
        updateHandDisplay(h);
    // knocked out: lies still until fed back to 10%
    if (tickHealth(h)) {
        if (rt(h).task)
            endTask(h);
        return;
    }
    const r = rt(h);
    const hungry = tickSelfCare(h, tick); // eats / cooks by itself, may go looking for food
    tickPlan(h, tick); // multi-step orders
    const mode = getMode(h);
    const owner = getOwner(h);
    // fighting comes first (like a wolf protecting its owner); hunting uses the same moves
    if (getSettings(h).fight && slot % 5 === 2)
        scanThreats(h);
    if (tickCombat(h)) {
        if (r.task && !r.task.breaking)
            endTask(h);
        return;
    }
    if (hungry) {
        tickFood(h, { hunt: true, crops: true });
        return;
    }
    switch (mode) {
        case "follow":
            if (r.task)
                endTask(h);
            if (r.followUntil <= system.currentTick)
                setAi(h, "follow");
            setWorking(h, false);
            followFallback(h, owner);
            break;
        case "stay":
            if (r.task)
                endTask(h);
            setAi(h, "stay");
            setWorking(h, false);
            if (r.moving)
                stopMoving(h);
            break;
        case "mine":
        case "chop":
        case "gather":
            // works on its own: does not follow the owner
            followWatch.delete(h.id);
            r.followUntil = 0;
            tickWork(h, mode, owner);
            break;
        case "food":
            if (r.task)
                endTask(h);
            tickFood(h);
            break;
        case "goto":
            if (r.task)
                endTask(h);
            tickGoto(h);
            break;
        case "dig":
            if (r.task)
                endTask(h);
            tickDig(h, owner);
            break;
        case "build":
            if (r.task)
                endTask(h);
            tickBuild(h, owner);
            break;
    }
}
system.runInterval(() => {
    const tick = system.currentTick;
    if (tick % 100 === 0)
        rescan();
    for (const [id, h] of helpers) {
        if (!h.isValid) {
            helpers.delete(id);
            dropRuntime(id);
            forgetMineRuntime(id);
            forgetFoodRuntime(id);
            forgetPlanRuntime(id);
            continue;
        }
        try {
            tickHelper(h, tick);
        }
        catch (e) {
            if (tick % 200 === 0)
                console.warn(`[helper] tick error: ${e}`);
        }
    }
}, 1);
