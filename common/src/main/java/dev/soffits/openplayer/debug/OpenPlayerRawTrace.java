package dev.soffits.openplayer.debug;

import java.nio.file.Path;

public final class OpenPlayerRawTrace {
    private static OpenPlayerRawTraceFileSink fileSink;

    private OpenPlayerRawTrace() {
    }

    public static synchronized void configureLogDirectory(Path directory) {
        fileSink = directory == null ? null : new OpenPlayerRawTraceFileSink(directory);
    }

    public static void commandText(String source, String ownerId, String assignmentId, String sessionId, String text) {
        record("command_text", source, ownerId, assignmentId, sessionId, text);
    }

    public static void providerRequest(String endpoint, String model, String body) {
        record("provider_request", endpoint, null, model, null, body);
    }

    public static void providerResponse(String endpoint, String model, int statusCode, String body) {
        record("provider_response", endpoint, Integer.toString(statusCode), model, null, body);
    }

    public static void parseInput(String source, String sessionId, String input) {
        record("parse_input", source, null, null, sessionId, input);
    }

    public static void parseOutput(String source, String sessionId, String output) {
        record("parse_output", source, null, null, sessionId, output);
    }

    public static void parseRejection(String source, String sessionId, String input, String reason) {
        record("parse_rejection", source, reason, null, sessionId, input);
    }

    public static void automationOperation(String status, String kind, String detail) {
        record("automation_operation", status, kind, null, null, detail);
    }

    public static synchronized void clearForTests() {
        fileSink = null;
    }

    private static synchronized void record(String category, String source, String status, String modelOrKind,
                                            String sessionId, String rawContent) {
        OpenPlayerRawTraceFileSink sink = fileSink;
        if (sink != null) {
            sink.append(category, source, status, modelOrKind, sessionId, rawContent);
        }
    }
}
