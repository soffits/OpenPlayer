package dev.soffits.openplayer.debug;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class OpenPlayerDebugEvents {
    public static final int MAX_MEMORY_EVENTS = 128;
    public static final int MAX_DETAIL_LENGTH = 160;
    public static final int MAX_LINE_LENGTH = 512;
    public static final int MAX_NETWORK_LINE_LENGTH = 192;
    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final Pattern SAFE_TOKEN = Pattern.compile("[^A-Za-z0-9_.:-]");
    private static final Pattern SENSITIVE_WORD = Pattern.compile(
            "(?i)(authorization|api[ _-]?key|bearer|token|secret|password)\\s*[:=]\\s*[^,;]+"
    );
    private static final ArrayDeque<OpenPlayerDebugEvent> EVENTS = new ArrayDeque<>();
    private static long nextSequence = 1L;
    private static OpenPlayerDebugFileSink fileSink;

    private OpenPlayerDebugEvents() {
    }

    public static synchronized void configureLogDirectory(Path directory) {
        fileSink = directory == null ? null : new OpenPlayerDebugFileSink(directory);
    }

    public static OpenPlayerDebugEvent record(String category, String status, String detail) {
        return record(category, status, null, null, null, detail);
    }

    public static synchronized OpenPlayerDebugEvent record(String category, String status, String assignmentId,
                                                           String profileId, String sessionId, String detail) {
        OpenPlayerDebugEvent event = new OpenPlayerDebugEvent(
                nextSequence++,
                System.currentTimeMillis(),
                category,
                status,
                assignmentId,
                profileId,
                sessionId,
                detail
        );
        EVENTS.addLast(event);
        while (EVENTS.size() > MAX_MEMORY_EVENTS) {
            EVENTS.removeFirst();
        }
        OpenPlayerDebugFileSink sink = fileSink;
        if (sink != null) {
            sink.append(event);
        }
        return event;
    }

    public static synchronized List<OpenPlayerDebugEvent> recent(int maxCount) {
        int count = Math.max(0, Math.min(maxCount, EVENTS.size()));
        ArrayList<OpenPlayerDebugEvent> result = new ArrayList<>(count);
        int skip = EVENTS.size() - count;
        int index = 0;
        for (OpenPlayerDebugEvent event : EVENTS) {
            if (index++ >= skip) {
                result.add(event);
            }
        }
        return List.copyOf(result);
    }

    public static synchronized void clearForTests() {
        EVENTS.clear();
        nextSequence = 1L;
        fileSink = null;
    }

    public static String sanitizeToken(String value) {
        String normalized = value == null ? "unknown" : value.toLowerCase(Locale.ROOT).trim();
        String sanitized = SAFE_TOKEN.matcher(normalized).replaceAll("_");
        return sanitized.isBlank() ? "unknown" : bound(sanitized, 48);
    }

    public static String sanitizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = SAFE_TOKEN.matcher(value.trim()).replaceAll("_");
        return bound(sanitized, MAX_IDENTIFIER_LENGTH);
    }

    public static String sanitizeDetail(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        String redacted = SENSITIVE_WORD.matcher(singleLine).replaceAll("$1=[redacted]");
        return bound(redacted, MAX_DETAIL_LENGTH);
    }

    static String boundLine(String value) {
        return bound(value, MAX_LINE_LENGTH);
    }

    public static String boundForNetwork(String value) {
        return bound(value, MAX_NETWORK_LINE_LENGTH);
    }

    private static String bound(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 1) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 1) + "~";
    }
}
