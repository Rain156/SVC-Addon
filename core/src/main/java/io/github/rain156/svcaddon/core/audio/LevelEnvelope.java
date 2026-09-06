package io.github.rain156.svcaddon.core.audio;

/** Frame-duration-aware attack/release smoothing, independent of packet arrival jitter. */
public final class LevelEnvelope {
    private double value = AudioLevels.SILENCE_DBFS;

    public double update(double dbfs, int samples, double attackMillis, double releaseMillis) {
        double timeConstant = dbfs > value ? attackMillis : releaseMillis;
        double alpha = 1 - Math.exp(-(samples / 48.0) / timeConstant);
        value += alpha * (dbfs - value);
        return value;
    }
}
