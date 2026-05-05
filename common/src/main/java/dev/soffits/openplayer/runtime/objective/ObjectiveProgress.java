package dev.soffits.openplayer.runtime.objective;

import java.util.List;
import java.util.Map;

public record ObjectiveProgress(
        boolean supported,
        boolean completed,
        Map<String, Integer> missingItems,
        List<String> blockerReasons,
        String suggestedNextRuntimeAction
) {
    public ObjectiveProgress {
        missingItems = Map.copyOf(missingItems == null ? Map.of() : missingItems);
        blockerReasons = List.copyOf(blockerReasons == null ? List.of() : blockerReasons);
        suggestedNextRuntimeAction = suggestedNextRuntimeAction == null || suggestedNextRuntimeAction.isBlank()
                ? "none" : suggestedNextRuntimeAction.trim();
        if (completed && !supported) {
            throw new IllegalArgumentException("unsupported objectives cannot be completed");
        }
    }
}
