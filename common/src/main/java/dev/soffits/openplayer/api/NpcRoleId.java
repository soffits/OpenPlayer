package dev.soffits.openplayer.api;

public record NpcRoleId(String value) {
    public NpcRoleId {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }
}
