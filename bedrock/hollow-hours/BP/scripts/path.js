import { system } from "@minecraft/server";
import { fl, blockId, passable, keyOf, SOFT } from "./util.js";
import { classify, basePos, isOpen, crackOf, POWER_DMG, boardsAt, boardsHits } from "./breach.js";

// =====================================================================
//  v1.4 หาทางอัจฉริยะ (A*) + เลือกพังเฉพาะที่จำเป็น
//  ผีจะเลือกทางที่ "ถูกที่สุด" :
//    เดิน < ขึ้นขั้นบันได < กระโดดลง < ปีนขอบสูง (ตามความสามารถแต่ละตัว) < มุดหน้าต่าง
//    < พังประตู/หน้าต่าง/ไม้กั้น (ตามพลัง) << พังผนัง/พื้น/เพดาน (แพงมาก ทำเมื่อไม่มีทางอื่นจริง ๆ)
//  ไม่พังบันได ไม่พังของที่ไม่ได้ขวางทาง ไม่พังหีบ/เตียง/ของมีค่า
//  คำนวณแบบแบ่งเป็นช่วง ๆ ทีละไม่กี่มิลลิวินาทีต่อ tick (ไม่ให้เกมกระตุก)
// =====================================================================
export const PROTECT = /(chest|barrel|shulker_box|furnace|smoker|hopper|dropper|dispenser|brewing_stand|beacon|conduit|enchanting_table|anvil|_bed$|^bed$|lectern|respawn_anchor|lodestone|spawner|obsidian|netherite_block|ancient_debris|diamond_block|emerald_block|jukebox|bookshelf|decorated_pot|vault|crafter|dragon_egg|end_portal|end_gateway|portal|command_block|structure_block|jigsaw|barrier|bedrock|light_block|border_block|reinforced_deepslate|^allow$|^deny$|ghost:)/;
// ความแข็งของบล็อก (ยิ่งแข็ง ผียิ่งต้องทุบหลายครั้ง)
export function hardness(b) { return hardnessOf(blockId(b).replace("minecraft:", "")); }
function hardnessOf(id) {
  if (/(leaves|wool|hay|sponge|moss|snow|dirt|grass|sand|gravel|clay|mud|farmland|soul_soil|mycelium|podzol|glass)/.test(id)) return 2;
  if (/(netherite|iron_block|gold_block|copper_block|cut_copper|waxed_)/.test(id)) return 8;          // บล็อกโลหะ
  if (/(deepslate|blackstone|basalt|end_stone|purpur|prismarine|quartz)/.test(id)) return 6;
  if (/(plank|log|_wood|stem|hyphae|bamboo|fence|bookshelf|barrel|crafting)/.test(id)) return 3;
  if (/(bricks|terracotta|concrete|tuff|_ore$)/.test(id)) return 5;
  if (/(stairs|slab)/.test(id)) return /(oak|spruce|birch|jungle|acacia|mangrove|cherry|bamboo|crimson|warped)/.test(id) ? 3 : 4;
  return 4;                                                                                            // หิน หินกรวด ฯลฯ
}
// จำนวนครั้งที่ผีต้องทุบบล็อกนี้ (ผีแรงน้อยทุบนานกว่า, โมโหทุบเร็วขึ้น)
export function digHits(b, power, rage) {
  const k = [1.4, 1.4, 1.0, 0.75][Math.max(1, Math.min(3, power || 1))];
  return Math.max(1, Math.round(hardness(b) * k * (rage ? 0.75 : 1)));
}
export function breakable(b) {
  if (!b || passable(b)) return false;
  const id = blockId(b);
  if (classify(b)) return false;
  return !PROTECT.test(id.replace("minecraft:", ""));
}

