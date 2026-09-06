package io.github.rain156.svcaddon.core.config;

import java.util.Objects;

public record VoiceConfig(boolean dynamicRange, double minimumRange, double maximumRange,
                          double crouchRange, double whisperRange, double quietDbfs, double loudDbfs,
                          double attackMillis, double releaseMillis, boolean crossDimensionBroadcast,
                          Threshold threshold) {
    public VoiceConfig {
        range(minimumRange, 0, 1024, "range.minimum");
        range(maximumRange, 0.1, 1024, "range.maximum");
        if (minimumRange > maximumRange) throw new IllegalArgumentException("Minimum range exceeds maximum");
        range(crouchRange, 0, maximumRange, "range.crouch");
        range(whisperRange, 0, maximumRange, "range.whisper");
        range(quietDbfs, -96, 0, "level.quiet-dbfs");
        range(loudDbfs, -96, 0, "level.loud-dbfs");
        if (quietDbfs >= loudDbfs) throw new IllegalArgumentException("Quiet dBFS must be below loud dBFS");
        range(attackMillis, 1, 10_000, "level.attack-ms");
        range(releaseMillis, 1, 10_000, "level.release-ms");
        Objects.requireNonNull(threshold);
    }

    public static VoiceConfig defaults() {
        return new VoiceConfig(true, 4, 48, 6, 6, -50, -10, 60, 400, false,
                new Threshold(false, -8, 500, 5000, ""));
    }

    public static void range(double value, double min, double max, String key) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(key + " must be finite and between " + min + " and " + max);
        }
    }

    public record Threshold(boolean enabled, double dbfs, long holdMillis, long cooldownMillis, String command) {
        public Threshold {
            range(dbfs, -96, 0, "threshold.dbfs");
            range(holdMillis, 20, 60_000, "threshold.hold-ms");
            range(cooldownMillis, 1000, 3_600_000, "threshold.cooldown-ms");
            Objects.requireNonNull(command);
            if (command.length() > 2048 || command.contains("\n") || command.contains("\r")) {
                throw new IllegalArgumentException("Threshold command must be a single line of at most 2048 characters");
            }
            if (enabled && command.isBlank()) throw new IllegalArgumentException("Enabled threshold requires a command");
        }
    }
}
