package dev.soffits.openplayer.runtime.perception;

import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.BlockCoordinate;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.BlockEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.HazardEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.ObjectCluster;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.PassabilityEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.SafeStandSpot;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.TerrainPatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WorldPerceptionClassifier {
    public static final int DEFAULT_SCAN_RADIUS = 8;
    public static final int DEFAULT_VERTICAL_DOWN = 5;
    public static final int DEFAULT_VERTICAL_UP = 5;
    public static final int MAX_SCAN_RADIUS = 16;
    public static final int MAX_OBJECT_EVIDENCE = 4;

    private WorldPerceptionClassifier() {
    }

    public static TerrainPatch terrainPatch(List<ColumnSample> columns, BlockCoordinate origin) {
        Map<String, List<ColumnSample>> bySector = new LinkedHashMap<>();
        for (String sector : List.of("north", "south", "east", "west", "near")) {
            bySector.put(sector, new ArrayList<>());
        }
        for (ColumnSample column : columns) {
            bySector.computeIfAbsent(direction(origin, column.position()), ignored -> new ArrayList<>()).add(column);
        }
        List<PassabilityEvidence> passability = new ArrayList<>();
        for (Map.Entry<String, List<ColumnSample>> entry : bySector.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            passability.add(classifySector(entry.getKey(), entry.getValue(), origin));
        }
        passability.sort(Comparator.comparing(PassabilityEvidence::sector));
        return new TerrainPatch(passability);
    }

    public static List<HazardEvidence> hazards(List<BlockSample> blocks, List<ColumnSample> columns, BlockCoordinate origin, int limit) {
        List<HazardEvidence> hazards = new ArrayList<>();
        for (BlockSample block : blocks) {
            String kind = hazardKind(block.id());
            if (kind.isBlank()) {
                continue;
            }
            hazards.add(new HazardEvidence(kind, block.position(), distance(origin, block.position()), direction(origin, block.position()), hazardSeverity(kind), block.id(), true));
        }
        for (ColumnSample column : columns) {
            if (column.dropDepth() >= 4) {
                hazards.add(new HazardEvidence("drop", column.position(), distance(origin, column.position()), direction(origin, column.position()),
                        column.dropDepth() >= 8 ? "high" : "medium", "dropDepth=" + column.dropDepth(), true));
            }
        }
        hazards.sort(Comparator.comparingDouble(HazardEvidence::distance).thenComparing(HazardEvidence::kind)
                .thenComparing(hazard -> hazard.position().compact()));
        return List.copyOf(hazards.subList(0, Math.min(Math.max(0, limit), hazards.size())));
    }

    public static List<SafeStandSpot> safeStandSpots(List<ColumnSample> columns, BlockCoordinate origin, int limit) {
        List<SafeStandSpot> spots = new ArrayList<>();
        for (ColumnSample column : columns) {
            if (!column.standable() || column.hazardous() || column.dropDepth() > 1) {
                continue;
            }
            int score = 100 - (int) Math.round(distance(origin, column.position()) * 4.0D) - Math.abs(column.heightChange()) * 8;
            String reason = Math.abs(column.heightChange()) == 0 ? "flat_2x1" : "nearby_step";
            spots.add(new SafeStandSpot(column.position(), reason, distance(origin, column.position()), score));
        }
        spots.sort(Comparator.comparingInt(SafeStandSpot::score).reversed()
                .thenComparingDouble(SafeStandSpot::distance)
                .thenComparing(spot -> spot.position().compact()));
        return List.copyOf(spots.subList(0, Math.min(Math.max(0, limit), spots.size())));
    }

    public static List<ObjectCluster> objectClusters(List<BlockSample> blocks, List<ColumnSample> columns, BlockCoordinate origin, int limit) {
        Map<String, List<BlockSample>> grouped = new LinkedHashMap<>();
        for (BlockSample block : blocks) {
            String kind = objectKind(block.id());
            if (!kind.isBlank()) {
                grouped.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(block);
            }
        }
        List<ObjectCluster> clusters = new ArrayList<>();
        for (Map.Entry<String, List<BlockSample>> entry : grouped.entrySet()) {
            List<BlockSample> samples = entry.getValue();
            samples.sort(Comparator.<BlockSample>comparingDouble(sample -> distance(origin, sample.position()))
                    .thenComparing(BlockSample::id).thenComparing(sample -> sample.position().compact()));
            clusters.add(new ObjectCluster(entry.getKey(), center(samples), evidence(samples), samples.size(),
                    nearestDistance(samples, origin) <= 6.0D, hasStandableColumnNear(samples, columns), confidence(samples.size())));
        }
        clusters.sort(Comparator.<ObjectCluster>comparingDouble(cluster -> distance(origin, cluster.center())).thenComparing(ObjectCluster::kind));
        return List.copyOf(clusters.subList(0, Math.min(Math.max(0, limit), clusters.size())));
    }

    public static boolean isPotentialObjectBlock(String id) {
        return !objectKind(id).isBlank();
    }

    public static boolean isHazardBlock(String id) {
        return !hazardKind(id).isBlank();
    }

    public static String direction(BlockCoordinate origin, BlockCoordinate target) {
        int dx = target.x() - origin.x();
        int dz = target.z() - origin.z();
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
            return "near";
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? "east" : "west";
        }
        return dz >= 0 ? "south" : "north";
    }

    public static double distance(BlockCoordinate origin, BlockCoordinate target) {
        int dx = target.x() - origin.x();
        int dy = target.y() - origin.y();
        int dz = target.z() - origin.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static PassabilityEvidence classifySector(String sector, List<ColumnSample> columns, BlockCoordinate origin) {
        columns.sort(Comparator.<ColumnSample>comparingDouble(column -> distance(origin, column.position())).thenComparing(column -> column.position().compact()));
        int open = 0;
        int blocked = 0;
        int hazardous = 0;
        ColumnSample nearest = columns.get(0);
        for (ColumnSample column : columns) {
            if (column.hazardous() || column.dropDepth() >= 4) {
                hazardous++;
            } else if (column.standable() && Math.abs(column.heightChange()) <= 1) {
                open++;
            } else {
                blocked++;
            }
        }
        String status;
        String reason;
        if (hazardous > 0 && open == 0) {
            status = "hazard";
            reason = "hazard_columns=" + hazardous;
        } else if (open > 0 && blocked == 0 && hazardous == 0) {
            status = "open";
            reason = "standable_columns=" + open;
        } else if (open == 0) {
            status = "blocked";
            reason = "blocked_columns=" + blocked;
        } else {
            status = "mixed";
            reason = "open=" + open + ",blocked=" + blocked + ",hazard=" + hazardous;
        }
        return new PassabilityEvidence(sector, status, reason, nearest.position(), distance(origin, nearest.position()), nearest.heightChange(), nearest.evidence());
    }

    private static List<BlockEvidence> evidence(List<BlockSample> samples) {
        ArrayList<BlockEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < samples.size() && evidence.size() < MAX_OBJECT_EVIDENCE; index++) {
            BlockSample sample = samples.get(index);
            evidence.add(new BlockEvidence(sample.id(), sample.position(), objectLabel(sample.id())));
        }
        return evidence;
    }

    private static boolean hasStandableColumnNear(List<BlockSample> samples, List<ColumnSample> columns) {
        for (BlockSample sample : samples) {
            for (ColumnSample column : columns) {
                if (column.standable() && distance(sample.position(), column.position()) <= 3.0D) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BlockCoordinate center(List<BlockSample> samples) {
        long x = 0;
        long y = 0;
        long z = 0;
        for (BlockSample sample : samples) {
            x += sample.position().x();
            y += sample.position().y();
            z += sample.position().z();
        }
        return new BlockCoordinate(Math.round((float) x / samples.size()), Math.round((float) y / samples.size()), Math.round((float) z / samples.size()));
    }

    private static double nearestDistance(List<BlockSample> samples, BlockCoordinate origin) {
        double nearest = Double.MAX_VALUE;
        for (BlockSample sample : samples) {
            nearest = Math.min(nearest, distance(origin, sample.position()));
        }
        return nearest;
    }

    private static String confidence(int count) {
        if (count >= 5) {
            return "high";
        }
        return count >= 2 ? "medium" : "low";
    }

    private static String hazardKind(String id) {
        String value = id.toLowerCase(Locale.ROOT);
        if (value.endsWith(":lava") || value.endsWith(":flowing_lava")) {
            return "lava";
        }
        if (value.endsWith(":fire") || value.endsWith(":soul_fire")) {
            return "fire";
        }
        if (value.endsWith(":cactus")) {
            return "cactus";
        }
        if (value.endsWith(":powder_snow")) {
            return "powder_snow";
        }
        if (value.endsWith(":water") || value.endsWith(":flowing_water")) {
            return "water";
        }
        if (value.startsWith("entity:")) {
            return "hostile_cluster";
        }
        return "";
    }

    private static String hazardSeverity(String kind) {
        return switch (kind) {
            case "lava", "fire" -> "high";
            case "drop", "cactus", "powder_snow", "hostile_cluster" -> "medium";
            default -> "low";
        };
    }

    private static String objectKind(String id) {
        String value = id.toLowerCase(Locale.ROOT);
        if (value.endsWith("_log") || value.endsWith("_wood") || value.endsWith("_stem") || value.endsWith("_hyphae")
                || value.endsWith("_leaves") || value.endsWith("_wart_block")) {
            return "tree_cluster";
        }
        if (value.endsWith(":crafting_table") || value.endsWith(":furnace") || value.endsWith(":blast_furnace")
                || value.endsWith(":smoker") || value.endsWith(":stonecutter") || value.endsWith(":anvil")) {
            return "workstation_area";
        }
        if (value.endsWith(":chest") || value.endsWith(":trapped_chest") || value.endsWith(":barrel")) {
            return "storage_area";
        }
        if (value.endsWith(":farmland") || value.contains("wheat") || value.contains("carrot") || value.contains("potato")
                || value.contains("beetroot") || value.contains("melon_stem") || value.contains("pumpkin_stem")) {
            return "farm_patch";
        }
        return "";
    }

    private static String objectLabel(String id) {
        String kind = objectKind(id);
        return kind.isBlank() ? "block" : kind;
    }

    public record BlockSample(String id, BlockCoordinate position) {
        public BlockSample {
            id = WorldPerceptionSnapshot.requireText(id, "id");
            if (position == null) {
                throw new IllegalArgumentException("position cannot be null");
            }
        }
    }

    public record ColumnSample(BlockCoordinate position, boolean standable, boolean hazardous, int heightChange, int dropDepth, String evidence) {
        public ColumnSample {
            if (position == null) {
                throw new IllegalArgumentException("position cannot be null");
            }
            if (dropDepth < 0) {
                throw new IllegalArgumentException("dropDepth cannot be negative");
            }
            evidence = WorldPerceptionSnapshot.requireText(evidence, "evidence");
        }
    }
}
