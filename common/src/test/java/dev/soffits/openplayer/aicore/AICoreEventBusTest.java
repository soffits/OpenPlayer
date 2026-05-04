package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreEventBusTest {
    private AICoreEventBusTest() {
    }

    public static void main(String[] args) {
        AICoreEventBus bus = new AICoreEventBus(2);
        bus.publish(EventType.CHAT, Map.of("message", "hello", "token", "hidden"));
        bus.publish(EventType.MOVE, Map.of("x", "1"));
        bus.publish(EventType.HEALTH, Map.of("health", "20"));
        AICoreTestSupport.require(bus.size() == 2, "event bus must be bounded");
        AICoreTestSupport.require(bus.observe(new EventCursor(0), 64).size() == 2, "observe must respect retained events");
        AICoreTestSupport.require(!bus.observe(new EventCursor(0), 1).get(0).payload().containsKey("token"), "event payload must drop token fields");
    }
}