// ---------------------------------------------------------------------------------- ชนิดช่อง
export const AIR = 0, WATER = 1, CLIMB = 2, SOLID = 3, OPEN = 4, HARD = 5, LAVA = 6, TALL = 7, VOID = 8, DOOR = 9;
export const isFree = (c) => c <= 2;                                   // AIR WATER CLIMB
export const isSupport = (c) => c === SOLID || c === OPEN || c === HARD || c === TALL || c === DOOR;
export const isBreak = (c) => c === SOLID || c === OPEN || c === TALL || c === DOOR;
const costOf = (v) => (v >> 4) / 10;
const CLIMB_RE = /(ladder|vine|scaffolding)/;
const OPENING_RE = /(_door$|trapdoor|fence_gate|glass|^ghost:barricade_)/;
const TALL_RE = /(fence|_wall$|^wall$)/;
const OPEN_BASE = 3, HIT_COST = 2.2;       // ต้นทุนพังประตู/หน้าต่าง = 3 + 2.2 ต่อครั้งที่ต้องทุบ (รวมไม้กั้นที่ตอกไว้)
const TRY_COST = 40;                       // ประตูที่แข็งเกินพลัง: ยังไปลองทุบ แต่เลือกช่องที่อ่อนกว่าก่อน
const GEN_BASE = 22, GEN_PER = 3.5;         // ต้นทุนพังบล็อกทั่วไป = (9 + 3.5 x ความแข็ง) x แรงของผี

export class Grid {
  constructor(dim, o) {
    this.dim = dim; this.power = o.power || 1; this.rage = !!o.rage; this.brk = (o.prof && o.prof.brk) || 1;
    this.failed = o.failed || null;
    this.walls = !!o.walls;                  // false = ห้ามพังผนัง/พื้น/เพดาน (พังได้แค่ประตู หน้าต่าง ไม้กั้น)
    this.ox = fl(o.ox || 0); this.oz = fl(o.oz || 0);
    this.m = new Map(); this.reads = 0;
    let hr = { min: -64, max: 320 };
    try { if (dim.heightRange) hr = dim.heightRange; } catch (_) {}
    this.ymin = hr.min; this.ymax = hr.max - 1;
  }
  key(x, y, z) { return (x - this.ox + 2048) * 4194304 + (z - this.oz + 2048) * 1024 + (y + 512); }
  get(x, y, z) {
    const k = (x - this.ox + 2048) * 4194304 + (z - this.oz + 2048) * 1024 + (y + 512);
    let v = this.m.get(k);
    if (v === undefined) { v = this.compute(x, y, z); this.m.set(k, v); }
    return v;
  }
  code(x, y, z) { return this.get(x, y, z) & 15; }
  forget(x, y, z) { this.m.delete(this.key(x, y, z)); }
  compute(x, y, z) {
    if (y < this.ymin || y > this.ymax) return VOID;
    let b;
    try { b = this.dim.getBlock({ x, y, z }); } catch (_) { return VOID; }
    if (!b) return VOID;
    this.reads++;
    let id = "";
    try { id = b.typeId; } catch (_) { return VOID; }
    if (id === "minecraft:air" || id === "minecraft:cave_air" || id === "minecraft:void_air") return AIR;
    const s = id.startsWith("minecraft:") ? id.slice(10) : id;
    if (s === "water" || s === "flowing_water") return WATER;
    if (s === "lava" || s === "flowing_lava") return LAVA;
    const info = OPENING_RE.test(id) ? classify(b) : null;
    if (info) {
      if ((info.kind === "door" || info.kind === "gate" || info.kind === "trapdoor") && isOpen(b)) return AIR;
      let pos = null;
      try { pos = basePos(this.dim, b, info); } catch (_) {}
      if (pos && this.failed && this.failed.has(keyOf(this.dim.id, pos))) return HARD;     // ลองแล้วพังไม่ไหว
      let hp = info.hp;
      try { const e = pos && crackOf(this.dim, pos); if (e && e.k === info.kind && e.f === info.fam) hp = Math.max(0.5, e.hp - e.h); } catch (_) {}
      const dmg = info.fam === "glass" ? 1 : POWER_DMG[Math.max(1, Math.min(3, this.power))] * (this.rage ? 1.5 : 1);
      let hits = Math.ceil(hp / dmg - 1e-6), strong = info.cls > this.power;
      try {
        const br = pos && boardsAt(this.dim, pos);
        if (br && br.n) { const bh = boardsHits(br, this.power, this.rage); hits += bh.hits; strong = strong || bh.strong; }
      } catch (_) {}
      return (info.kind === "door" ? DOOR : OPEN) | (Math.round((OPEN_BASE + hits * HIT_COST + (strong ? TRY_COST : 0)) * 10) << 4);
    }
    if (SOFT.test(s)) return CLIMB_RE.test(s) ? CLIMB : AIR;
    if (PROTECT.test(s)) return HARD;
    if (!this.walls) return HARD;                                   // รอบแรก: ไม่แตะผนัง
    if (this.failed && this.failed.size && this.failed.has(keyOf(this.dim.id, { x, y, z }))) return HARD;
    const cost = (GEN_BASE + GEN_PER * hardnessOf(s)) * this.brk;
    return (TALL_RE.test(s) ? TALL : SOLID) | (Math.round(cost * 10) << 4);
  }
  standable(x, y, z) {
    const c0 = this.code(x, y, z);
    if (!isFree(c0) || !isFree(this.code(x, y + 1, z))) return false;
    return c0 === WATER || c0 === CLIMB || isSupport(this.code(x, y - 1, z));
  }
  // ช่องว่างเหนือหัว (นับจากเท้า) สูงสุด max ช่อง
  clearance(x, y, z, max) {
    let n = 0;
    while (n < max && isFree(this.code(x, y + n, z))) n++;
    return n;
  }
}

