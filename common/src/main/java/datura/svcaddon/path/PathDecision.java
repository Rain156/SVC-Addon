package datura.svcaddon.path;

public record PathDecision(
        PathAction action,
        float replacementDistance,
        PathResult result,
        boolean cacheHit
) {
    public static PathDecision pass(PathResult result, boolean cacheHit) {
        return new PathDecision(PathAction.PASS, Float.NaN, result, cacheHit);
    }

    public static PathDecision cancel(PathResult result, boolean cacheHit) {
        return new PathDecision(PathAction.CANCEL, 0.0F, result, cacheHit);
    }

    public static PathDecision replace(float distance, PathResult result, boolean cacheHit) {
        return new PathDecision(PathAction.REPLACE, distance, result, cacheHit);
    }
}
