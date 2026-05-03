package dev.soffits.openplayer.debug;

public record OpenPlayerDebugEvent(
        long sequence,
        long epochMillis,
        String category,
        String status,
        String assignmentId,
        String profileId,
        String sessionId,
        String detail
) {
    public OpenPlayerDebugEvent {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category cannot be blank");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status cannot be blank");
        }
        category = OpenPlayerDebugEvents.sanitizeToken(category);
        status = OpenPlayerDebugEvents.sanitizeToken(status);
        assignmentId = OpenPlayerDebugEvents.sanitizeIdentifier(assignmentId);
        profileId = OpenPlayerDebugEvents.sanitizeIdentifier(profileId);
        sessionId = OpenPlayerDebugEvents.sanitizeIdentifier(sessionId);
        detail = OpenPlayerDebugEvents.sanitizeDetail(detail);
    }

    public String compactLine() {
        StringBuilder builder = new StringBuilder();
        builder.append('#').append(sequence).append(' ').append(category).append('/').append(status);
        appendField(builder, "assignment", assignmentId);
        appendField(builder, "profile", profileId);
        appendField(builder, "session", sessionId);
        appendField(builder, "detail", detail);
        return OpenPlayerDebugEvents.boundForNetwork(builder.toString());
    }

    String jsonLine() {
        StringBuilder builder = new StringBuilder(192);
        builder.append('{');
        appendJson(builder, "seq", Long.toString(sequence), false, false);
        appendJson(builder, "time", Long.toString(epochMillis), false, false);
        appendJson(builder, "category", category, true, false);
        appendJson(builder, "status", status, true, false);
        appendJson(builder, "assignment", assignmentId, true, true);
        appendJson(builder, "profile", profileId, true, true);
        appendJson(builder, "session", sessionId, true, true);
        appendJson(builder, "detail", detail, true, true);
        builder.append('}');
        return OpenPlayerDebugEvents.boundLine(builder.toString());
    }

    private static void appendField(StringBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(' ').append(name).append('=').append(value);
        }
    }

    private static void appendJson(StringBuilder builder, String key, String value, boolean quoted, boolean nullable) {
        if (nullable && (value == null || value.isBlank())) {
            return;
        }
        if (builder.length() > 1) {
            builder.append(',');
        }
        builder.append('"').append(key).append('"').append(':');
        if (quoted) {
            builder.append('"').append(escapeJson(value == null ? "" : value)).append('"');
        } else {
            builder.append(value);
        }
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                builder.append('\\').append(character);
            } else if (character == '\n') {
                builder.append("\\n");
            } else if (character == '\r') {
                builder.append("\\r");
            } else if (character == '\t') {
                builder.append("\\t");
            } else if (character < 0x20) {
                builder.append('?');
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }
}
