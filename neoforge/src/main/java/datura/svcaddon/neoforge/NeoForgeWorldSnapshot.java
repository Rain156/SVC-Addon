package datura.svcaddon.neoforge;

import datura.svcaddon.path.SnapshotRequest;
import datura.svcaddon.path.SnapshotProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class NeoForgeWorldSnapshot {
    private NeoForgeWorldSnapshot() {
    }

    static int captureSlice(
            MinecraftServer server,
            SnapshotRequest request,
            byte[] passable,
            int startIndex,
            long deadlineNanos
    ) {
        ServerLevel level = findLevel(server, request.key().dimension());
        if (level == null || !allChunksLoaded(level, request)) {
            return SnapshotProvider.UNAVAILABLE;
        }

        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int index = Math.clamp(startIndex, 0, request.volume());
        int processed = 0;
        while (index < request.volume()) {
            int relativeX = index % request.sizeX();
            int yz = index / request.sizeX();
            int relativeZ = yz % request.sizeZ();
            int relativeY = yz / request.sizeZ();
            position.set(
                    request.minX() + relativeX,
                    request.minY() + relativeY,
                    request.minZ() + relativeZ
            );
            if (!level.isOutsideBuildHeight(position)) {
                BlockState state = level.getBlockState(position);
                boolean openProperty = state.hasProperty(BlockStateProperties.OPEN)
                        && state.getValue(BlockStateProperties.OPEN);
                boolean emptyCollision = state.getCollisionShape(level, position).isEmpty();
                passable[index] = openProperty || emptyCollision ? (byte) 1 : (byte) 0;
            } else {
                passable[index] = 0;
            }
            index++;
            processed++;
            if ((processed & 63) == 0 && System.nanoTime() >= deadlineNanos) {
                break;
            }
        }
        return index;
    }

    static String dimensionId(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (dimensionId(level).equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static boolean allChunksLoaded(ServerLevel level, SnapshotRequest request) {
        int minChunkX = request.minX() >> 4;
        int maxChunkX = (request.minX() + request.sizeX() - 1) >> 4;
        int minChunkZ = request.minZ() >> 4;
        int maxChunkZ = (request.minZ() + request.sizeZ() - 1) >> 4;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
