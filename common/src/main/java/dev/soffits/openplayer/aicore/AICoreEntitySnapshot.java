package dev.soffits.openplayer.aicore;

public record AICoreEntitySnapshot(String id, String type, AICoreVec3 position) {
    public AICoreEntitySnapshot {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("entity id cannot be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("entity type cannot be blank");
        }
        if (position == null) {
            throw new IllegalArgumentException("entity position cannot be null");
        }
    }
}
