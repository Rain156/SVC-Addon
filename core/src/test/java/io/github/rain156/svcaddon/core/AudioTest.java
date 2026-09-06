package io.github.rain156.svcaddon.core;

import io.github.rain156.svcaddon.core.audio.AudioLevels;
import io.github.rain156.svcaddon.core.audio.EffectPreset;
import io.github.rain156.svcaddon.core.audio.LevelEnvelope;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class AudioTest {
    @Test void silenceAndFullScaleHaveDefinedDigitalLevels() {
        assertEquals(-96, AudioLevels.dbfs(new short[0]));
        assertEquals(-96, AudioLevels.dbfs(new short[960]));
        short[] full = new short[960];
        Arrays.fill(full, Short.MIN_VALUE);
        assertEquals(0, AudioLevels.dbfs(full), 1e-9);
        Arrays.fill(full, (short) 16384);
        assertEquals(-6.020599913, AudioLevels.dbfs(full), 1e-7);
    }

    @Test void sineHasExpectedRmsLevel() {
        short[] sine = new short[960];
        for (int i = 0; i < sine.length; i++) sine[i] = (short) (32767 * Math.sin(2 * Math.PI * 1000 * i / 48000));
        assertEquals(-3.011, AudioLevels.dbfs(sine), 0.003);
    }

    @Test void envelopeDoesNotDependOnFramePartitioning() {
        var a = new LevelEnvelope();
        var b = new LevelEnvelope();
        double expected = a.update(-10, 1920, 60, 400);
        b.update(-10, 960, 60, 400);
        assertEquals(expected, b.update(-10, 960, 60, 400), 1e-10);
        assertTrue(b.update(-96, 960, 60, 400) < expected);
    }

    @Test void effectInstancesDoNotShareStateAndSilenceStaysSilent() {
        for (EffectPreset preset : EffectPreset.values()) {
            short[] silent = new short[960];
            preset.create().process(silent);
            assertArrayEquals(new short[960], silent);
            short[] a = new short[960];
            Arrays.fill(a, (short) 30000);
            short[] b = a.clone();
            preset.create().process(a);
            preset.create().process(b);
            assertArrayEquals(a, b);
            if (preset != EffectPreset.NONE) assertFalse(Arrays.stream(toInt(a)).allMatch(v -> v == 30000));
        }
    }

    private static int[] toInt(short[] data) {
        int[] result = new int[data.length];
        for (int i = 0; i < data.length; i++) result[i] = data[i];
        return result;
    }
}
