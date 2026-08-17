package datura.svcaddon.config;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvcAddonConfigTest {
    @Test
    void repairsIllegalAndNonFiniteValues() {
        Properties properties = new Properties();
        properties.setProperty("noise_gate_dbfs", "0");
        properties.setProperty("quiet_dbfs", "-100");
        properties.setProperty("loud_dbfs", "-100");
        properties.setProperty("min_distance", "-5");
        properties.setProperty("max_distance", "-2");
        properties.setProperty("curve_exponent", "100");
        properties.setProperty("path_max_nodes", "1");
        properties.setProperty("path_queue_capacity", "999999");
        properties.setProperty("path_snapshot_budget_ms", "NaN");

        SvcAddonConfig.ParseResult result = assertDoesNotThrow(() -> SvcAddonConfig.fromProperties(properties));
        SvcAddonConfig config = result.config();

        assertTrue(config.noiseGateDbfs() < config.quietDbfs());
        assertTrue(config.quietDbfs() < config.loudDbfs());
        assertTrue(config.minDistance() > 0.0D);
        assertTrue(config.maxDistance() >= config.minDistance());
        assertEquals(8.0D, config.curveExponent());
        assertEquals(64, config.pathMaxNodes());
        assertEquals(4_096, config.pathQueueCapacity());
        assertEquals(SvcAddonConfig.defaults().pathSnapshotBudgetMs(), config.pathSnapshotBudgetMs());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void acceptsFriendlyBooleanSpellings() {
        Properties properties = new Properties();
        properties.setProperty("dynamic_range_enabled", "yes");
        properties.setProperty("path_tracing_enabled", "off");

        SvcAddonConfig config = SvcAddonConfig.fromProperties(properties).config();

        assertTrue(config.dynamicRangeEnabled());
        assertFalse(config.pathTracingEnabled());
    }

    @Test
    void rendersEveryValidatedDefaultSetting() {
        String rendered = assertDoesNotThrow(() -> ConfigText.render(SvcAddonConfig.defaults()));

        assertTrue(rendered.contains("dynamic_range_enabled=true"));
        assertTrue(rendered.contains("blocked_attenuation_factor=0.05"));
        assertTrue(rendered.contains("debug_enabled=false"));
    }
}
