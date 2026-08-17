package datura.svcaddon.audio;

import datura.svcaddon.config.SvcAddonConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceMapperTest {
    private static final SvcAddonConfig CONFIG = SvcAddonConfig.defaults();

    @Test
    void closedGateHasNoPropagationDistance() {
        assertEquals(0.0D, DistanceMapper.map(-10.0D, false, CONFIG));
    }

    @Test
    void clampsAtConfiguredMinimumAndMaximum() {
        assertEquals(CONFIG.minDistance(), DistanceMapper.map(-90.0D, true, CONFIG));
        assertEquals(CONFIG.minDistance(), DistanceMapper.map(CONFIG.quietDbfs(), true, CONFIG));
        assertEquals(CONFIG.maxDistance(), DistanceMapper.map(CONFIG.loudDbfs(), true, CONFIG));
        assertEquals(CONFIG.maxDistance(), DistanceMapper.map(0.0D, true, CONFIG));
    }

    @Test
    void mapsContinuouslyBetweenQuietAndLoud() {
        double low = DistanceMapper.map(-32.01D, true, CONFIG);
        double middle = DistanceMapper.map(-32.0D, true, CONFIG);
        double high = DistanceMapper.map(-31.99D, true, CONFIG);

        assertTrue(low < middle);
        assertTrue(middle < high);
        assertTrue(high - low < 0.1D);
        assertTrue(middle > CONFIG.minDistance());
        assertTrue(middle < CONFIG.maxDistance());
    }
}
