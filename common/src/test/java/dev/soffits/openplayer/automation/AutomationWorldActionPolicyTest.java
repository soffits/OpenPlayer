package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentPolicies;
import java.util.EnumSet;
import java.util.Set;

public final class AutomationWorldActionPolicyTest {
    public static void main(String[] args) {
        Set<IntentKind> gatedActions = EnumSet.of(
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
                IntentKind.FIND_LOADED_BIOME
        );

        for (IntentKind kind : IntentKind.values()) {
            boolean expectedGated = gatedActions.contains(kind);
            require(
                    RuntimeIntentPolicies.isLocalWorldOrInventoryAction(kind) == expectedGated,
                    kind + " allowWorldActions gate classification mismatch"
            );
            require(
                    VanillaAutomationBackend.isLocalWorldOrInventoryAction(kind)
                            == RuntimeIntentPolicies.isLocalWorldOrInventoryAction(kind),
                    kind + " backend and runtime gate classification mismatch"
            );
        }

        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.STOP),
                "STOP must remain available when allowWorldActions is false");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.MOVE),
                "MOVE must remain available when allowWorldActions is false");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.LOOK),
                "LOOK must remain available when allowWorldActions is false");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.FOLLOW_OWNER),
                "FOLLOW_OWNER must remain available when allowWorldActions is false");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.PATROL),
                "PATROL must remain available when allowWorldActions is false");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.REPORT_STATUS),
                "REPORT_STATUS must remain available when allowWorldActions is false");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.GOTO),
                "GOTO planned intent must not use the world-action gate yet");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.INVENTORY_QUERY),
                "INVENTORY_QUERY planned intent must not use the world-action gate yet");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.PAUSE),
                "PAUSE planned intent must not use the world-action gate yet");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.UNPAUSE),
                "UNPAUSE planned intent must not use the world-action gate yet");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.RESET_MEMORY),
                "RESET_MEMORY planned intent must not use the world-action gate yet");
        require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.BODY_LANGUAGE),
                "BODY_LANGUAGE planned intent must not use the world-action gate yet");
        require(RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.INTERACT),
                "INTERACT must use the world-action gate for safe runtime interactions");
        require(RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.ATTACK_TARGET),
                "ATTACK_TARGET must use the world-action gate for targeted combat");
        require(RuntimeIntentPolicies.isLocalWorldOrInventoryAction(IntentKind.LOCATE_LOADED_BLOCK),
                "LOCATE_LOADED_BLOCK must use the world-action gate for loaded-world reconnaissance");
        require(!RuntimeIntentPolicies.allIntentKindNames().contains("TRAVEL_NETHER"),
                "TRAVEL_NETHER must not remain in the runtime intent surface");
        require(VanillaAutomationBackend.PLAYER_LIKE_NAVIGATION_SPEED >= 1.2D,
                "NPC navigation speed must be faster than the prior slow default");
        require(VanillaAutomationBackend.PLAYER_LIKE_NAVIGATION_SPEED <= 1.5D,
                "NPC navigation speed must stay within a non-absurd player-like range");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
