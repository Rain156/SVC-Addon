package datura.svcaddon.path;

import datura.svcaddon.config.SvcAddonConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcousticPathfinderTest {
    private static final SvcAddonConfig CONFIG = testConfig();
    private static final AcousticPathfinder PATHFINDER = new AcousticPathfinder();

    @Test
    void findsStraightUnobstructedPath() {
        SnapshotRequest request = request(
                new Vec3d(0.5D, 0.5D, 1.5D),
                new Vec3d(6.5D, 0.5D, 1.5D),
                0, 0, 0, 7, 1, 3
        );

        PathResult result = PATHFINDER.search(VoxelSnapshot.filled(request, true), CONFIG);

        assertEquals(PathStatus.DIRECT, result.status());
        assertEquals(6.0D, result.effectiveDistance(), 1.0E-9D);
    }

    @Test
    void rejectsCompleteWallWithoutExit() {
        SnapshotRequest request = request(
                new Vec3d(1.5D, 0.5D, 2.5D),
                new Vec3d(5.5D, 0.5D, 2.5D),
                0, 0, 0, 7, 1, 5
        );
        VoxelSnapshot.Builder builder = VoxelSnapshot.filled(request, true).toBuilder();
        for (int z = 0; z < 5; z++) {
            builder.setPassable(3, 0, z, false);
        }

        PathResult result = PATHFINDER.search(builder.build(), CONFIG);

        assertEquals(PathStatus.NO_PATH, result.status());
    }

    @Test
    void routesThroughDoorOpeningInWall() {
        SnapshotRequest request = request(
                new Vec3d(1.5D, 0.5D, 1.5D),
                new Vec3d(5.5D, 0.5D, 1.5D),
                0, 0, 0, 7, 1, 5
        );
        VoxelSnapshot.Builder builder = VoxelSnapshot.filled(request, true).toBuilder();
        for (int z = 0; z < 5; z++) {
            builder.setPassable(3, 0, z, false);
        }
        builder.setPassable(3, 0, 3, true);

        PathResult result = PATHFINDER.search(builder.build(), CONFIG);

        assertEquals(PathStatus.ROUTED, result.status());
        assertTrue(result.effectiveDistance() > result.directDistance());
    }

    @Test
    void followsAnLCorridorAroundACorner() {
        SnapshotRequest request = request(
                new Vec3d(1.5D, 0.5D, 1.5D),
                new Vec3d(5.5D, 0.5D, 5.5D),
                0, 0, 0, 7, 1, 7
        );
        VoxelSnapshot.Builder builder = VoxelSnapshot.builder(request);
        for (int x = 1; x <= 5; x++) {
            builder.setPassable(x, 0, 1, true);
        }
        for (int z = 1; z <= 5; z++) {
            builder.setPassable(5, 0, z, true);
        }

        PathResult result = PATHFINDER.search(builder.build(), CONFIG);

        assertEquals(PathStatus.ROUTED, result.status());
        assertTrue(result.effectiveDistance() >= 8.0D);
    }

    @Test
    void doesNotLeakThroughDiagonalCorner() {
        SnapshotRequest request = request(
                new Vec3d(0.5D, 0.5D, 0.5D),
                new Vec3d(1.5D, 0.5D, 1.5D),
                0, 0, 0, 2, 1, 2
        );
        VoxelSnapshot snapshot = VoxelSnapshot.builder(request)
                .setPassable(0, 0, 0, true)
                .setPassable(1, 0, 1, true)
                .build();

        assertFalse(VoxelRaycaster.hasLineOfSight(snapshot, request.start(), request.end()));
        assertEquals(PathStatus.NO_PATH, PATHFINDER.search(snapshot, CONFIG).status());
    }

    private static SnapshotRequest request(
            Vec3d start,
            Vec3d end,
            int minX,
            int minY,
            int minZ,
            int sizeX,
            int sizeY,
            int sizeZ
    ) {
        PathKey key = new PathKey(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "minecraft:overworld",
                0, 0, 0, 0, 0, 0, 0L
        );
        return new SnapshotRequest(key, start, end, minX, minY, minZ, sizeX, sizeY, sizeZ);
    }

    private static SvcAddonConfig testConfig() {
        Properties properties = new Properties();
        properties.setProperty("path_search_timeout_ms", "100");
        return SvcAddonConfig.fromProperties(properties).config();
    }
}
