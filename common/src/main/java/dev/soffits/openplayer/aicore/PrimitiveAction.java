package dev.soffits.openplayer.aicore;

public record PrimitiveAction(ToolName toolName, ActionStatus status, String summary) {
    public PrimitiveAction {
        if (toolName == null) {
            throw new IllegalArgumentException("primitive action tool name cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("primitive action status cannot be null");
        }
        if (summary == null) {
            throw new IllegalArgumentException("primitive action summary cannot be null");
        }
    }
}
