package io.github.rain156.svcaddon.core.config;

import io.github.rain156.svcaddon.core.audio.EffectPreset;
import io.github.rain156.svcaddon.core.player.VoiceSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Writes occur on the game thread; packet threads read an immutable published map. */
public final class PlayerSettingsStore {
    private final Path path;
    private volatile Map<UUID, VoiceSettings> settings = Map.of();

    public PlayerSettingsStore(Path path) { this.path = path; }

    public void load() throws IOException {
        Properties p = AtomicProperties.read(path);
        Map<UUID, VoiceSettings> loaded = new HashMap<>();
        for (String key : p.stringPropertyNames()) {
            String[] fields = p.getProperty(key).split(",", -1);
            if (fields.length != 4) throw new IllegalArgumentException("Invalid player settings for " + key);
            if (!fields[2].equals("true") && !fields[2].equals("false")) throw new IllegalArgumentException("Invalid broadcast flag");
            loaded.put(UUID.fromString(key), new VoiceSettings(parseRange(fields[0]), parseRange(fields[1]),
                    Boolean.parseBoolean(fields[2]), EffectPreset.valueOf(fields[3])));
        }
        settings = Map.copyOf(loaded);
    }

    public VoiceSettings get(UUID id) { return settings.getOrDefault(id, VoiceSettings.DEFAULT); }

    /** Persist a whole command batch before publishing it, so a failed save has no partial effect. */
    public synchronized void update(Iterable<UUID> ids, UnaryOperator<VoiceSettings> change) throws IOException {
        Map<UUID, VoiceSettings> updated = new HashMap<>(settings);
        for (UUID id : ids) {
            VoiceSettings value = change.apply(updated.getOrDefault(id, VoiceSettings.DEFAULT));
            if (value.equals(VoiceSettings.DEFAULT)) updated.remove(id); else updated.put(id, value);
        }
        Properties p = new Properties();
        updated.forEach((id, value) -> p.setProperty(id.toString(), formatRange(value.transmitRange()) + ","
                + formatRange(value.receiveRange()) + "," + value.broadcast() + "," + value.effect().name()));
        AtomicProperties.write(path, p, "SVC Addon player policies v1: transmit,receive,broadcast,effect; default inherits");
        settings = Map.copyOf(updated);
    }

    private static String formatRange(Double value) { return value == null ? "default" : value.toString(); }
    private static Double parseRange(String value) { return value.equals("default") ? null : Double.valueOf(value); }
}
