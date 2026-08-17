package datura.svcaddon.neoforge;

import com.mojang.logging.LogUtils;
import datura.svcaddon.SvcAddon;
import datura.svcaddon.SvcAddonBootstrap;
import datura.svcaddon.SvcAddonRuntime;
import datura.svcaddon.path.PlayerSpatialState;
import datura.svcaddon.path.Vec3d;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(SvcAddon.MOD_ID)
public final class SvcAddonNeoForge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final SvcAddonRuntime runtime;

    public SvcAddonNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        runtime = SvcAddonBootstrap.install(FMLPaths.CONFIGDIR.get(), new NeoForgeAddonLogger(LOGGER));
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        NeoForgeCommands.register(event.getDispatcher(), runtime);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = System.nanoTime();
        List<PlayerSpatialState> players = new ArrayList<>(server.getPlayerList().getPlayerCount());
        server.getPlayerList().getPlayers().forEach(player -> {
            Vec3 eye = player.getEyePosition();
            players.add(new PlayerSpatialState(
                    player.getUUID(),
                    NeoForgeWorldSnapshot.dimensionId(player.level()),
                    new Vec3d(eye.x(), eye.y(), eye.z()),
                    now,
                    player.isSpectator()
            ));
        });
        runtime.tick(players, (request, cells, startIndex, deadlineNanos) ->
                NeoForgeWorldSnapshot.captureSlice(server, request, cells, startIndex, deadlineNanos), now);
    }

    @SubscribeEvent
    public void onBlockBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            runtime.markWorldChanged(NeoForgeWorldSnapshot.dimensionId(level));
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            runtime.markWorldChanged(NeoForgeWorldSnapshot.dimensionId(level));
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            runtime.markWorldChanged(NeoForgeWorldSnapshot.dimensionId(level));
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        runtime.shutdown();
    }
}
