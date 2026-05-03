package dev.soffits.openplayer.api;

import java.util.Optional;

public record NpcProfileSpec(String name, String skinTexture) {
    public NpcProfileSpec(String name) {
        this(name, null);
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
    }

    public Optional<String> optionalSkinTexture() {
        return Optional.ofNullable(skinTexture);
    }
}
