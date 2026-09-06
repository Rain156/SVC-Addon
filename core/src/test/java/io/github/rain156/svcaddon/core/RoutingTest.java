package io.github.rain156.svcaddon.core;

import io.github.rain156.svcaddon.api.spatial.PlayerSnapshot;
import io.github.rain156.svcaddon.api.spatial.Position;
import io.github.rain156.svcaddon.core.config.VoiceConfig;
import io.github.rain156.svcaddon.core.player.VoiceSettings;
import io.github.rain156.svcaddon.core.routing.VoiceRouter;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RoutingTest {
    private final VoiceConfig config = VoiceConfig.defaults();
    private final PlayerSnapshot speaker = player("overworld", 0, false);
    private final PlayerSnapshot listener = player("overworld", 10, false);

    @Test void dynamicRangeClampsAndInterpolates() {
        assertEquals(4, range(-96, false, false, VoiceSettings.DEFAULT));
        assertEquals(48, range(0, false, false, VoiceSettings.DEFAULT));
        assertEquals(26, range(-30, false, false, VoiceSettings.DEFAULT));
    }

    @Test void zeroTransmitAlwaysMutesAndCrouchCapsOverrides() {
        assertEquals(0, range(0, false, false, VoiceSettings.DEFAULT.withTransmit(0.0).withBroadcast(true)));
        assertEquals(6, range(0, true, false, VoiceSettings.DEFAULT.withTransmit(100.0)));
        assertEquals(6, range(0, false, true, VoiceSettings.DEFAULT));
        assertEquals(100, range(-96, false, false, VoiceSettings.DEFAULT.withTransmit(100.0)));
    }

    @Test void deliveryRequiresBothTransmitAndReceiveDistance() {
        assertTrue(hear(listener, VoiceSettings.DEFAULT, 11, false, false));
        assertFalse(hear(listener, VoiceSettings.DEFAULT, 10, false, false));
        assertFalse(hear(listener, VoiceSettings.DEFAULT.withReceive(5.0), 20, false, false));
        assertFalse(hear(listener, VoiceSettings.DEFAULT.withReceive(0.0), 20, true, true));
        assertFalse(hear(speaker, VoiceSettings.DEFAULT, 20, true, true));
    }

    @Test void crossDimensionBroadcastRequiresExplicitOptInAndUnlimitedReception() {
        var other = player("nether", 0, false);
        assertFalse(hear(other, VoiceSettings.DEFAULT, 48, false, true));
        assertFalse(hear(other, VoiceSettings.DEFAULT, 48, true, false));
        assertTrue(hear(other, VoiceSettings.DEFAULT, 48, true, true));
        assertFalse(hear(other, VoiceSettings.DEFAULT.withReceive(100.0), 48, true, true));
    }

    @Test void spectatorCannotBroadcastToLivingPlayer() {
        assertFalse(VoiceRouter.canHear(player("overworld", 0, true), listener, VoiceSettings.DEFAULT, 48, true, true));
    }

    @Test void nonFiniteRangesAndCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> VoiceSettings.DEFAULT.withTransmit(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> VoiceSettings.DEFAULT.withReceive(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, Double.NaN, 0));
    }

    private double range(double level, boolean crouching, boolean whispering, VoiceSettings settings) {
        return VoiceRouter.transmitRange(config, settings, level, crouching, whispering);
    }

    private boolean hear(PlayerSnapshot receiver, VoiceSettings settings, double distance, boolean broadcast, boolean crossDimension) {
        return VoiceRouter.canHear(speaker, receiver, settings, distance, broadcast, crossDimension);
    }

    private static PlayerSnapshot player(String dimension, double x, boolean spectator) {
        return new PlayerSnapshot(UUID.randomUUID(), dimension, new Position(x, 64, 0), false, spectator);
    }
}
