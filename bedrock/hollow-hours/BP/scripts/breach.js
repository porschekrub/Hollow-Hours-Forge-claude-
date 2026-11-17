import { isEn } from "./lang.js";
import { world, system, BlockPermutation } from "@minecraft/server";
import {
  fl, rand, pick, valid, dist, getBlock, blockId, stateOf, keyOf, center, setAir, destroyBlock, sound, particle,
  dirVars, shake, playersNear, isCreative, actionbar, passable, solid, sub,
} from "./util.js";

// =====================================================================
//  ระบบประตู/หน้าต่าง/ไม้กั้น: ผีทุบแล้วร้าวขึ้นเรื่อย ๆ จนระเบิด (ไม่ดรอปของ)
//  - ไม้ (กากสุด) < ทองแดง < เหล็ก (แข็งสุด) ใช้กับทั้งประตูและไม้กั้น
//  - กระจก: โดนทุบ 3 ครั้งแตก
//  - ร้าวแล้วต้องใช้ "ชุดซ่อม" ที่คราฟเฉพาะเท่านั้น (ขุดทิ้งจะแตกสลาย ไม่ได้ของคืน)
// =====================================================================
export const CLASS_NAME = { 0: "กระจก", 1: "ไม้", 2: "ทองแดง/หิน", 3: "เหล็ก" };
const BARRICADE = {
  wood: { cls: 1, hp: 8, fam: "wood" },
  stone: { cls: 2, hp: 14, fam: "stone" },
  iron: { cls: 3, hp: 22, fam: "iron" },
};
export const POWER_DMG = [0, 1.0, 1.5, 2.0];          // แรงต่อการทุบ 1 ครั้งตามพลังผี (1 ธรรมดา, 2 แข็งแรง, 3 โหดมาก)
const STUCK_CAP = 0.74;                                 // ผีที่พลังไม่พอ ทำได้แค่ร้าวถึง 74% (ร้าวหนัก) แต่พังไม่ได้

// ชุดซ่อม -> ใช้กับวัสดุอะไรได้บ้าง
export const KITS = {
  "ghost:repair_wood": { fam: ["wood"], name: "ชุดซ่อมไม้" },
  "ghost:repair_copper": { fam: ["copper"], name: "ชุดซ่อมทองแดง" },
  "ghost:repair_iron": { fam: ["iron"], name: "ชุดซ่อมเหล็ก" },
  "ghost:repair_stone": { fam: ["stone"], name: "ปูนซ่อมหิน" },
  "ghost:repair_glass": { fam: ["glass"], name: "ยาแนวกระจก" },
};
const KIT_FOR = { wood: "ชุดซ่อมไม้", copper: "ชุดซ่อมทองแดง", iron: "ชุดซ่อมเหล็ก", stone: "ปูนซ่อมหิน", glass: "ยาแนวกระจก" };

// ---------------------------------------------------------------------------------- classify
export function classify(b) {
  const full = blockId(b);
  if (!full) return null;
  if (full.startsWith("ghost:barricade_")) {
    const mat = full.slice("ghost:barricade_".length);
    const s = BARRICADE[mat];
    return s ? { kind: "barricade", mat, cls: s.cls, hp: s.hp, fam: s.fam, style: 0 } : null;
  }
  if (!full.startsWith("minecraft:")) return null;
  const id = full.slice(10);
  if (id.endsWith("_door")) {
    if (id.includes("iron")) return { kind: "door", cls: 3, hp: 16, fam: "iron", style: 0 };
    if (id.includes("copper")) return { kind: "door", cls: 2, hp: 10, fam: "copper", style: 0 };
    return { kind: "door", cls: 1, hp: 6, fam: "wood", style: 0 };
  }
  if (id.endsWith("trapdoor")) {
    if (id.includes("iron")) return { kind: "trapdoor", cls: 3, hp: 12, fam: "iron", style: 0 };
    if (id.includes("copper")) return { kind: "trapdoor", cls: 2, hp: 8, fam: "copper", style: 0 };
    return { kind: "trapdoor", cls: 1, hp: 5, fam: "wood", style: 0 };
  }
  if (id.includes("fence_gate")) return { kind: "gate", cls: 1, hp: 5, fam: "wood", style: 0 };
  if (id.includes("glass")) {
    const pane = id.includes("pane");
    return { kind: pane ? "pane" : "glass", cls: 0, hp: id.includes("tinted") ? 4 : 3, fam: "glass", style: 1 };
  }
  return null;
}
export const isUpperDoor = (b) => stateOf(b, "upper_block_bit") === true;
export const isOpen = (b) => stateOf(b, "open_bit") === true;

// ทิศที่ประตู/รั้ว "หัน" -> east/south/west/north (รองรับทั้ง minecraft:cardinal_direction และ direction แบบเก่า)
function doorFacing(b) {
  const c = stateOf(b, "minecraft:cardinal_direction");
  if (typeof c === "string") return c;
  const d = stateOf(b, "direction");
  return ["east", "south", "west", "north"][((d ?? 0) % 4 + 4) % 4];
}
function gateFacing(b) {
  const c = stateOf(b, "minecraft:cardinal_direction");
  if (typeof c === "string") return c;
  const d = stateOf(b, "direction");
  return ["south", "west", "north", "east"][((d ?? 0) % 4 + 4) % 4];
}
function trapSide(b) {          // ด้านที่ฝาเปิดแนบอยู่
  const c = stateOf(b, "minecraft:cardinal_direction");
  if (typeof c === "string") return { north: "south", south: "north", east: "west", west: "east" }[c] || "south";
  const d = stateOf(b, "direction");
  return ["south", "north", "east", "west"][((d ?? 0) % 4 + 4) % 4];
}
function barricadeFacing(b) {
  const c = stateOf(b, "minecraft:cardinal_direction");
  return typeof c === "string" ? c : "north";
}

