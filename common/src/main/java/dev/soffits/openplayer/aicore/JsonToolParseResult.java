package dev.soffits.openplayer.aicore;

import java.util.List;

public record JsonToolParseResult(List<ToolCall> calls, String error) {
    public JsonToolParseResult {
        if (calls == null) {
            throw new IllegalArgumentException("tool calls cannot be null");
        }
        if (error == null) {
            throw new IllegalArgumentException("parse error cannot be null");
        }
        calls = List.copyOf(calls);
    }

    public static JsonToolParseResult accepted(List<ToolCall> calls) {
        return new JsonToolParseResult(calls, "");
    }

    public static JsonToolParseResult rejected(String error) {
        return new JsonToolParseResult(List.of(), error);
    }

    public boolean isAccepted() {
        return error.isEmpty();
    }
}
