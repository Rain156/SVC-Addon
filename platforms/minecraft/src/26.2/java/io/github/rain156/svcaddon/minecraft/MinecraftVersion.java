package io.github.rain156.svcaddon.minecraft;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

final class MinecraftVersion {
    private MinecraftVersion() { }
    static boolean canManage(CommandSourceStack source) {
        return source.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
    }
    static String dimension(ServerPlayer player) { return player.level().dimension().identifier().toString(); }
}