// ทำให้ตำแหน่งชี้ไปที่ "ครึ่งล่าง" ของประตูเสมอ
export function basePos(dim, b, info) {
  const l = b.location;
  if (info && info.kind === "door" && isUpperDoor(b)) return { x: l.x, y: l.y - 1, z: l.z };
  return { x: l.x, y: l.y, z: l.z };
}

// แกนที่ต้อง "ทะลุ" ผ่าน (x หรือ z)
export function passAxis(dim, pos, info, from) {
  const b = getBlock(dim, pos.x, pos.y, pos.z);
  if (info.kind === "door") { const f = doorFacing(b); return f === "east" || f === "west" ? "x" : "z"; }
  if (info.kind === "gate") { const f = gateFacing(b); return f === "east" || f === "west" ? "x" : "z"; }
  if (info.kind === "barricade") { const f = barricadeFacing(b); return f === "east" || f === "west" ? "x" : "z"; }
  const E = solid(getBlock(dim, pos.x + 1, pos.y, pos.z)), W = solid(getBlock(dim, pos.x - 1, pos.y, pos.z));
  const N = solid(getBlock(dim, pos.x, pos.y, pos.z - 1)), S = solid(getBlock(dim, pos.x, pos.y, pos.z + 1));
  if ((E || W) && !(N && S)) return "z";
  if ((N || S) && !(E && W)) return "x";
  if (from) return Math.abs(from.x - (pos.x + 0.5)) > Math.abs(from.z - (pos.z + 0.5)) ? "x" : "z";
  return "z";
}

// ---------------------------------------------------------------------------------- overlay (รอยร้าว)
const SH = { cube: 0, pane_x: 1, pane_z: 2, door_x: 3, door_z: 4, trap_y: 5, trap_x: 6, trap_z: 7, gate_x: 8, gate_z: 9 };
const T1 = 1.5 / 16, T2 = 14.5 / 16;
export function overlaySpec(dim, pos, info) {
  const b = getBlock(dim, pos.x, pos.y, pos.z);
  if (!b) return null;
  const X = pos.x, Y = pos.y, Z = pos.z;
  switch (info.kind) {
    case "glass": case "generic": return { loc: { x: X + 0.5, y: Y, z: Z + 0.5 }, shape: SH.cube };
    case "barricade": return null;   // ไม้กั้นเปลี่ยนเท็กซ์เจอร์เอง (block state)
    case "pane": {
      const ew = solid(getBlock(dim, X + 1, Y, Z)) || solid(getBlock(dim, X - 1, Y, Z));
      const ns = solid(getBlock(dim, X, Y, Z + 1)) || solid(getBlock(dim, X, Y, Z - 1));
      return { loc: { x: X + 0.5, y: Y, z: Z + 0.5 }, shape: ns && !ew ? SH.pane_z : SH.pane_x };
    }
    case "gate": {
      const f = gateFacing(b);
      return { loc: { x: X + 0.5, y: Y, z: Z + 0.5 }, shape: f === "east" || f === "west" ? SH.gate_z : SH.gate_x };
    }
    case "trapdoor": {
      if (!isOpen(b)) return { loc: { x: X + 0.5, y: Y + (stateOf(b, "upside_down_bit") === true ? 13 / 16 : 0), z: Z + 0.5 }, shape: SH.trap_y };
      const s = trapSide(b);
      if (s === "south") return { loc: { x: X + 0.5, y: Y, z: Z + T2 }, shape: SH.trap_x };
      if (s === "north") return { loc: { x: X + 0.5, y: Y, z: Z + T1 }, shape: SH.trap_x };
      if (s === "east") return { loc: { x: X + T2, y: Y, z: Z + 0.5 }, shape: SH.trap_z };
      return { loc: { x: X + T1, y: Y, z: Z + 0.5 }, shape: SH.trap_z };
    }
    case "door": {
      let f = doorFacing(b);
      // v1.8.3: ตรวจทิศบานประตูจากกำแพงสองข้าง (ประตูที่ฝังในกำแพงจะมีบล็อกตันทั้งสองข้างตามแนวบาน)
      // ถ้าทิศจากค่าสถานะของบล็อกไม่ตรงกับกำแพง (เกมบางเวอร์ชันนับทิศเพี้ยน 90 องศา) ให้หมุนแก้
      {
        const sx = solid(getBlock(dim, X + 1, Y, Z)) && solid(getBlock(dim, X - 1, Y, Z));
        const sz = solid(getBlock(dim, X, Y, Z + 1)) && solid(getBlock(dim, X, Y, Z - 1));
        if (sx !== sz) {
          const ce = { east: "west", south: "north", west: "east", north: "south" }[f];
          const normalIsZ = ce === "north" || ce === "south";   // แผ่นบานตั้งฉากกับแกน Z = วางอยู่ขอบเหนือ/ใต้
          if (normalIsZ !== sx) f = { south: "east", west: "south", north: "west", east: "north" }[f] || f;
        }
      }
      const open = isOpen(b);
      const up = getBlock(dim, X, Y + 1, Z);
      const right = stateOf(up, "door_hinge_bit") === true || stateOf(b, "door_hinge_bit") === true;
      let edge;   // west/east -> แผ่นตั้งตามแกน Z ; north/south -> แผ่นตั้งตามแกน X
      if (!open) edge = { east: "west", south: "north", west: "east", north: "south" }[f];
      else edge = {
        east: right ? "south" : "north", south: right ? "west" : "east",
        west: right ? "north" : "south", north: right ? "east" : "west",
      }[f];
      if (edge === "west") return { loc: { x: X + T1, y: Y, z: Z + 0.5 }, shape: SH.door_z };
      if (edge === "east") return { loc: { x: X + T2, y: Y, z: Z + 0.5 }, shape: SH.door_z };
      if (edge === "north") return { loc: { x: X + 0.5, y: Y, z: Z + T1 }, shape: SH.door_x };
      return { loc: { x: X + 0.5, y: Y, z: Z + T2 }, shape: SH.door_x };
    }
  }
  return null;
}
function findOverlay(dim, key, pos) {
  try {
    for (const e of dim.getEntities({ type: "ghost:crack", location: { x: pos.x + 0.5, y: pos.y + 0.5, z: pos.z + 0.5 }, maxDistance: 2 })) {
      try { if (e.getDynamicProperty("ghost:k") === key) return e; } catch (_) {}
    }
  } catch (_) {}
  return null;
}
function placeOverlay(dim, key, pos, info, stage) {
  const spec = overlaySpec(dim, pos, info);
  if (!spec) return;
  let e = findOverlay(dim, key, pos);
  try {
    if (!e) { e = dim.spawnEntity("ghost:crack", spec.loc); e.setDynamicProperty("ghost:k", key); }
    e.teleport(spec.loc, { rotation: { x: 0, y: 0 } });
    e.setProperty("ghost:shape", spec.shape);
    e.setProperty("ghost:style", info.style || 0);
    e.setProperty("ghost:stage", Math.max(1, Math.min(4, stage)));
  } catch (_) {}
}
function removeOverlay(dim, key, pos) {
  const e = findOverlay(dim, key, pos);
  if (e) { try { e.remove(); } catch (_) {} }
}

