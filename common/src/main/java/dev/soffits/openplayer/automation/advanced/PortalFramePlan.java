package dev.soffits.openplayer.automation.advanced;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record PortalFramePlan(
        BlockPos origin,
        Direction.Axis horizontalAxis,
        List<BlockPos> framePositions,
        List<BlockPos> interiorPositions
) {
    public static final int REQUIRED_OBSIDIAN = 14;

    public PortalFramePlan {
        if (origin == null) {
            throw new IllegalArgumentException("origin cannot be null");
        }
        if (horizontalAxis != Direction.Axis.X && horizontalAxis != Direction.Axis.Z) {
            throw new IllegalArgumentException("horizontalAxis must be X or Z");
        }
        framePositions = List.copyOf(framePositions);
        interiorPositions = List.copyOf(interiorPositions);
        if (framePositions.size() != REQUIRED_OBSIDIAN || interiorPositions.size() != 6) {
            throw new IllegalArgumentException("portal frame must be 4x5 with a 2x3 interior");
        }
    }
}
