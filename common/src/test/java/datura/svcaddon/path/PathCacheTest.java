package datura.svcaddon.path;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PathCacheTest {
    private static final UUID SPEAKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LISTENER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void expiresEntriesAtTtl() {
        PathCache cache = new PathCache();
        PathKey key = key(0, "minecraft:overworld");
        PathResult result = new PathResult(PathStatus.DIRECT, 4.0D, 4.0D, 0, 1L);
        cache.put(key, result, 1_000_000L);

        assertSame(result, cache.get(key, 100_000_000L, 100L));
        assertNull(cache.get(key, 102_000_000L, 100L));
        assertEquals(0, cache.size());
    }

    @Test
    void replacesOldMovementKeyForSamePair() {
        PathCache cache = new PathCache();
        cache.put(key(0, "minecraft:overworld"), PathResult.unavailable(1.0D, PathStatus.NO_PATH), 1L);
        cache.put(key(1, "minecraft:overworld"), PathResult.unavailable(2.0D, PathStatus.NO_PATH), 2L);

        assertEquals(1, cache.size());
        assertNull(cache.get(key(0, "minecraft:overworld"), 3L, 1_000L));
    }

    @Test
    void invalidatesByPlayerAndDimension() {
        PathCache cache = new PathCache();
        cache.put(key(0, "minecraft:overworld"), PathResult.unavailable(1.0D, PathStatus.NO_PATH), 1L);
        cache.put(key(0, "minecraft:the_nether"), PathResult.unavailable(1.0D, PathStatus.NO_PATH), 1L);

        cache.invalidateDimension("minecraft:overworld");
        assertEquals(1, cache.size());
        cache.invalidatePlayer(SPEAKER);
        assertEquals(0, cache.size());
    }

    private static PathKey key(int speakerX, String dimension) {
        return new PathKey(SPEAKER, LISTENER, dimension, speakerX, 0, 0, 4, 0, 0, 0L);
    }
}
