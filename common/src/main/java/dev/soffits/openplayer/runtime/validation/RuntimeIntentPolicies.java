package dev.soffits.openplayer.runtime.validation;

import dev.soffits.openplayer.intent.IntentKind;

public final class RuntimeIntentPolicies {
    private RuntimeIntentPolicies() {
    }

    public static boolean isLocalWorldOrInventoryAction(IntentKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        return switch (kind) {
            case COLLECT_ITEMS,
                    EQUIP_BEST_ITEM,
                    EQUIP_ARMOR,
                    USE_SELECTED_ITEM,
                    SWAP_TO_OFFHAND,
                    DROP_ITEM,
                    BREAK_BLOCK,
                    PLACE_BLOCK,
                    ATTACK_NEAREST,
                    GUARD_OWNER -> true;
            case UNAVAILABLE,
                    OBSERVE,
                    STOP,
                    MOVE,
                    LOOK,
                    FOLLOW_OWNER,
                    PATROL,
                    INTERACT,
                    REPORT_STATUS,
                    CHAT -> false;
        };
    }
}
