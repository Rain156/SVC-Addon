package io.github.rain156.svcaddon.voicechat;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import java.util.function.Consumer;

public class SvcVoicechatPlugin implements VoicechatPlugin {
    @Override public String getPluginId() { return "svcaddon"; }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> RuntimeHost.voiceStarted(e.getVoicechat()));
        registration.registerEvent(VoicechatServerStoppedEvent.class, e -> RuntimeHost.voiceStopped());
        registration.registerEvent(PlayerDisconnectedEvent.class, e -> withRuntime(r -> r.disconnect(e.getPlayerUuid())));
        registration.registerEvent(MicrophonePacketEvent.class, e -> withRuntime(r -> r.microphone(e)), -100);
        registration.registerEvent(VoiceDistanceEvent.class, e -> withRuntime(r -> r.distance(e)), -100);
        registration.registerEvent(EntitySoundPacketEvent.class, e -> withRuntime(r -> r.outgoing(e)), -100);
        registration.registerEvent(StaticSoundPacketEvent.class, e -> withRuntime(r -> r.outgoing(e)), -100);
        registration.registerEvent(LocationalSoundPacketEvent.class, e -> withRuntime(r -> r.outgoing(e)), -100);
    }

    private static void withRuntime(Consumer<VoiceRuntime> action) {
        VoiceRuntime runtime = RuntimeHost.current();
        if (runtime != null) action.accept(runtime);
    }
}
