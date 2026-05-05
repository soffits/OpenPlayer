package dev.soffits.openplayer.runtime.perception;

import java.util.List;
import java.util.Locale;

public record WorldPerceptionSnapshot(
        String source,
        String dimension,
        BlockCoordinate origin,
        int scanRadius,
        int verticalDown,
        int verticalUp,
        int scannedBlocks,
        int sampledColumns,
        boolean capped,
        boolean truncated,
        TerrainPatch terrain,
        List<HazardEvidence> hazards,
        List<SafeStandSpot> safeStandSpots,
        List<ObjectCluster> objectClusters
) {
    public static final WorldPerceptionSnapshot EMPTY = new WorldPerceptionSnapshot(
            "npc", "unknown", new BlockCoordinate(0, 0, 0), 0, 0, 0, 0, 0, false, false,
            new TerrainPatch(List.of()), List.of(), List.of(), List.of());

    public WorldPerceptionSnapshot {
        source = requireText(source, "source");
        dimension = requireText(dimension, "dimension");
        if (origin == null) {
            throw new IllegalArgumentException("origin cannot be null");
        }
        if (scanRadius < 0 || verticalDown < 0 || verticalUp < 0 || scannedBlocks < 0 || sampledColumns < 0) {
            throw new IllegalArgumentException("scan counts and ranges cannot be negative");
        }
        if (terrain == null) {
            throw new IllegalArgumentException("terrain cannot be null");
        }
        hazards = List.copyOf(requireList(hazards, "hazards"));
        safeStandSpots = List.copyOf(requireList(safeStandSpots, "safeStandSpots"));
        objectClusters = List.copyOf(requireList(objectClusters, "objectClusters"));
    }

    public record BlockCoordinate(int x, int y, int z) {
        public String compact() {
            return x + "," + y + "," + z;
        }
    }

    public record TerrainPatch(List<PassabilityEvidence> passability) {
        public TerrainPatch {
            passability = List.copyOf(requireList(passability, "passability"));
        }
    }

    public record PassabilityEvidence(
            String sector,
            String status,
            String reason,
            BlockCoordinate nearestCoordinate,
            double distance,
            int heightChange,
            String evidence
    ) {
        public PassabilityEvidence {
            sector = requireText(sector, "sector");
            status = requireText(status, "status").toLowerCase(Locale.ROOT);
            reason = requireText(reason, "reason");
            if (nearestCoordinate == null) {
                throw new IllegalArgumentException("nearestCoordinate cannot be null");
            }
            if (!Double.isFinite(distance) || distance < 0.0D) {
                throw new IllegalArgumentException("distance cannot be negative");
            }
            evidence = requireText(evidence, "evidence");
        }
    }

    public record HazardEvidence(
            String kind,
            BlockCoordinate position,
            double distance,
            String direction,
            String severity,
            String reason,
            boolean loaded
    ) {
        public HazardEvidence {
            kind = requireText(kind, "kind");
            if (position == null) {
                throw new IllegalArgumentException("position cannot be null");
            }
            if (!Double.isFinite(distance) || distance < 0.0D) {
                throw new IllegalArgumentException("distance cannot be negative");
            }
            direction = requireText(direction, "direction");
            severity = requireText(severity, "severity");
            reason = requireText(reason, "reason");
        }
    }

    public record SafeStandSpot(
            BlockCoordinate position,
            String reason,
            double distance,
            int score
    ) {
        public SafeStandSpot {
            if (position == null) {
                throw new IllegalArgumentException("position cannot be null");
            }
            reason = requireText(reason, "reason");
            if (!Double.isFinite(distance) || distance < 0.0D) {
                throw new IllegalArgumentException("distance cannot be negative");
            }
        }
    }

    public record ObjectCluster(
            String kind,
            BlockCoordinate center,
            List<BlockEvidence> evidence,
            int count,
            boolean reachable,
            boolean standable,
            String confidence
    ) {
        public ObjectCluster {
            kind = requireText(kind, "kind");
            if (center == null) {
                throw new IllegalArgumentException("center cannot be null");
            }
            evidence = List.copyOf(requireList(evidence, "evidence"));
            if (count < 0) {
                throw new IllegalArgumentException("count cannot be negative");
            }
            confidence = requireText(confidence, "confidence");
        }
    }

    public record BlockEvidence(String id, BlockCoordinate position, String label) {
        public BlockEvidence {
            id = requireText(id, "id");
            if (position == null) {
                throw new IllegalArgumentException("position cannot be null");
            }
            label = requireText(label, "label");
        }
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private static <T> List<T> requireList(List<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return values;
    }
}
