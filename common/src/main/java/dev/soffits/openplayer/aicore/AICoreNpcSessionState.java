package dev.soffits.openplayer.aicore;

import net.minecraft.core.BlockPos;

public final class AICoreNpcSessionState {
    private BlockPos containerPos;
    private boolean slotRestrictedContainer;

    public void openContainer(BlockPos pos, boolean slotRestricted) {
        if (pos == null) {
            clear();
            return;
        }
        containerPos = pos.immutable();
        slotRestrictedContainer = slotRestricted;
    }

    public BlockPos containerPos() {
        return containerPos;
    }

    public boolean hasSlotRestrictedContainerSession() {
        return slotRestrictedContainer;
    }

    public void clear() {
        containerPos = null;
        slotRestrictedContainer = false;
    }
}
