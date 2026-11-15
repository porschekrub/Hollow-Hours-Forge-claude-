package varmite.verity.entity.llm.actions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Stand-in (see README.md): the object Verity's AI tools live on (who asked, where). */
public class Tools {
    public ServerPlayer player;
    public MinecraftServer server;
    public ServerLevel level;

    String transform_next_day() {
        throw new AssertionError();
    }
}
