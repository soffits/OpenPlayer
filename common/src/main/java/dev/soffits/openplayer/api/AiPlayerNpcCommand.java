package dev.soffits.openplayer.api;

import dev.soffits.openplayer.intent.CommandIntent;

import java.util.UUID;

public record AiPlayerNpcCommand(UUID commandId, CommandIntent intent) {
    public AiPlayerNpcCommand {
        if (commandId == null) {
            throw new IllegalArgumentException("commandId cannot be null");
        }
        if (intent == null) {
            throw new IllegalArgumentException("intent cannot be null");
        }
    }
}
