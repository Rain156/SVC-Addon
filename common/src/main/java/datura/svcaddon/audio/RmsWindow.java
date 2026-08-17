package datura.svcaddon.audio;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RmsWindow {
    public static final int SAMPLE_RATE = 48_000;

    private final Deque<EnergyFrame> frames = new ArrayDeque<>();
    private int maximumSamples;
    private int sampleCount;
    private double squareSum;

    public RmsWindow(int windowMilliseconds) {
        setWindowMilliseconds(windowMilliseconds);
    }

    public void setWindowMilliseconds(int windowMilliseconds) {
        maximumSamples = Math.max(1, Math.round(SAMPLE_RATE * windowMilliseconds / 1_000.0F));
        trim();
    }

    public double add(short[] samples) {
        if (samples != null && samples.length > 0) {
            EnergyFrame frame = new EnergyFrame(AudioMath.sumSquares(samples), samples.length);
            frames.addLast(frame);
            squareSum += frame.squareSum();
            sampleCount += frame.sampleCount();
            trim();
        }
        return normalizedRms();
    }

    public double normalizedRms() {
        if (sampleCount == 0) {
            return 0.0D;
        }
        return Math.sqrt(squareSum / sampleCount) / 32_768.0D;
    }

    public void clear() {
        frames.clear();
        sampleCount = 0;
        squareSum = 0.0D;
    }

    private void trim() {
        while (frames.size() > 1 && sampleCount - frames.getFirst().sampleCount() >= maximumSamples) {
            EnergyFrame removed = frames.removeFirst();
            squareSum -= removed.squareSum();
            sampleCount -= removed.sampleCount();
        }
        if (squareSum < 0.0D && squareSum > -1.0E-6D) {
            squareSum = 0.0D;
        }
    }

    private record EnergyFrame(double squareSum, int sampleCount) {
    }
}
