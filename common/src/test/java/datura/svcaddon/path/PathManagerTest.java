package datura.svcaddon.path;

import datura.svcaddon.AddonLogger;
import datura.svcaddon.config.SvcAddonConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathManagerTest {
    private static final UUID SPEAKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LISTENER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void buildsSnapshotsIncrementallyAndInvalidatesOnWorldChange() {
        SvcAddonConfig config = SvcAddonConfig.defaults();
        PathManager manager = new PathManager(new SilentLogger());
        manager.start(config);
        try {
            manager.updatePlayers(List.of(
                    player(SPEAKER, 0.5D),
                    player(LISTENER, 3.5D)
            ));

            PathDecision miss = manager.evaluate(SPEAKER, LISTENER, 48.0F, 1L, config);
            assertFalse(miss.cacheHit());

            SnapshotProvider oneCellPerTick = (request, cells, startIndex, deadlineNanos) -> {
                if (startIndex < cells.length) {
                    cells[startIndex] = 1;
                    return startIndex + 1;
                }
                return startIndex;
            };
            for (int tick = 0; tick < 8; tick++) {
                manager.tick(oneCellPerTick, config, 2L + tick);
            }

            PathDecision cached = manager.evaluate(SPEAKER, LISTENER, 48.0F, 20L, config);
            assertTrue(cached.cacheHit());
            assertEquals(PathStatus.DIRECT, cached.result().status());

            manager.invalidateDimension("minecraft:overworld");
            PathDecision invalidated = manager.evaluate(SPEAKER, LISTENER, 48.0F, 21L, config);
            assertFalse(invalidated.cacheHit());
        } finally {
            manager.stop();
        }
    }

    @Test
    void noPathEitherCancelsOrUsesConfiguredWeakAttenuation() {
        PathResult blocked = new PathResult(
                PathStatus.NO_PATH,
                10.0D,
                Double.POSITIVE_INFINITY,
                50,
                100L
        );

        assertEquals(
                PathAction.CANCEL,
                PathManager.decide(blocked, 48.0F, SvcAddonConfig.defaults(), true).action()
        );

        Properties properties = new Properties();
        properties.setProperty("no_path_cancels_audio", "false");
        properties.setProperty("blocked_attenuation_factor", "0.05");
        SvcAddonConfig weakConfig = SvcAddonConfig.fromProperties(properties).config();
        PathDecision weak = PathManager.decide(blocked, 48.0F, weakConfig, true);

        assertEquals(PathAction.REPLACE, weak.action());
        assertTrue(weak.replacementDistance() > 10.0F);
        assertTrue(weak.replacementDistance() < 10.6F);
    }

    @Test
    void tracksSpectatorsInImmutablePlayerSnapshot() {
        PathManager manager = new PathManager(new SilentLogger());
        manager.updatePlayers(List.of(new PlayerSpatialState(
                SPEAKER,
                "minecraft:overworld",
                new Vec3d(0.5D, 0.5D, 0.5D),
                1L,
                true
        )));

        assertTrue(manager.isSpectator(SPEAKER));
        assertFalse(manager.isSpectator(LISTENER));
    }

    private static PlayerSpatialState player(UUID uuid, double x) {
        return new PlayerSpatialState(
                uuid,
                "minecraft:overworld",
                new Vec3d(x, 0.5D, 0.5D),
                0L
        );
    }

    private static final class SilentLogger implements AddonLogger {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }
    }
}
