package datura.svcaddon.audio;

import datura.svcaddon.config.SvcAddonConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeEnvelopeTest {
    private static final SvcAddonConfig CONFIG = SvcAddonConfig.defaults();

    @Test
    void noiseGateUsesHysteresis() {
        VolumeEnvelope envelope = new VolumeEnvelope();

        assertFalse(envelope.update(-56.0D, 20.0D, CONFIG).gateOpen());
        assertTrue(envelope.update(-55.0D, 20.0D, CONFIG).gateOpen());
        assertTrue(envelope.update(-57.0D, 20.0D, CONFIG).gateOpen());
        assertFalse(envelope.update(-59.0D, 20.0D, CONFIG).gateOpen());
    }

    @Test
    void attackIsFasterThanRelease() {
        VolumeEnvelope attackEnvelope = primedAt(-40.0D);
        double attackStart = attackEnvelope.update(-40.0D, 20.0D, CONFIG).smoothedDbfs();
        double attacked = attackEnvelope.update(-10.0D, 20.0D, CONFIG).smoothedDbfs();
        double attackFraction = (attacked - attackStart) / 30.0D;

        VolumeEnvelope releaseEnvelope = primedAt(-40.0D);
        double releaseStart = releaseEnvelope.update(-40.0D, 20.0D, CONFIG).smoothedDbfs();
        double released = releaseEnvelope.update(-50.0D, 20.0D, CONFIG).smoothedDbfs();
        double releaseFraction = (releaseStart - released) / 10.0D;

        assertTrue(attackFraction > releaseFraction);
        assertTrue(attackFraction > 0.15D);
        assertTrue(releaseFraction < 0.10D);
    }

    private static VolumeEnvelope primedAt(double dbfs) {
        VolumeEnvelope envelope = new VolumeEnvelope();
        for (int index = 0; index < 200; index++) {
            envelope.update(dbfs, 20.0D, CONFIG);
        }
        return envelope;
    }
}
