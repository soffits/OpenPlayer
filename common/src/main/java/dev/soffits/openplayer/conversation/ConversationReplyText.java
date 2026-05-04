package dev.soffits.openplayer.conversation;

import java.util.ArrayList;
import java.util.List;

public final class ConversationReplyText {
    public static final int CHAT_REPLY_CHUNK_LENGTH = 96;
    public static final String TRUNCATION_SUFFIX = "... truncated";

    private ConversationReplyText() {
    }

    public static String chatReply(String instruction) {
        String reply = sanitizeProviderReply(instruction);
        return reply.isBlank() ? "I heard you." : reply;
    }

    public static String unavailableReply(String instruction) {
        String reply = sanitizeProviderReply(instruction);
        return reply.isBlank() ? "I cannot do that safely right now." : reply;
    }

    public static String sanitizeProviderReply(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ");
        return sanitized.replaceAll("\\s+", " ").trim();
    }

    public static String summary(String value, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        String sanitized = sanitizeProviderReply(value);
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        if (maxLength <= TRUNCATION_SUFFIX.length()) {
            return TRUNCATION_SUFFIX.substring(0, maxLength);
        }
        int contentLength = maxLength - TRUNCATION_SUFFIX.length();
        return sanitized.substring(0, safeEndIndex(sanitized, contentLength)).trim() + TRUNCATION_SUFFIX;
    }

    public static List<String> displayChunks(String value) {
        return displayChunks(value, CHAT_REPLY_CHUNK_LENGTH);
    }

    static List<String> displayChunks(String value, int maxChunkLength) {
        if (maxChunkLength <= 0) {
            throw new IllegalArgumentException("maxChunkLength must be positive");
        }
        String sanitized = sanitizeProviderReply(value);
        if (sanitized.isEmpty()) {
            return List.of();
        }
        ArrayList<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < sanitized.length()) {
            int end = safeEndIndex(sanitized, Math.min(start + maxChunkLength, sanitized.length()));
            if (end <= start) {
                end = sanitized.offsetByCodePoints(start, 1);
            }
            chunks.add(sanitized.substring(start, end));
            start = end;
        }
        return List.copyOf(chunks);
    }

    private static int safeEndIndex(String value, int requestedEnd) {
        if (requestedEnd <= 0 || requestedEnd >= value.length()) {
            return Math.max(0, Math.min(requestedEnd, value.length()));
        }
        return Character.isHighSurrogate(value.charAt(requestedEnd - 1))
                && Character.isLowSurrogate(value.charAt(requestedEnd))
                ? requestedEnd - 1
                : requestedEnd;
    }
}
