package io.github.rain156.svcaddon.minecraft;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

final class MinecraftVersion {
    private MinecraftVersion() { }
    static boolean canManage(CommandSourceStack source) { return source.hasPermission(2); }
    static String dimension(ServerPlayer player) { return player.serverLevel().dimension().location().toString(); }
}
