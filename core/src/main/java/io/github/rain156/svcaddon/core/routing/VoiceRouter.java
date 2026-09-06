package io.github.rain156.svcaddon.core.routing;

import io.github.rain156.svcaddon.api.spatial.PlayerSnapshot;
import io.github.rain156.svcaddon.core.config.VoiceConfig;
import io.github.rain156.svcaddon.core.player.VoiceSettings;

public final class VoiceRouter {
    private VoiceRouter() { }

    public static double transmitRange(VoiceConfig config, VoiceSettings settings, double dbfs,
                                       boolean crouching, boolean whispering) {
        if (settings.muted()) return 0;
        double fraction = Math.max(0, Math.min(1, (dbfs - config.quietDbfs()) / (config.loudDbfs() - config.quietDbfs())));
        double range = config.dynamicRange()
                ? config.minimumRange() + fraction * (config.maximumRange() - config.minimumRange()) : config.maximumRange();
        if (settings.transmitRange() != null) range = settings.transmitRange();
        if (crouching) range = Math.min(range, config.crouchRange());
        if (whispering) range = Math.min(range, config.whisperRange());
        return range;
    }

    public static boolean canHear(PlayerSnapshot speaker, PlayerSnapshot listener, VoiceSettings receiver,
                                  double transmitRange, boolean broadcast, boolean crossDimension) {
        if (speaker.id().equals(listener.id()) || receiver.deafened() || transmitRange <= 0) return false;
        if (speaker.spectator() && !listener.spectator()) return false;
        boolean sameDimension = speaker.dimension().equals(listener.dimension());
        if (!sameDimension) return broadcast && crossDimension && receiver.receiveRange() == null;
        double radius = broadcast ? Double.POSITIVE_INFINITY : transmitRange;
        if (receiver.receiveRange() != null) radius = Math.min(radius, receiver.receiveRange());
        return speaker.position().distanceSquared(listener.position()) < radius * radius;
    }
}
