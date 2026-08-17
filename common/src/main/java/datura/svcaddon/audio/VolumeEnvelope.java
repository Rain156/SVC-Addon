package datura.svcaddon.audio;

import datura.svcaddon.config.SvcAddonConfig;

public final class VolumeEnvelope {
    private double smoothedDbfs = SvcAddonConfig.DBFS_FLOOR;
    private boolean gateOpen;

    public EnvelopeResult update(double rawDbfs, double elapsedMilliseconds, SvcAddonConfig config) {
        double finiteRaw = Double.isFinite(rawDbfs)
                ? Math.clamp(rawDbfs, SvcAddonConfig.DBFS_FLOOR, 0.0D)
                : SvcAddonConfig.DBFS_FLOOR;

        if (gateOpen) {
            if (finiteRaw < config.noiseGateDbfs() - config.noiseGateHysteresisDb()) {
                gateOpen = false;
            }
        } else if (finiteRaw >= config.noiseGateDbfs()) {
            gateOpen = true;
        }

        double target = gateOpen ? finiteRaw : SvcAddonConfig.DBFS_FLOOR;
        double timeConstant = target > smoothedDbfs ? config.attackMs() : config.releaseMs();
        double dt = Math.clamp(elapsedMilliseconds, 1.0D, 100.0D);
        double alpha = 1.0D - Math.exp(-dt / Math.max(1.0D, timeConstant));
        smoothedDbfs += alpha * (target - smoothedDbfs);
        smoothedDbfs = Math.clamp(smoothedDbfs, SvcAddonConfig.DBFS_FLOOR, 0.0D);

        return new EnvelopeResult(finiteRaw, smoothedDbfs, gateOpen);
    }

    public void reset() {
        smoothedDbfs = SvcAddonConfig.DBFS_FLOOR;
        gateOpen = false;
    }

    public record EnvelopeResult(double rawDbfs, double smoothedDbfs, boolean gateOpen) {
    }
}
