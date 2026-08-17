package datura.svcaddon.audio;

public record VolumeMeasurement(
        double rawDbfs,
        double smoothedDbfs,
        double distance,
        boolean gateOpen,
        double clippedFraction,
        long measuredAtNanos
) {
    public static VolumeMeasurement silent(long measuredAtNanos) {
        return new VolumeMeasurement(-96.0D, -96.0D, 0.0D, false, 0.0D, measuredAtNanos);
    }
}
