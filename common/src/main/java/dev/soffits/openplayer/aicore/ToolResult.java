package dev.soffits.openplayer.aicore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ToolResult(ToolResultStatus status, String reason, String summary, Map<String, String> details) {
    public ToolResult {
        if (status == null) {
            throw new IllegalArgumentException("tool result status cannot be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("tool result reason cannot be null");
        }
        if (summary == null) {
            throw new IllegalArgumentException("tool result summary cannot be null");
        }
        if (details == null) {
            throw new IllegalArgumentException("tool result details cannot be null");
        }
        details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static ToolResult success(String summary) {
        return new ToolResult(ToolResultStatus.SUCCESS, "", summary, Map.of());
    }

    public static ToolResult rejected(String reason) {
        return new ToolResult(ToolResultStatus.REJECTED, reason, reason, Map.of());
    }

    public static ToolResult failed(String reason) {
        return new ToolResult(ToolResultStatus.FAILED, reason, reason, Map.of());
    }
}
