package dev.soffits.openplayer.aicore;

public record EventSubscription(EventType type, int limit, int timeoutTicks) {
    public EventSubscription {
        if (type == null) {
            throw new IllegalArgumentException("event type cannot be null");
        }
        if (limit < 1 || limit > 64) {
            throw new IllegalArgumentException("event limit must be between 1 and 64");
        }
        if (timeoutTicks < 0 || timeoutTicks > 1200) {
            throw new IllegalArgumentException("event timeout must be between 0 and 1200 ticks");
        }
    }
}
