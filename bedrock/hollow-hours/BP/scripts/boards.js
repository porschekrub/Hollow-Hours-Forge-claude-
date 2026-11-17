import { isEn } from "./lang.js";
import { world, system, ItemStack } from "@minecraft/server";
import { fl, rand, valid, dist, getBlock, center, sound, particle, isCreative, actionbar, sub } from "./util.js";
import { classify, basePos, isOpen, overlaySpec, boardsAt, putBoards, dropBoards, syncBoards, BOARD_ITEM } from "./breach.js";

// =====================================================================
//  v1.5 ไม้กั้นแบบตอก
//  ถือไม้กั้นแล้วคลิกขวาที่ประตู -> ตัวละครเอาค้อนตอกน็อต แผ่นไม้ติดบนประตู (ประตู 1 บาน ได้ 4 แผ่น)
//  คลิกขวาที่กระจก -> ไม้กั้น 1 อัน ตอกปิดกระจกที่ติดกันได้ 4 บาน
//  ประตูที่ตอกแล้วเปิดไม่ได้ / ย่อ + คลิกขวามือเปล่า = งัดไม้ออกทีละแผ่น
// =====================================================================
const ITEM_MAT = { "ghost:board_wood": 0, "ghost:board_stone": 1, "ghost:board_iron": 2 };
const OLD_MAT = { "ghost:barricade_wood": 0, "ghost:barricade_stone": 1, "ghost:barricade_iron": 2 };   // ไม้กั้นแบบวาง (รุ่นเก่า) ก็เอามาตอกได้
const MAT_NAME = ["ไม้", "หิน", "เหล็ก"];
const MAT_EN = ["wood", "stone", "iron"];
const HAMMER_PITCH = [1.0, 0.86, 1.22];
const STRIKES = [9, 16, 23];          // ตรงกับจังหวะค้อนในแอนิเมชัน (0.45 / 0.80 / 1.15 วินาที)
const SEQ = 32;                       // ตอกเสร็จใน 1.6 วินาที
const PANEL_HALF = 1.5 / 16;
const sessions = new Map();           // player id -> การตอกที่กำลังทำ
const cool = new Map();
const hintAt = new Map();

const matOf = (it) => (!it ? -1 : it.typeId in ITEM_MAT ? ITEM_MAT[it.typeId] : it.typeId in OLD_MAT ? OLD_MAT[it.typeId] : -1);
const isBoardItem = (it) => !!it && it.typeId in ITEM_MAT;
const boardable = (info) => !!info && (info.kind === "door" || info.kind === "glass" || info.kind === "pane");
function busy(p) {
  const t = system.currentTick;
  if (sessions.has(p.id) || t - (cool.get(p.id) ?? -99) < 8) return true;
  cool.set(p.id, t);
  return false;
}
// ข้อความแนะนำ (เลื่อนไปทำนอก before-event เพราะตอนนั้นโลกเป็น read-only)
function hint(p, text) {
  const t = system.currentTick;
  if (t - (hintAt.get(p.id) ?? -99) < 30) return;
  hintAt.set(p.id, t);
  system.run(() => { try { if (valid(p)) actionbar(p, text); } catch (_) {} });
}

