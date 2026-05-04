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
        return switch (kind) {
            case USE_PORTAL -> "USE_PORTAL is unsupported: portal construction/use needs a separate reviewed safe phase";
            case TRAVEL_NETHER -> "TRAVEL_NETHER is unsupported: Nether travel needs a separate reviewed safe phase";
            default -> throw new IllegalArgumentException(kind.name() + " is not an unsupported advanced kind");
        };
    }
}
