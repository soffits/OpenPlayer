package dev.soffits.openplayer.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class OpenPlayerRawTraceTest {
    private OpenPlayerRawTraceTest() {
    }

    public static void main(String[] args) throws IOException {
        writesCompleteRawContentWithoutSafeDebugMemoryExposure();
        redactsCredentialLikeContent();
        redactsCredentialLikeMetadata();
        rotatesAndBoundsRawTraceFile();
    }

    private static void writesCompleteRawContentWithoutSafeDebugMemoryExposure() throws IOException {
        OpenPlayerDebugEvents.clearForTests();
        OpenPlayerRawTrace.clearForTests();
        Path directory = Files.createTempDirectory("openplayer-raw-trace-test");
        OpenPlayerRawTrace.configureLogDirectory(directory);

        String rawPrompt = "user command: go to 1 64 2\nprovider message content should stay raw";
        OpenPlayerRawTrace.providerRequest("https://provider.example/v1/chat/completions", "model-a", rawPrompt);

        List<String> lines = Files.readAllLines(directory.resolve("raw-trace.jsonl"));
        require(lines.size() == 1, "raw trace must write one JSONL line");
        require(lines.get(0).contains("user command: go to 1 64 2"), "raw trace must include raw prompt text");
        require(lines.get(0).contains("provider message content should stay raw"),
                "raw trace must include raw provider message content");
        require(OpenPlayerDebugEvents.recent(10).isEmpty(), "raw trace must not enter safe debug/status event memory");
    }

    private static void redactsCredentialLikeContent() throws IOException {
        OpenPlayerRawTrace.clearForTests();
        Path directory = Files.createTempDirectory("openplayer-raw-trace-redaction-test");
        OpenPlayerRawTrace.configureLogDirectory(directory);

        OpenPlayerRawTrace.providerResponse("https://provider.example/v1/chat/completions", "model-a", 200,
                "Authorization: Bearer abc123 api_key=secret456 token: hidden789 useful raw body");

        String line = Files.readString(directory.resolve("raw-trace.jsonl"));
        require(!line.contains("abc123"), "raw trace must redact Authorization values");
        require(!line.contains("secret456"), "raw trace must redact API key values");
        require(!line.contains("hidden789"), "raw trace must redact token values");
        require(line.contains("useful raw body"), "raw trace must preserve non-secret raw content");
    }

    private static void redactsCredentialLikeMetadata() throws IOException {
        OpenPlayerRawTrace.clearForTests();
        Path directory = Files.createTempDirectory("openplayer-raw-trace-metadata-redaction-test");
        OpenPlayerRawTrace.configureLogDirectory(directory);

        OpenPlayerRawTrace.providerRequest("https://endpoint-secret@provider.example/v1/chat?api_key=query-secret",
                "model-a", "raw body mentions endpoint-secret and query-secret as ordinary text");
        OpenPlayerRawTrace.parseInput("source token: source-secret", "session password=session-secret",
                "raw body mentions source-secret and session-secret as ordinary text");

        String trace = Files.readString(directory.resolve("raw-trace.jsonl"));
        require(!trace.contains("https://endpoint-secret@"), "raw trace must redact URL userinfo in endpoint metadata");
        require(!trace.contains("api_key=query-secret"), "raw trace must redact API key values in endpoint metadata");
        require(!trace.contains("source token: source-secret"), "raw trace must redact source metadata token values");
        require(!trace.contains("session password=session-secret"), "raw trace must redact session metadata password values");
        require(trace.contains("raw body mentions endpoint-secret and query-secret as ordinary text"),
                "raw trace must preserve non-secret raw body content matching metadata values");
        require(trace.contains("raw body mentions source-secret and session-secret as ordinary text"),
                "raw trace must preserve non-secret raw body content matching metadata values");
    }

    private static void rotatesAndBoundsRawTraceFile() throws IOException {
        OpenPlayerRawTrace.clearForTests();
        Path directory = Files.createTempDirectory("openplayer-raw-trace-rotation-test");
        Path file = directory.resolve("raw-trace.jsonl");
        Files.writeString(file, "x".repeat((int) OpenPlayerRawTraceFileSink.MAX_FILE_BYTES));
        OpenPlayerRawTrace.configureLogDirectory(directory);
        OpenPlayerRawTrace.parseOutput("test", "session", "y".repeat(OpenPlayerRawTraceFileSink.MAX_LINE_CHARS + 1024));

        List<String> lines = Files.readAllLines(file);
        require(Files.isRegularFile(directory.resolve("raw-trace.previous.jsonl")), "raw trace must rotate full logs");
        require(lines.size() == 1, "raw trace must write one new active line after rotation");
        require(lines.get(0).length() <= OpenPlayerRawTraceFileSink.MAX_LINE_CHARS,
                "raw trace line must be bounded");
        require(lines.get(0).contains("\"truncated\":true"), "bounded raw trace must mark truncation");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
