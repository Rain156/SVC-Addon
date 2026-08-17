package datura.svcaddon.path;

public record PathResult(
        PathStatus status,
        double directDistance,
        double effectiveDistance,
        int visitedNodes,
        long computationMicros
) {
    public boolean hasUsableRoute() {
        return status == PathStatus.DIRECT || status == PathStatus.ROUTED;
    }

    public static PathResult unavailable(double directDistance, PathStatus status) {
        return new PathResult(status, directDistance, Double.POSITIVE_INFINITY, 0, 0L);
    }
}
