package io.github.rain156.svcaddon.core.trigger;

import io.github.rain156.svcaddon.core.config.VoiceConfig;

/** One gate per speaker. Uses monotonic milliseconds; each crossing can fire at most once. */
public final class ThresholdGate {
    private long aboveSince = Long.MIN_VALUE;
    private long lastFired = Long.MIN_VALUE;
    private boolean armed = true;

    public boolean update(double dbfs, long nowMillis, VoiceConfig.Threshold config) {
        if (!config.enabled()) { aboveSince = Long.MIN_VALUE; armed = true; return false; }
        if (dbfs < config.dbfs()) {
            aboveSince = Long.MIN_VALUE;
            if (dbfs <= config.dbfs() - 3) armed = true;
            return false;
        }
        if (aboveSince == Long.MIN_VALUE) aboveSince = nowMillis;
        if (!armed || nowMillis - aboveSince < config.holdMillis()) return false;
        if (lastFired != Long.MIN_VALUE && nowMillis - lastFired < config.cooldownMillis()) return false;
        lastFired = nowMillis;
        armed = false;
        return true;
    }
}
