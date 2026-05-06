package dev.soffits.openplayer.api;

public record CommandSubmissionResult(CommandSubmissionStatus status, String message, CommandResultDisplay display) {
    public CommandSubmissionResult(CommandSubmissionStatus status, String message) {
        this(status, message, CommandResultDisplay.status(status));
    }

    public CommandSubmissionResult {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
        if (display == null) {
            display = CommandResultDisplay.status(status);
        }
    }
}
