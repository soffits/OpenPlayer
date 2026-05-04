package dev.soffits.openplayer.conversation;

import dev.soffits.openplayer.intent.CommandIntent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConversationStatusRepository {
    public static final int MAX_EVENTS = 6;
    public static final int MAX_ASSIGNMENTS = 128;
    public static final int MAX_EVENT_TEXT_LENGTH = 96;
    public static final int MAX_NETWORK_EVENT_LINE_LENGTH = 128;

    private final Map<Key, List<ConversationEvent>> eventsByAssignment = new LinkedHashMap<>();

    public void recordGreeting(UUID ownerId, String assignmentId, String displayName) {
        record(ownerId, assignmentId, ConversationEventType.GREETING,
                "Hello, I am " + sanitize(displayName) + ". Ready when you are.");
    }

    public void recordPlayerMessage(UUID ownerId, String assignmentId, String message) {
        record(ownerId, assignmentId, ConversationEventType.PLAYER, "You: " + sanitize(message));
    }

    public void recordAction(UUID ownerId, String assignmentId, CommandIntent intent) {
        if (intent == null) {
            return;
        }
        record(ownerId, assignmentId, ConversationEventType.ACTION, "Action accepted: " + intent.kind().name());
    }

    public void recordNpcReply(UUID ownerId, String assignmentId, String message) {
        record(ownerId, assignmentId, ConversationEventType.ACTION, sanitize(message));
    }

    public void recordFailure(UUID ownerId, String assignmentId, String message) {
        record(ownerId, assignmentId, ConversationEventType.FAILURE, sanitizeFailure(message));
    }

    public List<ConversationEvent> events(UUID ownerId, String assignmentId) {
        if (ownerId == null || assignmentId == null || assignmentId.isBlank()) {
            return List.of();
        }
        return eventsByAssignment.getOrDefault(new Key(ownerId, assignmentId.trim()), List.of());
    }

    public List<String> eventLines(UUID ownerId, String assignmentId) {
        List<String> lines = new ArrayList<>();
        for (ConversationEvent event : events(ownerId, assignmentId)) {
            lines.add(ConversationReplyText.summary(
                    event.type().name().toLowerCase(java.util.Locale.ROOT) + ": " + event.text(),
                    MAX_NETWORK_EVENT_LINE_LENGTH
            ));
        }
        return List.copyOf(lines);
    }

    private void record(UUID ownerId, String assignmentId, ConversationEventType type, String text) {
        if (ownerId == null || assignmentId == null || assignmentId.isBlank()) {
            return;
        }
        String safeText = sanitize(text);
        if (safeText.isEmpty()) {
            return;
        }
        Key key = new Key(ownerId, assignmentId.trim());
        List<ConversationEvent> updated = new ArrayList<>(eventsByAssignment.getOrDefault(key, List.of()));
        updated.add(new ConversationEvent(type, safeText));
        while (updated.size() > MAX_EVENTS) {
            updated.remove(0);
        }
        eventsByAssignment.put(key, List.copyOf(updated));
        while (eventsByAssignment.size() > MAX_ASSIGNMENTS) {
            Key oldestKey = eventsByAssignment.keySet().iterator().next();
            eventsByAssignment.remove(oldestKey);
        }
    }

    private static String sanitizeFailure(String message) {
        if (message == null || message.isBlank()) {
            return "Unable to handle that request safely.";
        }
        if (message.toLowerCase(java.util.Locale.ROOT).contains("parser disabled")) {
            return "Conversation unavailable: parser disabled.";
        }
        return "Unable to handle that request safely.";
    }

    public static String sanitize(String value) {
        return ConversationReplyText.summary(value, MAX_EVENT_TEXT_LENGTH);
    }

    private record Key(UUID ownerId, String assignmentId) {
    }
}
