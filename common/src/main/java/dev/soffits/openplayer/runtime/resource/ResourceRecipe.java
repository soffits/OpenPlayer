package dev.soffits.openplayer.runtime.resource;

import java.util.Map;

public record ResourceRecipe(String outputItemId, int outputCount, Map<String, Integer> inputs,
                             boolean requiresCraftingTable, boolean adapterAvailable) {
    public ResourceRecipe {
        if (outputItemId == null || outputItemId.isBlank()) {
            throw new IllegalArgumentException("outputItemId cannot be blank");
        }
        outputCount = Math.max(1, outputCount);
        inputs = Map.copyOf(inputs == null ? Map.of() : inputs);
    }
}
