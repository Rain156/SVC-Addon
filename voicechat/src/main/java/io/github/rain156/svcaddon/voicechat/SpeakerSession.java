package io.github.rain156.svcaddon.voicechat;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import io.github.rain156.svcaddon.api.audio.AudioEffect;
import io.github.rain156.svcaddon.core.audio.AudioLevels;
import io.github.rain156.svcaddon.core.audio.EffectPreset;
import io.github.rain156.svcaddon.core.audio.LevelEnvelope;
import io.github.rain156.svcaddon.core.config.VoiceConfig;
import io.github.rain156.svcaddon.core.trigger.ThresholdGate;

/** All access to codecs/effect/gate is synchronized on this session by VoiceRuntime. */
final class SpeakerSession implements AutoCloseable {
    private final OpusDecoder decoder;
    private OpusEncoder encoder;
    private EffectPreset preset = EffectPreset.NONE;
    private AudioEffect effect = EffectPreset.NONE.create();
    private LevelEnvelope envelope = new LevelEnvelope();
    final ThresholdGate threshold = new ThresholdGate();
    volatile double level = AudioLevels.SILENCE_DBFS;
    volatile long lastFrameMillis;
    boolean closed;

    SpeakerSession(VoicechatServerApi api) { decoder = api.createDecoder(); }

    byte[] process(byte[] opus, VoicechatServerApi api, EffectPreset requested, VoiceConfig config, long now) {
        if (lastFrameMillis != 0 && now - lastFrameMillis > 1000) {
            resetAudio();
            threshold.update(AudioLevels.SILENCE_DBFS, now, config.threshold());
        }
        lastFrameMillis = now;
        if (opus.length == 0) {
            resetAudio();
            return opus;
        }
        short[] samples = decoder.decode(opus);
        if (samples.length != 960) throw new IllegalArgumentException("Expected a 20 ms mono voice frame");
        level = envelope.update(AudioLevels.dbfs(samples), samples.length, config.attackMillis(), config.releaseMillis());
        if (requested != preset) {
            preset = requested;
            effect = requested.create();
            if (encoder != null) encoder.resetState();
        }
        if (requested == EffectPreset.NONE) return opus;
        if (encoder == null) encoder = api.createEncoder();
        effect.process(samples);
        return encoder.encode(samples);
    }

    private void resetAudio() {
        decoder.resetState();
        if (encoder != null) encoder.resetState();
        envelope = new LevelEnvelope();
        level = AudioLevels.SILENCE_DBFS;
        effect = preset.create();
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { decoder.close(); } finally { if (encoder != null) encoder.close(); }
    }
}
