package dev.soffits.openplayer.runtime.objective;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeliveryObjectiveValidator {
    private DeliveryObjectiveValidator() {
    }

    public static ObjectiveProgress validate(Map<String, Integer> targetBefore, Map<String, Integer> targetAfter,
                                              Map<String, Integer> deliveredItems) {
        if (targetAfter == null) {
            return new ObjectiveProgress(false, false, Map.of(), List.of("missing target inventory adapter"),
                    "report missing adapter");
        }
        Map<String, Integer> before = targetBefore == null ? Map.of() : targetBefore;
        Map<String, Integer> required = deliveredItems == null ? Map.of() : deliveredItems;
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int delta = Math.max(0, targetAfter.getOrDefault(entry.getKey(), 0) - before.getOrDefault(entry.getKey(), 0));
            int requiredDelta = Math.max(0, entry.getValue());
            if (delta < requiredDelta) {
                missing.put(entry.getKey(), requiredDelta - delta);
            }
        }
        if (missing.isEmpty()) {
            return new ObjectiveProgress(true, true, Map.of(), List.of(), "report delivery completed");
        }
        return new ObjectiveProgress(true, false, missing, List.of("delivery not verified from target inventory delta"),
                "retry delivery or observe dropped item near target");
    }

    public static ObjectiveProgress validate(Map<String, Integer> targetBefore, Map<String, Integer> targetAfter,
                                             Map<String, Integer> deliveredItems, DeliveryDropEvidence dropEvidence) {
        ObjectiveProgress inventoryProgress = validate(targetBefore, targetAfter, deliveredItems);
        if (inventoryProgress.completed()) {
            return inventoryProgress;
        }
        if (dropEvidence == null) {
            return inventoryProgress;
        }
        if (!dropEvidence.adapterAvailable()) {
            return new ObjectiveProgress(false, false, inventoryProgress.missingItems(),
                    List.of("missing dropped item delivery observation adapter"), "report missing adapter");
        }
        Map<String, Integer> required = deliveredItems == null ? Map.of() : deliveredItems;
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int visible = Math.max(0, dropEvidence.visibleCollectableItems().getOrDefault(entry.getKey(), 0));
            int requiredCount = Math.max(0, entry.getValue());
            if (visible < requiredCount) {
                missing.put(entry.getKey(), requiredCount - visible);
            }
        }
        if (missing.isEmpty() && dropEvidence.targetWithinRange() && dropEvidence.itemEntityVisible()
                && dropEvidence.collectable()) {
            return new ObjectiveProgress(true, true, Map.of(), List.of(),
                    "delivery verified by visible collectable dropped item near target");
        }
        return new ObjectiveProgress(true, false, missing,
                List.of("delivery drop not verified near target; proximity=" + dropEvidence.distanceToTarget()
                        + " owner_visible=" + dropEvidence.ownerVisible()),
                "move closer, drop item again, or re-observe target inventory");
    }

    public record DeliveryDropEvidence(boolean adapterAvailable, boolean targetWithinRange, double distanceToTarget,
                                       boolean itemEntityVisible, boolean collectable, boolean ownerVisible,
                                       Map<String, Integer> visibleCollectableItems) {
        public DeliveryDropEvidence {
            distanceToTarget = Double.isFinite(distanceToTarget) ? Math.max(0.0D, distanceToTarget) : Double.MAX_VALUE;
            visibleCollectableItems = Map.copyOf(visibleCollectableItems == null ? Map.of() : visibleCollectableItems);
        }
    }
}
