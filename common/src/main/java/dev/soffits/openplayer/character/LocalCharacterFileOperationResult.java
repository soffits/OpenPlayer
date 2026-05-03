package dev.soffits.openplayer.character;

public record LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus status, String fileName,
                                               String message) {
    public static final int NETWORK_RESPONSE_MAX_LENGTH = 256;
    private static final int FILE_NAME_DISPLAY_MAX_LENGTH = 72;
    private static final int MESSAGE_DISPLAY_MAX_LENGTH = 160;

    public LocalCharacterFileOperationResult {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        fileName = safeDisplayText(fileName == null ? "" : fileName, FILE_NAME_DISPLAY_MAX_LENGTH);
        message = message == null || message.isBlank() ? status.name().toLowerCase(java.util.Locale.ROOT) : message;
        message = safeDisplayText(message, MESSAGE_DISPLAY_MAX_LENGTH);
    }

    public boolean succeeded() {
        return status == LocalCharacterFileOperationStatus.SAVED
                || status == LocalCharacterFileOperationStatus.IMPORTED
                || status == LocalCharacterFileOperationStatus.EXPORTED;
    }

    public String formatForClientStatus() {
        String filePart = fileName.isBlank() ? "" : " (" + fileName + ")";
        return safeDisplayText(status.name().toLowerCase(java.util.Locale.ROOT) + filePart + ": " + message,
                NETWORK_RESPONSE_MAX_LENGTH);
    }

    public static String safeDisplayText(String value, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        String normalized = value == null ? "" : value.trim();
        StringBuilder builder = new StringBuilder(Math.min(normalized.length(), maxLength));
        for (int index = 0; index < normalized.length() && builder.length() < maxLength; index++) {
            char character = normalized.charAt(index);
            if (character >= 0x20 && character <= 0x7e) {
                builder.append(character);
            } else if (Character.isWhitespace(character)) {
                builder.append(' ');
            } else {
                builder.append('?');
            }
        }
        if (builder.length() == maxLength && normalized.length() > maxLength && maxLength > 3) {
            builder.setLength(maxLength - 3);
            builder.append("...");
        }
        return builder.toString();
    }
}
