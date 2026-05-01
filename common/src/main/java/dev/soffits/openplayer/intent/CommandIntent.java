package dev.soffits.openplayer.intent;

public record CommandIntent(IntentKind kind, IntentPriority priority, String instruction) {
    public CommandIntent {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority cannot be null");
        }
        if (instruction == null) {
            throw new IllegalArgumentException("instruction cannot be null");
        }
    }
}
