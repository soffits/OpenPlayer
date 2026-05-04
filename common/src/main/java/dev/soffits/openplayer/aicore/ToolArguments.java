package dev.soffits.openplayer.aicore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ToolArguments(Map<String, String> values) {
    public ToolArguments {
        if (values == null) {
            throw new IllegalArgumentException("tool arguments cannot be null");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("tool argument key cannot be blank");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("tool argument value cannot be null");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        values = Collections.unmodifiableMap(copy);
    }

    public static ToolArguments empty() {
        return new ToolArguments(Map.of());
    }

    public static ToolArguments instruction(String instruction) {
        return new ToolArguments(Map.of("instruction", instruction == null ? "" : instruction));
    }

    public String instruction() {
        return values.getOrDefault("instruction", "");
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
