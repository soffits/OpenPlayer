package dev.soffits.openplayer;

import dev.soffits.openplayer.intent.IntentKind;

public final class OpenPlayerCommandsTest {
    private OpenPlayerCommandsTest() {
    }

    public static void main(String[] args) {
        parsesQueueIntentKindsCaseInsensitively();
        rejectsUnknownQueueIntentKinds();
        rejectsDisallowedQueueIntentKinds();
        suggestsOnlyQueueAppropriateIntentKinds();
    }

    private static void parsesQueueIntentKindsCaseInsensitively() {
        require(OpenPlayerCommands.parseQueueIntentKind("move") == IntentKind.MOVE,
                "queue kind parser must accept lowercase names");
        require(OpenPlayerCommands.parseQueueIntentKind("GOTO") == IntentKind.GOTO,
                "queue kind parser must accept uppercase names");
        require(OpenPlayerCommands.parseQueueIntentKind(" body_language ") == IntentKind.BODY_LANGUAGE,
                "queue kind parser must trim surrounding whitespace");
        require(OpenPlayerCommands.parseQueueIntentKind("INTERACT") == IntentKind.INTERACT,
                "queue kind parser must accept implemented INTERACT");
        require(OpenPlayerCommands.parseQueueIntentKind("attack_target") == IntentKind.ATTACK_TARGET,
                "queue kind parser must accept implemented ATTACK_TARGET");
    }

    private static void rejectsUnknownQueueIntentKinds() {
        require(OpenPlayerCommands.parseQueueIntentKind("mine_everything") == null,
                "queue kind parser must reject unknown names deterministically");
        require(OpenPlayerCommands.parseQueueIntentKind("") == null,
                "queue kind parser must reject blank names");
    }

    private static void rejectsDisallowedQueueIntentKinds() {
        require(OpenPlayerCommands.parseQueueIntentKind("chat") == null,
                "queue kind parser must reject CHAT even though it is a valid enum name");
        require(OpenPlayerCommands.parseQueueIntentKind("UNAVAILABLE") == null,
                "queue kind parser must reject UNAVAILABLE even though it is a valid enum name");
        require(OpenPlayerCommands.parseQueueIntentKind("observe") == null,
                "queue kind parser must reject OBSERVE even though it is a valid enum name");
    }

    private static void suggestsOnlyQueueAppropriateIntentKinds() {
        require(OpenPlayerCommands.queueSuggestedIntentKinds().contains(IntentKind.MOVE),
                "queue suggestions must include movement intents");
        require(OpenPlayerCommands.queueSuggestedIntentKinds().contains(IntentKind.BODY_LANGUAGE),
                "queue suggestions may include visual-only body language");
        require(!OpenPlayerCommands.queueSuggestedIntentKinds().contains(IntentKind.CHAT),
                "queue suggestions must not include CHAT");
        require(!OpenPlayerCommands.queueSuggestedIntentKinds().contains(IntentKind.UNAVAILABLE),
                "queue suggestions must not include UNAVAILABLE");
        require(!OpenPlayerCommands.queueSuggestedIntentKinds().contains(IntentKind.OBSERVE),
                "queue suggestions must not include OBSERVE");
        require(OpenPlayerCommands.queueSuggestedIntentKinds().contains(IntentKind.INTERACT),
                "queue suggestions must include implemented INTERACT");
        require(OpenPlayerCommands.queueSuggestedIntentKinds().contains(IntentKind.ATTACK_TARGET),
                "queue suggestions must include implemented ATTACK_TARGET");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
