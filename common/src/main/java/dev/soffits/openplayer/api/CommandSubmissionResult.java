package dev.soffits.openplayer.api;

public record CommandSubmissionResult(CommandSubmissionStatus status, String message) {
    public CommandSubmissionResult {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
    }
}
