package datura.svcaddon.path;

import java.util.Arrays;

public final class VoxelSnapshot {
    private final SnapshotRequest request;
    private final byte[] passable;

    public VoxelSnapshot(SnapshotRequest request, byte[] passable) {
        if (request == null || passable == null) {
            throw new IllegalArgumentException("Snapshot request and data must not be null");
        }
        if (passable.length != request.volume()) {
            throw new IllegalArgumentException("Expected " + request.volume() + " cells, got " + passable.length);
        }
        this.request = request;
        this.passable = Arrays.copyOf(passable, passable.length);
    }

    public static VoxelSnapshot filled(SnapshotRequest request, boolean defaultPassable) {
        byte[] cells = new byte[request.volume()];
        if (defaultPassable) {
            Arrays.fill(cells, (byte) 1);
        }
        return new VoxelSnapshot(request, cells);
    }

    public SnapshotRequest request() {
        return request;
    }

    public boolean contains(int worldX, int worldY, int worldZ) {
        int x = worldX - request.minX();
        int y = worldY - request.minY();
        int z = worldZ - request.minZ();
        return x >= 0 && x < request.sizeX()
                && y >= 0 && y < request.sizeY()
                && z >= 0 && z < request.sizeZ();
    }

    public boolean isPassable(int worldX, int worldY, int worldZ) {
        int index = index(worldX, worldY, worldZ);
        return index >= 0 && passable[index] != 0;
    }

    public int index(int worldX, int worldY, int worldZ) {
        int x = worldX - request.minX();
        int y = worldY - request.minY();
        int z = worldZ - request.minZ();
        if (x < 0 || x >= request.sizeX() || y < 0 || y >= request.sizeY() || z < 0 || z >= request.sizeZ()) {
            return -1;
        }
        return (y * request.sizeZ() + z) * request.sizeX() + x;
    }

    public GridPos position(int index) {
        int x = index % request.sizeX();
        int yz = index / request.sizeX();
        int z = yz % request.sizeZ();
        int y = yz / request.sizeZ();
        return new GridPos(request.minX() + x, request.minY() + y, request.minZ() + z);
    }

    public int volume() {
        return passable.length;
    }

    public Builder toBuilder() {
        return new Builder(request, Arrays.copyOf(passable, passable.length));
    }

    public static Builder builder(SnapshotRequest request) {
        return new Builder(request, new byte[request.volume()]);
    }

    public static final class Builder {
        private final SnapshotRequest request;
        private final byte[] cells;

        private Builder(SnapshotRequest request, byte[] cells) {
            this.request = request;
            this.cells = cells;
        }

        public Builder setPassable(int worldX, int worldY, int worldZ, boolean value) {
            int x = worldX - request.minX();
            int y = worldY - request.minY();
            int z = worldZ - request.minZ();
            if (x >= 0 && x < request.sizeX() && y >= 0 && y < request.sizeY() && z >= 0 && z < request.sizeZ()) {
                int index = (y * request.sizeZ() + z) * request.sizeX() + x;
                cells[index] = value ? (byte) 1 : (byte) 0;
            }
            return this;
        }

        public VoxelSnapshot build() {
            return new VoxelSnapshot(request, cells);
        }
    }
}
