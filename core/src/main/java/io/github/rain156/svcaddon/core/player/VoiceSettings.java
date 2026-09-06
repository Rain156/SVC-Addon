package io.github.rain156.svcaddon.core.player;

import io.github.rain156.svcaddon.core.audio.EffectPreset;
import io.github.rain156.svcaddon.core.config.VoiceConfig;
import java.util.Objects;

/** Null ranges inherit server defaults. Zero is an explicit mute/deafen. */
public record VoiceSettings(Double transmitRange, Double receiveRange, boolean broadcast, EffectPreset effect) {
    public static final VoiceSettings DEFAULT = new VoiceSettings(null, null, false, EffectPreset.NONE);

    public VoiceSettings {
        if (transmitRange != null) VoiceConfig.range(transmitRange, 0, 1024, "transmitRange");
        if (receiveRange != null) VoiceConfig.range(receiveRange, 0, 1024, "receiveRange");
        Objects.requireNonNull(effect);
    }

    public boolean muted() { return transmitRange != null && transmitRange == 0; }
    public boolean deafened() { return receiveRange != null && receiveRange == 0; }
    public VoiceSettings withTransmit(Double range) { return new VoiceSettings(range, receiveRange, broadcast, effect); }
    public VoiceSettings withReceive(Double range) { return new VoiceSettings(transmitRange, range, broadcast, effect); }
    public VoiceSettings withBroadcast(boolean enabled) { return new VoiceSettings(transmitRange, receiveRange, enabled, effect); }
    public VoiceSettings withEffect(EffectPreset preset) { return new VoiceSettings(transmitRange, receiveRange, broadcast, preset); }
}
