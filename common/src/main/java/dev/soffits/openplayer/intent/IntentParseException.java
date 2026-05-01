package dev.soffits.openplayer.intent;

public final class IntentParseException extends Exception {
    public IntentParseException(String message) {
        super(message);
    }

    public IntentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
