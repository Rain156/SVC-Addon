package io.github.rain156.svcaddon.core.audio;

import io.github.rain156.svcaddon.api.audio.AudioEffect;

public enum EffectPreset {
    NONE, RADIO, ROBOT;

    /** Returns fresh state for each speaker. No codec or filter state is shared between players. */
    public AudioEffect create() {
        return switch (this) {
            case NONE -> samples -> { };
            case RADIO -> new RadioEffect();
            case ROBOT -> new RobotEffect();
        };
    }

    private static final class RadioEffect implements AudioEffect {
        private double previousInput;
        private double highPass;
        private double lowPass;

        @Override
        public void process(short[] samples) {
            // One-pole 300 Hz high-pass followed by a 3 kHz low-pass and gentle saturation.
            double highAlpha = Math.exp(-2 * Math.PI * 300 / 48_000);
            double lowAlpha = 1 - Math.exp(-2 * Math.PI * 3_000 / 48_000);
            for (int i = 0; i < samples.length; i++) {
                double input = samples[i] / 32768.0;
                highPass = highAlpha * (highPass + input - previousInput);
                previousInput = input;
                lowPass += lowAlpha * (highPass - lowPass);
                samples[i] = clamp(Math.tanh(lowPass * 2) * 24_000);
            }
        }
    }

    private static final class RobotEffect implements AudioEffect {
        private double phase;

        @Override
        public void process(short[] samples) {
            for (int i = 0; i < samples.length; i++) {
                samples[i] = clamp(samples[i] * (0.5 + 0.5 * Math.sin(phase)));
                phase = (phase + 2 * Math.PI * 70 / 48_000) % (2 * Math.PI);
            }
        }
    }

    private static short clamp(double value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value)));
    }
}
