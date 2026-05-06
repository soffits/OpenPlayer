package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.CommandResultDisplay;

public record AutomationCommandResult(AutomationCommandStatus status, String message, CommandResultDisplay display) {
    public AutomationCommandResult(AutomationCommandStatus status, String message) {
        this(status, message, defaultDisplay(status));
    }

    public AutomationCommandResult {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
        if (display == null) {
            display = defaultDisplay(status);
        }
    }

    private static CommandResultDisplay defaultDisplay(AutomationCommandStatus status) {
        if (status == AutomationCommandStatus.ACCEPTED) {
            return CommandResultDisplay.translatable("commands.openplayer.result.automation.accepted");
        }
        return CommandResultDisplay.translatable("commands.openplayer.result.automation.rejected");
    }
}