// ---------------------------------------------------------------------------------- ข้อมูลรอยร้าว (บันทึกถาวรในโลก)
const DP = "ghost:cracks_v1";
const MAX_ENTRIES = 320;
const cracks = new Map();     // key -> { d: dimId, x, y, z, k: kind, f: fam, h: hits, hp, t: tick, tmp }
let dirty = false, loaded = false;
function load() {
  if (loaded) return;
  let raw;
  try { raw = world.getDynamicProperty(DP); } catch (_) { return; }      // โลกยังไม่พร้อม -> ลองใหม่ทีหลัง
  loaded = true;
  try { if (typeof raw === "string" && raw.length) for (const e of JSON.parse(raw)) if (!cracks.has(keyOf(e.d, e))) cracks.set(keyOf(e.d, e), e); } catch (_) {}
}
function save() {
  if (!dirty) return;
  load();
  if (!loaded) return;
  dirty = false;
  try {
    const arr = [...cracks.values()].filter((e) => !e.tmp).sort((a, b) => b.t - a.t).slice(0, MAX_ENTRIES);
    world.setDynamicProperty(DP, JSON.stringify(arr));
  } catch (_) {}
}
export function crackOf(dim, pos) { load(); return cracks.get(keyOf(dim.id, pos)); }
function stageOf(e) {
  if (e.f === "glass") return e.hp <= 3 ? (e.h >= 2 ? 4 : 2) : Math.max(1, Math.min(4, Math.ceil((4 * e.h) / e.hp)));
  return Math.max(1, Math.min(4, Math.ceil((4 * e.h) / e.hp)));
}
function applyVisual(dim, pos, info, e) {
  const stage = stageOf(e);
  if (info.kind === "barricade") {
    try {
      const b = getBlock(dim, pos.x, pos.y, pos.z);
      const dmg = Math.min(3, stage >= 4 ? 3 : stage);
      b.setPermutation(b.permutation.withState("ghost:dmg", dmg));
    } catch (_) {}
  } else placeOverlay(dim, keyOf(dim.id, pos), pos, info, stage);
  return stage;
}

