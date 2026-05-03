package dev.soffits.openplayer.conversation;

public record ConversationTurn(String speaker, String text) {
    public ConversationTurn {
        speaker = normalizeRequired(speaker, "speaker");
        text = normalizeRequired(text, "text");
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }
}
