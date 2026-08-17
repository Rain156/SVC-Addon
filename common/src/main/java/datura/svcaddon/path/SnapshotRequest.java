package datura.svcaddon.path;

public record SnapshotRequest(
        PathKey key,
        Vec3d start,
        Vec3d end,
        int minX,
        int minY,
        int minZ,
        int sizeX,
        int sizeY,
        int sizeZ
) {
    public static SnapshotPlan plan(
            PathKey key,
            Vec3d start,
            Vec3d end,
            int horizontalMargin,
            int verticalMargin,
            int maximumBlocks
    ) {
        GridPos startCell = start.floorToGrid();
        GridPos endCell = end.floorToGrid();
        int minX = Math.min(startCell.x(), endCell.x()) - horizontalMargin;
        int maxX = Math.max(startCell.x(), endCell.x()) + horizontalMargin;
        int minY = Math.min(startCell.y(), endCell.y()) - verticalMargin;
        int maxY = Math.max(startCell.y(), endCell.y()) + verticalMargin;
        int minZ = Math.min(startCell.z(), endCell.z()) - horizontalMargin;
        int maxZ = Math.max(startCell.z(), endCell.z()) + horizontalMargin;
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > maximumBlocks || volume > Integer.MAX_VALUE) {
            return new SnapshotPlan(null, PathStatus.SNAPSHOT_TOO_LARGE, volume);
        }
        return new SnapshotPlan(
                new SnapshotRequest(key, start, end, minX, minY, minZ, sizeX, sizeY, sizeZ),
                null,
                volume
        );
    }

    public int volume() {
        return Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
    }

    public record SnapshotPlan(SnapshotRequest request, PathStatus rejection, long volume) {
        public boolean accepted() {
            return request != null;
        }
    }
}
