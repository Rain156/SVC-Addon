package datura.svcaddon.path;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PathCache {
    private final Map<PathKey, CachedPath> entries = new ConcurrentHashMap<>();
    private final Map<PairKey, PathKey> currentKeys = new ConcurrentHashMap<>();

    public PathResult get(PathKey key, long nowNanos, long ttlMilliseconds) {
        CachedPath cached = entries.get(key);
        if (cached == null) {
            return null;
        }
        long ttlNanos = ttlMilliseconds * 1_000_000L;
        if (nowNanos - cached.createdAtNanos() > ttlNanos) {
            entries.remove(key, cached);
            currentKeys.remove(PairKey.of(key), key);
            return null;
        }
        return cached.result();
    }

    public void put(PathKey key, PathResult result, long nowNanos) {
        PathKey previous = currentKeys.put(PairKey.of(key), key);
        if (previous != null && !previous.equals(key)) {
            entries.remove(previous);
        }
        entries.put(key, new CachedPath(result, nowNanos));
    }

    public void invalidatePlayer(UUID playerUuid) {
        entries.keySet().removeIf(key -> key.speaker().equals(playerUuid) || key.listener().equals(playerUuid));
        currentKeys.keySet().removeIf(key -> key.speaker().equals(playerUuid) || key.listener().equals(playerUuid));
    }

    public void invalidateDimension(String dimension) {
        entries.keySet().removeIf(key -> key.dimension().equals(dimension));
        currentKeys.keySet().removeIf(key -> key.dimension().equals(dimension));
    }

    public void clear() {
        entries.clear();
        currentKeys.clear();
    }

    public int size() {
        return entries.size();
    }

    private record CachedPath(PathResult result, long createdAtNanos) {
    }

    private record PairKey(UUID speaker, UUID listener, String dimension) {
        private static PairKey of(PathKey key) {
            return new PairKey(key.speaker(), key.listener(), key.dimension());
        }
    }
}
