package dev.soffits.openplayer.aicore;

public record AICoreItemSnapshot(String resourceId, int count, String componentsSummary) {
    public AICoreItemSnapshot {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("item resource id cannot be blank");
        }
        if (count < 0) {
            throw new IllegalArgumentException("item count cannot be negative");
        }
        if (componentsSummary == null) {
            throw new IllegalArgumentException("item components summary cannot be null");
        }
    }
}
