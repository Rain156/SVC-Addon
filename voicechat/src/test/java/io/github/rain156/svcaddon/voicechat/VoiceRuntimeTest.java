package io.github.rain156.svcaddon.voicechat;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import io.github.rain156.svcaddon.api.spatial.PlayerSnapshot;
import io.github.rain156.svcaddon.api.spatial.Position;
import io.github.rain156.svcaddon.core.audio.EffectPreset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoiceRuntimeTest {
    @TempDir Path temp;
    private VoiceRuntime runtime;
    private final UUID speakerId = UUID.randomUUID();
    private final UUID listenerId = UUID.randomUUID();
    private VoicechatServerApi api;
    private VoicechatConnection speaker;
    private VoicechatConnection listener;
    private OpusDecoder decoder;

    @BeforeEach void setup() throws Exception {
        runtime = new VoiceRuntime(temp.resolve("server.properties"), temp.resolve("players.properties"));
        runtime.publish(Map.of(speakerId, snapshot(speakerId, 0), listenerId, snapshot(listenerId, 10)));
        api = mock(VoicechatServerApi.class);
        decoder = mock(OpusDecoder.class);
        when(api.createDecoder()).thenReturn(decoder);
        when(decoder.decode(any(byte[].class))).thenReturn(new short[960]);
        speaker = connection(speakerId);
        listener = connection(listenerId);
        when(api.getConnectionOf(listenerId)).thenReturn(listener);
    }

    @AfterEach void cleanup() { runtime.close(); }

    @Test void mutedMicrophoneIsRejectedBeforeAnyDecode() throws Exception {
        runtime.players().update(List.of(speakerId), state -> state.withTransmit(0.0).withBroadcast(true));
        var event = microphone();
        runtime.microphone(event);
        verify(event).cancel();
        verify(api, never()).createDecoder();
    }

    @Test void unchangedProximityAudioPassesThroughAndDecodesOnlyOnce() {
        var event = microphone();
        byte[] opus = event.getPacket().getOpusEncodedData();
        runtime.microphone(event);
        verify(decoder, times(1)).decode(opus);
        verify(event.getPacket()).setOpusEncodedData(same(opus));
        verify(event, never()).cancel();
    }

    @Test void distanceEventCanExpandBeyondSvcDefaultWithoutReplacingItsRouting() throws Exception {
        runtime.players().update(List.of(speakerId), state -> state.withTransmit(128.0));
        var event = mock(VoiceDistanceEvent.class);
        when(event.getSenderConnection()).thenReturn(speaker);
        when(event.getPacket()).thenReturn(mock(MicrophonePacket.class));
        runtime.distance(event);
        verify(event).setDistance(128f);
    }

    @Test void effectsUseOneEncoderPerSpeakerAndReleaseCodecs() throws Exception {
        runtime.players().update(List.of(speakerId), state -> state.withEffect(EffectPreset.RADIO));
        var encoder = mock(OpusEncoder.class);
        when(api.createEncoder()).thenReturn(encoder);
        when(encoder.encode(any())).thenReturn(new byte[]{2});
        runtime.microphone(microphone());
        runtime.microphone(microphone());
        verify(api, times(1)).createEncoder();
        verify(encoder, times(2)).encode(argThat(data -> data.length == 960));
        runtime.disconnect(speakerId);
        verify(decoder).close();
        verify(encoder).close();
        runtime.close();
        verify(decoder, times(1)).close();
    }

    @Test void invalidFrameIsDropped() {
        when(decoder.decode(any(byte[].class))).thenReturn(new short[100]);
        var event = microphone();
        runtime.microphone(event);
        verify(event).cancel();
    }

    @Test void privateGroupSpeechIsNeverPromotedToBroadcast() throws Exception {
        runtime.players().update(List.of(speakerId), state -> state.withBroadcast(true));
        when(speaker.isInGroup()).thenReturn(true);
        var event = microphone();
        runtime.microphone(event);
        verify(event, never()).cancel();
        verify(api, never()).sendStaticSoundPacketTo(any(), any());
    }

    @Test void broadcastUsesDedicatedChannelAndHonorsIsolatedGroups() throws Exception {
        runtime.players().update(List.of(speakerId), state -> state.withBroadcast(true));
        var event = microphone();
        var microphonePacket = event.getPacket();
        StaticSoundPacket.Builder<?> builder = mock(StaticSoundPacket.Builder.class, RETURNS_SELF);
        var packet = mock(StaticSoundPacket.class);
        doReturn(builder).when(microphonePacket).staticSoundPacketBuilder();
        when(builder.build()).thenReturn(packet);
        runtime.microphone(event);
        verify(event).cancel();
        verify(builder).channelId(VoiceRuntime.broadcastId(speakerId));
        verify(api).sendStaticSoundPacketTo(listener, packet);
        var isolated = mock(Group.class);
        when(isolated.getType()).thenReturn(Group.Type.ISOLATED);
        when(listener.getGroup()).thenReturn(isolated);
        clearInvocations(api);
        runtime.microphone(event);
        verify(api, never()).sendStaticSoundPacketTo(any(), any());
    }

    @Test void deafenAppliesToGroupAndPluginSound() throws Exception {
        runtime.players().update(List.of(listenerId), state -> state.withReceive(0.0));
        for (String source : List.of("group", "plugin")) {
            var event = outgoing();
            when(event.getSource()).thenReturn(source);
            runtime.outgoing(event);
            verify(event).cancel();
        }
    }

    @Test void receiveDistanceAndMuteAlsoApplyToExistingOutgoingStreams() throws Exception {
        runtime.players().update(List.of(listenerId), state -> state.withReceive(5.0));
        var event = outgoing();
        runtime.outgoing(event);
        verify(event).cancel();
        runtime.players().update(List.of(listenerId), state -> state.withReceive(null));
        runtime.players().update(List.of(speakerId), state -> state.withTransmit(0.0));
        var muted = outgoing();
        runtime.outgoing(muted);
        verify(muted).cancel();
    }

    @Test void failedReloadKeepsEffectiveSettings() throws Exception {
        var old = runtime.config();
        Files.writeString(temp.resolve("server.properties"), "range.maximum=NaN\n");
        assertThrows(IllegalArgumentException.class, runtime::reload);
        assertSame(old, runtime.config());
    }

    @Test void voiceApiCanStartBeforeGameAdapterAndWorldSwitchClearsState() throws Exception {
        RuntimeHost.voiceStarted(api);
        RuntimeHost.start(temp.resolve("config"), temp.resolve("world"));
        try {
            assertSame(api, RuntimeHost.voiceApi());
            assertNotNull(RuntimeHost.current());
        } finally { RuntimeHost.stop(); }
        assertNull(RuntimeHost.voiceApi());
        assertNull(RuntimeHost.current());
    }

    private MicrophonePacketEvent microphone() {
        var event = mock(MicrophonePacketEvent.class);
        var packet = mock(MicrophonePacket.class);
        when(event.getSenderConnection()).thenReturn(speaker);
        when(event.getVoicechat()).thenReturn(api);
        when(event.getPacket()).thenReturn(packet);
        when(packet.getOpusEncodedData()).thenReturn(new byte[]{1});
        return event;
    }

    private StaticSoundPacketEvent outgoing() {
        var event = mock(StaticSoundPacketEvent.class);
        var packet = mock(StaticSoundPacket.class);
        when(event.getReceiverConnection()).thenReturn(listener);
        when(event.getPacket()).thenReturn(packet);
        when(packet.getSender()).thenReturn(speakerId);
        return event;
    }

    private static VoicechatConnection connection(UUID id) {
        var result = mock(VoicechatConnection.class);
        var player = mock(ServerPlayer.class);
        when(result.getPlayer()).thenReturn(player);
        when(result.isConnected()).thenReturn(true);
        when(player.getUuid()).thenReturn(id);
        return result;
    }

    private static PlayerSnapshot snapshot(UUID id, double x) {
        return new PlayerSnapshot(id, "overworld", new Position(x, 64, 0), false, false);
    }
}
