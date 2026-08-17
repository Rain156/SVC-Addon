package datura.svcaddon.path;

public final class VoxelRaycaster {
    private static final double TIE_EPSILON = 1.0E-10D;

    private VoxelRaycaster() {
    }

    public static boolean hasLineOfSight(VoxelSnapshot snapshot, Vec3d start, Vec3d end) {
        GridPos startCell = start.floorToGrid();
        GridPos endCell = end.floorToGrid();
        int x = startCell.x();
        int y = startCell.y();
        int z = startCell.z();
        if (!snapshot.isPassable(x, y, z) || !snapshot.isPassable(endCell.x(), endCell.y(), endCell.z())) {
            return false;
        }
        if (startCell.equals(endCell)) {
            return true;
        }

        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double dz = end.z() - start.z();
        int stepX = Integer.compare((int) Math.signum(dx), 0);
        int stepY = Integer.compare((int) Math.signum(dy), 0);
        int stepZ = Integer.compare((int) Math.signum(dz), 0);

        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);
        double maxX = firstBoundaryTime(start.x(), x, stepX, dx);
        double maxY = firstBoundaryTime(start.y(), y, stepY, dy);
        double maxZ = firstBoundaryTime(start.z(), z, stepZ, dz);

        int safety = snapshot.volume() * 3 + 3;
        while ((x != endCell.x() || y != endCell.y() || z != endCell.z()) && safety-- > 0) {
            double next = Math.min(maxX, Math.min(maxY, maxZ));
            boolean crossX = Math.abs(maxX - next) <= TIE_EPSILON;
            boolean crossY = Math.abs(maxY - next) <= TIE_EPSILON;
            boolean crossZ = Math.abs(maxZ - next) <= TIE_EPSILON;

            int mask = (crossX ? 1 : 0) | (crossY ? 2 : 0) | (crossZ ? 4 : 0);
            for (int subset = mask; subset > 0; subset = (subset - 1) & mask) {
                int checkX = x + ((subset & 1) != 0 ? stepX : 0);
                int checkY = y + ((subset & 2) != 0 ? stepY : 0);
                int checkZ = z + ((subset & 4) != 0 ? stepZ : 0);
                if (!snapshot.isPassable(checkX, checkY, checkZ)) {
                    return false;
                }
            }

            if (crossX) {
                x += stepX;
                maxX += deltaX;
            }
            if (crossY) {
                y += stepY;
                maxY += deltaY;
            }
            if (crossZ) {
                z += stepZ;
                maxZ += deltaZ;
            }
        }
        return x == endCell.x() && y == endCell.y() && z == endCell.z();
    }

    private static double firstBoundaryTime(double coordinate, int cell, int step, double direction) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? cell + 1.0D : cell;
        return (boundary - coordinate) / direction;
    }
}
