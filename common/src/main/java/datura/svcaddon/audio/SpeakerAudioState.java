package datura.svcaddon.audio;

import datura.svcaddon.config.SvcAddonConfig;

public final class SpeakerAudioState {
    private RmsWindow rmsWindow;
    private final VolumeEnvelope envelope = new VolumeEnvelope();
    private int configuredWindowMs;
    private long lastFrameNanos;
    private volatile VolumeMeasurement latest = VolumeMeasurement.silent(System.nanoTime());

    public SpeakerAudioState(SvcAddonConfig config) {
        configuredWindowMs = config.rmsWindowMs();
        rmsWindow = new RmsWindow(configuredWindowMs);
    }

    public synchronized VolumeMeasurement accept(short[] pcm, long nowNanos, SvcAddonConfig config) {
        if (configuredWindowMs != config.rmsWindowMs()) {
            configuredWindowMs = config.rmsWindowMs();
            rmsWindow = new RmsWindow(configuredWindowMs);
            envelope.reset();
        }

        double elapsedMs = lastFrameNanos == 0L ? 20.0D : (nowNanos - lastFrameNanos) / 1_000_000.0D;
        lastFrameNanos = nowNanos;
        double windowRms = rmsWindow.add(pcm);
        double rawDbfs = AudioMath.dbfsFromNormalizedRms(windowRms);
        VolumeEnvelope.EnvelopeResult envelopeResult = envelope.update(rawDbfs, elapsedMs, config);
        double distance = DistanceMapper.map(envelopeResult.smoothedDbfs(), envelopeResult.gateOpen(), config);
        latest = new VolumeMeasurement(
                envelopeResult.rawDbfs(),
                envelopeResult.smoothedDbfs(),
                distance,
                envelopeResult.gateOpen(),
                AudioMath.clippedFraction(pcm),
                nowNanos
        );
        return latest;
    }

    public synchronized void reset(long nowNanos) {
        rmsWindow.clear();
        envelope.reset();
        lastFrameNanos = 0L;
        latest = VolumeMeasurement.silent(nowNanos);
    }

    public VolumeMeasurement latest() {
        return latest;
    }
}
