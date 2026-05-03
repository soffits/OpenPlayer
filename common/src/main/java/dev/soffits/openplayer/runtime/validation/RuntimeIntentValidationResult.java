package dev.soffits.openplayer.runtime.validation;

public record RuntimeIntentValidationResult(boolean isAccepted, String message) {
    public RuntimeIntentValidationResult {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
        if (!isAccepted && message.isBlank()) {
            throw new IllegalArgumentException("rejection message cannot be blank");
        }
    }

    public static RuntimeIntentValidationResult accepted() {
        return new RuntimeIntentValidationResult(true, "");
    }

    public static RuntimeIntentValidationResult rejected(String message) {
        return new RuntimeIntentValidationResult(false, message);
    }
}