// ---------------------------------------------------------------------------------- ตำแหน่งแผ่นไม้ (ฝั่งที่ผู้เล่นยืน)
function doorPlane(dim, pos, info, P) {
  const spec = overlaySpec(dim, pos, info);          // ตำแหน่งบานประตูตอนปิด
  if (!spec) return null;
  const ax = spec.shape === 3 ? 0 : 1;               // door_x = บานขวางแกน z, door_z = บานขวางแกน x
  const c = ax === 0 ? spec.loc.z : spec.loc.x;
  const sd = (ax === 0 ? P.z : P.x) >= c ? 1 : 0;
  const off = (sd ? 1 : -1) * (PANEL_HALF + 0.07);
  return ax === 0 ? { ax, sd, ex: spec.loc.x, ey: pos.y, ez: c + off } : { ax, sd, ex: c + off, ey: pos.y, ez: spec.loc.z };
}
function glassPlane(pos, info, P, ax) {
  const cx = pos.x + 0.5, cz = pos.z + 0.5;
  const c = ax === 0 ? cz : cx;
  const sd = (ax === 0 ? P.z : P.x) >= c ? 1 : 0;
  const off = (sd ? 1 : -1) * (info.kind === "pane" ? 1 / 16 + 0.06 : 0.5 + 0.035);
  return ax === 0 ? { ax, sd, ex: cx, ey: pos.y, ez: cz + off } : { ax, sd, ex: cx + off, ey: pos.y, ez: cz };
}
// กระจกที่ติดกัน (ระนาบเดียวกัน) ไม่เกิน 4 ช่อง เริ่มจากช่องที่คลิก
function glassGroup(dim, start, info, P) {
  let ax;
  if (info.kind === "pane") { const sp = overlaySpec(dim, start, info); ax = sp && sp.shape === 2 ? 1 : 0; }
  else ax = Math.abs(P.x - (start.x + 0.5)) > Math.abs(P.z - (start.z + 0.5)) ? 1 : 0;
  const nb = ax === 0 ? [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0]] : [[0, 0, 1], [0, 0, -1], [0, 1, 0], [0, -1, 0]];
  const k = (c) => `${c.x},${c.y},${c.z}`;
  const out = [], seen = new Set([k(start)]), q = [start];
  for (let i = 0; i < q.length && out.length < 4; i++) {
    const c = q[i];
    const inf = classify(getBlock(dim, c.x, c.y, c.z));
    if (!inf || inf.kind !== info.kind) continue;
    if (!boardsAt(dim, c)) out.push(c);
    for (const [dx, dy, dz] of nb) {
      const n = { x: c.x + dx, y: c.y + dy, z: c.z + dz };
      if (seen.has(k(n)) || Math.abs(n.x - start.x) + Math.abs(n.y - start.y) + Math.abs(n.z - start.z) > 3) continue;
      seen.add(k(n)); q.push(n);
    }
  }
  return { ax, cells: out };
}
function nailLoc(r) { return { x: r.ex, y: r.ey + (r.k === 0 ? (7 * Math.max(1, r.n) - 1) / 16 : 0.5), z: r.ez }; }

// ---------------------------------------------------------------------------------- ของในมือ
function consumeOne(p, typeId) {
  if (isCreative(p)) return true;
  try {
    const eq = p.getComponent("minecraft:equippable");
    const it = eq.getEquipment("Mainhand");
    if (!it || it.typeId !== typeId) return false;
    if (it.amount > 1) { it.amount -= 1; eq.setEquipment("Mainhand", it); } else eq.setEquipment("Mainhand", undefined);
    return true;
  } catch (_) {
    try { p.runCommand(`clear @s ${typeId} 0 1`); return true; } catch (__) {}
    try { p.runCommand(`clear @s ${typeId} -1 1`); return true; } catch (__) {}
    return false;
  }
}
function give(p, id) {
  try {
    const inv = p.getComponent("minecraft:inventory").container;
    const left = inv.addItem(new ItemStack(id, 1));
    if (left) p.dimension.spawnItem(left, p.location);
  } catch (_) { try { p.runCommand(`give @s ${id} 1`); } catch (__) {} }
}

