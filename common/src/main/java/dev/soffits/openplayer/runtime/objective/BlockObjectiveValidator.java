package dev.soffits.openplayer.runtime.objective;

import java.util.List;
import java.util.Map;

public final class BlockObjectiveValidator {
    private BlockObjectiveValidator() {
    }

    public static ObjectiveProgress validate(Map<String, String> observedBlocksByPosition, String position, String expectedBlockId) {
        if (observedBlocksByPosition == null) {
            return new ObjectiveProgress(false, false, Map.of(), List.of("missing world query adapter"),
                    "report missing adapter");
        }
        if (position == null || position.isBlank() || expectedBlockId == null || expectedBlockId.isBlank()) {
            return new ObjectiveProgress(false, false, Map.of(), List.of("invalid block objective"),
                    "ask for an unambiguous block objective");
        }
        String actual = observedBlocksByPosition.get(position.trim());
        if (expectedBlockId.trim().equals(actual)) {
            return new ObjectiveProgress(true, true, Map.of(), List.of(), "report block objective completed");
        }
        String blocker = actual == null ? "target block is unobserved" : "target block mismatch actual=" + actual;
        return new ObjectiveProgress(true, false, Map.of(), List.of(blocker), "move closer or place expected block if permitted");
    }
}
