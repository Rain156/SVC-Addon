package datura.svcaddon.path;

import java.util.UUID;

public record PlayerSpatialState(
        UUID playerUuid,
        String dimension,
        Vec3d eyePosition,
        long capturedAtNanos,
        boolean spectator
) {
    public PlayerSpatialState(UUID playerUuid, String dimension, Vec3d eyePosition, long capturedAtNanos) {
        this(playerUuid, dimension, eyePosition, capturedAtNanos, false);
    }

    public PlayerSpatialState {
        if (playerUuid == null || dimension == null || eyePosition == null) {
            throw new IllegalArgumentException("Player spatial state fields must not be null");
        }
    }
}
