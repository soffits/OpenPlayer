package dev.soffits.openplayer.aicore;

import net.minecraft.core.BlockPos;

public final class AICoreNpcSessionState {
    private BlockPos containerPos;
    private BlockPos furnacePos;

    public void openContainer(BlockPos pos, boolean furnace) {
        if (pos == null) {
            clear();
            return;
        }
        containerPos = pos.immutable();
        furnacePos = furnace ? pos.immutable() : null;
    }

    public void openFurnace(BlockPos pos) {
        if (pos == null) {
            clear();
            return;
        }
        containerPos = pos.immutable();
        furnacePos = pos.immutable();
    }

    public BlockPos containerPos() {
        return containerPos;
    }

    public BlockPos furnacePos() {
        return furnacePos;
    }

    public boolean hasFurnaceSession() {
        return furnacePos != null;
    }

    public void clear() {
        containerPos = null;
        furnacePos = null;
    }
}
