package datura.svcaddon.config;

import java.util.Locale;

public final class ConfigText {
    private ConfigText() {
    }

    public static String render(SvcAddonConfig config) {
        return """
                # SVC Addon server configuration
                # Loudness values are dBFS (digital full scale), not calibrated real-world SPL.

                # Dynamic voice distance
                dynamic_range_enabled=%s
                noise_gate_dbfs=%s
                noise_gate_hysteresis_db=%s
                quiet_dbfs=%s
                loud_dbfs=%s
                min_distance=%s
                max_distance=%s
                curve_exponent=%s
                rms_window_ms=%d
                attack_ms=%d
                release_ms=%d
                decoder_reset_gap_ms=%d
                whisper_distance_scale=%s
                whisper_max_distance=%s

                # Acoustic path tracing
                path_tracing_enabled=%s
                allow_player_path_tracing_toggle=%s
                path_cache_ttl_ms=%d
                path_max_nodes=%d
                path_search_timeout_ms=%d
                path_position_quantization=%d
                path_max_distance=%s
                path_search_margin=%d
                path_vertical_margin=%d
                path_max_snapshot_blocks=%d
                path_requests_per_tick=%d
                path_queue_capacity=%d
                path_snapshot_budget_ms=%s
                path_turn_penalty=%s
                path_narrow_penalty=%s
                no_path_cancels_audio=%s
                blocked_attenuation_factor=%s

                # Diagnostics (rate-limited)
                debug_enabled=%s
                debug_log_interval_ms=%d
                """.formatted(
                config.dynamicRangeEnabled(), number(config.noiseGateDbfs()),
                number(config.noiseGateHysteresisDb()), number(config.quietDbfs()),
                number(config.loudDbfs()), number(config.minDistance()), number(config.maxDistance()),
                number(config.curveExponent()), config.rmsWindowMs(), config.attackMs(), config.releaseMs(),
                config.decoderResetGapMs(), number(config.whisperDistanceScale()),
                number(config.whisperMaxDistance()), config.pathTracingEnabled(),
                config.allowPlayerPathTracingToggle(), config.pathCacheTtlMs(), config.pathMaxNodes(),
                config.pathSearchTimeoutMs(), config.pathPositionQuantization(),
                number(config.pathMaxDistance()), config.pathSearchMargin(), config.pathVerticalMargin(),
                config.pathMaxSnapshotBlocks(), config.pathRequestsPerTick(), config.pathQueueCapacity(),
                number(config.pathSnapshotBudgetMs()), number(config.pathTurnPenalty()),
                number(config.pathNarrowPenalty()), config.noPathCancelsAudio(),
                number(config.blockedAttenuationFactor()), config.debugEnabled(), config.debugLogIntervalMs());
    }

    private static String number(double value) {
        String formatted = String.format(Locale.ROOT, "%.4f", value);
        return formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
