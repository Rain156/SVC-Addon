package datura.svcaddon.path;

public enum PathStatus {
    DIRECT,
    ROUTED,
    NO_PATH,
    TIMEOUT,
    NODE_LIMIT,
    SNAPSHOT_TOO_LARGE,
    UNAVAILABLE,
    ERROR
}
