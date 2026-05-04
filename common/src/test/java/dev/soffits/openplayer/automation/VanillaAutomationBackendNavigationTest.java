package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.automation.navigation.NavigationTarget;
import dev.soffits.openplayer.automation.navigation.NavigationTargetKind;
import net.minecraft.world.phys.Vec3;

public final class VanillaAutomationBackendNavigationTest {
    private VanillaAutomationBackendNavigationTest() {
    }

    public static void main(String[] args) {
        droppedItemCollectionUsesPositionNavigationTarget();
        droppedItemCollectionUsesPrimitiveRejectionReason();
    }

    private static void droppedItemCollectionUsesPositionNavigationTarget() {
        NavigationTarget target = VanillaAutomationBackend.droppedItemNavigationTarget(new Vec3(12.25D, 64.0D, -3.75D));

        require(target.kind() == NavigationTargetKind.POSITION, "dropped item collection must navigate to position");
        require(target.summary().startsWith("pos("), "dropped item navigation summary must expose position target");
        require(!target.summary().startsWith("entity("), "dropped item collection must not use entity navigation target");
    }

    private static void droppedItemCollectionUsesPrimitiveRejectionReason() {
        require("navigation_item_position_rejected".equals(VanillaAutomationBackend.DROPPED_ITEM_NAVIGATION_REJECTED_REASON),
                "dropped item navigation rejection must be primitive-specific");
        require(!"navigation_entity_rejected".equals(VanillaAutomationBackend.DROPPED_ITEM_NAVIGATION_REJECTED_REASON),
                "dropped item navigation must not report entity rejection");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
