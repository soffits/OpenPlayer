package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.intent.IntentKind;

record RuntimeQueuedCommand(IntentKind kind, RuntimeCommandCoordinate coordinate) {
    static RuntimeQueuedCommand move(RuntimeCommandCoordinate coordinate) {
        return new RuntimeQueuedCommand(IntentKind.MOVE, coordinate);
    }

    static RuntimeQueuedCommand look(RuntimeCommandCoordinate coordinate) {
        return new RuntimeQueuedCommand(IntentKind.LOOK, coordinate);
    }

    static RuntimeQueuedCommand followOwner() {
        return new RuntimeQueuedCommand(IntentKind.FOLLOW_OWNER, null);
    }
}