// ---------------------------------------------------------------------------------- v1.5 ไม้กั้นที่ตอกปิดประตู/กระจก
//  ประตู 1 บาน ตอกได้ 4 แผ่น (แต่ละแผ่นเป็นไม้/หิน/เหล็กก็ได้), กระจก 1 ช่อง = ไม้ไขว้ 1 ชุด
//  ผีต้องพังไม้ทีละแผ่นจากแผ่นนอกสุด ก่อนจะทำอะไรประตู/กระจกได้
export const BOARD_MAT = ["wood", "stone", "iron"];
export const BOARD_ITEM = ["ghost:board_wood", "ghost:board_stone", "ghost:board_iron"];
export const BOARD_HP = [3, 5, 8];          // ความทนต่อแผ่น
export const BOARD_CLS = [1, 2, 3];         // ไม้ < หิน < เหล็ก (พลังผีต้องถึงถึงจะพังได้ เหมือนประตู)
const BDP = "ghost:boards_v1";
const boardMap = new Map();                 // key -> { d, x, y, z, k: 0 ประตู | 1 กระจก, n, m: [], h, ax, sd, ex, ey, ez, t }
let bdirty = false, bloaded = false;
function bload() {
  if (bloaded) return;
  let raw;
  try { raw = world.getDynamicProperty(BDP); } catch (_) { return; }     // โลกยังไม่พร้อม -> ลองใหม่ทีหลัง
  bloaded = true;
  try { if (typeof raw === "string" && raw.length) for (const e of JSON.parse(raw)) if (!boardMap.has(keyOf(e.d, e))) boardMap.set(keyOf(e.d, e), e); } catch (_) {}
}
function bsave() {
  if (!bdirty) return;
  bload();
  if (!bloaded) return;
  bdirty = false;
  try { world.setDynamicProperty(BDP, JSON.stringify([...boardMap.values()].sort((a, b) => (b.t || 0) - (a.t || 0)).slice(0, 600))); } catch (_) {}
}
export function boardsAt(dim, pos) { bload(); return boardMap.get(keyOf(dim.id, pos)); }
export function clearAllBoards() { bload(); boardMap.clear(); bdirty = true; }       // ใช้ในการทดสอบ
export function boardStage(r) {
  if (!r || !r.n || !r.h) return 0;
  const hp = BOARD_HP[r.m[r.n - 1] || 0];
  return Math.max(1, Math.min(3, Math.ceil((3 * r.h) / hp)));
}
// จำนวนครั้งที่ผีต้องทุบไม้ทั้งหมด + มีแผ่นที่แข็งเกินพลังไหม
export function boardsHits(r, power, rage) {
  const out = { hits: 0, strong: false };
  if (!r || !r.n) return out;
  const dmg = POWER_DMG[Math.max(1, Math.min(3, power))] * (rage ? 1.5 : 1);
  for (let i = 0; i < r.n; i++) {
    const mi = r.m[i] || 0;
    let hp = BOARD_HP[mi];
    if (i === r.n - 1) hp = Math.max(0.5, hp - (r.h || 0));
    out.hits += Math.ceil(hp / dmg - 1e-6);
    if (BOARD_CLS[mi] > power) out.strong = true;
  }
  return out;
}
function findBoardEnt(dim, key, r) {
  try {
    for (const e of dim.getEntities({ type: "ghost:boards", location: { x: r.ex, y: r.ey + 1, z: r.ez }, maxDistance: 2.5 })) {
      try { if (e.getDynamicProperty("ghost:k") === key) return e; } catch (_) {}
    }
  } catch (_) {}
  return null;
}
// สร้าง/อัปเดตตัวโชว์ไม้กั้น (fx: 1 = กำลังตอก มีค้อน, 2 = กำลังตอก ไม่มีค้อน, 0 = เสร็จแล้ว)
export function syncBoards(dim, r, fx) {
  const key = keyOf(dim.id, r);
  let e = findBoardEnt(dim, key, r);
  try {
    if (!e) { e = dim.spawnEntity("ghost:boards", { x: r.ex, y: r.ey, z: r.ez }); e.setDynamicProperty("ghost:k", key); }
    e.teleport({ x: r.ex, y: r.ey, z: r.ez }, { rotation: { x: 0, y: 0 } });
    e.setProperty("ghost:kind", r.k); e.setProperty("ghost:axis", r.ax); e.setProperty("ghost:side", r.sd);
    e.setProperty("ghost:n", Math.max(0, Math.min(4, r.n)));
    for (let i = 0; i < 4; i++) e.setProperty(`ghost:m${i + 1}`, r.m[i] || 0);
    e.setProperty("ghost:dmg", boardStage(r));
    if (fx !== undefined) e.setProperty("ghost:fx", fx);
  } catch (_) {}
  return e;
}
export function putBoards(dim, r, fx) {
  bload();
  r.t = system.currentTick;
  boardMap.set(keyOf(dim.id, r), r); bdirty = true;
  return syncBoards(dim, r, fx);
}
export function dropBoards(dim, r, burst) {
  bload();
  const key = keyOf(dim.id, r);
  boardMap.delete(key); bdirty = true;
  const e = findBoardEnt(dim, key, r);
  if (e) { try { e.remove(); } catch (_) {} }
  if (burst) {
    const c = { x: r.ex, y: r.ey + (r.k === 0 ? 1 : 0.5), z: r.ez };
    particle(dim, "ghost:splinter", c); particle(dim, "ghost:woodchunk", c);
    sound(dim, "ghost.board.snap", c, 1.6, rand(0.9, 1.1), 40);
  }
}
// ตำแหน่งแผ่นนอกสุด (ไว้ปล่อยเศษไม้/เสียง)
function boardLoc(r) {
  return { x: r.ex, y: r.ey + (r.k === 0 ? (7 * Math.max(1, r.n) - 1) / 16 : 0.5), z: r.ez };
}
function hitBoard(dim, pos, info, r, power, enraged, dir) {
  const mi = r.m[r.n - 1] || 0, hp = BOARD_HP[mi], canBreak = BOARD_CLS[mi] <= power;
  const dmg = POWER_DMG[Math.max(1, Math.min(3, power))] * (enraged ? 1.5 : 1);
  const before = r.h || 0;
  let h = before + dmg;
  if (!canBreak) h = Math.min(before + dmg * 1.6, hp * STUCK_CAP);
  const c = boardLoc(r), v = dirVars(dir ? dir.x : 0, dir ? dir.z : 0);
  if (canBreak && h >= hp - 1e-6) {
    r.n--; r.h = 0; r.m.length = Math.max(0, r.n); bdirty = true;
    sound(dim, mi === 2 ? "ghost.bang.metal" : "ghost.board.snap", c, 2.2, rand(0.85, 1.05), 56);
    particle(dim, mi === 0 ? "ghost:splinter" : mi === 1 ? "ghost:debris" : "ghost:spark", c, v);
    particle(dim, mi === 0 ? "ghost:woodchunk" : "ghost:debris", c, v);
    particle(dim, "ghost:dust", c, v);
    shake(dim, c, 14, 0.5, 0.4);
    if (r.n <= 0) dropBoards(dim, r, false); else syncBoards(dim, r);
    return { res: "crack", info, board: true, stage: 4 };
  }
  r.h = h; bdirty = true;
  syncBoards(dim, r);
  sound(dim, mi === 2 ? "ghost.bang.metal" : mi === 1 ? "ghost.bang.stone" : "ghost.bang.wood", c, 2.3, rand(0.8, 1.0), 56);
  particle(dim, mi === 0 ? "ghost:splinter" : "ghost:dust", c, v);
  shake(dim, c, 14, power >= 3 || enraged ? 0.5 : 0.3, 0.35);
  return { res: !canBreak && before >= hp * STUCK_CAP - 1e-6 ? "blocked" : "crack", info, board: true, stage: boardStage(r) };
}

