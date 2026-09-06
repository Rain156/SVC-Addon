package io.github.rain156.svcaddon.api.spatial;

public record Position(double x, double y, double z) {
    public Position {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Coordinates must be finite");
        }
    }

    public double distanceSquared(Position other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
