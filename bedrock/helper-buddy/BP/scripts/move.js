import { rt, setMoving, moveMult } from "./state.js";
import { isLava, isPassable, safeBlock, yawTo } from "./util.js";
const WALK_SPEED = 0.17; // blocks per tick (~3.4 blocks/second)
export function stopMoving(h) {
    try {
        const vel = h.getVelocity();
        if (Math.abs(vel.x) > 0.01 || Math.abs(vel.z) > 0.01)
            h.applyImpulse({ x: -vel.x * 0.8, y: 0, z: -vel.z * 0.8 });
    }
    catch {
        /* ignore */
    }
    setMoving(h, false);
}
export function face(h, target) {
    try {
        const head = { x: h.location.x, y: h.location.y + 1.6, z: h.location.z };
        const yaw = yawTo(head, target);
        const dx = target.x - head.x;
        const dz = target.z - head.z;
        const dy = target.y - head.y;
        const pitch = (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180) / Math.PI;
        h.setRotation({ x: Math.max(-80, Math.min(80, pitch)), y: yaw });
    }
    catch {
        /* ignore */
    }
}
/**
 * Steers the helper toward a horizontal goal using physics impulses (so walking animation,
 * gravity and collisions behave like a normal mob). Call once per tick.
 */
export function steer(h, goal, arriveDist) {
    const r = rt(h);
    const loc = h.location;
    const dx = goal.x - loc.x;
    const dz = goal.z - loc.z;
    const d = Math.sqrt(dx * dx + dz * dz);
    if (d <= arriveDist) {
        stopMoving(h);
        r.noProgressReset = true;
        return { status: "arrived" };
    }
    const nx = dx / d;
    const nz = dz / d;
    const dim = h.dimension;
    const feetY = Math.floor(loc.y + 0.5);
    const ax = Math.floor(loc.x + nx * 0.75);
    const az = Math.floor(loc.z + nz * 0.75);
    const sameCell = ax === Math.floor(loc.x) && az === Math.floor(loc.z);
    let jump = false;
    if (!sameCell) {
        const feet = safeBlock(dim, { x: ax, y: feetY, z: az });
        const head = safeBlock(dim, { x: ax, y: feetY + 1, z: az });
        const above = safeBlock(dim, { x: ax, y: feetY + 2, z: az });
        const myAbove = safeBlock(dim, { x: Math.floor(loc.x), y: feetY + 2, z: Math.floor(loc.z) });
        if (isLava(feet) || isLava(head))
            return { status: "blocked" };
        const feetOk = isPassable(feet);
        const headOk = isPassable(head);
        if (!headOk || (!feetOk && !(isPassable(above) && isPassable(myAbove)))) {
            const obstacles = [];
            if (head && !headOk)
                obstacles.push(head);
            if (feet && !feetOk)
                obstacles.push(feet);
            stopMoving(h);
            return { status: "blocked", obstacles };
        }
        if (!feetOk)
            jump = true;
        if (feetOk) {
            // check what is below the next cell (avoid cliffs and lava)
            let solidBelow = false;
            for (let i = 1; i <= 4; i++) {
                const b = safeBlock(dim, { x: ax, y: feetY - i, z: az });
                if (isLava(b))
                    return { status: "cliff" };
                if (b && !isPassable(b)) {
                    solidBelow = true;
                    break;
                }
                if (b && b.isLiquid) {
                    solidBelow = true;
                    break;
                }
            }
            if (!solidBelow) {
                stopMoving(h);
                return { status: "cliff" };
            }
        }
    }
    const speed = Math.min(WALK_SPEED * moveMult(h), Math.max(0.07, d * 0.25));
    try {
        const vel = h.getVelocity();
        const imp = { x: nx * speed - vel.x, y: 0, z: nz * speed - vel.z };
        if (jump && r.jumpCd <= 0 && h.isOnGround) {
            imp.y = 0.42;
            r.jumpCd = 8;
        }
        if (h.isInWater && vel.y < 0.1)
            imp.y = Math.max(imp.y, 0.06);
        h.applyImpulse(imp);
        h.setRotation({ x: 0, y: yawTo(loc, goal) });
    }
    catch {
        /* ignore */
    }
    if (r.jumpCd > 0)
        r.jumpCd--;
    setMoving(h, true);
    return { status: "moving" };
}
/** Progress tracker: returns true when the helper has not got closer for `limit` ticks. */
export function isStuck(h, distNow, limit = 40) {
    const r = rt(h);
    if (r.noProgressReset || r.bestDist === undefined) {
        r.noProgressReset = false;
        r.bestDist = distNow;
        r.noProgress = 0;
        return false;
    }
    if (distNow < r.bestDist - 0.15) {
        r.bestDist = distNow;
        r.noProgress = 0;
        return false;
    }
    r.noProgress = (r.noProgress ?? 0) + 1;
    return r.noProgress > limit;
}
export function resetProgress(h) {
    rt(h).noProgressReset = true;
}
/** A spot where the helper can stand (solid floor, two free blocks, no lava). */
export function isStandable(h, x, y, z) {
    const dim = h.dimension;
    const below = safeBlock(dim, { x, y: y - 1, z });
    const at = safeBlock(dim, { x, y, z });
    const up = safeBlock(dim, { x, y: y + 1, z });
    if (!below || below.isLiquid || isPassable(below))
        return false;
    if (isLava(at) || isLava(up))
        return false;
    return isPassable(at) && isPassable(up) && !(at && at.isLiquid && !at.typeId.includes("water"));
}
/** "Magic hop": when the way is blocked, jump a few blocks toward the goal onto a safe spot. */
export function hopToward(h, goal, maxStep = 4) {
    const loc = h.location;
    const dx = goal.x - loc.x;
    const dz = goal.z - loc.z;
    const l = Math.sqrt(dx * dx + dz * dz);
    if (l < 0.5)
        return false;
    const nx = dx / l;
    const nz = dz / l;
    const fy = Math.floor(loc.y + 0.5);
    const ty = Math.floor(goal.y);
    for (let step = Math.min(maxStep, Math.ceil(l)); step >= 2; step--) {
        const x = Math.floor(loc.x + nx * step);
        const z = Math.floor(loc.z + nz * step);
        const dys = [0, 1, -1, 2, -2, 3, -3, 4, -4].sort((a, b) => Math.abs(fy + a - ty) - Math.abs(fy + b - ty));
        for (const dy of dys) {
            const y = fy + dy;
            if (!isStandable(h, x, y, z))
                continue;
            try {
                h.dimension.spawnParticle("minecraft:villager_happy", { x: loc.x, y: loc.y + 1, z: loc.z });
                h.teleport({ x: x + 0.5, y, z: z + 0.5 });
            }
            catch {
                return false;
            }
            resetProgress(h);
            return true;
        }
    }
    return false;
}
