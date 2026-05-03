package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentPolicies;
import java.util.EnumSet;
import java.util.Set;

public final class AutomationWorldActionPolicyTest {
    public static void main(String[] args) {
        Set<IntentKind> gatedActions = EnumSet.of(
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
                IntentKind.ATTACK_TARGET,
                IntentKind.DEFEND_OWNER,
                IntentKind.BUILD_STRUCTURE
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