// ---------------------------------------------------------------------------------- เอฟเฟกต์
function hitFx(dim, pos, info, dir, stage, heavy) {
  const c = center(pos, info.kind === "door" ? 1.0 : 0.5);
  const v = dirVars(dir ? dir.x : 0, dir ? dir.z : 0);
  if (info.fam === "glass") {
    sound(dim, "ghost.glass.crack", c, 1.4, rand(0.9, 1.15), 40);
    sound(dim, "ghost.knock.glass", c, 1.2, rand(0.7, 0.85), 30);
    particle(dim, "ghost:shard", c, v);
  } else if (info.fam === "iron" || info.fam === "copper") {
    sound(dim, "ghost.bang.metal", c, 2.4, info.fam === "iron" ? rand(0.7, 0.85) : rand(0.95, 1.1), 64);
    particle(dim, "ghost:spark", c, v);
    particle(dim, "ghost:dust", c, v);
  } else if (info.fam === "stone") {
    sound(dim, "ghost.bang.stone", c, 2.4, rand(0.8, 1.0), 56);
    particle(dim, "ghost:dust", c, v);
    particle(dim, "ghost:debris", c, v);
  } else {
    sound(dim, "ghost.bang.wood", c, 2.4, rand(0.85, 1.05), 56);
    particle(dim, "ghost:splinter", c, v);
    if (stage >= 3) particle(dim, "ghost:woodchunk", c, v);
    particle(dim, "ghost:dust", c, v);
  }
  if (stage >= 3 && Math.random() < 0.5) sound(dim, "random.anvil_land", c, 0.35, rand(0.5, 0.6), 24);
  shake(dim, c, 14, heavy ? 0.55 : 0.32, 0.35);
}
function burstFx(dim, pos, info, dir) {
  const c = center(pos, info.kind === "door" ? 1.0 : 0.5);
  const v = dirVars(dir ? dir.x : 0, dir ? dir.z : 0);
  particle(dim, "minecraft:huge_explosion_emitter", c);
  particle(dim, "minecraft:large_explosion", c);
  if (info.fam === "glass") {
    sound(dim, "ghost.glass.shatter", c, 2.2, rand(0.9, 1.1), 64);
    sound(dim, "random.glass", c, 1.5, 0.8, 48);
    for (let i = 0; i < 3; i++) particle(dim, "ghost:shard", { x: c.x, y: c.y + (i - 1) * 0.3, z: c.z }, v);
  } else {
    sound(dim, "ghost.burst", c, 2.6, rand(0.9, 1.05), 80);
    sound(dim, "random.explode", c, 1.6, rand(0.95, 1.15), 80);
    const chunk = info.fam === "wood" ? "ghost:woodchunk" : "ghost:debris";
    for (let i = 0; i < (info.kind === "door" ? 3 : 2); i++) {
      const p = { x: c.x, y: c.y + (i - 1) * 0.6, z: c.z };
      particle(dim, info.fam === "wood" ? "ghost:splinter" : info.fam === "stone" ? "ghost:debris" : "ghost:spark", p, v);
      particle(dim, chunk, p, v);
      particle(dim, "ghost:dust", p, v);
    }
  }
  shake(dim, c, 18, 0.9, 0.7);
  for (const p of playersNear(dim, c, 4.5)) {
    try {
      const dx = p.location.x - c.x, dz = p.location.z - c.z, l = Math.hypot(dx, dz) || 1;
      p.applyKnockback(dx / l, dz / l, 1.1, 0.32);
      p.runCommand("camera @s fade time 0.02 0.1 0.35 color 60 0 0");
    } catch (_) {}
  }
}
// เก็บของที่อาจหลุดออกมา (ห้ามดรอป)
function sweepDrops(dim, pos, match) {
  const run = () => {
    try {
      for (const it of dim.getEntities({ type: "minecraft:item", location: { x: pos.x + 0.5, y: pos.y + 1, z: pos.z + 0.5 }, maxDistance: 2.6 })) {
        try { const st = it.getComponent("minecraft:item")?.itemStack; if (st && match(st.typeId)) it.remove(); } catch (_) {}
      }
    } catch (_) {}
  };
  run(); system.runTimeout(run, 1); system.runTimeout(run, 3); system.runTimeout(run, 6);
}
function dropMatcher(info) {
  if (info.kind === "door") return (id) => id.endsWith("_door");
  if (info.kind === "trapdoor") return (id) => id.endsWith("trapdoor");
  if (info.kind === "gate") return (id) => id.includes("fence_gate");
  if (info.kind === "barricade") return (id) => id.startsWith("ghost:barricade");
  return (id) => id.includes("glass");
}
function removeNoDrop(dim, pos, info) {
  const cells = info.kind === "door" ? [{ x: pos.x, y: pos.y + 1, z: pos.z }, pos] : [pos];
  for (const c of cells) setAir(dim, c);
  sweepDrops(dim, pos, dropMatcher(info));
}

