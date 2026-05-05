package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.IntentKind;
import net.minecraft.core.BlockPos;

public final class VanillaAutomationBackend implements AutomationBackend {
    public static final String NAME = VanillaAutomationControllerBase.NAME;
    public static final double PLAYER_LIKE_NAVIGATION_SPEED = VanillaAutomationControllerBase.PLAYER_LIKE_NAVIGATION_SPEED;
    public static final double PLAYER_LIKE_MOVEMENT_ATTRIBUTE_SPEED = VanillaAutomationControllerBase.PLAYER_LIKE_MOVEMENT_ATTRIBUTE_SPEED;
    static final String DROPPED_ITEM_NAVIGATION_REJECTED_REASON = VanillaAutomationControllerBase.DROPPED_ITEM_NAVIGATION_REJECTED_REASON;

    static boolean isLocalWorldOrInventoryAction(IntentKind kind) {
        return VanillaAutomationControllerBase.isLocalWorldOrInventoryAction(kind);
    }

    static dev.soffits.openplayer.automation.navigation.NavigationTarget droppedItemNavigationTarget(
            String itemTypeId,
            BlockPos standPosition
    ) {
        return VanillaAutomationControllerBase.droppedItemNavigationTarget(itemTypeId, standPosition);
    }

    static String itemPickupCompletionReason(int inventoryDelta, boolean targetAlive, boolean closeEnough,
                                             boolean inventoryCanAccept) {
        return VanillaAutomationControllerBase.itemPickupCompletionReason(
                inventoryDelta,
                targetAlive,
                closeEnough,
                inventoryCanAccept
        );
    }

    static String blockBreakSummary(String blockId, BlockPos target, String inventoryDelta, String nearbyDropDelta) {
        return VanillaAutomationControllerBase.blockBreakSummary(blockId, target, inventoryDelta, nearbyDropDelta);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AutomationBackendStatus status() {
        return new AutomationBackendStatus(NAME, AutomationBackendState.AVAILABLE, "Vanilla Minecraft NPC tasks");
    }

    @Override
    public AutomationController createController(OpenPlayerNpcEntity entity) {
        return new VanillaAutomationController(entity);
    }
}
