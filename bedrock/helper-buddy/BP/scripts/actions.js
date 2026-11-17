// Commands shared by the menus, the command book and the talk system.
import { ItemStack, system } from "@minecraft/server";
import { cancelJob, getJob, jobProgress, pauseJob } from "./builder.js";
import { describeHealth, feedFrom, foodValue, healthDebug, isFullHp, isKO, levelText, setTarget } from "./combat.js";
import { houseName } from "./house_gen.js";
import { cancelMineJob, describeMine, getMineJob, mineProgress, startMineJob } from "./minejob.js";
import { resetProgress, stopMoving } from "./move.js";
import { getMode, getName, getSettings, modeLabel, refreshNameTag, rt, saveSettings, setHome, setModeProp, } from "./state.js";
import { getContainer, getTool, giveAllToPlayer, giveToPlayer, playerContainer, setTool, storageAdd, usedSlots, } from "./storage.js";
import { durabilityText, enchantList, equipBest, hasToolKind, toolInfo } from "./tools.js";
import { dist, facingOf, floorV, isPassable, itemName, safeBlock, tellOwner } from "./util.js";
import { SEARCH_RADIUS, endTask, getGatherIds, setGatherIds } from "./work.js";
import { describeFoodOptions, setFoodOptions } from "./food.js";
import { clearPlan, getPlan, planStatus } from "./plan.js";
import { toolKindFor } from "./data.js";
export const BOOK_ITEM = "helper:command_book";
export function say(p, h, msg) {
    tellOwner(p, getName(h), msg);
}
function pop(p, h, pitch = 1.2) {
    try {
        h.dimension.playSound("random.pop", h.location, { volume: 0.8, pitch });
    }
    catch {
        /* ignore */
    }
    try {
        p.playSound("random.pop", { volume: 0.5, pitch });
    }
    catch {
        /* ignore */
    }
}
/** Leaves the current job (keeps a house job paused, a mine job stays saved) and forgets any plan. */
function leaveJob(h) {
    if (getMode(h) === "build")
        pauseJob(h);
    endTask(h);
    clearPlan(h);
    const r = rt(h);
    r.roam = undefined;
    r.roamFails = 0;
    r.oreOnly = undefined;
    r.hungryUntil = undefined;
    if (r.targetKind === "hunt")
        r.target = undefined;
    if (getMode(h) === "goto")
        h.setDynamicProperty("h:goto", undefined);
}
export function isNear(p, h, d = 16) {
    return p.dimension.id === h.dimension.id && dist(p.location, h.location) <= d;
}
// ------------------------------------------------------------------ teleport
/** Teleports the helper to a free spot next to the player (works across dimensions). */
export function bringTo(h, p) {
    const dim = p.dimension;
    const base = floorV(p.location);
    const offs = [[2, 0], [-2, 0], [0, 2], [0, -2], [1, 1], [-1, -1], [1, -1], [-1, 1], [0, 0]];
    for (const [ox, oz] of offs) {
        for (let dy = 1; dy >= -2; dy--) {
            const x = base.x + ox, y = base.y + dy, z = base.z + oz;
            const below = safeBlock(dim, { x, y: y - 1, z });
            if (below && !isPassable(below) && !below.isLiquid && isPassable(safeBlock(dim, { x, y, z })) &&
                isPassable(safeBlock(dim, { x, y: y + 1, z }))) {
                try {
                    h.dimension.spawnParticle("minecraft:villager_happy", h.location);
                }
                catch {
                    /* ignore */
                }
                try {
                    h.teleport({ x: x + 0.5, y, z: z + 0.5 }, { dimension: dim });
                    dim.playSound("mob.shulker.teleport", { x, y, z }, { volume: 0.5, pitch: 1.3 });
                }
                catch {
                    return false;
                }
                resetProgress(h);
                return true;
            }
        }
    }
    try {
        h.teleport(p.location, { dimension: dim });
        return true;
    }
    catch {
        return false;
    }
}
// ------------------------------------------------------------------ modes
export function cmdStop(p, h, m, quiet = false) {
    leaveJob(h);
    setModeProp(h, m);
    if (!quiet)
        say(p, h, m === "follow" ? "หยุดงานแล้ว จะเดินตามคุณไปนะครับ" : "โอเค ผมจะหยุดรออยู่ตรงนี้");
    refreshNameTag(h);
}
export function cmdCome(p, h) {
    leaveJob(h);
    setModeProp(h, "follow");
    if (!isNear(p, h, 6))
        bringTo(h, p);
    say(p, h, "มาแล้วครับ! จะเดินตามคุณไปนะ");
    refreshNameTag(h);
}
/** Sends the helper to work on its own around where it is now. */
export function cmdWork(p, h, m, quiet = false) {
    leaveJob(h);
    if (isKO(h)) {
        say(p, h, "§cผมยังสลบอยู่...§r ให้อาหารผมก่อนนะ แล้วผมจะไปทำงานต่อ");
    }
    setModeProp(h, m);
    setHome(h, floorV(h.location));
    const kind = m === "mine" ? "pickaxe" : "axe";
    const has = hasToolKind(h, kind);
    if (has)
        equipBest(h, kind);
    if (!quiet) {
        if (m === "mine") {
            say(p, h, has
                ? `§6ไปหาแร่แล้วนะ!§r ผมจะเดินหาแร่ที่มองเห็นในระยะ ${SEARCH_RADIUS} บล็อกแถวนี้แล้วขุดเอง (ไม่เดินตามคุณ)` +
                    (hasToolKind(h, "shovel") ? "" : "\n§7ถ้าส่ง §eพลั่ว§7 มาด้วย ผมจะขุดทะลุดิน/ทราย/กรวดได้")
                : "§6โหมดขุดแร่!§r แต่ผมยังไม่มี §eอีเต้อ§r เลย ส่งให้ผมหน่อยนะ (หนังสือสั่งงาน > ส่งของให้ผู้ช่วย)");
        }
        else {
            say(p, h, has
                ? `§2ไปตัดไม้แล้วนะ!§r ผมจะหาต้นไม้ในระยะ ${SEARCH_RADIUS} บล็อกแถวนี้ ตัดทั้งต้น เก็บกิ่งกับต้นกล้า แล้วปลูกคืนให้`
                : "§2โหมดตัดไม้!§r แต่ผมยังไม่มี §eขวาน§r เลย ส่งให้ผมหน่อยนะ (หนังสือสั่งงาน > ส่งของให้ผู้ช่วย)");
        }
    }
    refreshNameTag(h);
}
/** Collects one kind of block (sand, dirt, stone...) around where it is now. */
export function cmdGather(p, h, ids, name, quiet = false) {
    leaveJob(h);
    setGatherIds(h, ids);
    setModeProp(h, "gather");
    setHome(h, floorV(h.location));
    const kind = toolKindFor(ids[0]);
    const th = { pickaxe: "อีเต้อ", shovel: "พลั่ว", axe: "ขวาน" };
    if (!quiet) {
        say(p, h, `§eไปเก็บ${name}แล้วนะ!§r ผมจะหา${name}ที่มองเห็นในระยะ ${SEARCH_RADIUS} บล็อกแถวนี้` +
            (kind !== "hand" && !hasToolKind(h, kind)
                ? `\n§cแต่ต้องใช้${th[kind]}§r ส่งมาให้ผมด้วยนะ`
                : ""));
    }
    refreshNameTag(h);
}
/** Looks for food: hunts farm animals (leaving two of each) and harvests ripe crops. */
export function cmdFood(p, h, o, quiet = false) {
    leaveJob(h);
    setFoodOptions(h, o);
    setModeProp(h, "food");
    setHome(h, floorV(h.location));
    if (!quiet) {
        say(p, h, `§cไปหาอาหารแล้วนะ!§r (${describeFoodOptions(o)}) ในระยะ ${SEARCH_RADIUS} บล็อก` +
            "\n§7ผมเหลือสัตว์ไว้อย่างละ 2 ตัวให้มันออกลูก ไม่ล่าลูกสัตว์หรือสัตว์ที่มีชื่อ เก็บผักที่สุกแล้วปลูกคืนให้ด้วย" +
            "\n§7(มีถ่านหินในกระเป๋า ผมจะย่างเนื้อให้สุกเอง)");
    }
    refreshNameTag(h);
}
export function cmdMine(p, h, o) {
    leaveJob(h);
    cancelMineJob(h);
    let dir = o.dir;
    const opts = { ...o };
    if (!o.atHelper && isNear(p, h, 48)) {
        // the mine starts where the player stands, toward where the player looks
        opts.origin = { x: Math.floor(p.location.x), y: Math.floor(p.location.y + 0.01), z: Math.floor(p.location.z) };
        dir = dir ?? facingOf(p);
        if (!isNear(p, h, 8))
            bringTo(h, p);
    }
    else
        dir = dir ?? facingOf(h);
    const j = startMineJob(h, p, dir, opts);
    setModeProp(h, "dig");
    setHome(h, j.o);
    const lack = [];
    if (!hasToolKind(h, "pickaxe"))
        lack.push("§eอีเต้อ§r (ขุดหิน)");
    if (!hasToolKind(h, "shovel"))
        lack.push("§eพลั่ว§r (ขุดดิน ทราย กรวด)");
    say(p, h, `§8§lสร้างเหมือง!§r ${describeMine(j)}` +
        (lack.length ? `\n§cต้องใช้ ${lack.join(" และ ")}§r ส่งมาให้ผมด้วยนะ ผมจะรอจนกว่าจะได้เครื่องมือ` : "") +
        "\n§7(ใส่คบเพลิงในกระเป๋าผม ผมจะปักไฟให้ / ถ้าเจอแร่ข้างทางผมจะขุดเก็บให้ด้วย)");
    refreshNameTag(h);
}
export function cmdCancel(p, h) {
    let did = !!getPlan(h);
    leaveJob(h);
    if (getMineJob(h)) {
        cancelMineJob(h);
        did = true;
    }
    if (getJob(h)) {
        cancelJob(h); // what was built so far stays
        did = true;
    }
    endTask(h);
    setModeProp(h, "stay");
    refreshNameTag(h);
    say(p, h, did ? "ยกเลิกงานแล้ว ผมรออยู่ตรงนี้นะ" : "โอเค ไม่มีงานค้างอยู่ ผมรออยู่ตรงนี้นะ");
    return did;
}
export function cmdResume(p, h) {
    if (getMineJob(h)) {
        endTask(h);
        setModeProp(h, "dig");
        say(p, h, "ไปสร้างเหมืองต่อละนะ!");
        refreshNameTag(h);
        return true;
    }
    if (getJob(h)) {
        endTask(h);
        setModeProp(h, "build");
        say(p, h, "ไปสร้างบ้านต่อละนะ!");
        refreshNameTag(h);
        return true;
    }
    return false;
}
export function cmdFight(p, h, on) {
    const s = getSettings(h);
    s.fight = on;
    saveSettings(h, s);
    if (!on) {
        rt(h).target = undefined;
        stopMoving(h);
        say(p, h, "โอเค ผมจะไม่สู้กับมอนสเตอร์แล้ว");
        return;
    }
    if (isKO(h)) {
        say(p, h, "ผมสลบอยู่ ยังสู้ไม่ได้ ให้อาหารผมก่อนนะ");
        return;
    }
    // attack the closest monster around the player right away
    let best;
    if (p.dimension.id === h.dimension.id) {
        let bd = 99;
        try {
            for (const e of p.dimension.getEntities({ location: p.location, maxDistance: 16, families: ["monster"] })) {
                const d = dist(e.location, p.location);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        catch {
            /* ignore */
        }
    }
    if (best) {
        if (!isNear(p, h, 24))
            bringTo(h, p);
        setTarget(h, best, true);
        say(p, h, `§cลุยเลย!§r จัดการ ${itemName(best.typeId)} ให้เอง`);
    }
    else
        say(p, h, "พร้อมสู้ครับ! ถ้ามีมอนสเตอร์เข้าใกล้คุณหรือผม ผมจะเข้าไปจัดการเอง");
}
export function cmdGiveBack(p, h) {
    const n = giveAllToPlayer(h, p, false);
    say(p, h, n ? `ส่งของคืนให้แล้ว ${n} ชิ้น` : "กระเป๋าผมว่างอยู่ ไม่มีของจะให้เลย");
    if (n)
        pop(p, h, 1.0);
}
export function cmdRename(p, h, name) {
    const n = name.replace(/§./g, "").trim().slice(0, 24);
    if (!n)
        return;
    const old = getName(h);
    h.setDynamicProperty("h:name", n);
    refreshNameTag(h);
    say(p, h, `จากนี้ไปเรียกผมว่า §e${n}§r นะครับ${old !== n ? ` (ชื่อเดิม ${old})` : ""}`);
}
// ------------------------------------------------------------------ status
function dimName(id) {
    if (id.includes("nether"))
        return "เนเธอร์";
    if (id.includes("end"))
        return "ดิเอนด์";
    return "โลกปกติ";
}
export function statusLines(h, p) {
    const lines = [];
    const m = getMode(h);
    lines.push(`§fสถานะ: ${modeLabel(m)}${isKO(h) ? " §c(สลบ)" : ""}`);
    lines.push(`§f${describeHealth(h)}`);
    lines.push(healthDebug(h));
    lines.push(`§f${levelText(h)}`);
    const loc = floorV(h.location);
    let where = `§fอยู่ที่: §7${loc.x}, ${loc.y}, ${loc.z} (${dimName(h.dimension.id)})`;
    if (p) {
        if (p.dimension.id === h.dimension.id)
            where += ` ห่างจากคุณ ${Math.round(dist(p.location, h.location))} บล็อก`;
        else
            where += " §e(คนละมิติกับคุณ)";
    }
    lines.push(where);
    const tool = getTool(h);
    if (tool) {
        const ench = enchantList(tool);
        lines.push(`§fถืออยู่: §b${itemName(tool.typeId)}§r ${durabilityText(tool)}${ench ? ` §d${ench}` : ""}`);
    }
    else
        lines.push("§fถืออยู่: §7ไม่มีเครื่องมือ");
    const kinds = ["pickaxe", "axe", "shovel"].filter((k) => hasToolKind(h, k));
    const th = { pickaxe: "อีเต้อ", axe: "ขวาน", shovel: "พลั่ว" };
    lines.push(`§fเครื่องมือที่มี: ${kinds.length ? kinds.map((k) => th[k]).join(", ") : "§7ยังไม่มี"}`);
    const u = usedSlots(h);
    lines.push(`§fกระเป๋า: ${u.used}/${u.total} ช่อง${u.used >= u.total ? " §c(เต็ม)" : ""}`);
    const job = getJob(h);
    if (job)
        lines.push(`§fงานสร้างบ้าน: §d${houseName(job.opts)}§r ${jobProgress(h, job)}%${m !== "build" ? " §7(หยุดไว้)" : ""}`);
    const mj = getMineJob(h);
    if (mj)
        lines.push(`§fงานสร้างเหมือง: §8${mineProgress(h, mj)}%§r ขุดไป ${mj.dug} บล็อก แร่ ${mj.ores} ก้อน${m !== "dig" ? " §7(หยุดไว้)" : ""}`);
    const ps = planStatus(h);
    if (ps)
        lines.push(ps);
    if (m === "gather")
        lines.push(`§fกำลังเก็บ: §e${getGatherIds(h).map((id) => itemName(id)).join(", ")}`);
    lines.push(`§fช่วยต่อสู้: ${getSettings(h).fight ? "§aเปิด" : "§7ปิด"}`);
    const t = rt(h).target;
    if (t && t.isValid)
        lines.push(`§cกำลังสู้กับ ${itemName(t.typeId)}`);
    return lines;
}
// ------------------------------------------------------------------ giving items
const TOOL_FOR_MODE = { mine: "pickaxe", chop: "axe", dig: "pickaxe" };
/** Gives the stack in a slot of the player's inventory to the helper (works from any distance). */
export function giveSlot(p, h, c, slot) {
    const item = c.getItem(slot);
    if (!item)
        return false;
    if (item.typeId === BOOK_ITEM) {
        say(p, h, "หนังสือเล่มนี้เก็บไว้สั่งงานผมนะครับ ไม่ต้องให้ผม");
        return false;
    }
    const fv = foodValue(item);
    if (fv !== 0 && (isKO(h) || !isFullHp(h) || fv < 0)) {
        feedFrom(p, h, c, slot);
        return true;
    }
    const info = toolInfo(item);
    if (info && ["pickaxe", "axe", "shovel", "sword", "hoe"].includes(info.kind)) {
        const want = TOOL_FOR_MODE[getMode(h)];
        const cur = getTool(h);
        if (!cur || info.kind === want) {
            setTool(h, item);
            c.setItem(slot, undefined);
            if (cur) {
                const left = storageAdd(h, cur);
                if (left > 0)
                    giveToPlayer(p, cur);
            }
        }
        else {
            const left = storageAdd(h, item);
            if (left >= item.amount) {
                say(p, h, "§cกระเป๋าผมเต็มแล้ว§r รับของออกไปก่อนนะ");
                return false;
            }
            c.setItem(slot, undefined);
        }
        if (want)
            equipBest(h, want);
        const ench = enchantList(item);
        say(p, h, `ได้รับ §b${itemName(item.typeId)}§r ${durabilityText(item)} แล้ว ขอบคุณครับ!${ench ? ` §d(${ench})` : ""}`);
        pop(p, h);
        return true;
    }
    const before = item.amount;
    const left = storageAdd(h, item);
    if (left >= before) {
        say(p, h, "§cกระเป๋าผมเต็มแล้ว§r รับของออกไปก่อนนะ");
        return false;
    }
    if (left > 0) {
        const rest = item.clone();
        rest.amount = left;
        c.setItem(slot, rest);
    }
    else
        c.setItem(slot, undefined);
    say(p, h, `เก็บ §b${itemName(item.typeId)} x${before - left}§r ไว้ในกระเป๋าแล้ว` +
        (fv > 0 ? " §7(ตอนนี้เลือดเต็มอยู่ ถ้าเจ็บค่อยให้อาหารนะ)" : ""));
    pop(p, h, 1.0);
    return true;
}
export function giveHeld(p, h) {
    const pc = playerContainer(p);
    const slot = p.selectedSlotIndex;
    if (!pc || !pc.getItem(slot)) {
        say(p, h, "ถือของที่จะให้ไว้ในมือก่อนนะ");
        return;
    }
    giveSlot(p, h, pc, slot);
}
// ------------------------------------------------------------------ command book
export function hasBook(p) {
    const c = playerContainer(p);
    if (!c)
        return false;
    for (let i = 0; i < c.size; i++)
        if (c.getItem(i)?.typeId === BOOK_ITEM)
            return true;
    return false;
}
export function giveBook(p, force = false) {
    if (!force && hasBook(p))
        return false;
    try {
        giveToPlayer(p, new ItemStack(BOOK_ITEM, 1));
        p.sendMessage("§6[ผู้ช่วย]§r คุณได้รับ §e§lหนังสือสั่งงานผู้ช่วย§r! ใช้หนังสือ (กดค้าง/คลิกขวา) เพื่อสั่งงาน คุย ส่งของ หรือให้อาหารผู้ช่วยได้จากทุกที่\n§7หรือพิมพ์ในแชท: §f/helper:talk ข้อความ §7เช่น §f/helper:talk ไปขุดแร่");
        return true;
    }
    catch {
        return false;
    }
}
/** Lists the items in the player's inventory that can be sent. */
export function giveableSlots(p, foodOnly) {
    const c = playerContainer(p);
    const out = [];
    if (!c)
        return out;
    for (let i = 0; i < c.size; i++) {
        const it = c.getItem(i);
        if (!it || it.typeId === BOOK_ITEM)
            continue;
        if (foodOnly && foodValue(it) === 0)
            continue;
        out.push({ slot: i, item: it });
    }
    return out;
}
export function helperBagHasRoom(h) {
    const u = usedSlots(h);
    return !!getContainer(h) && u.used < u.total;
}
export function delayed(fn, ticks = 1) {
    system.runTimeout(fn, ticks);
}
