package io.github.rain156.svcaddon.core;

import io.github.rain156.svcaddon.core.audio.EffectPreset;
import io.github.rain156.svcaddon.core.config.ConfigStore;
import io.github.rain156.svcaddon.core.config.PlayerSettingsStore;
import io.github.rain156.svcaddon.core.config.VoiceConfig;
import io.github.rain156.svcaddon.core.player.VoiceSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @TempDir Path temp;

    @Test void firstRunCreatesDocumentedDefaults() throws IOException {
        Path path = temp.resolve("config/server.properties");
        var store = new ConfigStore(path);
        assertEquals(VoiceConfig.defaults(), store.load());
        assertTrue(Files.readString(path).contains("dBFS"));
        assertEquals(VoiceConfig.defaults(), store.load());
    }

    @Test void invalidConfigDoesNotGetOverwritten() throws IOException {
        Path path = temp.resolve("server.properties");
        String invalid = "range.maximum=NaN\n";
        Files.writeString(path, invalid);
        assertThrows(IllegalArgumentException.class, () -> new ConfigStore(path).load());
        assertEquals(invalid, Files.readString(path));
    }

    @Test void typosBooleansAndUnsafeCommandsFailValidation() {
        for (String[] pair : List.of(new String[]{"range.minimun", "5"}, new String[]{"range.dynamic", "yes"},
                new String[]{"schema", "2"}, new String[]{"threshold.hold-ms", "1.5"}, new String[]{"threshold.enabled", "true"})) {
            Properties p = new Properties();
            p.setProperty(pair[0], pair[1]);
            assertThrows(IllegalArgumentException.class, () -> ConfigStore.parse(p));
        }
        assertThrows(IllegalArgumentException.class, () -> new VoiceConfig.Threshold(true, -8, 500, 5000, "say hi\nstop"));
    }

    @Test void playerBatchesPersistAndResetRemovesOverrides() throws IOException {
        Path path = temp.resolve("world/players.properties");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var store = new PlayerSettingsStore(path);
        store.load();
        store.update(List.of(first, second), state -> state.withTransmit(0.0).withEffect(EffectPreset.RADIO));
        var reopened = new PlayerSettingsStore(path);
        reopened.load();
        assertTrue(reopened.get(first).muted());
        assertEquals(EffectPreset.RADIO, reopened.get(second).effect());
        reopened.update(List.of(first), ignored -> VoiceSettings.DEFAULT);
        assertEquals(VoiceSettings.DEFAULT, reopened.get(first));
        assertFalse(Files.readString(path).contains(first.toString()));
    }

    @Test void failedPersistenceNeverPublishesPartialState() throws IOException {
        Path blockedParent = temp.resolve("is-a-file");
        Files.writeString(blockedParent, "preserve");
        var store = new PlayerSettingsStore(blockedParent.resolve("players.properties"));
        UUID player = UUID.randomUUID();
        assertThrows(IOException.class, () -> store.update(List.of(player), state -> state.withTransmit(0.0)));
        assertEquals(VoiceSettings.DEFAULT, store.get(player));
        assertEquals("preserve", Files.readString(blockedParent));
    }
}
