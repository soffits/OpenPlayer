package dev.soffits.openplayer.automation.policy;

import net.minecraft.resources.ResourceLocation;

public record MovementProfile(
        ResourceLocation id,
        boolean canBreakObstacles,
        boolean canPlaceScaffold,
        int maxFallDistance,
        boolean avoidLiquids,
        boolean avoidHostiles,
        BlockSafetyPolicy blocks,
        EntitySafetyPolicy entities
) {
    public MovementProfile {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (maxFallDistance < 0) {
            maxFallDistance = 0;
        }
        blocks = blocks == null ? new BlockSafetyPolicy(java.util.Set.of(), java.util.Set.of(), java.util.Set.of()) : blocks;
        entities = entities == null ? new EntitySafetyPolicy(java.util.Set.of(), java.util.Set.of(), java.util.Set.of()) : entities;
    }

    public MovementProfile boundBy(MovementProfile caps) {
        if (caps == null) {
            return this;
        }
        return new MovementProfile(
                id,
                canBreakObstacles && caps.canBreakObstacles,
                canPlaceScaffold && caps.canPlaceScaffold,
                Math.min(maxFallDistance, caps.maxFallDistance),
                avoidLiquids || caps.avoidLiquids,
                avoidHostiles || caps.avoidHostiles,
                blocks.boundBy(caps.blocks),
                entities.boundBy(caps.entities)
        );
    }
}
