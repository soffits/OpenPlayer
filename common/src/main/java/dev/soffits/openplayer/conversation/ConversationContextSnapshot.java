package dev.soffits.openplayer.conversation;

public record ConversationContextSnapshot(String text) {
    public static final ConversationContextSnapshot EMPTY = new ConversationContextSnapshot("");

    public ConversationContextSnapshot {
        text = text == null ? "" : text.trim();
    }

    public boolean isEmpty() {
        return text.isBlank();
    }
}
