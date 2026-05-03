package dev.soffits.openplayer.conversation;

public record ConversationEvent(ConversationEventType type, String text) {
    public ConversationEvent {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text cannot be blank");
        }
        text = text.trim();
    }
}
