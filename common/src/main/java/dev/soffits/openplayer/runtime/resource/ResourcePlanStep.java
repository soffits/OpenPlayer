package dev.soffits.openplayer.runtime.resource;

import java.util.List;

public record ResourcePlanStep(ResourcePlanKind kind, String targetId, int count, String primitive, List<String> blockers,
                               int failCount, List<ResourcePlanStep> children) {
    public ResourcePlanStep {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        targetId = sanitize(targetId, "unknown");
        primitive = sanitize(primitive, "none");
        count = Math.max(0, count);
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
        children = List.copyOf(children == null ? List.of() : children);
        failCount = Math.max(0, failCount);
    }

    public boolean ready() {
        return blockers.isEmpty() && children.stream().allMatch(ResourcePlanStep::completedOrReadyLeaf);
    }

    public boolean completedOrReadyLeaf() {
        return blockers.isEmpty() && children.isEmpty();
    }

    private static String sanitize(String value, String fallback) {
        String sanitized = value == null || value.isBlank() ? fallback : value.trim();
        return sanitized.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }
}