// ---------------------------------------------------------------------------------- heap
class Heap {
  constructor() { this.a = []; }
  get size() { return this.a.length; }
  push(n) {
    const a = this.a; a.push(n);
    let i = a.length - 1;
    while (i > 0) { const p = (i - 1) >> 1; if (a[p].f <= n.f) break; a[i] = a[p]; i = p; }
    a[i] = n;
  }
  pop() {
    const a = this.a, top = a[0], last = a.pop();
    if (a.length) {
      let i = 0; const L = a.length;
      for (;;) {
        let c = 2 * i + 1;
        if (c >= L) break;
        if (c + 1 < L && a[c + 1].f < a[c].f) c++;
        if (a[c].f >= last.f) break;
        a[i] = a[c]; i = c;
      }
      a[i] = last;
    }
    return top;
  }
}

const N4 = [[1, 0], [-1, 0], [0, 1], [0, -1]];
const D4 = [[1, 1], [1, -1], [-1, 1], [-1, -1]];

// ---------------------------------------------------------------------------------- A*
//  o: { prof, power, rage, allowBreak, goalR, reachUp, reachDown, maxExpand, pad, failed }
export class PathJob {
  constructor(dim, from, goal, o) {
    this.o = o; this.prof = o.prof;
    this.G = new Grid(dim, { ...o, ox: from.x, oz: from.z });
    this.goal = { x: goal.x, y: goal.y, z: goal.z };
    this.gy = fl(goal.y + 0.02);
    this.brk = o.allowBreak !== false;
    this.nodes = new Map(); this.open = new Heap();
    this.expanded = 0; this.done = false; this.result = null;
    const pad = o.pad || 12;
    this.bx0 = fl(Math.min(from.x, goal.x)) - pad; this.bx1 = fl(Math.max(from.x, goal.x)) + pad;
    this.bz0 = fl(Math.min(from.z, goal.z)) - pad; this.bz1 = fl(Math.max(from.z, goal.z)) + pad;
    this.by0 = fl(Math.min(from.y, goal.y)) - 7; this.by1 = fl(Math.max(from.y, goal.y)) + 8;
    const s = this.startCell(from);
    if (!s) { this.finish({ ok: false, why: "nostart" }); return; }
    this.start = s;
    const n = this.node(s.x, s.y, s.z);
    n.g = 0; n.f = this.h(s.x, s.y, s.z); n.a = "start";
    this.best = n;
    this.open.push(n);
  }
  startCell(L) {
    const G = this.G, x = fl(L.x), z = fl(L.z), y0 = fl(L.y + 0.05);
    for (const dy of [0, 1, -1, 2, -2]) if (G.standable(x, y0 + dy, z)) return { x, y: y0 + dy, z };
    for (const [dx, dz] of [...N4, ...D4]) for (const dy of [0, 1, -1]) if (G.standable(x + dx, y0 + dy, z + dz)) return { x: x + dx, y: y0 + dy, z: z + dz };
    for (const dy of [0, 1, -1]) if (isFree(G.code(x, y0 + dy, z)) && isFree(G.code(x, y0 + dy + 1, z))) return { x, y: y0 + dy, z };   // กลางอากาศ
    return null;
  }
  node(x, y, z) {
    const k = this.G.key(x, y, z);
    let n = this.nodes.get(k);
    if (!n) { n = { x, y, z, g: Infinity, f: Infinity, p: null, a: null, brk: null, via: null, closed: false }; this.nodes.set(k, n); }
    return n;
  }
  h(x, y, z) {
    const dx = Math.abs(x + 0.5 - this.goal.x), dz = Math.abs(z + 0.5 - this.goal.z), dy = this.gy - y;
    const hor = Math.max(dx, dz) + 0.414 * Math.min(dx, dz);
    return (hor + (dy > 0 ? dy * 1.2 : -dy * 0.5)) * 1.15;
  }
  isGoal(n) {
    const o = this.o, R = o.goalR || 1.5;
    if (Math.abs(n.x + 0.5 - this.goal.x) > R || Math.abs(n.z + 0.5 - this.goal.z) > R) return false;
    const dy = this.gy - n.y;
    return dy <= (o.reachUp ?? 1) && -dy <= (o.reachDown ?? 2);
  }
  inBox(x, y, z) { return x >= this.bx0 && x <= this.bx1 && z >= this.bz0 && z <= this.bz1 && y >= this.by0 && y <= this.by1; }
  push(p, x, y, z, cost, a, brk, via) {
    if (!this.inBox(x, y, z)) return;
    const n = this.node(x, y, z);
    if (n.closed) return;
    const g = p.g + cost;
    if (g >= n.g) return;
    n.g = g; n.f = g + this.h(x, y, z); n.p = p; n.a = a; n.brk = brk || null; n.via = via || null;
    this.open.push(n);
  }
  // ทุบช่องนี้ได้ไหม -> ต้นทุน (หรือ -1)
  bc(v) { const c = v & 15; return this.brk && isBreak(c) ? costOf(v) : -1; }
  expand(n) {
    const G = this.G, P = this.prof, x = n.x, y = n.y, z = n.z;
    const here = G.code(x, y, z), above = G.code(x, y + 1, z);
    const swim = here === WATER, ladder = here === CLIMB || above === CLIMB;
    // ---- บันไดลิง / เถาวัลย์ / ว่ายน้ำ ขึ้น-ลง
    if (ladder || swim) {
      if (isFree(G.code(x, y + 2, z))) this.push(n, x, y + 1, z, swim ? P.swim : 1.5, ladder ? "ladder" : "swim");
      const cb = G.code(x, y - 1, z);
      if (isFree(cb)) this.push(n, x, y - 1, z, swim ? P.swim * 0.8 : 1.2, ladder ? "ladder" : "swim");
    }
    // ---- ขุดพื้นลงไป (ผู้เล่นอยู่ข้างล่าง ไม่มีทางลง)
    if (this.brk && this.gy < y) {
      const vb = G.get(x, y - 1, z), cb = vb & 15;
      if (isBreak(cb) && cb !== DOOR) {
        for (let k = 2; k <= P.drop + 2; k++) {            // ทุบพื้นแล้วร่วงลงไปจนเจอพื้นข้างล่าง
          const c = G.code(x, y - k, z);
          if (isSupport(c)) { this.push(n, x, y - k + 1, z, costOf(vb) + 2 + 0.5 * (k - 2), "dig", [[x, y - 1, z]]); break; }
          if (c === WATER || c === CLIMB) { this.push(n, x, y - k, z, costOf(vb) + 2 + 0.5 * (k - 1), "dig", [[x, y - 1, z]]); break; }
          if (!isFree(c)) break;
        }
      }
    }
    for (let i = 0; i < 4; i++) this.orth(n, N4[i][0], N4[i][1]);
    for (let i = 0; i < 4; i++) this.diag(n, D4[i][0], D4[i][1]);
  }
  diag(n, dx, dz) {
    const G = this.G, x = n.x, y = n.y, z = n.z;
    if (!isFree(G.code(x + dx, y, z)) || !isFree(G.code(x + dx, y + 1, z))) return;
    if (!isFree(G.code(x, y, z + dz)) || !isFree(G.code(x, y + 1, z + dz))) return;
    const nx = x + dx, nz = z + dz, c0 = G.code(nx, y, nz);
    if (!isFree(c0) || !isFree(G.code(nx, y + 1, nz))) return;
    if (isSupport(G.code(nx, y - 1, nz)) || c0 === WATER) this.push(n, nx, y, nz, c0 === WATER ? this.prof.swim * 1.41 : 1.414, c0 === WATER ? "swim" : "walk");
  }
  orth(n, dx, dz) {
    const G = this.G, P = this.prof, x = n.x, y = n.y, z = n.z, nx = x + dx, nz = z + dz;
    const v0 = G.get(nx, y, nz), v1 = G.get(nx, y + 1, nz), vf = G.get(nx, y - 1, nz);
    const c0 = v0 & 15, c1 = v1 & 15, cf = vf & 15;
    const f0 = isFree(c0), f1 = isFree(c1);
    // ---- 1) เดินระดับเดียวกัน / เดินตกขอบลงไป
    if (f0 && f1) {
      if (isSupport(cf) || c0 === WATER || c0 === CLIMB) this.push(n, nx, y, nz, c0 === WATER ? P.swim : 1, c0 === WATER ? "swim" : "walk");
      else if (cf === CLIMB) this.push(n, nx, y - 1, nz, 1.8, "ladder");          // ปีนลงบันไดลิง
      else if (isFree(cf)) {
        for (let k = 2; k <= P.drop + 1; k++) {
          const c = G.code(nx, y - k, nz);
          if (c === WATER || c === CLIMB) { this.push(n, nx, y - k, nz, 1 + 0.5 * k, "drop"); break; }
          if (isFree(c)) continue;
          if (isSupport(c)) this.push(n, nx, y - k + 1, nz, 1 + 0.5 * (k - 1), "drop");
          break;
        }
      }
    }
    // ---- 2) ขึ้นขั้น / ปีน-กระโดดขึ้นขอบ (ขึ้นในช่องตัวเองแล้วก้าวขึ้นไป) — รวมทุบเพดานเหนือหัวถ้าจำเป็น
    let extra = 0, own = [];
    const scanUp = !(f0 && f1) || this.gy > y + 1;
    for (let k = 1; scanUp && k <= P.up; k++) {
      const yb = y + k;
      const vo = G.get(x, yb + 1, z), co = vo & 15;
      if (!isFree(co)) {
        const c = this.bc(vo);
        if (c < 0 || co === DOOR) break;                           // เพดานแข็ง/ประตู: ขึ้นต่อไม่ได้
        extra += c; own = own.concat([[x, yb + 1, z]]);
      }
      const cs = G.code(nx, yb - 1, nz);
      if (!isSupport(cs)) continue;                                // ยังไม่มีขอบให้เหยียบ: ปีนสูงขึ้นอีก
      const va = G.get(nx, yb, nz), vb = G.get(nx, yb + 1, nz), ca = va & 15, cb = vb & 15;
      let lc = 0; const lb = [];
      if (!isFree(ca)) { const c = this.bc(va); if (c < 0) continue; lc += c; lb.push([nx, yb, nz]); }
      if (!isFree(cb)) {
        const c = this.bc(vb); if (c < 0) continue;
        if (!(ca === DOOR && cb === DOOR)) { lc += c; lb.push([nx, yb + 1, nz]); }
      }
      const step = k === 1 && cs !== TALL;
      const base = step ? 1.4 : 2 + P.climb * (k + (cs === TALL ? 0.5 : 0));
      const brk = own.length || lb.length ? own.concat(lb) : null;
      this.push(n, nx, yb, nz, base + extra + lc, step ? "step" : "climb", brk);
      if (lc === 0) break;                                         // เจอขอบโล่ง ๆ แล้ว ไม่ต้องดูที่สูงกว่า
    }
    if (!this.brk && !(f0 && f1)) {
      this.squeeze(n, dx, dz);
      return;
    }
    // ---- 3) ทุบทะลุ (ประตู/หน้าต่าง/ไม้กั้น/ผนัง) ไปช่องข้าง ๆ ระดับเดียวกัน
    if (!(f0 && f1) && (isSupport(cf) || c0 === WATER)) {
      let cost = 1; const brk = [];
      let ok = true;
      if (!f0) { const c = this.bc(v0); if (c < 0) ok = false; else { cost += c; brk.push([nx, y, nz]); } }
      if (ok && !f1) {
        const c = this.bc(v1);
        if (c < 0) ok = false;
        else if (!(c0 === DOOR && c1 === DOOR)) { cost += c; brk.push([nx, y + 1, nz]); }
      }
      if (ok) this.push(n, nx, y, nz, cost, "walk", brk);
    }
    // ---- 4) มุดช่องสูง 1 บล็อก (หน้าต่าง/รู) — ทุบกระจกก่อนถ้ายังปิด
    if (!(f0 && f1)) this.squeeze(n, dx, dz);
  }
  squeeze(n, dx, dz) {
    const G = this.G, x = n.x, y = n.y, z = n.z, nx = x + dx, nz = z + dz;
    for (const yb of [y, y + 1]) {
      const vg = G.get(nx, yb, nz), cg = vg & 15;
      const free = isFree(cg);
      let gc = 0;
      if (!free) { if (cg === DOOR) continue; gc = this.bc(vg); if (gc < 0) continue; }
      if (isFree(G.code(nx, yb + 1, nz))) continue;               // ช่องสูง 2 = ทางเดินปกติ (ข้อ 1/3)
      if (!isSupport(G.code(nx, yb - 1, nz))) continue;           // ใต้ช่องต้องมีขอบ (ขอบหน้าต่าง)
      if (yb > y + 1 || !isFree(G.code(x, yb, z))) continue;
      const mx = nx + dx, mz = nz + dz;
      if (!isFree(G.code(mx, yb, mz))) continue;
      let land = null;
      for (let k = 0; k <= 4; k++) {
        const ly = yb - k, cl = G.code(mx, ly, mz);
        if (!isFree(cl)) break;
        if (isFree(G.code(mx, ly + 1, mz)) && (isSupport(G.code(mx, ly - 1, mz)) || cl === WATER)) { land = ly; break; }
      }
      if (land === null) continue;
      this.push(n, mx, land, mz, 5 + 0.5 * (yb - land) + gc, "squeeze", free ? null : [[nx, yb, nz]], [nx, yb, nz]);
    }
  }
  // ทำงานต่อจนหมดงบเวลา -> true = เสร็จแล้ว (ดู this.result)
  step(t0, budgetMs) {
    if (this.done) return true;
    this.ticks = (this.ticks || 0) + 1;
    const max = this.o.maxExpand || 3000, readCap = this.G.reads + 900;
    let i = 0;
    while (this.open.size) {
      const n = this.open.pop();
      if (n.closed) continue;
      n.closed = true;
      if (this.isGoal(n)) return this.finish({ ok: true, path: this.trace(n), cost: n.g });
      if (n.f - n.g < this.best.f - this.best.g) this.best = n;
      if (++this.expanded >= max) break;
      this.expand(n);
      if ((++i & 3) === 0 && (Date.now() - t0 >= budgetMs || this.G.reads > readCap)) return false;
    }
    const b = this.best;
    const gain = this.start ? this.h(this.start.x, this.start.y, this.start.z) - (b.f - b.g) : 0;
    return this.finish({ ok: false, why: this.open.size ? "limit" : "noroute", partial: b && b.p ? this.trace(b) : null, gain: gain / 1.15 });
  }
  finish(r) {
    this.done = true; this.result = r; r.expanded = this.expanded; r.reads = this.G.reads;
    if (globalThis.__pathStats) globalThis.__pathStats.push({ ok: r.ok, why: r.why, exp: this.expanded, reads: this.G.reads, len: r.path ? r.path.length : 0, cost: r.cost, ticks: this.ticks || 0 });
    return true;
  }
  trace(n) {
    const out = [];
    for (let c = n; c; c = c.p) out.push({ x: c.x, y: c.y, z: c.z, a: c.a, brk: c.brk, via: c.via });
    out.reverse();
    return out;
  }
}

