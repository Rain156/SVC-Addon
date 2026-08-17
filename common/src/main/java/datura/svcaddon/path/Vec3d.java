package datura.svcaddon.path;

public record Vec3d(double x, double y, double z) {
    public double distanceTo(Vec3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public GridPos floorToGrid() {
        return new GridPos(floor(x), floor(y), floor(z));
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }
}
