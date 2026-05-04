package dev.soffits.openplayer.runtime.context;

import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.BlockTargetSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeEntitySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeNamedEntitySnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RuntimeContextFormatter {
    public static final int BLOCK_COUNT_SUMMARY_LIMIT = 16;
    public static final int NEAREST_BLOCK_TARGET_LIMIT = 12;
    public static final int ACTIONABLE_BLOCK_TARGET_LIMIT = 6;
    public static final int DROPPED_ITEM_SUMMARY_LIMIT = 8;
    public static final int HOSTILE_SUMMARY_LIMIT = 8;
    public static final int PLAYER_SUMMARY_LIMIT = 8;
    public static final int OPENPLAYER_NPC_SUMMARY_LIMIT = 8;
    public static final int INVENTORY_SUMMARY_LIMIT = 12;
    public static final int FREE_TEXT_NAME_LIMIT = 64;

    private RuntimeContextFormatter() {
    }

    public static String format(RuntimeContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot cannot be null");
        }
        RuntimeWorldSnapshot world = snapshot.world();
        RuntimeAgentSnapshot agent = snapshot.agent();
        RuntimeNearbySnapshot nearby = snapshot.nearby();
        StringBuilder builder = new StringBuilder();
        builder.append("world: dimension=").append(world.dimension())
                .append(", npcPosition=").append(world.npcX()).append(",").append(world.npcY()).append(",").append(world.npcZ())
                .append(", dayTime=").append(world.dayTime())
                .append(", isDay=").append(world.day())
                .append(", raining=").append(world.raining())
                .append(", thundering=").append(world.thundering())
                .append(", difficulty=").append(world.difficulty())
                .append("\n");
        builder.append("agent: status=").append(agent.status().toLowerCase(Locale.ROOT))
                .append(", health=").append(agent.health()).append("/").append(agent.maxHealth())
                .append(", food=").append(agent.food())
                .append(", saturation=").append(agent.saturation())
                .append(", air=").append(agent.air())
                .append(", effects=").append(agent.activeEffects())
                .append(", physical=").append(agent.physicalStatus())
                .append(", sprintControl=").append(agent.sprintControl())
                .append(", sprinting=").append(agent.sprinting())
                .append(", mainhand=").append(agent.mainhand())
                .append(", offhand=").append(agent.offhand())
                .append(", armor=").append(armorSummary(agent.armor()))
                .append(", inventory=").append(countedSummary(agent.inventoryCounts(), INVENTORY_SUMMARY_LIMIT))
                .append("\n");
        builder.append("nearbyBlocks: ").append(blockSummary(nearby.blockCounts(), nearby.nearestBlockTargets())).append("\n");
        builder.append("nearbyDroppedItems: ").append(countedSummary(nearby.droppedItemCounts(), DROPPED_ITEM_SUMMARY_LIMIT)).append("\n");
        builder.append("nearbyHostiles: ").append(entitySummary(nearby.hostiles(), HOSTILE_SUMMARY_LIMIT)).append("\n");
        builder.append("nearbyPlayers: ").append(namedEntitySummary(nearby.players(), PLAYER_SUMMARY_LIMIT)).append("\n");
        builder.append("nearbyOpenPlayerNpcs: ").append(namedEntitySummary(nearby.openPlayerNpcs(), OPENPLAYER_NPC_SUMMARY_LIMIT));
        return builder.toString();
    }

    static String normalizeFreeTextName(String value) {
        if (value == null) {
            return "unknown";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "unknown";
        }
        if (normalized.length() > FREE_TEXT_NAME_LIMIT) {
            return normalized.substring(0, FREE_TEXT_NAME_LIMIT);
        }
        return normalized;
    }

    private static String blockSummary(Map<String, Integer> counts, List<BlockTargetSnapshot> targets) {
        return "counts=[" + countedSummary(counts, BLOCK_COUNT_SUMMARY_LIMIT) + "]; nearestTargets=["
                + nearestBlockTargets(targets) + "]; actionableTargets=[" + actionableBlockTargets(targets) + "]";
    }

    private static String nearestBlockTargets(List<BlockTargetSnapshot> targets) {
        if (targets.isEmpty()) {
            return "none";
        }
        List<BlockTargetSnapshot> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparingDouble(BlockTargetSnapshot::distanceSquared)
                .thenComparing(BlockTargetSnapshot::id)
                .thenComparingInt(BlockTargetSnapshot::x)
                .thenComparingInt(BlockTargetSnapshot::y)
                .thenComparingInt(BlockTargetSnapshot::z));
        List<String> values = new ArrayList<>();
        int end = Math.min(NEAREST_BLOCK_TARGET_LIMIT, sorted.size());
        for (int index = 0; index < end; index++) {
            BlockTargetSnapshot target = sorted.get(index);
            values.add(target.id() + " @ " + target.x() + " " + target.y() + " " + target.z());
        }
        return String.join(", ", values);
    }

    private static String actionableBlockTargets(List<BlockTargetSnapshot> targets) {
        if (targets.isEmpty()) {
            return "none";
        }
        List<BlockTargetSnapshot> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparing(RuntimeContextFormatter::actionableGroup)
                .thenComparingDouble(BlockTargetSnapshot::distanceSquared)
                .thenComparing(BlockTargetSnapshot::id)
                .thenComparingInt(BlockTargetSnapshot::x)
                .thenComparingInt(BlockTargetSnapshot::y)
                .thenComparingInt(BlockTargetSnapshot::z));
        List<String> values = new ArrayList<>();
        for (BlockTargetSnapshot target : sorted) {
            if (!isActionableTargetId(target.id())) {
                continue;
            }
            values.add(target.id() + " @ " + target.x() + " " + target.y() + " " + target.z());
            if (values.size() >= ACTIONABLE_BLOCK_TARGET_LIMIT) {
                break;
            }
        }
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static int actionableGroup(BlockTargetSnapshot target) {
        String id = target.id();
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae")) {
            return 0;
        }
        if (id.endsWith("_ore") || id.contains("_ore_")) {
            return 1;
        }
        return 2;
    }

    private static boolean isActionableTargetId(String id) {
        return id.endsWith("_log")
                || id.endsWith("_wood")
                || id.endsWith("_stem")
                || id.endsWith("_hyphae")
                || id.endsWith("_ore")
                || id.contains("_ore_")
                || id.endsWith(":chest")
                || id.endsWith(":crafting_table")
                || id.endsWith(":furnace")
                || id.endsWith(":barrel");
    }

    private static String armorSummary(List<String> armor) {
        return armor.isEmpty() ? "none" : String.join(", ", armor);
    }

    private static String countedSummary(Map<String, Integer> counts, int limit) {
        if (counts.isEmpty()) {
            return "none";
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        List<String> values = new ArrayList<>();
        for (int index = 0; index < Math.min(limit, entries.size()); index++) {
            Map.Entry<String, Integer> entry = entries.get(index);
            values.add(entry.getKey() + " x" + entry.getValue());
        }
        if (entries.size() > limit) {
            values.add("more=" + (entries.size() - limit));
        }
        return String.join(", ", values);
    }

    private static String entitySummary(List<RuntimeEntitySnapshot> entities, int limit) {
        if (entities.isEmpty()) {
            return "none";
        }
        List<String> values = new ArrayList<>();
        for (RuntimeEntitySnapshot entity : entities) {
            values.add(entity.id() + " " + relativeSummary(entity.distanceMeters(), entity.direction()));
        }
        values.sort(String::compareTo);
        return limitedList(values, limit);
    }

    private static String namedEntitySummary(List<RuntimeNamedEntitySnapshot> entities, int limit) {
        if (entities.isEmpty()) {
            return "none";
        }
        List<String> values = new ArrayList<>();
        for (RuntimeNamedEntitySnapshot entity : entities) {
            values.add(entity.name() + " " + relativeSummary(entity.distanceMeters(), entity.direction()));
        }
        values.sort(String::compareTo);
        return limitedList(values, limit);
    }

    private static String limitedList(List<String> values, int limit) {
        List<String> limited = new ArrayList<>(values.subList(0, Math.min(limit, values.size())));
        if (values.size() > limit) {
            limited.add("more=" + (values.size() - limit));
        }
        return String.join(", ", limited);
    }

    private static String relativeSummary(long distanceMeters, String direction) {
        return "distance=" + distanceMeters + "m direction=" + direction;
    }
}
