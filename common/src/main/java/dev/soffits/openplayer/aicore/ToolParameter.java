package dev.soffits.openplayer.aicore;

public record ToolParameter(String name, String type, boolean required, String description) {
    public ToolParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool parameter name cannot be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("tool parameter type cannot be blank");
        }
        if (description == null) {
            throw new IllegalArgumentException("tool parameter description cannot be null");
        }
    }
}
