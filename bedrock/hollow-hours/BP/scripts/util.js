import { world, system, BlockPermutation, MolangVariableMap } from "@minecraft/server";

// =====================================================================
//  ตัวช่วยที่ใช้ร่วมกัน (ใช้ได้กับ @minecraft/server 1.10.0 ขึ้นไป)
// =====================================================================
export const fl = Math.floor;
export const rand = (a, b) => a + Math.random() * (b - a);
export const pick = (a) => a[Math.floor(Math.random() * a.length)];
export const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

export function valid(e) {
  try { if (!e) return false; return typeof e.isValid === "function" ? e.isValid() : e.isValid !== false; } catch (_) { return false; }
}
export function dist(a, b) {
  const x = a.x - b.x, y = a.y - b.y, z = a.z - b.z;
  return Math.sqrt(x * x + y * y + z * z);
}
export function dist2d(a, b) { return Math.hypot(a.x - b.x, a.z - b.z); }
export function getBlock(dim, x, y, z) {
  try { return dim.getBlock({ x: fl(x), y: fl(y), z: fl(z) }); } catch (_) { return undefined; }
}
export function blockId(b) { try { return b ? b.typeId : ""; } catch (_) { return ""; } }
export function stateOf(b, name) { try { return b.permutation.getState(name); } catch (_) { return undefined; } }
export const keyOf = (dimId, p) => `${dimId}|${fl(p.x)}|${fl(p.y)}|${fl(p.z)}`;
export const center = (p, dy = 0.5) => ({ x: fl(p.x) + 0.5, y: fl(p.y) + dy, z: fl(p.z) + 0.5 });

// บล็อกที่เดินทะลุได้ (อากาศ น้ำ หญ้า ดอกไม้ คบเพลิง ป้าย พรม ราง ฯลฯ) + ประตู/รั้ว ที่เปิดอยู่
export const SOFT = /^(short_grass|tall_grass|tallgrass|fern|large_fern|.*flower.*|.*tulip|blue_orchid|allium|azure_bluet|oxeye_daisy|cornflower|lily_of_the_valley|dandelion|poppy|rose_bush|peony|lilac|sunflower|.*torch.*|.*sign|.*carpet|.*rail|.*button|lever|.*pressure_plate|tripwire.*|string|vine|.*_vines.*|snow_layer|.*sapling|brown_mushroom|red_mushroom|crimson_fungus|warped_fungus|deadbush|dead_bush|seagrass|kelp|reeds|sugar_cane|wheat|carrots|potatoes|beetroot|melon_stem|pumpkin_stem|.*_crop|.*banner|redstone_wire|light_block.*|structure_void|fire|soul_fire|.*lantern|.*candle|web|cobweb|sea_pickle|.*coral|.*coral_fan|ladder|scaffolding|glow_lichen|moss_carpet|pink_petals|leaf_litter|bush|frog_spawn|hanging_roots|spore_blossom|small_dripleaf_block|cave_vines.*|nether_sprouts|crimson_roots|warped_roots|sweet_berry_bush|torchflower|wildflowers|firefly_bush|.*dry_grass|cactus_flower|resin_clump|sculk_vein|powder_snow)$/;
export function passable(b) {
  if (!b) return false;
  try {
    if (b.isAir || b.isLiquid) return !blockId(b).includes("lava");
    const id = blockId(b).replace("minecraft:", "");
    if (id.endsWith("_door") || id.includes("fence_gate")) return stateOf(b, "open_bit") === true;
    if (id.endsWith("trapdoor")) return true;
    return SOFT.test(id);
  } catch (_) { return false; }
}
export function solid(b) { return !!b && !passable(b); }

// ช่องที่ยืนได้ (มีพื้นรองรับ + ช่องว่างสูง h บล็อก) ค้นจาก yTop ลงไป depth ช่อง
export function standCell(dim, x, z, yTop, depth = 3, h = 2) {
  for (let y = fl(yTop); y >= fl(yTop) - depth; y--) {
    let ok = true;
    for (let k = 0; k < h; k++) { if (!passable(getBlock(dim, x, y + k, z))) { ok = false; break; } }
    if (!ok) continue;
    const under = getBlock(dim, x, y - 1, z);
    if (under && !passable(under)) return { x: fl(x), y, z: fl(z) };
  }
  return undefined;
}

// บล็อกที่ไม่ใช่อากาศบนสุดในคอลัมน์ (เฉพาะช่วง fromY..toY)
// ใช้ dimension.getTopmostBlock ถ้าผลอยู่ในช่วง ไม่งั้น (เช่นมีหน้าผา/เกาะลอยอยู่สูงกว่าช่วง) ไล่หาเอง
export function topBlock(dim, x, z, fromY, toY) {
  try {
    if (typeof dim.getTopmostBlock === "function") {
      const b = dim.getTopmostBlock({ x: fl(x), z: fl(z) });
      if (!b) return undefined;
      const y = b.location.y;
      if (toY !== undefined && y < toY) return undefined;
      if (fromY === undefined || y <= fromY) return b;
    }
  } catch (_) {}
  let hr = { min: -64, max: 320 };
  try { if (dim.heightRange) hr = dim.heightRange; } catch (_) {}
  const top = Math.min(fromY ?? hr.max - 1, hr.max - 1), bot = Math.max(toY ?? hr.min, hr.min);
  for (let y = fl(top); y >= bot; y--) {
    const b = getBlock(dim, x, y, z);
    if (b && !b.isAir && !blockId(b).includes("light_block")) return b;
  }
  return undefined;
}

