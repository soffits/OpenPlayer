package dev.soffits.openplayer.api;

import java.util.UUID;

public record NpcSessionId(UUID value) {
    public NpcSessionId {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
    }
}
