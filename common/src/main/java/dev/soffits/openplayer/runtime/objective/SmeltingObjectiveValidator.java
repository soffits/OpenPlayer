package dev.soffits.openplayer.runtime.objective;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SmeltingObjectiveValidator {
    private SmeltingObjectiveValidator() {
    }

    public static ObjectiveProgress validate(SmeltingStatus status) {
        if (status == null || !status.adapterAvailable()) {
            return new ObjectiveProgress(false, false, Map.of(), List.of("missing smelting adapter"),
                    "report missing adapter");
        }
        LinkedHashMap<String, Integer> missing = new LinkedHashMap<>();
        java.util.ArrayList<String> blockers = new java.util.ArrayList<>();
        if (!status.furnaceAccessible()) {
            blockers.add("furnace container not accessible");
        }
        if (!status.inputAvailable()) {
            missing.put(status.inputItemId(), status.inputCount());
        }
        if (!status.fuelAvailable()) {
            blockers.add("fuel missing");
        }
        if (!status.outputSlotAvailable()) {
            blockers.add("output slot blocked");
        }
        if (status.timedOut()) {
            blockers.add("smelting timed out before progress completed");
        }
        if (status.completedOutputCount() >= status.requestedOutputCount()) {
            return new ObjectiveProgress(true, true, Map.of(), List.of(), "smelting output verified");
        }
        if (missing.isEmpty() && blockers.isEmpty()) {
            blockers.add("smelting progress incomplete");
        }
        return new ObjectiveProgress(true, false, missing, List.copyOf(blockers), "continue smelting or inspect furnace state");
    }

    public record SmeltingStatus(boolean adapterAvailable, boolean furnaceAccessible, boolean inputAvailable,
                                 String inputItemId, int inputCount, boolean fuelAvailable, boolean outputSlotAvailable,
                                 boolean timedOut, int completedOutputCount, int requestedOutputCount) {
        public SmeltingStatus {
            inputItemId = inputItemId == null || inputItemId.isBlank() ? "unknown" : inputItemId.trim();
            inputCount = Math.max(0, inputCount);
            completedOutputCount = Math.max(0, completedOutputCount);
            requestedOutputCount = Math.max(1, requestedOutputCount);
        }
    }
}