// ---------------------------------------------------------------------------------- ตอก
function startNailing(p, block, mat, itemId) {
  if (!valid(p) || sessions.has(p.id)) return;
  const dim = block.dimension, info = classify(block);
  if (!boardable(info)) { hint(p, "§7ไม้กั้นใช้ตอกปิดประตูหรือกระจกเท่านั้น"); return; }
  const P = p.location;
  if (dist(P, center(block.location)) > 4.6) { hint(p, "§7เข้าไปใกล้อีกหน่อย"); return; }
  const recs = [];
  let kind;
  if (info.kind === "door") {
    if (isOpen(block)) { hint(p, "§7ปิดประตูก่อน แล้วค่อยตอกไม้กั้น"); return; }
    const pos = basePos(dim, block, info);
    let r = boardsAt(dim, pos);
    if (r && r.n >= 4) { hint(p, "§7ประตูบานนี้ตอกไม้กั้นครบ 4 แผ่นแล้ว"); return; }
    if (!r) {
      const pl = doorPlane(dim, pos, info, P);
      if (!pl) return;
      r = Object.assign({ d: dim.id, x: pos.x, y: pos.y, z: pos.z, k: 0, n: 0, m: [], h: 0 }, pl);
    }
    r.m[r.n] = mat; r.n++; r.h = 0;
    recs.push(r); kind = 0;
  } else {
    const g = glassGroup(dim, block.location, info, P);
    if (!g.cells.length) { hint(p, "§7กระจกตรงนี้ตอกไม้ไว้แล้ว"); return; }
    for (const c of g.cells) {
      const inf = classify(getBlock(dim, c.x, c.y, c.z));
      recs.push(Object.assign({ d: dim.id, x: c.x, y: c.y, z: c.z, k: 1, n: 1, m: [mat], h: 0 }, glassPlane(c, inf, P, g.ax)));
    }
    kind = 1;
  }
  if (!consumeOne(p, itemId)) return;
  recs.forEach((r, i) => putBoards(dim, r, i === 0 ? 1 : 2));
  sound(dim, "ghost.board.place", nailLoc(recs[0]), 1.0, rand(0.9, 1.1), 16);
  try { p.playAnimation("animation.ghost.player_hammer", { blendOutTime: 0.15, stopExpression: "query.any_animation_finished", controller: "ghost_hammer" }); }
  catch (_) { try { p.runCommand(`playanimation @s animation.ghost.player_hammer none 0.15 "query.any_animation_finished" ghost_hammer`); } catch (__) {} }
  try { p.addEffect("slowness", SEQ, { amplifier: 3, showParticles: false }); } catch (_) {}
  sessions.set(p.id, { p, dim, recs, mat, kind, t0: system.currentTick });
}
function finishNailing(S) {
  for (const r of S.recs) { const cur = boardsAt(S.dim, r); if (cur) syncBoards(S.dim, cur, 0); }
  if (!valid(S.p)) return;
  if (S.kind === 0) { const cur = boardsAt(S.dim, S.recs[0]); actionbar(S.p, isEn() ? `§aNailed ${MAT_EN[S.mat]} boards §7(${cur ? cur.n : 0}/4)` : `§aตอกไม้กั้น${MAT_NAME[S.mat]}แล้ว §7(${cur ? cur.n : 0}/4 แผ่น)`); }
  else actionbar(S.p, isEn() ? `§aBoarded up ${S.recs.length} window pane${S.recs.length === 1 ? "" : "s"}` : `§aตอกไม้ปิดกระจกแล้ว ${S.recs.length} บาน`);
}
function sessionTick(now) {
  for (const [id, S] of sessions) {
    const t = now - S.t0;
    if (STRIKES.includes(t)) {
      const loc = nailLoc(S.recs[0]);
      sound(S.dim, "ghost.hammer", loc, 1.3, HAMMER_PITCH[S.mat] * rand(0.95, 1.05), 24);
      particle(S.dim, "ghost:dust", loc);
    }
    if (t >= SEQ || !valid(S.p)) { sessions.delete(id); try { finishNailing(S); } catch (_) {} }
  }
}

