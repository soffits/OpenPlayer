package dev.soffits.openplayer.aicore;

import java.util.Locale;

public record ToolName(String value) {
    public ToolName {
        if (value == null) {
            throw new IllegalArgumentException("tool name cannot be null");
        }
        String trimmedValue = value.trim();
        String normalizedValue = trimmedValue.toLowerCase(Locale.ROOT);
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException("tool name cannot be blank");
        }
        if (!trimmedValue.equals(normalizedValue) || !normalizedValue.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("tool name must use lower_snake_case");
        }
        value = normalizedValue;
    }

    public static ToolName of(String value) {
        return new ToolName(value);
    }
}
