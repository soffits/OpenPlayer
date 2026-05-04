package dev.soffits.openplayer.runtime.validation;

import dev.soffits.openplayer.aicore.MinecraftPrimitiveTools;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

public final class RuntimeIntentPolicies {
    private static final EnumSet<IntentKind> LOCAL_WORLD_OR_INVENTORY_ACTIONS = EnumSet.of(
            IntentKind.COLLECT_ITEMS,
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
            IntentKind.INTERACT,
            IntentKind.ATTACK_TARGET,
            IntentKind.LOCATE_LOADED_BLOCK,
            IntentKind.LOCATE_LOADED_ENTITY,
            IntentKind.FIND_LOADED_BIOME,
            IntentKind.CRAFT
    );

    private RuntimeIntentPolicies() {
    }

    public static boolean isLocalWorldOrInventoryAction(IntentKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        return switch (kind) {
            case COLLECT_ITEMS,
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
                    INTERACT,
                    ATTACK_TARGET,
                    LOCATE_LOADED_BLOCK,
                    LOCATE_LOADED_ENTITY,
                    FIND_LOADED_BIOME,
                    CRAFT -> true;
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
                    BODY_LANGUAGE,
                    PROVIDER_PLAN -> false;
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

    public static String providerIntentKindNames() {
        return "CHAT, UNAVAILABLE, " + MinecraftPrimitiveTools.providerToolNames();
    }
}
