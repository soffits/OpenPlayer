package dev.soffits.openplayer.aicore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AICoreEventBus {
    private final int capacity;
    private final Deque<AgentEvent> events = new ArrayDeque<>();
    private long nextSequence = 1L;

    public AICoreEventBus(int capacity) {
        if (capacity < 1 || capacity > 4096) {
            throw new IllegalArgumentException("event bus capacity must be between 1 and 4096");
        }
        this.capacity = capacity;
    }

    public AgentEvent publish(EventType type, Map<String, String> payload) {
        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            String key = entry.getKey();
            if (key != null && !key.isBlank() && !key.toLowerCase().contains("secret") && !key.toLowerCase().contains("token")) {
                sanitized.put(key, entry.getValue() == null ? "" : entry.getValue());
            }
        }
        AgentEvent event = new AgentEvent(nextSequence++, type, sanitized);
        events.addLast(event);
        while (events.size() > capacity) {
            events.removeFirst();
        }
        return event;
    }

    public List<AgentEvent> observe(EventCursor cursor, int limit) {
        if (cursor == null) {
            throw new IllegalArgumentException("event cursor cannot be null");
        }
        if (limit < 1 || limit > 64) {
            throw new IllegalArgumentException("event limit must be between 1 and 64");
        }
        ArrayList<AgentEvent> matches = new ArrayList<>();
        for (AgentEvent event : events) {
            if (event.sequence() > cursor.sequence()) {
                matches.add(event);
                if (matches.size() == limit) {
                    break;
                }
            }
        }
        return List.copyOf(matches);
    }

    public int size() {
        return events.size();
    }
}
