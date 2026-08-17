package datura.svcaddon.path;

import datura.svcaddon.config.SvcAddonConfig;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

public final class AcousticPathfinder {
    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };
    private static final int NO_DIRECTION = 6;

    public PathResult search(VoxelSnapshot snapshot, SvcAddonConfig config) {
        long started = System.nanoTime();
        SnapshotRequest request = snapshot.request();
        double directDistance = request.start().distanceTo(request.end());

        if (VoxelRaycaster.hasLineOfSight(snapshot, request.start(), request.end())) {
            return new PathResult(
                    PathStatus.DIRECT,
                    directDistance,
                    directDistance,
                    0,
                    elapsedMicros(started)
            );
        }

        GridPos start = request.start().floorToGrid();
        GridPos goal = request.end().floorToGrid();
        int startIndex = snapshot.index(start.x(), start.y(), start.z());
        int goalIndex = snapshot.index(goal.x(), goal.y(), goal.z());
        if (startIndex < 0 || goalIndex < 0
                || !snapshot.isPassable(start.x(), start.y(), start.z())
                || !snapshot.isPassable(goal.x(), goal.y(), goal.z())) {
            return PathResult.unavailable(directDistance, PathStatus.NO_PATH);
        }

        double[] best = new double[snapshot.volume() * 7];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        best[stateIndex(startIndex, NO_DIRECTION)] = 0.0D;

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::estimatedTotal));
        open.add(new Node(startIndex, NO_DIRECTION, 0.0D, manhattan(start, goal)));
        long timeoutNanos = config.pathSearchTimeoutMs() * 1_000_000L;
        long deadline = started + timeoutNanos;
        int visited = 0;

        while (!open.isEmpty()) {
            if ((visited & 63) == 0 && System.nanoTime() >= deadline) {
                return new PathResult(
                        PathStatus.TIMEOUT,
                        directDistance,
                        Double.POSITIVE_INFINITY,
                        visited,
                        elapsedMicros(started)
                );
            }
            if (visited >= config.pathMaxNodes()) {
                return new PathResult(
                        PathStatus.NODE_LIMIT,
                        directDistance,
                        Double.POSITIVE_INFINITY,
                        visited,
                        elapsedMicros(started)
                );
            }

            Node current = open.poll();
            int currentStateIndex = stateIndex(current.cellIndex(), current.direction());
            if (current.cost() > best[currentStateIndex] + 1.0E-9D) {
                continue;
            }
            visited++;
            if (current.cellIndex() == goalIndex) {
                double effectiveDistance = Math.max(directDistance, current.cost());
                return new PathResult(
                        PathStatus.ROUTED,
                        directDistance,
                        effectiveDistance,
                        visited,
                        elapsedMicros(started)
                );
            }

            GridPos currentPosition = snapshot.position(current.cellIndex());
            for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                int[] offset = DIRECTIONS[direction];
                int nextX = currentPosition.x() + offset[0];
                int nextY = currentPosition.y() + offset[1];
                int nextZ = currentPosition.z() + offset[2];
                int nextIndex = snapshot.index(nextX, nextY, nextZ);
                if (nextIndex < 0 || !snapshot.isPassable(nextX, nextY, nextZ)) {
                    continue;
                }

                double stepCost = 1.0D;
                if (current.direction() != NO_DIRECTION && current.direction() != direction) {
                    stepCost += config.pathTurnPenalty();
                }
                if (countPassableNeighbors(snapshot, nextX, nextY, nextZ) <= 2) {
                    stepCost += config.pathNarrowPenalty();
                }
                double candidate = current.cost() + stepCost;
                double remaining = manhattan(new GridPos(nextX, nextY, nextZ), goal);
                if (candidate + remaining > config.pathMaxDistance()) {
                    continue;
                }
                int candidateState = stateIndex(nextIndex, direction);
                if (candidate + 1.0E-9D >= best[candidateState]) {
                    continue;
                }
                best[candidateState] = candidate;
                double estimate = candidate + remaining;
                open.add(new Node(nextIndex, direction, candidate, estimate));
            }
        }

        return new PathResult(
                PathStatus.NO_PATH,
                directDistance,
                Double.POSITIVE_INFINITY,
                visited,
                elapsedMicros(started)
        );
    }

    private static int countPassableNeighbors(VoxelSnapshot snapshot, int x, int y, int z) {
        int count = 0;
        for (int[] direction : DIRECTIONS) {
            if (snapshot.isPassable(x + direction[0], y + direction[1], z + direction[2])) {
                count++;
            }
        }
        return count;
    }

    private static int stateIndex(int cellIndex, int direction) {
        return cellIndex * 7 + direction;
    }

    private static double manhattan(GridPos first, GridPos second) {
        return Math.abs(first.x() - second.x())
                + Math.abs(first.y() - second.y())
                + Math.abs(first.z() - second.z());
    }

    private static long elapsedMicros(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000L);
    }

    private record Node(int cellIndex, int direction, double cost, double estimatedTotal) {
    }
}
