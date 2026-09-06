package io.github.rain156.svcaddon.voicechat;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import io.github.rain156.svcaddon.api.spatial.PlayerSnapshot;
import io.github.rain156.svcaddon.api.spatial.Position;
import io.github.rain156.svcaddon.core.audio.AudioLevels;
import io.github.rain156.svcaddon.core.config.ConfigStore;
import io.github.rain156.svcaddon.core.config.PlayerSettingsStore;
import io.github.rain156.svcaddon.core.config.VoiceConfig;
import io.github.rain156.svcaddon.core.routing.VoiceRouter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class VoiceRuntime implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("svcaddon");
    private final ConfigStore configStore;
    private final PlayerSettingsStore players;
    private final Map<UUID, SpeakerSession> speakers = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<Trigger> triggers = new ArrayBlockingQueue<>(128);
    private final AtomicLong lastError = new AtomicLong(Long.MIN_VALUE);
    private volatile VoiceConfig config;
    private volatile Map<UUID, PlayerSnapshot> snapshot = Map.of();
    private volatile boolean closed;

    public VoiceRuntime(Path configPath, Path playerPath) throws IOException {
        configStore = new ConfigStore(configPath);
        config = configStore.load();
        players = new PlayerSettingsStore(playerPath);
        players.load();
    }

    public PlayerSettingsStore players() { return players; }
    public VoiceConfig config() { return config; }
    public void reload() throws IOException {
        config = configStore.load();
        triggers.clear();
    }

    public void publish(Map<UUID, PlayerSnapshot> next) {
        snapshot = Map.copyOf(next);
        for (UUID id : speakers.keySet()) if (!next.containsKey(id)) disconnect(id);
    }

    public Trigger pollTrigger() {
        Trigger result;
        while ((result = triggers.poll()) != null) {
            if (result.rule() == config.threshold()) return result;
        }
        return null;
    }

    public double level(UUID id) {
        SpeakerSession session = speakers.get(id);
        return session == null || millis() - session.lastFrameMillis > 1000 ? AudioLevels.SILENCE_DBFS : session.level;
    }

    void microphone(MicrophonePacketEvent event) {
        if (closed || event.isCancelled() || event.getSenderConnection() == null) return;
        var sender = event.getSenderConnection();
        UUID id = sender.getPlayer().getUuid();
        var settings = players.get(id);
        if (settings.muted()) { event.cancel(); return; }
        if (!snapshot.containsKey(id)) return;
        VoiceConfig current = config;
        try {
            SpeakerSession session = speakers.computeIfAbsent(id, ignored -> new SpeakerSession(event.getVoicechat()));
            synchronized (session) {
                if (closed || session.closed) { session.close(); event.cancel(); return; }
                long now = millis();
                var packet = event.getPacket();
                packet.setOpusEncodedData(session.process(packet.getOpusEncodedData(), event.getVoicechat(), settings.effect(), current, now));
                if (session.threshold.update(session.level, now, current.threshold())) {
                    if (!triggers.offer(new Trigger(id, current.threshold().command(), now, current.threshold()))) {
                        logError(now, new IllegalStateException("Threshold queue full; command dropped"));
                    }
                }
                // SVC retains control of private/open groups and spectator possession.
                PlayerSnapshot source = snapshot.get(id);
                if (source != null && settings.broadcast() && !sender.isInGroup() && !source.spectator()) {
                    event.cancel();
                    var broadcast = packet.staticSoundPacketBuilder().channelId(broadcastId(id)).build();
                    for (PlayerSnapshot listener : snapshot.values()) {
                        if (!VoiceRouter.canHear(source, listener, players.get(listener.id()), 1, true, current.crossDimensionBroadcast())) continue;
                        var connection = event.getVoicechat().getConnectionOf(listener.id());
                        if (connection == null || !connection.isConnected() || connection.isDisabled()) continue;
                        if (connection.getGroup() != null && connection.getGroup().getType() == Group.Type.ISOLATED) continue;
                        event.getVoicechat().sendStaticSoundPacketTo(connection, broadcast);
                    }
                }
            }
        } catch (RuntimeException e) {
            event.cancel();
            logError(millis(), e);
        }
    }

    void distance(VoiceDistanceEvent event) {
        if (closed || event.getSenderConnection() == null) return;
        UUID id = event.getSenderConnection().getPlayer().getUuid();
        PlayerSnapshot player = snapshot.get(id);
        if (player == null) return;
        event.setDistance((float) VoiceRouter.transmitRange(config, players.get(id), level(id), player.crouching(), event.getPacket().isWhispering()));
    }

    void outgoing(SoundPacketEvent<? extends SoundPacket> event) {
        if (closed || event.isCancelled() || event.getReceiverConnection() == null) return;
        UUID listenerId = event.getReceiverConnection().getPlayer().getUuid();
        var receiver = players.get(listenerId);
        var packet = event.getPacket();
        if (receiver.deafened() || players.get(packet.getSender()).muted()) { event.cancel(); return; }
        if (receiver.receiveRange() == null) return;
        PlayerSnapshot listener = snapshot.get(listenerId);
        PlayerSnapshot sender = snapshot.get(packet.getSender());
        if (listener == null) { event.cancel(); return; }
        double distanceSquared;
        if (packet instanceof LocationalSoundPacket locational) {
            var pos = locational.getPosition();
            distanceSquared = listener.position().distanceSquared(new Position(pos.getX(), pos.getY(), pos.getZ()));
        } else if (sender != null && sender.dimension().equals(listener.dimension())) {
            distanceSquared = listener.position().distanceSquared(sender.position());
        } else {
            event.cancel();
            return;
        }
        if (distanceSquared >= receiver.receiveRange() * receiver.receiveRange()) event.cancel();
    }

    public void disconnect(UUID id) {
        SpeakerSession session = speakers.remove(id);
        if (session != null) session.close();
    }

    @Override public void close() {
        closed = true;
        resetVoice();
        snapshot = Map.of();
    }

    void resetVoice() {
        speakers.values().forEach(SpeakerSession::close);
        speakers.clear();
        triggers.clear();
    }

    private void logError(long now, RuntimeException error) {
        long previous = lastError.get();
        if ((previous == Long.MIN_VALUE || now - previous > 5000) && lastError.compareAndSet(previous, now)) {
            LOG.log(System.Logger.Level.WARNING, "Voice frame or trigger rejected", error);
        }
    }

    static UUID broadcastId(UUID player) {
        return UUID.nameUUIDFromBytes(("svcaddon:broadcast:" + player).getBytes(StandardCharsets.UTF_8));
    }

    public static long millis() { return System.nanoTime() / 1_000_000; }
    public record Trigger(UUID player, String command, long createdMillis, VoiceConfig.Threshold rule) { }
}
