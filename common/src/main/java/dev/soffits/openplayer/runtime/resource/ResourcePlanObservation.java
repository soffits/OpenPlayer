package dev.soffits.openplayer.runtime.resource;

import java.util.Map;
import java.util.Set;

public record ResourcePlanObservation(Map<String, Integer> inventoryCounts, Set<String> visibleLoadedBlocks,
                                      Map<String, ResourceRecipe> recipes, Map<String, Integer> failCounts,
                                      boolean collectBlockAdapterAvailable, boolean craftingAdapterAvailable,
                                      boolean deliveryAdapterAvailable, boolean policyAllowsWorldActions) {
    public ResourcePlanObservation {
        inventoryCounts = Map.copyOf(inventoryCounts == null ? Map.of() : inventoryCounts);
        visibleLoadedBlocks = Set.copyOf(visibleLoadedBlocks == null ? Set.of() : visibleLoadedBlocks);
        recipes = Map.copyOf(recipes == null ? Map.of() : recipes);
        failCounts = Map.copyOf(failCounts == null ? Map.of() : failCounts);
    }

    public int count(String itemId) {
        return Math.max(0, inventoryCounts.getOrDefault(itemId, 0));
    }

    public int failCount(String key) {
        return Math.max(0, failCounts.getOrDefault(key, 0));
    }
}
