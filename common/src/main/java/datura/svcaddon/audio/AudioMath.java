package datura.svcaddon.audio;

import datura.svcaddon.config.SvcAddonConfig;

public final class AudioMath {
    private static final double PCM_16_FULL_SCALE = 32_768.0D;
    private static final double EPSILON = 1.0E-12D;

    private AudioMath() {
    }

    public static double normalizedRms(short[] samples) {
        if (samples == null || samples.length == 0) {
            return 0.0D;
        }
        return Math.sqrt(sumSquares(samples) / samples.length) / PCM_16_FULL_SCALE;
    }

    public static double dbfs(short[] samples) {
        return dbfsFromNormalizedRms(normalizedRms(samples));
    }

    public static double dbfsFromNormalizedRms(double normalizedRms) {
        if (!Double.isFinite(normalizedRms) || normalizedRms <= EPSILON) {
            return SvcAddonConfig.DBFS_FLOOR;
        }
        return Math.max(SvcAddonConfig.DBFS_FLOOR, 20.0D * Math.log10(normalizedRms));
    }

    public static double sumSquares(short[] samples) {
        if (samples == null) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (short sample : samples) {
            double value = sample;
            sum += value * value;
        }
        return sum;
    }

    public static double clippedFraction(short[] samples) {
        if (samples == null || samples.length == 0) {
            return 0.0D;
        }
        int clipped = 0;
        for (short sample : samples) {
            if (sample == Short.MIN_VALUE || sample == Short.MAX_VALUE) {
                clipped++;
            }
        }
        return clipped / (double) samples.length;
    }
}
