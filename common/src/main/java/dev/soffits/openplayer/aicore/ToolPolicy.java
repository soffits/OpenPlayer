package dev.soffits.openplayer.aicore;

public interface ToolPolicy {
    ToolResult validate(ToolCall call, ToolSchema schema, ToolValidationContext context);
}
