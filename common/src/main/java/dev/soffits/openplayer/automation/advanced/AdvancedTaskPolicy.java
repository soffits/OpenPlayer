package dev.soffits.openplayer.automation.advanced;

import dev.soffits.openplayer.intent.IntentKind;

public final class AdvancedTaskPolicy {
    private AdvancedTaskPolicy() {
    }

    public static boolean isUnsupportedAdvancedKind(IntentKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        return false;
    }

    public static String unsupportedReason(IntentKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        throw new IllegalArgumentException(kind.name() + " is not an unsupported advanced kind");
    }
}
