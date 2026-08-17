package datura.svcaddon.audio;

import datura.svcaddon.AddonLogger;
import datura.svcaddon.config.SvcAddonConfig;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class AudioEngine {
    private static final short[] SILENCE_FRAME = new short[960];
    private static final long ERROR_LOG_INTERVAL_NANOS = 10_000_000_000L;

    private final AddonLogger logger;
    private final Map<UUID, DecoderSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastErrorLogNanos = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private volatile VoicechatApi voicechatApi;

    public AudioEngine(AddonLogger logger) {
        this.logger = logger;
    }

    public void setVoicechatApi(VoicechatApi voicechatApi) {
        lifecycleLock.writeLock().lock();
        try {
            this.voicechatApi = voicechatApi;
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    public VolumeMeasurement process(UUID playerUuid, byte[] opusData, long nowNanos, SvcAddonConfig config) {
        lifecycleLock.readLock().lock();
        try {
            if (!config.dynamicRangeEnabled()) {
                return null;
            }
            VoicechatApi api = voicechatApi;
            if (api == null) {
                return null;
            }

            DecoderSession session;
            try {
                session = sessions.computeIfAbsent(playerUuid, ignored ->
                        new DecoderSession(api.createDecoder(), new SpeakerAudioState(config)));
            } catch (RuntimeException | LinkageError exception) {
                logDecodeError(playerUuid, nowNanos, "Could not create an Opus decoder for player ", exception);
                return null;
            }
            return session.process(opusData, nowNanos, config, this, playerUuid);
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public VolumeMeasurement latest(UUID playerUuid) {
        DecoderSession session = sessions.get(playerUuid);
        return session == null ? null : session.audioState.latest();
    }

    public void remove(UUID playerUuid) {
        DecoderSession session = sessions.remove(playerUuid);
        if (session != null) {
            try {
                session.close();
            } catch (RuntimeException | LinkageError exception) {
                logger.error("Could not close the Opus decoder for player " + playerUuid, exception);
            }
        }
        lastErrorLogNanos.remove(playerUuid);
    }

    public void closeAll() {
        lifecycleLock.writeLock().lock();
        try {
            voicechatApi = null;
            sessions.forEach((playerUuid, session) -> {
                try {
                    session.close();
                } catch (RuntimeException | LinkageError exception) {
                    logger.error("Could not close the Opus decoder for player " + playerUuid, exception);
                }
            });
            sessions.clear();
            lastErrorLogNanos.clear();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private void logDecodeError(UUID playerUuid, long nowNanos, String prefix, Throwable exception) {
        Long previous = lastErrorLogNanos.putIfAbsent(playerUuid, nowNanos);
        if (previous == null || (nowNanos - previous >= ERROR_LOG_INTERVAL_NANOS
                && lastErrorLogNanos.replace(playerUuid, previous, nowNanos))) {
            logger.error(prefix + playerUuid + " (further errors are rate-limited)", exception);
        }
    }

    private static final class DecoderSession {
        private final OpusDecoder decoder;
        private final SpeakerAudioState audioState;
        private long lastPacketNanos;

        private DecoderSession(OpusDecoder decoder, SpeakerAudioState audioState) {
            this.decoder = decoder;
            this.audioState = audioState;
        }

        private synchronized VolumeMeasurement process(
                byte[] opusData,
                long nowNanos,
                SvcAddonConfig config,
                AudioEngine engine,
                UUID playerUuid
        ) {
            if (lastPacketNanos != 0L
                    && nowNanos - lastPacketNanos > config.decoderResetGapMs() * 1_000_000L) {
                try {
                    decoder.resetState();
                } catch (RuntimeException exception) {
                    engine.logDecodeError(
                            playerUuid,
                            nowNanos,
                            "Could not reset an interrupted Opus stream for player ",
                            exception
                    );
                }
                audioState.reset(nowNanos);
            }
            lastPacketNanos = nowNanos;

            if (opusData == null || opusData.length == 0) {
                return audioState.accept(SILENCE_FRAME, nowNanos, config);
            }
            try {
                short[] pcm = decoder.decode(opusData);
                if (pcm == null || pcm.length == 0) {
                    return audioState.accept(SILENCE_FRAME, nowNanos, config);
                }
                return audioState.accept(pcm, nowNanos, config);
            } catch (RuntimeException | LinkageError exception) {
                try {
                    decoder.resetState();
                } catch (RuntimeException ignored) {
                    // The original decode error is the useful failure to report.
                }
                audioState.reset(nowNanos);
                engine.logDecodeError(
                        playerUuid,
                        nowNanos,
                        "Could not decode a Simple Voice Chat packet for player ",
                        exception
                );
                return audioState.latest();
            }
        }

        private synchronized void close() {
            if (!decoder.isClosed()) {
                decoder.close();
            }
        }
    }
}
