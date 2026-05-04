package dev.soffits.openplayer.aicore;

public record AICoreToolDefinition(ToolSchema schema, String group, CapabilityStatus capabilityStatus, String resultReason) {
    public AICoreToolDefinition {
        if (schema == null) {
            throw new IllegalArgumentException("tool schema cannot be null");
        }
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("tool group cannot be blank");
        }
        if (capabilityStatus == null) {
            throw new IllegalArgumentException("capability status cannot be null");
        }
        if (resultReason == null) {
            throw new IllegalArgumentException("result reason cannot be null");
        }
    }
}
