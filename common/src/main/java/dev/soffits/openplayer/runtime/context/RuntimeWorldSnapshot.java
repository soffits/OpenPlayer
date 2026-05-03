package dev.soffits.openplayer.runtime.context;

public record RuntimeWorldSnapshot(
        String dimension,
        int npcX,
        int npcY,
        int npcZ,
        long dayTime,
        boolean day,
        boolean raining,
        boolean thundering,
        String difficulty
) {
    public RuntimeWorldSnapshot {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("dimension cannot be blank");
        }
        if (difficulty == null || difficulty.isBlank()) {
            throw new IllegalArgumentException("difficulty cannot be blank");
        }
        dimension = dimension.trim();
        difficulty = difficulty.trim();
    }
}
