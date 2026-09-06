package io.github.rain156.svcaddon.core.audio;

public final class AudioLevels {
    public static final double SILENCE_DBFS = -96.0;
    private AudioLevels() { }

    public static double dbfs(short[] samples) {
        if (samples.length == 0) return SILENCE_DBFS;
        double sum = 0;
        for (short sample : samples) {
            double normalized = sample / 32768.0;
            sum += normalized * normalized;
        }
        return sum == 0 ? SILENCE_DBFS : Math.max(SILENCE_DBFS, 10 * Math.log10(sum / samples.length));
    }
}
