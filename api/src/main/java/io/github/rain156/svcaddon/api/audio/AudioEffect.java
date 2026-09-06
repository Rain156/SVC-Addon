package io.github.rain156.svcaddon.api.audio;

/** A stateful effect owned by one voice stream. Samples are mono PCM16 at 48 kHz. */
@FunctionalInterface
public interface AudioEffect {
    /** Mutates the supplied frame; implementations must not retain the array or block. */
    void process(short[] samples);
}
