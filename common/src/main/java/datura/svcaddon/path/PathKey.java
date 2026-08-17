package datura.svcaddon.path;

import java.util.UUID;

public record PathKey(
        UUID speaker,
        UUID listener,
        String dimension,
        int speakerX,
        int speakerY,
        int speakerZ,
        int listenerX,
        int listenerY,
        int listenerZ,
        long worldRevision
) {
    public static PathKey create(
            PlayerSpatialState speaker,
            PlayerSpatialState listener,
            int quantization,
            long worldRevision
    ) {
        int scale = Math.max(1, quantization);
        GridPos speakerCell = speaker.eyePosition().floorToGrid();
        GridPos listenerCell = listener.eyePosition().floorToGrid();
        return new PathKey(
                speaker.playerUuid(),
                listener.playerUuid(),
                speaker.dimension(),
                Math.floorDiv(speakerCell.x(), scale),
                Math.floorDiv(speakerCell.y(), scale),
                Math.floorDiv(speakerCell.z(), scale),
                Math.floorDiv(listenerCell.x(), scale),
                Math.floorDiv(listenerCell.y(), scale),
                Math.floorDiv(listenerCell.z(), scale),
                worldRevision
        );
    }
}
