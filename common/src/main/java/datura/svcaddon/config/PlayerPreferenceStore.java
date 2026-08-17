package datura.svcaddon.config;

import datura.svcaddon.AddonLogger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PlayerPreferenceStore {
    public static final String FILE_NAME = "svc_addon-players.properties";

    private final Path file;
    private final AddonLogger logger;
    private final Map<UUID, PlayerPathPreference> preferences = new ConcurrentHashMap<>();

    public PlayerPreferenceStore(Path configDirectory, AddonLogger logger) {
        this.file = configDirectory.resolve(FILE_NAME);
        this.logger = logger;
    }

    public synchronized void load() {
        preferences.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            logger.error("Could not read player path tracing preferences from " + file, exception);
            return;
        }

        for (String rawUuid : properties.stringPropertyNames()) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                PlayerPathPreference preference = PlayerPathPreference.parse(properties.getProperty(rawUuid));
                if (preference != PlayerPathPreference.DEFAULT) {
                    preferences.put(uuid, preference);
                }
            } catch (IllegalArgumentException exception) {
                logger.warn("Ignoring invalid player UUID in " + FILE_NAME + ": " + rawUuid);
            }
        }
    }

    public PlayerPathPreference get(UUID playerUuid) {
        return preferences.getOrDefault(playerUuid, PlayerPathPreference.DEFAULT);
    }

    public synchronized void set(UUID playerUuid, PlayerPathPreference preference) throws IOException {
        PlayerPathPreference previous = preferences.get(playerUuid);
        if (preference == PlayerPathPreference.DEFAULT) {
            preferences.remove(playerUuid);
        } else {
            preferences.put(playerUuid, preference);
        }
        try {
            save();
        } catch (IOException exception) {
            if (previous == null) {
                preferences.remove(playerUuid);
            } else {
                preferences.put(playerUuid, previous);
            }
            throw exception;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        String body = preferences.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .map(entry -> entry.getKey() + "=" + entry.getValue().serializedName())
                .collect(Collectors.joining("\n"));
        String text = "# Per-player SVC Addon path tracing preferences\n" + body + (body.isEmpty() ? "" : "\n");
        Path temporary = Files.createTempFile(file.getParent(), "svc_addon-players-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            ConfigManager.moveAtomically(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
