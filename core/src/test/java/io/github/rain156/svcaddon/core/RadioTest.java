package io.github.rain156.svcaddon.core;

import io.github.rain156.svcaddon.api.radio.Frequency;
import io.github.rain156.svcaddon.core.radio.RadioArbiter;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RadioTest {
    @Test void leaseCanRenewReleaseAndRecoverFromMissingKeyUp() {
        var arbiter = new RadioArbiter(500);
        var channel = new Frequency(1);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(arbiter.acquire(channel, a, 0));
        assertFalse(arbiter.acquire(channel, b, 200));
        assertTrue(arbiter.acquire(channel, a, 400));
        assertFalse(arbiter.acquire(channel, b, 899));
        assertTrue(arbiter.acquire(channel, b, 900));
        arbiter.release(b);
        assertTrue(arbiter.acquire(channel, a, 901));
        assertTrue(arbiter.acquire(new Frequency(2), b, 901));
    }

    @Test void simultaneousPushToTalkHasExactlyOneWinner() throws Exception {
        var arbiter = new RadioArbiter(500);
        var ready = new CountDownLatch(16);
        var start = new CountDownLatch(1);
        var winners = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 16; i++) pool.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                if (arbiter.acquire(new Frequency(0), UUID.randomUUID(), 0)) winners.incrementAndGet();
            });
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady);
        }
        assertEquals(1, winners.get());
    }

    @Test void frequencyDomainIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new Frequency(-1));
        assertThrows(IllegalArgumentException.class, () -> new Frequency(16));
    }
}
