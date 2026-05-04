package dev.soffits.openplayer.aicore;

import java.util.List;

public record ToolSchema(ToolName name, String description, List<ToolParameter> parameters, boolean mutatesWorld) {
    public ToolSchema {
        if (name == null) {
            throw new IllegalArgumentException("tool schema name cannot be null");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("tool schema description cannot be blank");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("tool schema parameters cannot be null");
        }
        parameters = List.copyOf(parameters);
    }
}