// ---------------------------------------------------------------------------------- ตัวจัดคิวงาน (แบ่งเวลาให้ทุกผี)
const BUDGET_MS = 4;
const jobs = new Map();       // id -> { job, cb, t }
export function plan(id, dim, from, goal, o, cb) {
  const job = new PathJob(dim, from, goal, o);
  jobs.set(id, { job, cb, t: system.currentTick });
  if (job.done) pump();
}
export function cancelPlan(id) { jobs.delete(id); }
export function planning(id) { return jobs.has(id); }
function pump() {
  if (!jobs.size) return;
  const t0 = Date.now();
  for (const [id, J] of [...jobs]) {
    if (!jobs.has(id)) continue;
    let done = false;
    try { done = J.job.step(t0, BUDGET_MS); } catch (e) { done = true; J.job.result = { ok: false, why: "error", err: String(e) }; if (globalThis.__simDebug) globalThis.__errors.push(e); }
    if (done) {
      jobs.delete(id);
      try { J.cb(J.job.result); } catch (e) { if (globalThis.__simDebug) globalThis.__errors.push(e); }
    } else { jobs.delete(id); jobs.set(id, J); }     // หมุนคิว ไม่ให้ผีตัวแรกกินเวลาคนเดียว
    if (Date.now() - t0 >= BUDGET_MS) break;
  }
}
system.runInterval(() => { try { pump(); } catch (_) {} }, 1);
