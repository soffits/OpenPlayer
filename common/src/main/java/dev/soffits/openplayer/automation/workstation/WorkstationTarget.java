package dev.soffits.openplayer.automation.workstation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;

public record WorkstationTarget(BlockPos blockPos, WorkstationCapability capability, Container container) {
    public WorkstationTarget {
        blockPos = blockPos.immutable();
    }

    public boolean hasContainerAdapter() {
        return container != null;
    }
}
