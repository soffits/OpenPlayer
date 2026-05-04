package dev.soffits.openplayer.aicore;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ToolRegistry {
    private final Map<ToolName, ToolSchema> schemas;

    public ToolRegistry(Collection<ToolSchema> schemas) {
        if (schemas == null) {
            throw new IllegalArgumentException("tool schemas cannot be null");
        }
        LinkedHashMap<ToolName, ToolSchema> copy = new LinkedHashMap<>();
        for (ToolSchema schema : schemas) {
            if (copy.put(schema.name(), schema) != null) {
                throw new IllegalArgumentException("duplicate tool schema: " + schema.name().value());
            }
        }
        this.schemas = Collections.unmodifiableMap(copy);
    }

    public Optional<ToolSchema> schema(ToolName name) {
        if (name == null) {
            throw new IllegalArgumentException("tool name cannot be null");
        }
        return Optional.ofNullable(schemas.get(name));
    }

    public boolean contains(ToolName name) {
        return schema(name).isPresent();
    }

    public Collection<ToolSchema> schemas() {
        return schemas.values();
    }

    public String toolNamesCsv() {
        return schemas.keySet().stream().map(ToolName::value).collect(Collectors.joining(", "));
    }
}
