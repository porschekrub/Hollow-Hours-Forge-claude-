package com.hollowhours.grave;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** Server smoke test of the grave bookkeeping: crumbling on time, the restorer's list, saving and loading. */
public final class GraveSmoke {
    private GraveSmoke() {}

    public static String run(MinecraftServer server) {
        Graves d = Graves.get(server);
        UUID owner = UUID.randomUUID();
        long now = Graves.now(server);
        for (int i = 0; i < Graves.KEEP_LOST + 2; i++) {
            Graves.Grave g = new Graves.Grave();
            g.id = UUID.randomUUID();
            g.owner = owner;
            g.ownerName = "smoke";
            g.pos = new BlockPos(i, 64, 0);
            g.created = i == 0 ? now - Graves.lifetime() - 5 : now;
            g.xp = 10 + i;
            g.items.add(new ItemStack(Items.DIAMOND, i + 1));
            d.active.put(g.id, g);
        }
        Graves.tick(server);
        check(d.lost.get(owner) != null && d.lost.get(owner).size() == 1 && d.active.size() >= Graves.KEEP_LOST + 1, "an old grave crumbles, new ones stay");
        for (Graves.Grave g : d.active.values().stream().filter(g -> g.owner.equals(owner)).toList()) Graves.crumble(d, g);
        check(d.lost.get(owner).size() == Graves.KEEP_LOST, "only the newest crumbled graves are kept");
        Graves loaded = Graves.load(d.save(new net.minecraft.nbt.CompoundTag()));
        check(loaded.lost.get(owner).size() == Graves.KEEP_LOST, "graves survive saving");
        Graves.Grave newest = Graves.popLost(server, owner);
        check(newest != null && newest.items.get(0).getCount() == Graves.KEEP_LOST + 2 && newest.xp == 10 + Graves.KEEP_LOST + 1, "the restorer brings back the newest");
        d.lost.remove(owner);
        return "graves OK";
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
