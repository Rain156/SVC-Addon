package datura.svcaddon;

import datura.svcaddon.audio.AudioEngine;
import datura.svcaddon.audio.VolumeMeasurement;
import datura.svcaddon.config.ConfigManager;
import datura.svcaddon.config.PlayerPathPreference;
import datura.svcaddon.config.PlayerPreferenceStore;
import datura.svcaddon.config.SvcAddonConfig;
import datura.svcaddon.path.PathAction;
import datura.svcaddon.path.PathDecision;
import datura.svcaddon.path.PathManager;
import datura.svcaddon.path.PlayerSpatialState;
import datura.svcaddon.path.SnapshotProvider;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SvcAddonRuntime {
    private final AddonLogger logger;
    private final ConfigManager configManager;
    private final PlayerPreferenceStore preferenceStore;
    private final AudioEngine audioEngine;
    private final PathManager pathManager;
    private final Map<String, Long> debugLogTimes = new ConcurrentHashMap<>();
    private volatile VoicechatServerApi serverApi;

    SvcAddonRuntime(Path configDirectory, AddonLogger logger) {
        this.logger = logger;
        configManager = new ConfigManager(configDirectory, logger);
        preferenceStore = new PlayerPreferenceStore(configDirectory, logger);
        audioEngine = new AudioEngine(logger);
        pathManager = new PathManager(logger);
        configManager.load();
        preferenceStore.load();
        logger.info("SVC Addon initialized; configuration file: " + configDirectory.resolve(ConfigManager.FILE_NAME));
    }

    public SvcAddonConfig config() {
        return configManager.get();
    }

    public void initializeVoicechat(VoicechatApi api) {
        audioEngine.setVoicechatApi(api);
        if (api instanceof VoicechatServerApi voicechatServerApi) {
            serverApi = voicechatServerApi;
        }
    }

    public void onVoiceServerStarted(VoicechatServerApi api) {
        serverApi = api;
        audioEngine.setVoicechatApi(api);
        pathManager.start(config());
        double broadcastRange = api.getBroadcastRange();
        if (config().maxDistance() > broadcastRange) {
            logger.warn("Configured max_distance=" + format(config().maxDistance())
                    + " exceeds Simple Voice Chat broadcast_range=" + format(broadcastRange)
                    + "; listeners outside broadcast_range will never receive the packet");
        }
        logger.info("SVC Addon voice integration started (broadcast_range=" + format(broadcastRange) + ")");
    }

    public void onMicrophonePacket(UUID playerUuid, byte[] opusData, long nowNanos) {
        if (pathManager.isSpectator(playerUuid)) {
            return;
        }
        VolumeMeasurement measurement = audioEngine.process(playerUuid, opusData, nowNanos, config());
        if (measurement != null && config().debugEnabled() && shouldDebug("audio:" + playerUuid, nowNanos)) {
            logger.debug("audio player=" + playerUuid
                    + " raw_dbfs=" + format(measurement.rawDbfs())
                    + " smoothed_dbfs=" + format(measurement.smoothedDbfs())
                    + " distance=" + format(measurement.distance())
                    + " gate=" + (measurement.gateOpen() ? "open" : "closed")
                    + " clipped=" + format(measurement.clippedFraction() * 100.0D) + "%");
        }
    }

    public void applyVoiceDistance(UUID playerUuid, VoiceDistanceEvent event) {
        SvcAddonConfig config = config();
        if (!config.dynamicRangeEnabled() || pathManager.isSpectator(playerUuid)) {
            return;
        }
        VolumeMeasurement measurement = audioEngine.latest(playerUuid);
        if (measurement == null) {
            return;
        }

        double target = measurement.distance();
        if (event.getPacket().isWhispering()) {
            target = Math.min(event.getDistance(), target * config.whisperDistanceScale());
            target = Math.min(target, config.whisperMaxDistance());
        }
        target = Math.clamp(target, 0.0D, config.maxDistance());
        event.setDistance((float) target);
    }

    public void handleEntitySoundPacket(EntitySoundPacketEvent event, long nowNanos) {
        if (!SoundPacketEvent.SOURCE_PROXIMITY.equals(event.getSource())) {
            return;
        }
        VoicechatConnection sender = event.getSenderConnection();
        VoicechatConnection receiver = event.getReceiverConnection();
        if (sender == null || receiver == null) {
            return;
        }
        UUID speakerUuid = sender.getPlayer().getUuid();
        UUID listenerUuid = receiver.getPlayer().getUuid();
        if (pathManager.isSpectator(speakerUuid)
                || pathManager.isSpectator(listenerUuid)
                || !pathTracingAppliesTo(listenerUuid)) {
            return;
        }

        EntitySoundPacket packet = event.getPacket();
        PathDecision decision = pathManager.evaluate(
                speakerUuid,
                listenerUuid,
                packet.getDistance(),
                nowNanos,
                config()
        );
        if (decision.action() == PathAction.CANCEL) {
            event.cancel();
        } else if (decision.action() == PathAction.REPLACE) {
            try {
                EntitySoundPacket replacement = packet.entitySoundPacketBuilder()
                        .distance(decision.replacementDistance())
                        .build();
                if (event.cancel()) {
                    event.getVoicechat().sendEntitySoundPacketTo(receiver, replacement);
                }
            } catch (RuntimeException exception) {
                logger.error("Could not create a path-attenuated entity sound packet", exception);
            }
        }

        if (config().debugEnabled() && shouldDebug("path:" + speakerUuid + ':' + listenerUuid, nowNanos)) {
            logger.debug("path speaker=" + speakerUuid
                    + " listener=" + listenerUuid
                    + " action=" + decision.action()
                    + " status=" + decision.result().status()
                    + " direct=" + format(decision.result().directDistance())
                    + " acoustic=" + format(decision.result().effectiveDistance())
                    + " cache_hit=" + decision.cacheHit()
                    + " nodes=" + decision.result().visitedNodes()
                    + " search_us=" + decision.result().computationMicros());
        }
    }

    public void tick(Collection<PlayerSpatialState> players, SnapshotProvider snapshotProvider, long nowNanos) {
        pathManager.updatePlayers(players);
        for (PlayerSpatialState player : players) {
            if (player.spectator()) {
                audioEngine.remove(player.playerUuid());
            }
        }
        if (config().pathTracingEnabled()) {
            pathManager.tick(snapshotProvider, config(), nowNanos);
        }
    }

    public void markWorldChanged(String dimension) {
        pathManager.invalidateDimension(dimension);
    }

    public void removePlayer(UUID playerUuid) {
        audioEngine.remove(playerUuid);
        pathManager.removePlayer(playerUuid);
        String uuid = playerUuid.toString();
        debugLogTimes.keySet().removeIf(key -> key.contains(uuid));
    }

    public CommandReply setPlayerPathPreference(UUID playerUuid, PlayerPathPreference preference) {
        SvcAddonConfig config = config();
        if (!config.allowPlayerPathTracingToggle() && preference != PlayerPathPreference.DEFAULT) {
            return CommandReply.failure("服务器不允许玩家修改个人路径追踪设置。");
        }
        try {
            preferenceStore.set(playerUuid, preference);
            pathManager.removePlayer(playerUuid);
            return CommandReply.success(switch (preference) {
                case ON -> "你的语音接收路径追踪已开启（全局关闭时仍以全局设置为准）。";
                case OFF -> "你的语音接收路径追踪已关闭。";
                case DEFAULT -> "你的语音接收路径追踪已恢复服务器默认值。";
            });
        } catch (IOException exception) {
            logger.error("Could not save player path tracing preference", exception);
            return CommandReply.failure("保存个人路径追踪设置失败，请查看服务器日志。");
        }
    }

    public CommandReply playerPathStatus(UUID playerUuid) {
        PlayerPathPreference preference = preferenceStore.get(playerUuid);
        boolean effective = pathTracingAppliesTo(playerUuid);
        return CommandReply.success("路径追踪：" + (effective ? "已生效" : "未生效")
                + "；个人设置=" + preference.serializedName()
                + "；全局=" + (config().pathTracingEnabled() ? "on" : "off") + "。");
    }

    public CommandReply setGlobalPathTracing(boolean enabled) {
        try {
            configManager.setPathTracingEnabled(enabled);
            pathManager.clearCache();
            return CommandReply.success("全局路径追踪已" + (enabled ? "开启" : "关闭") + "。");
        } catch (IOException exception) {
            logger.error("Could not save global path tracing state", exception);
            return CommandReply.failure("保存全局路径追踪设置失败，请查看服务器日志。");
        }
    }

    public CommandReply globalPathStatus() {
        return CommandReply.success("全局路径追踪=" + (config().pathTracingEnabled() ? "on" : "off")
                + "；缓存条目=" + pathManager.cachedPathCount() + "。");
    }

    public CommandReply reload() {
        configManager.load();
        preferenceStore.load();
        pathManager.clearCache();
        if (serverApi != null) {
            pathManager.start(config());
        }
        if (!config().dynamicRangeEnabled()) {
            audioEngine.closeAll();
        }
        debugLogTimes.clear();
        VoicechatServerApi api = serverApi;
        if (api != null && config().maxDistance() > api.getBroadcastRange()) {
            logger.warn("Configured max_distance=" + format(config().maxDistance())
                    + " exceeds Simple Voice Chat broadcast_range=" + format(api.getBroadcastRange()));
        }
        return CommandReply.success("SVC Addon 配置已重新加载。");
    }

    public void shutdown() {
        audioEngine.closeAll();
        pathManager.stop();
        serverApi = null;
        debugLogTimes.clear();
    }

    private boolean pathTracingAppliesTo(UUID listenerUuid) {
        if (!config().pathTracingEnabled()) {
            return false;
        }
        return preferenceStore.get(listenerUuid) != PlayerPathPreference.OFF;
    }

    private boolean shouldDebug(String key, long nowNanos) {
        long intervalNanos = config().debugLogIntervalMs() * 1_000_000L;
        Long previous = debugLogTimes.putIfAbsent(key, nowNanos);
        if (previous == null) {
            return true;
        }
        if (nowNanos - previous >= intervalNanos) {
            return debugLogTimes.replace(key, previous, nowNanos);
        }
        return false;
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