export function setAir(dim, p) {
  try { const b = dim.getBlock({ x: fl(p.x), y: fl(p.y), z: fl(p.z) }); if (b) { b.setPermutation(BlockPermutation.resolve("minecraft:air")); return true; } } catch (_) {}
  try { dim.runCommand(`setblock ${fl(p.x)} ${fl(p.y)} ${fl(p.z)} air`); return true; } catch (_) {}
  return false;
}
export function destroyBlock(dim, p) {   // พังแบบมีของดรอป (เหมือนขุด)
  try { dim.runCommand(`setblock ${fl(p.x)} ${fl(p.y)} ${fl(p.z)} air [] destroy`); return true; } catch (_) {}
  try { dim.runCommand(`setblock ${fl(p.x)} ${fl(p.y)} ${fl(p.z)} air 0 destroy`); return true; } catch (_) {}
  return setAir(dim, p);
}

// เสียง: เล่นให้ผู้เล่นทุกคนในระยะ r ได้ยินจากตำแหน่งนั้น
export function sound(dim, id, loc, vol = 1, pitch = 1, r = 32) {
  const x = loc.x.toFixed(2), y = loc.y.toFixed(2), z = loc.z.toFixed(2);
  try { dim.runCommand(`playsound ${id} @a[x=${x},y=${y},z=${z},r=${r}] ${x} ${y} ${z} ${vol} ${pitch}`); } catch (_) {}
}
export function particle(dim, id, loc, vars) {
  try { if (vars) dim.spawnParticle(id, loc, vars); else dim.spawnParticle(id, loc); } catch (_) {
    try { dim.spawnParticle(id, loc); } catch (__) {}
  }
}
export function dirVars(dx, dz) {
  try { const m = new MolangVariableMap(); m.setFloat("variable.dx", dx); m.setFloat("variable.dz", dz); return m; } catch (_) { return undefined; }
}
export function shake(dim, loc, r, intensity, seconds) {
  try {
    for (const p of dim.getPlayers({ location: loc, maxDistance: r })) {
      const k = 1 - Math.min(1, dist(p.location, loc) / r);
      const it = Math.max(0.05, Math.min(1, intensity * (0.35 + 0.65 * k)));
      try { p.runCommand(`camerashake add @s ${it.toFixed(2)} ${seconds} positional`); } catch (_) {}
    }
  } catch (_) {}
}
export function playersNear(dim, loc, r) { try { return dim.getPlayers({ location: loc, maxDistance: r }); } catch (_) { return []; } }
export function isCreative(p) {
  try { if (typeof p.getGameMode === "function") return String(p.getGameMode()).toLowerCase() === "creative"; } catch (_) {}
  try { return p.matches({ gameMode: "creative" }); } catch (_) {}
  return false;
}
export function actionbar(p, text) { try { p.onScreenDisplay.setActionBar(text); } catch (_) {} }
export function now() { return system.currentTick; }

// ผู้เล่นอยู่ใต้หลังคาและมีผนังล้อมไหม (อยู่ในบ้าน/ฐาน)
export function isSheltered(p) {
  try {
    const dim = p.dimension, l = p.location, x = fl(l.x), y = fl(l.y), z = fl(l.z);
    let roof = false;
    for (let dy = 2; dy <= 10; dy++) {
      const b = getBlock(dim, x, y + dy, z);
      if (b && !b.isAir && !blockId(b).includes("leaves") && !blockId(b).includes("light_block")) { roof = true; break; }
    }
    if (!roof) return false;
    let walls = 0;
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      for (let d = 1; d <= 8; d++) { if (solid(getBlock(dim, x + dx * d, y + 1, z + dz * d))) { walls++; break; } }
    }
    return walls >= 3;
  } catch (_) { return false; }
}
// ใต้ดินลึก (ถ้ำ) : มีบล็อกตันอยู่เหนือหัวเกิน 6 ช่อง
export function isUndergroundAt(dim, loc) {
  const x = fl(loc.x), y = fl(loc.y), z = fl(loc.z);
  for (let dy = 7; dy <= 40; dy++) {
    const b = getBlock(dim, x, y + dy, z);
    if (b && !b.isAir && !blockId(b).includes("leaves") && !blockId(b).includes("log") && !blockId(b).includes("light_block")) return true;
  }
  return false;
}
// cos ของมุมระหว่างทิศที่ผู้เล่นมอง กับทิศไปยังจุด (มองแนวราบ)
export function lookDotTo(p, loc) {
  try {
    const v = p.getViewDirection();
    const dx = loc.x - p.location.x, dz = loc.z - p.location.z;
    const dl = Math.hypot(dx, dz) || 1, vl = Math.hypot(v.x, v.z) || 1;
    return (v.x * dx + v.z * dz) / (dl * vl);
  } catch (_) { return -1; }
}
export function lookingAtBlock(p, pos, maxD = 12) {
  try {
    const hit = p.getBlockFromViewDirection({ maxDistance: maxD });
    if (hit && hit.block) { const b = hit.block.location; return Math.abs(b.x - pos.x) <= 1 && Math.abs(b.y - pos.y) <= 1 && Math.abs(b.z - pos.z) <= 1; }
  } catch (_) {}
  return false;
}
export function sub(kind, name, fn) {
  try { world[kind][name].subscribe(fn); return true; } catch (_) { try { console.warn(`[ghost] event ${kind}.${name} unavailable`); } catch (__) {} return false; }
}
