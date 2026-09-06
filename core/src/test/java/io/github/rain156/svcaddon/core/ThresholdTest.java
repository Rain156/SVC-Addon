package io.github.rain156.svcaddon.core;

import io.github.rain156.svcaddon.core.config.VoiceConfig;
import io.github.rain156.svcaddon.core.trigger.ThresholdGate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThresholdTest {
    private final VoiceConfig.Threshold config = new VoiceConfig.Threshold(true, -8, 500, 5000, "say loud");

    @Test void holdTimeRejectsShortSpikesAndResetsOnDropout() {
        var gate = new ThresholdGate();
        assertFalse(gate.update(-5, 0, config));
        assertFalse(gate.update(-5, 499, config));
        assertFalse(gate.update(-10, 500, config));
        assertFalse(gate.update(-5, 600, config));
        assertTrue(gate.update(-5, 1100, config));
    }

    @Test void continuousSpeechFiresOnceAndRearmHonorsCooldown() {
        var gate = new ThresholdGate();
        gate.update(-5, 0, config);
        assertTrue(gate.update(-5, 500, config));
        assertFalse(gate.update(-5, 10_000, config));
        gate.update(-9, 10_010, config);
        assertFalse(gate.update(-5, 11_000, config));
        gate.update(-12, 11_010, config);
        gate.update(-5, 11_100, config);
        assertTrue(gate.update(-5, 11_600, config));
        gate.update(-96, 11_620, config);
        gate.update(-5, 11_640, config);
        assertFalse(gate.update(-5, 12_200, config));
        assertTrue(gate.update(-5, 16_600, config));
    }

    @Test void disabledRulesNeverFire() {
        var gate = new ThresholdGate();
        assertFalse(gate.update(0, 0, VoiceConfig.defaults().threshold()));
        assertFalse(gate.update(0, 60_000, VoiceConfig.defaults().threshold()));
    }
}
