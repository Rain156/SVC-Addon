package datura.svcaddon.audio;

import datura.svcaddon.config.SvcAddonConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioMathTest {
    @Test
    void computesPcmRmsAndDbfs() {
        short[] halfScale = {16_384, -16_384, 16_384, -16_384};

        assertEquals(0.5D, AudioMath.normalizedRms(halfScale), 1.0E-12D);
        assertEquals(-6.020_599_913D, AudioMath.dbfs(halfScale), 1.0E-6D);
    }

    @Test
    void handlesSilenceNullAndZeroSamples() {
        assertEquals(0.0D, AudioMath.normalizedRms(null));
        assertEquals(0.0D, AudioMath.normalizedRms(new short[0]));
        assertEquals(SvcAddonConfig.DBFS_FLOOR, AudioMath.dbfs(new short[960]));
        assertEquals(SvcAddonConfig.DBFS_FLOOR, AudioMath.dbfs(new short[0]));
    }

    @Test
    void detectsClippedSamplesWithoutOverflowingSquareSum() {
        short[] samples = {Short.MIN_VALUE, Short.MAX_VALUE, 0, 1};

        assertEquals(0.5D, AudioMath.clippedFraction(samples));
        assertEquals(0.0D, AudioMath.clippedFraction(null));
        assertEquals(0.0D, AudioMath.clippedFraction(new short[0]));
        assertEquals(0.0D, AudioMath.dbfs(new short[]{Short.MIN_VALUE}), 1.0E-12D);
    }
}
