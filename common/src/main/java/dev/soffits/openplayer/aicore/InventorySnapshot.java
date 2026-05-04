package dev.soffits.openplayer.aicore;

public record InventorySnapshot(String summary) {
    public InventorySnapshot {
        if (summary == null) {
            throw new IllegalArgumentException("inventory summary cannot be null");
        }
    }
}
