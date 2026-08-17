package datura.svcaddon.config;

import datura.svcaddon.AddonLogger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigManager {
    public static final String FILE_NAME = "svc_addon.properties";

    private final Path file;
    private final AddonLogger logger;
    private final AtomicReference<SvcAddonConfig> current = new AtomicReference<>(SvcAddonConfig.defaults());

    public ConfigManager(Path configDirectory, AddonLogger logger) {
        this.file = configDirectory.resolve(FILE_NAME);
        this.logger = logger;
    }

    public SvcAddonConfig get() {
        return current.get();
    }

    public synchronized List<String> load() {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException exception) {
                logger.error("Could not read " + file + "; keeping the last valid configuration", exception);
                return List.of("Could not read configuration: " + exception.getMessage());
            }
        }

        SvcAddonConfig.ParseResult result = SvcAddonConfig.fromProperties(properties);
        current.set(result.config());
        try {
            save(result.config());
        } catch (IOException exception) {
            logger.error("Could not write normalized configuration to " + file, exception);
        }
        result.warnings().forEach(warning -> logger.warn("Configuration: " + warning));
        return result.warnings();
    }

    public synchronized void setPathTracingEnabled(boolean enabled) throws IOException {
        SvcAddonConfig updated = current.get().withPathTracingEnabled(enabled);
        save(updated);
        current.set(updated);
    }

    private void save(SvcAddonConfig config) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "svc_addon-", ".tmp");
        try {
            Files.writeString(temporary, ConfigText.render(config), StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