// ---------------------------------------------------------------------------------- API ให้ผีใช้
// คืน "broken" | "crack" | "blocked" | "gone"
export function hitOpening(dim, pos, power, enraged, dir) {
  load();
  const b = getBlock(dim, pos.x, pos.y, pos.z);
  const info = classify(b);
  if (!info) return { res: "gone" };
  const br = boardsAt(dim, pos);
  if (br && br.n > 0) return hitBoard(dim, pos, info, br, power, enraged, dir);
  const key = keyOf(dim.id, pos);
  let e = cracks.get(key);
  if (!e || e.k !== info.kind || e.f !== info.fam) e = { d: dim.id, x: fl(pos.x), y: fl(pos.y), z: fl(pos.z), k: info.kind, f: info.fam, h: 0, hp: info.hp, t: 0 };
  const canBreak = info.cls <= power;
  const dmg = info.fam === "glass" ? 1 : POWER_DMG[Math.max(1, Math.min(3, power))] * (enraged ? 1.5 : 1);
  const before = e.h;
  let h = e.h + dmg;
  if (!canBreak) h = Math.min(e.h + dmg * 1.6, e.hp * STUCK_CAP);   // พลังไม่พอ: ร้าวเร็วแต่ไปไม่ถึงระเบิด
  e.h = h; e.t = system.currentTick;
  if (canBreak && h >= e.hp - 1e-6) {
    cracks.delete(key); dirty = true;
    removeOverlay(dim, key, pos);
    burstFx(dim, pos, info, dir);
    removeNoDrop(dim, pos, info);
    return { res: "broken", info };
  }
  cracks.set(key, e); dirty = true;
  const stage = applyVisual(dim, pos, info, e);
  hitFx(dim, pos, info, dir, stage, power >= 3 || enraged);
  return { res: !canBreak && before >= e.hp * STUCK_CAP - 1e-6 ? "blocked" : "crack", info, stage };
}

// รอยร้าวชั่วคราวบนบล็อกทั่วไป (ตอนผีตัวติดแล้วทุบผนังออก) — หายเองใน 5 นาที
export function crackGeneric(dim, pos, frac) {
  load();
  const key = keyOf(dim.id, pos);
  const e = { d: dim.id, x: fl(pos.x), y: fl(pos.y), z: fl(pos.z), k: "generic", f: "generic", h: frac * 4, hp: 4, t: system.currentTick, tmp: true };
  cracks.set(key, e);
  placeOverlay(dim, key, pos, { kind: "generic", style: 0 }, stageOf(e));
}
export function clearCrack(dim, pos) {
  const key = keyOf(dim.id, pos);
  if (cracks.delete(key)) dirty = true;
  removeOverlay(dim, key, pos);
}

