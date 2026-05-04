package dev.soffits.openplayer.aicore;

public record ToolCall(ToolName name, ToolArguments arguments) {
    public ToolCall {
        if (name == null) {
            throw new IllegalArgumentException("tool call name cannot be null");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("tool call arguments cannot be null");
        }
    }

    public static ToolCall of(String name, ToolArguments arguments) {
        return new ToolCall(ToolName.of(name), arguments);
    }
}
