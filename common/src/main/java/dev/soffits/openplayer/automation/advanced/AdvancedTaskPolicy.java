package dev.soffits.openplayer.automation.advanced;

import dev.soffits.openplayer.intent.IntentKind;

public final class AdvancedTaskPolicy {
    private AdvancedTaskPolicy() {
    }

    public static boolean isUnsupportedAdvancedKind(IntentKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        return switch (kind) {
            case USE_PORTAL,
                    TRAVEL_NETHER,
                    LOCATE_STRONGHOLD,
                    END_GAME_TASK -> true;
            case UNAVAILABLE,
                    OBSERVE,
                    STOP,
                    MOVE,
                    LOOK,
                    FOLLOW_OWNER,
                    GUARD_OWNER,
                    PATROL,
                    COLLECT_ITEMS,
                    EQUIP_BEST_ITEM,
                    EQUIP_ARMOR,
                    USE_SELECTED_ITEM,
                    SWAP_TO_OFFHAND,
                    DROP_ITEM,
                    BREAK_BLOCK,
                    PLACE_BLOCK,
                    ATTACK_NEAREST,
                    INTERACT,
                    REPORT_STATUS,
                    CHAT,
                    GOTO,
                    INVENTORY_QUERY,
                    EQUIP_ITEM,
                    GIVE_ITEM,
                    DEPOSIT_ITEM,
                    STASH_ITEM,
                    WITHDRAW_ITEM,
                    GET_ITEM,
                    SMELT_ITEM,
                    COLLECT_FOOD,
                    FARM_NEARBY,
                    FISH,
                    ATTACK_TARGET,
                    DEFEND_OWNER,
                    PAUSE,
                    UNPAUSE,
                    RESET_MEMORY,
                    BODY_LANGUAGE,
                    BUILD_STRUCTURE,
                    LOCATE_STRUCTURE,
                    LOCATE_LOADED_BLOCK,
                    LOCATE_LOADED_ENTITY,
                    FIND_LOADED_BIOME,
                    EXPLORE_CHUNKS -> false;
        };
    }

    public static String unsupportedReason(IntentKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        return switch (kind) {
            case USE_PORTAL -> "USE_PORTAL is unsupported: portal construction/use needs a separate reviewed safe phase";
            case TRAVEL_NETHER -> "TRAVEL_NETHER is unsupported: Nether travel needs a separate reviewed safe phase";
            case LOCATE_STRONGHOLD -> "LOCATE_STRONGHOLD is unsupported: stronghold location needs a separate reviewed safe phase";
            case END_GAME_TASK -> "END_GAME_TASK is unsupported: End/dragon/speedrun tasks need separate reviewed safe phases";
            default -> throw new IllegalArgumentException(kind.name() + " is not an unsupported advanced kind");
        };
    }
}
