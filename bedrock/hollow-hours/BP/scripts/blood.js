// Blood (port of the Java mod's BloodFx): every hit sprays blood, in the creature's own colour, away from whoever hit
// it; it splashes onto the ground and stays there for a while; a badly hurt player leaves drips.
import { world, system, MolangVariableMap } from "@minecraft/server";
import { players, playing, oops } from "./store.js";

export const CFG = { ENABLED: true };
/** 0xRRGGBB per creature; -1 = it doesn't bleed. */
const COLORS = { zombie: 0x5A1A0C, zombie_villager: 0x5A1A0C, zombie_villager_v2: 0x5A1A0C, husk: 0x6B4A1E, drowned: 0x2F5B4A, zombie_pigman: 0x7A3A2A, zoglin: 0x6A2A1A,
  skeleton: 0xE8E4D6, stray: 0xD8E4EC, bogged: 0xC8D4B0, wither_skeleton: 0x2A2A2A, skeleton_horse: 0xE8E4D6, creeper: 0x3DAA3D, spider: 0x5E7A1E, cave_spider: 0x2E6A6A,
  silverfish: 0x8A8A8A, enderman: 0x9A33CC, endermite: 0x9A33CC, shulker: 0xB57AB5, ender_dragon: 0x9A33CC, slime: 0x6BD66B, magma_cube: 0xFF7A1A, blaze: 0xFFB21A,
  ghast: 0xEDEDF2, phantom: 0x5A7AB8, vex: 0xAFC8E8, allay: 0x7AD4F0, bee: 0xE8C53A, squid: 0x111122, glow_squid: 0x2AE0C8, guardian: 0x4A9A8A, elder_guardian: 0x4A9A8A,
  wither: 0x151515, warden: 0x0E4A55, strider: 0xB8442A, axolotl: 0xE87AA0, frog: 0x8A3020, snow_golem: -1, iron_golem: -1, armor_stand: -1, creaking: -1, breeze: 0xBFD8FF };
const NO = /^minecraft:(item|xp_orb|arrow|boat|chest_boat|minecart|.*minecart|painting|armor_stand|ender_crystal|tnt|falling_block|fireball|small_fireball|snowball|egg|thrown_trident|fishing_hook|shulker_bullet|leash_knot|npc|agent)$/;

function color(e) {
  const id = e.typeId;
  if (id.startsWith("ghost:ghost_") || id.startsWith("ghost:stalker_")) return 0x120404;
  if (id.startsWith("ghost:") || NO.test(id)) return -1;
  const n = id.replace("minecraft:", "");
  return COLORS[n] ?? 0x8A0606;
}
function vars(col, n, dx, dz) {
  const m = new MolangVariableMap();
  m.setFloat("variable.r", ((col >> 16) & 255) / 255); m.setFloat("variable.g", ((col >> 8) & 255) / 255); m.setFloat("variable.b", (col & 255) / 255);
  m.setFloat("variable.n", n); m.setFloat("variable.dx", dx); m.setFloat("variable.dz", dz);
  return m;
}
function ground(dim, x, y, z) {
  for (let k = 0; k < 6; k++) {
    try { const b = dim.getBlock({ x: Math.floor(x), y: Math.floor(y) - k, z: Math.floor(z) }); if (b && !b.isAir && !b.isLiquid) return Math.floor(y) - k + 1.02; } catch (_) { return null; }
  }
  return null;
}
export function spray(e, by, damage) {
  const col = color(e);
  if (col < 0) return;
  const l = e.location, dim = e.dimension;
  let dx = 0, dz = 0;
  if (by) { try { dx = l.x - by.location.x; dz = l.z - by.location.z; const len = Math.hypot(dx, dz) || 1; dx /= len; dz /= len; } catch (_) { oops(_); } }
  const n = Math.max(4, Math.min(18, Math.round(4 + damage * 1.5)));
  try { dim.spawnParticle("ghost:blood", { x: l.x, y: l.y + 0.9, z: l.z }, vars(col, n, dx, dz)); } catch (_) { oops(_); }
  // it lands a little behind the creature and stays
  const stains = Math.min(3, 1 + Math.floor(damage / 5));
  for (let i = 0; i < stains; i++) {
    const sx = l.x + dx * (0.6 + Math.random() * 1.2) + (Math.random() - 0.5), sz = l.z + dz * (0.6 + Math.random() * 1.2) + (Math.random() - 0.5);
    system.runTimeout(() => { try { const y = ground(dim, sx, l.y + 1, sz); if (y !== null) dim.spawnParticle("ghost:blood_stain", { x: sx, y, z: sz }, vars(col, 1, 0, 0)); } catch (_) { oops(_); } }, 6 + i * 2);
  }
}

export function initBlood() {
  try {
    world.afterEvents.entityHurt.subscribe((ev) => {
      try {
        if (!CFG.ENABLED || ev.damage < 1) return;
        const cause = String(ev.damageSource.cause);
        if (!/entityAttack|projectile|entityExplosion|blockExplosion|fall|contact|thorns|sonicBoom|anvil|stalactite|stalagmite|maceSmash|ramAttack/.test(cause)) return;
        spray(ev.hurtEntity, ev.damageSource.damagingEntity, ev.damage);
      } catch (_) { oops(_); }
    });
  } catch (_) { oops(_); }
  // badly hurt: drips
  system.runInterval(() => {
    if (!CFG.ENABLED) return;
    for (const p of players()) {
      try {
        if (!playing(p)) continue;
        const h = p.getComponent("minecraft:health");
        if (!h || h.currentValue <= 0 || h.currentValue > 6) continue;
        const l = p.location, y = ground(p.dimension, l.x, l.y + 0.5, l.z);
        if (y !== null && Math.random() < 0.6) p.dimension.spawnParticle("ghost:blood_stain", { x: l.x + (Math.random() - 0.5) * 0.5, y, z: l.z + (Math.random() - 0.5) * 0.5 }, vars(0x8A0606, 1, 0, 0));
      } catch (_) { oops(_); }
    }
  }, 30);
}