// ---------------------------------------------------------------------------------- งัดออก
function pry(p, dim, r) {
  const loc = nailLoc(r);
  if (r.k === 0) {
    const mi = r.m[r.n - 1] || 0;
    r.n--; r.h = 0; r.m.length = Math.max(0, r.n);
    if (!isCreative(p)) give(p, BOARD_ITEM[mi]);
    if (r.n <= 0) dropBoards(dim, r, false); else putBoards(dim, r, 0);
    actionbar(p, r.n > 0 ? `§7งัดไม้กั้นออก 1 แผ่น §8(เหลือ ${r.n})` : "§7งัดไม้กั้นออกหมดแล้ว");
  } else {
    dropBoards(dim, r, false);
    actionbar(p, "§7งัดไม้ที่กระจกออกแล้ว §8(ไม้หัก)");
  }
  sound(dim, "ghost.board.pry", loc, 1.0, rand(0.9, 1.1), 16);
  particle(dim, "ghost:splinter", loc);
}

// ---------------------------------------------------------------------------------- events
// first = false เมื่อเป็นอีเวนต์ซ้ำจากการกดคลิกขวาค้าง (ไม่เริ่มงานใหม่ แต่ยังกันประตูเปิด)
function onInteract(p, block, it, cancel, first = true) {
  if (!p || !block) return;
  const info = classify(block);
  const mat = matOf(it);
  if (mat >= 0) {
    if (boardable(info)) {
      cancel();
      if (!first || busy(p)) return;
      const itemId = it.typeId;
      system.run(() => { try { startNailing(p, block, mat, itemId); } catch (_) {} });
      return;
    }
    if (first && isBoardItem(it)) hint(p, "§7ไม้กั้นใช้ตอกปิดประตูหรือกระจก §8(คลิกขวาที่ประตู/กระจก)");
    return;
  }
  if (!info || !(info.kind === "door" || info.fam === "glass")) return;
  const dim = block.dimension;
  const r = boardsAt(dim, basePos(dim, block, info));
  if (!r || r.n <= 0) return;
  if (info.kind === "door") cancel();                     // ตอกไว้แล้ว เปิดไม่ได้
  if (!it && p.isSneaking) {
    if (!first || busy(p)) return;
    system.run(() => { try { const cur = boardsAt(dim, r); if (cur && cur.n > 0) pry(p, dim, cur); } catch (_) {} });
  } else if (first && info.kind === "door" && !(it && it.typeId.startsWith("ghost:repair_"))) {
    hint(p, "§7ประตูถูกตอกไม้กั้นไว้ §8(ย่อ + คลิกขวามือเปล่า = งัดไม้ออก)");
  }
}
// ขุดประตู/กระจกที่ตอกไว้: ไม้กั้นของประตูได้คืน
function onBreak(p, block) {
  const info = classify(block);
  if (!info || !(info.kind === "door" || info.fam === "glass")) return;
  const dim = block.dimension, r = boardsAt(dim, basePos(dim, block, info));
  if (!r) return;
  system.run(() => {
    try {
      const cur = boardsAt(dim, r);
      if (!cur) return;
      if (cur.k === 0 && !isCreative(p)) for (let i = 0; i < cur.n; i++) dim.spawnItem(new ItemStack(BOARD_ITEM[cur.m[i] || 0], 1), nailLoc(cur));
      dropBoards(dim, cur, false);
    } catch (_) {}
  });
}
export function initBoards() {
  const ok = sub("beforeEvents", "playerInteractWithBlock", (ev) => {
    try { onInteract(ev.player, ev.block, ev.itemStack, () => { ev.cancel = true; }, ev.isFirstEvent !== false); } catch (_) {}
  });
  if (!ok) sub("beforeEvents", "itemUseOn", (ev) => { try { onInteract(ev.source, ev.block, ev.itemStack, () => { ev.cancel = true; }); } catch (_) {} });
  sub("beforeEvents", "playerBreakBlock", (ev) => { try { onBreak(ev.player, ev.block); } catch (_) {} });
  system.runInterval(() => { try { sessionTick(system.currentTick); } catch (_) {} }, 1);
}
