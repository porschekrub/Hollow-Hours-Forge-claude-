// Deterministic option grid shared by the JS and Java dumps.
import { TYPES } from "../js/house_gen.js";
const out = [];
const bools = [
  { light: true, furniture: true, porch: true, chimney: true, pool: true },
  { light: false, furniture: false, porch: false, chimney: false, pool: false },
  { light: true, furniture: true, porch: false, chimney: true, pool: false },
  {},
];
for (const t of TYPES) {
  const vmax = t.id === "statue" ? 12 : t.id === "underground" ? 5 : ["hotel","hospital","prison","school","library","church","barn","market"].includes(t.id) ? 3 : 1;
  for (let v = 0; v < vmax; v++)
    for (let size = 0; size < 3; size++)
      for (const floors of [1, 2, 3, 5])
        for (let bi = 0; bi < bools.length; bi++) {
          const styles = t.id === "standard" ? [0, 1, 2, 3] : [0];
          for (const style of styles) {
            const wood = (v + size + floors + bi) % 9;
            const roof = (size * 3 + floors + bi) % 7;
            const mat = (v * 7 + size * 3 + floors + bi) % 10;
            const o = { type: t.id, style, size, floors, wood, roof, mat, variant: v, ...bools[bi] };
            if (bi === 3) { delete o.mat; delete o.roof; }
            if ((size + floors) % 4 === 1) o.floorsExact = true;
            out.push(o);
          }
        }
  // exact sizes / heights
  for (const [w, d, h] of [[13, 17, undefined], [6, 30, 40], [25, 9, 20], [41, 41, 60]]) {
    const o = { type: t.id, style: 1, size: 1, floors: 2, wood: 3, roof: 2, mat: 0, variant: 1, light: true, furniture: true, porch: true, chimney: true, pool: true, w, d };
    if (h !== undefined) o.height = h;
    out.push(o);
  }
}
export const OPTS = out;
