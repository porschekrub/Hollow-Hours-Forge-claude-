package com.helperbuddy.gen;

import com.helperbuddy.gen.Gen.Base;
import com.helperbuddy.gen.Gen.Ctx;
import com.helperbuddy.gen.Gen.Opts;
import com.helperbuddy.gen.Gen.Plan;
import com.helperbuddy.gen.Gen.St;

import static com.helperbuddy.gen.Gen.M;

/** Small redstone builds that just work: an automatic iron door, a self-feeding furnace (hoppers and chests), a lever light floor. */
public final class RedstoneGen {
    private RedstoneGen() {}

    /** An iron door in a stone wall, with pressure plates on both sides: walk up and it opens. */
    static Plan autoDoor(Opts o) {
        int W = 5, D = 3;
        Ctx ctx = new Ctx();
        String wall = M + "stone_bricks";
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) ctx.put(x, 0, z, M + "polished_andesite", 1);
        for (int x = 0; x < W; x++)
            for (int y = 1; y <= 3; y++) {
                if (x == 2 && y <= 2) continue;
                ctx.put(x, y, 1, wall, 1);
            }
        Gen.ironDoor(ctx, 2, 1, 1, "pz");
        ctx.put(2, 1, 0, M + "stone_pressure_plate", 4);
        ctx.put(2, 1, 2, M + "stone_pressure_plate", 4);
        for (int x : new int[] {0, 4}) ctx.put(x, 4, 1, M + "lantern", 4, St.hanging(false));
        return Gen.finalize(ctx, W, D, Base.of().fill(M + "stone_bricks").door(2));
    }

    /**
     * A furnace fed by hoppers: things to smelt go in the chest on top, fuel in the chest at the side, and what comes
     * out drops into the chest in the floor.
     */
    static Plan smelter(Opts o) {
        int W = 3, D = 3;
        Ctx ctx = new Ctx();
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) if (x != 1 || z != 1) ctx.put(x, 0, z, M + "polished_andesite", 1);
        ctx.put(1, 0, 1, M + "chest", 1, St.f("nz"));
        ctx.put(1, 1, 1, M + "hopper", 2);
        ctx.put(1, 2, 1, M + "furnace", 1, St.f("nz"));
        ctx.put(1, 3, 1, M + "hopper", 2);
        ctx.put(1, 4, 1, M + "chest", 3, St.f("nz"));
        // fuel from the side
        ctx.put(0, 2, 1, M + "hopper", 2, St.f("px"));
        ctx.put(0, 3, 1, M + "chest", 3, St.f("nz"));
        ctx.put(2, 1, 1, M + "lantern", 4, St.hanging(false));
        return Gen.finalize(ctx, W, D, Base.of().fill(M + "stone").door(1));
    }

    /** Redstone lamps in the floor, switched by a lever on the middle one. */
    static Plan lampFloor(Opts o) {
        int W = 5, D = 5;
        Ctx ctx = new Ctx();
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++) {
                boolean lamp = x == 2 && z >= 1 && z <= 3 || z == 2 && x >= 1 && x <= 3;
                ctx.put(x, 0, z, lamp ? M + "redstone_lamp" : M + "polished_andesite", 1);
            }
        ctx.put(2, 1, 2, M + "lever", 4);
        return Gen.finalize(ctx, W, D, Base.of().fill(M + "stone").door(2));
    }

    public static Plan generate(Opts o) {
        return switch (o.type) {
            case "smelter" -> smelter(o);
            case "lamp" -> lampFloor(o);
            default -> autoDoor(o);
        };
    }
}
