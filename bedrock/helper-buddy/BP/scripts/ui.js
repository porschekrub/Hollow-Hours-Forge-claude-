import { ItemStack, system } from "@minecraft/server";
import { ActionFormData, FormCancelationReason, ModalFormData } from "@minecraft/server-ui";
import { BOOK_ITEM, bringTo, cmdCome, cmdFight, cmdFood, cmdMine, cmdStop, cmdWork, giveBook, giveHeld, giveSlot, giveableSlots, isNear, say, statusLines, } from "./actions.js";
import { SPEED_NAMES, cancelJob, getJob, getLastOpts, isFreeBuild, jobProgress, makeHouseJob, missingText, requiredMaterials, saveJob, saveLastOpts, showOutline, startJob, transferMaterials, } from "./builder.js";
import { describeHealth, feedFrom, foodValue, isFullHp, isKO } from "./combat.js";
import { ORE_GROUPS } from "./data.js";
import { MATS, ROOFS, SIZES, STYLES, TYPES, WOODS, houseName, variantName } from "./house_gen.js";
import { cancelMineJob, defaultTunnelY, describeMine, getMineJob, mineProgress } from "./minejob.js";
import { ownedHelpers, rememberHelper } from "./registry.js";
import { DEFAULT_NAME, getMode, getName, getOwnerName, getSettings, isOwner, modeLabel, refreshNameTag, saveSettings, setModeProp, setName, setOwner, } from "./state.js";
import { countId, depositToContainers, getTool, giveAllToPlayer, giveToPlayer, nearbyContainers, playerContainer, setTool, storageSummary, usedSlots, } from "./storage.js";
import { durabilityText } from "./tools.js";
import { CONTRACT_ITEM, dist, itemName } from "./util.js";
import { HELP_TEXT, applyHouseRel, handleTalk, norm, parseHouse, setHouseHooks } from "./talk.js";
import { VERSION } from "./version.js";
import { endTask } from "./work.js";
const busy = new Set();
const lastOpen = new Map();
async function showAction(p, f, tries = 6) {
    for (let i = 0; i < tries; i++) {
        const r = await f.show(p);
        if (r.canceled && r.cancelationReason === FormCancelationReason.UserBusy) {
            await system.waitTicks(8);
            continue;
        }
        return r;
    }
    return undefined;
}
async function showModal(p, f, tries = 6) {
    for (let i = 0; i < tries; i++) {
        const r = await f.show(p);
        if (r.canceled && r.cancelationReason === FormCancelationReason.UserBusy) {
            await system.waitTicks(8);
            continue;
        }
        return r;
    }
    return undefined;
}
function heldItem(p) {
    const c = playerContainer(p);
    const slot = p.selectedSlotIndex;
    return { item: c?.getItem(slot), slot };
}
/** Runs one menu at a time per player. */
function runMenu(p, fn) {
    if (busy.has(p.id))
        return;
    busy.add(p.id);
    fn()
        .catch((e) => console.warn(`[helper] menu error: ${e}`))
        .finally(() => busy.delete(p.id));
}
// ------------------------------------------------------------------ entry (tapping the helper)
export function openMenu(p, h, sneaking) {
    const now = system.currentTick;
    if (now - (lastOpen.get(p.id) ?? -100) < 6)
        return;
    lastOpen.set(p.id, now);
    if (busy.has(p.id) || !h.isValid)
        return;
    if (!getOwnerName(h)) {
        setOwner(h, p);
        say(p, h, "ตอนนี้ผมเป็นผู้ช่วยของคุณแล้วนะครับ!");
        giveBook(p);
    }
    if (!isOwner(h, p)) {
        p.sendMessage(`§7นี่คือผู้ช่วยของ §e${getOwnerName(h)}§7 คุณสั่งงานไม่ได้นะ`);
        return;
    }
    rememberHelper(p, h);
    const { item, slot } = heldItem(p);
    // holding food: feed it (like taming a pet) when it is hurt or knocked out
    if (item && foodValue(item) !== 0 && (isKO(h) || !isFullHp(h) || foodValue(item) < 0)) {
        const pc = playerContainer(p);
        if (pc)
            feedFrom(p, h, pc, slot);
        return;
    }
    if (sneaking && item && item.typeId !== BOOK_ITEM) {
        giveHeld(p, h);
        return;
    }
    runMenu(p, () => mainMenu(p, h));
}
/** Using the command book: remote control of the helper(s). */
export function openBook(p) {
    const now = system.currentTick;
    if (now - (lastOpen.get(p.id) ?? -100) < 10)
        return;
    lastOpen.set(p.id, now);
    runMenu(p, () => bookMenu(p));
}
function makeForm(title, body) {
    const f = new ActionFormData().title(title).body(body);
    const acts = [];
    const btn = (text, icon, fn) => {
        f.button(text, icon);
        acts.push(fn);
    };
    const run = async (p) => {
        const r = await showAction(p, f);
        if (!r || r.canceled || r.selection === undefined)
            return;
        await acts[r.selection]?.();
    };
    return { f, btn, run };
}
function workButtons(p, h, btn, back) {
    const job = getJob(h);
    const mj = getMineJob(h);
    btn("ขุดแร่\n§8ออกไปหาแร่เองในระยะ 100 บล็อก", "textures/items/iron_pickaxe", () => cmdWork(p, h, "mine"));
    btn("ตัดไม้\n§8ออกไปหาต้นไม้เองในระยะ 100 บล็อก", "textures/items/iron_axe", () => cmdWork(p, h, "chop"));
    btn("หาอาหาร\n§8ล่าสัตว์ เก็บผักที่สุก ย่างเนื้อเอง", "textures/items/beef_cooked", () => cmdFood(p, h, { hunt: true, crops: true }));
    if (mj)
        btn(`งานสร้างเหมือง (${mineProgress(h, mj)}%)\n§8ทำต่อ / ยกเลิก`, "textures/items/iron_shovel", () => mineJobMenu(p, h, back));
    else
        btn("สร้างเหมือง\n§8บันไดลงลึก + อุโมงค์ + กิ่งแยก", "textures/items/iron_shovel", () => mineMenu(p, h));
    if (job)
        btn(`งานสร้างบ้าน (${jobProgress(h, job)}%)\n§8สร้างต่อ / หยุด / ยกเลิก`, "textures/items/door_wood", () => houseJobMenu(p, h));
    else
        btn("สร้างบ้าน\n§8เลือกแบบบ้านที่อยากได้", "textures/items/door_wood", () => houseDesignMenu(p, h));
}
async function mainMenu(p, h) {
    const lines = statusLines(h, p);
    lines.push(`§8Helper Buddy v${VERSION}`);
    const held = heldItem(p).item;
    const { btn, run } = makeForm(`§l${getName(h)}`, lines.join("\n"));
    const back = () => mainMenu(p, h);
    btn("§1คุยกับผู้ช่วย\n§8พิมพ์สั่งงานเป็นประโยค", "textures/items/book_writable", () => talkPrompt(p, h));
    workButtons(p, h, btn, back);
    btn("เดินตามเรา\n§8หยุดงานแล้วตามมา", "textures/items/lead", () => cmdStop(p, h, "follow"));
    btn("ยืนรอตรงนี้\n§8ไม่ต้องตามมา", "textures/items/clock_item", () => cmdStop(p, h, "stay"));
    if (held && held.typeId !== BOOK_ITEM) {
        btn(`ให้ของในมือ\n§8${itemName(held.typeId)} x${held.amount}`, "textures/items/emerald", () => giveHeld(p, h));
    }
    btn("ส่งของให้ผู้ช่วย\n§8เลือกจากกระเป๋าเรา", "textures/ui/glyph_inventory", () => giveMenu(p, h, false, back));
    if (isKO(h) || !isFullHp(h))
        btn("§cให้อาหาร (รักษา)\n§8" + describeHealth(h).replace(/§./g, ""), "textures/items/bread", () => giveMenu(p, h, true, back));
    btn("กระเป๋าของผู้ช่วย\n§8ดู/รับของที่เก็บได้", "textures/blocks/chest_front", () => inventoryMenu(p, h));
    btn(getSettings(h).fight ? "ช่วยต่อสู้: §2เปิด\n§8กดเพื่อปิด" : "ช่วยต่อสู้: §4ปิด\n§8กดเพื่อเปิด", "textures/items/iron_sword", () => cmdFight(p, h, !getSettings(h).fight));
    btn("ตั้งค่า\n§8เลือกแร่ ตั้งชื่อ ฯลฯ", "textures/ui/gear", () => settingsMenu(p, h));
    btn("ปลดผู้ช่วย\n§8คืนของทั้งหมดแล้วให้กลับบ้าน", "textures/ui/cancel", () => dismissMenu(p, h));
    await run(p);
}
// ------------------------------------------------------------------ command book (remote)
async function bookMenu(p) {
    const mine = ownedHelpers(p);
    if (!mine.length) {
        p.sendMessage("§6[ผู้ช่วย]§r ยังไม่มีผู้ช่วยที่ติดต่อได้ วาง §eสัญญาจ้างผู้ช่วย§r เพื่อเรียกผู้ช่วยก่อนนะ\n§7(ถ้ามีผู้ช่วยอยู่แล้ว อาจอยู่ในพื้นที่ที่โลกยังไม่โหลด)");
        return;
    }
    if (mine.length === 1)
        return remoteMenu(p, mine[0], false);
    const { btn, run } = makeForm("หนังสือสั่งงาน", `§7เลือกผู้ช่วยที่จะสั่งงาน\n§8Helper Buddy v${VERSION}`);
    for (const h of mine) {
        const far = h.dimension.id !== p.dimension.id ? "คนละมิติ" : `${Math.round(dist(h.location, p.location))} บล็อก`;
        btn(`§l${getName(h)}§r ${modeLabel(getMode(h))}\n§8${describeHealth(h).replace(/§./g, "")} • ${far}`, "textures/ui/icon_recipe_nature", () => remoteMenu(p, h, true));
    }
    btn("ทุกตัว: เรียกมาหาเรา", "textures/items/ender_pearl", () => {
        for (const h of mine)
            cmdCome(p, h);
    });
    await run(p);
}
async function remoteMenu(p, h, many) {
    if (!h.isValid)
        return;
    rememberHelper(p, h);
    const near = isNear(p, h, 10);
    const lines = statusLines(h, p);
    lines.push("\n§7สั่งได้จากทุกที่ พิมพ์ในแชทก็ได้: §f/helper:talk ข้อความ");
    lines.push(`§8Helper Buddy v${VERSION}`);
    const { btn, run } = makeForm(`§lหนังสือสั่งงาน: ${getName(h)}`, lines.join("\n"));
    const back = () => remoteMenu(p, h, many);
    btn("§1คุยกับผู้ช่วย\n§8พิมพ์สั่งงานเป็นประโยค", "textures/items/book_writable", () => talkPrompt(p, h));
    if (!near)
        btn("เรียกมาหาเรา\n§8วาร์ปมาข้างๆ แล้วเดินตาม", "textures/items/ender_pearl", () => cmdCome(p, h));
    workButtons(p, h, btn, back);
    btn("ส่งของให้ผู้ช่วย\n§8เครื่องมือ คบเพลิง ฯลฯ (ส่งจากระยะไกลได้)", "textures/ui/glyph_inventory", () => giveMenu(p, h, false, back));
    if (isKO(h) || !isFullHp(h))
        btn("§cให้อาหาร (รักษา)\n§8" + describeHealth(h).replace(/§./g, ""), "textures/items/bread", () => giveMenu(p, h, true, back));
    btn("กระเป๋าของผู้ช่วย\n§8ดู/รับของที่เก็บได้", "textures/blocks/chest_front", () => inventoryMenu(p, h));
    btn("หยุดงาน ยืนรอ\n§8หยุดอยู่ตรงที่อยู่ตอนนี้", "textures/items/clock_item", () => cmdStop(p, h, "stay"));
    btn("เดินตามเรา", "textures/items/lead", () => {
        cmdStop(p, h, "follow", true);
        if (!isNear(p, h, 20))
            bringTo(h, p);
        say(p, h, "กำลังไปหาคุณครับ จะเดินตามไปนะ");
    });
    btn(getSettings(h).fight ? "ช่วยต่อสู้: §2เปิด\n§8กดเพื่อปิด" : "ช่วยต่อสู้: §4ปิด\n§8กดเพื่อเปิด", "textures/items/iron_sword", () => cmdFight(p, h, !getSettings(h).fight));
    btn("ตั้งค่า", "textures/ui/gear", () => settingsMenu(p, h));
    if (many)
        btn("เลือกผู้ช่วยตัวอื่น", "textures/ui/arrow_dark_left_stretch", () => bookMenu(p));
    await run(p);
}
// ------------------------------------------------------------------ talk
export async function talkPrompt(p, h) {
    const f = new ModalFormData().title(`คุยกับ ${getName(h)}`);
    f.textField("พิมพ์สิ่งที่อยากให้ผมทำ เป็นประโยคธรรมดาได้เลย สั่งหลายอย่างต่อกันด้วยคำว่า §eแล้ว§r ก็ได้\n§7ตัวอย่าง: ตัดไม้ 20 ท่อน แล้วขุดทราย 2 กอง แล้วเอามาให้ • หาอาหาร • ล่าวัว 3 ตัว แล้วทำอาหาร • ขุดเพชรจนกระเป๋าเต็ม แล้วเก็บเข้าหีบ • สร้างบ้านโมเดิร์น 3 ชั้น • ไปที่ 100 64 -50 • สถานะ", "พิมพ์ข้อความ...");
    f.submitButton("ส่ง");
    const r = await showModal(p, f);
    if (!r || r.canceled || !r.formValues)
        return;
    const text = String(r.formValues[0] ?? "").trim();
    if (!text) {
        p.sendMessage(HELP_TEXT);
        return;
    }
    await handleTalk(p, h, text);
}
// ------------------------------------------------------------------ give items (any distance)
async function giveMenu(p, h, foodOnly, back) {
    for (let round = 0; round < 40; round++) {
        if (!h.isValid)
            return;
        const list = giveableSlots(p, foodOnly).slice(0, 45);
        const head = foodOnly
            ? `${describeHealth(h)}\n§7เลือกอาหารจากกระเป๋าคุณ (อะไรก็ได้ ยกเว้นเนื้อเน่า)`
            : `§7เลือกของจากกระเป๋าคุณเพื่อส่งให้ ${getName(h)} (ส่งทั้งกอง)\n§7เครื่องมือ: อีเต้อ = ขุดหิน/แร่, พลั่ว = ขุดดิน/ทราย/กรวด, ขวาน = ตัดไม้`;
        if (!list.length) {
            p.sendMessage(foodOnly ? "§7คุณไม่มีอาหารในกระเป๋าเลย" : "§7กระเป๋าคุณว่างเปล่า");
            return;
        }
        const f = new ActionFormData().title(foodOnly ? "ให้อาหารผู้ช่วย" : "ส่งของให้ผู้ช่วย").body(head);
        for (const { item } of list) {
            const dur = durabilityText(item);
            f.button(`${itemName(item.typeId)} x${item.amount}${dur ? `\n${dur}` : ""}`);
        }
        f.button("§8เสร็จแล้ว / กลับ", "textures/ui/arrow_dark_left_stretch");
        const r = await showAction(p, f);
        if (!r || r.canceled || r.selection === undefined)
            return;
        if (r.selection >= list.length)
            return back();
        const pc = playerContainer(p);
        if (!pc)
            return;
        const pick = list[r.selection];
        // the inventory may have changed while the form was open
        if (pc.getItem(pick.slot)?.typeId !== pick.item.typeId)
            continue;
        giveSlot(p, h, pc, pick.slot);
        if (foodOnly && !isKO(h) && isFullHp(h))
            return;
    }
}
// ------------------------------------------------------------------ mine
const LEVELS = [
    { th: "ลึกสุด: เพชร เรดสโตน (Y -54)", y: -54 },
    { th: "ทอง (Y -16)", y: -16 },
    { th: "ลาพิส (Y 0)", y: 0 },
    { th: "เหล็ก (Y 16)", y: 16 },
    { th: "ทองแดง ถ่านหิน (Y 48)", y: 48 },
    { th: "ไม่ต้องขุดลง: อุโมงค์ที่ระดับนี้", flat: true },
    { th: "กำหนดเอง (ใส่ค่า Y ด้านล่าง)" },
];
async function mineMenu(p, h) {
    const near = isNear(p, h, 48);
    const nether = h.dimension.id.includes("nether");
    const f = new ModalFormData().title("สร้างเหมือง");
    f.dropdown("§7ผู้ช่วยจะขุดบันไดลงไปถึงระดับที่เลือก แล้วขุดอุโมงค์หลักกับกิ่งซ้าย-ขวา แร่ที่เจอข้างทางจะขุดเก็บให้หมด\n§eต้องมีอีเต้อ (และพลั่วสำหรับดิน/ทราย/กรวด)§r\n\nจุดเริ่มต้น", near ? ["ตรงที่ฉันยืน ขุดไปทางที่ฉันหันหน้า", "ตรงที่ผู้ช่วยอยู่ตอนนี้"] : ["ตรงที่ผู้ช่วยอยู่ตอนนี้"], { defaultValueIndex: 0 });
    f.dropdown(nether ? "ระดับความลึก §7(เนเธอร์: เศษโบราณอยู่ที่ Y 15)" : "ลงไปที่ระดับ (หาแร่อะไร)", nether ? ["เศษโบราณ (Y 15)", "ไม่ต้องขุดลง: อุโมงค์ที่ระดับนี้", "กำหนดเอง"] : LEVELS.map((l) => l.th), { defaultValueIndex: 0 });
    f.textField("ค่า Y (ถ้าเลือกกำหนดเอง)", `${defaultTunnelY(h.dimension.id)}`);
    f.slider("ความยาวอุโมงค์หลัก (บล็อก)", 8, 96, { valueStep: 4, defaultValue: 32 });
    f.slider("ความยาวกิ่งแยกซ้าย-ขวา (0 = ไม่มีกิ่ง)", 0, 24, { valueStep: 2, defaultValue: 10 });
    f.dropdown("ทิศทาง", ["ตามที่หันหน้าอยู่", "ทิศเหนือ", "ทิศใต้", "ทิศตะวันออก", "ทิศตะวันตก"], { defaultValueIndex: 0 });
    f.submitButton("เริ่มสร้างเหมือง");
    const r = await showModal(p, f);
    if (!r || r.canceled || !r.formValues)
        return;
    const v = r.formValues;
    const atHelper = !near || Number(v[0]) === 1;
    const li = Number(v[1] ?? 0);
    const custom = Number.parseInt(String(v[2] ?? ""), 10);
    let y;
    let flat = false;
    if (nether) {
        if (li === 0)
            y = 15;
        else if (li === 1)
            flat = true;
        else
            y = Number.isFinite(custom) ? custom : 15;
    }
    else {
        const L = LEVELS[li] ?? LEVELS[0];
        if (L.flat)
            flat = true;
        else if (L.y !== undefined)
            y = L.y;
        else
            y = Number.isFinite(custom) ? custom : defaultTunnelY(h.dimension.id);
    }
    const dirs = [undefined, "north", "south", "east", "west"];
    cmdMine(p, h, {
        y, flat, atHelper, length: Math.round(Number(v[3] ?? 32)), branch: Math.round(Number(v[4] ?? 10)),
        dir: dirs[Number(v[5] ?? 0)],
    });
}
async function mineJobMenu(p, h, back) {
    const j = getMineJob(h);
    if (!j)
        return mineMenu(p, h);
    const digging = getMode(h) === "dig";
    const body = [
        `§f${describeMine(j)}`,
        `§fความคืบหน้า: §e${mineProgress(h, j)}%§r • ขุดไป ${j.dug} บล็อก • แร่ ${j.ores} ก้อน`,
        `§fทางเข้า: §7${j.o.x}, ${j.o.y}, ${j.o.z}`,
        digging ? "§aกำลังขุดอยู่" : "§7หยุดไว้ชั่วคราว",
    ].join("\n");
    const { btn, run } = makeForm("งานสร้างเหมือง", body);
    if (!digging)
        btn("§2ขุดต่อ", "textures/ui/check", () => {
            endTask(h);
            setModeProp(h, "dig");
            refreshNameTag(h);
            say(p, h, "ไปสร้างเหมืองต่อละนะ!");
        });
    else
        btn("หยุดชั่วคราว (ยืนรอ)", "textures/items/clock_item", () => cmdStop(p, h, "stay"));
    btn("§cยกเลิกงานสร้างเหมือง", "textures/ui/cancel", () => {
        cancelMineJob(h);
        setModeProp(h, "stay");
        refreshNameTag(h);
        say(p, h, "ยกเลิกงานสร้างเหมืองแล้ว (ส่วนที่ขุดไปแล้วยังอยู่นะ)");
    });
    btn("สร้างเหมืองใหม่", "textures/items/iron_shovel", () => mineMenu(p, h));
    btn("กลับ", "textures/ui/arrow_dark_left_stretch", back);
    await run(p);
}
// ------------------------------------------------------------------ inventory
export async function inventoryMenu(p, h) {
    const sum = storageSummary(h);
    const tool = getTool(h);
    const lines = [];
    if (tool)
        lines.push(`§eเครื่องมือที่ถือ:§r ${itemName(tool.typeId)} ${durabilityText(tool)}`);
    const arr = [...sum.entries()].sort((a, b) => b[1] - a[1]);
    if (!arr.length)
        lines.push("§7กระเป๋าว่างเปล่า");
    for (const [id, n] of arr.slice(0, 18))
        lines.push(`§f${itemName(id)} §7x${n}`);
    if (arr.length > 18)
        lines.push(`§7...และอีก ${arr.length - 18} อย่าง`);
    const u = usedSlots(h);
    lines.push(`\n§7ใช้ไป ${u.used}/${u.total} ช่อง`);
    const job = getJob(h);
    const chests = nearbyContainers(h, h.location, 8);
    const f = new ActionFormData().title("กระเป๋าของผู้ช่วย").body(lines.join("\n"));
    const acts = [];
    const btn = (t, icon, fn) => {
        f.button(t, icon);
        acts.push(fn);
    };
    if (arr.length)
        btn("รับของทั้งหมด\n§8(ยกเว้นเครื่องมือที่ถืออยู่)", "textures/ui/glyph_inventory", () => {
            const n = giveAllToPlayer(h, p, false);
            say(p, h, `ส่งของคืนให้แล้ว ${n} ชิ้น`);
        });
    if (arr.length)
        btn("รับเฉพาะแร่และของที่ขุด/ตัดได้", "textures/items/diamond", () => {
            const n = giveAllToPlayer(h, p, false, (it) => isLoot(it.typeId));
            say(p, h, n ? `ส่งของที่ขุด/ตัดได้ให้แล้ว ${n} ชิ้น` : "ยังไม่มีของที่ขุดหรือตัดได้เลย");
        });
    if (tool)
        btn("รับเครื่องมือคืน", "textures/items/iron_pickaxe", () => {
            setTool(h, undefined);
            giveToPlayer(p, tool);
            say(p, h, `คืน ${itemName(tool.typeId)} ให้แล้ว`);
        });
    if (chests.length && arr.length)
        btn(`เก็บของเข้าหีบใกล้ๆ (${chests.length} หีบ)`, "textures/blocks/chest_front", () => {
            const n = depositToContainers(h, chests);
            say(p, h, n ? `เก็บของเข้าหีบแล้ว ${n} ชิ้น` : "หีบเต็มหมดแล้ว");
        });
    if (job && !isFreeBuild(h, job))
        btn("ส่งบล็อกสร้างบ้านจากกระเป๋าเรา", "textures/items/door_wood", () => doTransfer(p, h, job));
    btn("กลับ", "textures/ui/arrow_dark_left_stretch", () => (isNear(p, h, 10) ? mainMenu(p, h) : remoteMenu(p, h, false)));
    const r = await showAction(p, f);
    if (!r || r.canceled || r.selection === undefined)
        return;
    await acts[r.selection]?.();
}
function isLoot(id) {
    const n = id.replace("minecraft:", "");
    return /(^raw_|coal$|diamond$|emerald$|lapis_lazuli$|redstone$|quartz$|gold_nugget$|ancient_debris$|_ore$|_log$|_stem$|sapling$|propagule$|^apple$|^stick$|^flint$|cobblestone$|cobbled_deepslate$)/.test(n);
}
// ------------------------------------------------------------------ settings
export async function settingsMenu(p, h) {
    const s = getSettings(h);
    const f = new ModalFormData().title("ตั้งค่าผู้ช่วย");
    f.textField("ชื่อผู้ช่วย", DEFAULT_NAME, { defaultValue: getName(h) });
    for (const g of ORE_GROUPS)
        f.toggle(`ขุดแร่: ${g.th}`, { defaultValue: s.ores[g.id] !== false });
    f.toggle("ขุดทะลุหิน/ดินเพื่อไปเอาแร่ที่ซ่อนอยู่", { defaultValue: s.dig });
    f.toggle("ปลูกต้นกล้าคืนหลังตัดไม้", { defaultValue: s.replant });
    f.toggle("เก็บต้นกล้า/กิ่งไม้/แอปเปิลที่ตกพื้น", { defaultValue: s.pickup });
    f.toggle("เอาของเข้าหีบใกล้ๆ เมื่อกระเป๋าเต็ม", { defaultValue: s.chest });
    f.toggle("สร้างบ้านแบบต้องใช้บล็อกจริง (ปิด = ผู้ช่วยสร้างได้เลยไม่ต้องใช้บล็อก)", { defaultValue: s.needBlocks });
    f.toggle("ถ้าต้องใช้บล็อก: ให้หยิบบล็อกจากหีบใกล้ๆ เองได้", { defaultValue: s.chestBuild });
    f.toggle("สู้กลับ/ช่วยต่อสู้ (โดนอะไรโจมตีก็สู้กลับ + ปกป้องเรา + สู้ครีปเปอร์ด้วย) — เปิดเป็นค่าเริ่มต้น", { defaultValue: s.fight });
    f.toggle("เก็บหิน/ดินที่ขุดได้ทั้งหมด (ปิด = เก็บไว้แค่อย่างละ 1 กอง ที่เหลือทิ้ง กระเป๋าจะได้ไม่เต็มเร็ว)", { defaultValue: s.keepJunk });
    f.toggle("ดูแลตัวเอง (กินอาหารในกระเป๋าเมื่อเลือดน้อย ย่างเนื้อเอง และไปหาอาหารเองเมื่อหิว)", { defaultValue: s.selfCare });
    f.submitButton("บันทึก");
    const r = await showModal(p, f);
    if (!r || r.canceled || !r.formValues)
        return;
    const v = r.formValues;
    let i = 0;
    const name = String(v[i++] ?? "").trim().slice(0, 24);
    for (const g of ORE_GROUPS)
        s.ores[g.id] = !!v[i++];
    s.dig = !!v[i++];
    s.replant = !!v[i++];
    s.pickup = !!v[i++];
    s.chest = !!v[i++];
    s.needBlocks = !!v[i++];
    s.chestBuild = !!v[i++];
    s.fight = !!v[i++];
    s.keepJunk = !!v[i++];
    s.selfCare = !!v[i++];
    saveSettings(h, s);
    if (name.length)
        setName(h, name);
    refreshNameTag(h);
    say(p, h, "บันทึกการตั้งค่าแล้ว!");
}
// ------------------------------------------------------------------ dismiss
async function dismissMenu(p, h) {
    const f = new ActionFormData()
        .title("ปลดผู้ช่วย?")
        .body("ผู้ช่วยจะคืนของทั้งหมด (รวมเครื่องมือ) และคืน §eสัญญาจ้างผู้ช่วย§r ให้คุณ แล้วจะหายไป\n\nแน่ใจไหม?")
        .button("§cใช่ ปลดเลย", "textures/ui/check")
        .button("ไม่ ยกเลิก", "textures/ui/cancel");
    const r = await showAction(p, f);
    if (!r || r.canceled || r.selection !== 0 || !h.isValid)
        return;
    giveAllToPlayer(h, p, true);
    try {
        giveToPlayer(p, new ItemStack(CONTRACT_ITEM, 1));
    }
    catch {
        /* ignore */
    }
    saveJob(h, undefined);
    say(p, h, "ขอบคุณที่ใช้งานนะครับ ลาก่อน!");
    try {
        h.dimension.spawnParticle("minecraft:villager_happy", h.location);
        h.dimension.playSound("mob.villager.yes", h.location);
    }
    catch {
        /* ignore */
    }
    h.remove();
}
// ------------------------------------------------------------------ house
const DEFAULT_HOUSE = {
    size: 1, floors: 2, style: 0, wood: 0, roof: 0, light: true, furniture: true, porch: true, chimney: true, pool: true, speed: 1,
    type: "standard", mat: 0,
};
export async function houseDesignMenu(p, h, presetText) {
    const last = { ...DEFAULT_HOUSE, ...(getLastOpts(h) ?? {}) };
    const f = new ModalFormData().title("ออกแบบบ้าน");
    f.textField("§e✎ พิมพ์บอกแบบบ้านที่อยากได้ (ไม่พิมพ์ก็ได้ ใช้ตัวเลือกด้านล่าง)§r\n§7ตัวอย่าง: บ้านใต้ดิน 2 ชั้น • ปราสาทหินใหญ่ • บ้านต้นไม้ไม้เบิร์ช • อิกลู • หอคอยสูง • บ้านโมเดิร์น 3 ชั้น มีสระ • บ้านทะเลทราย • ปิรามิด\n§7สิ่งที่พิมพ์จะ§aแทนที่§7ตัวเลือกด้านล่าง", "เช่น บ้านใต้ดิน 2 ชั้น", { defaultValue: presetText ?? "" });
    f.dropdown("§7บ้านจะถูกสร้าง §eตรงหน้าคุณ§7 ให้หันหน้าไปทางที่อยากได้บ้านก่อนกดถัดไป§r\n\nประเภทบ้าน", TYPES.map((t) => t.th), { defaultValueIndex: Math.max(0, TYPES.findIndex((t) => t.id === last.type)) });
    f.dropdown("วัสดุ (ใช้กับบ้านประเภทใหม่ๆ ไม่ใช่ 'บ้านทั่วไป')", MATS.map((m) => m.th), { defaultValueIndex: Math.min(last.mat ?? 0, MATS.length - 1) });
    f.dropdown("ขนาดบ้าน", SIZES.map((s) => s.th), { defaultValueIndex: last.size });
    f.slider("จำนวนชั้น (หอคอยจะคูณ 2)", 1, 3, { valueStep: 1, defaultValue: last.floors });
    f.dropdown("สไตล์บ้าน (เฉพาะประเภท 'บ้านทั่วไป')", STYLES, { defaultValueIndex: last.style });
    f.dropdown("ชนิดไม้ (พื้น ประตู เฟอร์นิเจอร์)", WOODS.map((w) => w.th), { defaultValueIndex: last.wood });
    f.dropdown("หลังคา (เฉพาะ 'บ้านทั่วไป')", ROOFS, { defaultValueIndex: Math.min(last.roof, ROOFS.length - 1) });
    f.toggle("ตะเกียงให้แสงสว่าง", { defaultValue: last.light });
    f.toggle("ของใช้ในบ้าน (เตาเผา หีบ ทั่ง โต๊ะร่ายมนตร์ เตียง ฯลฯ)", { defaultValue: last.furniture });
    f.toggle("ทางเข้าหน้าบ้าน (บันได กันสาด เสาไฟ)", { defaultValue: last.porch });
    f.toggle("ปล่องไฟ (บ้านคันทรี/ยุคกลาง)", { defaultValue: last.chimney });
    f.toggle("สระว่ายน้ำใต้ชั้นยื่น (บ้านโมเดิร์น)", { defaultValue: last.pool ?? true });
    f.dropdown("ความเร็วในการสร้าง", SPEED_NAMES, { defaultValueIndex: last.speed });
    f.submitButton("ถัดไป");
    const r = await showModal(p, f);
    if (!r || r.canceled || !r.formValues)
        return;
    const v = r.formValues;
    let opts = {
        type: TYPES[Number(v[1] ?? 0)]?.id ?? "standard",
        mat: Number(v[2] ?? 0),
        size: Number(v[3] ?? 1),
        floors: Math.round(Number(v[4] ?? 2)),
        style: Number(v[5] ?? 0),
        wood: Number(v[6] ?? 0),
        roof: Number(v[7] ?? 0),
        light: !!v[8],
        furniture: !!v[9],
        porch: !!v[10],
        chimney: !!v[11],
        pool: !!v[12],
        speed: Number(v[13] ?? 1),
    };
    const typed = String(v[0] ?? "").trim();
    if (typed) {
        const pr = parseHouse(norm(typed));
        if (pr.said.length) {
            opts = applyHouseRel(opts, pr.o);
            say(p, h, `เข้าใจแล้ว! ${pr.said.join(", ")}`);
        }
        else {
            say(p, h, `ผมยังไม่แน่ใจว่า "${typed}" หมายถึงบ้านแบบไหน เลยใช้ตัวเลือกด้านล่างแทนนะ (ลองพิมพ์ เช่น "บ้านใต้ดิน 2 ชั้น")`);
        }
    }
    saveLastOpts(h, opts);
    await confirmSite(p, h, opts);
}
export async function confirmSite(p, h, opts) {
    const { job, site } = makeHouseJob(p, opts);
    const house = job.opts;
    showOutline(p, job, 12);
    if (!site.ok) {
        const f = new ActionFormData()
            .title("สร้างตรงนี้ไม่ได้")
            .body(`§c${site.reason}\n\n§7(ประกายสีเขียวคือขอบเขตของบ้าน)`)
            .button("ลองใหม่ (หันไปทางอื่นก่อนแล้วกด)", "textures/ui/refresh_light")
            .button("เปลี่ยนแบบบ้าน", "textures/items/door_wood")
            .button("ปิด", "textures/ui/cancel");
        const r = await showAction(p, f);
        if (!r || r.canceled)
            return;
        if (r.selection === 0)
            return confirmSite(p, h, opts);
        if (r.selection === 1)
            return houseDesignMenu(p, h);
        return;
    }
    const bom = requiredMaterials(job);
    const free = isFreeBuild(h, job);
    const pc = playerContainer(p);
    const playerCount = (id) => {
        let n = 0;
        if (!pc)
            return 0;
        for (let i = 0; i < pc.size; i++) {
            const it = pc.getItem(i);
            if (it && it.typeId === id)
                n += it.amount;
        }
        return n;
    };
    const req = [];
    const opt = [];
    let total = 0;
    let missingAll = 0;
    for (const [id, n] of [...bom.entries()].sort((a, b) => b[1].req - a[1].req)) {
        total += n.req + n.opt;
        const have = countId(h, id);
        const mine = playerCount(id);
        if (n.req > 0) {
            const ok = have + mine >= n.req;
            if (!ok)
                missingAll += n.req - have - mine;
            req.push(`${ok ? "§a✔" : "§c✘"} §f${itemName(id)}: §e${n.req}§7 (ผู้ช่วยมี ${have}, คุณมี ${mine})`);
        }
        if (n.opt > 0)
            opt.push(`§7• ${itemName(id)}: ${n.opt}`);
    }
    const body = [];
    body.push(`§fแบบ: §d${houseName(house)}${variantName(house) ? " (" + variantName(house) + ")" : ""}§f • ${SIZES[house.size].th} • ${house.floors} ชั้น`);
    body.push(`§fไม้: ${WOODS[house.wood].th} • หลังคา: ${ROOFS[house.roof]}`);
    body.push(`§fทั้งหมดประมาณ §e${total}§f บล็อก${site.clearCount ? ` §7(ต้องเคลียร์พื้นที่ ${site.clearCount} บล็อก)` : ""}`);
    const fillCount = site.fill.reduce((n, f) => n + f.n, 0);
    if (site.poolDropped)
        body.push("§7(ใต้ดินตรงนี้ไม่เหมาะทำสระ เลยข้ามสระว่ายน้ำไปนะ)");
    if (fillCount)
        body.push(`§7พื้นไม่เรียบ ต้องเสริมฐานราก ${fillCount} บล็อก (รวมในรายการแล้ว)`);
    body.push("§7ดูประกายสีเขียวรอบๆ = ขอบเขตบ้าน");
    if (free) {
        body.push("\n§a§lไม่ต้องใช้บล็อกจากคุณ!§r§a ผู้ช่วยจัดการบล็อกเองทั้งหมด กดเริ่มสร้างได้เลย");
        body.push("§7(อยากให้ต้องใช้บล็อกจริง เปิดได้ที่ ตั้งค่า)");
    }
    else {
        body.push("\n§l§6บล็อกที่ต้องเอาให้ผู้ช่วย:§r");
        body.push(...req);
        if (opt.length) {
            body.push("\n§l§6ของตกแต่ง (ไม่ให้ก็ได้ จะข้ามไป):§r");
            body.push(...opt);
        }
        body.push(missingAll > 0
            ? `\n§eยังขาดอีก ${missingAll} ชิ้น§f — ผู้ช่วยจะวางได้เท่าที่มีบล็อก แล้วหยุดรอจนกว่าเราจะเอาบล็อกมาให้`
            : "\n§aบล็อกครบแล้ว! กดส่งให้ผู้ช่วยได้เลย");
        body.push("§7ผู้ช่วยถือของได้ 36 ช่อง ส่งเพิ่มระหว่างสร้างได้");
    }
    const f = new ActionFormData().title("ยืนยันการสร้างบ้าน").body(body.join("\n"));
    const acts = [];
    const btn = (t, icon, fn) => {
        f.button(t, icon);
        acts.push(fn);
    };
    if (free)
        btn("§2เริ่มสร้างเลย!", "textures/ui/check", () => begin(p, h, job));
    else {
        btn("§2ส่งบล็อกจากกระเป๋าเรา + เริ่มสร้าง", "textures/ui/check", () => {
            begin(p, h, job);
            doTransfer(p, h, job);
        });
        btn("เริ่มสร้าง (เอาบล็อกให้ทีหลัง)", "textures/items/door_wood", () => begin(p, h, job));
    }
    btn("เปลี่ยนแบบบ้าน", "textures/ui/editIcon", () => houseDesignMenu(p, h));
    btn("ยกเลิก", "textures/ui/cancel", () => undefined);
    const r = await showAction(p, f, 20);
    if (!r || r.canceled || r.selection === undefined)
        return;
    await acts[r.selection]?.();
}
function begin(p, h, job) {
    endTask(h);
    saveLastOpts(h, { ...job.opts, speed: job.speed });
    if (getMineJob(h))
        cancelMineJob(h);
    // the house is built next to the player: bring the helper over when it is far away
    if (h.dimension.id !== job.dim || dist(h.location, { x: job.ox, y: job.oy, z: job.oz }) > 64)
        bringTo(h, p);
    startJob(h, job);
    refreshNameTag(h);
    say(p, h, "§dเริ่มสร้างบ้านแล้ว!§r ผมจะเคลียร์พื้นที่ก่อน แล้วค่อยๆ วางบล็อกจากฐานขึ้นไป\n§7(กดที่ตัวผมเพื่อดูความคืบหน้า หยุด หรือยกเลิกได้)");
}
function doTransfer(p, h, job) {
    const res = transferMaterials(p, h, job);
    const mt = missingText(h, job, 6);
    if (res.moved > 0)
        say(p, h, `รับบล็อกจากคุณแล้ว §e${res.moved}§r ชิ้น ขอบคุณครับ!`);
    else
        say(p, h, "ในกระเป๋าคุณไม่มีบล็อกที่ต้องใช้เลย ไปหามาให้ผมหน่อยนะ");
    if (res.full)
        say(p, h, "§eกระเป๋าผมเต็มแล้ว§r ใช้ของไปก่อน แล้วค่อยส่งที่เหลือมาใหม่นะ");
    else if (mt.missing > 0)
        say(p, h, `ยังขาดอีก:\n${mt.text}`);
}
export async function houseJobMenu(p, h) {
    const job = getJob(h);
    if (!job)
        return houseDesignMenu(p, h);
    const building = getMode(h) === "build";
    const mt = missingText(h, job, 10);
    const lines = [
        `§fแบบ: §d${houseName(job.opts)}${variantName(job.opts) ? " (" + variantName(job.opts) + ")" : ""}§f • ${SIZES[job.opts.size].th} • ${job.opts.floors} ชั้น`,
        `§fความคืบหน้า: §e${jobProgress(h, job)}%`,
        building ? "§aกำลังสร้างอยู่" : "§7หยุดไว้ชั่วคราว",
    ];
    if (isFreeBuild(h, job))
        lines.push("\n§aไม่ต้องใช้บล็อกจากคุณ");
    else if (mt.missing > 0)
        lines.push(`\n§6บล็อกที่ยังต้องเอามาให้:§r\n${mt.text}`);
    else
        lines.push("\n§aผู้ช่วยมีบล็อกครบแล้ว!");
    const f = new ActionFormData().title("งานสร้างบ้าน").body(lines.join("\n"));
    const acts = [];
    const btn = (t, icon, fn) => {
        f.button(t, icon);
        acts.push(fn);
    };
    if (!building)
        btn("§2สร้างต่อ", "textures/ui/check", () => {
            endTask(h);
            setModeProp(h, "build");
            refreshNameTag(h);
            say(p, h, "ไปสร้างบ้านต่อละนะ!");
        });
    else
        btn("หยุดสร้างชั่วคราว", "textures/items/clock_item", () => cmdStop(p, h, "follow"));
    if (!isFreeBuild(h, job))
        btn("ส่งบล็อกจากกระเป๋าเรา", "textures/ui/glyph_inventory", () => doTransfer(p, h, job));
    btn("§cยกเลิกงานสร้างบ้าน", "textures/ui/cancel", async () => {
        const c = new ActionFormData()
            .title("ยกเลิกงานสร้าง?")
            .body("ส่วนที่สร้างไปแล้วจะยังอยู่ บล็อกที่เหลือยังอยู่ในกระเป๋าผู้ช่วย (รับคืนได้ที่เมนูกระเป๋า)\nแน่ใจไหม?")
            .button("§cใช่ ยกเลิก")
            .button("ไม่");
        const rr = await showAction(p, c);
        if (!rr || rr.canceled || rr.selection !== 0)
            return;
        cancelJob(h);
        setModeProp(h, "follow");
        refreshNameTag(h);
        say(p, h, "ยกเลิกงานสร้างบ้านแล้ว");
    });
    btn("กลับ", "textures/ui/arrow_dark_left_stretch", () => (isNear(p, h, 10) ? mainMenu(p, h) : remoteMenu(p, h, false)));
    const r = await showAction(p, f);
    if (!r || r.canceled || r.selection === undefined)
        return;
    await acts[r.selection]?.();
}
export function quickGive(p, h) {
    giveHeld(p, h);
}
// ------------------------------------------------------------------ "I did not understand" helper
const QUICK_ORDERS = [
    ["ขุดแร่", "ไปขุดแร่", "textures/items/iron_pickaxe"],
    ["ตัดไม้", "ไปตัดไม้", "textures/items/iron_axe"],
    ["หาอาหาร", "หาอาหาร", "textures/items/beef_cooked"],
    ["สร้างบ้าน", "สร้างบ้าน", "textures/items/door_wood"],
    ["สร้างเหมือง", "สร้างเหมือง", "textures/items/iron_shovel"],
    ["มาหาเรา", "มานี่", undefined],
    ["เดินตามเรา", "ตามมา", undefined],
    ["รออยู่ตรงนี้", "รอตรงนี้", undefined],
    ["ดูสถานะ", "สถานะ", undefined],
    ["ดูคำสั่งทั้งหมด", "ช่วย", undefined],
];
async function fallbackMenu(p, h, text) {
    const f = new ActionFormData()
        .title("ผมไม่แน่ใจว่าหมายถึงอะไร")
        .body(`§7ผมฟังประโยค §f"${text}"§7 ไม่ออก เลือกสิ่งที่ใกล้เคียงที่สุดได้เลย\n§7(สร้างบ้านได้หลายแบบ: ใต้ดิน ปราสาท หอคอย บ้านต้นไม้ อิกลู ปิรามิด ฯลฯ — ลองพิมพ์ เช่น "บ้านใต้ดิน 2 ชั้น")`);
    for (const [label, , icon] of QUICK_ORDERS)
        f.button(label, icon);
    f.button("ปิด", "textures/ui/cancel");
    const r = await showAction(p, f);
    if (!r || r.canceled || r.selection === undefined || r.selection >= QUICK_ORDERS.length)
        return;
    await handleTalk(p, h, QUICK_ORDERS[r.selection][1]);
}
// talk.ts asks the UI to show the house forms
setHouseHooks({
    design: (p, h) => houseDesignMenu(p, h),
    fallback: (p, h, text) => fallbackMenu(p, h, text),
    confirm: (p, h, o) => confirmSite(p, h, o),
});