// ---------------------------------------------------------------------------------- ซ่อม
const lastRepair = new Map();
function useKit(p, kitId) {
  if (isCreative(p)) return;
  try {
    const eq = p.getComponent("minecraft:equippable");
    const it = eq.getEquipment("Mainhand");
    if (!it || it.typeId !== kitId) return;
    if (it.amount > 1) { it.amount -= 1; eq.setEquipment("Mainhand", it); } else eq.setEquipment("Mainhand", undefined);
  } catch (_) {
    try { p.runCommand(`clear @s ${kitId} 0 1`); } catch (__) { try { p.runCommand(`clear @s ${kitId} -1 1`); } catch (___) {} }
  }
}
function doRepair(p, dim, loc, kitId) {
  load();
  const b = getBlock(dim, loc.x, loc.y, loc.z);
  const info = classify(b);
  if (!info) return;
  const pos = basePos(dim, b, info);
  const key = keyOf(dim.id, pos);
  const e = cracks.get(key);
  if (!e) { actionbar(p, "§7ยังไม่มีรอยร้าว ไม่ต้องซ่อม"); return; }
  const kit = KITS[kitId];
  if (!kit.fam.includes(info.fam)) { actionbar(p, `§cใช้ไม่ได้ ต้องใช้ §e${KIT_FOR[info.fam] || "ชุดซ่อมที่ถูกชนิด"}`); return; }
  cracks.delete(key); dirty = true;
  removeOverlay(dim, key, pos);
  if (info.kind === "barricade") { try { b.setPermutation(b.permutation.withState("ghost:dmg", 0)); } catch (_) {} }
  useKit(p, kitId);
  const c = center(pos, info.kind === "door" ? 1.0 : 0.5);
  sound(dim, "ghost.repair", c, 1.2, rand(0.95, 1.1), 24);
  if (info.fam === "glass") sound(dim, "random.glass", c, 0.4, 1.6, 16);
  particle(dim, info.fam === "wood" ? "ghost:woodchunk" : "ghost:dust", c);
  actionbar(p, isEn() ? `§aRepaired the ${info.kind === "barricade" ? "barricade" : info.fam === "glass" ? "glass" : "door"}` : `§aซ่อม${info.kind === "barricade" ? "ไม้กั้น" : info.fam === "glass" ? "กระจก" : "ประตู"}เรียบร้อย`);
}
function onKitUse(player, block, itemStack, cancel) {
  if (!itemStack || !KITS[itemStack.typeId] || !block) return false;
  const info = classify(block);
  if (!info) return false;
  cancel();
  const t = system.currentTick;
  if (t - (lastRepair.get(player.id) ?? -99) < 8) return true;
  lastRepair.set(player.id, t);
  const dim = block.dimension, loc = block.location, kitId = itemStack.typeId;
  system.run(() => { try { doRepair(player, dim, loc, kitId); } catch (_) {} });
  return true;
}

// ---------------------------------------------------------------------------------- ขุดของที่ร้าว = แตกสลาย
function crumble(p, dim, pos, info) {
  const key = keyOf(dim.id, pos);
  cracks.delete(key); dirty = true;
  removeOverlay(dim, key, pos);
  removeNoDrop(dim, pos, info);
  const c = center(pos, info.kind === "door" ? 1.0 : 0.5);
  particle(dim, info.fam === "wood" ? "ghost:woodchunk" : "ghost:debris", c);
  particle(dim, "ghost:dust", c);
  sound(dim, info.fam === "wood" ? "ghost.bang.wood" : info.fam === "stone" ? "ghost.bang.stone" : "ghost.bang.metal", c, 0.6, 1.3, 20);
  actionbar(p, "§7มันร้าวเกินไป... แตกสลายไม่เหลือซาก §8(ต้องใช้ชุดซ่อมเท่านั้น)");
}

// ---------------------------------------------------------------------------------- ตรวจสอบเป็นระยะ
function maintain() {
  load();
  bload();
  if (!loaded || !bloaded) return;          // ข้อมูลที่บันทึกไว้ยังโหลดไม่ได้ -> อย่าเพิ่งลบอะไร
  const t = system.currentTick;
  const dims = {};
  for (const id of ["overworld", "nether", "the_end"]) { try { const d = world.getDimension(id); dims[d.id] = d; } catch (_) {} }
  for (const [key, e] of cracks) {
    const dim = dims[e.d];
    if (!dim) continue;
    if (e.tmp && t - e.t > 6000) { cracks.delete(key); removeOverlay(dim, key, e); continue; }
    let near = false;
    try { near = dim.getPlayers({ location: { x: e.x, y: e.y, z: e.z }, maxDistance: 56 }).length > 0; } catch (_) {}
    if (!near) continue;
    const b = getBlock(dim, e.x, e.y, e.z);
    if (!b) continue;
    if (e.tmp) {
      if (b.isAir || passable(b)) { cracks.delete(key); removeOverlay(dim, key, e); }
      continue;
    }
    const info = classify(b);
    if (!info || info.kind !== e.k || info.fam !== e.f || (info.kind === "door" && isUpperDoor(b))) {
      cracks.delete(key); dirty = true; removeOverlay(dim, key, e); continue;
    }
    if (info.kind === "barricade") {
      const want = Math.min(3, stageOf(e));
      if (stateOf(b, "ghost:dmg") !== want) { try { b.setPermutation(b.permutation.withState("ghost:dmg", want)); } catch (_) {} }
      continue;
    }
    const spec = overlaySpec(dim, e, info);
    const ov = findOverlay(dim, key, e);
    if (!ov) { placeOverlay(dim, key, e, info, stageOf(e)); continue; }
    try {
      const l = ov.location;
      if (Math.abs(l.x - spec.loc.x) > 0.02 || Math.abs(l.z - spec.loc.z) > 0.02 || Math.abs(l.y - spec.loc.y) > 0.02 || ov.getProperty("ghost:shape") !== spec.shape) {
        ov.teleport(spec.loc, { rotation: { x: 0, y: 0 } });
        ov.setProperty("ghost:shape", spec.shape);
      }
    } catch (_) {}
  }
  // ไม้กั้นที่ตอกไว้: ประตู/กระจกหายไป = ไม้หายด้วย, ประตูถูกเปิดด้วยวงจร = ไม้หลุดกระเด็น, ตัวโชว์หาย = สร้างใหม่
  bload();
  for (const [key, r] of boardMap) {
    const dim = dims[r.d];
    if (!dim) continue;
    let near = false;
    try { near = dim.getPlayers({ location: { x: r.x, y: r.y, z: r.z }, maxDistance: 56 }).length > 0; } catch (_) {}
    if (!near) continue;
    const b = getBlock(dim, r.x, r.y, r.z);
    if (!b) continue;
    const info = classify(b);
    const ok = info && (r.k === 0 ? info.kind === "door" && !isUpperDoor(b) : info.fam === "glass");
    if (!ok || !r.n) { dropBoards(dim, r, false); continue; }
    if (r.k === 0 && isOpen(b)) { dropBoards(dim, r, true); continue; }
    const ent = findBoardEnt(dim, key, r);
    if (!ent) syncBoards(dim, r, 0);
    else { try { if (ent.getProperty("ghost:n") !== r.n || ent.getProperty("ghost:dmg") !== boardStage(r)) syncBoards(dim, r); } catch (_) {} }
  }
  for (const dim of Object.values(dims)) {
    try {
      for (const e of dim.getEntities({ type: "ghost:boards" })) {
        let k; try { k = e.getDynamicProperty("ghost:k"); } catch (_) {}
        if (!k || !boardMap.has(k)) { try { e.remove(); } catch (_) {} }
      }
    } catch (_) {}
  }
  bsave();
  // รอยร้าวที่ไม่มีเจ้าของ (ข้อมูลหาย) -> ลบทิ้ง
  for (const dim of Object.values(dims)) {
    try {
      for (const ov of dim.getEntities({ type: "ghost:crack" })) {
        let k; try { k = ov.getDynamicProperty("ghost:k"); } catch (_) {}
        if (!k || !cracks.has(k)) { try { ov.remove(); } catch (_) {} }
      }
    } catch (_) {}
  }
  save();
}
function refreshSoon(dim, b) {
  const info = classify(b);
  if (!info || info.kind === "barricade") return;
  const pos = basePos(dim, b, info);
  const key = keyOf(dim.id, pos);
  if (!cracks.has(key)) return;
  system.runTimeout(() => { try { const e = cracks.get(key); if (e) placeOverlay(dim, key, pos, info, stageOf(e)); } catch (_) {} }, 2);
}

