// Multi-step orders: "ตัดไม้ 20 ท่อน แล้วขุดทราย 64 ก้อน แล้วเอามาให้" becomes a list of steps run one after another.
import { system, world } from "@minecraft/server";
import { getJob, makeHouseJob, startJob } from "./builder.js";
import { foodValueId, isKO } from "./combat.js";
import { LOGS, ORES, ORE_GROUPS } from "./data.js";
import { FOOD_ANIMALS, cookInBag, describeFoodOptions, eatFromBag, edibleInBag, setFoodOptions } from "./food.js";
import { SIZES, houseName } from "./house_gen.js";
import { cancelMineJob, describeMine, getMineJob, startMineJob } from "./minejob.js";
import { hopToward, isStuck, resetProgress, steer, stopMoving } from "./move.js";
import { onProgress } from "./progress.js";
import { getHome, getName, getOwner, getSettings, refreshNameTag, rt, saveSettings, setAi, setHome, setModeProp, } from "./state.js";
import { depositToContainers, giveAllToPlayer, isStorageFull, nearbyContainers } from "./storage.js";
import { toolInfo } from "./tools.js";
import { dist, distXZ, facingOf, floorV, isPassable, itemName, safeBlock, tellOwner } from "./util.js";
import { endTask, setGatherIds } from "./work.js";
const WORK = ["chop", "mine", "gather", "food"];
export const DEFAULT_N = { chop: 32, mine: 16, gather: 64, food: 8 };
const STEP_TIMEOUT = 20 * 60 * 10; // a work step gives up after 10 minutes
// ------------------------------------------------------------------ persistence
const CACHE = new Map();
const dirty = new Set();
export function getPlan(h) {
    if (CACHE.has(h.id))
        return CACHE.get(h.id) ?? undefined;
    const v = h.getDynamicProperty("h:plan");
    let p;
    if (typeof v === "string") {
        try {
            p = JSON.parse(v);
        }
        catch {
            p = undefined;
        }
    }
    if (p) {
        // after a world reload the tick counter starts again: restart the clock of the current step
        const s = p.steps[p.i];
        if (s && s.t0 !== undefined)
            s.t0 = system.currentTick;
        if (s?.k === "mine")
            rt(h).oreOnly = s.ores;
    }
    CACHE.set(h.id, p ?? null);
    return p;
}
function savePlan(h, p) {
    CACHE.set(h.id, p ?? null);
    dirty.delete(h.id);
    try {
        h.setDynamicProperty("h:plan", p ? JSON.stringify(p) : undefined);
    }
    catch {
        /* too big: keep it in memory only */
    }
}
/** Forgets the current plan (any new order replaces it). */
export function clearPlan(h) {
    if (!getPlan(h))
        return;
    rt(h).oreOnly = undefined;
    savePlan(h, undefined);
}
export function forgetPlanRuntime(id) {
    CACHE.delete(id);
    dirty.delete(id);
}
// ------------------------------------------------------------------ progress counting
onProgress((h, ev, id, n) => {
    const plan = getPlan(h);
    if (!plan)
        return;
    const s = plan.steps[plan.i];
    if (!s || s.t0 === undefined)
        return;
    let add = 0;
    if (s.k === "chop" && ev === "item" && LOGS.has(id))
        add = n;
    else if (s.k === "mine" && ev === "break" && ORES[id] && (!s.ores || s.ores.includes(ORES[id].group)))
        add = 1;
    else if (s.k === "gather" && ev === "break" && s.ids?.includes(id))
        add = 1;
    else if (s.k === "food") {
        if (s.food?.animal) {
            if (ev === "kill" && (id === s.food.animal || (s.food.animal === "minecraft:cow" && id === "minecraft:mooshroom")))
                add = 1;
        }
        else if (ev === "item" && foodValueId(id) > 0)
            add = n;
    }
    if (add > 0) {
        s.got = (s.got ?? 0) + add;
        dirty.add(h.id);
    }
});
// ------------------------------------------------------------------ describing
function unit(s) {
    if (s.k === "chop")
        return "ท่อน";
    if (s.k === "food")
        return s.food?.animal ? "ตัว" : "ชิ้น";
    return "ก้อน";
}
export function stepProgress(s) {
    if (!WORK.includes(s.k))
        return "";
    if (s.full)
        return `(${s.got ?? 0} จนกว่ากระเป๋าจะเต็ม)`;
    if (s.n === undefined)
        return "";
    return `(${Math.min(s.got ?? 0, s.n)}/${s.n} ${unit(s)})`;
}
export function planSummary(p) {
    return p.steps.map((s, i) => `${i === p.i ? "§e▶" : i < p.i ? "§a✔" : "§7•"} ${i + 1}. ${s.label}§r`).join("\n");
}
export function planStatus(h) {
    const p = getPlan(h);
    if (!p)
        return undefined;
    const s = p.steps[p.i];
    if (!s)
        return undefined;
    return `§fงานที่สั่งไว้: ขั้นที่ ${p.i + 1}/${p.steps.length} §e${s.label}§r ${stepProgress(s)}`;
}
// ------------------------------------------------------------------ building steps
export function workStep(k, o) {
    const amount = o.full ? " จนกว่ากระเป๋าจะเต็ม" : o.n !== undefined ? ` ${o.n}` : "";
    let label = "";
    if (k === "chop")
        label = `ตัดไม้${amount}${o.n !== undefined ? " ท่อน" : ""}`;
    else if (k === "mine") {
        const names = o.ores?.map((g) => ORE_GROUPS.find((x) => x.id === g)?.th ?? g).join(", ");
        label = `ขุดแร่${names ? `(${names})` : ""}${amount}${o.n !== undefined ? " ก้อน" : ""}`;
    }
    else if (k === "gather")
        label = `เก็บ${o.idsName ?? "บล็อก"}${amount}${o.n !== undefined ? " ก้อน" : ""}`;
    else {
        const f = o.food ?? { hunt: true, crops: true };
        label = `${f.animal ? `ล่า${FOOD_ANIMALS[f.animal] ?? itemName(f.animal)}` : `หาอาหาร (${describeFoodOptions(f)})`}${amount}${o.n !== undefined ? (f.animal ? " ตัว" : " ชิ้น") : ""}`;
    }
    return { k, label, name: o.idsName, n: o.n, full: o.full, ores: o.ores, ids: o.ids, food: o.food };
}
export function mineStep(p, h, o, first) {
    const near = p.dimension.id === h.dimension.id && dist(p.location, h.location) <= 48;
    const opts = { ...o, dir: o.dir ?? (near && !o.atHelper ? facingOf(p) : facingOf(h)) };
    if (first && near && !o.atHelper) {
        opts.origin = { x: Math.floor(p.location.x), y: Math.floor(p.location.y + 0.01), z: Math.floor(p.location.z) };
    }
    return { k: "dig", label: "สร้างเหมือง", mine: opts };
}
/** House steps are planned right away (in front of the player), so the site does not depend on where they walk later. */
export function houseStep(p, opts) {
    const { job, site } = makeHouseJob(p, opts);
    if (!site.ok)
        return site.reason ?? "สร้างตรงนี้ไม่ได้";
    return { k: "house", label: `สร้างบ้าน${houseName(job.opts)} ${SIZES[job.opts.size].th} ${job.opts.floors} ชั้น`, job };
}
// ------------------------------------------------------------------ running
export function startPlan(p, h, steps) {
    endTask(h);
    rt(h).target = undefined;
    // fill in amounts so every step but the last can finish
    steps.forEach((s, i) => {
        if (WORK.includes(s.k) && s.n === undefined && !s.full && i < steps.length - 1) {
            s.n = DEFAULT_N[s.k];
            s.label = workStep(s.k, { ...s, idsName: s.name }).label;
        }
    });
    const plan = { v: 1, steps, i: 0 };
    tellOwner(p, getName(h), `§aรับทราบ! ผมจะทำตามนี้:§r\n${steps.map((s, i) => `§e${i + 1}.§r ${s.label}`).join("\n")}`);
    savePlan(h, plan);
    beginStep(h, plan);
}
function owner(h) {
    return getOwner(h);
}
function nearOwner(h, p, d) {
    return !!p && p.dimension.id === h.dimension.id && dist(p.location, h.location) <= d;
}
/** Teleports the helper next to a player (any dimension). */
function bring(h, p) {
    const dim = p.dimension;
    const base = floorV(p.location);
    for (const [ox, oz] of [[2, 0], [-2, 0], [0, 2], [0, -2], [1, 1], [0, 0]]) {
        for (let dy = 1; dy >= -2; dy--) {
            const x = base.x + ox, y = base.y + dy, z = base.z + oz;
            const below = safeBlock(dim, { x, y: y - 1, z });
            if (below && !isPassable(below) && !below.isLiquid && isPassable(safeBlock(dim, { x, y, z })) &&
                isPassable(safeBlock(dim, { x, y: y + 1, z }))) {
                try {
                    h.teleport({ x: x + 0.5, y, z: z + 0.5 }, { dimension: dim });
                }
                catch {
                    /* ignore */
                }
                resetProgress(h);
                return;
            }
        }
    }
    try {
        h.teleport(p.location, { dimension: dim });
    }
    catch {
        /* ignore */
    }
}
function beginStep(h, plan) {
    const s = plan.steps[plan.i];
    if (!s)
        return finishPlan(h, plan);
    s.t0 = system.currentTick;
    s.got = 0;
    const r = rt(h);
    r.oreOnly = undefined;
    r.task = undefined;
    r.roam = undefined;
    r.roamFails = 0;
    const p = owner(h);
    const last = plan.i === plan.steps.length - 1;
    let instant = false;
    switch (s.k) {
        case "chop":
        case "mine":
        case "gather":
        case "food":
            if (s.k === "mine")
                r.oreOnly = s.ores;
            if (s.k === "gather")
                setGatherIds(h, s.ids);
            if (s.k === "food")
                setFoodOptions(h, s.food);
            setHome(h, floorV(h.location));
            setModeProp(h, s.k);
            break;
        case "dig": {
            const m = s.mine;
            cancelMineJob(h);
            if (m.origin && dist(m.origin, h.location) > 8) {
                try {
                    h.teleport({ x: m.origin.x + 0.5, y: m.origin.y, z: m.origin.z + 0.5 });
                }
                catch {
                    /* ignore */
                }
            }
            const j = startMineJob(h, p, m.dir, m);
            setModeProp(h, "dig");
            setHome(h, j.o);
            s.label = `สร้างเหมือง: ${describeMine(j)}`;
            break;
        }
        case "house": {
            const j = s.job;
            if (getMineJob(h))
                cancelMineJob(h);
            if (h.dimension.id !== j.dim || dist(h.location, { x: j.ox, y: j.oy, z: j.oz }) > 64) {
                if (p)
                    bring(h, p);
            }
            startJob(h, { ...j, stage: "clear", idx: 0, placed: 0, total: 0 });
            break;
        }
        case "come":
            if (p && !nearOwner(h, p, 6))
                bring(h, p);
            setModeProp(h, "follow");
            instant = true;
            break;
        case "follow":
            setModeProp(h, "follow");
            instant = true;
            break;
        case "stay":
            setModeProp(h, "stay");
            instant = true;
            break;
        case "guard":
            setModeProp(h, "stay");
            {
                const st = getSettings(h);
                st.fight = true;
                saveSettings(h, st);
            }
            instant = true;
            break;
        case "deliver":
            if (p) {
                if (!nearOwner(h, p, 6))
                    bring(h, p);
                const n = giveAllToPlayer(h, p, false, (it) => !toolInfo(it) && it.typeId !== "minecraft:torch");
                tellOwner(p, getName(h), n ? `เอาของมาส่งให้แล้ว §e${n}§r ชิ้น!` : "ผมไม่มีของจะให้เลย");
                setModeProp(h, "follow");
            }
            else {
                s.t0 = undefined; // wait until the owner is online
                savePlan(h, plan);
                return;
            }
            instant = true;
            break;
        case "deposit": {
            let cs = nearbyContainers(h, h.location, 10);
            const home = getHome(h);
            if (!cs.length && home && dist(home, h.location) < 48)
                cs = nearbyContainers(h, home, 10);
            if (!cs.length && p && nearOwner(h, p, 64)) {
                cs = nearbyContainers(h, p.location, 10);
                if (cs.length && !nearOwner(h, p, 10))
                    bring(h, p);
            }
            if (!cs.length)
                tellOwner(p, getName(h), "§eไม่เจอหีบแถวนี้เลย§r (ต้องอยู่ห่างไม่เกิน 10 บล็อก) ของยังอยู่ในกระเป๋าผมนะ");
            else {
                const keep = (it) => !!toolInfo(it) || it.typeId === "minecraft:torch" || edibleInBag(h).some((f) => f.item.typeId === it.typeId);
                const n = depositToContainers(h, cs, keep);
                tellOwner(p, getName(h), n ? `เก็บของเข้าหีบแล้ว §e${n}§r ชิ้น (เก็บเครื่องมือ คบเพลิง และอาหารไว้ใช้เอง)` : "หีบเต็มหมดแล้ว เก็บไม่ได้เลย");
            }
            instant = true;
            break;
        }
        case "goto":
            setGoto(h, s.pos, s.dim ?? h.dimension.id);
            break;
        case "wait":
            setModeProp(h, "stay");
            break;
        case "eat": {
            const ate = eatFromBag(h, true);
            if (!ate)
                tellOwner(p, getName(h), edibleInBag(h, true).length ? "ยังอิ่มอยู่เลยครับ เลือดเต็มแล้ว" : "ในกระเป๋าผมไม่มีอาหารเลย");
            instant = true;
            break;
        }
        case "cook": {
            const n = cookInBag(h);
            tellOwner(p, getName(h), n ? `ทำอาหารเสร็จแล้ว §e${n}§r ชิ้น!` :
                "ทำอาหารไม่ได้: ต้องมีเนื้อดิบ/มันฝรั่ง กับ §eถ่านหิน§r (หรือข้าวสาลี 3 อันเพื่อทำขนมปัง) ในกระเป๋าผม");
            instant = true;
            break;
        }
        case "fight": {
            const st = getSettings(h);
            st.fight = !!s.on;
            saveSettings(h, st);
            if (!s.on)
                r.target = undefined;
            instant = true;
            break;
        }
    }
    refreshNameTag(h);
    if (instant)
        return advance(h, plan, false);
    // a work step without an amount at the end just keeps going: the plan is over
    if (last && WORK.includes(s.k) && s.n === undefined && !s.full) {
        tellOwner(p, getName(h), `ขั้นสุดท้าย: §e${s.label}§r ผมจะทำไปเรื่อยๆ จนกว่าจะสั่งใหม่นะ`);
        savePlan(h, undefined);
        return;
    }
    savePlan(h, plan);
}
function advance(h, plan, announce) {
    const s = plan.steps[plan.i];
    if (announce && s) {
        const next = plan.steps[plan.i + 1];
        tellOwner(owner(h), getName(h), `§a✔ ${s.label} เสร็จแล้ว!§r ${stepProgress(s)}${next ? ` ต่อไป: §e${next.label}` : ""}`);
    }
    plan.i++;
    rt(h).oreOnly = undefined;
    if (plan.i >= plan.steps.length)
        return finishPlan(h, plan);
    beginStep(h, plan);
}
function finishPlan(h, plan) {
    savePlan(h, undefined);
    const lastStep = plan.steps[plan.steps.length - 1];
    if (lastStep && (WORK.includes(lastStep.k) || lastStep.k === "wait")) {
        endTask(h);
        setModeProp(h, "stay");
        setAi(h, "stay");
    }
    if (plan.steps.length > 1)
        tellOwner(owner(h), getName(h), "§a§lทำครบทุกอย่างที่สั่งแล้วครับ!§r");
    refreshNameTag(h);
}
/** Called every tick: checks whether the current step is done. */
export function tickPlan(h, tick) {
    const plan = getPlan(h);
    if (!plan)
        return;
    if ((tick + (h.id.charCodeAt(h.id.length - 1) || 0)) % 10 !== 0)
        return;
    const s = plan.steps[plan.i];
    if (!s)
        return finishPlan(h, plan);
    if (s.t0 === undefined)
        return beginStep(h, plan); // e.g. deliver waiting for the owner, or after a reload
    if (isKO(h))
        return;
    const now = system.currentTick;
    const age = now - s.t0;
    let done = false;
    switch (s.k) {
        case "chop":
        case "mine":
        case "gather":
        case "food":
            if (s.full)
                done = isStorageFull(h);
            else if (s.n !== undefined)
                done = (s.got ?? 0) >= s.n;
            if (!done && isStorageFull(h) && !s.full) {
                tellOwner(owner(h), getName(h), `§eกระเป๋าเต็มแล้ว§r ได้ ${s.got ?? 0}/${s.n} เลยข้ามไปขั้นต่อไปนะ`);
                done = true;
            }
            if (!done && age > STEP_TIMEOUT) {
                tellOwner(owner(h), getName(h), `หา${s.label.replace(/^(เก็บ|ขุด|ตัด|หา|ล่า)/, "")}ได้ไม่ครบ (ได้ ${s.got ?? 0}/${s.n}) ข้ามไปขั้นต่อไปนะ`);
                done = true;
            }
            break;
        case "dig":
            done = age > 40 && !getMineJob(h);
            break;
        case "house":
            done = age > 40 && !getJob(h);
            break;
        case "goto":
            done = !rt(h).gotoPos && age > 2;
            break;
        case "wait":
            done = age >= (s.secs ?? 0) * 20;
            break;
        default:
            done = true;
    }
    if (done)
        advance(h, plan, true);
    else if (dirty.has(h.id) && now % 40 < 10)
        savePlan(h, plan);
}
// ------------------------------------------------------------------ going to a place
export function setGoto(h, pos, dim) {
    rt(h).gotoPos = { x: pos.x, y: pos.y, z: pos.z };
    h.setDynamicProperty("h:goto", JSON.stringify({ ...pos, dim }));
    setModeProp(h, "goto");
    resetProgress(h);
}
function getGoto(h) {
    const v = h.getDynamicProperty("h:goto");
    if (typeof v !== "string")
        return undefined;
    try {
        return JSON.parse(v);
    }
    catch {
        return undefined;
    }
}
const gotoTp = new Map();
export function tickGoto(h) {
    const g = getGoto(h);
    const r = rt(h);
    if (!g) {
        r.gotoPos = undefined;
        setModeProp(h, "stay");
        return;
    }
    r.gotoPos = { x: g.x, y: g.y, z: g.z };
    setAi(h, "work");
    const goal = { x: g.x + 0.5, y: g.y, z: g.z + 0.5 };
    const arrive = () => {
        stopMoving(h);
        r.gotoPos = undefined;
        h.setDynamicProperty("h:goto", undefined);
        setModeProp(h, "stay");
        setAi(h, "stay");
        if (!getPlan(h))
            tellOwner(getOwner(h), getName(h), `ถึงแล้วครับ (${g.x}, ${g.y}, ${g.z})`);
    };
    const tp = () => {
        const now = system.currentTick;
        if (now - (gotoTp.get(h.id) ?? -999) < 40)
            return;
        gotoTp.set(h.id, now);
        let dim = h.dimension;
        try {
            dim = world.getDimension(g.dim);
        }
        catch {
            /* ignore */
        }
        let y = g.y;
        for (let dy = 3; dy >= -8; dy--) {
            const b = safeBlock(dim, { x: g.x, y: g.y + dy - 1, z: g.z });
            if (b && !isPassable(b) && !b.isLiquid && isPassable(safeBlock(dim, { x: g.x, y: g.y + dy, z: g.z })) &&
                isPassable(safeBlock(dim, { x: g.x, y: g.y + dy + 1, z: g.z }))) {
                y = g.y + dy;
                break;
            }
        }
        try {
            h.teleport({ x: goal.x, y, z: goal.z }, { dimension: dim });
        }
        catch {
            /* ignore */
        }
        resetProgress(h);
    };
    if (h.dimension.id !== g.dim)
        return tp();
    const d = distXZ(h.location, goal);
    if (d <= 1.6 && Math.abs(h.location.y - g.y) <= 3)
        return arrive();
    if (d > 48)
        return tp(); // far away: travel by "magic"
    const res = steer(h, goal, 1.2);
    if (res.status === "arrived") {
        if (Math.abs(h.location.y - g.y) > 3)
            tp();
        return;
    }
    if (res.status === "blocked" || res.status === "cliff") {
        if (!hopToward(h, goal))
            tp();
        return;
    }
    if (isStuck(h, d, 80))
        tp();
}
/** Where "home" is for the owner: their bed / spawn point, else the world spawn. */
export function ownerHome(p) {
    try {
        const sp = p.getSpawnPoint();
        if (sp)
            return { pos: { x: Math.floor(sp.x), y: Math.floor(sp.y), z: Math.floor(sp.z) }, dim: sp.dimension.id, bed: true };
    }
    catch {
        /* ignore */
    }
    const w = world.getDefaultSpawnLocation();
    return { pos: { x: Math.floor(w.x), y: w.y > 320 ? 64 : Math.floor(w.y), z: Math.floor(w.z) }, dim: "minecraft:overworld", bed: false };
}
