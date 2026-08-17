package datura.svcaddon.path;

public record GridPos(int x, int y, int z) {
    public GridPos offset(int dx, int dy, int dz) {
        return new GridPos(x + dx, y + dy, z + dz);
    }
}
