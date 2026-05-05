package dev.soffits.openplayer.api;

import java.util.Optional;

public record NpcProfileSpec(String name, String skinTexture, String movementPolicy) {
    public NpcProfileSpec(String name) {
        this(name, null, null);
    }

    public NpcProfileSpec(String name, String skinTexture) {
        this(name, skinTexture, null);
    }

    public NpcProfileSpec {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (skinTexture != null && skinTexture.isBlank()) {
            skinTexture = null;
        }
        if (movementPolicy != null && movementPolicy.isBlank()) {
            movementPolicy = null;
        }
    }

    public Optional<String> optionalSkinTexture() {
        return Optional.ofNullable(skinTexture);
    }

    public Optional<String> optionalMovementPolicy() {
        return Optional.ofNullable(movementPolicy);
    }
}
