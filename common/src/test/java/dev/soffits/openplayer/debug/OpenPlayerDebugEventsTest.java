package dev.soffits.openplayer.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class OpenPlayerDebugEventsTest {
    private OpenPlayerDebugEventsTest() {
    }

    public static void main(String[] args) throws IOException {
        sanitizesSecretsAndBoundsDetail();
        boundsInMemoryEvents();
        writesJsonLinesBestEffort();
        rotatesJsonLinesWhenBoundedFileIsFull();
    }

    private static void sanitizesSecretsAndBoundsDetail() {
        OpenPlayerDebugEvents.clearForTests();
        String longDetail = "Authorization: Bearer abc123 message=" + "x".repeat(300);
        OpenPlayerDebugEvent event = OpenPlayerDebugEvents.record(
                "Provider Test",
                "HTTP Status",
                "assignment/one",
                "profile one",
                "session one",
                longDetail
        );

        require("provider_test".equals(event.category()), "category must be stable and sanitized");
        require("http_status".equals(event.status()), "status must be stable and sanitized");
        require(!event.detail().contains("abc123"), "detail must redact bearer-like secrets");
        require(event.detail().length() <= OpenPlayerDebugEvents.MAX_DETAIL_LENGTH, "detail must be bounded");
        require(event.compactLine().length() <= OpenPlayerDebugEvents.MAX_NETWORK_LINE_LENGTH,
                "network line must be bounded");
    }

    private static void boundsInMemoryEvents() {
        OpenPlayerDebugEvents.clearForTests();
        for (int index = 0; index < OpenPlayerDebugEvents.MAX_MEMORY_EVENTS + 10; index++) {
            OpenPlayerDebugEvents.record("test", "event", "detail=" + index);
        }

        List<OpenPlayerDebugEvent> events = OpenPlayerDebugEvents.recent(OpenPlayerDebugEvents.MAX_MEMORY_EVENTS + 10);
        require(events.size() == OpenPlayerDebugEvents.MAX_MEMORY_EVENTS, "memory events must be bounded");
        require(events.get(0).sequence() == 11L, "oldest events must be evicted first");
    }

    private static void writesJsonLinesBestEffort() throws IOException {
        OpenPlayerDebugEvents.clearForTests();
        Path directory = Files.createTempDirectory("openplayer-debug-events-test");
        OpenPlayerDebugEvents.configureLogDirectory(directory);
        OpenPlayerDebugEvents.record("provider_test", "success", null, null, null, "kind=REPORT_STATUS");

        Path file = directory.resolve("events.jsonl");
        List<String> lines = Files.readAllLines(file);
        require(lines.size() == 1, "file sink must write one JSONL event");
        require(lines.get(0).startsWith("{"), "file sink must write JSON object lines");
        require(lines.get(0).contains("\"category\":\"provider_test\""), "JSONL must include category");
        require(lines.get(0).length() <= OpenPlayerDebugEvents.MAX_LINE_LENGTH, "JSONL line must be bounded");
    }

    private static void rotatesJsonLinesWhenBoundedFileIsFull() throws IOException {
        Path directory = Files.createTempDirectory("openplayer-debug-events-rotation-test");
        Path file = directory.resolve("events.jsonl");
        Files.writeString(file, "x".repeat((int) OpenPlayerDebugFileSink.MAX_FILE_BYTES));
        OpenPlayerDebugEvents.clearForTests();
        OpenPlayerDebugEvents.configureLogDirectory(directory);
        OpenPlayerDebugEvents.record("provider_test", "success", null, null, null, "kind=REPORT_STATUS");

        require(Files.isRegularFile(directory.resolve("events.previous.jsonl")), "file sink must rotate full logs");
        require(Files.size(file) < OpenPlayerDebugFileSink.MAX_FILE_BYTES, "new active log must stay bounded after rotation");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
