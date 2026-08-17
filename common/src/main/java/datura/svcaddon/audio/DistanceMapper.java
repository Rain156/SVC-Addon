package datura.svcaddon.audio;

import datura.svcaddon.config.SvcAddonConfig;

public final class DistanceMapper {
    private DistanceMapper() {
    }

    public static double map(double dbfs, boolean gateOpen, SvcAddonConfig config) {
        if (!gateOpen) {
            return 0.0D;
        }
        double denominator = config.loudDbfs() - config.quietDbfs();
        double normalized = Math.clamp((dbfs - config.quietDbfs()) / denominator, 0.0D, 1.0D);
        double smoothstep = normalized * normalized * (3.0D - 2.0D * normalized);
        double curved = Math.pow(smoothstep, config.curveExponent());
        double distance = config.minDistance() + curved * (config.maxDistance() - config.minDistance());
        return Math.clamp(distance, config.minDistance(), config.maxDistance());
    }
}
