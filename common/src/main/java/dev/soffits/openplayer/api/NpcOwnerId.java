package dev.soffits.openplayer.api;

import java.util.UUID;

public record NpcOwnerId(UUID value) {
    public NpcOwnerId {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
    }
}
