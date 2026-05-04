package dev.soffits.openplayer.runtime.validation;

import dev.soffits.openplayer.intent.IntentKind;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

public final class RuntimeIntentPolicies {
    private static final EnumSet<IntentKind> LOCAL_WORLD_OR_INVENTORY_ACTIONS = EnumSet.of(
            IntentKind.COLLECT_ITEMS,
            IntentKind.EQUIP_BEST_ITEM,
            IntentKind.EQUIP_ARMOR,
            IntentKind.USE_SELECTED_ITEM,
            IntentKind.SWAP_TO_OFFHAND,
            IntentKind.DROP_ITEM,
            IntentKind.BREAK_BLOCK,
            IntentKind.PLACE_BLOCK,
            IntentKind.ATTACK_NEAREST,
            IntentKind.GUARD_OWNER,
            IntentKind.EQUIP_ITEM,
            IntentKind.GIVE_ITEM,
            IntentKind.DEPOSIT_ITEM,
            IntentKind.STASH_ITEM,
            IntentKind.WITHDRAW_ITEM,
            IntentKind.GET_ITEM,
            IntentKind.SMELT_ITEM,
            IntentKind.COLLECT_FOOD,
            IntentKind.FARM_NEARBY,
            IntentKind.FISH,
            IntentKind.INTERACT,
            IntentKind.ATTACK_TARGET,
            IntentKind.DEFEND_OWNER,
            IntentKind.BUILD_STRUCTURE,
            IntentKind.LOCATE_LOADED_BLOCK,
            IntentKind.LOCATE_LOADED_ENTITY,
            IntentKind.FIND_LOADED_BIOME,
            IntentKind.LOCATE_STRUCTURE,
            IntentKind.EXPLORE_CHUNKS,
            IntentKind.USE_PORTAL,
            IntentKind.TRAVEL_NETHER,
            IntentKind.LOCATE_STRONGHOLD,
            IntentKind.END_GAME_TASK
    );

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
                    GUARD_OWNER,
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
                    INTERACT,
                    ATTACK_TARGET,
                    DEFEND_OWNER,
                    BUILD_STRUCTURE,
                    LOCATE_LOADED_BLOCK,
                    LOCATE_LOADED_ENTITY,
                    FIND_LOADED_BIOME,
                    LOCATE_STRUCTURE,
                    EXPLORE_CHUNKS,
                    USE_PORTAL,
                    TRAVEL_NETHER,
                    LOCATE_STRONGHOLD,
                    END_GAME_TASK -> true;
            case UNAVAILABLE,
                    OBSERVE,
                    STOP,
                    MOVE,
                    LOOK,
                    FOLLOW_OWNER,
                    PATROL,
                    REPORT_STATUS,
                    CHAT,
                    GOTO,
                    INVENTORY_QUERY,
                    PAUSE,
                    UNPAUSE,
                    RESET_MEMORY,
                    BODY_LANGUAGE -> false;
        };
    }

    public static EnumSet<IntentKind> localWorldOrInventoryActions() {
        return EnumSet.copyOf(LOCAL_WORLD_OR_INVENTORY_ACTIONS);
    }

    public static String localWorldOrInventoryActionNames() {
        return LOCAL_WORLD_OR_INVENTORY_ACTIONS.stream()
                .map(IntentKind::name)
                .collect(Collectors.joining(", "));
    }

    public static String allIntentKindNames() {
        return Arrays.stream(IntentKind.values())
                .map(IntentKind::name)
                .collect(Collectors.joining(", "));
    }
}
