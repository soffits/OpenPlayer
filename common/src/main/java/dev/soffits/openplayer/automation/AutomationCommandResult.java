package dev.soffits.openplayer.automation;

public record AutomationCommandResult(AutomationCommandStatus status, String message) {
    public AutomationCommandResult {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
    }
}
