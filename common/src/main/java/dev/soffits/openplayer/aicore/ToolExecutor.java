package dev.soffits.openplayer.aicore;

public interface ToolExecutor {
    ToolResult execute(ToolCall call, ToolValidationContext context);
}
