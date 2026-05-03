package dev.soffits.openplayer.runtime.context;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record RuntimeNearbySnapshot(
        Map<String, Integer> blockCounts,
        List<BlockTargetSnapshot> nearestBlockTargets,
        Map<String, Integer> droppedItemCounts,
        List<RuntimeEntitySnapshot> hostiles,
        List<RuntimeNamedEntitySnapshot> players,
        List<RuntimeNamedEntitySnapshot> openPlayerNpcs
) {
    public RuntimeNearbySnapshot {
        if (blockCounts == null) {
            throw new IllegalArgumentException("blockCounts cannot be null");
        }
        if (nearestBlockTargets == null) {
            throw new IllegalArgumentException("nearestBlockTargets cannot be null");
        }
        if (droppedItemCounts == null) {
            throw new IllegalArgumentException("droppedItemCounts cannot be null");
        }
        if (hostiles == null) {
            throw new IllegalArgumentException("hostiles cannot be null");
        }
        if (players == null) {
            throw new IllegalArgumentException("players cannot be null");
        }
        if (openPlayerNpcs == null) {
            throw new IllegalArgumentException("openPlayerNpcs cannot be null");
        }
        blockCounts = copyCounts(blockCounts, "blockCounts");
        nearestBlockTargets = List.copyOf(nearestBlockTargets);
        droppedItemCounts = copyCounts(droppedItemCounts, "droppedItemCounts");
        hostiles = List.copyOf(hostiles);
        players = List.copyOf(players);
        openPlayerNpcs = List.copyOf(openPlayerNpcs);
    }

    public record BlockTargetSnapshot(String id, int x, int y, int z, double distanceSquared) {
        public BlockTargetSnapshot {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id cannot be blank");
            }
            if (distanceSquared < 0.0D) {
                throw new IllegalArgumentException("distanceSquared cannot be negative");
            }
            id = id.trim();
        }
    }

    public record RuntimeEntitySnapshot(String id, long distanceMeters, String direction) {
        public RuntimeEntitySnapshot {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id cannot be blank");
            }
            if (distanceMeters < 0L) {
                throw new IllegalArgumentException("distanceMeters cannot be negative");
            }
            if (direction == null || direction.isBlank()) {
                throw new IllegalArgumentException("direction cannot be blank");
            }
            id = id.trim();
            direction = direction.trim();
        }
    }

    public record RuntimeNamedEntitySnapshot(String name, long distanceMeters, String direction) {
        public RuntimeNamedEntitySnapshot {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            if (distanceMeters < 0L) {
                throw new IllegalArgumentException("distanceMeters cannot be negative");
            }
            if (direction == null || direction.isBlank()) {
                throw new IllegalArgumentException("direction cannot be blank");
            }
            name = RuntimeContextFormatter.normalizeFreeTextName(name);
            direction = direction.trim();
        }
    }

    private static Map<String, Integer> copyCounts(Map<String, Integer> counts, String fieldName) {
        Map<String, Integer> copied = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException(fieldName + " cannot contain blank keys");
            }
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException(fieldName + " cannot contain negative values");
            }
            if (entry.getValue() > 0) {
                copied.put(entry.getKey().trim(), entry.getValue());
            }
        }
        return Map.copyOf(copied);
    }
}
