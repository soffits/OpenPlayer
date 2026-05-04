package dev.soffits.openplayer.aicore;

public record CapabilityModule(String id, String description, CapabilityStatus status) {
    public CapabilityModule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("capability module id cannot be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("capability module description cannot be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("capability module status cannot be null");
        }
    }
}
