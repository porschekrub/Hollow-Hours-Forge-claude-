import { generateHouse } from "../js/house_gen.js";
import { OPTS } from "./opts.mjs";
import { createHash } from "crypto";
import { writeFileSync } from "fs";
const fmt = (n) => (Number.isInteger(n) ? String(n) : String(n));
function canonSt(st) {
  if (!st) return "-";
  const keys = Object.keys(st).filter((k) => st[k] !== undefined).sort();
  return "{" + keys.map((k) => JSON.stringify(k) + ":" + JSON.stringify(st[k])).join(",") + "}";
}
function line(b) {
  const item = "item" in b ? (b.item === null || b.item === undefined ? (b.item === null ? "null" : "-") : b.item) : "-";
  return [b.x, b.y, b.z, b.id, fmt(b.ph), canonSt(b.st), item, b.dep ? b.dep.join(":") : "-", b.opt === true ? "1" : "-", b.tag ?? "-"].join("|");
}
function header(p) {
  const ca = p.clearArea;
  const cc = p.clearCells ? p.clearCells.map((c) => `${c.x},${c.y},${c.z}`).join(";") : "-";
  return [p.W, p.D, p.minX, p.maxX, p.minZ, p.maxZ, p.maxY, p.front, fmt(p.doorX), ca.x0, ca.x1, ca.z0, ca.z1, p.fillBlock,
    createHash("sha1").update(cc).digest("hex").slice(0, 10), p.underground ? 1 : 0, p.needsItems ? 1 : 0].join("|");
}
const optLines = [];
const res = [];
for (const o of OPTS) {
  const kv = Object.entries(o).filter(([, v]) => v !== undefined).map(([k, v]) => `${k}=${v}`).join(";");
  optLines.push(kv);
  const p = generateHouse(o);
  const text = header(p) + "\n" + p.blocks.map(line).join("\n");
  res.push(createHash("sha1").update(text).digest("hex") + " " + p.blocks.length + " " + kv);
  if (process.argv[2] && kv === process.argv[2]) writeFileSync("/tmp/js_plan.txt", text);
}
writeFileSync("opts.txt", optLines.join("\n") + "\n");
writeFileSync("js.txt", res.join("\n") + "\n");
console.log("plans", res.length);
