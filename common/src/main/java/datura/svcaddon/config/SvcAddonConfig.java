package datura.svcaddon.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public record SvcAddonConfig(
        boolean dynamicRangeEnabled,
        double noiseGateDbfs,
        double noiseGateHysteresisDb,
        double quietDbfs,
        double loudDbfs,
        double minDistance,
        double maxDistance,
        double curveExponent,
        int rmsWindowMs,
        int attackMs,
        int releaseMs,
        int decoderResetGapMs,
        double whisperDistanceScale,
        double whisperMaxDistance,
        boolean pathTracingEnabled,
        boolean allowPlayerPathTracingToggle,
        long pathCacheTtlMs,
        int pathMaxNodes,
        long pathSearchTimeoutMs,
        int pathPositionQuantization,
        double pathMaxDistance,
        int pathSearchMargin,
        int pathVerticalMargin,
        int pathMaxSnapshotBlocks,
        int pathRequestsPerTick,
        int pathQueueCapacity,
        double pathSnapshotBudgetMs,
        double pathTurnPenalty,
        double pathNarrowPenalty,
        boolean noPathCancelsAudio,
        double blockedAttenuationFactor,
        boolean debugEnabled,
        long debugLogIntervalMs
) {
    public static final double DBFS_FLOOR = -96.0D;

    public static SvcAddonConfig defaults() {
        return new SvcAddonConfig(
                true,
                -55.0D,
                3.0D,
                -42.0D,
                -12.0D,
                4.0D,
                48.0D,
                1.4D,
                100,
                80,
                350,
                500,
                0.35D,
                10.0D,
                true,
                true,
                300L,
                4096,
                8L,
                1,
                64.0D,
                6,
                3,
                65_536,
                2,
                256,
                1.5D,
                0.35D,
                0.08D,
                true,
                0.05D,
                false,
                1_000L
        );
    }

    public static ParseResult fromProperties(Properties properties) {
        SvcAddonConfig defaults = defaults();
        List<String> warnings = new ArrayList<>();

        SvcAddonConfig parsed = new SvcAddonConfig(
                booleanValue(properties, "dynamic_range_enabled", defaults.dynamicRangeEnabled, warnings),
                doubleValue(properties, "noise_gate_dbfs", defaults.noiseGateDbfs, warnings),
                doubleValue(properties, "noise_gate_hysteresis_db", defaults.noiseGateHysteresisDb, warnings),
                doubleValue(properties, "quiet_dbfs", defaults.quietDbfs, warnings),
                doubleValue(properties, "loud_dbfs", defaults.loudDbfs, warnings),
                doubleValue(properties, "min_distance", defaults.minDistance, warnings),
                doubleValue(properties, "max_distance", defaults.maxDistance, warnings),
                doubleValue(properties, "curve_exponent", defaults.curveExponent, warnings),
                intValue(properties, "rms_window_ms", defaults.rmsWindowMs, warnings),
                intValue(properties, "attack_ms", defaults.attackMs, warnings),
                intValue(properties, "release_ms", defaults.releaseMs, warnings),
                intValue(properties, "decoder_reset_gap_ms", defaults.decoderResetGapMs, warnings),
                doubleValue(properties, "whisper_distance_scale", defaults.whisperDistanceScale, warnings),
                doubleValue(properties, "whisper_max_distance", defaults.whisperMaxDistance, warnings),
                booleanValue(properties, "path_tracing_enabled", defaults.pathTracingEnabled, warnings),
                booleanValue(properties, "allow_player_path_tracing_toggle", defaults.allowPlayerPathTracingToggle, warnings),
                longValue(properties, "path_cache_ttl_ms", defaults.pathCacheTtlMs, warnings),
                intValue(properties, "path_max_nodes", defaults.pathMaxNodes, warnings),
                longValue(properties, "path_search_timeout_ms", defaults.pathSearchTimeoutMs, warnings),
                intValue(properties, "path_position_quantization", defaults.pathPositionQuantization, warnings),
                doubleValue(properties, "path_max_distance", defaults.pathMaxDistance, warnings),
                intValue(properties, "path_search_margin", defaults.pathSearchMargin, warnings),
                intValue(properties, "path_vertical_margin", defaults.pathVerticalMargin, warnings),
                intValue(properties, "path_max_snapshot_blocks", defaults.pathMaxSnapshotBlocks, warnings),
                intValue(properties, "path_requests_per_tick", defaults.pathRequestsPerTick, warnings),
                intValue(properties, "path_queue_capacity", defaults.pathQueueCapacity, warnings),
                doubleValue(properties, "path_snapshot_budget_ms", defaults.pathSnapshotBudgetMs, warnings),
                doubleValue(properties, "path_turn_penalty", defaults.pathTurnPenalty, warnings),
                doubleValue(properties, "path_narrow_penalty", defaults.pathNarrowPenalty, warnings),
                booleanValue(properties, "no_path_cancels_audio", defaults.noPathCancelsAudio, warnings),
                doubleValue(properties, "blocked_attenuation_factor", defaults.blockedAttenuationFactor, warnings),
                booleanValue(properties, "debug_enabled", defaults.debugEnabled, warnings),
                longValue(properties, "debug_log_interval_ms", defaults.debugLogIntervalMs, warnings)
        );
        return parsed.validate(warnings);
    }

    public ParseResult validate() {
        return validate(new ArrayList<>());
    }

    private ParseResult validate(List<String> warnings) {
        // Keep enough headroom for quiet_dbfs to remain strictly above the gate.
        double gate = clamp("noise_gate_dbfs", noiseGateDbfs, DBFS_FLOOR, -2.0D, warnings);
        double hysteresis = clamp("noise_gate_hysteresis_db", noiseGateHysteresisDb, 0.0D, 20.0D, warnings);
        double quiet = clamp("quiet_dbfs", quietDbfs, gate + 0.5D, -1.0D, warnings);
        double loud = clamp("loud_dbfs", loudDbfs, quiet + 0.5D, 0.0D, warnings);
        double minimumDistance = clamp("min_distance", minDistance, 0.5D, 256.0D, warnings);
        double maximumDistance = clamp("max_distance", maxDistance, minimumDistance, 512.0D, warnings);
        double exponent = clamp("curve_exponent", curveExponent, 0.1D, 8.0D, warnings);
        int window = clamp("rms_window_ms", rmsWindowMs, 20, 1_000, warnings);
        int attack = clamp("attack_ms", attackMs, 1, 2_000, warnings);
        int release = clamp("release_ms", releaseMs, 1, 5_000, warnings);
        int resetGap = clamp("decoder_reset_gap_ms", decoderResetGapMs, 100, 10_000, warnings);
        double whisperScale = clamp("whisper_distance_scale", whisperDistanceScale, 0.01D, 1.0D, warnings);
        double whisperMaximum = clamp("whisper_max_distance", whisperMaxDistance, 0.5D, maximumDistance, warnings);
        long cacheTtl = clamp("path_cache_ttl_ms", pathCacheTtlMs, 50L, 5_000L, warnings);
        int maxNodes = clamp("path_max_nodes", pathMaxNodes, 64, 100_000, warnings);
        long timeout = clamp("path_search_timeout_ms", pathSearchTimeoutMs, 1L, 100L, warnings);
        int quantization = clamp("path_position_quantization", pathPositionQuantization, 1, 8, warnings);
        double maxPathDistance = clamp("path_max_distance", pathMaxDistance, maximumDistance, 512.0D, warnings);
        int horizontalMargin = clamp("path_search_margin", pathSearchMargin, 1, 32, warnings);
        int verticalMargin = clamp("path_vertical_margin", pathVerticalMargin, 1, 16, warnings);
        int snapshotBlocks = clamp("path_max_snapshot_blocks", pathMaxSnapshotBlocks, 1_024, 2_000_000, warnings);
        int requestsPerTick = clamp("path_requests_per_tick", pathRequestsPerTick, 1, 16, warnings);
        int queueCapacity = clamp("path_queue_capacity", pathQueueCapacity, 16, 4_096, warnings);
        double snapshotBudget = clamp("path_snapshot_budget_ms", pathSnapshotBudgetMs, 0.1D, 20.0D, warnings);
        double turnPenalty = clamp("path_turn_penalty", pathTurnPenalty, 0.0D, 10.0D, warnings);
        double narrowPenalty = clamp("path_narrow_penalty", pathNarrowPenalty, 0.0D, 10.0D, warnings);
        double blockedAttenuation = clamp(
                "blocked_attenuation_factor",
                blockedAttenuationFactor,
                0.001D,
                0.25D,
                warnings
        );
        long debugInterval = clamp("debug_log_interval_ms", debugLogIntervalMs, 100L, 60_000L, warnings);

        return new ParseResult(new SvcAddonConfig(
                dynamicRangeEnabled,
                gate,
                hysteresis,
                quiet,
                loud,
                minimumDistance,
                maximumDistance,
                exponent,
                window,
                attack,
                release,
                resetGap,
                whisperScale,
                whisperMaximum,
                pathTracingEnabled,
                allowPlayerPathTracingToggle,
                cacheTtl,
                maxNodes,
                timeout,
                quantization,
                maxPathDistance,
                horizontalMargin,
                verticalMargin,
                snapshotBlocks,
                requestsPerTick,
                queueCapacity,
                snapshotBudget,
                turnPenalty,
                narrowPenalty,
                noPathCancelsAudio,
                blockedAttenuation,
                debugEnabled,
                debugInterval
        ), List.copyOf(warnings));
    }

    public Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("dynamic_range_enabled", Boolean.toString(dynamicRangeEnabled));
        properties.setProperty("noise_gate_dbfs", number(noiseGateDbfs));
        properties.setProperty("noise_gate_hysteresis_db", number(noiseGateHysteresisDb));
        properties.setProperty("quiet_dbfs", number(quietDbfs));
        properties.setProperty("loud_dbfs", number(loudDbfs));
        properties.setProperty("min_distance", number(minDistance));
        properties.setProperty("max_distance", number(maxDistance));
        properties.setProperty("curve_exponent", number(curveExponent));
        properties.setProperty("rms_window_ms", Integer.toString(rmsWindowMs));
        properties.setProperty("attack_ms", Integer.toString(attackMs));
        properties.setProperty("release_ms", Integer.toString(releaseMs));
        properties.setProperty("decoder_reset_gap_ms", Integer.toString(decoderResetGapMs));
        properties.setProperty("whisper_distance_scale", number(whisperDistanceScale));
        properties.setProperty("whisper_max_distance", number(whisperMaxDistance));
        properties.setProperty("path_tracing_enabled", Boolean.toString(pathTracingEnabled));
        properties.setProperty("allow_player_path_tracing_toggle", Boolean.toString(allowPlayerPathTracingToggle));
        properties.setProperty("path_cache_ttl_ms", Long.toString(pathCacheTtlMs));
        properties.setProperty("path_max_nodes", Integer.toString(pathMaxNodes));
        properties.setProperty("path_search_timeout_ms", Long.toString(pathSearchTimeoutMs));
        properties.setProperty("path_position_quantization", Integer.toString(pathPositionQuantization));
        properties.setProperty("path_max_distance", number(pathMaxDistance));
        properties.setProperty("path_search_margin", Integer.toString(pathSearchMargin));
        properties.setProperty("path_vertical_margin", Integer.toString(pathVerticalMargin));
        properties.setProperty("path_max_snapshot_blocks", Integer.toString(pathMaxSnapshotBlocks));
        properties.setProperty("path_requests_per_tick", Integer.toString(pathRequestsPerTick));
        properties.setProperty("path_queue_capacity", Integer.toString(pathQueueCapacity));
        properties.setProperty("path_snapshot_budget_ms", number(pathSnapshotBudgetMs));
        properties.setProperty("path_turn_penalty", number(pathTurnPenalty));
        properties.setProperty("path_narrow_penalty", number(pathNarrowPenalty));
        properties.setProperty("no_path_cancels_audio", Boolean.toString(noPathCancelsAudio));
        properties.setProperty("blocked_attenuation_factor", number(blockedAttenuationFactor));
        properties.setProperty("debug_enabled", Boolean.toString(debugEnabled));
        properties.setProperty("debug_log_interval_ms", Long.toString(debugLogIntervalMs));
        return properties;
    }

    public SvcAddonConfig withPathTracingEnabled(boolean enabled) {
        return new SvcAddonConfig(
                dynamicRangeEnabled, noiseGateDbfs, noiseGateHysteresisDb, quietDbfs, loudDbfs,
                minDistance, maxDistance, curveExponent, rmsWindowMs, attackMs, releaseMs,
                decoderResetGapMs, whisperDistanceScale, whisperMaxDistance, enabled,
                allowPlayerPathTracingToggle, pathCacheTtlMs, pathMaxNodes, pathSearchTimeoutMs,
                pathPositionQuantization, pathMaxDistance, pathSearchMargin, pathVerticalMargin,
                pathMaxSnapshotBlocks, pathRequestsPerTick, pathQueueCapacity, pathSnapshotBudgetMs,
                pathTurnPenalty, pathNarrowPenalty, noPathCancelsAudio, blockedAttenuationFactor,
                debugEnabled, debugLogIntervalMs
        );
    }

    private static boolean booleanValue(Properties properties, String key, boolean fallback, List<String> warnings) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> {
                warnings.add(key + " is not a boolean; using " + fallback);
                yield fallback;
            }
        };
    }

    private static int intValue(Properties properties, String key, int fallback, List<String> warnings) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            warnings.add(key + " is not an integer; using " + fallback);
            return fallback;
        }
    }

    private static long longValue(Properties properties, String key, long fallback, List<String> warnings) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            warnings.add(key + " is not an integer; using " + fallback);
            return fallback;
        }
    }

    private static double doubleValue(Properties properties, String key, double fallback, List<String> warnings) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("not finite");
            }
            return value;
        } catch (NumberFormatException exception) {
            warnings.add(key + " is not a finite number; using " + number(fallback));
            return fallback;
        }
    }

    private static double clamp(String key, double value, double minimum, double maximum, List<String> warnings) {
        double clamped = Math.clamp(value, minimum, maximum);
        if (Double.compare(value, clamped) != 0) {
            warnings.add(key + " corrected from " + number(value) + " to " + number(clamped));
        }
        return clamped;
    }

    private static int clamp(String key, int value, int minimum, int maximum, List<String> warnings) {
        int clamped = Math.clamp(value, minimum, maximum);
        if (value != clamped) {
            warnings.add(key + " corrected from " + value + " to " + clamped);
        }
        return clamped;
    }

    private static long clamp(String key, long value, long minimum, long maximum, List<String> warnings) {
        long clamped = Math.clamp(value, minimum, maximum);
        if (value != clamped) {
            warnings.add(key + " corrected from " + value + " to " + clamped);
        }
        return clamped;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public record ParseResult(SvcAddonConfig config, List<String> warnings) {
    }
}
