package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.intent.IntentKind;
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
                IntentKind.GUARD_OWNER
        );

        for (IntentKind kind : IntentKind.values()) {
            boolean expectedGated = gatedActions.contains(kind);
            require(
                    VanillaAutomationBackend.isLocalWorldOrInventoryAction(kind) == expectedGated,
                    kind + " allowWorldActions gate classification mismatch"
            );
        }

        require(!VanillaAutomationBackend.isLocalWorldOrInventoryAction(IntentKind.STOP),
                "STOP must remain available when allowWorldActions is false");
        require(!VanillaAutomationBackend.isLocalWorldOrInventoryAction(IntentKind.MOVE),
                "MOVE must remain available when allowWorldActions is false");
        require(!VanillaAutomationBackend.isLocalWorldOrInventoryAction(IntentKind.LOOK),
                "LOOK must remain available when allowWorldActions is false");
        require(!VanillaAutomationBackend.isLocalWorldOrInventoryAction(IntentKind.FOLLOW_OWNER),
                "FOLLOW_OWNER must remain available when allowWorldActions is false");
        require(!VanillaAutomationBackend.isLocalWorldOrInventoryAction(IntentKind.PATROL),
                "PATROL must remain available when allowWorldActions is false");
        require(!VanillaAutomationBackend.isLocalWorldOrInventoryAction(IntentKind.REPORT_STATUS),
                "REPORT_STATUS must remain available when allowWorldActions is false");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
