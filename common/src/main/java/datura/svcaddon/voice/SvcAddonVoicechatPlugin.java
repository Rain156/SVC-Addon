package datura.svcaddon.voice;

import datura.svcaddon.SvcAddon;
import datura.svcaddon.SvcAddonBootstrap;
import datura.svcaddon.SvcAddonRuntime;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

@ForgeVoicechatPlugin
public final class SvcAddonVoicechatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return SvcAddon.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        SvcAddonRuntime runtime = SvcAddonBootstrap.runtime();
        if (runtime != null) {
            runtime.initializeVoicechat(api);
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            SvcAddonRuntime runtime = SvcAddonBootstrap.runtime();
            if (runtime == null || event.getSenderConnection() == null) {
                return;
            }
            runtime.onMicrophonePacket(
                    event.getSenderConnection().getPlayer().getUuid(),
                    event.getPacket().getOpusEncodedData(),
                    System.nanoTime()
            );
        });
        registration.registerEvent(VoiceDistanceEvent.class, event -> {
            SvcAddonRuntime runtime = SvcAddonBootstrap.runtime();
            if (runtime != null) {
                runtime.applyVoiceDistance(event.getSenderConnection().getPlayer().getUuid(), event);
            }
        });
        registration.registerEvent(EntitySoundPacketEvent.class, event -> {
            SvcAddonRuntime runtime = SvcAddonBootstrap.runtime();
            if (runtime != null) {
                runtime.handleEntitySoundPacket(event, System.nanoTime());
            }
        });
        registration.registerEvent(PlayerDisconnectedEvent.class, event -> {
            SvcAddonRuntime runtime = SvcAddonBootstrap.runtime();
            if (runtime != null) {
                runtime.removePlayer(event.getPlayerUuid());
            }
        });
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            SvcAddonRuntime runtime = SvcAddonBootstrap.runtime();
            if (runtime != null) {
                runtime.onVoiceServerStarted(event.getVoicechat());
            }
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            SvcAddonRuntime runtime = SvcAddonBootstrap.runtime();
            if (runtime != null) {
                runtime.shutdown();
            }
        });
    }
}
