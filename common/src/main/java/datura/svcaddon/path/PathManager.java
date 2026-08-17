package datura.svcaddon.path;

import datura.svcaddon.AddonLogger;
import datura.svcaddon.config.SvcAddonConfig;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class PathManager {
    private static final int ABSOLUTE_QUEUE_CAPACITY = 4_096;

    private final AddonLogger logger;
    private final AcousticPathfinder pathfinder = new AcousticPathfinder();
    private final PathCache cache = new PathCache();
    private final ArrayBlockingQueue<QueuedPath> snapshotRequests = new ArrayBlockingQueue<>(ABSOLUTE_QUEUE_CAPACITY);
    private final java.util.Set<PathKey> pending = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, AtomicLong> worldRevisions = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Map<UUID, PlayerSpatialState>> players = new AtomicReference<>(Map.of());
    private final AtomicReference<ThreadPoolExecutor> worker = new AtomicReference<>();
    private final AtomicReference<ActiveSnapshot> activeSnapshot = new AtomicReference<>();
    private volatile boolean acceptingRequests;

    public PathManager(AddonLogger logger) {
        this.logger = logger;
    }

    public synchronized void start(SvcAddonConfig config) {
        acceptingRequests = false;
        stopWorker();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "svc-addon-pathfinder");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, throwable) ->
                    logger.error("Uncaught exception in acoustic path worker", throwable));
            return thread;
        };
        worker.set(new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.pathQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        ));
        acceptingRequests = true;
    }

    public void updatePlayers(Collection<PlayerSpatialState> spatialStates) {
        Map<UUID, PlayerSpatialState> copy = new ConcurrentHashMap<>();
        for (PlayerSpatialState state : spatialStates) {
            copy.put(state.playerUuid(), state);
        }
        players.set(Map.copyOf(copy));
    }

    public boolean isSpectator(UUID playerUuid) {
        PlayerSpatialState state = players.get().get(playerUuid);
        return state != null && state.spectator();
    }

    public PathDecision evaluate(
            UUID speakerUuid,
            UUID listenerUuid,
            float packetDistance,
            long nowNanos,
            SvcAddonConfig config
    ) {
        if (!acceptingRequests) {
            return PathDecision.pass(PathResult.unavailable(0.0D, PathStatus.UNAVAILABLE), false);
        }
        if (!Float.isFinite(packetDistance) || packetDistance <= 0.0F) {
            return PathDecision.pass(PathResult.unavailable(0.0D, PathStatus.UNAVAILABLE), false);
        }
        Map<UUID, PlayerSpatialState> positionSnapshot = players.get();
        PlayerSpatialState speaker = positionSnapshot.get(speakerUuid);
        PlayerSpatialState listener = positionSnapshot.get(listenerUuid);
        if (speaker == null || listener == null || !speaker.dimension().equals(listener.dimension())) {
            return PathDecision.pass(PathResult.unavailable(0.0D, PathStatus.UNAVAILABLE), false);
        }

        double directDistance = speaker.eyePosition().distanceTo(listener.eyePosition());
        if (directDistance > config.pathMaxDistance()) {
            return PathDecision.pass(PathResult.unavailable(directDistance, PathStatus.UNAVAILABLE), false);
        }

        long revision = revision(speaker.dimension());
        PathKey key = PathKey.create(speaker, listener, config.pathPositionQuantization(), revision);
        PathResult cached = cache.get(key, nowNanos, config.pathCacheTtlMs());
        if (cached != null) {
            return decide(cached, packetDistance, config, true);
        }

        if (pending.add(key)) {
            if (snapshotRequests.size() >= config.pathQueueCapacity()
                    || !snapshotRequests.offer(new QueuedPath(key, speaker, listener, generation.get()))) {
                pending.remove(key);
            }
        }
        return PathDecision.pass(PathResult.unavailable(directDistance, PathStatus.UNAVAILABLE), false);
    }

    public void tick(SnapshotProvider snapshotProvider, SvcAddonConfig config, long nowNanos) {
        if (!acceptingRequests) {
            return;
        }
        long budgetNanos = Math.max(100_000L, (long) (config.pathSnapshotBudgetMs() * 1_000_000.0D));
        long deadlineNanos = System.nanoTime() + budgetNanos;
        int startedPairs = 0;
        while (System.nanoTime() < deadlineNanos) {
            ActiveSnapshot active = activeSnapshot.get();
            if (active == null) {
                if (startedPairs >= config.pathRequestsPerTick()) {
                    break;
                }
                QueuedPath queued = snapshotRequests.poll();
                if (queued == null) {
                    break;
                }
                if (!isCurrent(queued, config)) {
                    pending.remove(queued.key());
                    continue;
                }
                active = createSnapshot(queued, true, config, nowNanos);
                if (active == null) {
                    continue;
                }
                if (!activeSnapshot.compareAndSet(null, active)) {
                    pending.remove(queued.key());
                    continue;
                }
                startedPairs++;
            }
            if (!isCurrent(active.queued, config)) {
                if (activeSnapshot.compareAndSet(active, null)) {
                    pending.remove(active.queued.key());
                }
                continue;
            }

            int nextIndex;
            try {
                nextIndex = snapshotProvider.captureSlice(
                        active.request,
                        active.passable,
                        active.nextIndex,
                        deadlineNanos
                );
            } catch (RuntimeException exception) {
                logger.error("Failed to capture an acoustic world snapshot", exception);
                completeUnavailable(active, PathStatus.ERROR, nowNanos, config);
                continue;
            }
            if (activeSnapshot.get() != active) {
                continue;
            }
            if (nextIndex == SnapshotProvider.UNAVAILABLE) {
                completeUnavailable(active, PathStatus.UNAVAILABLE, nowNanos, config);
                continue;
            }
            if (nextIndex < active.nextIndex || nextIndex > active.request.volume()) {
                logger.warn("Snapshot provider returned an invalid cell index");
                completeUnavailable(active, PathStatus.ERROR, nowNanos, config);
                continue;
            }
            active.nextIndex = nextIndex;
            if (nextIndex < active.request.volume()) {
                break;
            }

            forceEndpointPassable(active.request, active.passable, active.request.start().floorToGrid());
            forceEndpointPassable(active.request, active.passable, active.request.end().floorToGrid());
            VoxelSnapshot snapshot = new VoxelSnapshot(active.request, active.passable);
            if (active.directOnly) {
                if (VoxelRaycaster.hasLineOfSight(snapshot, active.request.start(), active.request.end())) {
                    if (activeSnapshot.compareAndSet(active, null) && isCurrent(active.queued, config)) {
                        double directDistance = directDistance(active.queued);
                        cache.put(active.queued.key(), new PathResult(
                                PathStatus.DIRECT,
                                directDistance,
                                directDistance,
                                0,
                                elapsedMicros(active.startedNanos)
                        ), nowNanos);
                    }
                    pending.remove(active.queued.key());
                    continue;
                }

                if (!activeSnapshot.compareAndSet(active, null)) {
                    continue;
                }
                ActiveSnapshot routed = createSnapshot(active.queued, false, config, nowNanos);
                if (routed != null) {
                    if (!isCurrent(active.queued, config)
                            || !activeSnapshot.compareAndSet(null, routed)) {
                        pending.remove(active.queued.key());
                    }
                }
                continue;
            }

            if (activeSnapshot.compareAndSet(active, null)) {
                submit(active.queued, snapshot, config);
            }
        }
    }

    public void invalidateDimension(String dimension) {
        worldRevisions.computeIfAbsent(dimension, ignored -> new AtomicLong()).incrementAndGet();
        cache.invalidateDimension(dimension);
        ActiveSnapshot active = activeSnapshot.get();
        if (active != null && active.queued.key().dimension().equals(dimension)
                && activeSnapshot.compareAndSet(active, null)) {
            pending.remove(active.queued.key());
        }
        snapshotRequests.removeIf(request -> request.key().dimension().equals(dimension));
        pending.removeIf(key -> key.dimension().equals(dimension));
    }

    public void removePlayer(UUID playerUuid) {
        cache.invalidatePlayer(playerUuid);
        ActiveSnapshot active = activeSnapshot.get();
        if (active != null && (active.queued.key().speaker().equals(playerUuid)
                || active.queued.key().listener().equals(playerUuid))
                && activeSnapshot.compareAndSet(active, null)) {
            pending.remove(active.queued.key());
        }
        snapshotRequests.removeIf(request -> request.key().speaker().equals(playerUuid)
                || request.key().listener().equals(playerUuid));
        pending.removeIf(key -> key.speaker().equals(playerUuid) || key.listener().equals(playerUuid));
    }

    public void clearCache() {
        generation.incrementAndGet();
        cache.clear();
        activeSnapshot.set(null);
        snapshotRequests.clear();
        pending.clear();
    }

    public synchronized void stop() {
        acceptingRequests = false;
        stopWorker();
        clearCache();
        players.set(Map.of());
        worldRevisions.clear();
    }

    public int cachedPathCount() {
        return cache.size();
    }

    private void calculate(QueuedPath queued, VoxelSnapshot snapshot, SvcAddonConfig config) {
        try {
            PathResult result = pathfinder.search(snapshot, config);
            if (isCurrent(queued, config)) {
                cache.put(queued.key(), result, System.nanoTime());
            }
        } catch (RuntimeException exception) {
            logger.error("Acoustic path search failed", exception);
        } finally {
            pending.remove(queued.key());
        }
    }

    private ActiveSnapshot createSnapshot(
            QueuedPath queued,
            boolean directOnly,
            SvcAddonConfig config,
            long nowNanos
    ) {
        int horizontalMargin = directOnly ? 0 : config.pathSearchMargin();
        int verticalMargin = directOnly ? 0 : config.pathVerticalMargin();
        SnapshotRequest.SnapshotPlan plan = SnapshotRequest.plan(
                queued.key(),
                queued.speaker().eyePosition(),
                queued.listener().eyePosition(),
                horizontalMargin,
                verticalMargin,
                config.pathMaxSnapshotBlocks()
        );
        if (!plan.accepted()) {
            if (isCurrent(queued, config)) {
                cache.put(queued.key(), PathResult.unavailable(directDistance(queued), plan.rejection()), nowNanos);
            }
            pending.remove(queued.key());
            return null;
        }
        return new ActiveSnapshot(
                queued,
                plan.request(),
                new byte[plan.request().volume()],
                directOnly,
                System.nanoTime()
        );
    }

    private void completeUnavailable(
            ActiveSnapshot active,
            PathStatus status,
            long nowNanos,
            SvcAddonConfig config
    ) {
        if (activeSnapshot.compareAndSet(active, null) && isCurrent(active.queued, config)) {
            cache.put(active.queued.key(), PathResult.unavailable(directDistance(active.queued), status), nowNanos);
        }
        pending.remove(active.queued.key());
    }

    private void submit(QueuedPath queued, VoxelSnapshot snapshot, SvcAddonConfig config) {
        ThreadPoolExecutor executor = worker.get();
        if (executor == null || executor.isShutdown()) {
            pending.remove(queued.key());
            return;
        }
        try {
            executor.execute(() -> calculate(queued, snapshot, config));
        } catch (RejectedExecutionException exception) {
            pending.remove(queued.key());
        }
    }

    private static void forceEndpointPassable(SnapshotRequest request, byte[] cells, GridPos endpoint) {
        int relativeX = endpoint.x() - request.minX();
        int relativeY = endpoint.y() - request.minY();
        int relativeZ = endpoint.z() - request.minZ();
        if (relativeX >= 0 && relativeX < request.sizeX()
                && relativeY >= 0 && relativeY < request.sizeY()
                && relativeZ >= 0 && relativeZ < request.sizeZ()) {
            int index = (relativeY * request.sizeZ() + relativeZ) * request.sizeX() + relativeX;
            cells[index] = 1;
        }
    }

    private static double directDistance(QueuedPath queued) {
        return queued.speaker().eyePosition().distanceTo(queued.listener().eyePosition());
    }

    private static long elapsedMicros(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000L);
    }

    private boolean isCurrent(QueuedPath queued, SvcAddonConfig config) {
        if (queued.generation() != generation.get()
                || revision(queued.key().dimension()) != queued.key().worldRevision()) {
            return false;
        }
        Map<UUID, PlayerSpatialState> positionSnapshot = players.get();
        PlayerSpatialState currentSpeaker = positionSnapshot.get(queued.key().speaker());
        PlayerSpatialState currentListener = positionSnapshot.get(queued.key().listener());
        if (currentSpeaker == null || currentListener == null
                || !currentSpeaker.dimension().equals(currentListener.dimension())) {
            return false;
        }
        PathKey currentKey = PathKey.create(
                currentSpeaker,
                currentListener,
                config.pathPositionQuantization(),
                queued.key().worldRevision()
        );
        return currentKey.equals(queued.key());
    }

    private long revision(String dimension) {
        return worldRevisions.computeIfAbsent(dimension, ignored -> new AtomicLong()).get();
    }

    static PathDecision decide(
            PathResult result,
            float packetDistance,
            SvcAddonConfig config,
            boolean cacheHit
    ) {
        if (result.status() == PathStatus.DIRECT) {
            return PathDecision.pass(result, cacheHit);
        }
        if (result.status() == PathStatus.ROUTED) {
            if (result.effectiveDistance() > packetDistance) {
                return PathDecision.cancel(result, cacheHit);
            }
            if (result.directDistance() <= 1.0E-6D || result.effectiveDistance() <= result.directDistance()) {
                return PathDecision.pass(result, cacheHit);
            }
            double adjusted = result.directDistance() * packetDistance / result.effectiveDistance();
            float replacement = (float) Math.clamp(adjusted, 0.01D, packetDistance);
            if (replacement + 1.0E-4F >= packetDistance) {
                return PathDecision.pass(result, cacheHit);
            }
            return PathDecision.replace(replacement, result, cacheHit);
        }
        if (result.status() == PathStatus.NO_PATH) {
            if (config.noPathCancelsAudio()) {
                return PathDecision.cancel(result, cacheHit);
            }
            double direct = result.directDistance();
            double weakRange = direct / (1.0D - config.blockedAttenuationFactor());
            float replacement = (float) Math.min(packetDistance, weakRange);
            if (direct <= 1.0E-6D || replacement <= direct + 1.0E-4D) {
                return PathDecision.cancel(result, cacheHit);
            }
            return PathDecision.replace(replacement, result, cacheHit);
        }
        return PathDecision.pass(result, cacheHit);
    }

    private void stopWorker() {
        ThreadPoolExecutor executor = worker.getAndSet(null);
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) {
                    logger.warn("Acoustic path worker did not stop within two seconds");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record QueuedPath(
            PathKey key,
            PlayerSpatialState speaker,
            PlayerSpatialState listener,
            long generation
    ) {
    }

    private static final class ActiveSnapshot {
        private final QueuedPath queued;
        private final SnapshotRequest request;
        private final byte[] passable;
        private final boolean directOnly;
        private final long startedNanos;
        private int nextIndex;

        private ActiveSnapshot(
                QueuedPath queued,
                SnapshotRequest request,
                byte[] passable,
                boolean directOnly,
                long startedNanos
        ) {
            this.queued = queued;
            this.request = request;
            this.passable = passable;
            this.directOnly = directOnly;
            this.startedNanos = startedNanos;
        }
    }
}
