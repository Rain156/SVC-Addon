package io.github.rain156.svcaddon.minecraft;

import io.github.rain156.svcaddon.api.spatial.PlayerSnapshot;
import io.github.rain156.svcaddon.api.spatial.Position;
import io.github.rain156.svcaddon.voicechat.RuntimeHost;
import io.github.rain156.svcaddon.voicechat.VoiceRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MinecraftServerAdapter {
    private static final System.Logger LOG = System.getLogger("svcaddon");
    private MinecraftServerAdapter() { }

    public static void start(MinecraftServer server, Path configDirectory) {
        try {
            RuntimeHost.start(configDirectory, server.getWorldPath(LevelResource.ROOT));
            tick(server);
            LOG.log(System.Logger.Level.INFO, "SVC Addon started; voice policies loaded");
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("SVC Addon could not load configuration; the original files were preserved", e);
        }
    }

    public static void tick(MinecraftServer server) {
        VoiceRuntime runtime = RuntimeHost.current();
        if (runtime == null) return;
        Map<UUID, PlayerSnapshot> players = new HashMap<>();
        for (var player : server.getPlayerList().getPlayers()) {
            var position = player.getEyePosition();
            players.put(player.getUUID(), new PlayerSnapshot(player.getUUID(), MinecraftVersion.dimension(player),
                    new Position(position.x, position.y, position.z), player.isShiftKeyDown(), player.isSpectator()));
        }
        runtime.publish(players);
        for (int i = 0; i < 8; i++) {
            VoiceRuntime.Trigger trigger = runtime.pollTrigger();
            if (trigger == null) break;
            var player = server.getPlayerList().getPlayer(trigger.player());
            if (player == null || VoiceRuntime.millis() - trigger.createdMillis() > 5000) continue;
            String command = trigger.command().replace("{uuid}", player.getUUID().toString())
                    .replace("{player}", player.getName().getString());
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }
    }
}
