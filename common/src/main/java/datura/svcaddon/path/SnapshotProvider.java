package datura.svcaddon.path;

@FunctionalInterface
public interface SnapshotProvider {
    int UNAVAILABLE = -1;

    /**
     * Reads an immutable-snapshot slice on the Minecraft server thread.
     *
     * @return the next linear cell index, {@link #UNAVAILABLE} if the dimension or a required chunk is unavailable
     */
    int captureSlice(
            SnapshotRequest request,
            byte[] passable,
            int startIndex,
            long deadlineNanos
    );
}
