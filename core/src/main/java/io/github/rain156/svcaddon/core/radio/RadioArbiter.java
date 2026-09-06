package io.github.rain156.svcaddon.core.radio;

import io.github.rain156.svcaddon.api.radio.Frequency;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Half-duplex channel ownership. Expiring leases recover from a lost key-up/disconnect packet. */
public final class RadioArbiter {
    private final Map<Frequency, Lease> leases = new HashMap<>();
    private final long leaseMillis;

    public RadioArbiter(long leaseMillis) {
        if (leaseMillis < 20 || leaseMillis > 10_000) throw new IllegalArgumentException("Invalid radio lease duration");
        this.leaseMillis = leaseMillis;
    }

    public synchronized boolean acquire(Frequency frequency, UUID speaker, long nowMillis) {
        Objects.requireNonNull(frequency);
        Objects.requireNonNull(speaker);
        Lease current = leases.get(frequency);
        if (current != null && nowMillis - current.renewedAt() < leaseMillis && !current.speaker().equals(speaker)) return false;
        leases.put(frequency, new Lease(speaker, nowMillis));
        return true;
    }

    public synchronized void release(UUID speaker) {
        leases.values().removeIf(lease -> lease.speaker().equals(speaker));
    }

    private record Lease(UUID speaker, long renewedAt) { }
}
