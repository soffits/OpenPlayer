package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.automation.navigation.NavigationTarget;
import dev.soffits.openplayer.automation.navigation.NavigationTargetKind;

public final class VanillaAutomationBackendNavigationTest {
    private VanillaAutomationBackendNavigationTest() {
    }

    public static void main(String[] args) {
        droppedItemCollectionUsesStandPositionNavigationTarget();
        droppedItemCollectionUsesPrimitiveRejectionReason();
        itemPickupCompletionRequiresInventoryDeltaOrCloseDisappearance();
        blockBreakSummaryIncludesPostActionFacts();
    }

    private static void droppedItemCollectionUsesStandPositionNavigationTarget() {
        NavigationTarget target = VanillaAutomationBackend.droppedItemNavigationTarget(
                "minecraft:cobblestone", new net.minecraft.core.BlockPos(1, 64, -2)
        );

        require(target.kind() == NavigationTargetKind.POSITION, "dropped item collection must navigate to a pickup stand position");
        require(target.summary().equals("pos(1.5,64.0,-1.5)"), "dropped item navigation summary must expose stand position target");
        require(!target.summary().startsWith("entity("), "dropped item collection must not navigate to the item entity");
    }

    private static void droppedItemCollectionUsesPrimitiveRejectionReason() {
        require("item_navigation_rejected".equals(VanillaAutomationBackend.DROPPED_ITEM_NAVIGATION_REJECTED_REASON),
                "dropped item navigation rejection must describe item entity/range navigation");
        require(!"navigation_item_position_rejected".equals(VanillaAutomationBackend.DROPPED_ITEM_NAVIGATION_REJECTED_REASON),
                "dropped item collection must not expose the old precise-position rejection reason");
    }

    private static void itemPickupCompletionRequiresInventoryDeltaOrCloseDisappearance() {
        require(VanillaAutomationBackend.itemPickupCompletionReason(1, true, true, true).startsWith("picked_up"),
                "positive inventory delta must complete pickup");
        require(VanillaAutomationBackend.itemPickupCompletionReason(0, false, true, true).startsWith("picked_up"),
                "close item disappearance may complete pickup");
        require("item_target_lost".equals(VanillaAutomationBackend.itemPickupCompletionReason(0, false, false, true)),
                "distant target disappearance must be treated as target lost");
        require("inventory_full".equals(VanillaAutomationBackend.itemPickupCompletionReason(0, true, true, false)),
                "full inventory must be reported explicitly");
        require("item_not_picked_up".equals(VanillaAutomationBackend.itemPickupCompletionReason(0, true, true, true)),
                "nearby live item without inventory delta must not complete pickup");
    }

    private static void blockBreakSummaryIncludesPostActionFacts() {
        String summary = VanillaAutomationBackend.blockBreakSummary(
                "minecraft:stone",
                new net.minecraft.core.BlockPos(1, 64, -2),
                "minecraft:cobblestone+1",
                "none"
        );
        require(summary.contains("block=minecraft:stone"), "summary must include broken block id");
        require(summary.contains("target=1, 64, -2"), "summary must include target coordinate");
        require(summary.contains("inventory_delta=minecraft:cobblestone+1"), "summary must include inventory delta");
        require(summary.contains("nearby_drop_delta=none"), "summary must include nearby drop delta");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
