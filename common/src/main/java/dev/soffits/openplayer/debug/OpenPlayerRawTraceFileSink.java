package dev.soffits.openplayer.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

public final class OpenPlayerRawTraceFileSink {
    public static final long MAX_FILE_BYTES = 4L * 1024L * 1024L;
    public static final int MAX_LINE_CHARS = 256 * 1024;
    private static final Pattern AUTHORIZATION_VALUE = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)(bearer\\s+)?\\S+"
    );
    private static final Pattern BEARER_VALUE = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)(authorization|api[ _-]?key|bearer|token|secret|password)(\\\\?\"?\\s*[:=]\\s*\\\\?\"?)[^\\\\\",;\\s}]+"
    );
    private static final Pattern URL_USERINFO = Pattern.compile("(?i)(https?://)[^/@\\s]+@");
    private final Path file;
    private final Path rotatedFile;

    public OpenPlayerRawTraceFileSink(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory cannot be null");
        }
        this.file = directory.resolve("raw-trace.jsonl");
        this.rotatedFile = directory.resolve("raw-trace.previous.jsonl");
    }

    public void append(String category, String source, String status, String modelOrKind, String sessionId, String rawContent) {
        try {
            Files.createDirectories(file.getParent());
            rotateIfNeeded();
            Files.writeString(
                    file,
                    jsonLine(category, source, status, modelOrKind, sessionId, rawContent) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException | SecurityException ignored) {
            // Raw trace logging is diagnostic only and must never affect gameplay.
        }
    }

    private String jsonLine(String category, String source, String status, String modelOrKind, String sessionId, String rawContent) {
        String safeRawContent = redact(rawContent == null ? "" : rawContent);
        boolean truncated = false;
        String prefix = "{\"timestamp\":" + System.currentTimeMillis()
                + ",\"category\":\"" + safeJsonString(category) + "\""
                + ",\"source\":\"" + safeJsonString(source) + "\""
                + ",\"status\":\"" + safeJsonString(status) + "\""
                + ",\"modelOrKind\":\"" + safeJsonString(modelOrKind) + "\""
                + ",\"sessionId\":\"" + safeJsonString(sessionId) + "\""
                + ",\"truncated\":";
        String suffix = ",\"raw\":\"";
        String escapedRawContent = escape(safeRawContent);
        int reservedLength = prefix.length() + "true".length() + suffix.length() + "\"}".length();
        int maxRawLength = Math.max(0, MAX_LINE_CHARS - reservedLength);
        if (escapedRawContent.length() > maxRawLength) {
            escapedRawContent = escapedRawContent.substring(0, maxRawLength);
            truncated = true;
        }
        return prefix + truncated + suffix + escapedRawContent + "\"}";
    }

    private void rotateIfNeeded() throws IOException {
        if (Files.isRegularFile(file) && Files.size(file) >= MAX_FILE_BYTES) {
            Files.move(file, rotatedFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String redact(String value) {
        String redacted = URL_USERINFO.matcher(value).replaceAll("$1[redacted]@");
        redacted = AUTHORIZATION_VALUE.matcher(redacted).replaceAll("$1[redacted]");
        redacted = BEARER_VALUE.matcher(redacted).replaceAll("Bearer [redacted]");
        return SECRET_FIELD.matcher(redacted).replaceAll("$1$2[redacted]");
    }

    private static String safeJsonString(String value) {
        return escape(redact(value == null ? "" : value));
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
