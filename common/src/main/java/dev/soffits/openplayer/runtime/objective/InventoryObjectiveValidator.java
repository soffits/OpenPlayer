package dev.soffits.openplayer.runtime.objective;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InventoryObjectiveValidator {
    private InventoryObjectiveValidator() {
    }

    public static ObjectiveProgress validate(Map<String, Integer> inventoryCounts, Map<String, Integer> requiredItems) {
        Map<String, Integer> inventory = inventoryCounts == null ? Map.of() : inventoryCounts;
        Map<String, Integer> required = requiredItems == null ? Map.of() : requiredItems;
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int requiredCount = Math.max(0, entry.getValue());
            int current = Math.max(0, inventory.getOrDefault(entry.getKey(), 0));
            if (current < requiredCount) {
                missing.put(entry.getKey(), requiredCount - current);
            }
        }
        if (missing.isEmpty()) {
            return new ObjectiveProgress(true, true, Map.of(), List.of(), "report objective completed");
        }
        return new ObjectiveProgress(true, false, missing, List.of("inventory objective missing items"),
                "collect or craft missing inventory items");
    }
}
