package io.github.rain156.svcaddon.api.radio;

/** Sixteen color channels, stored independently of a particular loader's dye enum. */
public record Frequency(int channel) {
    public Frequency {
        if (channel < 0 || channel > 15) {
            throw new IllegalArgumentException("Channel must be between 0 and 15");
        }
    }
}
