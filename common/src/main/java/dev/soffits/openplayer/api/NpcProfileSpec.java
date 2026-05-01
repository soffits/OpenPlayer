package dev.soffits.openplayer.api;

public record NpcProfileSpec(String name) {
    public NpcProfileSpec {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }
}
