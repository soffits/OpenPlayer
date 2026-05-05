package dev.soffits.openplayer.runtime.resource;

import java.util.List;
import java.util.Optional;

public record ResourcePlanResult(ResourcePlanStep root, Optional<ResourcePlanStep> nextReadyLeaf,
                                 List<String> missingItems, List<String> blockers, boolean completed) {
    public ResourcePlanResult {
        if (root == null) {
            throw new IllegalArgumentException("root cannot be null");
        }
        nextReadyLeaf = nextReadyLeaf == null ? Optional.empty() : nextReadyLeaf;
        missingItems = List.copyOf(missingItems == null ? List.of() : missingItems);
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
    }
}
