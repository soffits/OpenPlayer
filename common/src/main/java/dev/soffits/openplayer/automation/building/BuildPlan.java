package dev.soffits.openplayer.automation.building;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record BuildPlan(BuildPrimitive primitive, BlockPos origin, BuildSize size,
                        ResourceLocation materialId, List<BlockPos> positions) {
    public BuildPlan {
        if (primitive == null) {
            throw new IllegalArgumentException("primitive cannot be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("origin cannot be null");
        }
        if (size == null) {
            throw new IllegalArgumentException("size cannot be null");
        }
        if (materialId == null) {
            throw new IllegalArgumentException("materialId cannot be null");
        }
        if (positions == null || positions.isEmpty()) {
            throw new IllegalArgumentException("positions cannot be blank");
        }
        positions = List.copyOf(positions);
    }

    public int blockCount() {
        return positions.size();
    }
}