// ---------------------------------------------------------------------------------- init
export function initBreach() {
  load();
  // ใช้ชุดซ่อมกับบล็อก (ยกเลิกการเปิดประตูตอนถือชุดซ่อม)
  const okInteract = sub("beforeEvents", "playerInteractWithBlock", (ev) => {
    try { onKitUse(ev.player, ev.block, ev.itemStack, () => { ev.cancel = true; }); } catch (_) {}
  });
  sub("beforeEvents", "itemUseOn", (ev) => {
    try { if (!okInteract) onKitUse(ev.source, ev.block, ev.itemStack, () => { ev.cancel = true; }); else if (ev.itemStack && KITS[ev.itemStack.typeId] && classify(ev.block)) ev.cancel = true; } catch (_) {}
  });
  // ขุดของที่ร้าว -> แตกสลาย ไม่ดรอป
  sub("beforeEvents", "playerBreakBlock", (ev) => {
    try {
      const b = ev.block, info = classify(b);
      if (!info) return;
      const dim = b.dimension, pos = basePos(dim, b, info), key = keyOf(dim.id, pos);
      if (!cracks.has(key)) return;
      const p = ev.player;
      if (info.fam === "glass") { system.run(() => clearCrack(dim, pos)); return; }
      ev.cancel = true;
      system.run(() => { try { crumble(p, dim, pos, info); } catch (_) {} });
    } catch (_) {}
  });
  sub("afterEvents", "playerInteractWithBlock", (ev) => { try { refreshSoon(ev.block.dimension, ev.block); } catch (_) {} });
  // วางบล็อกใหม่ตรงที่เคยร้าว = ของใหม่ ไม่ร้าว
  sub("afterEvents", "playerPlaceBlock", (ev) => {
    try {
      const b = ev.block, dim = b.dimension, l = b.location;
      for (const dy of [0, -1]) {
        const k = keyOf(dim.id, { x: l.x, y: l.y + dy, z: l.z });
        const e = cracks.get(k);
        if (e && (dy === 0 || e.k === "door")) { cracks.delete(k); dirty = true; removeOverlay(dim, k, e); }
      }
    } catch (_) {}
  });
  system.runInterval(() => { try { maintain(); } catch (_) {} }, 40);
  system.runInterval(() => { try { save(); bsave(); } catch (_) {} }, 100);
}

// v1.8.3: คำสั่งดีบั๊ก /scriptevent ghost:doordebug  (มองที่ประตูแล้วพิมพ์) -> บอกค่าสถานะและทิศที่คำนวณได้
export function doorDebug(p) {
  try {
    const h = p.getBlockFromViewDirection({ maxDistance: 8 });
    const b = h && h.block;
    if (!b) return "look at a door first";
    const info = classify(b);
    let st = "";
    try { st = JSON.stringify(b.permutation.getAllStates()); } catch (_) {}
    const base = info ? basePos(b.dimension, b, info) : null;
    const spec = base && info ? overlaySpec(b.dimension, base, info) : null;
    return `${b.typeId} ${st} face=${h.face} hit=${JSON.stringify(h.faceLocation)} kind=${info ? info.kind : "-"} spec=${spec ? JSON.stringify(spec) : "none"}`;
  } catch (e) { return "doordebug error: " + e; }
}
