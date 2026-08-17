package datura.svcaddon.audio;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RmsWindowTest {
    @Test
    void keepsOnlyTheConfiguredShortTimeWindow() {
        RmsWindow window = new RmsWindow(20);
        short[] loud = new short[960];
        Arrays.fill(loud, (short) 16_384);

        assertEquals(0.5D, window.add(loud), 1.0E-12D);
        assertEquals(0.0D, window.add(new short[960]), 1.0E-12D);
    }

    @Test
    void emptyFramesDoNotCreateInvalidEnergy() {
        RmsWindow window = new RmsWindow(100);

        assertEquals(0.0D, window.add(null));
        assertEquals(0.0D, window.add(new short[0]));
    }
}
