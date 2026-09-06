package io.github.rain156.svcaddon.core.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

public final class ConfigStore {
    private static final Set<String> KEYS = Set.of("schema", "range.dynamic", "range.minimum", "range.maximum",
            "range.crouch", "range.whisper", "level.quiet-dbfs", "level.loud-dbfs", "level.attack-ms",
            "level.release-ms", "broadcast.cross-dimension", "threshold.enabled", "threshold.dbfs",
            "threshold.hold-ms", "threshold.cooldown-ms", "threshold.command");
    private final Path path;

    public ConfigStore(Path path) { this.path = path; }

    public VoiceConfig load() throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            try (var stream = ConfigStore.class.getResourceAsStream("/svcaddon-defaults.properties")) {
                if (stream == null) throw new IOException("Missing bundled default config");
                Files.copy(stream, path);
            }
        }
        return parse(AtomicProperties.read(path));
    }

    public VoiceConfig loadUnchecked() {
        try { return load(); } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public static VoiceConfig parse(Properties p) {
        for (String key : p.stringPropertyNames()) {
            if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown configuration key: " + key);
        }
        if (!p.getProperty("schema", "1").equals("1")) throw new IllegalArgumentException("Unsupported config schema");
        var defaults = VoiceConfig.defaults();
        var threshold = defaults.threshold();
        return new VoiceConfig(bool(p, "range.dynamic", defaults.dynamicRange()),
                number(p, "range.minimum", defaults.minimumRange()), number(p, "range.maximum", defaults.maximumRange()),
                number(p, "range.crouch", defaults.crouchRange()), number(p, "range.whisper", defaults.whisperRange()),
                number(p, "level.quiet-dbfs", defaults.quietDbfs()), number(p, "level.loud-dbfs", defaults.loudDbfs()),
                number(p, "level.attack-ms", defaults.attackMillis()), number(p, "level.release-ms", defaults.releaseMillis()),
                bool(p, "broadcast.cross-dimension", defaults.crossDimensionBroadcast()),
                new VoiceConfig.Threshold(bool(p, "threshold.enabled", threshold.enabled()),
                        number(p, "threshold.dbfs", threshold.dbfs()), integer(p, "threshold.hold-ms", threshold.holdMillis()),
                        integer(p, "threshold.cooldown-ms", threshold.cooldownMillis()), p.getProperty("threshold.command", "")));
    }

    private static double number(Properties p, String key, double fallback) {
        return Double.parseDouble(p.getProperty(key, Double.toString(fallback)).trim());
    }

    private static long integer(Properties p, String key, long fallback) {
        return Long.parseLong(p.getProperty(key, Long.toString(fallback)).trim());
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key, Boolean.toString(fallback)).trim();
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException(key + " must be true or false");
        return Boolean.parseBoolean(value);
    }
}
