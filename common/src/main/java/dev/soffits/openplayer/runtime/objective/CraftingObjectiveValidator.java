package dev.soffits.openplayer.runtime.objective;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.runtime.resource.ResourceRecipe;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CraftingObjectiveValidator {
    private CraftingObjectiveValidator() {
    }

    public static ObjectiveProgress validate(ResourceRecipe recipe, Map<String, Integer> inventoryCounts,
                                             boolean craftingAdapterAvailable, boolean craftingTableAvailable,
                                             int requestedOutputCount) {
        if (recipe == null) {
            return new ObjectiveProgress(false, false, Map.of(), List.of("missing recipe adapter or unavailable recipe"),
                    "query recipe before crafting");
        }
        if (!craftingAdapterAvailable || !recipe.adapterAvailable()) {
            return new ObjectiveProgress(false, false, Map.of(), List.of("missing crafting execution adapter"),
                    "report missing adapter");
        }
        Map<String, Integer> inventory = inventoryCounts == null ? Map.of() : inventoryCounts;
        Map<String, Integer> missing = new LinkedHashMap<>();
        int craftsNeeded = (int) Math.ceil((double) Math.max(1, requestedOutputCount) / recipe.outputCount());
        for (Map.Entry<String, Integer> input : recipe.inputs().entrySet()) {
            int required = Math.max(0, input.getValue()) * craftsNeeded;
            int carried = Math.max(0, inventory.getOrDefault(input.getKey(), 0));
            if (carried < required) {
                missing.put(input.getKey(), required - carried);
            }
        }
        if (recipe.requiresCraftingTable() && !craftingTableAvailable
                && inventory.getOrDefault(OpenPlayerConstants.MINECRAFT_CRAFTING_TABLE_ID, 0) < 1) {
            missing.put(OpenPlayerConstants.MINECRAFT_CRAFTING_TABLE_ID, 1);
        }
        if (missing.isEmpty()) {
            return new ObjectiveProgress(true, true, Map.of(), List.of(), "crafting inputs and workstation verified");
        }
        return new ObjectiveProgress(true, false, missing, List.of("crafting objective missing inputs or workstation"),
                "collect missing inputs or locate workstation");
    }
}
