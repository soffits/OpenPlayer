package dev.soffits.openplayer.runtime.context;

import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.BlockTargetSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeEntitySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeNamedEntitySnapshot;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.BlockCoordinate;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.BlockEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.HazardEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.ObjectCluster;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.PassabilityEvidence;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.SafeStandSpot;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.TerrainPatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeContextFormatterTest {
    private RuntimeContextFormatterTest() {
    }

    public static void main(String[] args) {
        representativeSnapshotFormatsGoldenOutput();
        emptySectionsRenderNone();
        overflowSectionsIncludeDeterministicFirstValuesAndMoreCount();
        actionableTargetsExposeUsefulBlocksOutsideNearestLimit();
        countedSummariesAreDeterministicAcrossInsertionOrder();
        snapshotsDefensivelyCopyCollections();
        formattedContextDoesNotContainSensitiveMarkersWithoutSensitiveFields();
        freeTextNamesAreSingleLineAndBounded();
        perceptionProjectionPreservesStructuredEvidence();
    }

    private static void representativeSnapshotFormatsGoldenOutput() {
        RuntimeContextSnapshot snapshot = new RuntimeContextSnapshot(
                new RuntimeWorldSnapshot("minecraft:overworld", 10, 64, -3, 6000L, true, false, false, "normal"),
                new RuntimeAgentSnapshot(
                        "active",
                        19,
                        20,
                        300,
                        "minecraft:iron_sword",
                        "minecraft:shield",
                        List.of("head=minecraft:iron_helmet", "chest=minecraft:iron_chestplate"),
                        mapOf(
                                "minecraft:bread", 5,
                                "minecraft:torch", 16,
                                "minecraft:apple", 2
                        )
                ),
                new RuntimeNearbySnapshot(
                        mapOf(
                                "minecraft:stone", 20,
                                "minecraft:dirt", 7,
                                "minecraft:oak_log", 3
                        ),
                        List.of(
                                new BlockTargetSnapshot("minecraft:oak_log", 8, 64, -3, 4.0D),
                                new BlockTargetSnapshot("minecraft:stone", 10, 63, -2, 2.0D)
                        ),
                        mapOf("minecraft:arrow", 3, "minecraft:bone", 1),
                        List.of(
                                new RuntimeEntitySnapshot("minecraft:zombie", 9L, "north"),
                                new RuntimeEntitySnapshot("minecraft:skeleton", 12L, "south-east+above")
                        ),
                        List.of(
                                new RuntimeNamedEntitySnapshot("Steve", 5L, "east"),
                                new RuntimeNamedEntitySnapshot("Alex", 3L, "near")
                        ),
                        List.of(new RuntimeNamedEntitySnapshot("Helper NPC", 7L, "west+below"))
                )
        );

        String expected = "world: dimension=minecraft:overworld, npcPosition=10,64,-3, dayTime=6000, isDay=true, raining=false, thundering=false, difficulty=normal\n"
                + "agent: status=active, health=19/20, food=unsupported, saturation=unsupported, air=300, effects=none, physical=unknown, sprintControl=unsupported, sprinting=unknown, mainhand=minecraft:iron_sword, offhand=minecraft:shield, armor=head=minecraft:iron_helmet, chest=minecraft:iron_chestplate, inventory=minecraft:torch x16, minecraft:bread x5, minecraft:apple x2\n"
                + "nearbyBlocks: counts=[minecraft:stone x20, minecraft:dirt x7, minecraft:oak_log x3]; nearestTargets=[minecraft:stone @ 10 63 -2, minecraft:oak_log @ 8 64 -3]; actionableTargets=[minecraft:oak_log @ 8 64 -3]\n"
                + "nearbyDroppedItems: minecraft:arrow x3, minecraft:bone x1\n"
                + "nearbyHostiles: minecraft:skeleton distance=12m direction=south-east+above, minecraft:zombie distance=9m direction=north\n"
                + "nearbyPlayers: Alex distance=3m direction=near, Steve distance=5m direction=east\n"
                + "nearbyOpenPlayerNpcs: Helper NPC distance=7m direction=west+below\n"
                + "worldPerception: {source=npc,dimension=unknown,origin=0,0,0,scan={radius=0,verticalDown=0,verticalUp=0,scannedBlocks=0,sampledColumns=0,capped=false,truncated=false},passability=[none],hazards=[none],safeStand=[none],clusters=[none]}";

        require(expected.equals(RuntimeContextFormatter.format(snapshot)), "representative context must match golden output");
    }

    private static void emptySectionsRenderNone() {
        String formatted = RuntimeContextFormatter.format(emptySnapshot());

        require(formatted.contains("armor=none, inventory=none"), "empty armor and inventory must render none");
        require(formatted.contains("nearbyBlocks: counts=[none]; nearestTargets=[none]; actionableTargets=[none]"), "empty blocks must render none");
        require(formatted.contains("nearbyDroppedItems: none"), "empty dropped items must render none");
        require(formatted.contains("nearbyHostiles: none"), "empty hostiles must render none");
        require(formatted.contains("nearbyPlayers: none"), "empty players must render none");
        require(formatted.contains("nearbyOpenPlayerNpcs: none"), "empty OpenPlayer NPCs must render none");
    }

    private static void overflowSectionsIncludeDeterministicFirstValuesAndMoreCount() {
        RuntimeContextSnapshot snapshot = new RuntimeContextSnapshot(
                new RuntimeWorldSnapshot("minecraft:overworld", 0, 64, 0, 0L, true, false, false, "normal"),
                new RuntimeAgentSnapshot("active", 20, 20, 300, "empty", "empty", List.of(), numberedCounts("minecraft:item_", 14)),
                new RuntimeNearbySnapshot(
                        numberedCounts("minecraft:block_", 18),
                        numberedTargets(14),
                        numberedCounts("minecraft:drop_", 10),
                        numberedEntities("minecraft:hostile_", 10),
                        numberedNamedEntities("Player", 10),
                        numberedNamedEntities("Npc", 10)
                )
        );

        String formatted = RuntimeContextFormatter.format(snapshot);

        require(formatted.contains("inventory=minecraft:item_13 x14, minecraft:item_12 x13"), "inventory overflow must start by count order");
        require(formatted.contains("minecraft:item_02 x3, more=2"), "inventory overflow must include more count");
        require(formatted.contains("counts=[minecraft:block_17 x18"), "block count overflow must start by count order");
        require(formatted.contains("minecraft:block_02 x3, more=2"), "block count overflow must include more count");
        require(formatted.contains("nearestTargets=[minecraft:block_00 @ 0 64 0"), "nearest targets must start by deterministic distance order");
        require(formatted.contains("minecraft:block_11 @ 11 64 0]"), "nearest target overflow must include the twelfth target");
        require(!nearestTargetsSummary(formatted).contains("more="), "nearest targets must not include more count");
        require(formatted.contains("nearbyDroppedItems: minecraft:drop_09 x10"), "dropped item overflow must start by count order");
        require(formatted.contains("minecraft:drop_02 x3, more=2"), "dropped item overflow must include more count");
        require(formatted.contains("nearbyHostiles: minecraft:hostile_00 distance=1m direction=north"), "hostiles overflow must start lexically");
        require(formatted.contains("minecraft:hostile_07 distance=8m direction=north, more=2"), "hostiles overflow must include more count");
        require(formatted.contains("nearbyPlayers: Player00 distance=1m direction=east"), "players overflow must start lexically");
        require(formatted.contains("Player07 distance=8m direction=east, more=2"), "players overflow must include more count");
        require(formatted.contains("nearbyOpenPlayerNpcs: Npc00 distance=1m direction=east"), "NPC overflow must start lexically");
        require(formatted.contains("Npc07 distance=8m direction=east, more=2"), "NPC overflow must include more count");
    }

    private static void actionableTargetsExposeUsefulBlocksOutsideNearestLimit() {
        List<BlockTargetSnapshot> targets = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index < RuntimeContextFormatter.NEAREST_BLOCK_TARGET_LIMIT; index++) {
            targets.add(new BlockTargetSnapshot("minecraft:snow", index, 64, 0, index));
            counts.merge("minecraft:snow", 1, Integer::sum);
        }
        targets.add(new BlockTargetSnapshot("minecraft:spruce_log", 20, 65, 0, 400.0D));
        counts.put("minecraft:spruce_log", 1);
        RuntimeContextSnapshot snapshot = new RuntimeContextSnapshot(
                new RuntimeWorldSnapshot("minecraft:overworld", 0, 64, 0, 0L, true, false, false, "normal"),
                new RuntimeAgentSnapshot("active", 20, 20, 300, "empty", "empty", List.of(), Map.of()),
                new RuntimeNearbySnapshot(counts, targets, Map.of(), List.of(), List.of(), List.of())
        );

        String formatted = RuntimeContextFormatter.format(snapshot);

        require(!nearestTargetsSummary(formatted).contains("minecraft:spruce_log @ 20 65 0"),
                "test setup must keep log outside global nearest target summary");
        require(formatted.contains("actionableTargets=[minecraft:spruce_log @ 20 65 0]"),
                "actionable target summary must expose log coordinates present in bounded scan");
    }

    private static void countedSummariesAreDeterministicAcrossInsertionOrder() {
        RuntimeContextSnapshot first = snapshotWithCounts(mapOf("minecraft:cobblestone", 4, "minecraft:andesite", 4, "minecraft:deepslate", 7));
        RuntimeContextSnapshot second = snapshotWithCounts(mapOf("minecraft:deepslate", 7, "minecraft:andesite", 4, "minecraft:cobblestone", 4));

        require(RuntimeContextFormatter.format(first).equals(RuntimeContextFormatter.format(second)),
                "counted summaries must not depend on insertion order");
    }

    private static void snapshotsDefensivelyCopyCollections() {
        ArrayList<String> armor = new ArrayList<>(List.of("head=minecraft:iron_helmet"));
        Map<String, Integer> inventoryCounts = new LinkedHashMap<>(Map.of("minecraft:bread", 2));
        RuntimeAgentSnapshot agent = new RuntimeAgentSnapshot("active", 20, 20, 300, "empty", "empty", armor, inventoryCounts);
        armor.add("chest=minecraft:iron_chestplate");
        inventoryCounts.put("minecraft:apple", 3);

        require(agent.armor().equals(List.of("head=minecraft:iron_helmet")), "agent armor must be defensively copied");
        require(agent.inventoryCounts().equals(Map.of("minecraft:bread", 2)), "agent inventory counts must be defensively copied");
        requireThrowsUnsupported(() -> agent.armor().add("feet=minecraft:iron_boots"), "agent armor must be unmodifiable");
        requireThrowsUnsupported(() -> agent.inventoryCounts().put("minecraft:torch", 4), "agent inventory counts must be unmodifiable");

        Map<String, Integer> blockCounts = new LinkedHashMap<>(Map.of("minecraft:stone", 4));
        ArrayList<BlockTargetSnapshot> nearestBlockTargets = new ArrayList<>(List.of(new BlockTargetSnapshot("minecraft:stone", 0, 64, 0, 1.0D)));
        Map<String, Integer> droppedItemCounts = new LinkedHashMap<>(Map.of("minecraft:arrow", 1));
        ArrayList<RuntimeEntitySnapshot> hostiles = new ArrayList<>(List.of(new RuntimeEntitySnapshot("minecraft:zombie", 8L, "north")));
        ArrayList<RuntimeNamedEntitySnapshot> players = new ArrayList<>(List.of(new RuntimeNamedEntitySnapshot("Steve", 5L, "east")));
        ArrayList<RuntimeNamedEntitySnapshot> openPlayerNpcs = new ArrayList<>(List.of(new RuntimeNamedEntitySnapshot("Helper", 3L, "west")));
        RuntimeNearbySnapshot nearby = new RuntimeNearbySnapshot(blockCounts, nearestBlockTargets, droppedItemCounts, hostiles, players, openPlayerNpcs);
        blockCounts.put("minecraft:dirt", 2);
        nearestBlockTargets.add(new BlockTargetSnapshot("minecraft:dirt", 1, 64, 0, 2.0D));
        droppedItemCounts.put("minecraft:bone", 2);
        hostiles.add(new RuntimeEntitySnapshot("minecraft:skeleton", 9L, "south"));
        players.add(new RuntimeNamedEntitySnapshot("Alex", 6L, "north"));
        openPlayerNpcs.add(new RuntimeNamedEntitySnapshot("Scout", 7L, "south"));

        require(nearby.blockCounts().equals(Map.of("minecraft:stone", 4)), "nearby block counts must be defensively copied");
        require(nearby.nearestBlockTargets().equals(List.of(new BlockTargetSnapshot("minecraft:stone", 0, 64, 0, 1.0D))), "nearest block targets must be defensively copied");
        require(nearby.droppedItemCounts().equals(Map.of("minecraft:arrow", 1)), "dropped item counts must be defensively copied");
        require(nearby.hostiles().equals(List.of(new RuntimeEntitySnapshot("minecraft:zombie", 8L, "north"))), "hostiles must be defensively copied");
        require(nearby.players().equals(List.of(new RuntimeNamedEntitySnapshot("Steve", 5L, "east"))), "players must be defensively copied");
        require(nearby.openPlayerNpcs().equals(List.of(new RuntimeNamedEntitySnapshot("Helper", 3L, "west"))), "OpenPlayer NPCs must be defensively copied");
        requireThrowsUnsupported(() -> nearby.blockCounts().put("minecraft:granite", 1), "nearby block counts must be unmodifiable");
        requireThrowsUnsupported(() -> nearby.nearestBlockTargets().add(new BlockTargetSnapshot("minecraft:granite", 2, 64, 0, 3.0D)), "nearest block targets must be unmodifiable");
        requireThrowsUnsupported(() -> nearby.droppedItemCounts().put("minecraft:string", 1), "dropped item counts must be unmodifiable");
        requireThrowsUnsupported(() -> nearby.hostiles().add(new RuntimeEntitySnapshot("minecraft:spider", 4L, "west")), "hostiles must be unmodifiable");
        requireThrowsUnsupported(() -> nearby.players().add(new RuntimeNamedEntitySnapshot("Builder", 4L, "east")), "players must be unmodifiable");
        requireThrowsUnsupported(() -> nearby.openPlayerNpcs().add(new RuntimeNamedEntitySnapshot("Miner", 4L, "east")), "OpenPlayer NPCs must be unmodifiable");
    }

    private static void formattedContextDoesNotContainSensitiveMarkersWithoutSensitiveFields() {
        String formatted = RuntimeContextFormatter.format(emptySnapshot());

        require(!formatted.contains("api_key"), "context must not contain API key markers");
        require(!formatted.contains("provider"), "context must not contain provider data markers");
        require(!formatted.contains("sessionId"), "context must not contain session ids");
        require(!formatted.contains("ownerId"), "context must not contain owner ids");
        require(!formatted.contains("skinTexture"), "context must not contain skin texture markers");
    }

    private static void freeTextNamesAreSingleLineAndBounded() {
        String longName = "Player\nName\t" + "x".repeat(120);
        RuntimeContextSnapshot snapshot = new RuntimeContextSnapshot(
                new RuntimeWorldSnapshot("minecraft:overworld", 0, 64, 0, 0L, true, false, false, "normal"),
                new RuntimeAgentSnapshot("active", 20, 20, 300, "empty", "empty", List.of(), Map.of()),
                new RuntimeNearbySnapshot(
                        Map.of(),
                        List.of(),
                        Map.of(),
                        List.of(),
                        List.of(new RuntimeNamedEntitySnapshot(longName, 2L, "near")),
                        List.of(new RuntimeNamedEntitySnapshot("Npc\nTab\tName", 4L, "south"))
                )
        );
        String formatted = RuntimeContextFormatter.format(snapshot);
        String playerLine = lineStartingWith(formatted, "nearbyPlayers: ");

        require(!playerLine.contains("\n"), "player names must not contain newlines inside formatted line");
        require(!playerLine.contains("\t"), "player names must not contain tabs");
        require(playerLine.contains("Player Name "), "player names must normalize whitespace");
        require(playerLine.indexOf("distance=2m") <= "nearbyPlayers: ".length() + RuntimeContextFormatter.FREE_TEXT_NAME_LIMIT + 1,
                "player names must be bounded before distance text");
        require(formatted.contains("nearbyOpenPlayerNpcs: Npc Tab Name distance=4m direction=south"),
                "NPC names must normalize whitespace");
    }

    private static void perceptionProjectionPreservesStructuredEvidence() {
        RuntimeContextSnapshot snapshot = new RuntimeContextSnapshot(
                new RuntimeWorldSnapshot("minecraft:overworld", 10, 64, -3, 0L, true, false, false, "normal"),
                new RuntimeAgentSnapshot("active", 20, 20, 300, "empty", "empty", List.of(), Map.of()),
                new RuntimeNearbySnapshot(Map.of(), List.of(), Map.of(), List.of(), List.of(), List.of()),
                new WorldPerceptionSnapshot(
                        "npc",
                        "minecraft:overworld",
                        new BlockCoordinate(10, 64, -3),
                        8,
                        5,
                        5,
                        500,
                        81,
                        true,
                        true,
                        new TerrainPatch(List.of(new PassabilityEvidence("north", "mixed", "open=2,blocked=1,hazard=1",
                                new BlockCoordinate(10, 64, -6), 3.0D, 0, "minecraft:grass_block"))),
                        List.of(new HazardEvidence("lava", new BlockCoordinate(12, 63, -3), 2.2D, "east", "high", "minecraft:lava", true)),
                        List.of(new SafeStandSpot(new BlockCoordinate(9, 64, -3), "flat_2x1", 1.0D, 96)),
                        List.of(new ObjectCluster("tree_cluster", new BlockCoordinate(8, 65, -4),
                                List.of(new BlockEvidence("modded:rubber_log", new BlockCoordinate(8, 64, -4), "tree_cluster")),
                                3, true, true, "medium"))
                )
        );

        String formatted = RuntimeContextFormatter.format(snapshot);

        require(formatted.contains("source=npc"), "perception source must be preserved");
        require(formatted.contains("dimension=minecraft:overworld"), "perception dimension must be preserved");
        require(formatted.contains("origin=10,64,-3"), "perception origin coordinates must be preserved");
        require(formatted.contains("capped=true,truncated=true"), "perception cap flags must be preserved");
        require(formatted.contains("nearest=10,64,-6"), "passability coordinates must be preserved");
        require(formatted.contains("kind=lava,pos=12,63,-3"), "hazard coordinates and kind must be preserved");
        require(formatted.contains("pos=9,64,-3,reason=flat_2x1"), "safe stand position and reason must be preserved");
        require(formatted.contains("id=modded:rubber_log,pos=8,64,-4,label=tree_cluster"), "cluster evidence must be preserved");
    }

    private static RuntimeContextSnapshot emptySnapshot() {
        return new RuntimeContextSnapshot(
                new RuntimeWorldSnapshot("minecraft:overworld", 0, 64, 0, 0L, true, false, false, "normal"),
                new RuntimeAgentSnapshot("active", 20, 20, 300, "empty", "empty", List.of(), Map.of()),
                new RuntimeNearbySnapshot(Map.of(), List.of(), Map.of(), List.of(), List.of(), List.of())
        );
    }

    private static RuntimeContextSnapshot snapshotWithCounts(Map<String, Integer> counts) {
        return new RuntimeContextSnapshot(
                new RuntimeWorldSnapshot("minecraft:overworld", 0, 64, 0, 0L, true, false, false, "normal"),
                new RuntimeAgentSnapshot("active", 20, 20, 300, "empty", "empty", List.of(), counts),
                new RuntimeNearbySnapshot(counts, List.of(), counts, List.of(), List.of(), List.of())
        );
    }

    private static Map<String, Integer> mapOf(String firstKey, int firstValue, String secondKey, int secondValue) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(firstKey, firstValue);
        values.put(secondKey, secondValue);
        return values;
    }

    private static Map<String, Integer> mapOf(String firstKey, int firstValue, String secondKey, int secondValue,
                                             String thirdKey, int thirdValue) {
        Map<String, Integer> values = mapOf(firstKey, firstValue, secondKey, secondValue);
        values.put(thirdKey, thirdValue);
        return values;
    }

    private static Map<String, Integer> numberedCounts(String prefix, int count) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            values.put(prefix + String.format("%02d", index), index + 1);
        }
        return values;
    }

    private static List<BlockTargetSnapshot> numberedTargets(int count) {
        java.util.ArrayList<BlockTargetSnapshot> values = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add(new BlockTargetSnapshot("minecraft:block_" + String.format("%02d", index), index, 64, 0, index));
        }
        return values;
    }

    private static List<RuntimeEntitySnapshot> numberedEntities(String prefix, int count) {
        java.util.ArrayList<RuntimeEntitySnapshot> values = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add(new RuntimeEntitySnapshot(prefix + String.format("%02d", index), index + 1L, "north"));
        }
        return values;
    }

    private static List<RuntimeNamedEntitySnapshot> numberedNamedEntities(String prefix, int count) {
        java.util.ArrayList<RuntimeNamedEntitySnapshot> values = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add(new RuntimeNamedEntitySnapshot(prefix + String.format("%02d", index), index + 1L, "east"));
        }
        return values;
    }

    private static String lineStartingWith(String value, String prefix) {
        for (String line : value.split("\\n")) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        throw new AssertionError("missing line with prefix: " + prefix);
    }

    private static String nearestTargetsSummary(String formatted) {
        String line = lineStartingWith(formatted, "nearbyBlocks: ");
        int start = line.indexOf("nearestTargets=[");
        if (start < 0) {
            throw new AssertionError("missing nearestTargets summary");
        }
        int end = line.indexOf("; actionableTargets=", start);
        return end < 0 ? line.substring(start) : line.substring(start, end);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireThrowsUnsupported(Runnable action, String message) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
