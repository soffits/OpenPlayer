package dev.soffits.openplayer.aicore;

import java.util.Map;

public record AgentEvent(long sequence, EventType type, Map<String, String> payload) {
    public AgentEvent {
        if (sequence < 1L) {
            throw new IllegalArgumentException("event sequence must be positive");
        }
        if (type == null) {
            throw new IllegalArgumentException("event type cannot be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("event payload cannot be null");
        }
        payload = Map.copyOf(payload);
    }
}
