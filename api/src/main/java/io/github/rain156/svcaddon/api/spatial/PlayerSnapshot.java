package io.github.rain156.svcaddon.api.spatial;

import java.util.Objects;
import java.util.UUID;

/** Immutable state captured on the game thread, safe to read on voice threads. */
public record PlayerSnapshot(UUID id, String dimension, Position position, boolean crouching, boolean spectator) {
    public PlayerSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(position);
    }
}
