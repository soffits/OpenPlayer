package dev.soffits.openplayer.api;

public record NpcSpawnLocation(String dimension, double x, double y, double z) {
    public NpcSpawnLocation {
        if (dimension == null) {
            throw new IllegalArgumentException("dimension cannot be null");
        }
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension cannot be blank");
        }
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("x must be finite");
        }
        if (!Double.isFinite(y)) {
            throw new IllegalArgumentException("y must be finite");
        }
        if (!Double.isFinite(z)) {
            throw new IllegalArgumentException("z must be finite");
        }
    }
}
