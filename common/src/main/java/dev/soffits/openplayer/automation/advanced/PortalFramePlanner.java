package dev.soffits.openplayer.automation.advanced;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class PortalFramePlanner {
    private PortalFramePlanner() {
    }

    public static PortalFramePlan plan(BlockPos origin, Direction.Axis horizontalAxis) {
        if (origin == null) {
            throw new IllegalArgumentException("origin cannot be null");
        }
        if (horizontalAxis != Direction.Axis.X && horizontalAxis != Direction.Axis.Z) {
            throw new IllegalArgumentException("horizontalAxis must be X or Z");
        }
        List<BlockPos> frame = new ArrayList<>(PortalFramePlan.REQUIRED_OBSIDIAN);
        List<BlockPos> interior = new ArrayList<>(6);
        for (int y = 0; y < 5; y++) {
            for (int horizontal = 0; horizontal < 4; horizontal++) {
                BlockPos pos = offset(origin, horizontalAxis, horizontal, y);
                boolean outer = y == 0 || y == 4 || horizontal == 0 || horizontal == 3;
                if (outer) {
                    frame.add(pos);
                } else {
                    interior.add(pos);
                }
            }
        }
        return new PortalFramePlan(origin.immutable(), horizontalAxis, frame, interior);
    }

    public static BlockPos offset(BlockPos origin, Direction.Axis horizontalAxis, int horizontal, int y) {
        if (horizontalAxis == Direction.Axis.X) {
            return origin.offset(horizontal, y, 0).immutable();
        }
        return origin.offset(0, y, horizontal).immutable();
    }
}
