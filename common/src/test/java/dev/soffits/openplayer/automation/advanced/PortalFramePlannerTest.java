package dev.soffits.openplayer.automation.advanced;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class PortalFramePlannerTest {
    private PortalFramePlannerTest() {
    }

    public static void main(String[] args) {
        buildsFourByFiveOuterFrameWithTwoByThreeInterior();
        supportsDeterministicZAxisFrame();
    }

    private static void buildsFourByFiveOuterFrameWithTwoByThreeInterior() {
        PortalFramePlan plan = PortalFramePlanner.plan(new BlockPos(10, 64, -3), Direction.Axis.X);
        require(plan.framePositions().size() == PortalFramePlan.REQUIRED_OBSIDIAN,
                "Portal frame should require 14 obsidian blocks");
        require(plan.interiorPositions().size() == 6, "Portal frame should have a 2x3 interior");
        require(plan.framePositions().contains(new BlockPos(10, 64, -3)), "Frame should include lower-left corner");
        require(plan.framePositions().contains(new BlockPos(13, 68, -3)), "Frame should include upper-right corner");
        require(!plan.framePositions().contains(new BlockPos(11, 65, -3)), "Frame should exclude interior air");
        require(plan.interiorPositions().contains(new BlockPos(11, 65, -3)), "Interior should include lower inner space");
        require(plan.interiorPositions().contains(new BlockPos(12, 67, -3)), "Interior should include upper inner space");
    }

    private static void supportsDeterministicZAxisFrame() {
        PortalFramePlan plan = PortalFramePlanner.plan(new BlockPos(0, 70, 0), Direction.Axis.Z);
        require(plan.framePositions().contains(new BlockPos(0, 70, 3)), "Z-axis frame should extend on Z");
        require(plan.framePositions().contains(new BlockPos(0, 74, 3)), "Z-axis frame should include top Z corner");
        require(plan.interiorPositions().contains(new BlockPos(0, 71, 1)), "Z-axis interior should extend on Z");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
