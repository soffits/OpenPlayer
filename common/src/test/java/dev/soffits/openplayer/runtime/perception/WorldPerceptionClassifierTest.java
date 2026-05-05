package dev.soffits.openplayer.runtime.perception;

import dev.soffits.openplayer.runtime.perception.WorldPerceptionClassifier.BlockSample;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionClassifier.ColumnSample;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.BlockCoordinate;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.HazardEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.ObjectCluster;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.PassabilityEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.SafeStandSpot;
import java.util.List;

public final class WorldPerceptionClassifierTest {
    private WorldPerceptionClassifierTest() {
    }

    public static void main(String[] args) {
        terrainPassabilityClassifiesOpenBlockedAndHazardSectors();
        hazardsPreserveKindPositionDirectionSeverityAndLoadedFlag();
        safeStandSpotsPreferFlatNearbyColumns();
        objectClustersDetectModFriendlyTreesWorkstationsStorageAndFarms();
    }

    private static void terrainPassabilityClassifiesOpenBlockedAndHazardSectors() {
        BlockCoordinate origin = new BlockCoordinate(0, 64, 0);
        List<ColumnSample> columns = List.of(
                new ColumnSample(new BlockCoordinate(0, 64, -3), true, false, 0, 0, "minecraft:grass_block"),
                new ColumnSample(new BlockCoordinate(1, 64, -3), false, false, 0, 0, "no_standable_surface"),
                new ColumnSample(new BlockCoordinate(0, 64, 3), false, true, 0, 0, "minecraft:lava"),
                new ColumnSample(new BlockCoordinate(3, 66, 0), true, false, 2, 0, "minecraft:stone"));

        List<PassabilityEvidence> passability = WorldPerceptionClassifier.terrainPatch(columns, origin).passability();

        require(hasPassability(passability, "north", "mixed", "nearest=0,64,-3"), "north must be mixed and preserve nearest coordinate");
        require(hasPassability(passability, "south", "hazard", "hazard_columns=1"), "south must report hazard evidence");
        require(hasPassability(passability, "east", "blocked", "blocked_columns=1"), "east two-block climb must be blocked");
    }

    private static void hazardsPreserveKindPositionDirectionSeverityAndLoadedFlag() {
        BlockCoordinate origin = new BlockCoordinate(0, 64, 0);
        List<HazardEvidence> hazards = WorldPerceptionClassifier.hazards(
                List.of(new BlockSample("minecraft:lava", new BlockCoordinate(2, 63, 0)),
                        new BlockSample("entity:minecraft:zombie", new BlockCoordinate(0, 64, -5))),
                List.of(new ColumnSample(new BlockCoordinate(4, 64, 0), true, false, 0, 5, "minecraft:stone")),
                origin,
                8);

        require(hazards.stream().anyMatch(hazard -> hazard.kind().equals("lava") && hazard.position().equals(new BlockCoordinate(2, 63, 0))
                && hazard.direction().equals("east") && hazard.severity().equals("high") && hazard.loaded()), "lava hazard must preserve structured evidence");
        require(hazards.stream().anyMatch(hazard -> hazard.kind().equals("hostile_cluster") && hazard.position().equals(new BlockCoordinate(0, 64, -5))),
                "hostile entity evidence must be a loaded hazard record");
        require(hazards.stream().anyMatch(hazard -> hazard.kind().equals("drop") && hazard.reason().equals("dropDepth=5")),
                "drop evidence must be derived from column samples");
    }

    private static void safeStandSpotsPreferFlatNearbyColumns() {
        BlockCoordinate origin = new BlockCoordinate(0, 64, 0);
        List<SafeStandSpot> spots = WorldPerceptionClassifier.safeStandSpots(List.of(
                new ColumnSample(new BlockCoordinate(1, 64, 0), true, false, 0, 0, "minecraft:grass_block"),
                new ColumnSample(new BlockCoordinate(2, 65, 0), true, false, 1, 0, "minecraft:dirt"),
                new ColumnSample(new BlockCoordinate(0, 64, 2), true, true, 0, 0, "minecraft:fire")), origin, 4);

        require(spots.size() == 2, "hazardous stand spots must be excluded");
        require(spots.get(0).position().equals(new BlockCoordinate(1, 64, 0)), "flat nearby spot must sort first");
        require(spots.get(0).reason().equals("flat_2x1"), "flat reason must be preserved");
    }

    private static void objectClustersDetectModFriendlyTreesWorkstationsStorageAndFarms() {
        BlockCoordinate origin = new BlockCoordinate(0, 64, 0);
        List<ObjectCluster> clusters = WorldPerceptionClassifier.objectClusters(List.of(
                        new BlockSample("modded:rubber_log", new BlockCoordinate(2, 64, 0)),
                        new BlockSample("minecraft:birch_leaves", new BlockCoordinate(2, 66, 0)),
                        new BlockSample("minecraft:crafting_table", new BlockCoordinate(4, 64, 0)),
                        new BlockSample("minecraft:barrel", new BlockCoordinate(5, 64, 0)),
                        new BlockSample("minecraft:farmland", new BlockCoordinate(0, 63, 4))),
                List.of(new ColumnSample(new BlockCoordinate(2, 64, 1), true, false, 0, 0, "minecraft:grass_block")),
                origin,
                8);

        require(hasCluster(clusters, "tree_cluster", "modded:rubber_log"), "modded log names must be treated as tree evidence");
        require(hasCluster(clusters, "workstation_area", "minecraft:crafting_table"), "crafting table must be workstation evidence");
        require(hasCluster(clusters, "storage_area", "minecraft:barrel"), "barrel must be storage evidence");
        require(hasCluster(clusters, "farm_patch", "minecraft:farmland"), "farmland must be farm patch evidence");
    }

    private static boolean hasPassability(List<PassabilityEvidence> values, String sector, String status, String marker) {
        for (PassabilityEvidence value : values) {
            String line = "nearest=" + value.nearestCoordinate().compact() + "," + value.reason();
            if (value.sector().equals(sector) && value.status().equals(status) && line.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCluster(List<ObjectCluster> clusters, String kind, String evidenceId) {
        return clusters.stream().anyMatch(cluster -> cluster.kind().equals(kind)
                && cluster.evidence().stream().anyMatch(evidence -> evidence.id().equals(evidenceId)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
