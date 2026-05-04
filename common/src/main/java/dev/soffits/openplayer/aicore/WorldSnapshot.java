package dev.soffits.openplayer.aicore;

public record WorldSnapshot(String summary) {
    public WorldSnapshot {
        if (summary == null) {
            throw new IllegalArgumentException("world summary cannot be null");
        }
    }
}
