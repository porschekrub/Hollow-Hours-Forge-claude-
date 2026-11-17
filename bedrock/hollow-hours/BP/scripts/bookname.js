import { world, system } from "@minecraft/server";

// =====================================================================
//  ชื่อสมุดมั่ว ๆ แบบ "susy16#6-*6#" ที่ตัวอักษรเปลี่ยนไปเรื่อย ๆ
//  - ถือในมือ: เปลี่ยนชื่อทุก 0.5 วินาที (ชื่อเหนือแถบไอเทมกระตุกตลอด)
//  - อยู่ในกระเป๋า: เปลี่ยนทุก 1 วินาที
//  - บางส่วนใช้ §k (ตัวอักษรสุ่มเปลี่ยนเองทุกเฟรมฝั่งเกม)
//  - v1.5: ตั้ง keepOnDeath ให้ทุกเล่ม -> ตายแล้วสมุดยังอยู่ในกระเป๋า
// =====================================================================
const GUIDE = "ghost:ghost_guide";
const BASE = "susy16#6-*6#";
const CH = "abcdefghijklmnopqrstuvwxyz0123456789#*-+=%&@?!~^";
const COL = ["§4", "§c", "§4", "§8", "§4"];
const r = (n) => Math.floor(Math.random() * n);

export function glitchName() {
  let s = "";
  for (const ch of BASE) s += Math.random() < 0.3 ? CH[r(CH.length)] : ch;
  if (Math.random() < 0.3) s = s.slice(0, s.length - 1 - r(2)) + CH[r(CH.length)];
  if (Math.random() < 0.3) s += CH[r(CH.length)];
  let out = "§r§4", obf = false;
  for (let i = 0; i < s.length; i++) {
    if (Math.random() < 0.16) { obf = !obf; out += obf ? "§k" : "§r§4"; }
    if (Math.random() < 0.1) out += COL[r(COL.length)] + (obf ? "§k" : "");
    out += s[i];
  }
  return out + "§r";
}
function gibLore() {
  let a = "", b = "";
  for (let i = 0; i < 14; i++) a += CH[r(CH.length)];
  for (let i = 0; i < 9; i++) b += CH[r(CH.length)];
  return ["§r§8§k" + a, "§r§4" + b.slice(0, 3) + "§k" + b.slice(3)];
}
function renamed(it) {
  it.nameTag = glitchName();
  try { it.setLore(gibLore()); } catch (_) {}
  try { it.keepOnDeath = true; } catch (_) {}       // สมุดไม่ตกตอนตาย
  return it;
}
export function initBookName() {
  let n = 0;
  system.runInterval(() => {
    n++;
    for (const p of world.getAllPlayers()) {
      if (n % 2 === 0) {
        try {
          const eq = p.getComponent("minecraft:equippable");
          const it = eq && eq.getEquipment("Mainhand");
          if (it && it.typeId === GUIDE) eq.setEquipment("Mainhand", renamed(it));
        } catch (_) {}
      }
      if (n % 4 !== 0) continue;
      try {
        const inv = p.getComponent("minecraft:inventory").container;
        for (let i = 0; i < inv.size; i++) {
          const it = inv.getItem(i);
          if (it && it.typeId === GUIDE) inv.setItem(i, renamed(it));
        }
      } catch (_) {}
    }
  }, 5);
}
