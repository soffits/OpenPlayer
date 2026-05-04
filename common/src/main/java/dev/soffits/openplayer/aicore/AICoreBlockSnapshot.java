package dev.soffits.openplayer.aicore;

public record AICoreBlockSnapshot(String resourceId, AICoreVec3 position, boolean loaded) {
    public AICoreBlockSnapshot {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("block resource id cannot be blank");
        }
        if (position == null) {
            throw new IllegalArgumentException("block position cannot be null");
        }
    }
}
