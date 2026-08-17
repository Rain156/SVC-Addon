package datura.svcaddon.fabric;

import datura.svcaddon.SvcAddon;
import datura.svcaddon.SvcAddonBootstrap;
import datura.svcaddon.SvcAddonRuntime;
import datura.svcaddon.path.PlayerSpatialState;
import datura.svcaddon.path.Vec3d;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class SvcAddonFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SvcAddon.MOD_ID);

    @Override
    public void onInitialize() {
        SvcAddonRuntime runtime = SvcAddonBootstrap.install(
                FabricLoader.getInstance().getConfigDir(),
                new FabricAddonLogger(LOGGER)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                FabricCommands.register(dispatcher, runtime));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.nanoTime();
            List<PlayerSpatialState> players = new ArrayList<>(server.getPlayerList().getPlayerCount());
            server.getPlayerList().getPlayers().forEach(player -> {
                Vec3 eye = player.getEyePosition();
                players.add(new PlayerSpatialState(
                        player.getUUID(),
                        FabricWorldSnapshot.dimensionId(player.level()),
                        new Vec3d(eye.x(), eye.y(), eye.z()),
                        now,
                        player.isSpectator()
                ));
            });
            runtime.tick(players, (request, cells, startIndex, deadlineNanos) ->
                    FabricWorldSnapshot.captureSlice(server, request, cells, startIndex, deadlineNanos), now);
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!level.isClientSide()) {
                runtime.markWorldChanged(level.dimension().identifier().toString());
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide()) {
                runtime.markWorldChanged(level.dimension().identifier().toString());
            }
            return InteractionResult.PASS;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> runtime.shutdown());
    }
}
