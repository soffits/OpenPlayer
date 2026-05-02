package dev.soffits.openplayer.automation;

public record AutomationBackendStatus(String name, AutomationBackendState state, String message) {
    public AutomationBackendStatus {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
    }
}
