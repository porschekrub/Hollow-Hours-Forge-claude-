package net.minecraftforge.common.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FakePlayerFactory {
    private FakePlayerFactory() {}

    public static ServerPlayer getMinecraft(ServerLevel level) {
        return net.fabricmc.fabric.api.entity.FakePlayer.get(level);
    }
}
